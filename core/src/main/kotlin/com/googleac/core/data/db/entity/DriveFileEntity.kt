package com.googleac.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Drive file metadata partitioned by accountId.
 * Includes Drive API v3 fields: capabilities and contentHints.
 * Up to 128KB of text is indexed for semantic search.
 */
@Entity(
    tableName = "drive_files",
    primaryKeys = ["file_id", "account_id"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["account_id"]), Index(value = ["parent_id"])]
)
data class DriveFileEntity(
    @ColumnInfo(name = "file_id")
    val fileId: String,

    /** Partition key — isolates files per account */
    @ColumnInfo(name = "account_id")
    val accountId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "mime_type")
    val mimeType: String,

    @ColumnInfo(name = "parent_id")
    val parentId: String? = null,

    @ColumnInfo(name = "size")
    val size: Long? = null,

    @ColumnInfo(name = "created_time")
    val createdTime: String? = null,

    @ColumnInfo(name = "modified_time")
    val modifiedTime: String? = null,

    @ColumnInfo(name = "web_view_link")
    val webViewLink: String? = null,

    @ColumnInfo(name = "thumbnail_link")
    val thumbnailLink: String? = null,

    /** Shared Drive ID if the file belongs to a Shared Drive */
    @ColumnInfo(name = "drive_id")
    val driveId: String? = null,

    /** JSON blob of Drive API v3 capabilities object */
    @ColumnInfo(name = "capabilities_json")
    val capabilitiesJson: String? = null,

    /** Content type description hint from Drive API v3 contentHints.indexableText */
    @ColumnInfo(name = "indexable_text")
    val indexableText: String? = null,

    /** Up to 128KB of extracted text for on-device semantic search */
    @ColumnInfo(name = "semantic_index_text")
    val semanticIndexText: String? = null,

    /** User's role in Shared Drive: ORGANIZER, FILE_ORGANIZER, CONTRIBUTOR, COMMENTER, VIEWER */
    @ColumnInfo(name = "user_role")
    val userRole: String? = null,

    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = true,

    @ColumnInfo(name = "pending_operation")
    val pendingOperation: String? = null,

    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long = System.currentTimeMillis()
)
