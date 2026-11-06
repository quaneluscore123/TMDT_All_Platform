package com.socialshop.app.data.remote

import kotlinx.serialization.Serializable

// JSON từ /api/v1 dùng snake_case; Json được cấu hình JsonNamingStrategy.SnakeCase (NetworkModule)
// nên các thuộc tính ở đây viết camelCase.

@Serializable
data class DataEnvelope<T>(val data: T)

@Serializable
data class PageMeta(
    val currentPage: Int = 1,
    val lastPage: Int = 1,
    val total: Int = 0,
)

@Serializable
data class Paged<T>(
    val data: List<T> = emptyList(),
    val meta: PageMeta = PageMeta(),
)

@Serializable
data class CategoryRef(val id: Long, val name: String, val slug: String)

@Serializable
data class Category(
    val id: Long,
    val name: String,
    val slug: String,
    val parentId: Long? = null,
    val imageUrl: String? = null,
    val productsCount: Int? = null,
)

@Serializable
data class Product(
    val id: Long,
    val slug: String,
    val name: String,
    val brand: String? = null,
    val price: Long,
    val originalPrice: Long,
    val isOnSale: Boolean = false,
    val discountPercent: Int = 0,
    val saleEndsAt: String? = null,
    val imageUrl: String? = null,
    val rating: Double = 0.0,
    val reviewsCount: Int = 0,
    val stock: Int = 0,
    val hasVariants: Boolean = false,
    val category: CategoryRef? = null,
)

@Serializable
data class Variant(
    val id: Long,
    val size: String? = null,
    val color: String? = null,
    val label: String = "",
    val stock: Int = 0,
    val price: Long,
)

@Serializable
data class ProductDetail(
    val id: Long,
    val slug: String,
    val name: String,
    val brand: String? = null,
    val sku: String? = null,
    val description: String? = null,
    val price: Long,
    val originalPrice: Long,
    val isOnSale: Boolean = false,
    val discountPercent: Int = 0,
    val saleEndsAt: String? = null,
    val imageUrl: String? = null,
    val images: List<String> = emptyList(),
    val rating: Double = 0.0,
    val reviewsCount: Int = 0,
    val stock: Int = 0,
    val totalStock: Int = 0,
    val hasVariants: Boolean = false,
    val variants: List<Variant> = emptyList(),
    val category: CategoryRef? = null,
    val soldCount: Int = 0,
    val viewsCount: Int? = null,
    val isWishlisted: Boolean = false,
    val canReview: Boolean = false,
    val related: List<Product> = emptyList(),
    val boughtTogether: List<Product> = emptyList(),
    val webUrl: String? = null,
)

@Serializable
data class Review(
    val id: Long,
    val rating: Int,
    val comment: String? = null,
    val imageUrl: String? = null,
    val userName: String = "",
    val status: String = "approved",
    val createdAt: String? = null,
)

@Serializable
data class FlashSale(val endsAt: String? = null, val products: List<Product> = emptyList())

@Serializable
data class HomeSection(val key: String, val title: String, val products: List<Product> = emptyList())

@Serializable
data class HomeResponse(
    val flashSale: FlashSale = FlashSale(),
    val categories: List<Category> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
)

@Serializable
data class PaymentMethod(val code: String, val label: String)

@Serializable
data class ShippingConfig(
    val provinces: List<String> = emptyList(),
    val taxLabel: String = "VAT",
    val taxRate: Double = 0.0,
    val paymentMethods: List<PaymentMethod> = emptyList(),
)

// ─── Auth / tài khoản ──────────────────────────────────────────────────────────

@Serializable
data class User(
    val id: Long,
    val name: String,
    val email: String,
    val phone: String? = null,
    val address: String? = null,
    val avatar: String? = null,
    val role: String = "customer",
    val referralCode: String? = null,
    val marketingConsent: Boolean = false,
)

@Serializable
data class LoginRequest(val email: String, val password: String, val deviceName: String = "android")

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val passwordConfirmation: String,
    val referralCode: String? = null,
    val deviceName: String = "android",
)

@Serializable
data class AuthResponse(val token: String, val user: User)

@Serializable
data class EmailRequest(val email: String)

@Serializable
data class ProfileUpdateRequest(val name: String, val phone: String?, val address: String?)

@Serializable
data class PasswordChangeRequest(
    val currentPassword: String,
    val password: String,
    val passwordConfirmation: String,
)

@Serializable
data class MessageResponse(val message: String? = null)

// ─── Giỏ hàng ─────────────────────────────────────────────────────────────────

@Serializable
data class CartItem(
    val id: Long,
    val productId: Long,
    val productSlug: String,
    val name: String,
    val imageUrl: String? = null,
    val variantId: Long? = null,
    val variantLabel: String? = null,
    val quantity: Int,
    val unitPrice: Long,
    val subtotal: Long,
    val stock: Int = 0,
)

@Serializable
data class Cart(
    val message: String? = null,
    val items: List<CartItem> = emptyList(),
    val itemsCount: Int = 0,
    val subtotal: Long = 0,
)

@Serializable
data class AddToCartRequest(val productId: Long, val variantId: Long?, val quantity: Int)

@Serializable
data class QuantityRequest(val quantity: Int)

// ─── Thanh toán & đơn hàng ──────────────────────────────────────────────────────

@Serializable
data class CouponRequest(val code: String)

@Serializable
data class CouponResult(val code: String, val discount: Long, val message: String? = null)

@Serializable
data class QuoteRequest(val shippingProvince: String?, val couponCode: String?)

@Serializable
data class Quote(
    val subtotal: Long,
    val discount: Long,
    val couponError: String? = null,
    val shippingFee: Long,
    val shippingZone: String? = null,
    val taxRate: Double = 0.0,
    val taxLabel: String = "VAT",
    val taxAmount: Long = 0,
    val total: Long,
)

@Serializable
data class PlaceOrderRequest(
    val shippingName: String,
    val shippingPhone: String,
    val shippingAddress: String,
    val shippingProvince: String,
    val shippingNotes: String?,
    val paymentMethod: String,
    val couponCode: String?,
    val expectedTotal: Long,
    val agreeTerms: Boolean,
)

@Serializable
data class OrderItem(
    val id: Long,
    val productId: Long? = null,
    val productSlug: String? = null,
    val name: String,
    val imageUrl: String? = null,
    val variantLabel: String? = null,
    val price: Long,
    val quantity: Int,
    val subtotal: Long,
)

@Serializable
data class Order(
    val id: Long,
    val orderCode: String,
    val status: String,
    val statusLabel: String,
    val paymentMethod: String,
    val paymentStatus: String,
    val paymentStatusLabel: String,
    val subtotal: Long,
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
    val canCancel: Boolean = false,
    val itemsCount: Int = 0,
    val items: List<OrderItem> = emptyList(),
    val createdAt: String? = null,
)

@Serializable
data class PlaceOrderResponse(val message: String? = null, val order: Order, val paymentUrl: String? = null)

@Serializable
data class OrderEnvelope(val message: String? = null, val order: Order)

@Serializable
data class WishlistToggle(val isWishlisted: Boolean, val wishlistCount: Int = 0, val message: String? = null)

@Serializable
data class ReviewRequest(val rating: Int, val comment: String?)

@Serializable
data class ReviewCreated(val message: String? = null, val review: Review)

/** Thân lỗi chuẩn của Laravel: 422 {message, errors{field:[...]}}, 409 {message, quoted_total, current_total}. */
@Serializable
data class ApiErrorBody(
    val message: String? = null,
    val errors: Map<String, List<String>>? = null,
    val currentTotal: Long? = null,
)
