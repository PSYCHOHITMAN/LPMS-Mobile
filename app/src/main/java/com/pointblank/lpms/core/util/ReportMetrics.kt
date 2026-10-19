package com.pointblank.lpms.core.util

import com.pointblank.lpms.core.model.*

/** What a funder wants to know: reach, attendance and how much reading levels improved. */
data class ProgrammeMetrics(
    val programmeName: String,
    val schoolName: String,
    val learnersReached: Int,
    val sessionsHeld: Int,
    val attendance: Int?,        // percent, null if no attendance recorded
    val learnersAssessed: Int,   // learners with at least two reading-level records
    val learnersImproved: Int,
    val averageGain: Double?     // average of (latest level - first level), null if nobody assessed twice
)

data class Headline(
    val schools: Int,
    val teachers: Int,
    val learners: Int,
    val attendance: Int?,
    val learnersAssessed: Int,
    val learnersImproved: Int,
    val averageGain: Double?
)

object ReportMetrics {

    /** Reading-level change for one learner: latest minus first (by date). Null with fewer than two records. */
    fun gain(learnerId: String, progress: List<ProgressRecord>): Int? {
        val mine = progress.filter { it.learnerId == learnerId }.sortedBy { it.date }
        return if (mine.size < 2) null else mine.last().level - mine.first().level
    }

    private fun round1(x: Double): Double = Math.round(x * 10.0) / 10.0

    private fun gains(learnerIds: Collection<String>, progress: List<ProgressRecord>): List<Int> =
        learnerIds.mapNotNull { gain(it, progress) }

    fun programme(
        programme: Programme,
        schools: List<School>,
        enrollments: List<Enrollment>,
        sessions: List<Session>,
        attendance: List<AttendanceRecord>,
        progress: List<ProgressRecord>
    ): ProgrammeMetrics {
        val learnerIds = enrollments.filter { it.programmeId == programme.id }.map { it.learnerId }.toSet()
        val sessionIds = sessions.filter { it.programme == programme.name && it.status != SessionStatus.CANCELLED }.map { it.id }.toSet()
        val held = attendance.filter { it.sessionId in sessionIds }.map { it.sessionId }.distinct().size
        val g = gains(learnerIds, progress)
        return ProgrammeMetrics(
            programmeName = programme.name,
            schoolName = schools.firstOrNull { it.id == programme.schoolId }?.name ?: "",
            learnersReached = learnerIds.size,
            sessionsHeld = held,
            attendance = LearnerInsights.overallRate(attendance.filter { it.learnerId in learnerIds }),
            learnersAssessed = g.size,
            learnersImproved = g.count { it > 0 },
            averageGain = if (g.isEmpty()) null else round1(g.average())
        )
    }

    fun headline(
        schools: List<School>,
        teachers: Int,
        learners: List<Learner>,
        attendance: List<AttendanceRecord>,
        progress: List<ProgressRecord>
    ): Headline {
        val g = gains(learners.map { it.id }, progress)
        return Headline(
            schools = schools.size,
            teachers = teachers,
            learners = learners.size,
            attendance = LearnerInsights.overallRate(attendance),
            learnersAssessed = g.size,
            learnersImproved = g.count { it > 0 },
            averageGain = if (g.isEmpty()) null else round1(g.average())
        )
    }

    private fun csv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

    /** A funder-ready CSV: headline figures, then one row per programme. */
    fun toCsv(headline: Headline, rows: List<ProgrammeMetrics>, generatedOn: String): String = buildString {
        appendLine("LPMS impact report,$generatedOn")
        appendLine("Schools,${headline.schools}")
        appendLine("Teachers,${headline.teachers}")
        appendLine("Learners,${headline.learners}")
        appendLine("Attendance %,${headline.attendance ?: ""}")
        appendLine("Learners improved,${headline.learnersImproved} of ${headline.learnersAssessed} assessed")
        appendLine("Average reading level gain,${headline.averageGain ?: ""}")
        appendLine()
        appendLine("Programme,School,Learners reached,Sessions held,Attendance %,Learners assessed,Learners improved,Average gain")
        rows.forEach {
            appendLine(
                listOf(
                    csv(it.programmeName), csv(it.schoolName), it.learnersReached.toString(), it.sessionsHeld.toString(),
                    it.attendance?.toString() ?: "", it.learnersAssessed.toString(), it.learnersImproved.toString(),
                    it.averageGain?.toString() ?: ""
                ).joinToString(",")
            )
        }
    }
}
