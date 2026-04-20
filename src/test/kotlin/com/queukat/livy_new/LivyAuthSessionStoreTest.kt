package com.queukat.livy_new

import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
}
