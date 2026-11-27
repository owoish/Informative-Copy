package com.informativecopy.provider

import com.informativecopy.format.CodeSnippet
import com.informativecopy.settings.InformativeCopySettings
import com.intellij.openapi.editor.Editor
import kotlin.math.max
import kotlin.math.min

object EditorSelectionExtractor {

    fun extractSnippets(
        editor: Editor,
        displayPath: String,
        settings: InformativeCopySettings.State,
        detailed: Boolean,
        contextTag: String? = null
    ): List<CodeSnippet> {
        val document = editor.document
        if (document.lineCount == 0) return emptyList()

        val carets = editor.caretModel.allCarets
        val snippets = mutableListOf<CodeSnippet>()

        for (caret in carets) {
            if (caret.hasSelection()) {
                val startOffset = caret.selectionStart
                val endOffset = caret.selectionEnd
                val startLine0 = document.getLineNumber(startOffset)
                var endLine0 = document.getLineNumber(endOffset)

                var selectedText = caret.selectedText ?: ""
                if (endOffset > startOffset &&
                    endLine0 > startLine0 &&
                    endOffset == document.getLineStartOffset(endLine0)
                ) {
                    endLine0 -= 1
                    selectedText = selectedText.removeSuffix("\r\n").removeSuffix("\n")
                }

                snippets += CodeSnippet(
                    path = displayPath,
                    startLine = startLine0 + 1,
                    endLine = endLine0 + 1,
                    text = selectedText,
                    contextTag = contextTag
                )
            } else {
                val line0 = document.getLineNumber(caret.offset)
                if (detailed && settings.contextLinesWhenDetailed > 0) {
                    val startLine0 = max(0, line0 - settings.contextLinesWhenDetailed)
                    val endLine0 = min(document.lineCount - 1, line0 + settings.contextLinesWhenDetailed)
                    val text = document.charsSequence.subSequence(
                        document.getLineStartOffset(startLine0),
                        document.getLineEndOffset(endLine0)
                    ).toString()

                    snippets += CodeSnippet(
                        path = displayPath,
                        startLine = startLine0 + 1,
                        endLine = endLine0 + 1,
                        text = text,
                        contextTag = contextTag
                    )
                } else {
                    val lineText = document.charsSequence.subSequence(
                        document.getLineStartOffset(line0),
                        document.getLineEndOffset(line0)
                    ).toString()

                    snippets += CodeSnippet(
                        path = displayPath,
                        startLine = line0 + 1,
                        endLine = line0 + 1,
                        text = lineText,
                        contextTag = contextTag
                    )
                }
            }
        }

        return snippets
    }
}
