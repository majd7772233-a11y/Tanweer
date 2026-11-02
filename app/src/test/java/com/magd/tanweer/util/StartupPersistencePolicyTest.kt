package com.magd.tanweer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupPersistencePolicyTest {

    @Test
    fun savedGroupWinsOverDefaultGroup() {
        assertEquals(
            "class_10_C",
            AcademicSessionSelection.resolveGroupId("class_10_C", "class_10_B")
        )
    }

    @Test
    fun blankSavedGroupFallsBackToAccountDefault() {
        assertEquals(
            "class_10_B",
            AcademicSessionSelection.resolveGroupId(" ", "class_10_B")
        )
    }

    @Test
    fun missingGroupValuesResolveSafely() {
        assertEquals("", AcademicSessionSelection.resolveGroupId(null, null))
    }

    @Test
    fun validSavedStudyDateSurvivesRestart() {
        assertEquals(
            "2026-10-31",
            AcademicSessionSelection.resolveStudyDate("2026-10-31", "2026-11-02")
        )
    }

    @Test
    fun invalidSavedStudyDateFallsBackToToday() {
        assertEquals(
            "2026-11-02",
            AcademicSessionSelection.resolveStudyDate("2026-02-31", "2026-11-02")
        )
    }

    @Test
    fun missingGenerationMarkerNeverPurgesExistingLocalData() {
        assertFalse(DataGenerationPolicy.shouldPurgeLocalCache(null, "gen_initial_1"))
        assertFalse(DataGenerationPolicy.shouldPurgeLocalCache("", "gen_initial_1"))
    }

    @Test
    fun onlyAnExplicitGenerationChangeRequestsPurge() {
        assertFalse(DataGenerationPolicy.shouldPurgeLocalCache("gen_initial_1", "gen_initial_1"))
        assertTrue(DataGenerationPolicy.shouldPurgeLocalCache("gen_initial_1", "gen_initial_2"))
        assertFalse(DataGenerationPolicy.shouldPurgeLocalCache("gen_initial_1", null))
    }
}
