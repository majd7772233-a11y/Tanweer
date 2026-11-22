package com.magd.tanweer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueStatusPolicyTest {
    @Test
    fun openAndInDiscussionShareTheActiveDiscussionFilter() {
        assertEquals(IssueStatus.OPEN, IssueStatusPolicy.fromApiValue("OPEN"))
        assertEquals(IssueStatus.IN_DISCUSSION, IssueStatusPolicy.fromApiValue("IN_DISCUSSION"))
        assertTrue(IssueStatusPolicy.matches(IssueFilter.DISCUSSING, "OPEN"))
        assertTrue(IssueStatusPolicy.matches(IssueFilter.DISCUSSING, "IN_DISCUSSION"))
        assertFalse(IssueStatusPolicy.matches(IssueFilter.SOLVED, "OPEN"))
    }

    @Test
    fun solvedAndVerifiedShareTheResolvedFilter() {
        assertEquals(IssueStatus.SOLVED, IssueStatusPolicy.fromApiValue("SOLVED"))
        assertEquals(IssueStatus.VERIFIED, IssueStatusPolicy.fromApiValue(" VERIFIED "))
        assertTrue(IssueStatusPolicy.matches(IssueFilter.SOLVED, "SOLVED"))
        assertTrue(IssueStatusPolicy.matches(IssueFilter.SOLVED, "VERIFIED"))
        assertTrue(IssueStatusPolicy.fromApiValue("VERIFIED").isResolved)
    }

    @Test
    fun closedHasItsOwnFilterAndUnknownValuesRemainVisibleInAll() {
        assertEquals(IssueStatus.CLOSED, IssueStatusPolicy.fromApiValue("CLOSED"))
        assertTrue(IssueStatusPolicy.matches(IssueFilter.CLOSED, "CLOSED"))
        assertFalse(IssueStatusPolicy.matches(IssueFilter.DISCUSSING, "CLOSED"))
        assertEquals(IssueStatus.UNKNOWN, IssueStatusPolicy.fromApiValue("FUTURE_STATUS"))
        assertTrue(IssueStatusPolicy.matches(IssueFilter.ALL, "FUTURE_STATUS"))
        assertFalse(IssueStatusPolicy.matches(IssueFilter.SOLVED, "FUTURE_STATUS"))
    }

    @Test
    fun normalizationIsCaseAndWhitespaceInsensitive() {
        assertEquals(IssueStatus.OPEN, IssueStatusPolicy.fromApiValue(" open "))
        assertEquals(IssueStatus.IN_DISCUSSION, IssueStatusPolicy.fromApiValue("in_discussion"))
        assertEquals(IssueStatus.UNKNOWN, IssueStatusPolicy.fromApiValue(null))
        assertTrue(IssueStatusPolicy.matches(IssueFilter.ALL, null))
    }
}
