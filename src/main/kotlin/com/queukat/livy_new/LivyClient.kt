package com.queukat.livy_new

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
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

    fun testConnection(): Boolean {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/sessions")
                .get()
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: IOException) {
            false
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
            val bodyStr = response.body?.string()
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
            val bodyStr = response.body?.string()
            if (!response.isSuccessful) throw IOException("getSession failed: ${response.code} ${response.message}. body=$bodyStr")
            return gson.fromJson(bodyStr, Session::class.java)
        }
    }

    fun deleteSession(sessionId: Int) {
        val url = "$baseUrl/sessions/$sessionId"
        val request = Request.Builder().url(url).delete().build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string()
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
            val bodyStr = response.body?.string()
            if (!response.isSuccessful) throw IOException("runStatement failed: ${response.code} ${response.message}. body=$bodyStr")
            return gson.fromJson(bodyStr, Statement::class.java)
        }
    }

    fun getSessionState(sessionId: Int): String? {
        val url = "$baseUrl/sessions/$sessionId/state"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string()
            if (!response.isSuccessful) throw IOException("getSessionState failed: ${response.code} ${response.message}. body=$bodyStr")
            return gson.fromJson(bodyStr, SessionState::class.java).state
        }
    }

    fun getSessionLogs(sessionId: Int, from: Int = 0, size: Int = 100): List<String> {
        val url = "$baseUrl/sessions/$sessionId/log?from=$from&size=$size"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string()
            if (!response.isSuccessful) throw IOException("getSessionLogs failed: ${response.code} ${response.message}. body=$bodyStr")
            return gson.fromJson(bodyStr, SessionLogs::class.java).log ?: emptyList()
        }
    }

    fun getSessions(from: Int = 0, size: Int = 10): List<Session> {
        val url = "$baseUrl/sessions?from=$from&size=$size"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string()
            if (!response.isSuccessful) throw IOException("getSessions failed: ${response.code} ${response.message}. body=$bodyStr")
            return gson.fromJson(bodyStr, SessionsResponse::class.java).sessions ?: emptyList()
        }
    }

    fun getStatement(sessionId: Int, statementId: Int): Statement {
        val url = "$baseUrl/sessions/$sessionId/statements/$statementId"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string()
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
            val bodyStr = response.body?.string()
            if (!response.isSuccessful) throw IOException("cancelStatement failed: ${response.code} ${response.message}. body=$bodyStr")
        }
    }

    fun getSessionCompletions(sessionId: Int, code: String, cursor: Int, kind: String? = null): List<String> {
        val url = "$baseUrl/sessions/$sessionId/completion"

        val bodyMap = linkedMapOf<String, Any>(
            "code" to code,
            "cursor" to cursor
        )
        if (!kind.isNullOrBlank()) bodyMap["kind"] = kind

        val json = gson.toJson(bodyMap)

        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string()
            if (!response.isSuccessful) throw IOException("completion failed: ${response.code} ${response.message}. body=$bodyStr")
            return gson.fromJson(bodyStr, CompletionResponse::class.java).candidates ?: emptyList()
        }
    }

    fun listStatements(sessionId: Int, from: Int = 0, size: Int = 10, orderDesc: Boolean = false): List<Statement> {
        val orderParam = if (orderDesc) "&order=desc" else ""
        val url = "$baseUrl/sessions/$sessionId/statements?from=$from&size=$size$orderParam"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string()
            if (!response.isSuccessful) throw IOException("listStatements failed: ${response.code} ${response.message}. body=$bodyStr")
            return gson.fromJson(bodyStr, StatementsResponse::class.java).statements ?: emptyList()
        }
    }
}

data class CompletionResponse(val candidates: List<String>? = null)
data class StatementsResponse(val statements: List<Statement>? = null)
