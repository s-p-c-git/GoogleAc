package com.googleac.feature.drive.data.api

import com.googleac.feature.drive.data.model.DriveFileListResponse
import com.googleac.feature.drive.data.model.DriveFileResponse
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * Retrofit service for the Google Drive API v3.
 * Uses per-request Authorization headers to support multiple accounts simultaneously.
 */
interface DriveApiService {
    companion object {
        const val BASE_URL = "https://www.googleapis.com/drive/v3/"
        // Default fields including Drive API v3 capabilities and contentHints
        const val DEFAULT_FILE_FIELDS = "id,name,mimeType,parents,size,createdTime,modifiedTime," +
                "webViewLink,thumbnailLink,driveId,capabilities,contentHints,owners,permissions"
    }

    @GET("files")
    suspend fun listFiles(
        @Header("Authorization") authorization: String,
        @Query("q") query: String? = null,
        @Query("fields") fields: String = "nextPageToken,files($DEFAULT_FILE_FIELDS)",
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String? = null,
        @Query("includeItemsFromAllDrives") includeAllDrives: Boolean = true,
        @Query("supportsAllDrives") supportsAllDrives: Boolean = true,
        @Query("driveId") driveId: String? = null,
        @Query("corpora") corpora: String? = null,
        @QueryMap extraParams: Map<String, String> = emptyMap()
    ): DriveFileListResponse

    @GET("files/{fileId}")
    suspend fun getFile(
        @Header("Authorization") authorization: String,
        @Path("fileId") fileId: String,
        @Query("fields") fields: String = DEFAULT_FILE_FIELDS,
        @Query("supportsAllDrives") supportsAllDrives: Boolean = true
    ): DriveFileResponse

    @PATCH("files/{fileId}")
    suspend fun renameFile(
        @Header("Authorization") authorization: String,
        @Path("fileId") fileId: String,
        @retrofit2.http.Body body: Map<String, String>,
        @Query("supportsAllDrives") supportsAllDrives: Boolean = true
    ): DriveFileResponse

    @DELETE("files/{fileId}")
    suspend fun deleteFile(
        @Header("Authorization") authorization: String,
        @Path("fileId") fileId: String,
        @Query("supportsAllDrives") supportsAllDrives: Boolean = true
    )
}
