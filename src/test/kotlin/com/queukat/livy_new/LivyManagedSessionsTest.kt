package com.queukat.livy_new

import kotlin.test.Test
import kotlin.test.assertEquals

class LivyManagedSessionsTest {

    @Test
    fun remember_and_match_only_for_same_server_and_fingerprint() {
        val state = LivyPluginSettings.PluginState()

        LivyManagedSessions.remember(state, sessionId = 10, serverUrl = "http://livy-a/", fingerprint = "fp-a")
        LivyManagedSessions.remember(state, sessionId = 11, serverUrl = "http://livy-a", fingerprint = "fp-b")
        LivyManagedSessions.remember(state, sessionId = 12, serverUrl = "http://livy-b", fingerprint = "fp-a")

        assertEquals(setOf(10), LivyManagedSessions.matchingSessionIds(state, "http://livy-a", "fp-a"))
        assertEquals(setOf(11), LivyManagedSessions.matchingSessionIds(state, "http://livy-a/", "fp-b"))
        assertEquals(setOf(12), LivyManagedSessions.matchingSessionIds(state, "http://livy-b", "fp-a"))
    }

    @Test
    fun prune_and_forget_only_touch_target_server_entries() {
        val state = LivyPluginSettings.PluginState()

        LivyManagedSessions.remember(state, sessionId = 1, serverUrl = "http://livy-a", fingerprint = "fp-a", createdAtMs = 1)
        LivyManagedSessions.remember(state, sessionId = 2, serverUrl = "http://livy-a", fingerprint = "fp-b", createdAtMs = 2)
        LivyManagedSessions.remember(state, sessionId = 3, serverUrl = "http://livy-b", fingerprint = "fp-c", createdAtMs = 3)

        LivyManagedSessions.pruneMissingForServer(state, "http://livy-a", setOf(2))
        assertEquals(setOf(2), LivyManagedSessions.managedSessionIdsForServer(state, "http://livy-a"))
        assertEquals(setOf(3), LivyManagedSessions.managedSessionIdsForServer(state, "http://livy-b"))

        LivyManagedSessions.forget(state, sessionId = 3, serverUrl = "http://livy-b")
        assertEquals(emptySet(), LivyManagedSessions.managedSessionIdsForServer(state, "http://livy-b"))
    }
}
