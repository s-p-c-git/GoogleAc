package com.googleac.app.di

import com.googleac.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

/**
 * App-level Hilt module.
 *
 * Exposes the OAuth client ID (read from `local.properties` at build time) as a
 * named string so feature modules can inject it without a direct dependency on
 * the app module's [BuildConfig].
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * The Google OAuth 2.0 client ID for this installation.
     *
     * Set `oauth.client_id=<your-id>.apps.googleusercontent.com` in
     * `local.properties` before building.  An empty string is provided as a
     * safe default so the app compiles even without the file, but sign-in will
     * fail until a real value is supplied.
     */
    @Provides
    @Named("oauthClientId")
    fun provideOAuthClientId(): String = BuildConfig.OAUTH_CLIENT_ID
}
