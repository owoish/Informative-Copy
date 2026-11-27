package com.informativecopy.provider

import com.informativecopy.format.InformativeFormatter
import com.informativecopy.settings.InformativeCopySettings
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.contents.FileContent
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager

class DiffEditorCopyProvider : ContextCopyProvider {

    override val priority: Int = 100

    override fun createExecution(
        event: AnActionEvent,
        settings: InformativeCopySettings.State,
        detailed: Boolean
    ): CopyExecution? {
        val diffRequest = event.getData(DiffDataKeys.DIFF_REQUEST)
        val diffViewer = event.getData(DiffDataKeys.DIFF_VIEWER)
        if (diffRequest == null && diffViewer == null) return null

        val editor = event.getData(CommonDataKeys.EDITOR) ?: return null
        val project = event.project

        var resolvedPath: String? = null
        var sideLabel: String? = null

        if (diffRequest is ContentDiffRequest) {
            val contents = diffRequest.contents
            val titles = diffRequest.contentTitles
            val matchedIndex = contents.indexOfFirst { content ->
                content is DocumentContent && content.document == editor.document
            }

            if (matchedIndex >= 0) {
                sideLabel = titles.getOrNull(matchedIndex)?.takeIf { it.isNotBlank() }
                val matchedContent = contents[matchedIndex]
                if (matchedContent is FileContent) {
                    resolvedPath = CopyPathResolver.resolve(project, matchedContent.file, settings)
                }
            }

            if (resolvedPath == null) {
                val anyFile = contents.filterIsInstance<FileContent>().firstOrNull()?.file
                if (anyFile != null) {
                    resolvedPath = CopyPathResolver.resolve(project, anyFile, settings)
                }
            }
        }

        if (resolvedPath == null) {
            val virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
                ?: FileDocumentManager.getInstance().getFile(editor.document)
            resolvedPath = when {
                virtualFile != null -> CopyPathResolver.resolve(project, virtualFile, settings)
                !diffRequest?.title.isNullOrBlank() -> diffRequest!!.title!!
                else -> "Diff"
            }
        }

        val contextTag = if (!sideLabel.isNullOrBlank()) "Diff: $sideLabel" else "Diff"
        val snippets = EditorSelectionExtractor.extractSnippets(
            editor = editor,
            displayPath = resolvedPath,
            settings = settings,
            detailed = detailed,
            contextTag = contextTag
        )
        if (snippets.isEmpty()) return null

        val formatted = InformativeFormatter.formatCodeSnippets(snippets, settings)
        val first = snippets.first()
        val lineSummary = if (first.startLine == first.endLine) "L${first.startLine}" else "L${first.startLine}-${first.endLine}"
        return CopyExecution.Immediate(
            text = formatted,
            summary = "Copied diff snippet: $resolvedPath ($lineSummary)"
        )
    }
}
