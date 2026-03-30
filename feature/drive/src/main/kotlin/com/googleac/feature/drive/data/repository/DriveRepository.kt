package com.googleac.feature.drive.data.repository

import android.util.Log
import com.googleac.core.data.db.dao.DriveFileDao
import com.googleac.core.data.db.entity.DriveFileEntity
import com.googleac.feature.drive.data.api.DriveApiService
import com.googleac.feature.drive.data.model.DriveFileResponse
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveRepository @Inject constructor(
    private val apiService: DriveApiService,
    private val driveFileDao: DriveFileDao,
    private val moshi: Moshi
) {
    companion object {
        private const val TAG = "DriveRepository"
    }
    /** Observe files in a folder - DB is the canonical truth (offline-first) */
    fun observeFilesInFolder(accountId: String, parentId: String): Flow<List<DriveFileEntity>> =
        driveFileDao.observeFilesInFolder(accountId, parentId)

    /** Observe root files for an account */
    fun observeRootFiles(accountId: String): Flow<List<DriveFileEntity>> =
        driveFileDao.observeRootFiles(accountId)

    /** Observe all files across all accounts (unified explorer) */
    fun observeAllFiles(): Flow<List<DriveFileEntity>> =
        driveFileDao.observeAllFiles()

    /** Search files using local DB full-text index within a specific account */
    fun searchFiles(accountId: String, query: String): Flow<List<DriveFileEntity>> =
        driveFileDao.searchFiles(accountId, query)

    /** Search files across all accounts (unified explorer search) */
    fun searchAllFiles(query: String): Flow<List<DriveFileEntity>> =
        driveFileDao.searchAllFiles(query)

    /**
     * Refresh files from Drive API and update local DB.
     * This is called by WorkManager and manual pull-to-refresh.
     */
    suspend fun syncFiles(accountId: String, accessToken: String, parentId: String? = null) {
        val query = if (parentId != null) "'$parentId' in parents and trashed = false"
        else "'root' in parents and trashed = false"

        var pageToken: String? = null
        do {
            val response = apiService.listFiles(
                authorization = "Bearer $accessToken",
                query = query,
                pageToken = pageToken
            )
            val entities = response.files.map { it.toEntity(accountId) }
            driveFileDao.insertFiles(entities)
            pageToken = response.nextPageToken
        } while (pageToken != null)
    }

    /** Rename a file; queue locally if offline */
    suspend fun renameFile(
        accountId: String,
        fileId: String,
        newName: String,
        accessToken: String?
    ) {
        if (accessToken != null) {
            apiService.renameFile(
                authorization = "Bearer $accessToken",
                fileId = fileId,
                body = mapOf("name" to newName)
            )
        } else {
            driveFileDao.queueOperation(fileId, accountId, "RENAME:$newName")
        }
    }

    /** Delete a file; queue locally if offline */
    suspend fun deleteFile(
        accountId: String,
        fileId: String,
        accessToken: String?
    ) {
        if (accessToken != null) {
            apiService.deleteFile(
                authorization = "Bearer $accessToken",
                fileId = fileId
            )
            driveFileDao.deleteFile(fileId, accountId)
        } else {
            driveFileDao.queueOperation(fileId, accountId, "DELETE")
        }
    }

    /**
     * Move a file from one account to another.
     *
     * Copies the file entity under [toAccountId] (resetting the parent to root),
     * then deletes the entry under [fromAccountId].  Both steps are performed
     * against the local DB so the operation works offline.
     *
     * @return true if the file was found and moved, false if the source file was not found.
     */
    suspend fun moveFile(
        fileId: String,
        fromAccountId: String,
        toAccountId: String
    ): Boolean {
        Log.d(TAG, "Moving file $fileId from account $fromAccountId to $toAccountId")
        val source = driveFileDao.getFile(fileId, fromAccountId)
        if (source == null) {
            Log.w(TAG, "moveFile: file not found in account $fromAccountId")
            return false
        }
        val moved = source.copy(
            accountId = toAccountId,
            parentId = null,
            isSynced = false,
            pendingOperation = "MOVE_FROM:$fromAccountId"
        )
        driveFileDao.insertFile(moved)
        driveFileDao.deleteFile(fileId, fromAccountId)
        Log.d(TAG, "File moved successfully to account $toAccountId")
        return true
    }

    private fun DriveFileResponse.toEntity(accountId: String): DriveFileEntity {
        val capJson = capabilities?.let {
            moshi.adapter(com.googleac.feature.drive.data.model.DriveCapabilities::class.java).toJson(it)
        }
        // Truncate indexable text to 128KB for semantic search
        val maxSemanticIndexBytes = 128 * 1024
        val indexText = contentHints?.indexableText?.take(maxSemanticIndexBytes)
        return DriveFileEntity(
            fileId = id,
            accountId = accountId,
            name = name,
            mimeType = mimeType,
            parentId = parents?.firstOrNull(),
            size = size?.toLongOrNull(),
            createdTime = createdTime,
            modifiedTime = modifiedTime,
            webViewLink = webViewLink,
            thumbnailLink = thumbnailLink,
            driveId = driveId,
            capabilitiesJson = capJson,
            indexableText = indexText
        )
    }
}
