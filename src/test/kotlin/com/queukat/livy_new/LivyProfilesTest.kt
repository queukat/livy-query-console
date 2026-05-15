package com.queukat.livy_new

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LivyProfilesTest {

    @Test
    fun loadState_migrates_legacy_single_settings_into_default_profile() {
        val legacyState = LivyPluginSettings.PluginState().apply {
            livyServerUrl = "http://legacy-livy/"
            kind = "sql"
            proxyUser = "analytics-user"
            maxSessions = 5
            sessionManagementStrategy = "always_create"
            killOldestIfFull = true
        }
        val settings = LivyPluginSettings()

        settings.loadState(legacyState)

        val migrated = settings.pluginState
        assertEquals(1, migrated.profiles.size)
        val profile = migrated.activeProfile()
        assertEquals(migrated.defaultProfileId, migrated.activeProfileId)
        assertEquals("Default Profile", profile.displayName)
        assertEquals("http://legacy-livy", profile.livyServerUrl)
        assertEquals("sql", profile.kind)
        assertEquals("analytics-user", profile.proxyUser)
        assertEquals(5, profile.maxSessions)
        assertEquals("always_create", profile.sessionManagementStrategy)
        assertTrue(profile.killOldestIfFull)
    }

    @Test
    fun switching_active_profile_updates_legacy_mirror_fields_without_changing_profiles() {
        val profileA = createProfile(displayName = "A", livyServerUrl = "http://livy-a").apply {
            kind = "sql"
        }
        val profileB = createProfile(displayName = "B", livyServerUrl = "http://livy-b").apply {
            kind = "pyspark"
        }
        val state = LivyPluginSettings.PluginState().apply {
            profiles = mutableListOf(profileA, profileB)
            activeProfileId = profileA.id
            defaultProfileId = profileA.id
            syncLegacyFieldsFromActiveProfile()
        }

        state.setActiveProfile(profileB.id)

        assertEquals("http://livy-b", state.livyServerUrl)
        assertEquals("pyspark", state.kind)
        assertEquals(profileA.id, state.defaultProfileId)
        assertEquals(2, state.profiles.size)
    }

    @Test
    fun remove_profile_reassigns_active_and_default_to_remaining_profile() {
        val profileA = createProfile(displayName = "A", livyServerUrl = "http://livy-a")
        val profileB = createProfile(displayName = "B", livyServerUrl = "http://livy-b")
        val state = LivyPluginSettings.PluginState().apply {
            profiles = mutableListOf(profileA, profileB)
            activeProfileId = profileA.id
            defaultProfileId = profileA.id
            syncLegacyFieldsFromActiveProfile()
        }

        state.removeProfile(profileA.id)

        assertEquals(1, state.profiles.size)
        assertEquals(profileB.id, state.activeProfileId)
        assertEquals(profileB.id, state.defaultProfileId)
        assertEquals("http://livy-b", state.livyServerUrl)
        assertNotEquals(profileA.id, state.profiles.first().id)
    }

    @Test
    fun profile_selection_options_use_active_profile_as_default_choice() {
        val profileA = createProfile(displayName = "A", livyServerUrl = "http://livy-a")
        val profileB = createProfile(displayName = "B", livyServerUrl = "http://livy-b")
        val state = LivyPluginSettings.PluginState().apply {
            profiles = mutableListOf(profileA, profileB)
            activeProfileId = profileB.id
            defaultProfileId = profileA.id
        }

        val options = buildLivyProfileSelectionOptions(state)

        assertEquals(
            listOf("A (Default) - http://livy-a", "B (Active) - http://livy-b"),
            options.labels
        )
        assertEquals(1, options.defaultIndex)
    }

    @Test
    fun profile_selection_options_fall_back_to_first_profile_when_active_is_missing() {
        val profileA = createProfile(displayName = "A", livyServerUrl = "http://livy-a")
        val profileB = createProfile(displayName = "B", livyServerUrl = "http://livy-b")
        val state = LivyPluginSettings.PluginState().apply {
            profiles = mutableListOf(profileA, profileB)
            activeProfileId = "missing"
            defaultProfileId = profileA.id
        }

        val options = buildLivyProfileSelectionOptions(state)

        assertEquals(0, options.defaultIndex)
    }

    @Test
    fun loadState_normalizes_profiles_sessions_history_drafts_and_panel_defaults() {
        val duplicateId = "duplicate-id"
        val rawProfileA = LivyPluginSettings.ConnectionProfileState().apply {
            id = duplicateId
            displayName = "  "
            livyServerUrl = ""
        }
        val rawProfileB = LivyPluginSettings.ConnectionProfileState().apply {
            id = duplicateId
            displayName = " Analytics "
            livyServerUrl = "http://livy-b/"
        }
        val state = LivyPluginSettings.PluginState().apply {
            profiles = mutableListOf(rawProfileA, rawProfileB)
            activeProfileId = "missing"
            defaultProfileId = "missing"
            maxHistoryItems = 1_000
            sessionTableColumns = mutableListOf()
            sessionsAutoRefreshIntervalSeconds = 1
            managedSessions = mutableListOf(
                LivyPluginSettings.ManagedSessionState().apply {
                    sessionId = 1
                    serverUrl = "http://livy-b"
                    fingerprint = "abc"
                    createdAtMs = 10L
                },
                LivyPluginSettings.ManagedSessionState().apply {
                    sessionId = 1
                    serverUrl = "http://livy-b"
                    fingerprint = "duplicate"
                    createdAtMs = 11L
                },
                LivyPluginSettings.ManagedSessionState().apply {
                    sessionId = -1
                    serverUrl = "http://livy-b"
                    fingerprint = "bad"
                }
            )
            consoleHistory = mutableListOf(
                LivyPluginSettings.ConsoleHistoryEntryState().apply {
                    id = ""
                    createdAtMs = -5L
                    profileId = duplicateId
                    profileName = " Analytics "
                    baseUrl = ""
                    languageOrKind = " SQL "
                    snippet = "select 1"
                    status = ""
                },
                LivyPluginSettings.ConsoleHistoryEntryState().apply {
                    profileId = duplicateId
                    snippet = ""
                }
            )
            consoleDrafts = mutableListOf(
                LivyPluginSettings.ConsoleDraftState().apply {
                    profileId = duplicateId
                    languageOrKind = " SQL "
                    text = "old"
                    updatedAtMs = 1L
                },
                LivyPluginSettings.ConsoleDraftState().apply {
                    profileId = duplicateId
                    languageOrKind = "sql"
                    text = "new"
                    updatedAtMs = 2L
                },
                LivyPluginSettings.ConsoleDraftState().apply {
                    profileId = "unknown"
                    text = "ignored"
                }
            )
        }
        val settings = LivyPluginSettings()

        settings.loadState(state)

        val normalized = settings.pluginState
        assertEquals(listOf("id", "appId", "owner", "kind", "state", "log"), normalized.sessionTableColumns)
        assertEquals(LivyPluginSettings.MIN_SESSIONS_AUTO_REFRESH_INTERVAL_SECONDS, normalized.sessionsAutoRefreshIntervalSeconds)
        assertEquals(1, normalized.managedSessions.size)
        assertEquals(2, normalized.profiles.size)
        assertNotEquals(normalized.profiles[0].id, normalized.profiles[1].id)
        assertEquals("Default Profile", normalized.profiles[0].displayName)
        assertEquals(LivyPluginSettings.DEFAULT_SERVER_URL, normalized.profiles[0].livyServerUrl)
        assertEquals("Analytics", normalized.profiles[1].displayName)
        assertEquals(normalized.defaultProfileId, normalized.activeProfileId)
        assertEquals(500, normalized.maxHistoryItems)
        assertEquals(1, normalized.consoleHistory.size)
        assertEquals(0L, normalized.consoleHistory.single().createdAtMs)
        assertEquals("unknown", normalized.consoleHistory.single().status)
        assertEquals(1, normalized.consoleDrafts.size)
        assertEquals("new", normalized.consoleDrafts.single().text)
        assertEquals("sql", normalized.consoleDrafts.single().languageOrKind)
    }

    @Test
    fun profile_helpers_cover_fallback_names_defaults_and_profile_scoped_clear() {
        val state = LivyPluginSettings.PluginState()

        assertEquals("Profile", state.nextProfileDisplayName())
        assertEquals("Profile", state.defaultProfile().displayName)

        val profileA = createProfile(displayName = "Profile", livyServerUrl = "http://livy-a")
        val profileB = createProfile(displayName = "Profile 2", livyServerUrl = "http://livy-b")
        state.profiles = mutableListOf(profileA, profileB)
        state.activeProfileId = profileA.id
        state.defaultProfileId = profileA.id
        state.consoleHistory = mutableListOf(
            LivyPluginSettings.ConsoleHistoryEntryState().apply { profileId = profileA.id; snippet = "a" },
            LivyPluginSettings.ConsoleHistoryEntryState().apply { profileId = profileB.id; snippet = "b" }
        )
        state.consoleDrafts = mutableListOf(
            LivyPluginSettings.ConsoleDraftState().apply { profileId = profileA.id; text = "a" },
            LivyPluginSettings.ConsoleDraftState().apply { profileId = profileB.id; text = "b" }
        )

        state.setDefaultProfile("missing")
        state.clearLocalHistoryAndDrafts(profileA.id)

        assertEquals("Profile 3", state.nextProfileDisplayName())
        assertEquals(profileA.id, state.defaultProfileId)
        assertEquals(listOf(profileB.id), state.consoleHistory.map { it.profileId })
        assertEquals(listOf(profileB.id), state.consoleDrafts.map { it.profileId })

        state.clearLocalHistoryAndDrafts()

        assertTrue(state.consoleHistory.isEmpty())
        assertTrue(state.consoleDrafts.isEmpty())
        assertFalse(state.profileLabel(profileB).contains("Default"))
    }
}
