package com.queukat.livy_new

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

data class LivySourceRequest(
    val snippet: String,
    val origin: LivySourceOrigin
)

fun resolveSelectionOrCurrentLineRequest(e: AnActionEvent): LivySourceRequest? {
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return null
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
    return resolveSelectionOrCurrentLineRequest(editor, file)
}

fun resolveWholeFileRequest(e: AnActionEvent): LivySourceRequest? {
    val editor = e.getData(CommonDataKeys.EDITOR)
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    if (editor != null && file != null) {
        return resolveWholeFileRequest(editor.document.text, file)
    }

    val project = e.project ?: return null
    val selectedFile = selectedVirtualFile(e, project) ?: return null
    return resolveWholeFileRequest(readVirtualFileText(selectedFile), selectedFile)
}

fun resolveSelectionOrCurrentLineRequest(editor: Editor, file: VirtualFile): LivySourceRequest {
    val documentText = editor.document.text
    val selectionModel = editor.selectionModel
    val resolved = resolveSelectionOrCurrentLine(
        documentText = documentText,
        selectionStart = selectionModel.selectionStart,
        selectionEnd = selectionModel.selectionEnd,
        caretOffset = editor.caretModel.offset
    )
    return LivySourceRequest(
        snippet = resolved.text,
        origin = LivySourceOrigin(
            sourcePath = file.path,
            sourceName = file.presentableName,
            mode = resolved.mode,
            lineStart = resolved.lineStart,
            lineEnd = resolved.lineEnd
        )
    )
}

fun resolveWholeFileRequest(documentText: String, file: VirtualFile): LivySourceRequest {
    val resolved = resolveWholeFile(documentText)
    return LivySourceRequest(
        snippet = resolved.text,
        origin = LivySourceOrigin(
            sourcePath = file.path,
            sourceName = file.presentableName,
            mode = resolved.mode,
            lineStart = resolved.lineStart,
            lineEnd = resolved.lineEnd
        )
    )
}

private fun selectedVirtualFile(e: AnActionEvent, project: Project): VirtualFile? {
    val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
    return when {
        !files.isNullOrEmpty() -> files.firstOrNull { !it.isDirectory }
        else -> e.getData(CommonDataKeys.VIRTUAL_FILE)
    }
}

private fun readVirtualFileText(file: VirtualFile): String =
    try {
        String(file.contentsToByteArray(), Charsets.UTF_8)
    } catch (_: Exception) {
        ""
    }
