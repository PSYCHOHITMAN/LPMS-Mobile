package com.pointblank.lpms

import com.pointblank.lpms.core.model.*
import com.pointblank.lpms.core.util.ReportMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportMetricsTest {
    private fun p(id: String, learner: String, level: Int, date: String) = ProgressRecord(id, learner, "Reading Boost", level, date)

    @Test fun gainIsLatestMinusFirstByDate() {
        val rows = listOf(p("1", "a", 2, "2026-06-01"), p("2", "a", 5, "2026-08-01"), p("3", "a", 4, "2026-07-01"))
        // by date the order is 2, 4, 5; list order would wrongly give 4 - 2
        assertEquals(3, ReportMetrics.gain("a", rows)) // 5 (latest by date) - 2 (first)
    }

    @Test fun gainNeedsTwoRecords() {
        assertNull(ReportMetrics.gain("a", listOf(p("1", "a", 3, "2026-06-01"))))
        assertNull(ReportMetrics.gain("nobody", emptyList()))
    }

    @Test fun regressionIsNegative() {
        assertEquals(-1, ReportMetrics.gain("a", listOf(p("1", "a", 3, "2026-06-01"), p("2", "a", 2, "2026-07-01"))))
    }

    @Test fun programmeMetricsAreConsistentWithTheOtherScreens() {
        val programme = Programme("p1", "Reading Boost", "s1", "d")
        val enrol = listOf(Enrollment("e1", "a", "p1"), Enrollment("e2", "b", "p1"), Enrollment("e3", "c", "p2"))
        val sessions = listOf(Session("se1", "Reading Boost", "s1", "2026-10-01", "T"), Session("se2", "Other", "s1", "2026-10-02", "T"))
        val att = listOf(
            AttendanceRecord("1", "se1", "a", true), AttendanceRecord("2", "se1", "b", false),
            AttendanceRecord("3", "se2", "c", true), AttendanceRecord("4", "se1", "c", true)
        )
        val prog = listOf(p("1", "a", 2, "2026-06-01"), p("2", "a", 4, "2026-07-01"), p("3", "b", 3, "2026-06-01"), p("4", "b", 3, "2026-07-01"))
        val m = ReportMetrics.programme(programme, listOf(School("s1", "Soshanguve", "", "")), enrol, sessions, att, prog)
        assertEquals(2, m.learnersReached)           // a and b; c is in another programme
        assertEquals(1, m.sessionsHeld)              // se1 only
        assertEquals(50, m.attendance)               // a present, b absent
        assertEquals(2, m.learnersAssessed)
        assertEquals(1, m.learnersImproved)          // a +2, b +0
        assertEquals(1.0, m.averageGain!!, 0.001)    // (2 + 0) / 2
        assertEquals("Soshanguve", m.schoolName)
    }

    @Test fun emptyDataGivesNullsNotZeros() {
        val m = ReportMetrics.programme(Programme("p", "X", "s", ""), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(0, m.learnersReached); assertNull(m.attendance); assertNull(m.averageGain)
    }

    @Test fun headlineCountsAreRight() {
        val learners = listOf(Learner("a", null, "A", 3, "s1"), Learner("b", null, "B", 0, "s1"))
        val h = ReportMetrics.headline(listOf(School("s1", "S", "", "")), 4, learners,
            listOf(AttendanceRecord("1", "x", "a", true)), listOf(p("1", "a", 1, "2026-06-01"), p("2", "a", 3, "2026-07-01")))
        assertEquals(1, h.schools); assertEquals(4, h.teachers); assertEquals(2, h.learners)
        assertEquals(100, h.attendance); assertEquals(1, h.learnersImproved); assertEquals(1, h.learnersAssessed)
        assertEquals(2.0, h.averageGain!!, 0.001)
    }

    @Test fun csvHasHeadlineThenOneRowPerProgrammeAndEscapesCommas() {
        val h = ReportMetrics.headline(emptyList(), 0, emptyList(), emptyList(), emptyList())
        val row = com.pointblank.lpms.core.util.ProgrammeMetrics("Reading, Boost", "Soshanguve \"Primary\"", 2, 1, 50, 2, 1, 1.0)
        val csv = ReportMetrics.toCsv(h, listOf(row), "2026-10-12")
        assertTrue(csv.startsWith("LPMS impact report,2026-10-12"))
        assertTrue(csv.contains("Programme,School,Learners reached"))
        assertTrue(csv.contains("\"Reading, Boost\",\"Soshanguve \"\"Primary\"\"\",2,1,50,2,1,1.0"))
    }
}
