package com.pointblank.lpms.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointblank.lpms.core.model.Role
import com.pointblank.lpms.core.model.SessionStatus
import com.pointblank.lpms.core.model.isStaff
import com.pointblank.lpms.core.util.Grades
import com.pointblank.lpms.core.util.LearnerInsights
import com.pointblank.lpms.core.util.Messaging
import com.pointblank.lpms.core.util.ProgrammeCycle
import com.pointblank.lpms.core.util.ReportMetrics
import com.pointblank.lpms.core.util.formatDisplayDate
import com.pointblank.lpms.data.repository.AuthRepository
import com.pointblank.lpms.data.repository.LpmsRepository
import com.pointblank.lpms.data.seed.DemoDataSeeder
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionLine(val title: String, val subtitle: String)

/** One child as a parent sees them. */
data class ChildSummary(
    val name: String,
    val gradeText: String,
    val school: String,
    val level: Int?,
    val attendance: Int?
)

/** Everything the dashboards show, all derived from the (privacy-scoped) database. */
data class HomeData(
    val loaded: Boolean = false,
    val upcoming: List<SessionLine> = emptyList(),
    // coordinator / admin
    val schools: Int = 0,
    val learners: Int = 0,
    val sessions: Int = 0,
    val users: Int = 0,
    val teachers: Int = 0,
    val pending: Int = 0,
    val usersByRole: Map<Role, Int> = emptyMap(),
    val averageAttendance: Int? = null,
    val visitsThisMonth: Int = 0,
    val unreadMessages: Int = 0,
    val libraryUpgradesDue: Int = 0,
    val learnersImproved: Int = 0,
    val learnersAssessed: Int = 0,
    val averageGain: Double? = null,
    // teacher
    val myLearners: Int = 0,
    val sessionsFacilitated: Int = 0,
    val atReadingLevel: Int = 0,
    // teacher (class), learner (own) and parent (children)
    val attendance: Int? = null,
    // learner
    val hasLearnerProfile: Boolean = true,
    val programmes: Int = 0,
    val sessionsAttended: Int = 0,
    val readingLevel: Int? = null,
    val progressRows: List<Pair<String, Int>> = emptyList(),
    // parent
    val children: List<ChildSummary> = emptyList()
)

class HomeViewModel(
    private val repo: LpmsRepository,
    private val auth: AuthRepository
) : ViewModel() {

    private val _data = MutableStateFlow(HomeData())
    val data: StateFlow<HomeData> = _data.asStateFlow()

    private val _seedMessage = MutableStateFlow<String?>(null)
    val seedMessage: StateFlow<String?> = _seedMessage.asStateFlow()

    private val _seeding = MutableStateFlow(false)
    val seeding: StateFlow<Boolean> = _seeding.asStateFlow()

    private var loadedForUserId: String? = null

    init { refresh() }

    fun refresh() {
        // This view model outlives logout, so never show the previous user's numbers to the next one.
        val uid = auth.currentUser.value?.id
        if (uid != loadedForUserId) {
            loadedForUserId = uid
            _data.value = HomeData()
            _seedMessage.value = null
        }
        viewModelScope.launch {
            try {
                _data.value = compute()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // keep what is already on screen
            }
        }
    }

    fun seedDemoData() {
        viewModelScope.launch {
            _seeding.value = true
            _seedMessage.value = try {
                DemoDataSeeder(auth, repo).run()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Could not load demo data: ${e.localizedMessage ?: "unknown error"}"
            }
            _seeding.value = false
            refresh()
        }
    }

    private suspend fun compute(): HomeData {
        val user = auth.currentUser.value ?: return HomeData()
        val schools = repo.schools()
        val learners = repo.learners()          // already limited to what this user may see
        val sessions = repo.sessions()
        val attendance = repo.attendance()
        val progress = repo.progress()
        val enrollments = repo.enrollments()
        val staff = user.role.isStaff
        val users = if (staff) auth.allUsers() else emptyList()
        val visits = if (staff) repo.visits() else emptyList()
        val roster = if (user.role == Role.TEACHER) repo.classRoster(repo.schoolIdFor(user), user.grade) else emptyList()
        val rosterIds = roster.map { it.id }.toSet()
        val mine = learners.firstOrNull { it.userId == user.id }

        val schoolNames = schools.associate { it.id to it.name }
        val today = LocalDate.now()
        val upcoming = sessions
            .filter { it.date >= today.toString() && it.status == SessionStatus.SCHEDULED } // not closed or cancelled ones
            .filter { user.role != Role.LEARNER || mine == null || it.schoolId == mine.schoolId }
            .filter { user.role != Role.PARENT || learners.any { c -> c.schoolId == it.schoolId } }
            .sortedBy { it.date }
            .take(3)
            .map { SessionLine(it.programme, "${schoolNames[it.schoolId] ?: ""} \u00B7 ${formatDisplayDate(it.date)}") }

        val headline = ReportMetrics.headline(schools, users.count { it.role == Role.TEACHER }, learners, attendance, progress)
        val monthStart = today.minusDays(30).toString()

        return HomeData(
            loaded = true,
            upcoming = upcoming,
            schools = schools.size,
            learners = learners.size,
            sessions = sessions.size,
            users = users.size,
            teachers = users.count { it.role == Role.TEACHER },
            pending = users.count { !it.approved },
            usersByRole = users.groupingBy { it.role }.eachCount(),
            averageAttendance = LearnerInsights.overallRate(attendance),
            visitsThisMonth = visits.count { it.date >= monthStart },
            unreadMessages = Messaging.unreadCount(user.id, repo.messages()),
            libraryUpgradesDue = schools.count { ProgrammeCycle.status(it, today).upgradeDue },
            learnersImproved = headline.learnersImproved,
            learnersAssessed = headline.learnersAssessed,
            averageGain = headline.averageGain,
            myLearners = roster.size,
            // sessions this person has run: closed sessions they facilitated
            sessionsFacilitated = sessions.count { it.facilitator == user.displayName && it.status == SessionStatus.COMPLETED },
            // "At reading level" = learners whose latest level is 3 (on track) or higher
            atReadingLevel = roster.count { (LearnerInsights.latestLevel(it.id, progress) ?: 0) >= 3 },
            attendance = when (user.role) {
                Role.TEACHER -> LearnerInsights.overallRate(attendance.filter { it.learnerId in rosterIds })
                Role.LEARNER -> mine?.let { LearnerInsights.attendanceRate(it.id, attendance) }
                Role.PARENT -> LearnerInsights.overallRate(attendance)
                else -> null
            },
            hasLearnerProfile = user.role != Role.LEARNER || mine != null,
            programmes = mine?.let { m -> enrollments.count { it.learnerId == m.id } } ?: 0,
            sessionsAttended = mine?.let { m -> attendance.count { it.learnerId == m.id && it.present } } ?: 0,
            readingLevel = mine?.let { LearnerInsights.latestLevel(it.id, progress) },
            progressRows = mine?.let { m ->
                progress.filter { it.learnerId == m.id }.groupBy { it.programme }
                    .map { (programme, rows) -> programme to rows.sortedBy { it.date }.last().level }
            } ?: emptyList(),
            children = if (user.role == Role.PARENT) learners.map { c ->
                ChildSummary(
                    name = c.name, gradeText = Grades.display(c.grade), school = schoolNames[c.schoolId] ?: "",
                    level = LearnerInsights.latestLevel(c.id, progress),
                    attendance = LearnerInsights.attendanceRate(c.id, attendance)
                )
            } else emptyList()
        )
    }
}
