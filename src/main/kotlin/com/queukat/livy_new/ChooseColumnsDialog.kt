package com.queukat.livy_new

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.panel
import javax.swing.JCheckBox
import javax.swing.JComponent

/**
 * Dialog to let the user pick columns via checkboxes.
 * On OK, the chosen columns are stored in [chosenColumns].
 */
class ChooseColumnsDialog(
    private val allColumns: Array<String>,
    currentVisibleColumns: Array<String>
) : DialogWrapper(true) {

    private val checkboxes = mutableListOf<JCheckBox>()
    var chosenColumns: Array<String> = currentVisibleColumns.copyOf()
        private set

    init {
        title = "Choose Columns"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val initiallySelected = chosenColumns.toSet()

        return panel {
            row {
                label("Select columns:")
            }

            allColumns.forEach { col ->
                row {
                    val cell = checkBox(col)
                    cell.component.isSelected = col in initiallySelected
                    checkboxes.add(cell.component)
                }
            }
        }
    }

    override fun doOKAction() {
        val selected = checkboxes
            .filter { it.isSelected }
            .map { it.text }
            .toTypedArray()

        if (selected.isEmpty()) {
            Messages.showWarningDialog(
                contentPanel,
                "At least one column must be selected",
                "Choose Columns"
            )
            return
        }

        chosenColumns = selected
        super.doOKAction()
    }
}
