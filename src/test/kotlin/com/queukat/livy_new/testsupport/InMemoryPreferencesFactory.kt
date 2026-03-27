package com.queukat.livy_new.testsupport

import java.util.prefs.AbstractPreferences
import java.util.prefs.BackingStoreException
import java.util.prefs.Preferences
import java.util.prefs.PreferencesFactory

class InMemoryPreferencesFactory : PreferencesFactory {
    private val userRoot = InMemoryPreferences(null, "")
    private val systemRoot = InMemoryPreferences(null, "")

    override fun userRoot(): Preferences = userRoot

    override fun systemRoot(): Preferences = systemRoot
}

private class InMemoryPreferences(
    parent: AbstractPreferences?,
    name: String
) : AbstractPreferences(parent, name) {

    private val values = linkedMapOf<String, String>()
    private val children = linkedMapOf<String, InMemoryPreferences>()

    override fun putSpi(key: String, value: String) {
        values[key] = value
    }

    override fun getSpi(key: String): String? = values[key]

    override fun removeSpi(key: String) {
        values.remove(key)
    }

    @Throws(BackingStoreException::class)
    override fun removeNodeSpi() {
        values.clear()
        children.clear()
    }

    @Throws(BackingStoreException::class)
    override fun keysSpi(): Array<String> = values.keys.toTypedArray()

    @Throws(BackingStoreException::class)
    override fun childrenNamesSpi(): Array<String> = children.keys.toTypedArray()

    override fun childSpi(name: String): AbstractPreferences =
        children.getOrPut(name) { InMemoryPreferences(this, name) }

    @Throws(BackingStoreException::class)
    override fun syncSpi() = Unit

    @Throws(BackingStoreException::class)
    override fun flushSpi() = Unit
}
