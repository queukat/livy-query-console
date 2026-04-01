package com.queukat.livy_new

interface LivySessionClient {
    fun createSession(sessionConfig: SessionConfig): Session
    fun getSession(sessionId: Int): Session
    fun deleteSession(sessionId: Int)
    fun getAllSessions(): List<Session>
    fun getBaseUrl(): String
}
