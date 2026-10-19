package com.pointblank.lpms.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointblank.lpms.core.model.*
import com.pointblank.lpms.core.util.Grades
import com.pointblank.lpms.core.util.LearnerInsights
import com.pointblank.lpms.core.util.ProgrammeCycle
import com.pointblank.lpms.core.util.SessionRules
import com.pointblank.lpms.data.repository.AuthRepository
import com.pointblank.lpms.data.repository.AuthResult
import com.pointblank.lpms.data.repository.LpmsRepository
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Shared data for the portal screens. Reads are already limited to what the signed-in user may see. */
data class ScreensUiState(
    val loading: Boolean = true,
    val schools: List<School> = emptyList(),
    val programmes: List<Programme> = emptyList(),
    val learners: List<Learner> = emptyList(),
    val enrollments: List<Enrollment> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val attendance: List<AttendanceRecord> = emptyList(),
    val progress: List<ProgressRecord> = emptyList(),
    val reports: List<Report> = emptyList(),
    val feedback: List<FeedbackEntry> = emptyList(),
    val resources: List<Resource> = emptyList(),
    val users: List<User> = emptyList(),
    val visits: List<SchoolVisit> = emptyList(),
    val audit: List<AuditEntry> = emptyList(),
    val message: String? = null
) {
    fun schoolName(id: String) = schools.firstOrNull { it.id == id }?.name ?: "Unknown school"

    fun programmeName(id: String) = programmes.firstOrNull { it.id == id }?.name ?: "Programme"

    // Delegates to LearnerInsights so every screen shows the same number for the same data.
    fun latestLevel(learnerId: String): Int? = LearnerInsights.latestLevel(learnerId, progress)

    fun attendanceRate(learnerId: String): Int? = LearnerInsights.attendanceRate(learnerId, attendance)

    fun overallAttendance(): Int? = LearnerInsights.overallRate(attendance)

    /** The Learner record linked to this login, or null (never someone else's record). */
    fun learnerFor(user: User): Learner? = learners.firstOrNull { it.userId == user.id }
}

private const val QUEUED_NOTE = " Saved on this phone; it will sync when you're back online."

class ScreensViewModel(
    private val repo: LpmsRepository,
    private val auth: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ScreensUiState())
    val state: StateFlow<ScreensUiState> = _state.asStateFlow()

    private var loadedForUserId: String? = null

    init { refresh() }

    /** Called whenever a screen is opened, so data saved elsewhere (e.g. attendance) is never stale. */
    fun refresh() {
        viewModelScope.launch { reload(message = null) }
    }

    private suspend fun reload(message: String?) {
        val user = auth.currentUser.value
        val uid = user?.id
        if (uid != loadedForUserId) { // new person signed in: drop the previous person's data
            loadedForUserId = uid
            _state.value = ScreensUiState()
        }
        val staff = user?.role?.isStaff == true
        try {
            _state.value = ScreensUiState(
                loading = false,
                schools = repo.schools(),
                programmes = repo.programmes(),
                learners = repo.learners(),
                enrollments = repo.enrollments(),
                sessions = repo.sessions(),
                attendance = repo.attendance(),
                progress = repo.progress(),
                reports = repo.reports(),
                feedback = repo.feedback(),
                resources = repo.resources(),
                users = if (staff) auth.allUsers() else emptyList(),
                visits = if (staff) repo.visits() else emptyList(),
                audit = if (user?.role == Role.ADMIN) repo.audit() else emptyList(),
                message = message
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(loading = false, message = message ?: "Could not load data. Check your connection.")
        }
    }

    // ---- writes

    private class WriteScope {
        var queued = false
        /** Pass each repository write's result through here; false means "saved on the phone, syncing later". */
        fun track(synced: Boolean) { if (!synced) queued = true }
    }

    private data class AuditSpec(val action: String, val target: String)

    /**
     * Runs a database write, then reloads and shows the outcome. [block] returns an error message, or null on success.
     * Failures become messages, never crashes; successful staff actions are written to the activity log.
     */
    private fun write(success: String? = null, audit: AuditSpec? = null, block: suspend WriteScope.() -> String?) {
        viewModelScope.launch {
            val scope = WriteScope()
            val error = try {
                scope.block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Could not save: ${e.localizedMessage ?: "unknown error"}"
            }
            val message = if (error != null) error else {
                if (audit != null) logAudit(audit)
                ((success ?: "") + if (scope.queued) QUEUED_NOTE else "").trim().ifBlank { null }
            }
            reload(message)
        }
    }

    private suspend fun logAudit(spec: AuditSpec) {
        val actor = auth.currentUser.value ?: return
        if (!actor.role.isStaff) return // the log is for administrative actions
        try {
            repo.logAudit(AuditEntry(UUID.randomUUID().toString(), actor.id, actor.displayName, spec.action, spec.target, System.currentTimeMillis()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // an audit failure must not undo or hide the action itself
        }
    }

    private fun AuthResult.failure(): String? = (this as? AuthResult.Failure)?.message

    private fun userName(id: String) = _state.value.users.firstOrNull { it.id == id }?.displayName ?: id

    // ---- user management
    fun setActive(userId: String, active: Boolean) =
        write(audit = AuditSpec(if (active) "Reactivated account" else "Suspended account", userName(userId))) {
            auth.setActive(userId, active).failure()
        }

    fun setRole(userId: String, role: Role) =
        write("Role updated", AuditSpec("Changed role to ${role.label}", userName(userId))) { auth.setRole(userId, role).failure() }

    fun approve(userId: String) =
        write("Account approved", AuditSpec("Approved account", userName(userId))) { auth.approveUser(userId).failure() }

    fun createUser(email: String, name: String, password: String, role: Role, school: School?, grade: String) =
        write("Account created for $name", AuditSpec("Created ${role.label} account", name)) {
            val gradeLabel = Grades.parse(grade)?.let { Grades.label(it) } ?: ""
            auth.adminCreateUser(email, name, password, role, school?.name ?: "", gradeLabel, school?.id ?: "").failure()
        }

    fun updateAccount(userId: String, role: Role, school: School?, grade: String) =
        write("Account updated", AuditSpec("Edited account (${role.label}, ${school?.name ?: "no school"})", userName(userId))) {
            auth.updateAccount(userId, role, school, grade).failure()
        }

    // ---- sessions: schedule, close, cancel, reopen (rules in SessionRules)
    fun scheduleSession(user: User, school: School?, programme: String, date: String, facilitator: String) =
        write("Session scheduled") {
            val error = SessionRules.validateNew(programme, date, school?.id, _state.value.sessions)
            if (error != null) error
            else if (!SessionRules.canSchedule(user.role)) "You can't schedule sessions"
            else {
                val session = Session(
                    id = "se-" + UUID.randomUUID().toString().take(8), programme = programme.trim(), schoolId = school!!.id,
                    date = date, facilitator = facilitator.trim().ifBlank { user.displayName }, createdByName = user.displayName
                )
                track(repo.addSession(session))
                logAudit(AuditSpec("Scheduled session", "${session.programme} at ${school.name} on $date"))
                null
            }
        }

    fun changeSessionStatus(user: User, session: Session, status: String) {
        val allowed = when (status) {
            SessionStatus.COMPLETED -> SessionRules.canComplete(user.role, session)
            SessionStatus.CANCELLED -> SessionRules.canCancel(user.role, session)
            SessionStatus.SCHEDULED -> SessionRules.canReopen(user.role, session)
            else -> false
        }
        val verb = when (status) {
            SessionStatus.COMPLETED -> "Session closed"
            SessionStatus.CANCELLED -> "Session cancelled"
            else -> "Session reopened"
        }
        write(verb) {
            if (!allowed) "You can't do that to a ${SessionStatus.label(session.status).lowercase()} session"
            else {
                track(repo.setSessionStatus(session.id, status))
                logAudit(AuditSpec("$verb", "${session.programme} on ${session.date}"))
                null
            }
        }
    }

    // ---- schools and the three-year programme
    fun addSchool(name: String, district: String) = write("School added") {
        val clean = name.trim()
        if (clean.length < 2) "Enter the school name"
        else if (_state.value.schools.any { it.name.equals(clean, ignoreCase = true) }) "That school already exists"
        else {
            val school = School("s-${UUID.randomUUID().toString().take(8)}", clean, district.trim(), "",
                startDate = LocalDate.now().toString(), libraryLevel = 1)
            track(repo.addSchool(school))
            logAudit(AuditSpec("Added school", clean))
            null
        }
    }

    fun upgradeLibrary(school: School) = write("Library upgraded to level ${school.libraryLevel + 1}") {
        if (!ProgrammeCycle.canUpgrade(school)) "This library is already at the highest level"
        else {
            track(repo.upgradeLibrary(school.id, LocalDate.now().toString()))
            logAudit(AuditSpec("Upgraded box library to level ${school.libraryLevel + 1}", school.name))
            null
        }
    }

    // ---- learners (enrolled by staff) and their parents
    fun addLearner(name: String, school: School?, grade: String) = write("Learner enrolled") {
        val g = Grades.parse(grade)
        when {
            name.trim().length < 2 -> "Enter the learner's name"
            school == null -> "Choose the learner's school"
            g == null -> "Choose a grade"
            else -> {
                track(repo.addLearner(name, school.id, g))
                logAudit(AuditSpec("Enrolled learner", "${name.trim()} (${school.name})"))
                null
            }
        }
    }

    fun linkAccount(learner: Learner, account: User) =
        write("${account.displayName} linked to ${learner.name}") {
            track(repo.linkAccount(learner.id, account))
            logAudit(AuditSpec("Linked ${account.role.label} account", "${account.displayName} to ${learner.name}"))
            null
        }

    // ---- reading progress (teachers)
    fun addProgress(user: User, learnerId: String, programme: String, level: Int) = write("Progress recorded") {
        if (programme.isBlank()) "Choose a programme"
        else {
            track(repo.addProgress(learnerId, programme, level, user))
            null
        }
    }

    // ---- field visits (staff)
    fun addVisit(user: User, school: School?, teacher: String, notes: String, followUp: String) = write("Visit logged") {
        when {
            school == null -> "Choose the school you visited"
            notes.isBlank() -> "Write a short note about the visit"
            else -> {
                val now = System.currentTimeMillis()
                track(repo.addVisit(SchoolVisit(UUID.randomUUID().toString(), school.id, school.name, user.id, user.displayName,
                    LocalDate.now().toString(), teacher.trim(), notes.trim(), followUp.trim(), now)))
                null
            }
        }
    }

    // ---- feedback
    fun addFeedback(user: User, text: String, stars: Int) = write("Thank you for your feedback") {
        if (text.isBlank()) "Write your feedback first"
        else {
            track(repo.addFeedback(FeedbackEntry(UUID.randomUUID().toString(), user.id, user.displayName, "General", text.trim(),
                stars.coerceIn(1, 5), System.currentTimeMillis())))
            null
        }
    }

    // ---- resources
    fun addResource(user: User, title: String, type: String, shared: Boolean) = write("Resource added") {
        if (title.isBlank()) "Enter a title"
        else {
            track(repo.addResource(Resource(UUID.randomUUID().toString(), title.trim(), type, user.displayName, shared,
                System.currentTimeMillis())))
            null
        }
    }

    fun setResourceShared(id: String, shared: Boolean) = write { track(repo.setResourceShared(id, shared)); null }

    // ---- reports: one per programme per day, so pressing the button twice does not duplicate
    fun generateReports() = write("Reports generated", AuditSpec("Generated programme reports", LocalDate.now().toString())) {
        val today = LocalDate.now().toString()
        _state.value.programmes.forEach { p ->
            track(repo.addReport(Report("rep-${p.id}-$today", "${p.name} summary", p.id, today)))
        }
        null
    }
}
