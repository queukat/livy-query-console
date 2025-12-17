package com.queukat.livy_new

/**
 * Более терпимые модели (nullable + дефолты), чтобы не падать на различиях версий Livy.
 */

data class Session(
    val id: Int? = null,
    val appId: String? = null,
    val owner: String? = null,
    val proxyUser: String? = null,
    val kind: String? = null,
    val state: String? = null,
    val driverMemory: String? = null,
    val driverCores: Int? = null,
    val executorMemory: String? = null,
    val executorCores: Int? = null,
    val numExecutors: Int? = null,
    val queue: String? = null,
    val name: String? = null,
    val conf: Map<String, String>? = null,
    val heartbeatTimeoutInSecond: Int? = null,
    val ttl: String? = null,
    val log: List<String>? = null
)

data class SessionsResponse(
    val from: Int? = null,
    val total: Int? = null,
    val sessions: List<Session> = emptyList()
)

data class SessionState(
    val id: Int? = null,
    val state: String? = null
)

data class SessionLogs(
    val id: Int? = null,
    val from: Int? = null,
    val total: Int? = null,
    val log: List<String> = emptyList()
)

data class Statement(
    val id: Int? = null,
    val code: String? = null,
    val state: String? = null,
    val output: StatementOutput? = null,
    val progress: Double? = null,
    val started: Long? = null,
    val completed: Long? = null
)

/**
 * ВАЖНО: для error Livy кладёт ename/evalue/traceback.
 * Раньше у тебя их не было => ты не видел причину ошибки.
 */
data class StatementOutput(
    val status: String? = null,
    val execution_count: Int? = null,
    val data: Map<String, Any?>? = null,

    // error details (при status="error")
    val ename: String? = null,
    val evalue: String? = null,
    val traceback: List<String>? = null
)

data class SessionConfig(
    val kind: String,
    val proxyUser: String? = null,
    val jars: List<String>? = null,
    val pyFiles: List<String>? = null,
    val files: List<String>? = null,
    val driverMemory: String? = null,
    val driverCores: Int? = null,
    val executorMemory: String? = null,
    val executorCores: Int? = null,
    val numExecutors: Int? = null,
    val archives: List<String>? = null,
    val queue: String? = null,
    val name: String? = null,
    val conf: Map<String, String>? = null,
    val heartbeatTimeoutInSecond: Int? = null,
    val ttl: String? = null
)
