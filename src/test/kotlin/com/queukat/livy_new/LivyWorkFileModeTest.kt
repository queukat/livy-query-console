package com.queukat.livy_new

import com.intellij.openapi.fileTypes.FileTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LivyWorkFileModeTest {

    @Test
    fun work_file_mode_resolves_execution_kind_and_path_suffix() {
        assertEquals(LivyWorkFileMode.SQL, LivyWorkFileMode.fromExecutionKind(" SQL "))
        assertEquals(LivyWorkFileMode.PYTHON, LivyWorkFileMode.fromExecutionKind("python"))
        assertEquals(LivyWorkFileMode.PYTHON, LivyWorkFileMode.fromExecutionKind("pyspark"))
        assertEquals(LivyWorkFileMode.PLAIN, LivyWorkFileMode.fromExecutionKind("spark"))

        assertEquals(LivyWorkFileMode.SQL, LivyWorkFileMode.fromPathSuffix(" sql "))
        assertEquals(LivyWorkFileMode.PYTHON, LivyWorkFileMode.fromPathSuffix("python"))
        assertEquals(LivyWorkFileMode.PLAIN, LivyWorkFileMode.fromPathSuffix("spark"))
        assertNull(LivyWorkFileMode.fromPathSuffix("scala"))
    }

    @Test
    fun execution_target_uses_snapshot_kind_for_work_file_mode() {
        val target = LivyExecutionTarget(
            profileId = "p1",
            profileName = "Profile",
            baseUrl = "http://livy",
            settingsSnapshot = createProfile().apply { kind = "sql" }
        )

        assertEquals(LivyWorkFileMode.SQL, target.workFileMode())
    }

    @Test
    fun editor_file_type_resolution_falls_back_to_plain_text_when_no_extension_is_preferred() {
        assertEquals(FileTypes.PLAIN_TEXT, LivyWorkFileMode.PLAIN.resolveEditorFileType())
        assertNotNull(LivyWorkFileMode.SQL.resolveEditorFileType())
    }
}
