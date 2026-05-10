package com.phonebackup.app.ui.backups

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phonebackup.app.data.model.BackupFile
import com.phonebackup.app.data.repository.BackupRepository
import kotlinx.coroutines.launch

class BackupsViewModel(private val repository: BackupRepository) : ViewModel() {

    private val _files = MutableLiveData<List<BackupFile>>()
    val files: LiveData<List<BackupFile>> = _files

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    init {
        fetchFiles()
    }

    fun fetchFiles() {
        _isLoading.value = true
        viewModelScope.launch {
            val result = repository.listFiles()
            result.onSuccess { fileList ->
                _files.value = fileList
                _isLoading.value = false
            }.onFailure { exception ->
                _error.value = exception.message ?: "Failed to load files"
                _isLoading.value = false
            }
        }
    }

    fun deleteFile(file: BackupFile) {
        viewModelScope.launch {
            val result = repository.deleteFile(file.filename)
            if (result.isSuccess) {
                fetchFiles() // Refresh list on success
            } else {
                _error.value = "Failed to delete ${file.original_name}"
            }
        }
    }

    // For a complete implementation, downloadFile would fetch the stream, 
    // pass it to the EncryptionManager if encrypted, and save it to the Downloads folder.
    // That is omitted here to keep the skeleton lightweight.
}