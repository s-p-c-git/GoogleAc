package com.googleac.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.googleac.core.data.db.entity.DriveFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DriveFileDao {
    /** Observe files in a specific folder for one account (offline-first) */
    @Query("SELECT * FROM drive_files WHERE account_id = :accountId AND parent_id = :parentId ORDER BY name ASC")
    fun observeFilesInFolder(accountId: String, parentId: String): Flow<List<DriveFileEntity>>

    /** Observe root-level files for one account */
    @Query("SELECT * FROM drive_files WHERE account_id = :accountId AND parent_id IS NULL ORDER BY name ASC")
    fun observeRootFiles(accountId: String): Flow<List<DriveFileEntity>>

    /** Observe all files across all accounts for unified explorer */
    @Query("SELECT * FROM drive_files ORDER BY account_id, name ASC")
    fun observeAllFiles(): Flow<List<DriveFileEntity>>

    /** Full-text semantic search within an account's files */
    @Query("""
        SELECT * FROM drive_files 
        WHERE account_id = :accountId 
        AND (name LIKE '%' || :query || '%' OR semantic_index_text LIKE '%' || :query || '%' OR indexable_text LIKE '%' || :query || '%')
        ORDER BY name ASC
    """)
    fun searchFiles(accountId: String, query: String): Flow<List<DriveFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<DriveFileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: DriveFileEntity)

    @Update
    suspend fun updateFile(file: DriveFileEntity)

    @Query("DELETE FROM drive_files WHERE account_id = :accountId")
    suspend fun deleteAllFilesForAccount(accountId: String)

    @Query("DELETE FROM drive_files WHERE file_id = :fileId AND account_id = :accountId")
    suspend fun deleteFile(fileId: String, accountId: String)

    /** Queue a write operation for offline sync */
    @Query("UPDATE drive_files SET pending_operation = :operation, is_synced = 0 WHERE file_id = :fileId AND account_id = :accountId")
    suspend fun queueOperation(fileId: String, accountId: String, operation: String)

    /** Get all unsynced files for WorkManager sync */
    @Query("SELECT * FROM drive_files WHERE is_synced = 0 AND account_id = :accountId")
    suspend fun getUnsyncedFiles(accountId: String): List<DriveFileEntity>
}
