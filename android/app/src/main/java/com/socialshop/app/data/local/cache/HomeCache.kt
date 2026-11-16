package com.socialshop.app.data.local.cache

import com.socialshop.app.data.remote.FlashSale
import com.socialshop.app.data.remote.HomeResponse
import com.socialshop.app.data.remote.HomeSection
import javax.inject.Inject
import javax.inject.Singleton

/** Trang chủ đọc từ cache kèm thời điểm lưu (epoch millis). */
data class CachedHome(val home: HomeResponse, val savedAt: Long)

/** Cache trang chủ để mở app khi mất mạng vẫn xem được. Interface để unit test dùng bản giả. */
interface HomeCache {
    suspend fun save(home: HomeResponse, now: Long = System.currentTimeMillis())

    suspend fun load(): CachedHome?
}

@Singleton
class RoomHomeCache @Inject constructor(private val dao: HomeCacheDao) : HomeCache {

    override suspend fun save(home: HomeResponse, now: Long) {
        val products = home.flashSale.products.mapIndexed { i, p -> CachedProduct.from(FLASH_SALE, i, p) } +
            home.sections.flatMap { section -> section.products.mapIndexed { i, p -> CachedProduct.from(section.key, i, p) } }

        dao.replaceHome(
            categories = home.categories.mapIndexed { i, c -> CachedCategory.from(c, i) },
            sections = home.sections.mapIndexed { i, s -> CachedSection(s.key, s.title, i) },
            products = products,
            meta = CacheMeta(META_HOME, updatedAt = now, value = home.flashSale.endsAt),
        )
    }

    override suspend fun load(): CachedHome? {
        val meta = dao.meta(META_HOME) ?: return null
        val byList = dao.products().groupBy({ it.listKey }, { it.toModel() })

        return CachedHome(
            home = HomeResponse(
                flashSale = FlashSale(endsAt = meta.value, products = byList[FLASH_SALE].orEmpty()),
                categories = dao.categories().map { it.toModel() },
                sections = dao.sections().map { HomeSection(it.key, it.title, byList[it.key].orEmpty()) },
            ),
            savedAt = meta.updatedAt,
        )
    }

    private companion object {
        const val FLASH_SALE = "flash_sale"
        const val META_HOME = "home"
    }
}
