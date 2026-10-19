package com.pointblank.lpms.core.util

import com.pointblank.lpms.core.model.School
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * READ supports a school for three years and upgrades its box library each year.
 * [year] is 1 to 3 while the programme runs; [completed] once the three years are over.
 */
data class CycleStatus(
    val started: Boolean,
    val year: Int?,
    val completed: Boolean,
    val upgradeDue: Boolean
) {
    val label: String
        get() = when {
            !started -> "Not started"
            completed -> "Programme completed"
            else -> "Year $year of ${ProgrammeCycle.YEARS}"
        }
}

object ProgrammeCycle {
    const val YEARS = 3
    const val MAX_LIBRARY_LEVEL = 3

    fun status(school: School, today: LocalDate = LocalDate.now()): CycleStatus {
        val start = runCatching { LocalDate.parse(school.startDate) }.getOrNull()
            ?: return CycleStatus(started = false, year = null, completed = false, upgradeDue = false)
        if (start.isAfter(today)) return CycleStatus(false, null, false, false)
        val year = ChronoUnit.YEARS.between(start, today).toInt() + 1
        if (year > YEARS) return CycleStatus(true, null, completed = true, upgradeDue = false)
        // The library should be at least as advanced as the programme year.
        return CycleStatus(true, year, completed = false, upgradeDue = school.libraryLevel < year)
    }

    fun canUpgrade(school: School): Boolean = school.libraryLevel < MAX_LIBRARY_LEVEL
}
