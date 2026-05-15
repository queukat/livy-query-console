package com.queukat.livy_new

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import java.util.UUID

open class LivyConnectionRuntimeSettings {
    var livyServerUrl: String = LivyPluginSettings.DEFAULT_SERVER_URL
    var maxSessions: Int = 4
    var sessionManagementStrategy: String = "reuse"
    var kind: String = ""
    var proxyUser: String = ""
    var jars: String = ""
    var pyFiles: String = ""
    var files: String = ""
    var driverMemory: String = "1g"
    var driverCores: Int = 1
    var executorMemory: String = "1g"
    var executorCores: Int = 1
    var numExecutors: Int = 2
    var archives: String = ""
    var queue: String = ""
    var name: String = ""
    var conf: String = ""
    var heartbeatTimeoutInSecond: Int = 60
    var ttl: String = ""
    var killOldestIfFull: Boolean = false
}

@Service(Service.Level.APP)
@State(name = "LivyPluginSettings", storages = [Storage("LivyPluginSettings.xml")])
class LivyPluginSettings : PersistentStateComponent<LivyPluginSettings.PluginState> {

    var pluginState: PluginState = PluginState()

    override fun getState(): PluginState = pluginState

    override fun loadState(state: PluginState) {
        XmlSerializerUtil.copyBean(state, pluginState)
        if (pluginState.sessionTableColumns.isEmpty()) {
            pluginState.sessionTableColumns = mutableListOf(
                "id", "appId", "owner", "kind", "state", "log"
            )
        }
        pluginState.managedSessions = pluginState.managedSessions
            .filter { it.sessionId >= 0 && it.serverUrl.isNotBlank() && it.fingerprint.isNotBlank() }
            .distinctBy { it.serverUrl to it.sessionId }
            .toMutableList()
        pluginState.normalizeProfilesAfterLoad()
        pluginState.normalizeLocalConsoleData()
        pluginState.normalizeSessionsPanelState()
        pluginState.syncLegacyFieldsFromActiveProfile()
    }

    class ManagedSessionState {
        var sessionId: Int = -1
        var serverUrl: String = ""
        var fingerprint: String = ""
        var createdAtMs: Long = 0L
    }

    class ConnectionProfileState : LivyConnectionRuntimeSettings() {
        var id: String = ""
        var displayName: String = ""
    }

    class ConsoleHistoryEntryState {
        var id: String = ""
        var createdAtMs: Long = 0L
        var profileId: String = ""
        var profileName: String = ""
        var baseUrl: String = ""
        var languageOrKind: String = ""
        var snippet: String = ""
        var snippetTruncated: Boolean = false
        var status: String = ""
        var sessionId: Int = -1
        var statementId: Int = -1
    }

    class ConsoleDraftState {
        var profileId: String = ""
        var languageOrKind: String = ""
        var text: String = ""
        var updatedAtMs: Long = 0L
    }

    class PluginState : LivyConnectionRuntimeSettings() {
        /**
         * Legacy single-profile fields kept only for migration/backward-compatibility mirrors.
         * New runtime/configuration code should resolve through [profiles].
         */
        var profiles: MutableList<ConnectionProfileState> = mutableListOf()
        var activeProfileId: String = ""
        var defaultProfileId: String = ""
        var localHistoryEnabled: Boolean = true
        var maxHistoryItems: Int = DEFAULT_MAX_HISTORY_ITEMS
        var consoleHistory: MutableList<ConsoleHistoryEntryState> = mutableListOf()
        var consoleDrafts: MutableList<ConsoleDraftState> = mutableListOf()

        /**
         * Columns shown in Sessions tool window table (by column id).
         * User can change via "Columns…" button.
         */
        var sessionTableColumns: MutableList<String> = mutableListOf(
            "id", "appId", "owner", "kind", "state", "log"
        )
        var sessionsAutoRefreshEnabled: Boolean = false
        var sessionsAutoRefreshIntervalSeconds: Int = DEFAULT_SESSIONS_AUTO_REFRESH_INTERVAL_SECONDS

        /**
         * Sessions created by this plugin and considered safe to reuse/manage.
         * Reuse and auto-delete must never target arbitrary foreign sessions.
         */
        var managedSessions: MutableList<ManagedSessionState> = mutableListOf()
    }

    companion object {
        const val DEFAULT_SERVER_URL: String = "http://localhost:8998"
        const val DEFAULT_MAX_HISTORY_ITEMS: Int = 50
        const val MAX_HISTORY_SNIPPET_CHARS: Int = 8_000
        const val MAX_DRAFT_CHARS: Int = 20_000
        const val DEFAULT_SESSIONS_AUTO_REFRESH_INTERVAL_SECONDS: Int = 30
        const val MIN_SESSIONS_AUTO_REFRESH_INTERVAL_SECONDS: Int = 10
        const val MAX_SESSIONS_AUTO_REFRESH_INTERVAL_SECONDS: Int = 600

        fun getInstance(): LivyPluginSettings {
            return ApplicationManager.getApplication().getService(LivyPluginSettings::class.java)
        }
    }
}

fun LivyPluginSettings.PluginState.snapshot(): LivyPluginSettings.PluginState =
    LivyPluginSettings.PluginState().apply {
        copyRuntimeSettingsFrom(this@snapshot)
        profiles = this@snapshot.profiles
            .map { it.snapshot() }
            .toMutableList()
        activeProfileId = this@snapshot.activeProfileId
        defaultProfileId = this@snapshot.defaultProfileId
        localHistoryEnabled = this@snapshot.localHistoryEnabled
        maxHistoryItems = this@snapshot.maxHistoryItems
        consoleHistory = this@snapshot.consoleHistory
            .map { it.snapshot() }
            .toMutableList()
        consoleDrafts = this@snapshot.consoleDrafts
            .map { it.snapshot() }
            .toMutableList()
        sessionTableColumns = this@snapshot.sessionTableColumns.toMutableList()
        sessionsAutoRefreshEnabled = this@snapshot.sessionsAutoRefreshEnabled
        sessionsAutoRefreshIntervalSeconds = this@snapshot.sessionsAutoRefreshIntervalSeconds
        managedSessions = this@snapshot.managedSessions
            .map { it.snapshot() }
            .toMutableList()
    }

fun LivyPluginSettings.ConnectionProfileState.snapshot(): LivyPluginSettings.ConnectionProfileState =
    LivyPluginSettings.ConnectionProfileState().also { copy ->
        copy.id = id
        copy.displayName = displayName
        copy.copyRuntimeSettingsFrom(this)
    }

private fun LivyPluginSettings.ManagedSessionState.snapshot(): LivyPluginSettings.ManagedSessionState =
    LivyPluginSettings.ManagedSessionState().also { copy ->
        copy.sessionId = sessionId
        copy.serverUrl = serverUrl
        copy.fingerprint = fingerprint
        copy.createdAtMs = createdAtMs
    }

private fun LivyPluginSettings.ConsoleHistoryEntryState.snapshot(): LivyPluginSettings.ConsoleHistoryEntryState =
    LivyPluginSettings.ConsoleHistoryEntryState().also { copy ->
        copy.id = id
        copy.createdAtMs = createdAtMs
        copy.profileId = profileId
        copy.profileName = profileName
        copy.baseUrl = baseUrl
        copy.languageOrKind = languageOrKind
        copy.snippet = snippet
        copy.snippetTruncated = snippetTruncated
        copy.status = status
        copy.sessionId = sessionId
        copy.statementId = statementId
    }

private fun LivyPluginSettings.ConsoleDraftState.snapshot(): LivyPluginSettings.ConsoleDraftState =
    LivyPluginSettings.ConsoleDraftState().also { copy ->
        copy.profileId = profileId
        copy.languageOrKind = languageOrKind
        copy.text = text
        copy.updatedAtMs = updatedAtMs
    }

fun LivyPluginSettings.PluginState.findProfile(profileId: String?): LivyPluginSettings.ConnectionProfileState? =
    profiles.firstOrNull { it.id == profileId }

fun LivyPluginSettings.PluginState.requireProfile(profileId: String?): LivyPluginSettings.ConnectionProfileState =
    findProfile(profileId) ?: activeProfile()

fun LivyPluginSettings.PluginState.activeProfile(): LivyPluginSettings.ConnectionProfileState =
    findProfile(activeProfileId)?.snapshot()
        ?: findProfile(defaultProfileId)?.snapshot()
        ?: profiles.firstOrNull()?.snapshot()
        ?: createProfile()

fun LivyPluginSettings.PluginState.defaultProfile(): LivyPluginSettings.ConnectionProfileState =
    findProfile(defaultProfileId)?.snapshot()
        ?: profiles.firstOrNull()?.snapshot()
        ?: createProfile()

fun LivyPluginSettings.PluginState.setActiveProfile(profileId: String) {
    if (findProfile(profileId) != null) {
        activeProfileId = profileId
        syncLegacyFieldsFromActiveProfile()
    }
}

fun LivyPluginSettings.PluginState.setDefaultProfile(profileId: String) {
    if (findProfile(profileId) != null) {
        defaultProfileId = profileId
    }
}

fun LivyPluginSettings.PluginState.removeProfile(profileId: String) {
    profiles.removeAll { it.id == profileId }
    normalizeProfilesAfterLoad()
    syncLegacyFieldsFromActiveProfile()
}

fun LivyPluginSettings.PluginState.nextProfileDisplayName(): String {
    val existing = profiles.map { it.displayName.trim() }.toSet()
    var counter = 1
    while (true) {
        val candidate = if (counter == 1) "Profile" else "Profile $counter"
        if (candidate !in existing) return candidate
        counter++
    }
}

fun LivyPluginSettings.PluginState.profileChoices(): List<LivyPluginSettings.ConnectionProfileState> =
    profiles.map { it.snapshot() }

fun LivyPluginSettings.PluginState.profileLabel(profile: LivyPluginSettings.ConnectionProfileState): String {
    val tags = mutableListOf<String>()
    if (profile.id == activeProfileId) tags += "Active"
    if (profile.id == defaultProfileId) tags += "Default"
    return if (tags.isEmpty()) {
        profile.displayName
    } else {
        "${profile.displayName} (${tags.joinToString(", ")})"
    }
}

fun LivyPluginSettings.PluginState.historyEntriesForProfile(profileId: String): List<LivyPluginSettings.ConsoleHistoryEntryState> =
    consoleHistory
        .asSequence()
        .filter { it.profileId == profileId }
        .sortedByDescending { it.createdAtMs }
        .map { it.snapshot() }
        .toList()

fun LivyPluginSettings.PluginState.recordConsoleHistory(
    target: LivyExecutionTarget,
    snippet: String,
    status: String,
    sessionId: Int? = null,
    statementId: Int? = null,
    createdAtMs: Long = System.currentTimeMillis()
) {
    if (!localHistoryEnabled) return
    if (snippet.isBlank()) return

    val (storedSnippet, wasTruncated) = trimPersistedText(snippet, LivyPluginSettings.MAX_HISTORY_SNIPPET_CHARS)
    consoleHistory.add(
        0,
        LivyPluginSettings.ConsoleHistoryEntryState().apply {
            id = UUID.randomUUID().toString()
            this.createdAtMs = createdAtMs
            profileId = target.profileId
            profileName = target.profileName
            baseUrl = target.baseUrl
            languageOrKind = target.settingsSnapshot.kind
            this.snippet = storedSnippet
            snippetTruncated = wasTruncated
            this.status = status
            this.sessionId = sessionId ?: -1
            this.statementId = statementId ?: -1
        }
    )
    trimHistoryToRetention()
}

fun LivyPluginSettings.PluginState.rememberConsoleDraft(
    profileId: String,
    languageOrKind: String,
    text: String,
    updatedAtMs: Long = System.currentTimeMillis()
) {
    val normalizedKind = normalizeDraftKind(languageOrKind)
    consoleDrafts.removeAll {
        it.profileId == profileId && normalizeDraftKind(it.languageOrKind) == normalizedKind
    }
    if (!localHistoryEnabled) return

    if (text.isBlank()) return

    val (storedText, _) = trimPersistedText(text, LivyPluginSettings.MAX_DRAFT_CHARS)
    consoleDrafts.add(
        LivyPluginSettings.ConsoleDraftState().apply {
            this.profileId = profileId
            this.languageOrKind = normalizedKind
            this.text = storedText
            this.updatedAtMs = updatedAtMs
        }
    )
}

fun LivyPluginSettings.PluginState.draftTextForProfile(
    profileId: String,
    languageOrKind: String? = null
): String {
    val normalizedKind = normalizeDraftKind(languageOrKind.orEmpty())
    val exactMatch = consoleDrafts
        .filter { it.profileId == profileId && normalizeDraftKind(it.languageOrKind) == normalizedKind }
        .maxByOrNull { it.updatedAtMs }
        ?.text
        .orEmpty()
    if (exactMatch.isNotEmpty()) return exactMatch
    if (normalizedKind.isBlank()) {
        return consoleDrafts
            .filter { it.profileId == profileId }
            .maxByOrNull { it.updatedAtMs }
            ?.text
            .orEmpty()
    }
    return consoleDrafts
        .filter { it.profileId == profileId && normalizeDraftKind(it.languageOrKind).isBlank() }
        .maxByOrNull { it.updatedAtMs }
        ?.text
        .orEmpty()
}

fun LivyPluginSettings.PluginState.clearLocalHistoryAndDrafts(profileId: String? = null) {
    if (profileId == null) {
        consoleHistory.clear()
        consoleDrafts.clear()
        return
    }
    consoleHistory.removeAll { it.profileId == profileId }
    consoleDrafts.removeAll { it.profileId == profileId }
}

fun createProfile(
    displayName: String = "Profile",
    livyServerUrl: String = LivyPluginSettings.DEFAULT_SERVER_URL
): LivyPluginSettings.ConnectionProfileState =
    LivyPluginSettings.ConnectionProfileState().apply {
        id = UUID.randomUUID().toString()
        this.displayName = displayName
        this.livyServerUrl = LivyManagedSessions.normalizeServerUrl(livyServerUrl.ifBlank { LivyPluginSettings.DEFAULT_SERVER_URL })
    }

private fun LivyPluginSettings.PluginState.normalizeProfilesAfterLoad() {
    val normalizedProfiles = mutableListOf<LivyPluginSettings.ConnectionProfileState>()
    val usedIds = mutableSetOf<String>()

    for (rawProfile in profiles) {
        val profile = rawProfile.snapshot()
        profile.id = profile.id.trim().ifBlank { UUID.randomUUID().toString() }
        while (!usedIds.add(profile.id)) {
            profile.id = UUID.randomUUID().toString()
        }

        profile.displayName = profile.displayName.trim().ifBlank {
            if (normalizedProfiles.isEmpty()) "Default Profile" else "Profile ${normalizedProfiles.size + 1}"
        }
        profile.livyServerUrl = LivyManagedSessions.normalizeServerUrl(
            profile.livyServerUrl.ifBlank { LivyPluginSettings.DEFAULT_SERVER_URL }
        )
        normalizedProfiles += profile
    }

    if (normalizedProfiles.isEmpty()) {
        normalizedProfiles += legacyProfileFromSingleConfig()
    }

    profiles = normalizedProfiles
    defaultProfileId = defaultProfileId.takeIf { id -> profiles.any { it.id == id } } ?: profiles.first().id
    activeProfileId = activeProfileId.takeIf { id -> profiles.any { it.id == id } } ?: defaultProfileId
}

private fun LivyPluginSettings.PluginState.normalizeLocalConsoleData() {
    maxHistoryItems = maxHistoryItems.coerceIn(1, 500)
    val knownProfileIds = profiles.map { it.id }.toSet()

    consoleHistory = consoleHistory
        .asSequence()
        .filter { it.snippet.isNotBlank() }
        .map { entry ->
            val copy = entry.snapshot()
            copy.id = copy.id.ifBlank { UUID.randomUUID().toString() }
            copy.createdAtMs = copy.createdAtMs.coerceAtLeast(0L)
            copy.profileName = copy.profileName.trim()
            copy.baseUrl = LivyManagedSessions.normalizeServerUrl(copy.baseUrl.ifBlank { LivyPluginSettings.DEFAULT_SERVER_URL })
            copy.languageOrKind = copy.languageOrKind.trim()
            val (storedSnippet, wasTruncated) = trimPersistedText(copy.snippet, LivyPluginSettings.MAX_HISTORY_SNIPPET_CHARS)
            copy.snippet = storedSnippet
            copy.snippetTruncated = copy.snippetTruncated || wasTruncated
            copy.status = copy.status.trim().ifBlank { "unknown" }
            copy
        }
        .sortedByDescending { it.createdAtMs }
        .take(maxHistoryItems)
        .toMutableList()

    consoleDrafts = consoleDrafts
        .asSequence()
        .filter { it.profileId in knownProfileIds && it.text.isNotBlank() }
        .groupBy { it.profileId to normalizeDraftKind(it.languageOrKind) }
        .values
        .mapNotNull { draftsForProfile ->
            draftsForProfile.maxByOrNull { it.updatedAtMs }?.snapshot()
        }
        .map { draft ->
            val (storedText, _) = trimPersistedText(draft.text, LivyPluginSettings.MAX_DRAFT_CHARS)
            draft.languageOrKind = normalizeDraftKind(draft.languageOrKind)
            draft.text = storedText
            draft.updatedAtMs = draft.updatedAtMs.coerceAtLeast(0L)
            draft
        }
        .toMutableList()
}

private fun LivyPluginSettings.PluginState.normalizeSessionsPanelState() {
    sessionsAutoRefreshIntervalSeconds = normalizeSessionsAutoRefreshIntervalSeconds(sessionsAutoRefreshIntervalSeconds)
}

private fun LivyPluginSettings.PluginState.trimHistoryToRetention() {
    maxHistoryItems = maxHistoryItems.coerceIn(1, 500)
    if (consoleHistory.size <= maxHistoryItems) return
    consoleHistory = consoleHistory
        .sortedByDescending { it.createdAtMs }
        .take(maxHistoryItems)
        .toMutableList()
}

private fun LivyPluginSettings.PluginState.legacyProfileFromSingleConfig(): LivyPluginSettings.ConnectionProfileState =
    createProfile(displayName = "Default Profile", livyServerUrl = livyServerUrl).apply {
        copyRuntimeSettingsFrom(this@legacyProfileFromSingleConfig, includeServerUrl = false)
    }

fun LivyPluginSettings.PluginState.syncLegacyFieldsFromActiveProfile() {
    val profile = findProfile(activeProfileId) ?: findProfile(defaultProfileId) ?: profiles.firstOrNull() ?: return
    copyRuntimeSettingsFrom(profile)
}

private fun LivyConnectionRuntimeSettings.copyRuntimeSettingsFrom(
    source: LivyConnectionRuntimeSettings,
    includeServerUrl: Boolean = true
) {
    if (includeServerUrl) {
        livyServerUrl = source.livyServerUrl
    }
    maxSessions = source.maxSessions
    sessionManagementStrategy = source.sessionManagementStrategy
    kind = source.kind
    proxyUser = source.proxyUser
    jars = source.jars
    pyFiles = source.pyFiles
    files = source.files
    driverMemory = source.driverMemory
    driverCores = source.driverCores
    executorMemory = source.executorMemory
    executorCores = source.executorCores
    numExecutors = source.numExecutors
    archives = source.archives
    queue = source.queue
    name = source.name
    conf = source.conf
    heartbeatTimeoutInSecond = source.heartbeatTimeoutInSecond
    ttl = source.ttl
    killOldestIfFull = source.killOldestIfFull
}

private fun trimPersistedText(value: String, maxChars: Int): Pair<String, Boolean> {
    if (value.length <= maxChars) return value to false
    return value.take(maxChars) to true
}

private fun normalizeDraftKind(value: String): String = value.trim().lowercase()

fun normalizeSessionsAutoRefreshIntervalSeconds(value: Int): Int =
    value.coerceIn(
        LivyPluginSettings.MIN_SESSIONS_AUTO_REFRESH_INTERVAL_SECONDS,
        LivyPluginSettings.MAX_SESSIONS_AUTO_REFRESH_INTERVAL_SECONDS
    )
