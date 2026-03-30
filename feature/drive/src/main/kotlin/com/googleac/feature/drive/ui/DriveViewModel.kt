package com.googleac.feature.drive.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.googleac.core.data.db.dao.AccountDao
import com.googleac.core.data.db.entity.AccountEntity
import com.googleac.core.data.db.entity.DriveFileEntity
import com.googleac.feature.drive.data.repository.DriveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DriveUiState(
    val files: List<DriveFileEntity> = emptyList(),
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val filterMimeType: String? = null
)

@HiltViewModel
class DriveViewModel @Inject constructor(
    private val repository: DriveRepository,
    private val accountDao: AccountDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isSearching = MutableStateFlow(false)
    private val _filterMimeType = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _files = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) repository.observeAllFiles()
            else repository.searchAllFiles(query)
        }

    /** Observable list of all registered accounts, used by the Move-to-account picker. */
    val accounts: StateFlow<List<AccountEntity>> = accountDao.observeAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<DriveUiState> = combine(
        _files, _isSearching, _searchQuery, _filterMimeType
    ) { files, searching, query, filterMimeType ->
        val filteredFiles = if (filterMimeType != null) {
            files.filter { it.mimeType == filterMimeType }
        } else {
            files
        }
        DriveUiState(
            files = filteredFiles,
            isSearching = searching,
            searchQuery = query,
            filterMimeType = filterMimeType
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DriveUiState())

    fun toggleSearch() {
        _isSearching.value = !_isSearching.value
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    /** Filter the file list to files matching [mimeType]; pass null to clear the filter. */
    fun filterByMimeType(mimeType: String?) {
        _filterMimeType.value = mimeType
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

    fun moveFile(fileId: String, fromAccountId: String, toAccountId: String) {
        viewModelScope.launch {
            repository.moveFile(fileId, fromAccountId, toAccountId)
        }
    }
}
