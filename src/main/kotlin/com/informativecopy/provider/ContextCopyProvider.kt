package com.informativecopy.provider

import com.informativecopy.format.InformativeFormatter
import com.informativecopy.settings.InformativeCopySettings
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

sealed class CopyExecution {
    data class Immediate(val text: String, val summary: String) : CopyExecution()
    data class Async(val taskTitle: String, val compute: () -> Immediate?) : CopyExecution()
}

interface ContextCopyProvider {
    val priority: Int

    fun createExecution(
        event: AnActionEvent,
        settings: InformativeCopySettings.State,
        detailed: Boolean
    ): CopyExecution?

    companion object {
        val EP_NAME: ExtensionPointName<ContextCopyProvider> =
            ExtensionPointName.create("com.informativecopy.contextProvider")
    }
}

object CopyPathResolver {
    fun resolve(project: Project?, file: VirtualFile, settings: InformativeCopySettings.State): String {
        return InformativeFormatter.normalizePath(
            fullPath = file.path,
            basePath = project?.basePath,
            style = settings.pathStyle
        )
    }

    fun resolve(project: Project?, rawPath: String, settings: InformativeCopySettings.State): String {
        return InformativeFormatter.normalizePath(
            fullPath = rawPath,
            basePath = project?.basePath,
            style = settings.pathStyle
        )
    }
}
