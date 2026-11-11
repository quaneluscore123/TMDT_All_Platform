package com.socialshop.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// DTO cho /api/v1/admin/... (chỉ tài khoản role=admin). snake_case ↔ camelCase do Json của NetworkModule lo.

// ─── Tổng quan ────────────────────────────────────────────────────────────────

@Serializable
data class TaxSummary(val enabled: Boolean = false, val rate: Double = 0.0, val label: String = "Thuế VAT")

@Serializable
data class DailyRevenue(val date: String, val label: String, val total: Long)

@Serializable
data class TopProduct(val productId: Long, val name: String, val slug: String? = null, val sold: Int, val revenue: Long = 0)

@Serializable
data class AdminDashboard(
    val revenueMonth: Long = 0,
    val taxMonth: Long = 0,
    val ordersMonth: Int = 0,
    val ordersToday: Int = 0,
    val pendingOrders: Int = 0,
    val pendingReviews: Int = 0,
    val lowStockProducts: Int = 0,
    val lowStockThreshold: Int = 5,
    val totalProducts: Int = 0,
    val totalUsers: Int = 0,
    val tax: TaxSummary = TaxSummary(),
    @SerialName("revenue_7_days") val revenue7Days: List<DailyRevenue> = emptyList(),
    val topSelling: List<TopProduct> = emptyList(),
    val recentOrders: List<AdminOrder> = emptyList(),
)

// ─── Đơn hàng ─────────────────────────────────────────────────────────────────

@Serializable
data class Customer(val id: Long, val name: String, val email: String, val phone: String? = null)

@Serializable
data class StatusOption(val value: String, val label: String)

@Serializable
data class AdminOrder(
    val id: Long,
    val orderCode: String,
    val status: String,
    val statusLabel: String,
    val paymentMethod: String,
    val paymentStatus: String,
    val paymentStatusLabel: String,
    val subtotal: Long = 0,
    val discount: Long = 0,
    val shippingFee: Long = 0,
    val taxRate: Double = 0.0,
    val taxAmount: Long = 0,
    val total: Long,
    val shippingName: String = "",
    val shippingPhone: String = "",
    val shippingAddress: String = "",
    val shippingProvince: String? = null,
    val note: String? = null,
    val itemsCount: Int = 0,
    val items: List<OrderItem> = emptyList(),
    val customer: Customer? = null,
    val nextStatuses: List<StatusOption> = emptyList(),
    val createdAt: String? = null,
)

@Serializable
data class StatusRequest(val status: String)

@Serializable
data class AdminOrderEnvelope(val message: String? = null, val order: AdminOrder)

// ─── Sản phẩm ─────────────────────────────────────────────────────────────────

@Serializable
data class AdminVariant(val id: Long, val size: String? = null, val color: String? = null, val stock: Int = 0, val price: Long? = null) {
    val label: String get() = listOfNotNull(size, color).joinToString(" - ").ifBlank { "Mặc định" }
}

@Serializable
data class AdminProduct(
    val id: Long,
    val slug: String,
    val name: String,
    val brand: String? = null,
    val imageUrl: String? = null,
    val categoryName: String? = null,
    val price: Long,
    val salePrice: Long? = null,
    val effectivePrice: Long,
    val isOnSale: Boolean = false,
    val saleEndsAt: String? = null,
    val costPrice: Long? = null,
    val grossMarginPercent: Double? = null,
    val stock: Int = 0,
    val status: String,
    val statusLabel: String,
    val viewsCount: Int = 0,
    val variants: List<AdminVariant>? = null,
)

@Serializable
data class VariantUpdate(val id: Long, val stock: Int, val price: Long?)

@Serializable
data class ProductUpdateRequest(
    val price: Long,
    val salePrice: Long?,
    val stock: Int?,
    val status: String?,
    val variants: List<VariantUpdate>?,
)

@Serializable
data class AdminProductEnvelope(val message: String? = null, val product: AdminProduct)

// ─── Đánh giá ─────────────────────────────────────────────────────────────────

@Serializable
data class AdminReview(
    val id: Long,
    val rating: Int,
    val comment: String? = null,
    val imageUrl: String? = null,
    val status: String,
    val statusLabel: String,
    val userName: String,
    val productId: Long? = null,
    val productName: String? = null,
    val productSlug: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class AdminReviewEnvelope(val message: String? = null, val review: AdminReview)

// ─── Người dùng ───────────────────────────────────────────────────────────────

@Serializable
data class AdminUser(
    val id: Long,
    val name: String,
    val email: String,
    val phone: String? = null,
    val role: String = "customer",
    val isActive: Boolean = true,
    val ordersCount: Int = 0,
    val createdAt: String? = null,
)

@Serializable
data class AdminUserEnvelope(val message: String? = null, val user: AdminUser)

// ─── Mã giảm giá ──────────────────────────────────────────────────────────────

@Serializable
data class AdminCoupon(
    val id: Long,
    val code: String,
    val type: String,
    val value: Long,
    val maxDiscount: Long? = null,
    val minOrderAmount: Long = 0,
    val startAt: String? = null,
    val endAt: String? = null,
    val usageLimit: Int? = null,
    val usedCount: Int = 0,
    val status: String,
    val isExpired: Boolean = false,
)

@Serializable
data class CouponStats(val total: Int = 0, val active: Int = 0, val expired: Int = 0, val used: Int = 0)

@Serializable
data class CouponPage(
    val data: List<AdminCoupon> = emptyList(),
    val meta: PageMeta = PageMeta(),
    val stats: CouponStats = CouponStats(),
)

@Serializable
data class CouponCreateRequest(
    val code: String,
    val type: String,
    val value: Long,
    val maxDiscount: Long?,
    val minOrderAmount: Long,
    val startAt: String?,
    val endAt: String?,
    val usageLimit: Int?,
    val status: String,
)

@Serializable
data class AdminCouponEnvelope(val message: String? = null, val coupon: AdminCoupon)

// ─── Thuế VAT ─────────────────────────────────────────────────────────────────

@Serializable
data class PricingExample(
    val subtotal: Long = 0,
    val discount: Long = 0,
    val shippingFee: Long = 0,
    val taxRate: Double = 0.0,
    val taxAmount: Long = 0,
    val total: Long = 0,
)

@Serializable
data class TaxSettings(
    val message: String? = null,
    val taxEnabled: Boolean = false,
    val taxRate: Double = 0.0,
    val taxLabel: String = "Thuế VAT",
    val taxOnShipping: Boolean = false,
    val example: PricingExample = PricingExample(),
)

@Serializable
data class TaxSettingsRequest(val taxEnabled: Boolean, val taxRate: Double, val taxLabel: String, val taxOnShipping: Boolean)
