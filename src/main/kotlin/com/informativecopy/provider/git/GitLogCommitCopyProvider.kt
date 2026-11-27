package com.informativecopy.provider.git

import com.informativecopy.format.CommitInfo
import com.informativecopy.format.FileChangeEntry
import com.informativecopy.format.InformativeFormatter
import com.informativecopy.provider.ContextCopyProvider
import com.informativecopy.provider.CopyExecution
import com.informativecopy.provider.CopyPathResolver
import com.informativecopy.settings.InformativeCopySettings
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLogDataKeys
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import javax.swing.JTree

class GitLogCommitCopyProvider : ContextCopyProvider {

    override val priority: Int = 80

    private data class CommitTarget(
        val hash: String,
        val root: VirtualFile
    )

    override fun createExecution(
        event: AnActionEvent,
        settings: InformativeCopySettings.State,
        detailed: Boolean
    ): CopyExecution? {
        if (event.getData(CommonDataKeys.EDITOR) != null) return null
        val project = event.project ?: return null
        val selection = event.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION) ?: return null
        val commits = selection.commits
        if (commits.isEmpty()) return null

        val targets = commits.map { CommitTarget(it.hash.asString(), it.root) }
        val focusedComponent = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
        val selectedChanges = event.getData(VcsDataKeys.SELECTED_CHANGES)?.toList().orEmpty()

        // If user specifically focused the changed-files tree inside Git Log, filter to those files
        val specificPaths: List<String> = if (focusedComponent is JTree && selectedChanges.isNotEmpty()) {
            selectedChanges.mapNotNull { change ->
                change.afterRevision?.file?.path ?: change.beforeRevision?.file?.path
            }.distinct()
        } else {
            emptyList()
        }

        val includeDiff = detailed || settings.includeCommitDiffByDefault
        val taskTitle = if (targets.size == 1) {
            "Informative Copy: Reading commit ${targets.first().hash.take(8)}..."
        } else {
            "Informative Copy: Reading ${targets.size} commits..."
        }

        return CopyExecution.Async(taskTitle) {
            val commitInfos = targets.mapNotNull { target ->
                loadCommitInfo(
                    project = project,
                    target = target,
                    settings = settings,
                    includeDiff = includeDiff,
                    filterAbsolutePaths = specificPaths
                )
            }
            if (commitInfos.isEmpty()) return@Async null

            val formatted = InformativeFormatter.formatCommits(commitInfos, settings)
            val summary = if (commitInfos.size == 1) {
                val c = commitInfos.first()
                val diffNote = if (!c.diff.isNullOrBlank()) ", with diff" else ""
                "Copied commit ${c.shortHash} (${c.changedFiles.size} files$diffNote)"
            } else {
                val diffNote = if (includeDiff) " (with diff)" else ""
                "Copied ${commitInfos.size} commits$diffNote"
            }

            CopyExecution.Immediate(text = formatted, summary = summary)
        }
    }

    private fun loadCommitInfo(
        project: Project,
        target: CommitTarget,
        settings: InformativeCopySettings.State,
        includeDiff: Boolean,
        filterAbsolutePaths: List<String>
    ): CommitInfo? {
        val metaResult = runGit(
            project = project,
            root = target.root,
            args = listOf(
                "show",
                "--no-patch",
                "--date=format:%Y-%m-%d %H:%M",
                "--format=%H%x1f%h%x1f%an%x1f%ad%x1f%B",
                target.hash
            )
        ) ?: return null

        val parts = metaResult.split('\u001f', limit = 5)
        if (parts.size < 5) return null

        val fullHash = parts[0].trim()
        val shortHash = parts[1].trim()
        val author = parts[2].trim()
        val date = parts[3].trim()
        val message = parts[4].trim()

        val relativeFilters = filterAbsolutePaths.mapNotNull { absPath ->
            toRepoRelativePath(target.root, absPath)
        }

        val nameStatusArgs = mutableListOf("show", "--name-status", "--format=", "-m", "--first-parent", target.hash)
        if (relativeFilters.isNotEmpty()) {
            nameStatusArgs += "--"
            nameStatusArgs += relativeFilters
        }

        val nameStatusOut = runGit(project, target.root, nameStatusArgs).orEmpty()
        val changedFiles = parseNameStatus(project, target.root, nameStatusOut, settings)

        val diffText = if (includeDiff) {
            val diffArgs = mutableListOf("show", "--format=", "--patch", "-m", "--first-parent", target.hash)
            if (relativeFilters.isNotEmpty()) {
                diffArgs += "--"
                diffArgs += relativeFilters
            }
            runGit(project, target.root, diffArgs)?.takeIf { it.isNotBlank() }
        } else {
            null
        }

        return CommitInfo(
            shortHash = shortHash,
            fullHash = fullHash,
            author = author,
            date = date,
            message = message,
            changedFiles = changedFiles,
            diff = diffText
        )
    }

    private fun parseNameStatus(
        project: Project,
        root: VirtualFile,
        output: String,
        settings: InformativeCopySettings.State
    ): List<FileChangeEntry> {
        val entries = mutableListOf<FileChangeEntry>()
        val rootPath = root.path.replace('\\', '/').trimEnd('/')

        for (rawLine in output.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val cols = line.split('\t')
            if (cols.size < 2) continue

            val rawStatus = cols[0].trim()
            val statusCode = rawStatus.firstOrNull()?.uppercaseChar()?.toString() ?: "M"

            if ((statusCode == "R" || statusCode == "C") && cols.size >= 3) {
                val oldResolved = CopyPathResolver.resolve(project, "$rootPath/${cols[1]}", settings)
                val newResolved = CopyPathResolver.resolve(project, "$rootPath/${cols[2]}", settings)
                entries += FileChangeEntry(status = statusCode, path = newResolved, oldPath = oldResolved)
            } else {
                val resolved = CopyPathResolver.resolve(project, "$rootPath/${cols[1]}", settings)
                entries += FileChangeEntry(status = statusCode, path = resolved)
            }
        }
        return entries
    }

    private fun toRepoRelativePath(root: VirtualFile, absolutePath: String): String? {
        val normalizedRoot = root.path.replace('\\', '/').trimEnd('/') + "/"
        val normalizedPath = absolutePath.replace('\\', '/')
        return if (normalizedPath.startsWith(normalizedRoot, ignoreCase = true)) {
            normalizedPath.substring(normalizedRoot.length)
        } else {
            null
        }
    }

    private fun runGit(project: Project, root: VirtualFile, args: List<String>): String? {
        val handler = GitLineHandler(project, root, GitCommand.SHOW)
        handler.setSilent(true)
        handler.setStdoutSuppressed(true)
        // args[0] is "show", skip it since GitCommand.SHOW already supplies it
        handler.addParameters(args.drop(1))
        val result = Git.getInstance().runCommand(handler)
        if (!result.success()) return null
        return result.outputAsJoinedString
    }
}
