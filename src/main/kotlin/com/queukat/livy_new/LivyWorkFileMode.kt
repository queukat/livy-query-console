package com.queukat.livy_new

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.FileTypes

enum class LivyWorkFileMode(
    val displayName: String,
    private val preferredExtensions: List<String>,
    val statementAwareRun: Boolean,
    val persistedKind: String,
    private val suffix: String
) {
    SQL("SQL", listOf("sql"), true, "sql", "sql"),
    PYTHON("PySpark / Python", listOf("py"), false, "pyspark", "python"),
    PLAIN("Spark / Plain Text", emptyList(), false, "spark", "spark");

    fun resolveEditorFileType(): FileType {
        val manager = FileTypeManager.getInstance()
        return preferredExtensions
            .asSequence()
            .map { manager.getFileTypeByExtension(it) }
            .firstOrNull { it != FileTypes.UNKNOWN && it != FileTypes.PLAIN_TEXT }
            ?: FileTypes.PLAIN_TEXT
    }

    fun pathSuffix(): String = suffix

    companion object {
        fun fromExecutionKind(kind: String): LivyWorkFileMode =
            when (kind.trim().lowercase()) {
                "sql" -> SQL
                "python", "pyspark" -> PYTHON
                else -> PLAIN
            }

        fun fromPathSuffix(suffix: String): LivyWorkFileMode? =
            entries.firstOrNull { it.pathSuffix() == suffix.trim().lowercase() }
    }
}

fun LivyExecutionTarget.workFileMode(): LivyWorkFileMode =
    LivyWorkFileMode.fromExecutionKind(settingsSnapshot.kind)
