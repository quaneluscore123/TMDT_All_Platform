package com.socialshop.app.di

import android.content.Context
import androidx.room.Room
import com.socialshop.app.data.local.cache.AppDatabase
import com.socialshop.app.data.local.cache.HomeCache
import com.socialshop.app.data.local.cache.HomeCacheDao
import com.socialshop.app.data.local.cache.RoomHomeCache
import dagger.Binds
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "socialshop-cache.db")
            // Chỉ là cache: đổi cấu trúc bảng thì xóa đi tạo lại, lần mở app có mạng sẽ nạp lại
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideHomeCacheDao(db: AppDatabase): HomeCacheDao = db.homeCacheDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CacheBindings {
    @Binds
    abstract fun bindHomeCache(impl: RoomHomeCache): HomeCache
}
