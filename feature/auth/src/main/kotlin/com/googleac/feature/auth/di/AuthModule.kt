package com.googleac.feature.auth.di

import com.googleac.feature.auth.data.OAuthRetrofitTokenExchanger
import com.googleac.feature.auth.data.OAuthTokenExchanger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the auth feature.
 *
 * Binds [OAuthRetrofitTokenExchanger] as the [OAuthTokenExchanger] singleton.
 * The [OkHttpClient] it depends on is provided by the Drive module's
 * [com.googleac.feature.drive.di.DriveModule] which is installed in the same
 * [SingletonComponent].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindOAuthTokenExchanger(
        impl: OAuthRetrofitTokenExchanger
    ): OAuthTokenExchanger
}
