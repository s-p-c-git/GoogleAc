package com.googleac.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data class Success(val email: String) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

@HiltViewModel
class AuthViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    fun startAuth() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            // The actual OAuth flow is initiated from the Activity via a custom tab.
            // This ViewModel coordinates state; actual URL launching happens via the UI.
            _uiState.value = AuthUiState.Idle
        }
    }

    fun handleAuthResult(email: String) {
        _uiState.value = AuthUiState.Success(email)
    }

    fun handleAuthError(message: String) {
        _uiState.value = AuthUiState.Error(message)
    }
}
