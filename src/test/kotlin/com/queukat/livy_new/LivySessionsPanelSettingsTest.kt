package com.queukat.livy_new

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LivySessionsPanelSettingsTest {

    @Test
    fun normalize_sessions_auto_refresh_interval_clamps_to_supported_range() {
        assertEquals(10, normalizeSessionsAutoRefreshIntervalSeconds(1))
        assertEquals(45, normalizeSessionsAutoRefreshIntervalSeconds(45))
        assertEquals(600, normalizeSessionsAutoRefreshIntervalSeconds(5_000))
    }

    @Test
    fun plugin_state_snapshot_preserves_sessions_auto_refresh_settings() {
        val state = LivyPluginSettings.PluginState().apply {
            sessionsAutoRefreshEnabled = true
            sessionsAutoRefreshIntervalSeconds = 45
        }

        val copy = state.snapshot()

        assertTrue(copy.sessionsAutoRefreshEnabled)
        assertEquals(45, copy.sessionsAutoRefreshIntervalSeconds)
        assertFalse(copy === state)
    }
}
