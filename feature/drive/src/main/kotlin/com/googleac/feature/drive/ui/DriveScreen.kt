package com.googleac.feature.drive.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.googleac.core.data.db.entity.AccountEntity
import com.googleac.core.data.db.entity.DriveFileEntity
import kotlinx.coroutines.launch

/** MIME type options shown in the filter chip row. */
private data class MimeFilter(val label: String, val mimeType: String?)

private val MIME_FILTERS = listOf(
    MimeFilter("All", null),
    MimeFilter("Folders", "application/vnd.google-apps.folder"),
    MimeFilter("Docs", "application/vnd.google-apps.document"),
    MimeFilter("Sheets", "application/vnd.google-apps.spreadsheet"),
    MimeFilter("PDFs", "application/pdf")
)

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DriveScreen(
    viewModel: DriveViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val navigator = rememberListDetailPaneScaffoldNavigator<DriveFileEntity>()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("All Files")
                            val subtitle = when (uiState.accountCount) {
                                0 -> "No accounts connected"
                                1 -> "Files from 1 account"
                                else -> "Files from ${uiState.accountCount} accounts"
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleSearch() }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                )
                // Search bar — visible only when the user has activated search
                AnimatedVisibility(visible = uiState.isSearching) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.search(it) },
                        placeholder = { Text("Search across all accounts…") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                // MIME-type filter chips
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    MIME_FILTERS.forEach { filter ->
                        FilterChip(
                            selected = uiState.filterMimeType == filter.mimeType,
                            onClick = { viewModel.filterByMimeType(filter.mimeType) },
                            label = { Text(filter.label) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        ListDetailPaneScaffold(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            directive = navigator.scaffoldDirective,
            value = navigator.scaffoldValue,
            listPane = {
                AnimatedPane {
                    FileListPane(
                        files = uiState.files,
                        onFileClick = { file ->
                            scope.launch {
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, file)
                            }
                        }
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    val selectedFile = navigator.currentDestination?.contentKey
                    if (selectedFile != null) {
                        FileDetailPane(
                            file = selectedFile,
                            accounts = accounts,
                            onRename = { viewModel.renameFile(selectedFile.fileId, selectedFile.accountId, it) },
                            onDelete = { viewModel.deleteFile(selectedFile.fileId, selectedFile.accountId) },
                            onMove = { toAccountId ->
                                viewModel.moveFile(selectedFile.fileId, selectedFile.accountId, toAccountId)
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Select a file to view details",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun FileListPane(
    files: List<DriveFileEntity>,
    onFileClick: (DriveFileEntity) -> Unit
) {
    if (files.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No files found.\nTap the search icon to filter across all accounts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp)
            )
        }
    } else {
        LazyColumn {
            items(files, key = { "${it.accountId}/${it.fileId}" }) { file ->
                FileListItem(file = file, onClick = { onFileClick(file) })
            }
        }
    }
}

@Composable
private fun FileListItem(
    file: DriveFileEntity,
    onClick: () -> Unit
) {
    val isFolder = file.mimeType == "application/vnd.google-apps.folder"
    // Derive a short human-readable account badge (e.g. first 6 chars of accountId)
    val accountBadge = file.accountId.take(6)
    ListItem(
        headlineContent = { Text(file.name) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "[$accountBadge]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = file.modifiedTime ?: file.mimeType,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = if (isFolder) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (isFolder) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
private fun FileDetailPane(
    file: DriveFileEntity,
    accounts: List<AccountEntity>,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onMove: (String) -> Unit
) {
    val capabilities = file.capabilitiesJson
    // Parse capabilities to determine allowed actions
    val canDelete = capabilities?.contains("\"canDelete\":true") == true
    val canRename = capabilities?.contains("\"canRename\":true") == true
    val canMove = capabilities?.contains("\"canMoveItemWithinDrive\":true") == true

    var showRenameDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var renameInput by remember(file.fileId) { mutableStateOf(file.name) }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            onRename(renameInput.trim())
                        }
                        showRenameDialog = false
                    }
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Account picker for cross-account file move
    if (showMoveDialog) {
        val targetAccounts = accounts.filter { it.accountId != file.accountId }
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("Move to account") },
            text = {
                if (targetAccounts.isEmpty()) {
                    Text("No other accounts available. Add another account first.")
                } else {
                    Column {
                        targetAccounts.forEach { account ->
                            TextButton(
                                onClick = {
                                    onMove(account.accountId)
                                    showMoveDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(account.displayName.ifBlank { account.email })
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = file.name,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = file.mimeType,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        file.size?.let { bytes ->
            val sizeText = when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
                else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            }
            Text(text = "Size: $sizeText", style = MaterialTheme.typography.bodySmall)
        }
        file.modifiedTime?.let {
            Text(text = "Modified: $it", style = MaterialTheme.typography.bodySmall)
        }
        // Role-based action buttons (disabled when not permitted per Drive API capabilities)
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { showRenameDialog = true }, enabled = canRename) {
                Icon(Icons.Default.Edit, contentDescription = "Rename")
            }
            IconButton(onClick = { onDelete() }, enabled = canDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
            IconButton(onClick = { showMoveDialog = true }, enabled = canMove) {
                Icon(Icons.Default.DriveFileMove, contentDescription = "Move to account")
            }
        }
        file.semanticIndexText?.let {
            Text(
                text = "Summary:",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(text = it.take(500), style = MaterialTheme.typography.bodySmall)
        }
    }
}
