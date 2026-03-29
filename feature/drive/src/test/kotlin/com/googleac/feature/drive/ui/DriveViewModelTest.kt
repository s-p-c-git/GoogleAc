package com.googleac.feature.drive.ui

import com.googleac.core.data.db.entity.DriveFileEntity
import com.googleac.feature.drive.data.repository.DriveRepository
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class DriveViewModelTest {

    /**
     * A single [StandardTestDispatcher] is used for both Main and runTest so that
     * the ViewModel's viewModelScope coroutines and the test coroutines share the
     * same virtual-time scheduler.  That lets [advanceUntilIdle] drain everything
     * in one call.  [backgroundScope].launch is used to subscribe to uiState so
     * the WhileSubscribed sharing starts without preventing runTest from completing.
     */
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: DriveRepository
    private lateinit var viewModel: DriveViewModel

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeFile(
        fileId: String,
        accountId: String,
        name: String,
        mimeType: String = "application/pdf"
    ) = DriveFileEntity(fileId = fileId, accountId = accountId, name = name, mimeType = mimeType)

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mock()
        whenever(repository.observeAllFiles()).thenReturn(flowOf(emptyList()))
        whenever(repository.searchAllFiles(any())).thenReturn(flowOf(emptyList()))
        viewModel = DriveViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty file list`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.files.isEmpty())
    }

    @Test
    fun `initial state has search disabled`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSearching)
    }

    @Test
    fun `initial state has empty search query`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.searchQuery.isEmpty())
    }

    // ── toggleSearch ──────────────────────────────────────────────────────────

    @Test
    fun `toggleSearch enables search when it is off`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSearching)

        viewModel.toggleSearch()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSearching)
    }

    @Test
    fun `toggleSearch disables search when it is on`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.toggleSearch()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSearching)

        viewModel.toggleSearch()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSearching)
    }

    @Test
    fun `toggleSearch three times leaves search enabled`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.toggleSearch()
        viewModel.toggleSearch()
        viewModel.toggleSearch()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSearching)
    }

    // ── search / query routing ────────────────────────────────────────────────

    @Test
    fun `search updates searchQuery in state`() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { } }
        viewModel.search("quarterly report")
        advanceUntilIdle()
        assertEquals("quarterly report", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun `blank query routes to observeAllFiles`() = runTest(testDispatcher) {
        val allFiles = listOf(
            makeFile("f1", "acc1", "report.pdf"),
            makeFile("f2", "acc2", "invoice.pdf")
        )
        whenever(repository.observeAllFiles()).thenReturn(flowOf(allFiles))
        viewModel = DriveViewModel(repository)

        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.files.size)
        verify(repository).observeAllFiles()
    }

    @Test
    fun `non-blank query routes to searchAllFiles`() = runTest(testDispatcher) {
        val searchResults = listOf(makeFile("f3", "acc1", "quarterly_budget.pdf"))
        whenever(repository.searchAllFiles("budget")).thenReturn(flowOf(searchResults))

        backgroundScope.launch { viewModel.uiState.collect { } }
        viewModel.search("budget")
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.files.size)
        assertEquals("quarterly_budget.pdf", viewModel.uiState.value.files[0].name)
        verify(repository).searchAllFiles("budget")
    }

    @Test
    fun `clearing query after a search returns to observeAllFiles`() = runTest(testDispatcher) {
        val allFiles = listOf(makeFile("f1", "acc1", "all.pdf"))
        whenever(repository.observeAllFiles()).thenReturn(flowOf(allFiles))
        whenever(repository.searchAllFiles("budget")).thenReturn(flowOf(emptyList()))
        viewModel = DriveViewModel(repository)

        backgroundScope.launch { viewModel.uiState.collect { } }
        viewModel.search("budget")
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.files.size)

        viewModel.search("")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.files.size)
    }

    @Test
    fun `uiState reflects files emitted by the repository flow`() = runTest(testDispatcher) {
        val files = listOf(
            makeFile("id1", "account_personal", "notes.pdf"),
            makeFile("id2", "account_corporate", "contract.docx", mimeType = "application/msword")
        )
        whenever(repository.observeAllFiles()).thenReturn(flowOf(files))
        viewModel = DriveViewModel(repository)

        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        val result = viewModel.uiState.value.files
        assertEquals(2, result.size)
        assertEquals("notes.pdf", result[0].name)
        assertEquals("contract.docx", result[1].name)
    }
}
