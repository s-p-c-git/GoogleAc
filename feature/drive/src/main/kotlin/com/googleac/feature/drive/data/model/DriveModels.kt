package com.googleac.feature.drive.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DriveFileListResponse(
    @Json(name = "nextPageToken") val nextPageToken: String? = null,
    @Json(name = "files") val files: List<DriveFileResponse> = emptyList()
)

@JsonClass(generateAdapter = true)
data class DriveFileResponse(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "parents") val parents: List<String>? = null,
    @Json(name = "size") val size: String? = null,
    @Json(name = "createdTime") val createdTime: String? = null,
    @Json(name = "modifiedTime") val modifiedTime: String? = null,
    @Json(name = "webViewLink") val webViewLink: String? = null,
    @Json(name = "thumbnailLink") val thumbnailLink: String? = null,
    @Json(name = "driveId") val driveId: String? = null,
    @Json(name = "capabilities") val capabilities: DriveCapabilities? = null,
    @Json(name = "contentHints") val contentHints: ContentHints? = null
)

/**
 * Drive API v3 capabilities object - used for role-based UI rendering.
 * Actions like "Move" or "Delete" are hidden/disabled based on these flags.
 */
@JsonClass(generateAdapter = true)
data class DriveCapabilities(
    @Json(name = "canEdit") val canEdit: Boolean = false,
    @Json(name = "canDelete") val canDelete: Boolean = false,
    @Json(name = "canRename") val canRename: Boolean = false,
    @Json(name = "canMoveItemWithinDrive") val canMoveItemWithinDrive: Boolean = false,
    @Json(name = "canMoveItemOutOfDrive") val canMoveItemOutOfDrive: Boolean = false,
    @Json(name = "canShare") val canShare: Boolean = false,
    @Json(name = "canDownload") val canDownload: Boolean = false,
    @Json(name = "canAddChildren") val canAddChildren: Boolean = false,
    @Json(name = "canComment") val canComment: Boolean = false,
    @Json(name = "canReadRevisions") val canReadRevisions: Boolean = false,
    @Json(name = "canTrash") val canTrash: Boolean = false
)

/**
 * Drive API v3 contentHints - provides indexable text for semantic search.
 */
@JsonClass(generateAdapter = true)
data class ContentHints(
    @Json(name = "indexableText") val indexableText: String? = null,
    @Json(name = "thumbnail") val thumbnail: ContentThumbnail? = null
)

@JsonClass(generateAdapter = true)
data class ContentThumbnail(
    @Json(name = "image") val image: String? = null,
    @Json(name = "mimeType") val mimeType: String? = null
)
