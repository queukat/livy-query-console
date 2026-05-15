package com.queukat.livy_new

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

private val JSON_MEDIA_TYPE = "application/json".toMediaType()

/**
 * Livy REST client. Uses shared OkHttpClient & Gson (see LivyClientProvider).
 */
class LivyClient(
    baseUrl: String,
    private val client: OkHttpClient,
    private val gson: Gson
) : LivySessionClient {
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
                val authRequired = livyResponseLooksLikeAuthenticationRequired(
                    httpCode = response.code,
                    responseHeaders = response.headers.toMultimap(),
                    responseBody = responseBody
                )
                ConnectionTestDiagnostics(
                    requestUrl = requestUrl,
                    success = response.isSuccessful && !authRequired,
                    elapsedMs = System.currentTimeMillis() - startedAtMs,
                    httpCode = response.code,
                    httpMessage = response.message,
                    responseHeaders = response.headers.toMultimap(),
                    responseBodyPreview = truncateForDiagnostics(responseBody, 12_000),
                    authRequired = authRequired
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

    override fun createSession(sessionConfig: SessionConfig): Session {
        val url = "$baseUrl/sessions"
        val json = gson.toJson(sessionConfig)

        log.info("createSession: $url")

        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = readBodyOrEmpty(response)
            throwIfAuthenticationRequired(response, bodyStr)
            if (!response.isSuccessful) {
                throw IOException("createSession failed: ${response.code} ${response.message}. body=$bodyStr")
            }
            return gson.fromJson(bodyStr, Session::class.java)
        }
    }

    override fun getSession(sessionId: Int): Session {
        val url = "$baseUrl/sessions/$sessionId"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            val bodyStr = readBodyOrEmpty(response)
            throwIfAuthenticationRequired(response, bodyStr)
            if (!response.isSuccessful) throw IOException("getSession failed: ${response.code} ${response.message}. body=$bodyStr")
            return gson.fromJson(bodyStr, Session::class.java)
        }
    }

    override fun deleteSession(sessionId: Int) {
        val url = "$baseUrl/sessions/$sessionId"
        val request = Request.Builder().url(url).delete().build()

        client.newCall(request).execute().use { response ->
            val bodyStr = readBodyOrEmpty(response)
            throwIfAuthenticationRequired(response, bodyStr)
            if (!response.isSuccessful) throw IOException("deleteSession failed: ${response.code} ${response.message}. body=$bodyStr")
        }
    }

    fun runStatement(sessionId: Int, code: String): Statement {
        val url = "$baseUrl/sessions/$sessionId/statements"
        val json = gson.toJson(mapOf("code" to code))

        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = readBodyOrEmpty(response)
            throwIfAuthenticationRequired(response, bodyStr)
            if (!response.isSuccessful) throw IOException("runStatement failed: ${response.code} ${response.message}. body=$bodyStr")
            return gson.fromJson(bodyStr, Statement::class.java)
        }
    }

    fun getSessionLogs(sessionId: Int, from: Int = 0, size: Int = 100): List<String> {
        val url = "$baseUrl/sessions/$sessionId/log?from=$from&size=$size"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            val bodyStr = readBodyOrEmpty(response)
            throwIfAuthenticationRequired(response, bodyStr)
            if (!response.isSuccessful) throw IOException("getSessionLogs failed: ${response.code} ${response.message}. body=$bodyStr")
            return gson.fromJson(bodyStr, SessionLogs::class.java).log
        }
    }

    fun getSessions(from: Int = 0, size: Int = 10): List<Session> {
        return getSessionsResponse(from, size).sessions
    }

    override fun getAllSessions(): List<Session> = getAllSessions(pageSize = 100)

    fun getAllSessions(pageSize: Int): List<Session> {
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

    override fun getBaseUrl(): String = baseUrl

    private fun getSessionsResponse(from: Int, size: Int): SessionsResponse {
        val url = "$baseUrl/sessions?from=$from&size=$size"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            val bodyStr = readBodyOrEmpty(response)
            throwIfAuthenticationRequired(response, bodyStr)
            if (!response.isSuccessful) throw IOException("getSessions failed: ${response.code} ${response.message}. body=$bodyStr")
            return gson.fromJson(bodyStr, SessionsResponse::class.java)
        }
    }

    fun getStatement(sessionId: Int, statementId: Int): Statement {
        val url = "$baseUrl/sessions/$sessionId/statements/$statementId"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            val bodyStr = readBodyOrEmpty(response)
            throwIfAuthenticationRequired(response, bodyStr)
            if (!response.isSuccessful) throw IOException("getStatement failed: ${response.code} ${response.message}. body=$bodyStr")
            return gson.fromJson(bodyStr, Statement::class.java)
        }
    }

    fun cancelStatement(sessionId: Int, statementId: Int) {
        val url = "$baseUrl/sessions/$sessionId/statements/$statementId/cancel"
        val json = gson.toJson(mapOf("msg" to "cancel"))

        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = readBodyOrEmpty(response)
            throwIfAuthenticationRequired(response, bodyStr)
            if (!response.isSuccessful) throw IOException("cancelStatement failed: ${response.code} ${response.message}. body=$bodyStr")
        }
    }

    fun listStatements(sessionId: Int, from: Int = 0, size: Int = 10, orderDesc: Boolean = false): List<Statement> {
        val orderParam = if (orderDesc) "&order=desc" else ""
        val url = "$baseUrl/sessions/$sessionId/statements?from=$from&size=$size$orderParam"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            val bodyStr = readBodyOrEmpty(response)
            throwIfAuthenticationRequired(response, bodyStr)
            if (!response.isSuccessful) throw IOException("listStatements failed: ${response.code} ${response.message}. body=$bodyStr")
            return gson.fromJson(bodyStr, StatementsResponse::class.java).statements ?: emptyList()
        }
    }

    private fun readBodyOrEmpty(response: Response): String = response.body?.string().orEmpty()

    private fun throwIfAuthenticationRequired(response: Response, responseBody: String) {
        if (!livyResponseLooksLikeAuthenticationRequired(response.code, response.headers.toMultimap(), responseBody)) {
            return
        }
        throw LivyAuthenticationRequiredException(
            httpCode = response.code,
            httpMessage = response.message,
            responseHeaders = response.headers.toMultimap(),
            responseBodyPreview = truncateForDiagnostics(responseBody, 12_000)
        )
    }

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
    val causeChain: List<String> = emptyList(),
    val authRequired: Boolean = false
) {
    fun summary(): String {
        if (authRequired) {
            return "Connection requires browser authentication (${httpCode ?: "?"} ${httpMessage.orEmpty().trim()}, ${elapsedMs} ms)"
        }
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
        appendAuthHint()
        appendHttpDetails()
        appendResponseHeaders()
        appendResponseBody()
        appendExceptionDetails()
        appendCauseChain()
    }

    private fun StringBuilder.appendAuthHint() {
        if (authRequired) {
            appendLine("Auth hint: Browser authentication appears to be required.")
        }
    }

    private fun StringBuilder.appendHttpDetails() {
        if (httpCode != null) {
            appendLine("HTTP: $httpCode ${httpMessage.orEmpty().trim()}")
        }
    }

    private fun StringBuilder.appendResponseHeaders() {
        if (responseHeaders.isNotEmpty()) {
            appendLine()
            appendLine("Response Headers:")
            responseHeaders.toSortedMap().forEach { (key, values) ->
                appendLine("$key: ${values.joinToString(", ")}")
            }
        }
    }

    private fun StringBuilder.appendResponseBody() {
        if (!responseBodyPreview.isNullOrBlank()) {
            appendLine()
            appendLine("Response Body (preview):")
            appendLine(responseBodyPreview)
        }
    }

    private fun StringBuilder.appendExceptionDetails() {
        if (!exceptionClass.isNullOrBlank()) {
            appendLine()
            appendLine("Exception: $exceptionClass")
            if (!exceptionMessage.isNullOrBlank()) appendLine("Message: $exceptionMessage")
        }
    }

    private fun StringBuilder.appendCauseChain() {
        if (causeChain.isNotEmpty()) {
            appendLine()
            appendLine("Cause Chain:")
            causeChain.forEach { appendLine("- $it") }
        }
    }
}

class LivyAuthenticationRequiredException(
    val httpCode: Int,
    val httpMessage: String,
    val responseHeaders: Map<String, List<String>>,
    val responseBodyPreview: String
) : IOException(
    buildString {
        append("Livy endpoint requires browser authentication")
        append(" (HTTP $httpCode ${httpMessage.trim()})")
    }
)

fun failureLooksLikeAuthenticationRequired(failure: Throwable): Boolean {
    if (failure is LivyAuthenticationRequiredException) return true
    val text = buildString {
        append(failure.message.orEmpty())
        if (failure.cause?.message != null) {
            append('\n')
            append(failure.cause?.message.orEmpty())
        }
    }
    return livyTextLooksLikeAuthenticationRequired(text)
}

fun livyResponseLooksLikeAuthenticationRequired(
    httpCode: Int,
    responseHeaders: Map<String, List<String>>,
    responseBody: String
): Boolean {
    val body = responseBody.lowercase()
    val locationHeader = responseHeaders.entries
        .firstOrNull { it.key.equals("location", ignoreCase = true) }
        ?.value
        ?.joinToString(" ")
        .orEmpty()
        .lowercase()
    val contentType = responseHeaders.entries
        .firstOrNull { it.key.equals("content-type", ignoreCase = true) }
        ?.value
        ?.joinToString(" ")
        .orEmpty()
        .lowercase()
    val loginMarkers = listOf(
        "/oauth2/",
        "openid-connect",
        "authoriz",
        "авториз",
        "sso",
        "sign in",
        "login",
        "<html"
    )
    val hasAuthMarker = loginMarkers.any { marker ->
        body.contains(marker) || locationHeader.contains(marker)
    }
    val htmlLike = contentType.contains("text/html") || body.contains("<html")
    if (httpCode in setOf(401, 403)) {
        return hasAuthMarker || htmlLike
    }
    if (httpCode in setOf(302, 303, 307, 308)) {
        return hasAuthMarker
    }
    if (httpCode in 200..299 && htmlLike) {
        return hasAuthMarker
    }
    return false
}

private fun livyTextLooksLikeAuthenticationRequired(text: String): Boolean {
    val normalized = text.lowercase()
    val strongMarkers = listOf(
        "requires browser authentication",
        "openid-connect",
        "/oauth2/",
        "авториз",
        "sign in",
        "please log in"
    )
    if (strongMarkers.any { normalized.contains(it) }) return true
    val hasHttpAuthCode = normalized.contains("401") || normalized.contains("403")
    val hasLoginHint = normalized.contains("sso") || normalized.contains("login") || normalized.contains("<html")
    return hasHttpAuthCode && hasLoginHint
}
