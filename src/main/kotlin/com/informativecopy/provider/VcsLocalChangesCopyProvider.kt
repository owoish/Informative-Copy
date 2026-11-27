package com.informativecopy.provider

import com.informativecopy.format.FileChangeEntry
import com.informativecopy.format.InformativeFormatter
import com.informativecopy.settings.InformativeCopySettings
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import java.io.StringWriter
import java.nio.file.Paths

class VcsLocalChangesCopyProvider : ContextCopyProvider {

    override val priority: Int = 75

    override fun createExecution(
        event: AnActionEvent,
        settings: InformativeCopySettings.State,
        detailed: Boolean
    ): CopyExecution? {
        if (event.getData(CommonDataKeys.EDITOR) != null) return null
        val project = event.project ?: return null

        val changes = event.getData(VcsDataKeys.SELECTED_CHANGES)?.toList()
            ?.takeIf { it.isNotEmpty() }
            ?: event.getData(VcsDataKeys.CHANGES)?.toList()
            ?: return null

        if (changes.isEmpty()) return null

        val entries = changes.map { change -> toFileChangeEntry(project, change, settings) }
        val includeDiff = detailed || settings.includeCommitDiffByDefault

        if (!includeDiff) {
            val formatted = InformativeFormatter.formatLocalChanges(entries, null, settings)
            return CopyExecution.Immediate(
                text = formatted,
                summary = "Copied ${entries.size} local ${if (entries.size == 1) "change" else "changes"}"
            )
        }

        return CopyExecution.Async("Informative Copy: Building diff for local changes...") {
            val diffText = buildUnifiedDiff(project, changes)
            val formatted = InformativeFormatter.formatLocalChanges(entries, diffText, settings)
            CopyExecution.Immediate(
                text = formatted,
                summary = "Copied ${entries.size} local ${if (entries.size == 1) "change" else "changes"} (with diff)"
            )
        }
    }

    private fun toFileChangeEntry(
        project: Project,
        change: Change,
        settings: InformativeCopySettings.State
    ): FileChangeEntry {
        val beforePath = change.beforeRevision?.file?.path
        val afterPath = change.afterRevision?.file?.path

        return when (change.type) {
            Change.Type.NEW -> FileChangeEntry(
                status = "A",
                path = CopyPathResolver.resolve(project, afterPath ?: beforePath.orEmpty(), settings)
            )
            Change.Type.DELETED -> FileChangeEntry(
                status = "D",
                path = CopyPathResolver.resolve(project, beforePath ?: afterPath.orEmpty(), settings)
            )
            Change.Type.MOVED -> FileChangeEntry(
                status = "R",
                path = CopyPathResolver.resolve(project, afterPath ?: beforePath.orEmpty(), settings),
                oldPath = beforePath?.let { CopyPathResolver.resolve(project, it, settings) }
            )
            Change.Type.MODIFICATION -> FileChangeEntry(
                status = "M",
                path = CopyPathResolver.resolve(project, afterPath ?: beforePath.orEmpty(), settings)
            )
        }
    }

    private fun buildUnifiedDiff(project: Project, changes: List<Change>): String? {
        return try {
            val basePath = project.basePath?.let { Paths.get(it) } ?: Paths.get(".")
            val patches = ReadAction.compute<List<com.intellij.openapi.diff.impl.patch.FilePatch>, Throwable> {
                IdeaTextPatchBuilder.buildPatch(project, changes, basePath, false)
            }
            if (patches.isEmpty()) return null
            val writer = StringWriter()
            UnifiedDiffWriter.write(project, basePath, patches, writer, "\n", null, null)
            writer.toString().takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }
}
