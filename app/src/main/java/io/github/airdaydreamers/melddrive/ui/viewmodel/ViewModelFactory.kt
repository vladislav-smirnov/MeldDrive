package io.github.airdaydreamers.melddrive.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import io.github.airdaydreamers.melddrive.data.repository.ServerRepository
import io.github.airdaydreamers.melddrive.data.settings.SettingsManager

class ViewModelFactory(
    private val repository: FileRepository,
    private val serverRepository: ServerRepository,
    private val settingsManager: SettingsManager? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel = when {
            modelClass.isAssignableFrom(FileManagerViewModel::class.java) -> {
                FileManagerViewModel(repository, serverRepository)
            }

            modelClass.isAssignableFrom(AddStorageViewModel::class.java) -> {
                AddStorageViewModel(serverRepository)
            }

            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(settingsManager!!)
            }

            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
        @Suppress("UNCHECKED_CAST")
        return viewModel as T
    }
}
