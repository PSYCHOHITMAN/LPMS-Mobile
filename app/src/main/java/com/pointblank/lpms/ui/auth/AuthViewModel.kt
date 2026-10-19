package com.pointblank.lpms.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointblank.lpms.core.model.Role
import com.pointblank.lpms.core.model.School
import com.pointblank.lpms.core.util.Grades
import com.pointblank.lpms.core.util.Validators
import com.pointblank.lpms.data.repository.AuthRepository
import com.pointblank.lpms.data.repository.AuthResult
import com.pointblank.lpms.data.repository.LpmsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val info: String? = null
)

class AuthViewModel(
    private val repo: AuthRepository,
    private val lpms: LpmsRepository
) : ViewModel() {

    val currentUser = repo.currentUser

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val _schools = MutableStateFlow<List<School>>(emptyList())
    /** Schools to choose from on the register screen (they are publicly readable). */
    val schools: StateFlow<List<School>> = _schools.asStateFlow()

    init { loadSchools() }

    fun loadSchools() {
        viewModelScope.launch { _schools.value = lpms.schools() }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isEmpty()) {
            _state.update { it.copy(error = "Enter your email and password") }
            return
        }
        submit { repo.login(email, password) }
    }

    /** The role, school, grade and child's name checks shared by email and Google sign-up. */
    fun profileError(role: Role?, school: School?, grade: String, childName: String): String? =
        (if (role == null) "Choose Teacher or Parent / Guardian" else null)
            ?: (if (_schools.value.isNotEmpty() && school == null) "Choose your school" else null)
            ?: (if (role == Role.TEACHER) Validators.validateGrade(grade) else null)
            ?: (if (role == Role.PARENT) Validators.validateChildName(childName) else null)

    /** Only teachers and parents sign themselves up; staff enrol learners and an admin adds coordinators. */
    fun register(
        name: String, email: String, password: String, confirm: String,
        role: Role?, school: School?, grade: String, childName: String
    ) {
        val error = Validators.validateName(name)
            ?: Validators.validateEmail(email)
            ?: Validators.validatePassword(password)
            ?: (if (password != confirm) "Passwords do not match" else null)
            ?: profileError(role, school, grade, childName)
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        val gradeLabel = if (role == Role.TEACHER) Grades.parse(grade)?.let { Grades.label(it) } ?: "" else ""
        submit {
            repo.register(email, name, password, role!!, school?.name ?: "", gradeLabel, school?.id ?: "", childName)
        }
    }

    /** Google sign-up using the details already chosen on the register screen. */
    fun googleSignUp(idToken: String, role: Role, school: School?, grade: String, childName: String) {
        val gradeLabel = if (role == Role.TEACHER) Grades.parse(grade)?.let { Grades.label(it) } ?: "" else ""
        submit { repo.signUpWithGoogle(idToken, role, school?.name ?: "", gradeLabel, school?.id ?: "", childName) }
    }

    fun signInWithGoogle(idToken: String) = submit { repo.signInWithGoogle(idToken) }

    fun showError(message: String) = _state.update { it.copy(error = message) }

    fun clearError() = _state.update { it.copy(error = null, info = null) }

    private fun submit(block: suspend () -> AuthResult) {
        viewModelScope.launch {
            _state.value = AuthUiState(isLoading = true)
            _state.value = try {
                when (val r = block()) {
                    is AuthResult.Failure -> AuthUiState(error = r.message)
                    is AuthResult.Pending -> AuthUiState(info = r.message)
                    is AuthResult.Success -> AuthUiState()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AuthUiState(error = "Something went wrong. Please try again")
            }
        }
    }
}
