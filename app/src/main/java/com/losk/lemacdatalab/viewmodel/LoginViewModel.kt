package com.losk.lemacdatalab.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val isLoginSuccess: Boolean = false,
    val errorMessage: String? = null
)

class LoginViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(newEmail: String) {
        _uiState.update { it.copy(email = newEmail, errorMessage = null) }
    }

    fun onPasswordChange(newPass: String) {
        _uiState.update { it.copy(password = newPass, errorMessage = null) }
    }

    fun login() {
        if (_uiState.value.email.isBlank() || _uiState.value.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Los campos no pueden estar vacíos") }
            return
        }
        _uiState.update { it.copy(isLoading = true) }
        _uiState.update { it.copy(isLoading = false, isLoginSuccess = true) }
    }
}