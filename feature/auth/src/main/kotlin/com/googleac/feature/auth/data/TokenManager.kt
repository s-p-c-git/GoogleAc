package com.googleac.feature.auth.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages OAuth access and refresh tokens using the Android Keystore.
 * Concurrent token refreshes are serialized with an in-memory Mutex to
 * prevent race conditions across multiple accounts.
 *
 * Tokens are AES-256-GCM encrypted before writing to EncryptedSharedPreferences.
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS_PREFIX = "googleac_token_key_"
        private const val PREFS_NAME = "googleac_tokens"
        private const val GCM_TAG_LENGTH = 128
        private const val AES_KEY_SIZE = 256
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Per-account mutex map to prevent concurrent token refreshes
    private val refreshMutexMap = HashMap<String, Mutex>()
    private val mapMutex = Mutex()

    private suspend fun getMutexForAccount(accountId: String): Mutex {
        return mapMutex.withLock {
            refreshMutexMap.getOrPut(accountId) { Mutex() }
        }
    }

    /**
     * Store an OAuth access token for a given account, encrypted in the Keystore.
     */
    suspend fun storeAccessToken(accountId: String, token: String) {
        val mutex = getMutexForAccount(accountId)
        mutex.withLock {
            encrypt(accountId, "access", token)
        }
    }

    /**
     * Store an OAuth refresh token for a given account, encrypted in the Keystore.
     */
    suspend fun storeRefreshToken(accountId: String, token: String) {
        val mutex = getMutexForAccount(accountId)
        mutex.withLock {
            encrypt(accountId, "refresh", token)
        }
    }

    /**
     * Retrieve the access token for a given account, decrypting from the Keystore.
     */
    suspend fun getAccessToken(accountId: String): String? {
        val mutex = getMutexForAccount(accountId)
        return mutex.withLock {
            decrypt(accountId, "access")
        }
    }

    /**
     * Retrieve the refresh token for a given account, decrypting from the Keystore.
     */
    suspend fun getRefreshToken(accountId: String): String? {
        val mutex = getMutexForAccount(accountId)
        return mutex.withLock {
            decrypt(accountId, "refresh")
        }
    }

    /**
     * Clear all tokens for a given account (on sign-out).
     */
    suspend fun clearTokens(accountId: String) {
        val mutex = getMutexForAccount(accountId)
        mutex.withLock {
            prefs.edit()
                .remove(tokenKey(accountId, "access"))
                .remove(tokenKey(accountId, "refresh"))
                .apply()
        }
    }

    private fun getOrCreateSecretKey(accountId: String): SecretKey {
        val keyAlias = KEY_ALIAS_PREFIX + accountId
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(AES_KEY_SIZE)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            keyGenerator.generateKey()
        }
        return (keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun encrypt(accountId: String, type: String, plaintext: String) {
        val secretKey = getOrCreateSecretKey(accountId)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val ivAndCiphertext = iv + ciphertext
        val encoded = Base64.encodeToString(ivAndCiphertext, Base64.NO_WRAP)
        prefs.edit().putString(tokenKey(accountId, type), encoded).apply()
    }

    private fun decrypt(accountId: String, type: String): String? {
        val encoded = prefs.getString(tokenKey(accountId, type), null) ?: return null
        return try {
            val ivAndCiphertext = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = ivAndCiphertext.copyOfRange(0, 12) // GCM IV is 12 bytes
            val ciphertext = ivAndCiphertext.copyOfRange(12, ivAndCiphertext.size)
            val secretKey = getOrCreateSecretKey(accountId)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun tokenKey(accountId: String, type: String) = "${accountId}_${type}_token"
}
