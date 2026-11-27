package com.informativecopy.provider

import com.informativecopy.format.InformativeFormatter
import com.informativecopy.settings.InformativeCopySettings
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager

class ConsoleCopyProvider : ContextCopyProvider {

    override val priority: Int = 85

    override fun createExecution(
        event: AnActionEvent,
        settings: InformativeCopySettings.State,
        detailed: Boolean
    ): CopyExecution? {
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return null
        val isConsole = event.getData(LangDataKeys.CONSOLE_VIEW) != null
        val hasFile = event.getData(CommonDataKeys.VIRTUAL_FILE) != null ||
            FileDocumentManager.getInstance().getFile(editor.document) != null

        if (!isConsole && hasFile) return null

        val selectionModel = editor.selectionModel
        val text = if (selectionModel.hasSelection()) {
            selectionModel.selectedText.orEmpty()
        } else {
            val doc = editor.document
            if (doc.lineCount == 0) return null
            val line0 = doc.getLineNumber(editor.caretModel.offset)
            doc.charsSequence.subSequence(doc.getLineStartOffset(line0), doc.getLineEndOffset(line0)).toString()
        }

        if (text.isBlank()) return null

        val title = event.getData(PlatformDataKeys.TOOL_WINDOW)?.stripeTitle
            ?.takeIf { it.isNotBlank() }
            ?: "Console Output"

        val formatted = InformativeFormatter.formatConsole(title, text)
        val lineCount = text.replace("\r\n", "\n").trimEnd('\n').split('\n').size
        return CopyExecution.Immediate(
            text = formatted,
            summary = "Copied $title ($lineCount ${if (lineCount == 1) "line" else "lines"})"
        )
    }
}
