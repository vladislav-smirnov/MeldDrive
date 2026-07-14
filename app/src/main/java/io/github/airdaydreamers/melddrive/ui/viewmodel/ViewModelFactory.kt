package io.github.airdaydreamers.melddrive.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import io.github.airdaydreamers.melddrive.data.repository.ServerRepository
import io.github.airdaydreamers.melddrive.data.storage.SettingsManager

class ViewModelFactory(private val repository: FileRepository, private val serverRepository: ServerRepository, private val settingsManager: SettingsManager) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return when {
            modelClass.isAssignableFrom(FileManagerViewModel::class.java) -> {
                FileManagerViewModel(repository, serverRepository) as T
            }

            modelClass.isAssignableFrom(AddStorageViewModel::class.java) -> {
                AddStorageViewModel(serverRepository) as T
            }

            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(settingsManager) as T
            }

            else -> {
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
