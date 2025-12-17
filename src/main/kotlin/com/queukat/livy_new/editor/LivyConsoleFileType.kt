package com.queukat.livy_new.editor

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

class LivyConsoleFileType : LanguageFileType(LivyConsoleLanguage) {

    override fun getName(): String = DISPLAY_NAME
    override fun getDescription(): String = "Livy Console virtual file"
    override fun getDefaultExtension(): String = EXTENSION
    override fun getIcon(): Icon? = IconLoader.getIcon("/icons/livyToolWindow.svg", LivyConsoleFileType::class.java)

    companion object {
        const val DISPLAY_NAME = "Livy Console"
        const val EXTENSION = "livyconsole"
    }
}
