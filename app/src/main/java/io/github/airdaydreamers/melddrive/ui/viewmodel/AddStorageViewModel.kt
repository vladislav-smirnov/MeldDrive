package io.github.airdaydreamers.melddrive.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.airdaydreamers.melddrive.data.db.RemoteServer
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import io.github.airdaydreamers.melddrive.ui.mvi.AddStorageEffect
import io.github.airdaydreamers.melddrive.ui.mvi.AddStorageIntent
import io.github.airdaydreamers.melddrive.ui.mvi.AddStorageState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AddStorageViewModel(private val repository: FileRepository) : ViewModel() {
    private val _state = MutableStateFlow(AddStorageState())
    val state = _state.asStateFlow()

    private val _effect = Channel<AddStorageEffect>()
    val effect = _effect.receiveAsFlow()

    fun onIntent(intent: AddStorageIntent) {
        when (intent) {
            is AddStorageIntent.DisplayNameChange -> _state.update { it.copy(displayName = intent.value) }
            is AddStorageIntent.HostChange -> _state.update { it.copy(host = intent.value) }
            is AddStorageIntent.PortChange -> _state.update { it.copy(port = intent.value) }
            is AddStorageIntent.UsernameChange -> _state.update { it.copy(username = intent.value) }
            is AddStorageIntent.PasswordChange -> _state.update { it.copy(password = intent.value) }
            is AddStorageIntent.AnonymousChange -> _state.update { it.copy(isAnonymous = intent.value) }
            AddStorageIntent.SaveServer -> saveServer()
        }
    }

    private fun saveServer() {
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
                _effect.send(AddStorageEffect.NavigateBack)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
