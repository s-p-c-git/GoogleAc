package com.googleac.feature.drive.data.repository

import com.googleac.core.data.db.dao.DriveFileDao
import com.googleac.core.data.db.entity.DriveFileEntity
import com.googleac.feature.drive.data.api.DriveApiService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [DriveRepository].
 *
 * Validates:
 * 1. Per-account file access — observeRootFiles / observeFilesInFolder / searchFiles
 *    return flows scoped to the requested account.
 * 2. Cross-account file move (ac1 → ac2):
 *    - Source file is fetched from ac1.
 *    - A copy with toAccountId=ac2 is inserted.
 *    - The original entry in ac1 is deleted.
 *    - Returns true on success, false when the source is not found.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DriveRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var driveFileDao: DriveFileDao
    private lateinit var apiService: DriveApiService
    private lateinit var repository: DriveRepository

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private fun makeFile(
        fileId: String,
        accountId: String,
        name: String,
        parentId: String? = null
    ) = DriveFileEntity(
        fileId = fileId,
        accountId = accountId,
        name = name,
        mimeType = "application/pdf",
        parentId = parentId
    )

    private val fileAc1 = makeFile("file_001", "ac1", "report_q1.pdf")
    private val fileAc2 = makeFile("file_002", "ac2", "budget_2024.xlsx")

    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        driveFileDao = mock()
        apiService = mock()
        repository = DriveRepository(apiService, driveFileDao, moshi)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Per-account file access ───────────────────────────────────────────────

    @Test
    fun `observeRootFiles for ac1 returns only ac1 files`() = runTest(testDispatcher) {
        whenever(driveFileDao.observeRootFiles("ac1")).thenReturn(flowOf(listOf(fileAc1)))

        val collected = mutableListOf<DriveFileEntity>()
        repository.observeRootFiles("ac1").collect { collected.addAll(it) }

        assertEquals(1, collected.size)
        assertEquals("ac1", collected[0].accountId)
        assertEquals("file_001", collected[0].fileId)
    }

    @Test
    fun `observeRootFiles for ac2 returns only ac2 files`() = runTest(testDispatcher) {
        whenever(driveFileDao.observeRootFiles("ac2")).thenReturn(flowOf(listOf(fileAc2)))

        val collected = mutableListOf<DriveFileEntity>()
        repository.observeRootFiles("ac2").collect { collected.addAll(it) }

        assertEquals(1, collected.size)
        assertEquals("ac2", collected[0].accountId)
    }

    @Test
    fun `observeRootFiles passes the correct accountId to the DAO`() = runTest(testDispatcher) {
        whenever(driveFileDao.observeRootFiles("ac1")).thenReturn(flowOf(emptyList()))

        repository.observeRootFiles("ac1").collect {}

        verify(driveFileDao).observeRootFiles("ac1")
    }

    @Test
    fun `observeFilesInFolder scopes query to the given account and folder`() = runTest(testDispatcher) {
        val folderFile = makeFile("child_01", "ac1", "notes.docx", parentId = "folder_123")
        whenever(driveFileDao.observeFilesInFolder("ac1", "folder_123"))
            .thenReturn(flowOf(listOf(folderFile)))

        val collected = mutableListOf<DriveFileEntity>()
        repository.observeFilesInFolder("ac1", "folder_123").collect { collected.addAll(it) }

        assertEquals(1, collected.size)
        assertEquals("folder_123", collected[0].parentId)
        verify(driveFileDao).observeFilesInFolder("ac1", "folder_123")
    }

    @Test
    fun `searchFiles passes accountId and query to the DAO`() = runTest(testDispatcher) {
        whenever(driveFileDao.searchFiles("ac1", "report")).thenReturn(flowOf(listOf(fileAc1)))

        val collected = mutableListOf<DriveFileEntity>()
        repository.searchFiles("ac1", "report").collect { collected.addAll(it) }

        assertEquals(1, collected.size)
        verify(driveFileDao).searchFiles("ac1", "report")
    }

    @Test
    fun `searchFiles for ac2 does not return ac1 files`() = runTest(testDispatcher) {
        whenever(driveFileDao.searchFiles("ac2", "report")).thenReturn(flowOf(emptyList()))

        val collected = mutableListOf<DriveFileEntity>()
        repository.searchFiles("ac2", "report").collect { collected.addAll(it) }

        assertTrue(collected.isEmpty())
    }

    @Test
    fun `observeAllFiles returns files from all accounts`() = runTest(testDispatcher) {
        whenever(driveFileDao.observeAllFiles()).thenReturn(flowOf(listOf(fileAc1, fileAc2)))

        val collected = mutableListOf<DriveFileEntity>()
        repository.observeAllFiles().collect { collected.addAll(it) }

        assertEquals(2, collected.size)
        val accountIds = collected.map { it.accountId }.toSet()
        assertTrue("ac1" in accountIds)
        assertTrue("ac2" in accountIds)
    }

    // ── moveFile: success path ────────────────────────────────────────────────

    @Test
    fun `moveFile returns true when source file exists`() = runTest(testDispatcher) {
        whenever(driveFileDao.getFile("file_001", "ac1")).thenReturn(fileAc1)

        val result = repository.moveFile("file_001", "ac1", "ac2")

        assertTrue(result)
    }

    @Test
    fun `moveFile inserts a copy of the file under toAccountId`() = runTest(testDispatcher) {
        whenever(driveFileDao.getFile("file_001", "ac1")).thenReturn(fileAc1)

        repository.moveFile("file_001", "ac1", "ac2")

        val captor = argumentCaptor<DriveFileEntity>()
        verify(driveFileDao).insertFile(captor.capture())
        assertEquals("ac2", captor.firstValue.accountId)
        assertEquals("file_001", captor.firstValue.fileId)
        assertEquals("report_q1.pdf", captor.firstValue.name)
    }

    @Test
    fun `moveFile clears parentId on the inserted copy`() = runTest(testDispatcher) {
        val fileWithParent = fileAc1.copy(parentId = "some_folder")
        whenever(driveFileDao.getFile("file_001", "ac1")).thenReturn(fileWithParent)

        repository.moveFile("file_001", "ac1", "ac2")

        val captor = argumentCaptor<DriveFileEntity>()
        verify(driveFileDao).insertFile(captor.capture())
        assertEquals(null, captor.firstValue.parentId)
    }

    @Test
    fun `moveFile marks the inserted copy as unsynced with MOVE_FROM operation`() = runTest(testDispatcher) {
        whenever(driveFileDao.getFile("file_001", "ac1")).thenReturn(fileAc1)

        repository.moveFile("file_001", "ac1", "ac2")

        val captor = argumentCaptor<DriveFileEntity>()
        verify(driveFileDao).insertFile(captor.capture())
        assertFalse(captor.firstValue.isSynced)
        assertEquals("MOVE_FROM:ac1", captor.firstValue.pendingOperation)
    }

    @Test
    fun `moveFile deletes the source entry from fromAccountId`() = runTest(testDispatcher) {
        whenever(driveFileDao.getFile("file_001", "ac1")).thenReturn(fileAc1)

        repository.moveFile("file_001", "ac1", "ac2")

        verify(driveFileDao).deleteFile("file_001", "ac1")
    }

    @Test
    fun `moveFile inserts before deleting to avoid data loss`() = runTest(testDispatcher) {
        val order = mutableListOf<String>()
        whenever(driveFileDao.getFile("file_001", "ac1")).thenReturn(fileAc1)
        whenever(driveFileDao.insertFile(any())).thenAnswer { order.add("insert"); Unit }
        whenever(driveFileDao.deleteFile(any(), any())).thenAnswer { order.add("delete"); Unit }

        repository.moveFile("file_001", "ac1", "ac2")

        assertEquals(listOf("insert", "delete"), order)
    }

    // ── moveFile: file not found ──────────────────────────────────────────────

    @Test
    fun `moveFile returns false when source file is not found`() = runTest(testDispatcher) {
        whenever(driveFileDao.getFile("missing_file", "ac1")).thenReturn(null)

        val result = repository.moveFile("missing_file", "ac1", "ac2")

        assertFalse(result)
    }

    @Test
    fun `moveFile does not insert or delete when source file is missing`() = runTest(testDispatcher) {
        whenever(driveFileDao.getFile("missing_file", "ac1")).thenReturn(null)

        repository.moveFile("missing_file", "ac1", "ac2")

        verify(driveFileDao, never()).insertFile(any())
        verify(driveFileDao, never()).deleteFile(any(), any())
    }
}
