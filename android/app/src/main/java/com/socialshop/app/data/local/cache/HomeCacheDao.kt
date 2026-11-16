package com.socialshop.app.data.local.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface HomeCacheDao {

    @Query("SELECT * FROM cached_categories ORDER BY position")
    suspend fun categories(): List<CachedCategory>

    @Query("SELECT * FROM cached_sections ORDER BY position")
    suspend fun sections(): List<CachedSection>

    @Query("SELECT * FROM cached_products ORDER BY listKey, position")
    suspend fun products(): List<CachedProduct>

    @Query("SELECT * FROM cache_meta WHERE `key` = :key")
    suspend fun meta(key: String): CacheMeta?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(rows: List<CachedCategory>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(rows: List<CachedSection>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(rows: List<CachedProduct>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeta(row: CacheMeta)

    @Query("DELETE FROM cached_categories")
    suspend fun clearCategories()

    @Query("DELETE FROM cached_sections")
    suspend fun clearSections()

    @Query("DELETE FROM cached_products")
    suspend fun clearProducts()

    /** Thay toàn bộ cache trang chủ trong một transaction: không bao giờ đọc phải dữ liệu nửa cũ nửa mới. */
    @Transaction
    suspend fun replaceHome(categories: List<CachedCategory>, sections: List<CachedSection>, products: List<CachedProduct>, meta: CacheMeta) {
        clearCategories()
        clearSections()
        clearProducts()
        insertCategories(categories)
        insertSections(sections)
        insertProducts(products)
        insertMeta(meta)
    }
}
