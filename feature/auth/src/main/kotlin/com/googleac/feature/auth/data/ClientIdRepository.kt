package com.googleac.feature.auth.data

/**
 * Provides the Google OAuth 2.0 client ID that the app uses to initiate the
 * PKCE sign-in flow.
 *
 * Two sources are consulted in priority order:
 * 1. A value entered by the user at runtime (persisted across app restarts).
 * 2. The build-time value baked into [BuildConfig.OAUTH_CLIENT_ID] from
 *    `local.properties`.
 *
 * This split allows pre-built APKs (e.g., from CI) to be useful even when no
 * build-time client ID is available: the user is prompted once to enter their
 * own client ID, which is then stored and reused.
 */
interface ClientIdRepository {
    /** Returns the effective OAuth client ID, or an empty string if none is configured. */
    fun get(): String

    /** Persists [clientId] so it survives app restarts. */
    fun save(clientId: String)

    /** True when a non-blank client ID is available from either source. */
    fun isConfigured(): Boolean = get().isNotBlank()
}
