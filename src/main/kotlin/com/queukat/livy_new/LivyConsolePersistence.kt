package com.queukat.livy_new

fun resolveInitialConsoleText(
    fileText: String,
    savedDraft: String,
    localHistoryEnabled: Boolean
): String = when {
    fileText.isNotBlank() -> fileText
    localHistoryEnabled && savedDraft.isNotBlank() -> savedDraft
    else -> fileText
}

fun historyStatusFor(statement: Statement): String =
    statement.output?.status?.trim().takeIf { !it.isNullOrBlank() }
        ?: statement.state?.trim().takeIf { !it.isNullOrBlank() }
        ?: "unknown"
