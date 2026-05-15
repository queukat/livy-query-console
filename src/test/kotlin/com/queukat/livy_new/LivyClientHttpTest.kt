package com.queukat.livy_new

import com.google.gson.GsonBuilder
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class LivyClientHttpTest {

    @Test
    fun session_and_statement_endpoints_send_expected_requests_and_parse_responses() {
        val interceptor = RecordingInterceptor { request ->
            when (request.method to request.url.encodedPath) {
                "POST" to "/sessions" -> ResponseSpec(body = """{"id":10,"state":"starting"}""")
                "GET" to "/sessions/10" -> ResponseSpec(body = """{"id":10,"state":"idle"}""")
                "DELETE" to "/sessions/10" -> ResponseSpec(body = "{}")
                "POST" to "/sessions/10/statements" -> ResponseSpec(
                    body = """{"id":3,"state":"running","code":"select 1"}"""
                )
                "GET" to "/sessions/10/statements/3" -> ResponseSpec(
                    body = """{"id":3,"state":"available","output":{"status":"ok","execution_count":1}}"""
                )
                "POST" to "/sessions/10/statements/3/cancel" -> ResponseSpec(body = "{}")
                else -> fail("Unexpected request: ${request.method} ${request.url}")
            }
        }
        val client = livyClient(interceptor)

        assertEquals(10, client.createSession(SessionConfig(kind = "sql", proxyUser = "alice")).id)
        assertEquals("idle", client.getSession(10).state)
        client.deleteSession(10)
        assertEquals(3, client.runStatement(10, "select 1").id)
        assertEquals(1, client.getStatement(10, 3).output?.executionCount)
        client.cancelStatement(10, 3)

        assertEquals(
            listOf(
                "POST /sessions",
                "GET /sessions/10",
                "DELETE /sessions/10",
                "POST /sessions/10/statements",
                "GET /sessions/10/statements/3",
                "POST /sessions/10/statements/3/cancel"
            ),
            interceptor.requests.map { "${it.method} ${it.url.encodedPath}" }
        )
        assertContains(interceptor.requests[0].bodyAsString(), """"proxyUser":"alice"""")
        assertContains(interceptor.requests[3].bodyAsString(), """"code":"select 1"""")
        assertContains(interceptor.requests[5].bodyAsString(), """"msg":"cancel"""")
    }

    @Test
    fun list_endpoints_parse_livy_payloads_and_page_until_complete() {
        val interceptor = RecordingInterceptor { request ->
            when (request.url.encodedPath) {
                "/sessions" -> when (request.url.query) {
                    "from=5&size=6" -> ResponseSpec(
                        body = """{"from":5,"total":7,"sessions":[{"id":7,"state":"idle"}]}"""
                    )
                    "from=0&size=2" -> ResponseSpec(
                        body = """{"from":0,"total":3,"sessions":[{"id":1},{"id":2}]}"""
                    )
                    "from=2&size=2" -> ResponseSpec(
                        body = """{"from":2,"total":3,"sessions":[{"id":3}]}"""
                    )
                    else -> fail("Unexpected sessions query: ${request.url.query}")
                }
                "/sessions/10/log" -> ResponseSpec(
                    body = """{"id":10,"from":1,"total":2,"log":["line one","line two"]}"""
                )
                "/sessions/10/statements" -> ResponseSpec(
                    body = """{"statements":[{"id":4,"state":"available"},{"id":5,"state":"running"}]}"""
                )
                else -> fail("Unexpected request: ${request.method} ${request.url}")
            }
        }
        val client = livyClient(interceptor)

        assertEquals(listOf(7), client.getSessions(from = 5, size = 6).map { it.id })
        assertEquals(listOf(1, 2, 3), client.getAllSessions(pageSize = 2).map { it.id })
        assertEquals(listOf("line one", "line two"), client.getSessionLogs(10, from = 1, size = 2))
        assertEquals(listOf(4, 5), client.listStatements(10, from = 1, size = 2, orderDesc = true).map { it.id })

        assertTrue(interceptor.requests.any { it.url.toString().endsWith("/sessions/10/log?from=1&size=2") })
        assertTrue(interceptor.requests.any { it.url.toString().endsWith("/sessions/10/statements?from=1&size=2&order=desc") })
    }

    @Test
    fun connection_diagnostics_detect_auth_html_and_transport_failures() {
        val authClient = livyClient(
            RecordingInterceptor {
                ResponseSpec(
                    body = "<html>Please login with SSO</html>",
                    headers = mapOf("Content-Type" to listOf("text/html"))
                )
            }
        )

        val authDiagnostics = authClient.testConnectionDetailed()

        assertFalse(authDiagnostics.success)
        assertTrue(authDiagnostics.authRequired)
        assertContains(authDiagnostics.summary(), "requires browser authentication")

        val failingClient = livyClient(RecordingInterceptor { throw IOException("network down") })

        val failureDiagnostics = failingClient.testConnectionDetailed()

        assertFalse(failureDiagnostics.success)
        assertEquals(IOException::class.java.name, failureDiagnostics.exceptionClass)
        assertContains(failureDiagnostics.summary(), "network down")
    }

    @Test
    fun failed_livy_requests_throw_http_or_auth_exceptions_with_body_context() {
        val httpFailure = livyClient(
            RecordingInterceptor {
                ResponseSpec(code = 500, message = "Server Error", body = "nope")
            }
        )

        val ioFailure = runCatching { httpFailure.getSession(99) }.exceptionOrNull()

        assertIs<IOException>(ioFailure)
        assertContains(ioFailure.message.orEmpty(), "500 Server Error")
        assertContains(ioFailure.message.orEmpty(), "body=nope")

        val authFailure = livyClient(
            RecordingInterceptor {
                ResponseSpec(
                    code = 401,
                    message = "Unauthorized",
                    body = "<html>login</html>",
                    headers = mapOf("Content-Type" to listOf("text/html"))
                )
            }
        )

        val authException = runCatching {
            authFailure.createSession(SessionConfig(kind = "spark"))
        }.exceptionOrNull()

        assertIs<LivyAuthenticationRequiredException>(authException)
        assertEquals(401, authException.httpCode)
        assertContains(authException.responseBodyPreview, "login")
    }

    private fun livyClient(interceptor: RecordingInterceptor): LivyClient =
        LivyClient(
            baseUrl = "http://livy.test/",
            client = OkHttpClient.Builder().addInterceptor(interceptor).build(),
            gson = GsonBuilder().create()
        )

    private data class ResponseSpec(
        val code: Int = 200,
        val message: String = "OK",
        val body: String,
        val headers: Map<String, List<String>> = emptyMap()
    )

    private class RecordingInterceptor(
        private val responder: (Request) -> ResponseSpec
    ) : Interceptor {
        val requests = mutableListOf<Request>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests += request
            val spec = responder(request)
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(spec.code)
                .message(spec.message)
                .headers(spec.headers.toOkHttpHeaders())
                .body(spec.body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }
}

private fun Map<String, List<String>>.toOkHttpHeaders(): Headers {
    val builder = Headers.Builder()
    forEach { (name, values) ->
        values.forEach { value -> builder.add(name, value) }
    }
    return builder.build()
}

private fun Request.bodyAsString(): String {
    val buffer = okio.Buffer()
    body?.writeTo(buffer)
    return buffer.readUtf8()
}
