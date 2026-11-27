package com.informativecopy.provider

import com.informativecopy.format.DiagnosticEntry
import com.informativecopy.format.InformativeFormatter
import com.informativecopy.settings.InformativeCopySettings
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.ui.SimpleColoredComponent
import java.awt.Component
import java.awt.Container
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class ToolWindowNodeCopyProvider : ContextCopyProvider {

    override val priority: Int = 65

    private val excludedToolWindowIds = setOf(
        "Project",
        "SolutionExplorer"
    )

    override fun createExecution(
        event: AnActionEvent,
        settings: InformativeCopySettings.State,
        detailed: Boolean
    ): CopyExecution? {
        if (event.getData(CommonDataKeys.EDITOR) != null) return null

        val toolWindow = event.getData(PlatformDataKeys.TOOL_WINDOW) ?: return null
        if (toolWindow.id in excludedToolWindowIds) return null

        val project = event.project
        val component = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
        val selectedTexts = extractSelectedComponentTexts(component)
        val navigatables = extractNavigatables(event)
        val descriptors = navigatables.mapNotNull { resolveFileLocation(it) }

        if (descriptors.isEmpty() && selectedTexts.isEmpty()) return null

        val entries = mutableListOf<DiagnosticEntry>()

        if (descriptors.isNotEmpty()) {
            descriptors.forEachIndexed { index, (file, line1Based) ->
                val resolvedPath = CopyPathResolver.resolve(project, file, settings)
                val message = selectedTexts.getOrNull(index)
                    ?: selectedTexts.singleOrNull()
                    ?: ""
                val sourceLine = line1Based?.let { readSourceLine(file, it) }
                entries += DiagnosticEntry(
                    path = resolvedPath,
                    line = line1Based,
                    message = message,
                    sourceLine = sourceLine
                )
            }
        } else {
            val fallbackFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
            val fallbackPath = fallbackFile?.let { CopyPathResolver.resolve(project, it, settings) }
            selectedTexts.forEach { text ->
                entries += DiagnosticEntry(
                    path = fallbackPath,
                    line = null,
                    message = text,
                    sourceLine = null
                )
            }
        }

        if (entries.isEmpty()) return null

        val formatted = InformativeFormatter.formatDiagnostics(entries, settings)
        val twName = toolWindow.stripeTitle.ifBlank { toolWindow.id }
        return CopyExecution.Immediate(
            text = formatted,
            summary = "Copied ${entries.size} ${if (entries.size == 1) "item" else "items"} from $twName"
        )
    }

    private fun extractNavigatables(event: AnActionEvent): List<Navigatable> {
        val array = event.getData(CommonDataKeys.NAVIGATABLE_ARRAY)?.toList()
        if (!array.isNullOrEmpty()) return array
        return listOfNotNull(event.getData(CommonDataKeys.NAVIGATABLE))
    }

    private fun resolveFileLocation(navigatable: Navigatable): Pair<VirtualFile, Int?>? {
        if (navigatable is OpenFileDescriptor) {
            val line1Based = if (navigatable.line >= 0) navigatable.line + 1 else null
            return navigatable.file to line1Based
        }
        return null
    }

    private fun readSourceLine(file: VirtualFile, line1Based: Int): String? {
        val doc = FileDocumentManager.getInstance().getDocument(file) ?: return null
        val line0 = line1Based - 1
        if (line0 < 0 || line0 >= doc.lineCount) return null
        return doc.charsSequence.subSequence(
            doc.getLineStartOffset(line0),
            doc.getLineEndOffset(line0)
        ).toString()
    }

    private fun extractSelectedComponentTexts(component: Component?): List<String> {
        return when (component) {
            is JTree -> {
                val paths = component.selectionPaths ?: return emptyList()
                paths.mapNotNull { path ->
                    val node = path.lastPathComponent
                    val row = component.getRowForPath(path)
                    val rendered = try {
                        val comp = component.cellRenderer?.getTreeCellRendererComponent(
                            component,
                            node,
                            true,
                            component.isExpanded(path),
                            component.model.isLeaf(node),
                            row,
                            true
                        )
                        extractComponentText(comp)
                    } catch (_: Throwable) {
                        null
                    }
                    (rendered ?: nodeToFallbackString(node))?.trim()?.takeIf { it.isNotEmpty() }
                }
            }
            is JList<*> -> {
                val indices = component.selectedIndices
                if (indices.isEmpty()) return emptyList()
                indices.toList().mapNotNull { idx ->
                    val value = component.model.getElementAt(idx)
                    val rendered = try {
                        @Suppress("UNCHECKED_CAST")
                        val renderer = component.cellRenderer as? javax.swing.ListCellRenderer<Any?>
                        val comp = renderer?.getListCellRendererComponent(
                            component as JList<out Any?>,
                            value,
                            idx,
                            true,
                            true
                        )
                        extractComponentText(comp)
                    } catch (_: Throwable) {
                        null
                    }
                    (rendered ?: value?.toString())?.trim()?.takeIf { it.isNotEmpty() }
                }
            }
            is JTable -> {
                val rows = component.selectedRows
                if (rows.isEmpty()) return emptyList()
                rows.toList().mapNotNull { row ->
                    val cells = (0 until component.columnCount).mapNotNull { col ->
                        val value = component.getValueAt(row, col)
                        val rendered = try {
                            val renderer = component.getCellRenderer(row, col)
                            val comp = renderer.getTableCellRendererComponent(component, value, true, true, row, col)
                            extractComponentText(comp)
                        } catch (_: Throwable) {
                            null
                        }
                        (rendered ?: value?.toString())?.trim()?.takeIf { it.isNotEmpty() }
                    }
                    cells.joinToString(" | ").takeIf { it.isNotBlank() }
                }
            }
            else -> emptyList()
        }
    }

    private fun extractComponentText(component: Component?): String? {
        if (component == null) return null
        if (component is SimpleColoredComponent) {
            return component.getCharSequence(false).toString().trim().takeIf { it.isNotEmpty() }
        }
        if (component is JLabel) {
            return component.text?.trim()?.takeIf { it.isNotEmpty() }
        }
        if (component is Container) {
            val parts = component.components.mapNotNull { extractComponentText(it) }
            if (parts.isNotEmpty()) return parts.joinToString(" ")
        }
        return null
    }

    private fun nodeToFallbackString(node: Any?): String? {
        val raw = if (node is DefaultMutableTreeNode) node.userObject?.toString() else node?.toString()
        if (raw.isNullOrBlank()) return null
        // Avoid raw object identity hashes like "com.foo.Bar@1a2b3c"
        if (raw.matches(Regex("^[\\w.$]+@[0-9a-fA-F]+$"))) return null
        return raw
    }
}
