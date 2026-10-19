package com.pointblank.lpms

import com.pointblank.lpms.core.model.Role
import com.pointblank.lpms.core.model.Session
import com.pointblank.lpms.core.model.SessionStatus
import com.pointblank.lpms.core.util.SessionRules
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRulesTest {
    private val today = LocalDate.of(2026, 10, 14)
    private fun s(status: String) = Session("x", "Reading Boost", "s1", "2026-10-15", "T", status)

    @Test fun teachersMarkOnlyScheduledSessions() {
        assertTrue(SessionRules.canMarkAttendance(Role.TEACHER, s(SessionStatus.SCHEDULED)))
        assertFalse(SessionRules.canMarkAttendance(Role.TEACHER, s(SessionStatus.COMPLETED)))
        assertFalse(SessionRules.canMarkAttendance(Role.TEACHER, s(SessionStatus.CANCELLED)))
    }

    @Test fun staffCanCorrectCompletedSessionsButNotCancelledOnes() {
        assertTrue(SessionRules.canMarkAttendance(Role.COORDINATOR, s(SessionStatus.COMPLETED)))
        assertTrue(SessionRules.canMarkAttendance(Role.ADMIN, s(SessionStatus.SCHEDULED)))
        assertFalse(SessionRules.canMarkAttendance(Role.ADMIN, s(SessionStatus.CANCELLED)))
    }

    @Test fun parentsAndLearnersNeverMarkAttendance() {
        listOf(Role.PARENT, Role.LEARNER).forEach {
            assertFalse(SessionRules.canMarkAttendance(it, s(SessionStatus.SCHEDULED)))
            assertFalse(SessionRules.canComplete(it, s(SessionStatus.SCHEDULED)))
            assertFalse(SessionRules.canSchedule(it))
        }
    }

    @Test fun completeAndCancelOnlyWhileScheduled() {
        assertTrue(SessionRules.canComplete(Role.TEACHER, s(SessionStatus.SCHEDULED)))
        assertTrue(SessionRules.canCancel(Role.COORDINATOR, s(SessionStatus.SCHEDULED)))
        assertFalse(SessionRules.canComplete(Role.TEACHER, s(SessionStatus.COMPLETED)))
        assertFalse(SessionRules.canCancel(Role.TEACHER, s(SessionStatus.CANCELLED)))
    }

    @Test fun onlyStaffReopen() {
        assertTrue(SessionRules.canReopen(Role.COORDINATOR, s(SessionStatus.COMPLETED)))
        assertTrue(SessionRules.canReopen(Role.ADMIN, s(SessionStatus.CANCELLED)))
        assertFalse(SessionRules.canReopen(Role.TEACHER, s(SessionStatus.COMPLETED)))
        assertFalse(SessionRules.canReopen(Role.COORDINATOR, s(SessionStatus.SCHEDULED)))
    }

    @Test fun teachersAndStaffCanSchedule() {
        assertTrue(SessionRules.canSchedule(Role.TEACHER)); assertTrue(SessionRules.canSchedule(Role.COORDINATOR))
        assertTrue(SessionRules.canSchedule(Role.ADMIN))
    }

    @Test fun newSessionNeedsSchoolProgrammeAndAValidDate() {
        assertNotNull(SessionRules.validateNew("Reading Boost", "2026-10-20", null, emptyList(), today))
        assertNotNull(SessionRules.validateNew("Reading Boost", "2026-10-20", " ", emptyList(), today))
        assertNotNull(SessionRules.validateNew("  ", "2026-10-20", "s1", emptyList(), today))
        assertNotNull(SessionRules.validateNew("Reading Boost", "", "s1", emptyList(), today))
        assertNotNull(SessionRules.validateNew("Reading Boost", "not a date", "s1", emptyList(), today))
        assertNull(SessionRules.validateNew("Reading Boost", "2026-10-20", "s1", emptyList(), today))
    }

    @Test fun dateWindowIsThirtyDaysBackAndAYearAhead() {
        assertNull(SessionRules.validateNew("P", "2026-09-14", "s1", emptyList(), today))      // exactly 30 days back
        assertNotNull(SessionRules.validateNew("P", "2026-09-13", "s1", emptyList(), today))   // 31 days back
        assertNull(SessionRules.validateNew("P", "2027-10-14", "s1", emptyList(), today))      // exactly 365 days ahead
        assertNotNull(SessionRules.validateNew("P", "2027-10-15", "s1", emptyList(), today))
    }

    @Test fun duplicatesAreRefusedButACancelledOneDoesNotBlock() {
        val existing = listOf(Session("a", "Reading Boost", "s1", "2026-10-20", "T"))
        assertNotNull(SessionRules.validateNew("reading boost", "2026-10-20", "s1", existing, today)) // case-insensitive
        assertNull(SessionRules.validateNew("Reading Boost", "2026-10-21", "s1", existing, today))    // another day
        assertNull(SessionRules.validateNew("Reading Boost", "2026-10-20", "s2", existing, today))    // another school
        val cancelled = listOf(existing[0].copy(status = SessionStatus.CANCELLED))
        assertNull(SessionRules.validateNew("Reading Boost", "2026-10-20", "s1", cancelled, today))
    }

    @Test fun statusLabels() {
        assertEquals("Scheduled", SessionStatus.label(SessionStatus.SCHEDULED))
        assertEquals("Completed", SessionStatus.label(SessionStatus.COMPLETED))
        assertEquals("Cancelled", SessionStatus.label(SessionStatus.CANCELLED))
        assertEquals("Scheduled", SessionStatus.label("whatever"))
    }
}
