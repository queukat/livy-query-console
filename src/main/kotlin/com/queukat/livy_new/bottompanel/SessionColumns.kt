package com.queukat.livy_new.bottompanel

import com.queukat.livy_new.Session

data class SessionColumn(
    val id: String,
    val title: String,
    val extractor: (Session) -> Any?
)

object SessionColumns {

    val ALL: List<SessionColumn> = listOf(
        SessionColumn("id", "ID") { it.id ?: -1 },
        SessionColumn("appId", "App ID") { it.appId.orEmpty() },
        SessionColumn("owner", "Owner") { it.owner.orEmpty() },
        SessionColumn("proxyUser", "Proxy User") { it.proxyUser.orEmpty() },
        SessionColumn("kind", "Kind") { it.kind.orEmpty() },
        SessionColumn("state", "State") { it.state.orEmpty() },

        SessionColumn("driverMemory", "Driver Memory") { it.driverMemory.orEmpty() },
        SessionColumn("driverCores", "Driver Cores") { it.driverCores ?: 0 },
        SessionColumn("executorMemory", "Executor Memory") { it.executorMemory.orEmpty() },
        SessionColumn("executorCores", "Executor Cores") { it.executorCores ?: 0 },
        SessionColumn("numExecutors", "Num Executors") { it.numExecutors ?: 0 },

        SessionColumn("queue", "Queue") { it.queue.orEmpty() },
        SessionColumn("name", "Name") { it.name.orEmpty() },
        SessionColumn("heartbeatTimeout", "Heartbeat Timeout") { it.heartbeatTimeoutInSecond ?: 0 },
        SessionColumn("ttl", "TTL") { it.ttl.orEmpty() },

        SessionColumn("conf", "Conf") { confPreview(it.conf ?: emptyMap()) },
        SessionColumn("log", "Log") { shorten((it.log ?: emptyList()).joinToString("\n"), 400) }
    )

    val DEFAULT_VISIBLE_IDS: List<String> = listOf("id", "appId", "owner", "kind", "state", "log")

    private val byId: Map<String, SessionColumn> = ALL.associateBy { it.id }

    fun getById(id: String): SessionColumn? = byId[id]

    fun normalize(ids: List<String>): List<String> {
        val known = ids.filter { byId.containsKey(it) }
        return if (known.isNotEmpty()) known else DEFAULT_VISIBLE_IDS
    }

    fun titlesFor(ids: List<String>): Array<String> =
        normalize(ids).map { byId.getValue(it).title }.toTypedArray()

    fun valuesFor(session: Session, ids: List<String>): Array<Any?> =
        normalize(ids).map { byId.getValue(it).extractor(session) }.toTypedArray()

    private fun shorten(s: String, max: Int): String {
        val t = s.trim()
        if (t.length <= max) return t
        return t.substring(0, max - 1) + "…"
    }

    private fun confPreview(conf: Map<String, String>): String {
        if (conf.isEmpty()) return ""
        val head = conf.entries.take(3).joinToString("; ") { "${it.key}=${it.value}" }
        return if (conf.size <= 3) head else "$head; … (${conf.size})"
    }
}
