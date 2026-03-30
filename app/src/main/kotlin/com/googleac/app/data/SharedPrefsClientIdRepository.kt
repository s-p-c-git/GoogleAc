package com.googleac.app.data

import android.content.Context
import com.googleac.feature.auth.data.ClientIdRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ClientIdRepository] implementation backed by [SharedPreferences].
 *
 * Priority order:
 * 1. Value stored by the user at runtime via [save] (survives app restarts).
 * 2. Build-time value from [BuildConfig.OAUTH_CLIENT_ID] (populated from
 *    `local.properties` at build time, or empty for CI/pre-built APKs).
 */
@Singleton
class SharedPrefsClientIdRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val buildTimeClientId: String
) : ClientIdRepository {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun get(): String {
        val stored = prefs.getString(KEY_CLIENT_ID, null)
        return if (!stored.isNullOrBlank()) stored else buildTimeClientId
    }

    override fun save(clientId: String) {
        prefs.edit().putString(KEY_CLIENT_ID, clientId.trim()).apply()
    }

    companion object {
        private const val PREFS_NAME = "gShare_config"
        private const val KEY_CLIENT_ID = "oauth_client_id"
    }
}
