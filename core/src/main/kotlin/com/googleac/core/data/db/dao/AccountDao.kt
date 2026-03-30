package com.googleac.core.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.googleac.core.data.db.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY created_at ASC")
    fun observeAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE is_active = 1 LIMIT 1")
    fun observeActiveAccount(): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE account_id = :accountId")
    suspend fun getAccount(accountId: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("UPDATE accounts SET is_active = 0")
    suspend fun clearActiveAccount()

    @Query("UPDATE accounts SET is_active = 1 WHERE account_id = :accountId")
    suspend fun setActiveAccount(accountId: String)

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun getAccountCount(): Int
}
