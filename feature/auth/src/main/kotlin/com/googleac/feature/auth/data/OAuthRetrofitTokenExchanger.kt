package com.googleac.feature.auth.data

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
 * standard Google token endpoint) and extracts fields from the JSON response
 * with simple regex patterns, avoiding the need for a JSON library dependency
 * in this module.
 *
 * The user's email address is extracted from the OIDC `id_token` claim using
 * [java.util.Base64] URL-safe decoding (available since Android 8 / minSdk 26).
 */
@Singleton
class OAuthRetrofitTokenExchanger @Inject constructor(
    private val okHttpClient: OkHttpClient
) : OAuthTokenExchanger {

    companion object {
        private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    }

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

    // ── Response parsing ─────────────────────────────────────────────────────

    private fun parseTokenResponse(json: String): TokenExchangeResult {
        val accessToken = extractStringField(json, "access_token")
            ?: error("No access_token in token response")
        val refreshToken = extractStringField(json, "refresh_token")
        val idToken = extractStringField(json, "id_token")
            ?: error("No id_token in token response")
        val email = extractEmailFromIdToken(idToken)
        return TokenExchangeResult(
            accessToken = accessToken,
            refreshToken = refreshToken,
            email = email,
            idToken = idToken
        )
    }

    /**
     * Extracts the value of a JSON string field by name using a simple regex.
     * Sufficient for the well-defined Google token response structure.
     */
    internal fun extractStringField(json: String, fieldName: String): String? =
        Regex(""""${Regex.escape(fieldName)}"\s*:\s*"([^"]+)"""")
            .find(json)
            ?.groupValues
            ?.get(1)

    /**
     * Decodes the payload section of an OIDC JWT and extracts the `email` claim.
     *
     * Uses [java.util.Base64] URL-safe decoding (requires minSdk 26).
     */
    internal fun extractEmailFromIdToken(idToken: String): String {
        val parts = idToken.split(".")
        require(parts.size >= 2) { "id_token does not have the expected JWT structure" }
        val payload = parts[1]
        // Base64url padding: length must be a multiple of 4
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        val payloadJson = String(
            Base64.getUrlDecoder().decode(padded),
            Charsets.UTF_8
        )
        return Regex(""""email"\s*:\s*"([^"]+)"""")
            .find(payloadJson)
            ?.groupValues
            ?.get(1)
            ?: error("No email claim found in id_token payload")
    }
}
