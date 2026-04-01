package com.queukat.livy_new

import com.intellij.testFramework.LightVirtualFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LivySourceRoutingTest {

    @Test
    fun resolve_selection_or_current_line_prefers_selection() {
        val text = "first line\nselected line\nthird line"
        val start = text.indexOf("selected")
        val end = start + "selected line".length

        val resolved = resolveSelectionOrCurrentLine(
            documentText = text,
            selectionStart = start,
            selectionEnd = end,
            caretOffset = 0
        )

        assertEquals(LivySourceSnippetMode.SELECTION, resolved.mode)
        assertEquals("selected line", resolved.text)
        assertEquals(2, resolved.lineStart)
        assertEquals(2, resolved.lineEnd)
    }

    @Test
    fun resolve_selection_or_current_line_falls_back_to_current_line() {
        val text = "first line\nsecond line\nthird line"
        val caretOffset = text.indexOf("second") + 3

        val resolved = resolveSelectionOrCurrentLine(
            documentText = text,
            selectionStart = caretOffset,
            selectionEnd = caretOffset,
            caretOffset = caretOffset
        )

        assertEquals(LivySourceSnippetMode.CURRENT_LINE, resolved.mode)
        assertEquals("second line", resolved.text)
        assertEquals(2, resolved.lineStart)
        assertEquals(2, resolved.lineEnd)
    }

    @Test
    fun resolve_whole_file_keeps_full_text_and_line_range() {
        val text = "line one\nline two\nline three"

        val resolved = resolveWholeFile(text)

        assertEquals(LivySourceSnippetMode.WHOLE_FILE, resolved.mode)
        assertEquals(text, resolved.text)
        assertEquals(1, resolved.lineStart)
        assertEquals(3, resolved.lineEnd)
    }

    @Test
    fun profile_id_hint_from_work_file_resolves_profile_by_path_segment() {
        val profile = createProfile(displayName = "Analytics Prod", livyServerUrl = "http://livy-a")
        val settings = LivyPluginSettings.PluginState().apply {
            profiles = mutableListOf(profile)
            activeProfileId = profile.id
            defaultProfileId = profile.id
        }
        val plainFile = object : LightVirtualFile("Analytics-Prod.livyconsole") {
            override fun getPath(): String = "/tmp/Analytics-Prod.livyconsole"
        }
        val target = LivyExecutionTarget.capture(settings, profile.id, "sql")
        val hintedFile = object : LightVirtualFile("Analytics-Prod.livyconsole") {
            override fun getPath(): String = "/tmp/${workFilePath(target)}"
        }

        assertEquals(profile.id, profileIdHintFromWorkFile(hintedFile, settings))
        assertEquals(LivyWorkFileMode.SQL, workFileModeHintFromWorkFile(hintedFile))
        assertNull(profileIdHintFromWorkFile(plainFile, settings))
        assertNull(workFileModeHintFromWorkFile(plainFile))
    }

    @Test
    fun resolve_sql_statement_at_caret_prefers_statement_around_caret() {
        val text = """
            select 1;
            
            select
              2;
            
            select 3
        """.trimIndent()

        val resolved = resolveSqlStatementAtCaret(
            documentText = text,
            caretOffset = text.indexOf("2")
        )

        assertNotNull(resolved)
        assertEquals(LivySourceSnippetMode.STATEMENT, resolved.mode)
        assertEquals("select\n  2", resolved.text)
        assertEquals(3, resolved.lineStart)
        assertEquals(4, resolved.lineEnd)
    }

    @Test
    fun resolve_sql_statement_at_caret_uses_last_statement_without_trailing_semicolon() {
        val text = "select 1;\nselect 2"

        val resolved = resolveSqlStatementAtCaret(
            documentText = text,
            caretOffset = text.length
        )

        assertNotNull(resolved)
        assertEquals("select 2", resolved.text)
        assertEquals(2, resolved.lineStart)
        assertEquals(2, resolved.lineEnd)
    }

    @Test
    fun resolve_sql_statement_at_caret_returns_null_for_blank_segments() {
        val resolved = resolveSqlStatementAtCaret(
            documentText = " ; \n ; ",
            caretOffset = 1
        )

        assertNull(resolved)
    }

    @Test
    fun pending_work_surface_request_is_consumed_only_once() {
        val file = LightVirtualFile("test.livyconsole")
        val request = LivyWorkSurfaceRequest(snippet = "select 1", autorun = true)

        LivyWorkSurfaceRequests.attach(file, request)

        assertEquals(request, LivyWorkSurfaceRequests.consume(file))
        assertNull(LivyWorkSurfaceRequests.consume(file))
    }
}
