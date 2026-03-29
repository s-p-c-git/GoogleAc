package com.googleac.feature.auth.ui

import com.googleac.feature.auth.data.OAuthCallbackRouter
import com.googleac.feature.auth.data.OAuthPkceHelper
import com.googleac.feature.auth.data.OAuthTokenExchanger
import com.googleac.feature.auth.data.TokenExchangeResult
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
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: AuthViewModel
    private lateinit var tokenExchanger: OAuthTokenExchanger
    private lateinit var callbackRouter: OAuthCallbackRouter

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tokenExchanger = mock()
        callbackRouter = OAuthCallbackRouter()
        viewModel = AuthViewModel(
            clientId = "test-client-id",
            tokenExchanger = tokenExchanger,
            callbackRouter = callbackRouter
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state is Idle`() {
        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    // ── startAuth ─────────────────────────────────────────────────────────────

    @Test
    fun `startAuth transitions to AwaitingRedirect`() {
        viewModel.startAuth()
        assertTrue(viewModel.uiState.value is AuthUiState.AwaitingRedirect)
    }

    @Test
    fun `startAuth AwaitingRedirect URL starts with Google OAuth endpoint`() {
        viewModel.startAuth()
        val state = viewModel.uiState.value as AuthUiState.AwaitingRedirect
        assertTrue(state.authUrl.startsWith("https://accounts.google.com/o/oauth2/v2/auth"))
    }

    @Test
    fun `startAuth AwaitingRedirect URL contains the injected client_id`() {
        viewModel.startAuth()
        val state = viewModel.uiState.value as AuthUiState.AwaitingRedirect
        assertTrue(state.authUrl.contains("test-client-id"))
    }

    @Test
    fun `startAuth AwaitingRedirect URL contains PKCE parameters`() {
        viewModel.startAuth()
        val state = viewModel.uiState.value as AuthUiState.AwaitingRedirect
        assertTrue(state.authUrl.contains("code_challenge="))
        assertTrue(state.authUrl.contains("code_challenge_method=S256"))
    }

    // ── handleAuthRedirect — success ──────────────────────────────────────────

    @Test
    fun `handleAuthRedirect exchanges code and emits Success with email`() = runTest(testDispatcher) {
        whenever(tokenExchanger.exchangeCode(any(), any(), any(), any()))
            .thenReturn(TokenExchangeResult("access", "refresh", "user@gmail.com", "id"))

        viewModel.startAuth()
        viewModel.handleAuthRedirect("${OAuthPkceHelper.REDIRECT_URI}?code=test_code")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AuthUiState.Success("user@gmail.com"), viewModel.uiState.value)
    }

    @Test
    fun `handleAuthRedirect preserves exact email from token exchange`() = runTest(testDispatcher) {
        val email = "work+alias@company.org"
        whenever(tokenExchanger.exchangeCode(any(), any(), any(), any()))
            .thenReturn(TokenExchangeResult("access", null, email, "id"))

        viewModel.startAuth()
        viewModel.handleAuthRedirect("${OAuthPkceHelper.REDIRECT_URI}?code=abc")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as AuthUiState.Success
        assertEquals(email, state.email)
    }

    // ── handleAuthRedirect — error paths ─────────────────────────────────────

    @Test
    fun `handleAuthRedirect with no code emits Error`() {
        viewModel.startAuth()
        viewModel.handleAuthRedirect("${OAuthPkceHelper.REDIRECT_URI}?error=access_denied")
        assertTrue(viewModel.uiState.value is AuthUiState.Error)
    }

    @Test
    fun `handleAuthRedirect without prior startAuth emits Error`() {
        viewModel.handleAuthRedirect("${OAuthPkceHelper.REDIRECT_URI}?code=abc")
        assertTrue(viewModel.uiState.value is AuthUiState.Error)
    }

    @Test
    fun `handleAuthRedirect emits Error when token exchange throws`() = runTest(testDispatcher) {
        whenever(tokenExchanger.exchangeCode(any(), any(), any(), any()))
            .thenThrow(RuntimeException("network failure"))

        viewModel.startAuth()
        viewModel.handleAuthRedirect("${OAuthPkceHelper.REDIRECT_URI}?code=abc")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("network failure", (state as AuthUiState.Error).message)
    }

    // ── Convenience methods (backward-compatible) ─────────────────────────────

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
