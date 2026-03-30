package com.googleac.feature.auth.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.util.Base64

/**
 * Unit tests for the JWT parsing helper in [OAuthRetrofitTokenExchanger].
 *
 * Network calls are not tested here — only the pure JWT parsing logic
 * that runs entirely on the JVM.
 */
class OAuthRetrofitTokenExchangerTest {

    private lateinit var exchanger: OAuthRetrofitTokenExchanger

    @Before
    fun setUp() {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        exchanger = OAuthRetrofitTokenExchanger(OkHttpClient(), moshi)
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

        assertThrows(IllegalArgumentException::class.java) {
            exchanger.extractEmailFromIdToken(idToken)
        }
    }

    @Test
    fun `extractEmailFromIdToken handles base64url payload without padding`() {
        // buildIdToken already uses withoutPadding — our implementation re-adds it
        val idToken = buildIdToken("nopad@test.com")
        assertEquals("nopad@test.com", exchanger.extractEmailFromIdToken(idToken))
    }

    @Test
    fun `extractEmailFromIdToken correctly handles emails with special JSON-safe characters`() {
        // Underscore and hyphen are common in email addresses
        val idToken = buildIdToken("my_user-name@sub.domain.org")
        assertEquals("my_user-name@sub.domain.org", exchanger.extractEmailFromIdToken(idToken))
    }
}
