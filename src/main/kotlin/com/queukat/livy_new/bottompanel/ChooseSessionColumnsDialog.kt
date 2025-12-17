package com.queukat.livy_new.bottompanel

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.panel
import javax.swing.JCheckBox
import javax.swing.JComponent

class ChooseSessionColumnsDialog(
    private val allColumns: List<SessionColumn>,
    currentVisibleColumnIds: List<String>
) : DialogWrapper(true) {

    private val checkboxes = LinkedHashMap<String, JCheckBox>()

    var chosenColumnIds: List<String> = currentVisibleColumnIds

    init {
        title = "Choose Session Columns"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val current = currentVisibleColumnIdsSet()

        return panel {
            row { label("Select columns to show in Sessions table:") }
            group("Columns") {
                allColumns.forEach { col ->
                    row {
                        val cb = JCheckBox(col.title, current.contains(col.id))
                        checkboxes[col.id] = cb
                        cell(cb)
                    }
                }
            }
        }
    }

    override fun doOKAction() {
        val selected = allColumns
            .map { it.id }
            .filter { checkboxes[it]?.isSelected == true }

        if (selected.isEmpty()) {
            Messages.showWarningDialog(
                contentPanel,
                "At least one column must be selected.",
                "Choose Session Columns"
            )
            return
        }

        chosenColumnIds = selected
        super.doOKAction()
    }

    private fun currentVisibleColumnIdsSet(): Set<String> = chosenColumnIds.toSet()
}
