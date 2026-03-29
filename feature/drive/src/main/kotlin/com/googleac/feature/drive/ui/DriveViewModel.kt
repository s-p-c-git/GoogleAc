package com.googleac.feature.drive.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.googleac.core.data.db.entity.DriveFileEntity
import com.googleac.feature.drive.data.repository.DriveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DriveUiState(
    val files: List<DriveFileEntity> = emptyList(),
    val isSearching: Boolean = false,
    val searchQuery: String = ""
)

@HiltViewModel
class DriveViewModel @Inject constructor(
    private val repository: DriveRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isSearching = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _files = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) repository.observeAllFiles()
            else repository.searchFiles(accountId = "", query = query) // empty = all accounts
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<DriveUiState> = MutableStateFlow(DriveUiState()).also { state ->
        viewModelScope.launch {
            _files.collect { files ->
                state.value = DriveUiState(
                    files = files,
                    isSearching = _isSearching.value,
                    searchQuery = _searchQuery.value
                )
            }
        }
    }

    fun toggleSearch() {
        _isSearching.value = !_isSearching.value
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun renameFile(fileId: String, accountId: String, newName: String) {
        viewModelScope.launch {
            repository.renameFile(accountId, fileId, newName, null)
        }
    }

    fun deleteFile(fileId: String, accountId: String) {
        viewModelScope.launch {
            repository.deleteFile(accountId, fileId, null)
        }
    }
}
