package com.googleac.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.googleac.core.data.db.AppDatabase
import com.googleac.core.data.db.dao.AccountDao
import com.googleac.core.data.db.dao.DriveFileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Adds the `enabled_features` TEXT column to the `accounts` table.
     * Defaults to an empty JSON array so all existing rows appear as "no features enabled".
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE accounts ADD COLUMN enabled_features TEXT NOT NULL DEFAULT '[]'"
            )
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "googleac_db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideDriveFileDao(db: AppDatabase): DriveFileDao = db.driveFileDao()
}
