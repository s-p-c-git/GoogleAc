package com.googleac.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.googleac.feature.auth.data.OAuthCallbackRouter
import com.googleac.feature.auth.data.OAuthPkceHelper
import com.googleac.feature.auth.data.OAuthTokenExchanger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()

    /**
     * The PKCE authorization URL has been built and should be opened in a
     * Chrome Custom Tab.  Transitions to [Loading] once the tab is launched,
     * then to [Success] or [Error] after the redirect is processed.
     */
    data class AwaitingRedirect(val authUrl: String) : AuthUiState()

    data class Success(val email: String) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

/**
 * ViewModel that coordinates the full OAuth 2.0 + PKCE sign-in flow.
 *
 * Flow:
 * 1. [startAuth] → generates code verifier + challenge → emits [AuthUiState.AwaitingRedirect]
 * 2. [AuthScreen] launches a Chrome Custom Tab with [AwaitingRedirect.authUrl]
 * 3. User consents → Google redirects to the app's custom scheme URI
 * 4. [MainActivity] receives the redirect via [onNewIntent] and calls
 *    [OAuthCallbackRouter.onRedirectReceived]
 * 5. This ViewModel collects the redirect URI, extracts the auth code, exchanges
 *    it for tokens, and emits [AuthUiState.Success] with the user's email.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    @Named("oauthClientId") private val clientId: String,
    private val tokenExchanger: OAuthTokenExchanger,
    private val callbackRouter: OAuthCallbackRouter
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    /** Stores the PKCE code verifier across the Custom Tab round-trip. */
    private var pendingCodeVerifier: String? = null

    init {
        // Subscribe to OAuth redirects forwarded by MainActivity.onNewIntent.
        viewModelScope.launch {
            callbackRouter.redirectUriFlow.collect { uri ->
                handleAuthRedirect(uri)
            }
        }
    }

    /**
     * Kicks off the PKCE sign-in flow.
     *
     * Generates a fresh code verifier / challenge pair and transitions to
     * [AuthUiState.AwaitingRedirect] so the UI can open a Chrome Custom Tab.
     */
    fun startAuth() {
        val codeVerifier = OAuthPkceHelper.generateCodeVerifier()
        val codeChallenge = OAuthPkceHelper.generateCodeChallenge(codeVerifier)
        pendingCodeVerifier = codeVerifier

        val authUrl = OAuthPkceHelper.buildAuthorizationUrl(
            clientId = clientId,
            codeChallenge = codeChallenge
        )
        _uiState.value = AuthUiState.AwaitingRedirect(authUrl)
    }

    /**
     * Processes the OAuth redirect URI forwarded from [MainActivity.onNewIntent].
     *
     * Extracts the authorization code, exchanges it for tokens, and updates
     * [uiState] to [AuthUiState.Success] or [AuthUiState.Error].
     */
    fun handleAuthRedirect(uri: String) {
        val code = OAuthPkceHelper.extractAuthCode(uri)
        if (code == null) {
            _uiState.value = AuthUiState.Error("Sign-in was cancelled or no code returned")
            return
        }
        val verifier = pendingCodeVerifier
        if (verifier == null) {
            _uiState.value = AuthUiState.Error("Auth flow not started — call startAuth() first")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val result = tokenExchanger.exchangeCode(
                    clientId = clientId,
                    code = code,
                    codeVerifier = verifier,
                    redirectUri = OAuthPkceHelper.REDIRECT_URI
                )
                pendingCodeVerifier = null
                _uiState.value = AuthUiState.Success(result.email)
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "Token exchange failed")
            }
        }
    }

    // ── Convenience methods kept for backward-compatibility with callers that
    // drive state directly (e.g., tests, instrumented flows). ────────────────

    fun handleAuthResult(email: String) {
        _uiState.value = AuthUiState.Success(email)
    }

    fun handleAuthError(message: String) {
        _uiState.value = AuthUiState.Error(message)
    }
}

