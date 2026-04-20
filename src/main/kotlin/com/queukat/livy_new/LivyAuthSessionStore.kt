package com.queukat.livy_new

import com.google.gson.GsonBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.ui.jcef.JBCefCookie
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.APP)
class LivyAuthSessionStore {

    private val gson = GsonBuilder().create()
    private val sessionCache = ConcurrentHashMap<String, LivyStoredAuthSession>()

    fun saveBrowserSession(
        profileId: String,
        baseUrl: String,
        cookies: List<JBCefCookie>,
        savedAtMs: Long = System.currentTimeMillis()
    ): LivyAuthStatus {
        val normalizedBaseUrl = LivyManagedSessions.normalizeServerUrl(baseUrl)
        val storedCookies = normalizeStoredCookies(
            cookies.mapNotNull { it.toStoredCookieOrNull() },
            nowMs = savedAtMs
        )
        val session = LivyStoredAuthSession(
            baseUrl = normalizedBaseUrl,
            updatedAtMs = savedAtMs,
            cookies = storedCookies
        )
        persistSession(profileId, session)
        return status(profileId, normalizedBaseUrl, savedAtMs)
    }

    fun mergeResponseCookies(
        profileId: String,
        baseUrl: String,
        cookies: List<Cookie>,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (profileId.isBlank() || cookies.isEmpty()) return
        val existing = loadSession(profileId, nowMs)
        val merged = mergeStoredCookies(
            existing = existing?.cookies.orEmpty(),
            incoming = cookies.mapNotNull { it.toStoredCookieOrNull() },
            nowMs = nowMs
        )
        if (merged.isEmpty()) {
            clear(profileId)
            return
        }
        persistSession(
            profileId,
            LivyStoredAuthSession(
                baseUrl = existing?.baseUrl ?: LivyManagedSessions.normalizeServerUrl(baseUrl),
                updatedAtMs = nowMs,
                cookies = merged
            )
        )
    }

    fun cookiesForRequest(
        profileId: String,
        url: HttpUrl,
        nowMs: Long = System.currentTimeMillis()
    ): List<Cookie> {
        return loadSession(profileId, nowMs)
            ?.cookies
            .orEmpty()
            .mapNotNull { it.toOkHttpCookieOrNull() }
            .filter { it.matches(url) }
    }

    fun status(
        profileId: String,
        currentBaseUrl: String,
        nowMs: Long = System.currentTimeMillis()
    ): LivyAuthStatus {
        val session = loadSession(profileId, nowMs)
        if (session == null) {
            return LivyAuthStatus(
                hasSavedSession = false,
                storedCookieCount = 0,
                matchingCookieCount = 0
            )
        }

        val requestUrl = LivyManagedSessions.normalizeServerUrl(currentBaseUrl)
            .toHttpUrlOrNull()
        val matchingCount = requestUrl?.let { url ->
            session.cookies
                .mapNotNull { it.toOkHttpCookieOrNull() }
                .count { it.matches(url) }
        } ?: 0

        return LivyAuthStatus(
            hasSavedSession = true,
            storedCookieCount = session.cookies.size,
            matchingCookieCount = matchingCount,
            storedBaseUrl = session.baseUrl,
            updatedAtMs = session.updatedAtMs
        )
    }

    fun clear(profileId: String) {
        if (profileId.isBlank()) return
        sessionCache.remove(profileId)
        PasswordSafe.instance.set(credentialsFor(profileId), null)
    }

    private fun loadSession(
        profileId: String,
        nowMs: Long = System.currentTimeMillis()
    ): LivyStoredAuthSession? {
        if (profileId.isBlank()) return null

        sessionCache[profileId]?.let { cached ->
            val normalized = normalizeStoredSession(cached, nowMs)
            if (normalized == null) {
                clear(profileId)
                return null
            }
            if (normalized != cached) {
                persistSession(profileId, normalized)
            }
            return normalized
        }

        val raw = PasswordSafe.instance
            .get(credentialsFor(profileId))
            ?.getPasswordAsString()
            .orEmpty()
            .trim()
        if (raw.isBlank()) return null

        val loaded = runCatching { gson.fromJson(raw, LivyStoredAuthSession::class.java) }.getOrNull()
        val normalized = normalizeStoredSession(loaded, nowMs)
        if (normalized == null) {
            clear(profileId)
            return null
        }
        sessionCache[profileId] = normalized
        if (loaded != normalized) {
            persistSession(profileId, normalized)
        }
        return normalized
    }

    private fun persistSession(profileId: String, session: LivyStoredAuthSession?) {
        if (profileId.isBlank()) return
        val normalized = normalizeStoredSession(session)
        if (normalized == null) {
            clear(profileId)
            return
        }
        sessionCache[profileId] = normalized
        PasswordSafe.instance.set(
            credentialsFor(profileId),
            Credentials(profileId, gson.toJson(normalized))
        )
    }

    private fun credentialsFor(profileId: String): CredentialAttributes =
        CredentialAttributes(AUTH_SERVICE_NAME, profileId, LivyAuthSessionStore::class.java)

    companion object {
        private const val AUTH_SERVICE_NAME = "Livy Query Console Browser Auth"

        fun getInstance(): LivyAuthSessionStore =
            ApplicationManager.getApplication().getService(LivyAuthSessionStore::class.java)
    }
}

data class LivyAuthStatus(
    val hasSavedSession: Boolean,
    val storedCookieCount: Int,
    val matchingCookieCount: Int,
    val storedBaseUrl: String? = null,
    val updatedAtMs: Long? = null
) {
    fun toDisplayText(currentBaseUrl: String): String {
        if (!hasSavedSession) {
            return "Browser auth: no saved session for this profile."
        }

        val targetHost = currentBaseUrl.toHttpUrlOrNull()?.host
        val storedHost = storedBaseUrl?.toHttpUrlOrNull()?.host
        val hostHint = when {
            storedHost.isNullOrBlank() || storedHost == targetHost -> ""
            else -> " Saved session was captured for $storedHost."
        }

        return buildString {
            append("Browser auth: $storedCookieCount cookie(s) saved in Password Safe")
            if (matchingCookieCount > 0) {
                append(", $matchingCookieCount currently match this server.")
            } else {
                append(", none currently match this server.")
            }
            append(hostHint)
        }
    }
}

internal data class LivyStoredAuthSession(
    val baseUrl: String = "",
    val updatedAtMs: Long = 0L,
    val cookies: List<LivyStoredCookie> = emptyList()
)

internal data class LivyStoredCookie(
    val name: String = "",
    val value: String = "",
    val domain: String = "",
    val path: String = "/",
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val expiresAtMs: Long? = null
) {
    fun storageKey(): String = "${name.lowercase()}|${domain.lowercase()}|$path"

    fun isExpiredAt(nowMs: Long): Boolean = expiresAtMs?.let { it <= nowMs } == true

    fun normalizedOrNull(): LivyStoredCookie? {
        val normalizedName = name.trim()
        val normalizedValue = value
        val normalizedDomain = domain.trim().removePrefix(".")
        val normalizedPath = path.trim().ifBlank { "/" }
        if (normalizedName.isBlank() || normalizedValue.isBlank() || normalizedDomain.isBlank()) return null
        return copy(
            name = normalizedName,
            value = normalizedValue,
            domain = normalizedDomain,
            path = normalizedPath
        )
    }

    fun toOkHttpCookieOrNull(): Cookie? {
        val normalized = normalizedOrNull() ?: return null
        return runCatching {
            Cookie.Builder()
                .name(normalized.name)
                .value(normalized.value)
                .domain(normalized.domain)
                .path(normalized.path)
                .apply {
                    if (normalized.secure) secure()
                    if (normalized.httpOnly) httpOnly()
                    normalized.expiresAtMs?.let(::expiresAt)
                }
                .build()
        }.getOrNull()
    }
}

internal fun normalizeStoredSession(
    session: LivyStoredAuthSession?,
    nowMs: Long = System.currentTimeMillis()
): LivyStoredAuthSession? {
    session ?: return null
    val normalizedBaseUrl = session.baseUrl
        .trim()
        .takeIf { it.isNotBlank() }
        ?.let(LivyManagedSessions::normalizeServerUrl)
        ?: return null
    val normalizedCookies = normalizeStoredCookies(session.cookies, nowMs)
    if (normalizedCookies.isEmpty()) return null
    return LivyStoredAuthSession(
        baseUrl = normalizedBaseUrl,
        updatedAtMs = session.updatedAtMs.coerceAtLeast(0L),
        cookies = normalizedCookies
    )
}

internal fun normalizeStoredCookies(
    cookies: List<LivyStoredCookie>,
    nowMs: Long = System.currentTimeMillis()
): List<LivyStoredCookie> =
    mergeStoredCookies(emptyList(), cookies, nowMs)

internal fun mergeStoredCookies(
    existing: List<LivyStoredCookie>,
    incoming: List<LivyStoredCookie>,
    nowMs: Long = System.currentTimeMillis()
): List<LivyStoredCookie> {
    val merged = LinkedHashMap<String, LivyStoredCookie>()
    existing.forEach { cookie ->
        val normalized = cookie.normalizedOrNull()
        if (normalized != null && !normalized.isExpiredAt(nowMs)) {
            merged[normalized.storageKey()] = normalized
        }
    }
    incoming.forEach { cookie ->
        val normalized = cookie.normalizedOrNull()
        if (normalized == null) return@forEach
        if (normalized.isExpiredAt(nowMs)) {
            merged.remove(normalized.storageKey())
        } else {
            merged[normalized.storageKey()] = normalized
        }
    }
    return merged.values
        .sortedWith(compareBy<LivyStoredCookie> { it.domain.lowercase() }.thenBy { it.path }.thenBy { it.name.lowercase() })
}

internal fun JBCefCookie.toStoredCookieOrNull(): LivyStoredCookie? =
    LivyStoredCookie(
        name = name.orEmpty(),
        value = value.orEmpty(),
        domain = domain.orEmpty(),
        path = path.orEmpty(),
        secure = isSecure,
        httpOnly = isHttpOnly,
        expiresAtMs = if (hasExpires()) expires?.time else null
    ).normalizedOrNull()

internal fun Cookie.toStoredCookieOrNull(): LivyStoredCookie? =
    LivyStoredCookie(
        name = name,
        value = value,
        domain = domain,
        path = path,
        secure = secure,
        httpOnly = httpOnly,
        expiresAtMs = if (persistent) expiresAt else null
    ).normalizedOrNull()

class LivyProfileCookieJar(
    private val profileId: String,
    private val baseUrl: String,
    private val authStore: LivyAuthSessionStore
) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        authStore.mergeResponseCookies(profileId, baseUrl, cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        authStore.cookiesForRequest(profileId, url)
}
