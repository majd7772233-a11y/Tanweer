package com.magd.tanweer.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanPageDraftSaverTest {

    @Test
    fun resumesWhenProcessedOutputIsMissingButSourceStillExists() {
        assertTrue(
            shouldResumeScanPageProcessing(
                processedResultAvailable = false,
                sourceMediaAvailable = true
            )
        )
    }

    @Test
    fun doesNotResumeWhenProcessedOutputWasRestored() {
        assertFalse(
            shouldResumeScanPageProcessing(
                processedResultAvailable = true,
                sourceMediaAvailable = true
            )
        )
    }

    @Test
    fun doesNotResumeWhenSourceMediaIsUnavailable() {
        assertFalse(
            shouldResumeScanPageProcessing(
                processedResultAvailable = false,
                sourceMediaAvailable = false
            )
        )
    }
}
