package com.pointblank.lpms

import com.pointblank.lpms.core.model.School
import com.pointblank.lpms.core.util.ProgrammeCycle
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgrammeCycleTest {
    private val today = LocalDate.of(2026, 10, 12)
    private fun school(start: String, level: Int) = School("s", "S", "D", "", start, level)

    @Test fun firstYear() {
        val s = ProgrammeCycle.status(school("2026-09-01", 1), today)
        assertEquals(1, s.year); assertFalse(s.upgradeDue); assertFalse(s.completed)
        assertEquals("Year 1 of 3", s.label)
    }

    @Test fun secondYearWithLibraryStillAtLevelOneIsDue() {
        val s = ProgrammeCycle.status(school("2025-06-01", 1), today)
        assertEquals(2, s.year); assertTrue(s.upgradeDue)
    }

    @Test fun secondYearWithLibraryUpgradedIsNotDue() {
        assertFalse(ProgrammeCycle.status(school("2025-06-01", 2), today).upgradeDue)
    }

    @Test fun thirdYearNeedsLevelThree() {
        assertTrue(ProgrammeCycle.status(school("2024-06-01", 2), today).upgradeDue)
        assertFalse(ProgrammeCycle.status(school("2024-06-01", 3), today).upgradeDue)
    }

    @Test fun afterThreeYearsTheProgrammeIsCompleteAndNothingIsDue() {
        val s = ProgrammeCycle.status(school("2022-01-01", 3), today)
        assertTrue(s.completed); assertNull(s.year); assertFalse(s.upgradeDue)
        assertEquals("Programme completed", s.label)
    }

    @Test fun exactlyOneYearMovesToYearTwo() {
        assertEquals(1, ProgrammeCycle.status(school("2025-10-13", 1), today).year)
        assertEquals(2, ProgrammeCycle.status(school("2025-10-12", 1), today).year)
    }

    @Test fun notStartedOrUnknownDates() {
        assertFalse(ProgrammeCycle.status(school("2027-01-01", 1), today).started)
        assertFalse(ProgrammeCycle.status(school("", 1), today).started)
        assertFalse(ProgrammeCycle.status(school("garbage", 1), today).upgradeDue)
    }

    @Test fun libraryCannotGoAboveLevelThree() {
        assertTrue(ProgrammeCycle.canUpgrade(school("2025-01-01", 2)))
        assertFalse(ProgrammeCycle.canUpgrade(school("2025-01-01", 3)))
    }
}
