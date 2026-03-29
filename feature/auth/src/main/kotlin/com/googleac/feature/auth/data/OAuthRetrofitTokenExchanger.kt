package com.googleac.feature.auth.data

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp-backed implementation of [OAuthTokenExchanger].
 *
 * Uses a form-encoded POST to `https://oauth2.googleapis.com/token` (the
 * standard Google token endpoint).  The JSON response is parsed with [Moshi]
 * (the same singleton instance used by the Drive module) to correctly handle
 * escaped characters and any future response structure changes.
 *
 * The user's email address is extracted from the OIDC `id_token` claim using
 * [java.util.Base64] URL-safe decoding (requires minSdk 26 / Android 8).
 */
@Singleton
class OAuthRetrofitTokenExchanger @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) : OAuthTokenExchanger {

    companion object {
        private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    }

    // ── Moshi model for the Google token endpoint response ───────────────────

    private data class TokenResponse(
        @Json(name = "access_token") val accessToken: String,
        @Json(name = "refresh_token") val refreshToken: String?,
        @Json(name = "id_token") val idToken: String
    )

    /** Subset of the OIDC id_token JWT payload — only the fields we need. */
    private data class IdTokenPayload(
        @Json(name = "email") val email: String?
    )

    // ── OAuthTokenExchanger ──────────────────────────────────────────────────

    override suspend fun exchangeCode(
        clientId: String,
        code: String,
        codeVerifier: String,
        redirectUri: String
    ): TokenExchangeResult = withContext(Dispatchers.IO) {
        val requestBody = FormBody.Builder()
            .add("client_id", clientId)
            .add("code", code)
            .add("code_verifier", codeVerifier)
            .add("redirect_uri", redirectUri)
            .add("grant_type", "authorization_code")
            .build()

        val httpRequest = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(requestBody)
            .build()

        val responseBody = okHttpClient.newCall(httpRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) {
                "Token exchange failed (${response.code}): $body"
            }
            body
        }

        parseTokenResponse(responseBody)
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    private fun parseTokenResponse(json: String): TokenExchangeResult {
        val adapter = moshi.adapter(TokenResponse::class.java)
        val response = requireNotNull(adapter.fromJson(json)) {
            "Failed to parse token response"
        }
        val email = extractEmailFromIdToken(response.idToken)
        return TokenExchangeResult(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            email = email,
            idToken = response.idToken
        )
    }

    /**
     * Decodes the payload section of an OIDC JWT and extracts the `email` claim.
     *
     * Base64url-decodes the middle segment of the JWT, then uses [Moshi] to parse
     * the JSON payload.  Uses [java.util.Base64] URL-safe decoding (requires minSdk 26).
     */
    internal fun extractEmailFromIdToken(idToken: String): String {
        val parts = idToken.split(".")
        require(parts.size >= 2) { "id_token does not have the expected JWT structure (got ${parts.size} part(s))" }
        val payload = parts[1]
        // Base64url padding: length must be a multiple of 4
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        val payloadJson = String(
            Base64.getUrlDecoder().decode(padded),
            Charsets.UTF_8
        )
        val adapter = moshi.adapter(IdTokenPayload::class.java)
        val parsed = requireNotNull(adapter.fromJson(payloadJson)) {
            "Failed to parse id_token payload"
        }
        return requireNotNull(parsed.email) {
            "No email claim found in id_token payload"
        }
    }
}
