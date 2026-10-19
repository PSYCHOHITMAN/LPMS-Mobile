package com.pointblank.lpms.core.util

import com.pointblank.lpms.core.model.AttendanceRecord
import com.pointblank.lpms.core.model.ProgressRecord
import kotlin.math.roundToInt

enum class LearnerStatus(val label: String) {
    NOT_ASSESSED("Not yet assessed"),
    EXCELLING("Excelling"),
    ON_TRACK("On track"),
    NEEDS_SUPPORT("Needs support")
}

/** Same rules as the web portal's teacher screens. */
object LearnerInsights {

    fun statusFor(level: Int?): LearnerStatus = when {
        level == null -> LearnerStatus.NOT_ASSESSED
        level >= 4 -> LearnerStatus.EXCELLING
        level == 3 -> LearnerStatus.ON_TRACK
        else -> LearnerStatus.NEEDS_SUPPORT
    }

    /** "Grade 3", "3", "R" and "Grade R" all work; Grade R is 0. */
    fun parseGrade(text: String): Int? = Grades.parse(text)

    /** Newest record by date; if two share a date, the one saved later wins. */
    fun latestLevel(learnerId: String, progress: List<ProgressRecord>): Int? =
        progress.filter { it.learnerId == learnerId }.sortedBy { it.date }.lastOrNull()?.level

    /** Rounded percentage; every screen must use this so the same data never shows two numbers. */
    fun percent(part: Int, total: Int): Int =
        if (total == 0) 0 else (part * 100.0 / total).roundToInt()

    /** Present rate across every record, or null when there are none. */
    fun overallRate(records: List<AttendanceRecord>): Int? =
        if (records.isEmpty()) null else percent(records.count { it.present }, records.size)

    /** Percentage of saved sessions the learner was present for, or null if never marked. */
    fun attendanceRate(learnerId: String, records: List<AttendanceRecord>): Int? {
        val mine = records.filter { it.learnerId == learnerId }
        if (mine.isEmpty()) return null
        return percent(mine.count { it.present }, mine.size)
    }
}
