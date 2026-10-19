package com.pointblank.lpms.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pointblank.lpms.core.util.Validators
import com.pointblank.lpms.data.repository.AuthRepository
import com.pointblank.lpms.data.repository.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

class SettingsViewModel(private val repo: AuthRepository) : ViewModel() {

    val currentUser = repo.currentUser

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun saveName(name: String) =
        submit("Name updated") { repo.updateDisplayName(name) }

    fun changePassword(old: String, new: String, confirm: String) {
        val error = Validators.validatePassword(new)
            ?: if (new != confirm) "Passwords do not match" else null
        if (error != null) {
            _state.value = SettingsUiState(message = error, isError = true)
            return
        }
        submit("Password changed") { repo.changePassword(old, new) }
    }

    fun logout() = repo.logout()

    private fun submit(successMessage: String, block: suspend () -> AuthResult) {
        viewModelScope.launch {
            _state.value = SettingsUiState(isLoading = true)
            _state.value = when (val r = block()) {
                is AuthResult.Success -> SettingsUiState(message = successMessage)
                is AuthResult.Failure -> SettingsUiState(message = r.message, isError = true)
                is AuthResult.Pending -> SettingsUiState(message = r.message)
            }
        }
    }
}
