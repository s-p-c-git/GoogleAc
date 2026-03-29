package com.googleac.feature.drive.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.googleac.core.data.db.dao.AccountDao
import com.googleac.feature.auth.data.TokenManager
import com.googleac.feature.drive.data.repository.DriveRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * WorkManager periodic sync worker.
 * Iterates over all active accounts, refreshes tokens if needed,
 * and syncs Drive metadata to the local Room database.
 */
@HiltWorker
class DriveSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val driveRepository: DriveRepository,
    private val accountDao: AccountDao,
    private val tokenManager: TokenManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val accounts = accountDao.observeAllAccounts().first()
            for (account in accounts) {
                val accessToken = tokenManager.getAccessToken(account.accountId) ?: continue
                driveRepository.syncFiles(account.accountId, accessToken)
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
