package com.pointblank.lpms

import com.pointblank.lpms.core.model.AttendanceRecord
import com.pointblank.lpms.core.model.ProgressRecord
import com.pointblank.lpms.core.util.LearnerInsights
import com.pointblank.lpms.core.util.LearnerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LearnerInsightsTest {
    @Test fun statusRulesMatchWebsite() {
        assertEquals(LearnerStatus.NOT_ASSESSED, LearnerInsights.statusFor(null))
        assertEquals(LearnerStatus.EXCELLING, LearnerInsights.statusFor(4))
        assertEquals(LearnerStatus.EXCELLING, LearnerInsights.statusFor(5))
        assertEquals(LearnerStatus.ON_TRACK, LearnerInsights.statusFor(3))
        assertEquals(LearnerStatus.NEEDS_SUPPORT, LearnerInsights.statusFor(2))
        assertEquals(LearnerStatus.NEEDS_SUPPORT, LearnerInsights.statusFor(1))
    }

    @Test fun gradeParsingAcceptsLabelOrNumber() {
        assertEquals(3, LearnerInsights.parseGrade("Grade 3"))
        assertEquals(4, LearnerInsights.parseGrade("4"))
        assertEquals(0, LearnerInsights.parseGrade("R"))
        assertEquals(0, LearnerInsights.parseGrade("Grade R"))
        assertNull(LearnerInsights.parseGrade(""))
    }

    @Test fun latestLevelIsTheLastRecordForThatLearner() {
        val p = listOf(
            ProgressRecord("1", "l1", "RB", 2, "2026-06-01"),
            ProgressRecord("2", "l2", "RB", 4, "2026-06-02"),
            ProgressRecord("3", "l1", "RB", 3, "2026-06-03")
        )
        assertEquals(3, LearnerInsights.latestLevel("l1", p))
        assertNull(LearnerInsights.latestLevel("l9", p))
    }

    @Test fun learnerAttendanceRateIsPresentOverMarkedSessions() {
        val r = listOf(
            AttendanceRecord("1", "se1", "l1", true),
            AttendanceRecord("2", "se2", "l1", true),
            AttendanceRecord("3", "se3", "l1", false),
            AttendanceRecord("4", "se1", "l2", false)
        )
        assertEquals(67, LearnerInsights.attendanceRate("l1", r))
        assertEquals(0, LearnerInsights.attendanceRate("l2", r))
        assertNull(LearnerInsights.attendanceRate("l3", r))
    }


    @Test fun percentRoundsHalfUpAndHandlesZero() {
        assertEquals(67, LearnerInsights.percent(2, 3)) // 66.67 must show 67 everywhere, never 66
        assertEquals(0, LearnerInsights.percent(0, 0))
        assertEquals(100, LearnerInsights.percent(5, 5))
    }

    @Test fun overallRateMatchesPerLearnerRounding() {
        val r = listOf(
            AttendanceRecord("1", "se1", "l1", true),
            AttendanceRecord("2", "se2", "l1", true),
            AttendanceRecord("3", "se3", "l1", false)
        )
        assertEquals(LearnerInsights.attendanceRate("l1", r), LearnerInsights.overallRate(r))
        assertNull(LearnerInsights.overallRate(emptyList()))
    }

    @Test fun latestLevelUsesDateNotListOrder() {
        val p = listOf(
            ProgressRecord("1", "l1", "RB", 4, "2026-06-10"),
            ProgressRecord("2", "l1", "RB", 2, "2026-06-01")
        )
        assertEquals(4, LearnerInsights.latestLevel("l1", p))
    }
}
