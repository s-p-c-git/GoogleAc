package com.googleac.feature.auth.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AuthViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() {
        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `handleAuthResult transitions state to Success with the given email`() {
        viewModel.handleAuthResult("user@example.com")
        assertEquals(AuthUiState.Success("user@example.com"), viewModel.uiState.value)
    }

    @Test
    fun `handleAuthResult preserves the exact email value`() {
        val email = "corporate.user+alias@company.org"
        viewModel.handleAuthResult(email)
        val state = viewModel.uiState.value
        assertTrue(state is AuthUiState.Success)
        assertEquals(email, (state as AuthUiState.Success).email)
    }

    @Test
    fun `handleAuthError transitions state to Error with the given message`() {
        viewModel.handleAuthError("Token exchange failed")
        assertEquals(AuthUiState.Error("Token exchange failed"), viewModel.uiState.value)
    }

    @Test
    fun `handleAuthError preserves the exact error message`() {
        val message = "network_error: connection timed out after 30s"
        viewModel.handleAuthError(message)
        val state = viewModel.uiState.value
        assertTrue(state is AuthUiState.Error)
        assertEquals(message, (state as AuthUiState.Error).message)
    }

    @Test
    fun `startAuth returns to Idle after completing`() = runTest {
        viewModel.startAuth()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `handleAuthResult replaces a prior error state`() {
        viewModel.handleAuthError("first error")
        viewModel.handleAuthResult("recovered@example.com")
        assertTrue(viewModel.uiState.value is AuthUiState.Success)
    }

    @Test
    fun `handleAuthError replaces a prior success state`() {
        viewModel.handleAuthResult("user@example.com")
        viewModel.handleAuthError("session expired")
        assertTrue(viewModel.uiState.value is AuthUiState.Error)
    }

    @Test
    fun `multiple handleAuthResult calls keep the most recent email`() {
        viewModel.handleAuthResult("first@example.com")
        viewModel.handleAuthResult("second@example.com")
        assertEquals(AuthUiState.Success("second@example.com"), viewModel.uiState.value)
    }
}
