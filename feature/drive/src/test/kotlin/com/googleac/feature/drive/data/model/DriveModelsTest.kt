package com.googleac.feature.drive.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveModelsTest {

    // ── DriveCapabilities ─────────────────────────────────────────────────────

    @Test
    fun `DriveCapabilities defaults all capabilities to false`() {
        val caps = DriveCapabilities()
        assertFalse(caps.canEdit)
        assertFalse(caps.canDelete)
        assertFalse(caps.canRename)
        assertFalse(caps.canMoveItemWithinDrive)
        assertFalse(caps.canMoveItemOutOfDrive)
        assertFalse(caps.canShare)
        assertFalse(caps.canDownload)
        assertFalse(caps.canAddChildren)
        assertFalse(caps.canComment)
        assertFalse(caps.canReadRevisions)
        assertFalse(caps.canTrash)
    }

    @Test
    fun `DriveCapabilities organizer role has full write permissions`() {
        val caps = DriveCapabilities(
            canEdit = true,
            canDelete = true,
            canRename = true,
            canMoveItemWithinDrive = true,
            canMoveItemOutOfDrive = true,
            canShare = true,
            canDownload = true,
            canAddChildren = true
        )
        assertTrue(caps.canEdit)
        assertTrue(caps.canDelete)
        assertTrue(caps.canRename)
        assertTrue(caps.canMoveItemWithinDrive)
        assertTrue(caps.canMoveItemOutOfDrive)
        assertTrue(caps.canShare)
        assertTrue(caps.canDownload)
        assertTrue(caps.canAddChildren)
    }

    @Test
    fun `DriveCapabilities viewer role has only download permission`() {
        val caps = DriveCapabilities(canDownload = true)
        assertTrue(caps.canDownload)
        assertFalse(caps.canEdit)
        assertFalse(caps.canDelete)
        assertFalse(caps.canRename)
        assertFalse(caps.canMoveItemWithinDrive)
    }

    @Test
    fun `DriveCapabilities commenter role has comment permission`() {
        val caps = DriveCapabilities(canComment = true, canDownload = true)
        assertTrue(caps.canComment)
        assertTrue(caps.canDownload)
        assertFalse(caps.canEdit)
        assertFalse(caps.canDelete)
    }

    @Test
    fun `DriveCapabilities contributor role can edit but not delete`() {
        val caps = DriveCapabilities(
            canEdit = true,
            canDownload = true,
            canComment = true,
            canReadRevisions = true
        )
        assertTrue(caps.canEdit)
        assertFalse(caps.canDelete)
        assertFalse(caps.canShare)
    }

    // ── DriveFileResponse ─────────────────────────────────────────────────────

    @Test
    fun `DriveFileResponse stores required fields correctly`() {
        val file = DriveFileResponse(
            id = "file_abc123",
            name = "annual_report.pdf",
            mimeType = "application/pdf"
        )
        assertEquals("file_abc123", file.id)
        assertEquals("annual_report.pdf", file.name)
        assertEquals("application/pdf", file.mimeType)
    }

    @Test
    fun `DriveFileResponse optional fields default to null`() {
        val file = DriveFileResponse(id = "id", name = "name", mimeType = "text/plain")
        assertNull(file.parents)
        assertNull(file.size)
        assertNull(file.createdTime)
        assertNull(file.modifiedTime)
        assertNull(file.webViewLink)
        assertNull(file.thumbnailLink)
        assertNull(file.driveId)
        assertNull(file.capabilities)
        assertNull(file.contentHints)
    }

    @Test
    fun `DriveFileResponse with Shared Drive fields`() {
        val file = DriveFileResponse(
            id = "shared_file_001",
            name = "project_plan.docx",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            driveId = "shared_drive_xyz"
        )
        assertEquals("shared_drive_xyz", file.driveId)
    }

    @Test
    fun `DriveFileResponse with capabilities and parent hierarchy`() {
        val caps = DriveCapabilities(canEdit = true, canDownload = true)
        val file = DriveFileResponse(
            id = "child_file",
            name = "child.txt",
            mimeType = "text/plain",
            parents = listOf("parent_folder_id"),
            capabilities = caps
        )
        assertEquals(listOf("parent_folder_id"), file.parents)
        assertTrue(file.capabilities?.canEdit == true)
    }

    @Test
    fun `DriveFileResponse size is stored as string for large file compatibility`() {
        val file = DriveFileResponse(
            id = "big_file",
            name = "backup.zip",
            mimeType = "application/zip",
            size = "10737418240" // 10 GB — exceeds Int range
        )
        assertEquals("10737418240", file.size)
        // Ensure it can be parsed to Long
        assertEquals(10_737_418_240L, file.size?.toLongOrNull())
    }

    // ── DriveFileListResponse ─────────────────────────────────────────────────

    @Test
    fun `DriveFileListResponse defaults to empty file list and null pagination token`() {
        val response = DriveFileListResponse()
        assertTrue(response.files.isEmpty())
        assertNull(response.nextPageToken)
    }

    @Test
    fun `DriveFileListResponse holds multiple files`() {
        val files = listOf(
            DriveFileResponse(id = "1", name = "a.pdf", mimeType = "application/pdf"),
            DriveFileResponse(id = "2", name = "b.docx", mimeType = "application/msword"),
            DriveFileResponse(id = "3", name = "c.xlsx", mimeType = "application/vnd.ms-excel")
        )
        val response = DriveFileListResponse(files = files)
        assertEquals(3, response.files.size)
        assertEquals("a.pdf", response.files[0].name)
    }

    @Test
    fun `DriveFileListResponse nextPageToken signals more pages available`() {
        val response = DriveFileListResponse(nextPageToken = "page_cursor_xyz")
        assertEquals("page_cursor_xyz", response.nextPageToken)
    }

    @Test
    fun `DriveFileListResponse null nextPageToken signals last page`() {
        val response = DriveFileListResponse(nextPageToken = null, files = listOf(
            DriveFileResponse(id = "last", name = "last.pdf", mimeType = "application/pdf")
        ))
        assertNull("Null token indicates this is the final page", response.nextPageToken)
    }

    // ── ContentHints ─────────────────────────────────────────────────────────

    @Test
    fun `ContentHints stores indexable text for semantic search`() {
        val hints = ContentHints(indexableText = "This document discusses Q4 financial results and revenue.")
        assertEquals(
            "This document discusses Q4 financial results and revenue.",
            hints.indexableText
        )
        assertNull(hints.thumbnail)
    }

    @Test
    fun `ContentHints without indexable text defaults to null`() {
        val hints = ContentHints()
        assertNull(hints.indexableText)
    }

    @Test
    fun `ContentThumbnail stores image and mimeType`() {
        val thumb = ContentThumbnail(image = "base64imagedata", mimeType = "image/png")
        assertEquals("base64imagedata", thumb.image)
        assertEquals("image/png", thumb.mimeType)
    }
}
