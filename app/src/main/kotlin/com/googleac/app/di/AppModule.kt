package com.googleac.app.di

import android.content.Context
import com.googleac.app.BuildConfig
import com.googleac.app.data.SharedPrefsClientIdRepository
import com.googleac.feature.auth.data.ClientIdRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * App-level Hilt module.
 *
 * Provides a [ClientIdRepository] that returns the Google OAuth 2.0 client ID.
 * Two sources are consulted in order:
 *  1. A value the user entered at runtime (stored in SharedPreferences).
 *  2. The build-time value from `local.properties` (empty for CI/pre-built APKs).
 *
 * This allows the pre-built CI APK to be installed and configured by any
 * developer without needing to rebuild the app.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideClientIdRepository(
        @ApplicationContext context: Context
    ): ClientIdRepository = SharedPrefsClientIdRepository(
        context = context,
        buildTimeClientId = BuildConfig.OAUTH_CLIENT_ID
    )
}
