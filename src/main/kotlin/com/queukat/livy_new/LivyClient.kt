package com.queukat.livy_new

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Livy REST client. Uses shared OkHttpClient & Gson (see LivyClientProvider).
 */
class LivyClient(
    baseUrl: String,
    private val client: OkHttpClient,
    private val gson: Gson
) {
    private val log = Logger.getInstance(LivyClient::class.java)
    private val baseUrl: String = baseUrl.trimEnd('/')

    fun testConnectionDetailed(): ConnectionTestDiagnostics {
        val requestUrl = "$baseUrl/sessions"
        val startedAtMs = System.currentTimeMillis()
        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = runCatching { readBodyOrEmpty(response) }
                    .getOrElse { "<failed to read response body: ${it.javaClass.name}: ${it.message}>" }
                ConnectionTestDiagnostics(
                    requestUrl = requestUrl,
                    success = response.isSuccessful,
                    elapsedMs = System.currentTimeMillis() - startedAtMs,
                    httpCode = response.code,
                    httpMessage = response.message,
                    responseHeaders = response.headers.toMultimap(),
                    responseBodyPreview = truncateForDiagnostics(responseBody, 12_000)
                )
            }
        } catch (t: Throwable) {
            ConnectionTestDiagnostics(
                requestUrl = requestUrl,
                success = false,
                elapsedMs = System.currentTimeMillis() - startedAtMs,
                exceptionClass = t.javaClass.name,
                exceptionMessage = t.message,
                causeChain = buildCauseChain(t)
            )
        }
    }

    fun createSession(sessionConfig: SessionConfig): Session {
        val url = "$baseUrl/sessions"
        val json = gson.toJson(sessionConfig)

        log.info("createSession: $url")

        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = readBodyOrEmpty(response)
            if (!response.isSuccessful) {
                throw IOException("createSession failed: ${response.code} ${response.message}. body=$bodyStr")
            }
            return gson.fromJson(bodyStr, Session::class.java)
        }
    }

    fun getSession(sessionId: Int): Session {
        val url = "$baseUrl/sessions/$sessionId"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            val bodyStr = readBodyOrEmpty(response)
            if (!response.isSuccessful) throw IOException("getSession failed: ${response.code} ${response.message}. body=$bodyStr")
            return gson.fromJson(bodyStr, Session::class.java)
        }
    }

    fun deleteSession(sessionId: Int) {
        val url = "$baseUrl/sessions/$sessionId"
        val request = Request.Builder().url(url).delete().build()

        client.newCall(request).execute().use { response ->
            val bodyStr = readBodyOrEmpty(response)
            if (!response.isSuccessful) throw IOException("deleteSession failed: ${response.code} ${response.message}. body=$bodyStr")
        }
    }

    fun runStatement(sessionId: Int, code: String): Statement {
        val url = "$baseUrl/sessions/$sessionId/statements"
        val json = gson.toJson(mapOf("code" to code))

        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = readBodyOrEmpty(response)
            if (!response.isSuccessful) throw IOException("runStatement failed: ${response.code} ${response.message}. body=$bodyStr")
            return gson.fromJson(bodyStr, Statement::class.java)
        }
    }

    fun getSessionLogs(sessionId: Int, from: Int = 0, size: Int = 100): List<String> {
        val url = "$baseUrl/sessions/$sessionId/log?from=$from&size=$size"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            val bodyStr = readBodyOrEmpty(response)
            if (!response.isSuccessful) throw IOException("getSessionLogs failed: ${response.code} ${response.message}. body=$bodyStr")
            return gson.fromJson(bodyStr, SessionLogs::class.java).log
        }
    }

    fun getSessions(from: Int = 0, size: Int = 10): List<Session> {
        return getSessionsResponse(from, size).sessions
    }

    fun getAllSessions(pageSize: Int = 100): List<Session> {
        val allSessions = mutableListOf<Session>()
        var from = 0

        while (true) {
            val page = getSessionsResponse(from, pageSize)
            val sessions = page.sessions
            if (sessions.isEmpty()) break

            allSessions.addAll(sessions)

            val total = page.total
            if (sessions.size < pageSize || (total != null && allSessions.size >= total)) {
                break
            }
            from += sessions.size
        }

        return allSessions
    }

    fun getBaseUrl(): String = baseUrl

    private fun getSessionsResponse(from: Int, size: Int): SessionsResponse {
        val url = "$baseUrl/sessions?from=$from&size=$size"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            val bodyStr = readBodyOrEmpty(response)
            if (!response.isSuccessful) throw IOException("getSessions failed: ${response.code} ${response.message}. body=$bodyStr")
            return gson.fromJson(bodyStr, SessionsResponse::class.java)
        }
    }

    fun getStatement(sessionId: Int, statementId: Int): Statement {
        val url = "$baseUrl/sessions/$sessionId/statements/$statementId"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            val bodyStr = readBodyOrEmpty(response)
            if (!response.isSuccessful) throw IOException("getStatement failed: ${response.code} ${response.message}. body=$bodyStr")
            return gson.fromJson(bodyStr, Statement::class.java)
        }
    }

    fun cancelStatement(sessionId: Int, statementId: Int) {
        val url = "$baseUrl/sessions/$sessionId/statements/$statementId/cancel"
        val json = gson.toJson(mapOf("msg" to "cancel"))

        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = readBodyOrEmpty(response)
            if (!response.isSuccessful) throw IOException("cancelStatement failed: ${response.code} ${response.message}. body=$bodyStr")
        }
    }

    fun listStatements(sessionId: Int, from: Int = 0, size: Int = 10, orderDesc: Boolean = false): List<Statement> {
        val orderParam = if (orderDesc) "&order=desc" else ""
        val url = "$baseUrl/sessions/$sessionId/statements?from=$from&size=$size$orderParam"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            val bodyStr = readBodyOrEmpty(response)
            if (!response.isSuccessful) throw IOException("listStatements failed: ${response.code} ${response.message}. body=$bodyStr")
            return gson.fromJson(bodyStr, StatementsResponse::class.java).statements ?: emptyList()
        }
    }

    private fun readBodyOrEmpty(response: Response): String = response.body?.string().orEmpty()

    private fun truncateForDiagnostics(value: String, maxChars: Int): String {
        if (value.length <= maxChars) return value
        return value.take(maxChars) + "\n... [truncated, total ${value.length} chars]"
    }

    private fun buildCauseChain(t: Throwable): List<String> {
        val result = mutableListOf<String>()
        var current = t.cause
        while (current != null && result.size < 10) {
            val message = current.message?.trim().orEmpty()
            result.add("${current.javaClass.name}: $message")
            current = current.cause
        }
        return result
    }
}

data class StatementsResponse(val statements: List<Statement>? = null)

data class ConnectionTestDiagnostics(
    val requestUrl: String,
    val success: Boolean,
    val elapsedMs: Long,
    val httpCode: Int? = null,
    val httpMessage: String? = null,
    val responseHeaders: Map<String, List<String>> = emptyMap(),
    val responseBodyPreview: String? = null,
    val exceptionClass: String? = null,
    val exceptionMessage: String? = null,
    val causeChain: List<String> = emptyList()
) {
    fun summary(): String {
        if (success) {
            return "Connection successful (HTTP ${httpCode ?: "?"} ${httpMessage.orEmpty().trim()}, ${elapsedMs} ms)"
        }
        if (httpCode != null) {
            return "Connection failed (HTTP $httpCode ${httpMessage.orEmpty().trim()}, ${elapsedMs} ms)"
        }
        if (!exceptionClass.isNullOrBlank()) {
            return "Connection failed: $exceptionClass: ${exceptionMessage.orEmpty()} (${elapsedMs} ms)"
        }
        return "Connection failed (${elapsedMs} ms)"
    }

    fun toDiagnosticsText(): String = buildString {
        appendLine("Livy Connection Diagnostics")
        appendLine("Request URL: $requestUrl")
        appendLine("Elapsed: ${elapsedMs} ms")
        appendLine("Result: ${if (success) "SUCCESS" else "FAILURE"}")

        if (httpCode != null) {
            appendLine("HTTP: $httpCode ${httpMessage.orEmpty().trim()}")
        }

        if (responseHeaders.isNotEmpty()) {
            appendLine()
            appendLine("Response Headers:")
            responseHeaders.toSortedMap().forEach { (key, values) ->
                appendLine("$key: ${values.joinToString(", ")}")
            }
        }

        if (!responseBodyPreview.isNullOrBlank()) {
            appendLine()
            appendLine("Response Body (preview):")
            appendLine(responseBodyPreview)
        }

        if (!exceptionClass.isNullOrBlank()) {
            appendLine()
            appendLine("Exception: $exceptionClass")
            if (!exceptionMessage.isNullOrBlank()) appendLine("Message: $exceptionMessage")
        }

        if (causeChain.isNotEmpty()) {
            appendLine()
            appendLine("Cause Chain:")
            causeChain.forEach { appendLine("- $it") }
        }
    }
}
