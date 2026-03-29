package com.googleac.feature.drive.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.googleac.core.data.db.entity.DriveFileEntity

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DriveScreen(
    viewModel: DriveViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val navigator = rememberListDetailPaneScaffoldNavigator<DriveFileEntity>()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Files") },
                actions = {
                    IconButton(onClick = { viewModel.toggleSearch() }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            )
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
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, file)
                        }
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    val selectedFile = navigator.currentDestination?.content
                    if (selectedFile != null) {
                        FileDetailPane(
                            file = selectedFile,
                            onRename = { viewModel.renameFile(selectedFile.fileId, selectedFile.accountId, it) },
                            onDelete = { viewModel.deleteFile(selectedFile.fileId, selectedFile.accountId) }
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
    LazyColumn {
        items(files, key = { "${it.accountId}/${it.fileId}" }) { file ->
            FileListItem(file = file, onClick = { onFileClick(file) })
        }
    }
}

@Composable
private fun FileListItem(
    file: DriveFileEntity,
    onClick: () -> Unit
) {
    val isFolder = file.mimeType == "application/vnd.google-apps.folder"
    ListItem(
        headlineContent = { Text(file.name) },
        supportingContent = {
            Text(
                text = file.modifiedTime ?: file.mimeType,
                style = MaterialTheme.typography.bodySmall
            )
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
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    val capabilities = file.capabilitiesJson
    // Parse capabilities to determine allowed actions
    val canDelete = capabilities?.contains("\"canDelete\":true") == true
    val canRename = capabilities?.contains("\"canRename\":true") == true
    val canMove = capabilities?.contains("\"canMoveItemWithinDrive\":true") == true

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
        file.size?.let {
            Text(
                text = "Size: ${it / 1024} KB",
                style = MaterialTheme.typography.bodySmall
            )
        }
        file.modifiedTime?.let {
            Text(text = "Modified: $it", style = MaterialTheme.typography.bodySmall)
        }
        // Role-based action buttons (disabled when not permitted)
        Row(modifier = Modifier.fillMaxWidth()) {
            // TODO: Replace placeholder rename with a dialog that collects user input
            IconButton(onClick = { onRename("Renamed_${file.name}") }, enabled = canRename) {
                Icon(Icons.Default.Edit, contentDescription = "Rename")
            }
            IconButton(onClick = { onDelete() }, enabled = canDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
            IconButton(onClick = {}, enabled = canMove) {
                Icon(Icons.Default.DriveFileMove, contentDescription = "Move")
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
