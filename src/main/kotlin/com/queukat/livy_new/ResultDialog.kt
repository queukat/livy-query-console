package com.queukat.livy_new

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JTextArea

/**
 * A simple dialog to show short textual results (e.g. from one-time run).
 */
class ResultDialog(result: String) : DialogWrapper(true) {

    private val textArea = JTextArea(result).apply {
        isEditable = false
        lineWrap = false
        wrapStyleWord = false
    }

    init {
        title = "Execution Result"
        init()
        setOKButtonText("Close")
    }

    override fun createCenterPanel(): JComponent {
        val scroll = JScrollPane(textArea).apply {
            preferredSize = Dimension(900, 500)
        }

        return panel {
            row {
                scrollCell(scroll)
                    .resizableColumn()
                    .align(Align.FILL)
            }.resizableRow()
        }
    }

    override fun createActions(): Array<Action> = arrayOf(okAction)
}
