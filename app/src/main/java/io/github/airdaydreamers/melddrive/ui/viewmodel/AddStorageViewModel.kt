package io.github.airdaydreamers.melddrive.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.airdaydreamers.melddrive.data.db.RemoteServer
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddStorageState(
    val displayName: String = "",
    val host: String = "",
    val port: String = "445",
    val username: String = "",
    val password: String = "",
    val isAnonymous: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

class AddStorageViewModel(private val repository: FileRepository) : ViewModel() {
    private val _state = MutableStateFlow(AddStorageState())
    val state = _state.asStateFlow()

    fun onDisplayNameChange(value: String) = _state.update { it.copy(displayName = value) }
    fun onHostChange(value: String) = _state.update { it.copy(host = value) }
    fun onPortChange(value: String) = _state.update { it.copy(port = value) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value) }
    fun onAnonymousChange(value: Boolean) = _state.update { it.copy(isAnonymous = value) }

    fun saveServer() {
        val s = _state.value
        if (s.host.isBlank() || s.displayName.isBlank()) {
            _state.update { it.copy(error = "Host and Display Name are mandatory") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val server = RemoteServer(
                    displayName = s.displayName,
                    host = s.host,
                    port = s.port.toIntOrNull() ?: 445,
                    username = if (s.isAnonymous) null else s.username,
                    password = if (s.isAnonymous) null else s.password,
                    isAnonymous = s.isAnonymous
                )
                repository.addRemoteServer(server)
                _state.update { it.copy(isLoading = false, isSuccess = true) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
