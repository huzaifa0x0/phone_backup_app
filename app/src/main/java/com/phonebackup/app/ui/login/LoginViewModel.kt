package com.phonebackup.app.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonebackup.app.data.model.LoginRequest
import com.phonebackup.app.data.prefs.BackupPreferences
import com.phonebackup.app.data.repository.BackupRepository
import kotlinx.coroutines.launch

class LoginViewModel(
    private val repository: BackupRepository,
    private val prefs: BackupPreferences
) : ViewModel() {

    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    init {
        // Auto-login if token exists
        if (!prefs.token.isNullOrEmpty()) {
            _loginState.value = LoginState.Success
        }
    }

    fun login(serverUrl: String, username: String, password: String) {
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("All fields are required")
            return
        }

        _loginState.value = LoginState.Loading
        
        // Save the fallback URL
        prefs.serverUrl = if (serverUrl.endsWith("/")) serverUrl.dropLast(1) else serverUrl

        viewModelScope.launch {
            val result = repository.login(LoginRequest(username, password))
            result.onSuccess { authResponse ->
                prefs.token = authResponse.token
                prefs.username = username
                _loginState.value = LoginState.Success
            }.onFailure { error ->
                _loginState.value = LoginState.Error(error.message ?: "Login Failed")
            }
        }
    }

    sealed class LoginState {
        object Loading : LoginState()
        object Success : LoginState()
        data class Error(val message: String) : LoginState()
    }
}