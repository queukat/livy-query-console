package com.queukat.livy_new

import com.google.gson.Gson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LivyModelsTest {

    @Test
    fun statement_output_maps_livy_execution_count_field() {
        val gson = Gson()

        val output = gson.fromJson(
            """{"status":"ok","execution_count":7,"data":{"text/plain":"done"}}""",
            StatementOutput::class.java
        )

        assertEquals(7, output.executionCount)
        assertTrue(""""execution_count":7""" in gson.toJson(output))
    }
}
