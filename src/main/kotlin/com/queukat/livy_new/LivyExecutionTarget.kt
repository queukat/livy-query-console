package com.queukat.livy_new

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile

data class LivyExecutionTarget(
    val profileId: String,
    val profileName: String,
    val baseUrl: String,
    val settingsSnapshot: LivyPluginSettings.ConnectionProfileState
) {
    companion object {
        fun capture(
            settings: LivyPluginSettings.PluginState,
            profileId: String? = settings.activeProfileId,
            kindOverride: String? = null
        ): LivyExecutionTarget {
            val profile = settings.requireProfile(profileId)
            val snapshot = profile.snapshot()
            val normalizedBaseUrl = LivyManagedSessions.normalizeServerUrl(snapshot.livyServerUrl)
            snapshot.livyServerUrl = normalizedBaseUrl
            kindOverride
                ?.trim()
                ?.ifBlank { null }
                ?.let { snapshot.kind = it }
            return LivyExecutionTarget(
                profileId = profile.id,
                profileName = profile.displayName,
                baseUrl = normalizedBaseUrl,
                settingsSnapshot = snapshot
            )
        }

        fun captureCurrent(): LivyExecutionTarget =
            capture(LivyPluginSettings.getInstance().pluginState)
    }
}

object LivyExecutionTargets {
    private val TARGET_KEY = Key.create<LivyExecutionTarget>("livy.console.execution.target")

    fun attach(file: VirtualFile, target: LivyExecutionTarget) {
        file.putUserData(TARGET_KEY, target)
    }

    fun attached(file: VirtualFile): LivyExecutionTarget? = file.getUserData(TARGET_KEY)

    fun resolve(file: VirtualFile): LivyExecutionTarget {
        val existing = attached(file)
        if (existing != null) {
            return existing
        }

        val settings = LivyPluginSettings.getInstance().pluginState
        val hintedProfileId = profileIdHintFromWorkFile(file, settings)
        val hintedKind = workFileModeHintFromWorkFile(file)?.persistedKind
        val captured = LivyExecutionTarget.capture(
            settings,
            hintedProfileId ?: settings.activeProfileId,
            hintedKind
        )
        attach(file, captured)
        return captured
    }
}

data class LivySessionRef(
    val baseUrl: String,
    val sessionId: Int
)

data class LivyStatementRef(
    val baseUrl: String,
    val sessionId: Int,
    val statementId: Int
)
