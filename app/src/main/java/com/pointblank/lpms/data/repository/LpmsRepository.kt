package com.pointblank.lpms.data.repository

import com.pointblank.lpms.core.model.*

/**
 * Data access for LPMS entities. Implemented by Firestore in the app and in memory for unit tests.
 *
 * Reads of learners, attendance, progress and feedback are scoped to the signed-in user (see
 * [com.pointblank.lpms.core.util.Scoping]): staff see everything, a teacher sees their school,
 * a parent or learner sees only their own records.
 *
 * Every write returns true when the server confirmed it, or false when it was saved on this device and is
 * queued until the connection returns (the app keeps working offline). A failed write throws.
 */
interface LpmsRepository {
    suspend fun schools(): List<School>
    suspend fun programmes(): List<Programme>
    suspend fun learners(): List<Learner>
    suspend fun enrollments(): List<Enrollment>
    suspend fun sessions(): List<Session>
    suspend fun progress(): List<ProgressRecord>
    suspend fun attendance(): List<AttendanceRecord>
    suspend fun reports(): List<Report>
    suspend fun feedback(): List<FeedbackEntry>
    suspend fun resources(): List<Resource>
    /** Messages the signed-in user sent or received. */
    suspend fun messages(): List<Message>
    /**
     * People the signed-in user could message (the screen narrows this with
     * [com.pointblank.lpms.core.util.Messaging.allowedContacts]). Parents only ever receive staff and teachers.
     */
    suspend fun contacts(): List<Contact>
    /** Staff only; empty for everyone else. */
    suspend fun visits(): List<SchoolVisit>
    /** Admin only; empty for everyone else. */
    suspend fun audit(): List<AuditEntry>

    /** The school id for a user, falling back to matching the school name for accounts made before ids existed. */
    suspend fun schoolIdFor(user: User): String

    /** Learners at [schoolId] in [grade] ("R", "3" or "Grade 3"), i.e. one teacher's class. */
    suspend fun classRoster(schoolId: String, grade: String): List<Learner>

    /**
     * Saves present/absent per learner for one session, replacing earlier marks for those learners in that
     * session, and records who saved it and when. Learners outside the caller's scope are ignored.
     * Throws if the session is closed (or cancelled) for the caller; see [com.pointblank.lpms.core.util.SessionRules].
     */
    suspend fun saveAttendance(sessionId: String, marks: Map<String, Boolean>, by: User): Boolean

    suspend fun addSession(session: Session): Boolean
    suspend fun setSessionStatus(sessionId: String, status: String): Boolean
    suspend fun sendMessage(message: Message): Boolean
    suspend fun markMessagesRead(ids: List<String>): Boolean
    suspend fun addProgress(learnerId: String, programme: String, level: Int, by: User): Boolean
    suspend fun addSchool(school: School): Boolean
    suspend fun upgradeLibrary(schoolId: String, today: String): Boolean
    suspend fun addLearner(name: String, schoolId: String, grade: Int): Boolean

    /** Links a parent (as guardian) or a learner login to a learner record, so they can see its records. */
    suspend fun linkAccount(learnerId: String, user: User): Boolean

    suspend fun addVisit(visit: SchoolVisit): Boolean
    suspend fun addFeedback(entry: FeedbackEntry): Boolean
    suspend fun addResource(resource: Resource): Boolean
    suspend fun setResourceShared(id: String, shared: Boolean): Boolean
    suspend fun addReport(report: Report): Boolean
    suspend fun logAudit(entry: AuditEntry): Boolean

    /** Writes the demo data set (merge, so it is safe to run twice). Returns how many records were written. */
    suspend fun seedDemoData(): Int
}
