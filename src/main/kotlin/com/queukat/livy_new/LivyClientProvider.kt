package com.queukat.livy_new

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Service(Service.Level.APP)
class LivyClientProvider {

    private val baseClientBuilder: OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)

    private val gson: Gson = GsonBuilder().create()

    private val cache = ConcurrentHashMap<String, LivyClient>()

    fun get(baseUrlRaw: String): LivyClient {
        val activeProfile = LivyPluginSettings.getInstance().pluginState.activeProfile()
        return get(activeProfile.id, baseUrlRaw)
    }

    fun get(profile: LivyPluginSettings.ConnectionProfileState): LivyClient =
        get(profile.id, profile.livyServerUrl)

    fun get(target: LivyExecutionTarget): LivyClient =
        get(target.profileId, target.baseUrl)

    fun get(profileId: String, baseUrlRaw: String): LivyClient {
        val baseUrl = baseUrlRaw.trim().trimEnd('/')
        val normalizedProfileId = profileId.trim().ifBlank { "default-profile" }
        val cacheKey = "$normalizedProfileId|$baseUrl"
        return cache.computeIfAbsent(cacheKey) {
            LivyClient(
                baseUrl,
                baseClientBuilder
                    .build()
                    .newBuilder()
                    .cookieJar(
                        LivyProfileCookieJar(
                            profileId = normalizedProfileId,
                            baseUrl = baseUrl,
                            authStore = LivyAuthSessionStore.getInstance()
                        )
                    )
                    .build(),
                gson
            )
        }
    }

    fun fromSettings(): LivyClient = fromActiveProfile()

    fun fromActiveProfile(): LivyClient {
        val settings = LivyPluginSettings.getInstance().pluginState
        return get(settings.activeProfile())
    }

    companion object {
        fun getInstance(): LivyClientProvider =
            ApplicationManager.getApplication().getService(LivyClientProvider::class.java)
    }
}
