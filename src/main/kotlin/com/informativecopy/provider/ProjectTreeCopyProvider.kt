package com.informativecopy.provider

import com.informativecopy.format.FileEntry
import com.informativecopy.format.InformativeFormatter
import com.informativecopy.settings.InformativeCopySettings
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.VfsUtilCore

class ProjectTreeCopyProvider : ContextCopyProvider {

    override val priority: Int = 50

    override fun createExecution(
        event: AnActionEvent,
        settings: InformativeCopySettings.State,
        detailed: Boolean
    ): CopyExecution? {
        if (event.getData(CommonDataKeys.EDITOR) != null) return null

        val files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList()
            ?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(event.getData(CommonDataKeys.VIRTUAL_FILE))

        if (files.isEmpty()) return null

        val project = event.project
        val entries = files.map { file ->
            val resolved = CopyPathResolver.resolve(project, file, settings)
            val content = if (detailed && files.size == 1 && !file.isDirectory && !file.fileType.isBinary && file.length <= 128_000) {
                try {
                    VfsUtilCore.loadText(file)
                } catch (_: Throwable) {
                    null
                }
            } else {
                null
            }
            FileEntry(
                path = resolved,
                isDirectory = file.isDirectory,
                content = content
            )
        }

        val formatted = InformativeFormatter.formatFiles(entries, settings)
        val summary = if (entries.size == 1) {
            val suffix = if (entries.first().content != null) " (with contents)" else ""
            "Copied path: ${entries.first().path}$suffix"
        } else {
            "Copied ${entries.size} file paths"
        }

        return CopyExecution.Immediate(text = formatted, summary = summary)
    }
}
