package com.informativecopy.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toNullableProperty

class InformativeCopyConfigurable : BoundConfigurable("Informative Copy") {

    private val settings: InformativeCopySettings.State
        get() = InformativeCopySettings.getInstance().state

    override fun createPanel(): DialogPanel = panel {
        group("Location & Code Format") {
            row("File path format:") {
                comboBox(
                    items = PathStyle.entries,
                    renderer = SimpleListCellRenderer.create("") { it.displayName }
                ).bindItem(settings::pathStyle.toNullableProperty())
            }
            row("Line number format:") {
                comboBox(
                    items = LineStyle.entries,
                    renderer = SimpleListCellRenderer.create("") { it.displayName }
                ).bindItem(settings::lineStyle.toNullableProperty())
            }
            row("Single-line code format:") {
                comboBox(
                    items = SingleLineStyle.entries,
                    renderer = SimpleListCellRenderer.create("") { it.displayName }
                ).bindItem(settings::singleLineStyle.toNullableProperty())
            }
            row {
                checkBox("Trim leading/trailing whitespace on single-line snippets")
                    .bindSelected(settings::trimSingleLine)
            }
            row {
                checkBox("Auto-dedent multi-line code blocks")
                    .bindSelected(settings::dedentMultiLine)
            }
        }

        group("Commits, Diffs & Detailed Copy") {
            row {
                checkBox("Include unified diff when copying Git commits / local changes with primary shortcut")
                    .bindSelected(settings::includeCommitDiffByDefault)
            }
            row("Max diff / file lines (0 = unlimited):") {
                intTextField(0..10000)
                    .bindIntText(settings::maxDiffLines)
            }
            row("Surrounding context lines (Detailed copy with no selection):") {
                intTextField(0..50)
                    .bindIntText(settings::contextLinesWhenDetailed)
            }
        }

        group("Feedback") {
            row {
                checkBox("Show popup notification after copying")
                    .bindSelected(settings::showNotification)
            }
        }
    }
}
