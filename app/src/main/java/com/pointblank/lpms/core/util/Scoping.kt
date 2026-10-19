package com.pointblank.lpms.core.util

import com.pointblank.lpms.core.model.*

/** Who is asking, reduced to what access rules need. */
data class Viewer(val id: String, val role: Role, val schoolId: String)

/**
 * The privacy rules, written once. The Firestore repository enforces the same rules with scoped queries
 * (and firestore.rules enforces them on the server); the in-memory repository uses these functions directly.
 *  - Staff (admin, coordinator) see everything.
 *  - A teacher sees learners and records at their own school.
 *  - A parent or learner sees only the learners linked to them, and those learners' records.
 */
object Scoping {
    fun canSeeLearner(v: Viewer?, learner: Learner): Boolean = when {
        v == null -> false
        v.role.isStaff -> true
        v.role == Role.TEACHER -> v.schoolId.isNotBlank() && learner.schoolId == v.schoolId
        else -> learner.userId == v.id || v.id in learner.guardianIds
    }

    fun canSeeRecord(v: Viewer?, schoolId: String, viewerIds: List<String>): Boolean = when {
        v == null -> false
        v.role.isStaff -> true
        v.role == Role.TEACHER -> v.schoolId.isNotBlank() && schoolId == v.schoolId
        else -> v.id in viewerIds
    }

    fun canSeeFeedback(v: Viewer?, authorId: String): Boolean = v != null && (v.role.isStaff || authorId == v.id)
}
