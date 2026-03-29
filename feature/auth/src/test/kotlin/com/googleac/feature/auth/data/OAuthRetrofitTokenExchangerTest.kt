package com.googleac.feature.auth.data

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.util.Base64

/**
 * Unit tests for the parsing helpers in [OAuthRetrofitTokenExchanger].
 *
 * Network calls are not tested here — only the pure JSON/JWT parsing logic that
 * runs entirely on the JVM.
 */
class OAuthRetrofitTokenExchangerTest {

    // Use a real (but idle) OkHttpClient — no network calls are made in these tests.
    private lateinit var exchanger: OAuthRetrofitTokenExchanger

    @Before
    fun setUp() {
        exchanger = OAuthRetrofitTokenExchanger(OkHttpClient())
    }

    // ── extractStringField ────────────────────────────────────────────────────

    @Test
    fun `extractStringField returns value for existing field`() {
        val json = """{"access_token":"abc123","token_type":"Bearer"}"""
        assertEquals("abc123", exchanger.extractStringField(json, "access_token"))
    }

    @Test
    fun `extractStringField returns null for absent field`() {
        val json = """{"access_token":"abc123"}"""
        assertNull(exchanger.extractStringField(json, "refresh_token"))
    }

    @Test
    fun `extractStringField handles whitespace around colon`() {
        val json = """{"id_token" : "jwt.payload.sig"}"""
        assertEquals("jwt.payload.sig", exchanger.extractStringField(json, "id_token"))
    }

    @Test
    fun `extractStringField returns first match when field appears multiple times`() {
        val json = """{"foo":"first","foo":"second"}"""
        assertEquals("first", exchanger.extractStringField(json, "foo"))
    }

    // ── extractEmailFromIdToken ───────────────────────────────────────────────

    private fun buildIdToken(email: String): String {
        val header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"12345","email":"$email","email_verified":true}""".toByteArray())
        // Signature is not verified in this implementation — a placeholder is fine.
        return "$header.$payload.fakesig"
    }

    @Test
    fun `extractEmailFromIdToken returns email from valid id_token`() {
        val idToken = buildIdToken("user@gmail.com")
        assertEquals("user@gmail.com", exchanger.extractEmailFromIdToken(idToken))
    }

    @Test
    fun `extractEmailFromIdToken handles plus-alias emails`() {
        val idToken = buildIdToken("user+work@example.com")
        assertEquals("user+work@example.com", exchanger.extractEmailFromIdToken(idToken))
    }

    @Test
    fun `extractEmailFromIdToken handles corporate domain emails`() {
        val idToken = buildIdToken("firstname.lastname@corp.co.uk")
        assertEquals("firstname.lastname@corp.co.uk", exchanger.extractEmailFromIdToken(idToken))
    }

    @Test
    fun `extractEmailFromIdToken throws for id_token without enough segments`() {
        assertThrows(IllegalArgumentException::class.java) {
            exchanger.extractEmailFromIdToken("onlyone")
        }
    }

    @Test
    fun `extractEmailFromIdToken throws when email claim is absent from payload`() {
        val header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"alg":"RS256"}""".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"12345"}""".toByteArray())
        val idToken = "$header.$payload.sig"

        assertThrows(IllegalStateException::class.java) {
            exchanger.extractEmailFromIdToken(idToken)
        }
    }

    @Test
    fun `extractEmailFromIdToken handles base64url payload without padding`() {
        // Some issuers omit the '=' padding — our implementation adds it back.
        val idToken = buildIdToken("nopad@test.com")
        // Strip any padding that buildIdToken added (it uses withoutPadding already)
        assertEquals("nopad@test.com", exchanger.extractEmailFromIdToken(idToken))
    }
}
