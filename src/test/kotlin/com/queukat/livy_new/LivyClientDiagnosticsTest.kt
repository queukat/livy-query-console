package com.queukat.livy_new

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LivyClientDiagnosticsTest {

    @Test
    fun diagnostics_text_includes_all_available_context_sections() {
        val diagnostics = ConnectionTestDiagnostics(
            requestUrl = "http://livy.example/sessions",
            success = false,
            elapsedMs = 42,
            httpCode = 403,
            httpMessage = "Forbidden",
            responseHeaders = mapOf("content-type" to listOf("text/html")),
            responseBodyPreview = "<html>login</html>",
            exceptionClass = "java.io.IOException",
            exceptionMessage = "blocked",
            causeChain = listOf("java.net.SocketTimeoutException: timed out"),
            authRequired = true
        )

        val text = diagnostics.toDiagnosticsText()

        assertTrue("Auth hint: Browser authentication appears to be required." in text)
        assertTrue("HTTP: 403 Forbidden" in text)
        assertTrue("content-type: text/html" in text)
        assertTrue("<html>login</html>" in text)
        assertTrue("Exception: java.io.IOException" in text)
        assertTrue("- java.net.SocketTimeoutException: timed out" in text)
    }

    @Test
    fun diagnostics_summary_covers_success_http_failure_exception_and_auth() {
        assertTrue(
            "Connection successful" in ConnectionTestDiagnostics(
                requestUrl = "http://livy/sessions",
                success = true,
                elapsedMs = 5,
                httpCode = 200,
                httpMessage = "OK"
            ).summary()
        )
        assertTrue(
            "HTTP 503" in ConnectionTestDiagnostics(
                requestUrl = "http://livy/sessions",
                success = false,
                elapsedMs = 5,
                httpCode = 503,
                httpMessage = "Unavailable"
            ).summary()
        )
        assertTrue(
            "java.io.IOException" in ConnectionTestDiagnostics(
                requestUrl = "http://livy/sessions",
                success = false,
                elapsedMs = 5,
                exceptionClass = "java.io.IOException",
                exceptionMessage = "network down"
            ).summary()
        )
        assertTrue(
            "requires browser authentication" in ConnectionTestDiagnostics(
                requestUrl = "http://livy/sessions",
                success = false,
                elapsedMs = 5,
                httpCode = 401,
                httpMessage = "Unauthorized",
                authRequired = true
            ).summary()
        )
    }

    @Test
    fun auth_detection_handles_http_redirect_html_and_plain_failures() {
        assertTrue(
            livyResponseLooksLikeAuthenticationRequired(
                httpCode = 302,
                responseHeaders = mapOf("Location" to listOf("/oauth2/start")),
                responseBody = ""
            )
        )
        assertTrue(
            livyResponseLooksLikeAuthenticationRequired(
                httpCode = 403,
                responseHeaders = mapOf("Content-Type" to listOf("text/html")),
                responseBody = "<html>sign in</html>"
            )
        )
        assertTrue(failureLooksLikeAuthenticationRequired(RuntimeException("401 login required")))
        assertFalse(
            livyResponseLooksLikeAuthenticationRequired(
                httpCode = 500,
                responseHeaders = emptyMap(),
                responseBody = "plain server failure"
            )
        )
        assertFalse(failureLooksLikeAuthenticationRequired(RuntimeException("connection refused")))
    }
}
