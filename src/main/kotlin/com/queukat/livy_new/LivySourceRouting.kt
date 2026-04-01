package com.queukat.livy_new

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

enum class LivySourceSnippetMode {
    SELECTION,
    CURRENT_LINE,
    WHOLE_FILE,
    HISTORY,
    STATEMENT,
    MANUAL
}

data class LivySourceOrigin(
    val sourcePath: String,
    val sourceName: String,
    val mode: LivySourceSnippetMode,
    val lineStart: Int? = null,
    val lineEnd: Int? = null
) {
    fun presentableLabel(): String = buildString {
        append(sourceName.ifBlank { sourcePath })
        when {
            lineStart != null && lineEnd != null && lineStart == lineEnd -> append(":$lineStart")
            lineStart != null && lineEnd != null -> append(":$lineStart-$lineEnd")
            else -> {}
        }
        append(" (${modeLabel(mode)})")
    }

    fun navigate(project: Project): Boolean {
        val file = LocalFileSystem.getInstance().findFileByPath(sourcePath) ?: return false
        val line = (lineStart ?: 1).coerceAtLeast(1) - 1
        val descriptor = OpenFileDescriptor(project, file, line, 0)
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
        return true
    }

    private fun modeLabel(mode: LivySourceSnippetMode): String = when (mode) {
        LivySourceSnippetMode.SELECTION -> "selection"
        LivySourceSnippetMode.CURRENT_LINE -> "line"
        LivySourceSnippetMode.WHOLE_FILE -> "file"
        LivySourceSnippetMode.HISTORY -> "history"
        LivySourceSnippetMode.STATEMENT -> "statement"
        LivySourceSnippetMode.MANUAL -> "manual"
    }
}

data class LivyResolvedSnippet(
    val text: String,
    val mode: LivySourceSnippetMode,
    val lineStart: Int,
    val lineEnd: Int
)

data class LivyWorkSurfaceRequest(
    val snippet: String = "",
    val origin: LivySourceOrigin? = null,
    val contentMode: LivyWorkSurfaceContentMode = LivyWorkSurfaceContentMode.NONE,
    val autorun: Boolean = false
)

enum class LivyWorkSurfaceContentMode {
    NONE,
    INSERT,
    REPLACE
}

object LivySourceOrigins {
    private val ORIGIN_KEY = Key.create<LivySourceOrigin>("livy.console.source.origin")

    fun attach(file: VirtualFile, origin: LivySourceOrigin?) {
        file.putUserData(ORIGIN_KEY, origin)
    }

    fun resolve(file: VirtualFile): LivySourceOrigin? = file.getUserData(ORIGIN_KEY)
}

object LivyWorkSurfaceRequests {
    private val REQUEST_KEY = Key.create<LivyWorkSurfaceRequest>("livy.console.pending.work.surface.request")

    fun attach(file: VirtualFile, request: LivyWorkSurfaceRequest?) {
        file.putUserData(REQUEST_KEY, request)
    }

    fun consume(file: VirtualFile): LivyWorkSurfaceRequest? {
        val request = file.getUserData(REQUEST_KEY)
        if (request != null) {
            file.putUserData(REQUEST_KEY, null)
        }
        return request
    }
}

fun resolveSelectionOrCurrentLine(
    documentText: String,
    selectionStart: Int,
    selectionEnd: Int,
    caretOffset: Int
): LivyResolvedSnippet {
    if (selectionEnd > selectionStart) {
        val safeStart = selectionStart.coerceIn(0, documentText.length)
        val safeEnd = selectionEnd.coerceIn(safeStart, documentText.length)
        return LivyResolvedSnippet(
            text = documentText.substring(safeStart, safeEnd),
            mode = LivySourceSnippetMode.SELECTION,
            lineStart = lineNumberAt(documentText, safeStart),
            lineEnd = lineNumberAt(documentText, (safeEnd - 1).coerceAtLeast(safeStart))
        )
    }

    val safeOffset = caretOffset.coerceIn(0, documentText.length)
    val lineStartOffset = lineStartOffset(documentText, safeOffset)
    val lineEndOffset = lineEndOffset(documentText, safeOffset)
    val lineNumber = lineNumberAt(documentText, safeOffset)
    return LivyResolvedSnippet(
        text = documentText.substring(lineStartOffset, lineEndOffset),
        mode = LivySourceSnippetMode.CURRENT_LINE,
        lineStart = lineNumber,
        lineEnd = lineNumber
    )
}

fun resolveWholeFile(documentText: String): LivyResolvedSnippet =
    LivyResolvedSnippet(
        text = documentText,
        mode = LivySourceSnippetMode.WHOLE_FILE,
        lineStart = 1,
        lineEnd = lineCount(documentText).coerceAtLeast(1)
    )

fun resolveSqlStatementAtCaret(
    documentText: String,
    caretOffset: Int
): LivyResolvedSnippet? {
    if (documentText.isBlank()) return null
    val safeCaret = caretOffset.coerceIn(0, documentText.length)
    val statements = splitSqlStatements(documentText)
    val statement = statements.firstOrNull { safeCaret >= it.rawStart && safeCaret <= it.rawEndExclusive }
        ?: statements.lastOrNull { safeCaret >= it.rawStart }
        ?: return null

    return LivyResolvedSnippet(
        text = documentText.substring(statement.contentStart, statement.contentEndExclusive),
        mode = LivySourceSnippetMode.STATEMENT,
        lineStart = lineNumberAt(documentText, statement.contentStart),
        lineEnd = lineNumberAt(
            documentText,
            (statement.contentEndExclusive - 1).coerceAtLeast(statement.contentStart)
        )
    )
}

private fun lineNumberAt(text: String, offset: Int): Int {
    if (text.isEmpty()) return 1
    val safeOffset = offset.coerceIn(0, text.length)
    var line = 1
    for (index in 0 until safeOffset) {
        if (text[index] == '\n') line++
    }
    return line
}

private fun lineStartOffset(text: String, offset: Int): Int {
    var index = offset.coerceIn(0, text.length)
    while (index > 0 && text[index - 1] != '\n') {
        index--
    }
    return index
}

private fun lineEndOffset(text: String, offset: Int): Int {
    var index = offset.coerceIn(0, text.length)
    while (index < text.length && text[index] != '\n') {
        index++
    }
    return index
}

private fun lineCount(text: String): Int =
    if (text.isEmpty()) 1 else text.count { it == '\n' } + 1

private data class SqlStatementRange(
    val rawStart: Int,
    val rawEndExclusive: Int,
    val contentStart: Int,
    val contentEndExclusive: Int
)

private fun splitSqlStatements(text: String): List<SqlStatementRange> {
    val statements = mutableListOf<SqlStatementRange>()
    var rawStart = 0

    fun addStatement(rawEndExclusive: Int) {
        var contentStart = rawStart
        while (contentStart < rawEndExclusive && text[contentStart].isWhitespace()) {
            contentStart++
        }
        var contentEndExclusive = rawEndExclusive
        while (contentEndExclusive > contentStart && text[contentEndExclusive - 1].isWhitespace()) {
            contentEndExclusive--
        }
        if (contentEndExclusive > contentStart) {
            statements += SqlStatementRange(rawStart, rawEndExclusive, contentStart, contentEndExclusive)
        }
    }

    text.forEachIndexed { index, char ->
        if (char == ';') {
            addStatement(index)
            rawStart = index + 1
        }
    }
    addStatement(text.length)

    return statements
}
