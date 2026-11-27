package com.informativecopy.provider

import com.informativecopy.format.InformativeFormatter
import com.informativecopy.settings.InformativeCopySettings
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager

class CodeEditorCopyProvider : ContextCopyProvider {

    override val priority: Int = 90

    override fun createExecution(
        event: AnActionEvent,
        settings: InformativeCopySettings.State,
        detailed: Boolean
    ): CopyExecution? {
        if (event.getData(LangDataKeys.CONSOLE_VIEW) != null) return null
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return null
        val virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
            ?: FileDocumentManager.getInstance().getFile(editor.document)
            ?: return null

        val displayPath = CopyPathResolver.resolve(event.project, virtualFile, settings)
        val snippets = EditorSelectionExtractor.extractSnippets(
            editor = editor,
            displayPath = displayPath,
            settings = settings,
            detailed = detailed
        )
        if (snippets.isEmpty()) return null

        val formatted = InformativeFormatter.formatCodeSnippets(snippets, settings)
        val first = snippets.first()
        val lineSummary = if (first.startLine == first.endLine) "L${first.startLine}" else "L${first.startLine}-${first.endLine}"
        val extra = if (snippets.size > 1) " (+${snippets.size - 1} more)" else ""

        return CopyExecution.Immediate(
            text = formatted,
            summary = "Copied $displayPath ($lineSummary)$extra"
        )
    }
}
