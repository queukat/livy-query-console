package com.queukat.livy_new

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
}
