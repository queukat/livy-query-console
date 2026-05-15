package com.queukat.livy_new

import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LivyAuthSessionStoreTest {

    @Test
    fun merge_stored_cookies_replaces_existing_cookie_with_same_identity() {
        val existing = listOf(
            LivyStoredCookie(
                name = "_oauth2_proxy",
                value = "old",
                domain = "analytics.crpt.tech",
                path = "/"
            )
        )

        val merged = mergeStoredCookies(
            existing = existing,
            incoming = listOf(
                LivyStoredCookie(
                    name = "_oauth2_proxy",
                    value = "new",
                    domain = "analytics.crpt.tech",
                    path = "/"
                )
            ),
            nowMs = 1_000L
        )

        assertEquals(1, merged.size)
        assertEquals("new", merged.single().value)
    }

    @Test
    fun normalize_stored_session_filters_expired_cookies_and_normalizes_base_url() {
        val session = LivyStoredAuthSession(
            baseUrl = "http://livy350.test06.analytics.crpt.tech/",
            updatedAtMs = 10L,
            cookies = listOf(
                LivyStoredCookie(
                    name = "live",
                    value = "ok",
                    domain = "analytics.crpt.tech",
                    path = "/",
                    expiresAtMs = 2_000L
                ),
                LivyStoredCookie(
                    name = "expired",
                    value = "gone",
                    domain = "analytics.crpt.tech",
                    path = "/",
                    expiresAtMs = 500L
                )
            )
        )

        val normalized = normalizeStoredSession(session, nowMs = 1_000L)

        assertNotNull(normalized)
        assertEquals("http://livy350.test06.analytics.crpt.tech", normalized.baseUrl)
        assertEquals(1, normalized.cookies.size)
        assertEquals("live", normalized.cookies.single().name)
    }

    @Test
    fun stored_cookie_matches_subdomain_requests_for_parent_domain_cookie() {
        val cookie = LivyStoredCookie(
            name = "_oauth2_proxy",
            value = "session",
            domain = "analytics.crpt.tech",
            path = "/",
            secure = true,
            httpOnly = true
        ).toOkHttpCookieOrNull()

        assertNotNull(cookie)
        assertTrue(cookie.matches("https://livy350.test06.analytics.crpt.tech/sessions".toHttpUrl()))
    }

    @Test
    fun merge_stored_cookies_removes_expired_incoming_cookie_and_keeps_stable_order() {
        val merged = mergeStoredCookies(
            existing = listOf(
                LivyStoredCookie(name = "b", value = "2", domain = "b.example.com", path = "/"),
                LivyStoredCookie(name = "old", value = "1", domain = "a.example.com", path = "/")
            ),
            incoming = listOf(
                LivyStoredCookie(name = "old", value = "expired", domain = "a.example.com", path = "/", expiresAtMs = 10L),
                LivyStoredCookie(name = "a", value = "1", domain = "a.example.com", path = "/api")
            ),
            nowMs = 20L
        )

        assertEquals(listOf("a", "b"), merged.map { it.name })
        assertEquals(listOf("a.example.com", "b.example.com"), merged.map { it.domain })
    }

    @Test
    fun stored_cookie_normalization_rejects_blank_identity_and_preserves_flags() {
        assertNull(LivyStoredCookie(name = "", value = "v", domain = "example.com").normalizedOrNull())
        assertNull(LivyStoredCookie(name = "n", value = "", domain = "example.com").normalizedOrNull())
        assertNull(LivyStoredCookie(name = "n", value = "v", domain = "").normalizedOrNull())

        val cookie = LivyStoredCookie(
            name = " session ",
            value = "abc",
            domain = ".example.com",
            path = "",
            secure = true,
            httpOnly = true,
            expiresAtMs = 2_000L
        ).normalizedOrNull()?.toOkHttpCookieOrNull()

        assertNotNull(cookie)
        assertEquals("session", cookie.name)
        assertEquals("example.com", cookie.domain)
        assertEquals("/", cookie.path)
        assertTrue(cookie.secure)
        assertTrue(cookie.httpOnly)
        assertTrue(cookie.persistent)
    }

    @Test
    fun normalize_stored_session_rejects_missing_base_url_or_cookies() {
        assertNull(normalizeStoredSession(null, nowMs = 1_000L))
        assertNull(
            normalizeStoredSession(
                LivyStoredAuthSession(
                    baseUrl = "",
                    cookies = listOf(LivyStoredCookie(name = "n", value = "v", domain = "example.com"))
                ),
                nowMs = 1_000L
            )
        )
        assertNull(
            normalizeStoredSession(
                LivyStoredAuthSession(baseUrl = "http://livy", cookies = emptyList()),
                nowMs = 1_000L
            )
        )
    }

    @Test
    fun auth_status_display_text_describes_missing_matching_and_mismatched_sessions() {
        assertEquals(
            "Browser auth: no saved session for this profile.",
            LivyAuthStatus(hasSavedSession = false, storedCookieCount = 0, matchingCookieCount = 0)
                .toDisplayText("http://livy-a")
        )

        val matching = LivyAuthStatus(
            hasSavedSession = true,
            storedCookieCount = 2,
            matchingCookieCount = 1,
            storedBaseUrl = "http://livy-a"
        ).toDisplayText("http://livy-a")

        assertContains(matching, "2 cookie(s)")
        assertContains(matching, "1 currently match")
        assertFalse(matching.contains("captured for"))

        val mismatched = LivyAuthStatus(
            hasSavedSession = true,
            storedCookieCount = 2,
            matchingCookieCount = 0,
            storedBaseUrl = "http://livy-old"
        ).toDisplayText("http://livy-new")

        assertContains(mismatched, "none currently match")
        assertContains(mismatched, "captured for livy-old")
    }
}
