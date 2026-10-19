package com.pointblank.lpms.core.util

import com.pointblank.lpms.core.model.Role
import com.pointblank.lpms.core.model.Session
import com.pointblank.lpms.core.model.SessionStatus
import com.pointblank.lpms.core.model.isStaff
import java.time.LocalDate

/**
 * The life of a session, written once:
 *  - A coordinator (or a teacher, for their own school) schedules it.
 *  - While it is scheduled, the teacher marks attendance.
 *  - Completing it closes it: the register is locked. Cancelling it means it did not happen.
 *  - Only staff can reopen a completed or cancelled session.
 */
object SessionRules {
    const val MAX_DAYS_BACK = 30L
    const val MAX_DAYS_AHEAD = 365L

    fun isOpen(session: Session) = session.status == SessionStatus.SCHEDULED

    fun canMarkAttendance(role: Role, session: Session): Boolean = when {
        role.isStaff -> session.status != SessionStatus.CANCELLED
        role == Role.TEACHER -> isOpen(session)
        else -> false
    }

    fun canComplete(role: Role, session: Session) = (role.isStaff || role == Role.TEACHER) && isOpen(session)

    fun canCancel(role: Role, session: Session) = (role.isStaff || role == Role.TEACHER) && isOpen(session)

    fun canReopen(role: Role, session: Session) = role.isStaff && !isOpen(session)

    fun canSchedule(role: Role) = role.isStaff || role == Role.TEACHER

    /** Why a new session cannot be created, or null if it is fine. */
    fun validateNew(
        programme: String,
        date: String,
        schoolId: String?,
        existing: List<Session>,
        today: LocalDate = LocalDate.now()
    ): String? {
        if (schoolId.isNullOrBlank()) return "Choose the school"
        if (programme.isBlank()) return "Choose or type the programme"
        val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return "Pick a date"
        if (d.isBefore(today.minusDays(MAX_DAYS_BACK))) return "That date is too far in the past"
        if (d.isAfter(today.plusDays(MAX_DAYS_AHEAD))) return "That date is too far ahead"
        val clash = existing.any {
            it.schoolId == schoolId && it.date == date && it.programme.equals(programme.trim(), ignoreCase = true) &&
                it.status != SessionStatus.CANCELLED
        }
        return if (clash) "That programme is already scheduled at this school on that date" else null
    }
}
