package com.queukat.livy_new

import com.intellij.testFramework.LightVirtualFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LivyExecutionTargetTest {

    @Test
    fun capture_clones_and_normalizes_selected_profile_for_console_binding() {
        val profileA = createProfile(displayName = "A", livyServerUrl = "http://livy-a/").apply {
            kind = "sql"
            maxSessions = 3
            killOldestIfFull = true
        }
        val profileB = createProfile(displayName = "B", livyServerUrl = "http://livy-b").apply {
            kind = "spark"
            maxSessions = 9
        }
        val settings = LivyPluginSettings.PluginState().apply {
            profiles = mutableListOf(profileA, profileB)
            activeProfileId = profileA.id
            defaultProfileId = profileA.id
        }

        val target = LivyExecutionTarget.capture(settings, profileA.id)

        settings.setActiveProfile(profileB.id)
        settings.requireProfile(profileA.id).apply {
            livyServerUrl = "http://changed"
            kind = "pyspark"
            maxSessions = 12
            killOldestIfFull = false
        }

        assertEquals(profileA.id, target.profileId)
        assertEquals("A", target.profileName)
        assertEquals("http://livy-a", target.baseUrl)
        assertEquals("http://livy-a", target.settingsSnapshot.livyServerUrl)
        assertEquals("sql", target.settingsSnapshot.kind)
        assertEquals(3, target.settingsSnapshot.maxSessions)
        assertTrue(target.settingsSnapshot.killOldestIfFull)
    }

    @Test
    fun capture_uses_active_profile_by_default() {
        val profileA = createProfile(displayName = "A", livyServerUrl = "http://livy-a")
        val profileB = createProfile(displayName = "B", livyServerUrl = "http://livy-b")
        val settings = LivyPluginSettings.PluginState().apply {
            profiles = mutableListOf(profileA, profileB)
            activeProfileId = profileB.id
            defaultProfileId = profileA.id
        }

        val target = LivyExecutionTarget.capture(settings)

        assertEquals(profileB.id, target.profileId)
        assertEquals("B", target.profileName)
        assertEquals("http://livy-b", target.baseUrl)
    }

    @Test
    fun capture_can_preserve_kind_hint_for_reopened_work_file_without_retargeting_profile() {
        val profile = createProfile(displayName = "SQL Profile", livyServerUrl = "http://livy-a").apply {
            kind = "spark"
            driverMemory = "2g"
        }
        val settings = LivyPluginSettings.PluginState().apply {
            profiles = mutableListOf(profile)
            activeProfileId = profile.id
            defaultProfileId = profile.id
        }

        val target = LivyExecutionTarget.capture(settings, profile.id, kindOverride = "sql")

        assertEquals(profile.id, target.profileId)
        assertEquals("SQL Profile", target.profileName)
        assertEquals("http://livy-a", target.baseUrl)
        assertEquals("sql", target.settingsSnapshot.kind)
        assertEquals("2g", target.settingsSnapshot.driverMemory)
    }

    @Test
    fun attached_execution_target_is_reused_for_virtual_file_resolution() {
        val file = LightVirtualFile("work.livyconsole")
        val target = LivyExecutionTarget(
            profileId = "profile",
            profileName = "Profile",
            baseUrl = "http://livy",
            settingsSnapshot = createProfile(displayName = "Profile", livyServerUrl = "http://livy")
        )

        LivyExecutionTargets.attach(file, target)

        assertEquals(target, LivyExecutionTargets.attached(file))
        assertEquals(target, LivyExecutionTargets.resolve(file))
    }
}
