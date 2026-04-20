package com.queukat.livy_new

import kotlin.test.Test
import kotlin.test.assertEquals

class LivyConsolePersistenceTest {

    @Test
    fun resolve_initial_console_text_prefers_existing_file_text() {
        val restored = resolveInitialConsoleText(
            fileText = "current editor text",
            savedDraft = "saved draft",
            localHistoryEnabled = true
        )

        assertEquals("current editor text", restored)
    }

    @Test
    fun resolve_initial_console_text_uses_saved_draft_only_when_history_is_enabled() {
        assertEquals(
            "saved draft",
            resolveInitialConsoleText(fileText = "", savedDraft = "saved draft", localHistoryEnabled = true)
        )
        assertEquals(
            "",
            resolveInitialConsoleText(fileText = "", savedDraft = "saved draft", localHistoryEnabled = false)
        )
    }

    @Test
    fun remember_console_draft_replaces_existing_entry_for_same_profile_and_kind() {
        val state = LivyPluginSettings.PluginState().apply {
            localHistoryEnabled = true
        }

        state.rememberConsoleDraft(profileId = "profile-a", languageOrKind = "sql", text = "select 1", updatedAtMs = 1)
        state.rememberConsoleDraft(profileId = "profile-a", languageOrKind = "sql", text = "select 2", updatedAtMs = 2)

        assertEquals(1, state.consoleDrafts.size)
        assertEquals("select 2", state.draftTextForProfile("profile-a", "sql"))
    }
}
