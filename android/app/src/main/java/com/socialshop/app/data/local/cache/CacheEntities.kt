package com.socialshop.app.data.local.cache

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.socialshop.app.data.remote.Category
import com.socialshop.app.data.remote.CategoryRef
import com.socialshop.app.data.remote.Product

// Bảng cache trang chủ: lưu chuẩn hóa theo từng bảng (danh mục, nhóm, sản phẩm) thay vì một khối JSON.

@Entity(tableName = "cached_categories")
data class CachedCategory(
    @PrimaryKey val id: Long,
    val name: String,
    val slug: String,
    val parentId: Long?,
    val imageUrl: String?,
    val productsCount: Int?,
    val position: Int,
) {
    fun toModel() = Category(id, name, slug, parentId, imageUrl, productsCount)

    companion object {
        fun from(category: Category, position: Int) =
            CachedCategory(category.id, category.name, category.slug, category.parentId, category.imageUrl, category.productsCount, position)
    }
}

/** Một nhóm sản phẩm trên trang chủ ("Đang hot", "Bán chạy"...). */
@Entity(tableName = "cached_sections")
data class CachedSection(
    @PrimaryKey val key: String,
    val title: String,
    val position: Int,
)

/**
 * Sản phẩm thuộc một danh sách trên trang chủ. [listKey] = "flash_sale" hoặc key của nhóm;
 * cùng một sản phẩm có thể nằm ở nhiều danh sách nên khóa chính là (listKey, position).
 */
@Entity(tableName = "cached_products", primaryKeys = ["listKey", "position"])
data class CachedProduct(
    val listKey: String,
    val position: Int,
    val id: Long,
    val slug: String,
    val name: String,
    val brand: String?,
    val price: Long,
    val originalPrice: Long,
    val isOnSale: Boolean,
    val discountPercent: Int,
    val saleEndsAt: String?,
    val imageUrl: String?,
    val rating: Double,
    val reviewsCount: Int,
    val stock: Int,
    val hasVariants: Boolean,
    val categoryId: Long?,
    val categoryName: String?,
    val categorySlug: String?,
) {
    fun toModel() = Product(
        id = id,
        slug = slug,
        name = name,
        brand = brand,
        price = price,
        originalPrice = originalPrice,
        isOnSale = isOnSale,
        discountPercent = discountPercent,
        saleEndsAt = saleEndsAt,
        imageUrl = imageUrl,
        rating = rating,
        reviewsCount = reviewsCount,
        stock = stock,
        hasVariants = hasVariants,
        category = if (categoryId != null && categoryName != null && categorySlug != null) CategoryRef(categoryId, categoryName, categorySlug) else null,
    )

    companion object {
        fun from(listKey: String, position: Int, p: Product) = CachedProduct(
            listKey, position, p.id, p.slug, p.name, p.brand, p.price, p.originalPrice, p.isOnSale, p.discountPercent,
            p.saleEndsAt, p.imageUrl, p.rating, p.reviewsCount, p.stock, p.hasVariants,
            p.category?.id, p.category?.name, p.category?.slug,
        )
    }
}

/** Thông tin phụ của lần lưu: thời điểm lưu, hạn Flash Sale. */
@Entity(tableName = "cache_meta")
data class CacheMeta(
    @PrimaryKey val key: String,
    val updatedAt: Long,
    val value: String?,
)
