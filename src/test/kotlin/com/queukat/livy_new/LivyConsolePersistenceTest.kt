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
}
