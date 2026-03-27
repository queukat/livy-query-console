package com.queukat.livy_new

import java.util.UUID

data class LivySessionSpec(
    val config: SessionConfig,
    val fingerprint: String
)

object LivySessionSpecFactory {

    fun fromSettings(
        settings: LivyPluginSettings.PluginState,
        serverUrl: String,
        generatedName: String = "Livy Query Console ${UUID.randomUUID()}"
    ): LivySessionSpec {
        val normalizedServerUrl = LivyManagedSessions.normalizeServerUrl(serverUrl)
        val kind = settings.kind.trim().ifBlank { "spark" }
        val proxyUser = settings.proxyUser.trim().takeIf { it.isNotBlank() }
        val jars = parseCsvList(settings.jars)
        val pyFiles = parseCsvList(settings.pyFiles)
        val files = parseCsvList(settings.files)
        val archives = parseCsvList(settings.archives)
        val queue = settings.queue.trim().takeIf { it.isNotBlank() }
        val name = settings.name.trim().takeIf { it.isNotBlank() } ?: generatedName
        val conf = parseConfMap(settings.conf)
        val ttl = settings.ttl.trim().takeIf { it.isNotBlank() }
        val driverMemory = settings.driverMemory.ifBlank { "1g" }
        val executorMemory = settings.executorMemory.ifBlank { "1g" }
        val driverCores = settings.driverCores.takeIf { it > 0 } ?: 1
        val executorCores = settings.executorCores.takeIf { it > 0 } ?: 1
        val numExecutors = settings.numExecutors.takeIf { it > 0 } ?: 2
        val heartbeatTimeout = settings.heartbeatTimeoutInSecond.takeIf { it > 0 } ?: 60

        val config = SessionConfig(
            kind = kind,
            proxyUser = proxyUser,
            jars = jars,
            pyFiles = pyFiles,
            files = files,
            driverMemory = driverMemory,
            driverCores = driverCores,
            executorMemory = executorMemory,
            executorCores = executorCores,
            numExecutors = numExecutors,
            archives = archives,
            queue = queue,
            name = name,
            conf = conf,
            heartbeatTimeoutInSecond = heartbeatTimeout,
            ttl = ttl
        )

        val fingerprint = listOf(
            normalizedServerUrl,
            kind,
            proxyUser.orEmpty(),
            listFingerprint(jars),
            listFingerprint(pyFiles),
            listFingerprint(files),
            driverMemory,
            driverCores.toString(),
            executorMemory,
            executorCores.toString(),
            numExecutors.toString(),
            listFingerprint(archives),
            queue.orEmpty(),
            mapFingerprint(conf),
            heartbeatTimeout.toString(),
            ttl.orEmpty()
        ).joinToString("||")

        return LivySessionSpec(config = config, fingerprint = fingerprint)
    }

    internal fun parseCsvList(raw: String): List<String>? = raw
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .takeIf { it.isNotEmpty() }

    internal fun parseConfMap(raw: String): Map<String, String>? {
        if (raw.isBlank()) return null
        val pairs = raw
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { kv ->
                val parts = kv.split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }
        return pairs.takeIf { it.isNotEmpty() }?.toMap()
    }

    private fun listFingerprint(values: List<String>?): String = values?.joinToString("|").orEmpty()

    private fun mapFingerprint(values: Map<String, String>?): String = values
        ?.toSortedMap()
        ?.entries
        ?.joinToString("|") { "${it.key}=${it.value}" }
        .orEmpty()
}
