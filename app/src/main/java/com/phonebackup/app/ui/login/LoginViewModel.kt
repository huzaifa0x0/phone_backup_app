package com.phonebackup.app.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonebackup.app.data.model.LoginRequest
import com.phonebackup.app.data.prefs.BackupPreferences
import com.phonebackup.app.data.repository.BackupRepository
import com.phonebackup.app.util.normalizeServerUrl
import kotlinx.coroutines.launch

class LoginViewModel(
    private val repository: BackupRepository,
    private val prefs: BackupPreferences
) : ViewModel() {

    private val _loginState = MutableLiveData<LoginState>()
    val loginState: LiveData<LoginState> = _loginState

    init {
        // If a valid token already exists, skip straight to main screen
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

        prefs.serverUrl = normalizeServerUrl(serverUrl)

        viewModelScope.launch {
            val result = repository.login(LoginRequest(username, password))
            result.fold(
                onSuccess = { authResponse ->
                    // Persist token and username for all future requests
                    prefs.token = authResponse.token
                    prefs.username = authResponse.username
                    _loginState.value = LoginState.Success
                },
                onFailure = { e ->
                    _loginState.value = LoginState.Error(
                        when {
                            e.message?.contains("401") == true -> "Invalid username or password"
                            e.message?.contains("Unable to resolve") == true ||
                            e.message?.contains("failed to connect") == true ->
                                "Cannot reach server. Check the URL and your connection."
                            else -> "Login failed: ${e.message}"
                        }
                    )
                }
            )
        }
    }

    fun logout() {
        prefs.clearCredentials()
        _loginState.value = LoginState.LoggedOut
    }


    sealed class LoginState {
        object Loading : LoginState()
        object Success : LoginState()
        object LoggedOut : LoginState()
        data class Error(val message: String) : LoginState()
    }
}
