package com.informativecopy.action

import com.informativecopy.provider.ContextCopyProvider
import com.informativecopy.provider.CopyExecution
import com.informativecopy.settings.InformativeCopySettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import java.awt.datatransfer.StringSelection

abstract class BaseInformativeCopyAction(
    private val detailed: Boolean
) : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val settings = InformativeCopySettings.getInstance().state.copy()
        val providers = ContextCopyProvider.EP_NAME.extensionList.sortedByDescending { it.priority }

        var execution: CopyExecution? = null
        for (provider in providers) {
            execution = provider.createExecution(e, settings, detailed)
            if (execution != null) break
        }

        val project = e.project
        if (execution == null) {
            notifyStatus(project, "Informative Copy: Nothing supported in current focus", settings, isWarning = true)
            return
        }

        when (execution) {
            is CopyExecution.Immediate -> {
                copyAndNotify(project, execution, settings)
            }
            is CopyExecution.Async -> {
                ProgressManager.getInstance().run(object : Task.Backgroundable(project, execution.taskTitle, true) {
                    override fun run(indicator: ProgressIndicator) {
                        val result = execution.compute()
                        ApplicationManager.getApplication().invokeLater {
                            if (result != null) {
                                copyAndNotify(project, result, settings)
                            } else {
                                notifyStatus(
                                    project,
                                    "Informative Copy: Failed to extract details",
                                    settings,
                                    isWarning = true
                                )
                            }
                        }
                    }
                })
            }
        }
    }

    private fun copyAndNotify(
        project: Project?,
        result: CopyExecution.Immediate,
        settings: InformativeCopySettings.State
    ) {
        CopyPasteManager.getInstance().setContents(StringSelection(result.text))
        notifyStatus(project, result.summary, settings, isWarning = false)
    }

    private fun notifyStatus(
        project: Project?,
        message: String,
        settings: InformativeCopySettings.State,
        isWarning: Boolean
    ) {
        if (project != null) {
            WindowManager.getInstance().getStatusBar(project)?.info = message
        }
        if (settings.showNotification) {
            val type = if (isWarning) NotificationType.WARNING else NotificationType.INFORMATION
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Informative Copy")
                .createNotification(message, type)
                .notify(project)
        }
    }
}

class InformativeCopyAction : BaseInformativeCopyAction(detailed = false)

class InformativeCopyDetailedAction : BaseInformativeCopyAction(detailed = true)
