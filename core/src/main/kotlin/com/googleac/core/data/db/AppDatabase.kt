package com.googleac.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.googleac.core.data.db.dao.AccountDao
import com.googleac.core.data.db.dao.DriveFileDao
import com.googleac.core.data.db.entity.AccountEntity
import com.googleac.core.data.db.entity.DriveFileEntity

@Database(
    entities = [AccountEntity::class, DriveFileEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun driveFileDao(): DriveFileDao
}
