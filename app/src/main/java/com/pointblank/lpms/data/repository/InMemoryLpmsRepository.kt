package com.pointblank.lpms.data.repository

import com.pointblank.lpms.core.model.*
import com.pointblank.lpms.core.util.Grades
import com.pointblank.lpms.core.util.LearnerInsights
import com.pointblank.lpms.core.util.Messaging
import com.pointblank.lpms.core.util.Scoping
import com.pointblank.lpms.core.util.SessionRules
import com.pointblank.lpms.core.util.Viewer
import com.pointblank.lpms.data.seed.SeedData
import java.util.UUID

/**
 * Used by unit tests. The app itself uses [com.pointblank.lpms.data.firebase.FirestoreLpmsRepository].
 * With a [currentUser] it applies the same privacy scoping as the real repository; without one it returns everything.
 */
class InMemoryLpmsRepository(
    private val currentUser: (() -> User?)? = null
) : LpmsRepository {
    private val lock = Any()
    private var nextId = 1000
    private var schools: List<School> = SeedData.schools
    private var learners: List<Learner> = SeedData.learners
    private var attendance: List<AttendanceRecord> = SeedData.attendance
    private var progress: List<ProgressRecord> = SeedData.progress
    private var feedback: List<FeedbackEntry> = SeedData.feedback
    private var resources: List<Resource> = SeedData.resources
    private var reports: List<Report> = SeedData.reports
    private var visits: List<SchoolVisit> = SeedData.visits
    private var audit: List<AuditEntry> = emptyList()
    private var sessions: List<Session> = SeedData.sessions
    private var messages: List<Message> = SeedData.messages
    // Hashing the seed passwords is slow, so the seed users are built once.
    private val seedUsers: List<User> by lazy { SeedData.users() }

    private fun newId(prefix: String) = "$prefix${nextId++}"

    private fun viewer(): Viewer? {
        val provider = currentUser ?: return null
        val u = provider() ?: return Viewer("", Role.LEARNER, "") // signed out sees nothing
        val sid = u.schoolId.ifBlank { schools.firstOrNull { it.name == u.school }?.id ?: "" }
        return Viewer(u.id, u.role, sid)
    }

    private fun scoped() = currentUser != null

    override suspend fun schools() = synchronized(lock) { schools }
    override suspend fun programmes() = SeedData.programmes
    override suspend fun enrollments() = SeedData.enrollments
    override suspend fun sessions() = synchronized(lock) { sessions }
    override suspend fun reports() = synchronized(lock) { reports }
    override suspend fun resources() = synchronized(lock) { resources }

    override suspend fun learners(): List<Learner> = synchronized(lock) {
        if (!scoped()) learners else learners.filter { Scoping.canSeeLearner(viewer(), it) }
    }

    override suspend fun attendance(): List<AttendanceRecord> = synchronized(lock) {
        if (!scoped()) attendance else attendance.filter { Scoping.canSeeRecord(viewer(), it.schoolId, it.viewerIds) }
    }

    override suspend fun progress(): List<ProgressRecord> = synchronized(lock) {
        if (!scoped()) progress else progress.filter { Scoping.canSeeRecord(viewer(), it.schoolId, it.viewerIds) }
    }

    override suspend fun feedback(): List<FeedbackEntry> = synchronized(lock) {
        if (!scoped()) feedback else feedback.filter { Scoping.canSeeFeedback(viewer(), it.authorId) }
    }

    override suspend fun messages(): List<Message> = synchronized(lock) {
        if (!scoped()) messages else messages.filter { viewer()?.id in it.participants }
    }

    private fun User.toContact() = Contact(
        id, displayName, role, schoolId.ifBlank { schools.firstOrNull { it.name == school }?.id ?: "" }, grade,
        if (role == Role.PARENT && childName.isNotBlank()) "Parent of $childName" else ""
    )

    override suspend fun contacts(): List<Contact> {
        val v = viewer()
        val people = seedUsers.filter { it.approved && it.active }
        val staffAndTeachers = people.filter { it.role == Role.TEACHER || it.role == Role.COORDINATOR || it.role == Role.ADMIN }
            .map { it.toContact() }
        val parentsOfVisibleLearners = learners().flatMap { l ->
            l.guardianNames.map { (id, name) -> Contact(id, name, Role.PARENT, l.schoolId, "", "Parent of ${l.name}") }
        }
        return when {
            v == null || v.role.isStaff -> (people.map { it.toContact() } + parentsOfVisibleLearners).distinctBy { it.userId }
            v.role == Role.TEACHER -> (staffAndTeachers + parentsOfVisibleLearners).distinctBy { it.userId }
            v.role == Role.PARENT -> staffAndTeachers // parents never see other parents
            else -> emptyList()
        }
    }

    override suspend fun visits(): List<SchoolVisit> = synchronized(lock) {
        if (!scoped() || viewer()?.role?.isStaff == true) visits else emptyList()
    }

    override suspend fun audit(): List<AuditEntry> = synchronized(lock) {
        if (!scoped() || viewer()?.role == Role.ADMIN) audit else emptyList()
    }

    override suspend fun schoolIdFor(user: User): String =
        user.schoolId.ifBlank { schools().firstOrNull { it.name == user.school }?.id ?: "" }

    override suspend fun classRoster(schoolId: String, grade: String): List<Learner> {
        val gradeNumber = LearnerInsights.parseGrade(grade) ?: return emptyList()
        if (schoolId.isBlank()) return emptyList()
        return learners().filter { it.schoolId == schoolId && it.grade == gradeNumber }
    }

    override suspend fun saveAttendance(sessionId: String, marks: Map<String, Boolean>, by: User): Boolean {
        val session = synchronized(lock) { sessions.firstOrNull { it.id == sessionId } }
            ?: throw IllegalStateException("Session not found")
        if (!SessionRules.canMarkAttendance(by.role, session)) {
            throw IllegalStateException("This session is ${SessionStatus.label(session.status).lowercase()}, so attendance can't be changed")
        }
        val visible = learners().associateBy { it.id }
        synchronized(lock) {
            val allowed = marks.filterKeys { it in visible }
            val kept = attendance.filterNot { it.sessionId == sessionId && it.learnerId in allowed }
            val added = allowed.map { (learnerId, present) ->
                val l = visible.getValue(learnerId)
                AttendanceRecord(newId("a"), sessionId, learnerId, present, l.schoolId, l.viewerIds(), by.displayName,
                    System.currentTimeMillis())
            }
            attendance = kept + added
        }
        return true
    }

    override suspend fun addSession(session: Session): Boolean { synchronized(lock) { sessions = sessions + session }; return true }

    override suspend fun setSessionStatus(sessionId: String, status: String): Boolean {
        synchronized(lock) { sessions = sessions.map { if (it.id == sessionId) it.copy(status = status) else it } }
        return true
    }

    override suspend fun sendMessage(message: Message): Boolean {
        Messaging.validate(message.text)?.let { throw IllegalArgumentException(it) }
        synchronized(lock) { messages = messages + message.copy(text = message.text.trim()) }
        return true
    }

    override suspend fun markMessagesRead(ids: List<String>): Boolean {
        val me = viewer()?.id
        synchronized(lock) {
            messages = messages.map { if (it.id in ids && (me == null || it.toId == me)) it.copy(read = true) else it }
        }
        return true
    }

    override suspend fun addProgress(learnerId: String, programme: String, level: Int, by: User): Boolean {
        val l = learners().firstOrNull { it.id == learnerId }
            ?: throw IllegalStateException("You can only record progress for learners at your own school")
        synchronized(lock) {
            progress = progress + ProgressRecord(newId("pr"), learnerId, programme, level.coerceIn(1, 5),
                java.time.LocalDate.now().toString(), l.schoolId, l.viewerIds(), by.displayName)
        }
        return true
    }

    override suspend fun addSchool(school: School): Boolean { synchronized(lock) { schools = schools + school }; return true }

    override suspend fun upgradeLibrary(schoolId: String, today: String): Boolean {
        synchronized(lock) {
            schools = schools.map {
                if (it.id == schoolId && it.libraryLevel < 3) it.copy(libraryLevel = it.libraryLevel + 1, lastUpgrade = today) else it
            }
        }
        return true
    }

    override suspend fun addLearner(name: String, schoolId: String, grade: Int): Boolean {
        synchronized(lock) { learners = learners + Learner(newId("l"), null, name.trim(), grade, schoolId) }
        return true
    }

    override suspend fun linkAccount(learnerId: String, user: User): Boolean {
        synchronized(lock) {
            learners = learners.map {
                if (it.id != learnerId) it
                else if (user.role == Role.PARENT) it.copy(
                    guardianIds = (it.guardianIds + user.id).distinct(),
                    guardianNames = it.guardianNames + (user.id to user.displayName)
                )
                else it.copy(userId = user.id)
            }
            val l = learners.first { it.id == learnerId }
            attendance = attendance.map { if (it.learnerId == learnerId) it.copy(viewerIds = l.viewerIds()) else it }
            progress = progress.map { if (it.learnerId == learnerId) it.copy(viewerIds = l.viewerIds()) else it }
        }
        return true
    }

    override suspend fun addVisit(visit: SchoolVisit): Boolean { synchronized(lock) { visits = visits + visit }; return true }
    override suspend fun addFeedback(entry: FeedbackEntry): Boolean { synchronized(lock) { feedback = feedback + entry }; return true }
    override suspend fun addResource(resource: Resource): Boolean { synchronized(lock) { resources = resources + resource }; return true }

    override suspend fun setResourceShared(id: String, shared: Boolean): Boolean {
        synchronized(lock) { resources = resources.map { if (it.id == id) it.copy(shared = shared) else it } }
        return true
    }

    override suspend fun addReport(report: Report): Boolean {
        synchronized(lock) { reports = reports.filterNot { it.id == report.id } + report }
        return true
    }

    override suspend fun logAudit(entry: AuditEntry): Boolean { synchronized(lock) { audit = audit + entry }; return true }

    override suspend fun seedDemoData(): Int = 0 // seed data is already built in
}
