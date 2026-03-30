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
    val filterMimeType: String? = null,
    /** Number of distinct accounts whose files are shown in the current list. */
    val accountCount: Int = 0
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
        _files, _isSearching, _searchQuery, _filterMimeType, accounts
    ) { args ->
        val files = args[0] as List<*>
        val searching = args[1] as Boolean
        val query = args[2] as String
        val filterMimeType = args[3] as? String
        @Suppress("UNCHECKED_CAST")
        val allAccounts = args[4] as List<AccountEntity>
        @Suppress("UNCHECKED_CAST")
        val fileList = files as List<DriveFileEntity>
        val filteredFiles = if (filterMimeType != null) {
            fileList.filter { it.mimeType == filterMimeType }
        } else {
            fileList
        }
        DriveUiState(
            files = filteredFiles,
            isSearching = searching,
            searchQuery = query,
            filterMimeType = filterMimeType,
            accountCount = allAccounts.size
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
