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

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson: Gson = GsonBuilder().create()

    private val cache = ConcurrentHashMap<String, LivyClient>()

    fun get(baseUrlRaw: String): LivyClient {
        val baseUrl = baseUrlRaw.trim().trimEnd('/')
        return cache.computeIfAbsent(baseUrl) { url ->
            LivyClient(url, okHttpClient, gson)
        }
    }

    fun fromSettings(): LivyClient {
        val url = LivyPluginSettings.getInstance().pluginState.livyServerUrl
        return get(url)
    }

    companion object {
        fun getInstance(): LivyClientProvider =
            ApplicationManager.getApplication().getService(LivyClientProvider::class.java)
    }
}
