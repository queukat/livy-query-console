package com.queukat.livy_new

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LivyManagedSessionsTest {

    @Test
    fun is_managed_session_respects_server_boundary() {
        val state = LivyPluginSettings.PluginState()
        LivyManagedSessions.remember(state, sessionId = 10, serverUrl = "http://livy-a", fingerprint = "fp-a")
        LivyManagedSessions.remember(state, sessionId = 10, serverUrl = "http://livy-b", fingerprint = "fp-b")

        assertTrue(LivyManagedSessions.isManagedSession(state, "http://livy-a", 10))
        assertTrue(LivyManagedSessions.isManagedSession(state, "http://livy-b", 10))
        assertFalse(LivyManagedSessions.isManagedSession(state, "http://livy-c", 10))
    }

    @Test
    fun forget_removes_only_bound_server_entry_when_server_is_provided() {
        val state = LivyPluginSettings.PluginState()
        LivyManagedSessions.remember(state, sessionId = 21, serverUrl = "http://livy-a", fingerprint = "fp-a")
        LivyManagedSessions.remember(state, sessionId = 21, serverUrl = "http://livy-b", fingerprint = "fp-b")

        LivyManagedSessions.forget(state, sessionId = 21, serverUrl = "http://livy-a")

        assertFalse(LivyManagedSessions.isManagedSession(state, "http://livy-a", 21))
        assertTrue(LivyManagedSessions.isManagedSession(state, "http://livy-b", 21))
    }
}
