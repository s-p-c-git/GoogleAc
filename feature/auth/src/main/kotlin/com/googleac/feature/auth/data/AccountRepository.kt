package com.googleac.feature.auth.data

import android.util.Log
import com.googleac.core.data.db.dao.AccountDao
import com.googleac.core.data.db.entity.AccountEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing Google accounts.
 *
 * Each mutating operation emits a structured log entry so that multi-account
 * additions and removals can be traced end-to-end in logcat as well as in
 * unit tests via captured log output.
 */
@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao
) {
    companion object {
        private const val TAG = "AccountRepository"
    }

    /** Observe all accounts ordered by creation time (oldest first). */
    fun observeAllAccounts(): Flow<List<AccountEntity>> = accountDao.observeAllAccounts()

    /** Observe the currently active account (null if none selected). */
    fun observeActiveAccount(): Flow<AccountEntity?> = accountDao.observeActiveAccount()

    /** Returns the total number of registered accounts. */
    suspend fun getAccountCount(): Int = accountDao.getAccountCount()

    /** Returns a single account by [accountId], or null if not found. */
    suspend fun getAccount(accountId: String): AccountEntity? = accountDao.getAccount(accountId)

    /**
     * Add (or replace) an account.
     *
     * Logs the operation at DEBUG level so that multi-account additions are
     * visible in logcat during development and stripped in release builds.
     */
    suspend fun addAccount(account: AccountEntity) {
        val count = accountDao.getAccountCount()
        Log.d(TAG, "addAccount: adding account #${count + 1} — persona=${account.personaType}")
        accountDao.insertAccount(account)
        val newCount = accountDao.getAccountCount()
        Log.d(TAG, "addAccount: done — total accounts=$newCount")
    }

    /**
     * Update an existing account's fields (e.g., enabled features after the onboarding step).
     */
    suspend fun updateAccount(account: AccountEntity) {
        Log.d(TAG, "updateAccount: id=${account.accountId} features=${account.enabledFeatures}")
        accountDao.updateAccount(account)
    }

    /**
     * Remove an account and all its associated data (cascade handled by DB).
     */
    suspend fun removeAccount(account: AccountEntity) {
        Log.d(TAG, "removeAccount: id=${account.accountId}")
        accountDao.deleteAccount(account)
        val newCount = accountDao.getAccountCount()
        Log.d(TAG, "removeAccount: done — total accounts=$newCount")
    }

    /**
     * Set the active account.
     */
    suspend fun setActiveAccount(accountId: String) {
        Log.d(TAG, "setActiveAccount: accountId=$accountId")
        accountDao.clearActiveAccount()
        accountDao.setActiveAccount(accountId)
    }
}
