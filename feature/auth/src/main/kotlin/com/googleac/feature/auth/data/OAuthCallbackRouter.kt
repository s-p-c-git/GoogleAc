package com.googleac.feature.auth.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-level singleton that routes the OAuth redirect URI from the host
 * Activity ([MainActivity.onNewIntent]) to [AuthViewModel].
 *
 * Using a [MutableSharedFlow] with [extraBufferCapacity] = 1 ensures the URI
 * is not dropped if the ViewModel has not yet subscribed (e.g., during the
 * brief window between Activity resume and the first recomposition).
 */
@Singleton
class OAuthCallbackRouter @Inject constructor() {

    private val _redirectUriFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Emits the full redirect URI (scheme + path + query) received from Chrome Custom Tabs. */
    val redirectUriFlow: Flow<String> = _redirectUriFlow.asSharedFlow()

    /**
     * Called by the host Activity when the OAuth redirect intent arrives.
     * Safe to call from any thread.
     */
    fun onRedirectReceived(uri: String) {
        _redirectUriFlow.tryEmit(uri)
    }
}
