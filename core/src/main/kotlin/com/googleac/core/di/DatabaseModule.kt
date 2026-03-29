package com.googleac.core.di

import android.content.Context
import androidx.room.Room
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

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "googleac_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideDriveFileDao(db: AppDatabase): DriveFileDao = db.driveFileDao()
}
