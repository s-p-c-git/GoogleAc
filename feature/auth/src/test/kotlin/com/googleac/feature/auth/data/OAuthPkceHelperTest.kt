package com.googleac.feature.auth.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthPkceHelperTest {

    // ── Code verifier ─────────────────────────────────────────────────────────

    @Test
    fun `generateCodeVerifier returns base64url characters only`() {
        val verifier = OAuthPkceHelper.generateCodeVerifier()
        assertTrue(
            "Verifier must contain only base64url characters (A-Z, a-z, 0-9, -, _)",
            verifier.matches(Regex("[A-Za-z0-9\\-_]+"))
        )
    }

    @Test
    fun `generateCodeVerifier length is within RFC 7636 range`() {
        val verifier = OAuthPkceHelper.generateCodeVerifier()
        assertTrue(
            "Verifier length ${verifier.length} must be between 43 and 128",
            verifier.length in 43..128
        )
    }

    @Test
    fun `generateCodeVerifier produces unique values on each call`() {
        val v1 = OAuthPkceHelper.generateCodeVerifier()
        val v2 = OAuthPkceHelper.generateCodeVerifier()
        assertNotEquals("Each call should produce a unique verifier", v1, v2)
    }

    // ── Code challenge ────────────────────────────────────────────────────────

    @Test
    fun `generateCodeChallenge returns non-empty base64url string`() {
        val challenge = OAuthPkceHelper.generateCodeChallenge("some_verifier_value")
        assertTrue(challenge.isNotBlank())
        assertTrue(
            "Challenge must contain only base64url characters",
            challenge.matches(Regex("[A-Za-z0-9\\-_]+"))
        )
    }

    @Test
    fun `generateCodeChallenge is deterministic for the same verifier`() {
        val verifier = "deterministic_test_verifier_abc123"
        val c1 = OAuthPkceHelper.generateCodeChallenge(verifier)
        val c2 = OAuthPkceHelper.generateCodeChallenge(verifier)
        assertEquals("Same verifier must always produce the same challenge", c1, c2)
    }

    @Test
    fun `generateCodeChallenge differs for different verifiers`() {
        val c1 = OAuthPkceHelper.generateCodeChallenge("verifier_alpha")
        val c2 = OAuthPkceHelper.generateCodeChallenge("verifier_beta")
        assertNotEquals("Different verifiers must produce different challenges", c1, c2)
    }

    @Test
    fun `generateCodeChallenge does not contain padding characters`() {
        val challenge = OAuthPkceHelper.generateCodeChallenge(OAuthPkceHelper.generateCodeVerifier())
        assertFalse("S256 challenge must not be padded with '='", challenge.contains('='))
    }

    // ── Authorization URL ─────────────────────────────────────────────────────

    @Test
    fun `buildAuthorizationUrl starts with Google OAuth endpoint`() {
        val url = OAuthPkceHelper.buildAuthorizationUrl("client_id", "challenge")
        assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth"))
    }

    @Test
    fun `buildAuthorizationUrl contains client_id parameter`() {
        val url = OAuthPkceHelper.buildAuthorizationUrl("my-client-id", "challenge")
        assertTrue(url.contains("client_id=my-client-id"))
    }

    @Test
    fun `buildAuthorizationUrl contains PKCE code_challenge and S256 method`() {
        val url = OAuthPkceHelper.buildAuthorizationUrl("client", "my_challenge")
        assertTrue(url.contains("code_challenge=my_challenge"))
        assertTrue(url.contains("code_challenge_method=S256"))
    }

    @Test
    fun `buildAuthorizationUrl requests authorization code response type`() {
        val url = OAuthPkceHelper.buildAuthorizationUrl("client", "challenge")
        assertTrue(url.contains("response_type=code"))
    }

    @Test
    fun `buildAuthorizationUrl requests offline access for refresh token`() {
        val url = OAuthPkceHelper.buildAuthorizationUrl("client", "challenge")
        assertTrue(url.contains("access_type=offline"))
    }

    @Test
    fun `buildAuthorizationUrl includes consent prompt to ensure refresh token`() {
        val url = OAuthPkceHelper.buildAuthorizationUrl("client", "challenge")
        assertTrue(url.contains("prompt=consent"))
    }

    @Test
    fun `buildAuthorizationUrl includes redirect_uri`() {
        val url = OAuthPkceHelper.buildAuthorizationUrl("client", "challenge")
        assertTrue(url.contains("redirect_uri="))
        // Must reference the app's custom scheme
        assertTrue(url.contains("googleac"))
    }

    @Test
    fun `buildAuthorizationUrl includes login_hint when provided`() {
        val url = OAuthPkceHelper.buildAuthorizationUrl(
            clientId = "client",
            codeChallenge = "challenge",
            loginHint = "user@example.com"
        )
        assertTrue(url.contains("login_hint="))
    }

    @Test
    fun `buildAuthorizationUrl omits login_hint when null`() {
        val url = OAuthPkceHelper.buildAuthorizationUrl(
            clientId = "client",
            codeChallenge = "challenge",
            loginHint = null
        )
        assertFalse(url.contains("login_hint"))
    }

    @Test
    fun `buildAuthorizationUrl uses drive file scope by default`() {
        val url = OAuthPkceHelper.buildAuthorizationUrl("client", "challenge")
        // scope param must contain the least-privilege drive.file scope
        assertTrue(url.contains("drive.file"))
    }

    @Test
    fun `buildAuthorizationUrl accepts custom scopes`() {
        val url = OAuthPkceHelper.buildAuthorizationUrl(
            clientId = "client",
            codeChallenge = "challenge",
            scopes = listOf("https://www.googleapis.com/auth/drive")
        )
        assertTrue(url.contains("drive"))
        assertFalse("Custom scopes should replace defaults", url.contains("drive.file"))
    }

    // ── Auth code extraction ──────────────────────────────────────────────────

    @Test
    fun `extractAuthCode returns code from valid redirect URI`() {
        val redirect = "${OAuthPkceHelper.REDIRECT_URI}?code=auth_code_xyz&state=abc"
        val code = OAuthPkceHelper.extractAuthCode(redirect)
        assertEquals("auth_code_xyz", code)
    }

    @Test
    fun `extractAuthCode returns null when code parameter is absent`() {
        val redirect = "${OAuthPkceHelper.REDIRECT_URI}?error=access_denied"
        val code = OAuthPkceHelper.extractAuthCode(redirect)
        assertNull(code)
    }

    @Test
    fun `extractAuthCode returns null when URI has no query string`() {
        val redirect = OAuthPkceHelper.REDIRECT_URI
        val code = OAuthPkceHelper.extractAuthCode(redirect)
        assertNull(code)
    }

    @Test
    fun `extractAuthCode handles code as sole query parameter`() {
        val redirect = "${OAuthPkceHelper.REDIRECT_URI}?code=solo_code"
        val code = OAuthPkceHelper.extractAuthCode(redirect)
        assertEquals("solo_code", code)
    }
}
