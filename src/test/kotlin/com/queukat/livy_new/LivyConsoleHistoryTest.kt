package com.queukat.livy_new

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LivyConsoleHistoryTest {

    @Test
    fun record_console_history_is_isolated_by_profile() {
        val profileA = createProfile(displayName = "Analytics Prod", livyServerUrl = "http://livy-a").apply {
            kind = "sql"
        }
        val profileB = createProfile(displayName = "Analytics Stage", livyServerUrl = "http://livy-b").apply {
            kind = "pyspark"
        }
        val state = LivyPluginSettings.PluginState().apply {
            profiles = mutableListOf(profileA, profileB)
            activeProfileId = profileA.id
            defaultProfileId = profileA.id
            syncLegacyFieldsFromActiveProfile()
        }

        state.recordConsoleHistory(
            target = LivyExecutionTarget.capture(state, profileA.id),
            snippet = "select 1",
            status = "ok",
            sessionId = 10,
            statementId = 20,
            createdAtMs = 100
        )
        state.recordConsoleHistory(
            target = LivyExecutionTarget.capture(state, profileB.id),
            snippet = "spark.range(1).show()",
            status = "ok",
            createdAtMs = 200
        )

        val profileAHistory = state.historyEntriesForProfile(profileA.id)
        val profileBHistory = state.historyEntriesForProfile(profileB.id)

        assertEquals(1, profileAHistory.size)
        assertEquals("Analytics Prod", profileAHistory.single().profileName)
        assertEquals("http://livy-a", profileAHistory.single().baseUrl)
        assertEquals(10, profileAHistory.single().sessionId)
        assertEquals(20, profileAHistory.single().statementId)

        assertEquals(1, profileBHistory.size)
        assertEquals("Analytics Stage", profileBHistory.single().profileName)
        assertEquals("pyspark", profileBHistory.single().languageOrKind)
    }

    @Test
    fun record_console_history_trims_to_retention_and_truncates_long_snippets() {
        val profile = createProfile(displayName = "Analytics Prod", livyServerUrl = "http://livy-a")
        val state = LivyPluginSettings.PluginState().apply {
            profiles = mutableListOf(profile)
            activeProfileId = profile.id
            defaultProfileId = profile.id
            maxHistoryItems = 2
        }
        val target = LivyExecutionTarget.capture(state, profile.id)
        val longSnippet = "x".repeat(LivyPluginSettings.MAX_HISTORY_SNIPPET_CHARS + 25)

        state.recordConsoleHistory(target, "select 1", "ok", createdAtMs = 100)
        state.recordConsoleHistory(target, longSnippet, "error", createdAtMs = 200)
        state.recordConsoleHistory(target, "select 3", "ok", createdAtMs = 300)

        val history = state.historyEntriesForProfile(profile.id)

        assertEquals(2, history.size)
        assertEquals("select 3", history[0].snippet)
        assertEquals("error", history[1].status)
        assertTrue(history[1].snippetTruncated)
        assertEquals(LivyPluginSettings.MAX_HISTORY_SNIPPET_CHARS, history[1].snippet.length)
    }

    @Test
    fun remember_console_draft_is_isolated_by_profile_and_kind_and_trimmed() {
        val profileA = createProfile(displayName = "A", livyServerUrl = "http://livy-a").apply {
            kind = "sql"
        }
        val profileB = createProfile(displayName = "B", livyServerUrl = "http://livy-b")
        val state = LivyPluginSettings.PluginState().apply {
            profiles = mutableListOf(profileA, profileB)
            activeProfileId = profileA.id
            defaultProfileId = profileA.id
        }
        val longDraft = "d".repeat(LivyPluginSettings.MAX_DRAFT_CHARS + 10)

        state.rememberConsoleDraft(profileA.id, "sql", "select 1", updatedAtMs = 100)
        state.rememberConsoleDraft(profileA.id, "pyspark", "spark.range(1).show()", updatedAtMs = 150)
        state.rememberConsoleDraft(profileB.id, "spark", longDraft, updatedAtMs = 200)
        state.rememberConsoleDraft(profileA.id, "sql", "select 2", updatedAtMs = 300)

        assertEquals("select 2", state.draftTextForProfile(profileA.id, "sql"))
        assertEquals("spark.range(1).show()", state.draftTextForProfile(profileA.id, "pyspark"))
        assertEquals(LivyPluginSettings.MAX_DRAFT_CHARS, state.draftTextForProfile(profileB.id, "spark").length)
        assertTrue(state.draftTextForProfile(profileB.id, "spark").all { it == 'd' })
    }

    @Test
    fun local_history_disabled_stops_new_history_and_drafts() {
        val profile = createProfile(displayName = "A", livyServerUrl = "http://livy-a")
        val state = LivyPluginSettings.PluginState().apply {
            profiles = mutableListOf(profile)
            activeProfileId = profile.id
            defaultProfileId = profile.id
            localHistoryEnabled = false
        }
        val target = LivyExecutionTarget.capture(state, profile.id)

        state.recordConsoleHistory(target, "select 1", "ok", createdAtMs = 100)
        state.rememberConsoleDraft(profile.id, "sql", "select 2", updatedAtMs = 100)

        assertTrue(state.historyEntriesForProfile(profile.id).isEmpty())
        assertFalse(state.draftTextForProfile(profile.id, "sql").isNotEmpty())
    }

    @Test
    fun history_uses_captured_execution_target_snapshot_not_live_profile_state() {
        val profile = createProfile(displayName = "Analytics Prod", livyServerUrl = "http://livy-a/").apply {
            kind = "sql"
        }
        val state = LivyPluginSettings.PluginState().apply {
            profiles = mutableListOf(profile)
            activeProfileId = profile.id
            defaultProfileId = profile.id
        }
        val target = LivyExecutionTarget.capture(state, profile.id)

        state.requireProfile(profile.id).apply {
            displayName = "Changed Later"
            livyServerUrl = "http://changed"
            kind = "pyspark"
        }

        state.recordConsoleHistory(target, "select 1", "ok", createdAtMs = 100)
        val entry = state.historyEntriesForProfile(profile.id).single()

        assertEquals("Analytics Prod", entry.profileName)
        assertEquals("http://livy-a", entry.baseUrl)
        assertEquals("sql", entry.languageOrKind)
    }
}
