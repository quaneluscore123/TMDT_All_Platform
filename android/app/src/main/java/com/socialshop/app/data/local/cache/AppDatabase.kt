package com.socialshop.app.data.local.cache

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CachedCategory::class, CachedSection::class, CachedProduct::class, CacheMeta::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun homeCacheDao(): HomeCacheDao
}
