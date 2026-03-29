package com.googleac.feature.auth.data

import android.net.Uri
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * OAuth 2.0 with PKCE (Proof Key for Code Exchange) implementation.
 * Mitigates authorization code interception attacks as required by CASA Tier 3.
 */
object OAuthPkceHelper {
    private const val GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val REDIRECT_URI = "com.googleac.app:/oauth2redirect"

    /**
     * Generates a cryptographically random code verifier (43-128 chars).
     */
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Derives S256 code challenge from the code verifier.
     */
    fun generateCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Builds the OAuth 2.0 authorization URL with PKCE parameters.
     *
     * Uses least-privilege scopes (drive.file) by default per security requirements.
     */
    fun buildAuthorizationUrl(
        clientId: String,
        codeChallenge: String,
        scopes: List<String> = listOf(
            "https://www.googleapis.com/auth/drive.file",
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/userinfo.profile"
        ),
        loginHint: String? = null
    ): String {
        val scopeStr = scopes.joinToString(" ")
        return Uri.parse(GOOGLE_AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", scopeStr)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .apply { loginHint?.let { appendQueryParameter("login_hint", it) } }
            .build()
            .toString()
    }

    /**
     * Extracts the authorization code from the OAuth redirect URI.
     */
    fun extractAuthCode(redirectUri: String): String? {
        return Uri.parse(redirectUri).getQueryParameter("code")
    }
}
