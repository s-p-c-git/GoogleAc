package com.googleac.feature.auth.data

/**
 * Result of a successful OAuth 2.0 authorization-code → token exchange.
 *
 * @property accessToken  Short-lived bearer token for API calls.
 * @property refreshToken Long-lived token for obtaining new access tokens (may be null on
 *                        re-authorisation when the user already granted offline access).
 * @property email        Verified email address extracted from the OIDC id_token.
 * @property idToken      Raw OIDC id_token JWT (retained for account registration).
 */
data class TokenExchangeResult(
    val accessToken: String,
    val refreshToken: String?,
    val email: String,
    val idToken: String
)

/**
 * Abstraction over the OAuth 2.0 token-exchange HTTP call.
 *
 * Keeping this as an interface allows [AuthViewModel] to be fully unit-tested
 * with a mock/fake without any Android or network dependencies.
 */
interface OAuthTokenExchanger {

    /**
     * Exchange an authorization [code] (obtained via PKCE) for access/refresh tokens.
     *
     * @param clientId     The Google OAuth client ID for this app.
     * @param code         The authorization code returned in the redirect URI.
     * @param codeVerifier The PKCE code verifier generated at the start of the flow.
     * @param redirectUri  Must exactly match the URI registered in the Google Cloud Console.
     * @return [TokenExchangeResult] on success.
     * @throws Exception on any HTTP or parsing error.
     */
    suspend fun exchangeCode(
        clientId: String,
        code: String,
        codeVerifier: String,
        redirectUri: String
    ): TokenExchangeResult
}
