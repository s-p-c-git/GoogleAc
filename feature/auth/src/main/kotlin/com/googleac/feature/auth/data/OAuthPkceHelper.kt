package com.googleac.feature.auth.data

import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * OAuth 2.0 with PKCE (Proof Key for Code Exchange) implementation.
 * Mitigates authorization code interception attacks as required by CASA Tier 3.
 *
 * Uses only standard JVM APIs (java.util.Base64, java.net.URLEncoder) so that
 * all PKCE logic can be unit-tested on the JVM without Android SDK dependencies.
 * minSdk 26 (Android 8) guarantees java.util.Base64 availability.
 */
object OAuthPkceHelper {
    private const val GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
    const val REDIRECT_URI = "com.googleac.app:/oauth2redirect"

    /**
     * Generates a cryptographically random code verifier (RFC 7636 §4.1).
     * Output is Base64url-encoded without padding, yielding 86 characters from 64 random bytes.
     */
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Derives the S256 code challenge from [codeVerifier] (RFC 7636 §4.2).
     * challenge = BASE64URL(SHA-256(ASCII(verifier)))
     */
    fun generateCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
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
        val params = buildMap<String, String> {
            put("client_id", clientId)
            put("redirect_uri", REDIRECT_URI)
            put("response_type", "code")
            put("scope", scopeStr)
            put("code_challenge", codeChallenge)
            put("code_challenge_method", "S256")
            put("access_type", "offline")
            put("prompt", "consent")
            if (loginHint != null) put("login_hint", loginHint)
        }
        val query = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        return "$GOOGLE_AUTH_URL?$query"
    }

    /**
     * Extracts the authorization code from the OAuth redirect URI.
     * Returns null if no `code` parameter is present (e.g., on `error=access_denied`).
     */
    fun extractAuthCode(redirectUri: String): String? {
        val queryStart = redirectUri.indexOf('?')
        if (queryStart == -1) return null
        val queryString = redirectUri.substring(queryStart + 1)
        return queryString.split("&")
            .firstOrNull { it.startsWith("code=") }
            ?.removePrefix("code=")
            ?.let { URLDecoder.decode(it, "UTF-8") }
    }
}
