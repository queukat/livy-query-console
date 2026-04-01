package com.queukat.livy_new

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LivySessionSpecFactoryTest {

    @Test
    fun fingerprint_ignores_generated_name_but_keeps_runtime_config() {
        val settings = LivyPluginSettings.ConnectionProfileState().apply {
            kind = "sql"
            driverMemory = "4g"
            executorMemory = "8g"
            conf = "spark.sql.shuffle.partitions=16,spark.app.name=test"
        }

        val first = LivySessionSpecFactory.fromSettings(settings, "http://livy", generatedName = "Generated A")
        val second = LivySessionSpecFactory.fromSettings(settings, "http://livy/", generatedName = "Generated B")

        assertEquals(first.fingerprint, second.fingerprint)
        assertNotEquals(first.config.name, second.config.name)
        assertEquals("sql", first.config.kind)
    }

    @Test
    fun blank_values_get_safe_defaults() {
        val settings = LivyPluginSettings.ConnectionProfileState().apply {
            kind = ""
            driverMemory = ""
            executorMemory = ""
            driverCores = 0
            executorCores = 0
            numExecutors = 0
            heartbeatTimeoutInSecond = 0
        }

        val spec = LivySessionSpecFactory.fromSettings(settings, "http://livy", generatedName = "Generated")

        assertEquals("spark", spec.config.kind)
        assertEquals("1g", spec.config.driverMemory)
        assertEquals("1g", spec.config.executorMemory)
        assertEquals(1, spec.config.driverCores)
        assertEquals(1, spec.config.executorCores)
        assertEquals(2, spec.config.numExecutors)
        assertEquals(60, spec.config.heartbeatTimeoutInSecond)
    }
}
