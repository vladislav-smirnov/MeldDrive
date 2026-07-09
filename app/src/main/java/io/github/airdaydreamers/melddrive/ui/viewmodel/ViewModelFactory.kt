package io.github.airdaydreamers.melddrive.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.airdaydreamers.melddrive.data.repository.FileRepository
import io.github.airdaydreamers.melddrive.data.repository.ServerRepository

class ViewModelFactory(private val repository: FileRepository, private val serverRepository: ServerRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FileManagerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FileManagerViewModel(repository, serverRepository) as T
        }
        if (modelClass.isAssignableFrom(AddStorageViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AddStorageViewModel(serverRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
