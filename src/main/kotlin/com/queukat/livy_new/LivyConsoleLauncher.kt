package com.queukat.livy_new

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.queukat.livy_new.editor.LivyConsoleFileEditor
import com.queukat.livy_new.editor.LivyConsoleFileType

fun openLivyConsole(
    project: Project,
    executionTarget: LivyExecutionTarget,
    initialCode: String = ""
) {
    openLivyWorkSurface(
        project = project,
        executionTarget = executionTarget,
        request = LivyWorkSurfaceRequest(
            snippet = initialCode,
            contentMode = if (initialCode.isBlank()) LivyWorkSurfaceContentMode.NONE else LivyWorkSurfaceContentMode.REPLACE,
            autorun = false
        )
    )
}

fun openLivyWorkSurface(
    project: Project,
    executionTarget: LivyExecutionTarget,
    request: LivyWorkSurfaceRequest = LivyWorkSurfaceRequest()
): VirtualFile {
    val file = findOrCreateLivyWorkFile(project, executionTarget)
    if (LivyExecutionTargets.attached(file) == null) {
        LivyExecutionTargets.attach(file, executionTarget)
    }
    if (request.origin != null) LivySourceOrigins.attach(file, request.origin)
    LivyWorkSurfaceRequests.attach(file, request)

    val editors = FileEditorManager.getInstance(project).openFile(file, true)
    editors
        .filterIsInstance<LivyConsoleFileEditor>()
        .firstOrNull()
        ?.apply {
            FileEditorManager.getInstance(project).setSelectedEditor(file, editorTypeId())
            LivyWorkSurfaceRequests.consume(file)?.let { pendingRequest ->
                applyWorkSurfaceRequest(pendingRequest)
            }
        }

    return file
}

internal fun findOrCreateLivyWorkFile(project: Project, executionTarget: LivyExecutionTarget): VirtualFile {
    val existing = FileEditorManager.getInstance(project).openFiles.firstOrNull { openFile ->
        openFile.extension == LivyConsoleFileType.EXTENSION &&
            LivyExecutionTargets.attached(openFile)?.let { attached ->
                attached.profileId == executionTarget.profileId &&
                    attached.workFileMode() == executionTarget.workFileMode()
            } == true
    }
    if (existing != null) return existing

    val fileType = FileTypeManager.getInstance().getFileTypeByExtension(LivyConsoleFileType.EXTENSION)
    val mode = executionTarget.workFileMode()
    return LightVirtualFile(
        "${sanitizeWorkFileComponent(executionTarget.profileName)}-${mode.pathSuffix()}.${LivyConsoleFileType.EXTENSION}",
        fileType,
        ""
    )
}

internal fun LivyConsoleFileEditor.editorTypeId(): String = "LivyConsoleFileEditor"

internal fun workFilePath(executionTarget: LivyExecutionTarget): String {
    val shortProfileId = executionTarget.profileId.take(8).ifBlank { "default" }
    val safeName = sanitizeWorkFileComponent(executionTarget.profileName.ifBlank { "profile" })
    val mode = executionTarget.workFileMode().pathSuffix()
    return "livy/$shortProfileId/$safeName-$mode.${LivyConsoleFileType.EXTENSION}"
}

internal fun profileIdHintFromWorkFile(file: VirtualFile, settings: LivyPluginSettings.PluginState): String? {
    val path = file.path.replace('\\', '/')
    val match = Regex("/livy/([A-Za-z0-9_-]{4,})/").find(path) ?: return null
    val hint = match.groupValues[1]
    return settings.profiles.firstOrNull { it.id.startsWith(hint) }?.id
}

internal fun workFileModeHintFromWorkFile(file: VirtualFile): LivyWorkFileMode? {
    val path = file.path.replace('\\', '/')
    val fileName = path.substringAfterLast('/')
    val suffix = Regex("-([A-Za-z]+)\\.${LivyConsoleFileType.EXTENSION}$")
        .find(fileName)
        ?.groupValues
        ?.getOrNull(1)
        ?: return null
    return LivyWorkFileMode.fromPathSuffix(suffix)
}

private fun sanitizeWorkFileComponent(value: String): String =
    value
        .trim()
        .ifBlank { "profile" }
        .replace(Regex("[^A-Za-z0-9._-]+"), "-")
        .replace(Regex("-+"), "-")
        .trim('-')
        .ifBlank { "profile" }
