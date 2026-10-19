package com.pointblank.lpms.ui.teacher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointblank.lpms.core.model.AttendanceRecord
import com.pointblank.lpms.core.model.Learner
import com.pointblank.lpms.core.model.Session
import com.pointblank.lpms.core.model.SessionStatus
import com.pointblank.lpms.core.util.LearnerInsights
import com.pointblank.lpms.core.util.SessionRules
import com.pointblank.lpms.core.util.timeAgo
import com.pointblank.lpms.data.repository.AuthRepository
import com.pointblank.lpms.data.repository.LpmsRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AttendanceUiState(
    /** Sessions at the teacher's own school only. */
    val sessions: List<Session> = emptyList(),
    val selectedSessionId: String? = null,
    val roster: List<Learner> = emptyList(),
    val marks: Map<String, Boolean> = emptyMap(), // learnerId -> present
    val message: String? = null,
    val isError: Boolean = false,
    val isLoading: Boolean = true,
    /** Who last saved the selected session's attendance, and when (accountability). */
    val lastSaved: String? = null,
    /** False once the session is completed or cancelled: the register is locked. */
    val canMark: Boolean = false,
    val canClose: Boolean = false,
    /** False when the account has no school yet, so there is nothing to show. */
    val hasSchool: Boolean = true
) {
    val selected: Session? get() = sessions.firstOrNull { it.id == selectedSessionId }
}

class TeacherAttendanceViewModel(
    private val repo: LpmsRepository,
    private val auth: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AttendanceUiState())
    val state: StateFlow<AttendanceUiState> = _state.asStateFlow()

    private var saved: List<AttendanceRecord> = emptyList()
    // Unsaved changes per session, so switching sessions does not lose them (as on the website).
    private val drafts = mutableMapOf<String, Map<String, Boolean>>()
    // This view model outlives logout, so remember whose data it holds and start clean for a new user.
    private var loadedForUserId: String? = null

    init {
        load()
    }

    fun load() = loadInternal(null)

    /** "Load" button: discard unsaved changes for this session and show what is saved. */
    fun reload() {
        _state.value.selectedSessionId?.let { drafts.remove(it) }
        loadInternal("Attendance loaded.")
    }

    /** Open sessions first (nearest date first), then closed ones, newest first. */
    private fun order(sessions: List<Session>): List<Session> {
        val open = sessions.filter { SessionRules.isOpen(it) }.sortedBy { it.date }
        val closed = sessions.filterNot { SessionRules.isOpen(it) }.sortedByDescending { it.date }
        return open + closed
    }

    /** The session to start on: the first open one that is due today or earlier, else the first open one. */
    private fun defaultSession(sessions: List<Session>): String? {
        val today = LocalDate.now().toString()
        val open = sessions.filter { SessionRules.isOpen(it) }
        return (open.firstOrNull { it.date <= today } ?: open.firstOrNull() ?: sessions.firstOrNull())?.id
    }

    private fun loadInternal(message: String?) {
        viewModelScope.launch {
            try {
                val user = auth.currentUser.value
                val sameUser = user?.id == loadedForUserId
                if (!sameUser) {
                    drafts.clear()
                    loadedForUserId = user?.id
                }
                val schoolId = if (user != null) repo.schoolIdFor(user) else ""
                val roster = if (user != null) repo.classRoster(schoolId, user.grade) else emptyList()
                val sessions = order(repo.sessions().filter { schoolId.isNotBlank() && it.schoolId == schoolId })
                saved = repo.attendance()
                val previous = if (sameUser) _state.value.selectedSessionId else null
                val selected = previous?.takeIf { id -> sessions.any { it.id == id } } ?: defaultSession(sessions)
                _state.value = withFlags(
                    AttendanceUiState(
                        sessions = sessions,
                        selectedSessionId = selected,
                        roster = roster,
                        marks = marksFor(selected, roster),
                        message = message,
                        isLoading = false,
                        lastSaved = lastSavedFor(selected),
                        hasSchool = schoolId.isNotBlank()
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, message = "Could not load attendance.", isError = true) }
            }
        }
    }

    private fun withFlags(s: AttendanceUiState): AttendanceUiState {
        val role = auth.currentUser.value?.role
        val session = s.selected
        return s.copy(
            canMark = role != null && session != null && SessionRules.canMarkAttendance(role, session),
            canClose = role != null && session != null && SessionRules.canComplete(role, session)
        )
    }

    fun selectSession(id: String) = _state.update {
        withFlags(it.copy(selectedSessionId = id, marks = marksFor(id, it.roster), message = null, lastSaved = lastSavedFor(id)))
    }

    fun setMark(learnerId: String, present: Boolean) {
        val s = _state.value
        val id = s.selectedSessionId ?: return
        if (!s.canMark) return // a closed register cannot be edited
        val updated = s.marks + (learnerId to present)
        drafts[id] = updated
        _state.value = s.copy(marks = updated, message = null)
    }

    fun save() {
        val s = _state.value
        val id = s.selectedSessionId
        if (id == null) {
            _state.update { it.copy(message = "No sessions scheduled yet.", isError = true) }
            return
        }
        if (!s.canMark) {
            _state.update { it.copy(message = "This session is closed, so attendance can't be changed.", isError = true) }
            return
        }
        if (s.roster.isEmpty() || s.marks.isEmpty()) {
            _state.update { it.copy(message = "Mark at least one learner present or absent first.", isError = true) }
            return
        }
        val user = auth.currentUser.value ?: return
        viewModelScope.launch {
            val synced = try {
                repo.saveAttendance(id, s.marks, user)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.localizedMessage ?: "Could not save attendance. Check your connection and try again.", isError = true) }
                return@launch
            }
            drafts.remove(id)
            saved = repo.attendance()
            val present = s.marks.values.count { it }
            val total = s.marks.size
            val rate = LearnerInsights.percent(present, total)
            val summary = "$present/$total present ($rate%)"
            _state.update {
                it.copy(
                    marks = marksFor(id, it.roster),
                    // Offline, the save is kept on the phone and uploaded later; say so instead of hanging.
                    message = if (synced) "Attendance saved! $summary"
                    else "Saved on this phone, $summary. It will sync when you're back online.",
                    isError = false,
                    lastSaved = lastSavedFor(id)
                )
            }
        }
    }

    /** Closes the session: attendance is final and the register is locked. */
    fun closeSession() {
        val s = _state.value
        val session = s.selected ?: return
        val user = auth.currentUser.value ?: return
        if (!SessionRules.canComplete(user.role, session)) return
        viewModelScope.launch {
            val synced = try {
                repo.setSessionStatus(session.id, SessionStatus.COMPLETED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "Could not close the session. Try again.", isError = true) }
                return@launch
            }
            loadInternal(
                if (synced) "Session closed. The register is now locked."
                else "Session closed on this phone. It will sync when you're back online."
            )
        }
    }

    private fun lastSavedFor(sessionId: String?): String? {
        if (sessionId == null) return null
        val newest = saved.filter { it.sessionId == sessionId && it.markedAt > 0 }.maxByOrNull { it.markedAt } ?: return null
        return "Last saved by ${newest.markedByName.ifBlank { "a teacher" }} \u00B7 ${timeAgo(newest.markedAt)}"
    }

    // Saved value wins over the default "present", and an unsaved draft wins over both.
    private fun marksFor(sessionId: String?, roster: List<Learner>): Map<String, Boolean> {
        if (sessionId == null) return emptyMap()
        val draft = drafts[sessionId]
        val savedHere = saved.filter { it.sessionId == sessionId }.associate { it.learnerId to it.present }
        return roster.associate { l -> l.id to (draft?.get(l.id) ?: savedHere[l.id] ?: true) }
    }
}
