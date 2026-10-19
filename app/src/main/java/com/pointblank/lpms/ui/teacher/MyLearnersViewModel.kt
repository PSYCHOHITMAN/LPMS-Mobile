package com.pointblank.lpms.ui.teacher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointblank.lpms.core.model.Learner
import com.pointblank.lpms.core.util.LearnerInsights
import com.pointblank.lpms.core.util.LearnerStatus
import com.pointblank.lpms.data.repository.AuthRepository
import com.pointblank.lpms.data.repository.LpmsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LearnerRow(
    val learner: Learner,
    val level: Int?,
    val attendance: Int?,
    val status: LearnerStatus
)

data class MyLearnersUiState(
    val rows: List<LearnerRow> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true
) {
    val visible: List<LearnerRow>
        get() = if (query.isBlank()) rows
        else rows.filter { it.learner.name.contains(query.trim(), ignoreCase = true) }
}

class MyLearnersViewModel(
    private val repo: LpmsRepository,
    private val auth: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MyLearnersUiState())
    val state: StateFlow<MyLearnersUiState> = _state.asStateFlow()

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun load() {
        viewModelScope.launch {
            try {
                loadRows()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun loadRows() {
        run {
            val user = auth.currentUser.value
            val roster = if (user != null) repo.classRoster(repo.schoolIdFor(user), user.grade) else emptyList()
            val progress = repo.progress()
            val attendance = repo.attendance()
            val rows = roster.map { l ->
                val level = LearnerInsights.latestLevel(l.id, progress)
                LearnerRow(l, level, LearnerInsights.attendanceRate(l.id, attendance), LearnerInsights.statusFor(level))
            }
            _state.update { it.copy(rows = rows, isLoading = false) }
        }
    }
}
