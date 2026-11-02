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
    fun savedGroupIsKeptOnlyWhenServerConfirmsMembership() {
        assertEquals(
            "class_10_C",
            AcademicSessionSelection.resolveAuthorizedGroupId(
                "class_10_C",
                "class_10_B",
                listOf("class_10_B", "class_10_C")
            )
        )
    }

    @Test
    fun removedSavedGroupFallsBackToAuthorizedDefault() {
        assertEquals(
            "class_10_B",
            AcademicSessionSelection.resolveAuthorizedGroupId(
                "class_10_C",
                "class_10_B",
                listOf("class_10_B")
            )
        )
    }

    @Test
    fun unavailableDefaultFallsBackToFirstActiveMembership() {
        assertEquals(
            "class_10_A",
            AcademicSessionSelection.resolveAuthorizedGroupId(
                "class_10_C",
                "class_10_B",
                listOf("class_10_A", "class_10_D")
            )
        )
    }

    @Test
    fun noActiveMembershipResolvesToEmptyGroup() {
        assertEquals(
            "",
            AcademicSessionSelection.resolveAuthorizedGroupId(
                "class_10_C",
                "class_10_B",
                emptyList()
            )
        )
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
