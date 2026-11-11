package com.socialshop.app.ui.navigation

import kotlinx.serialization.Serializable

// Tab chính (bottom bar)
@Serializable data object HomeRoute
@Serializable data class ProductsRoute(val search: String? = null, val categoryId: Long? = null, val title: String? = null)
@Serializable data object CartRoute
@Serializable data object WishlistRoute
@Serializable data object AccountRoute

// Màn hình con
@Serializable data class ProductDetailRoute(val slug: String)
@Serializable data class ReviewsRoute(val slug: String, val name: String, val canReview: Boolean = false)
@Serializable data object CheckoutRoute
@Serializable data class PaymentRoute(val url: String, val orderId: Long)
@Serializable data object OrdersRoute
@Serializable data class OrderDetailRoute(val id: Long, val justPlaced: Boolean = false)
@Serializable data object LoginRoute
@Serializable data object RegisterRoute
@Serializable data object ForgotPasswordRoute
@Serializable data object EditProfileRoute
@Serializable data object ChangePasswordRoute

// Trợ lý AI
@Serializable data class ChatRoute(val initialMessage: String? = null)

// Quản trị (chỉ hiện với tài khoản role=admin; server vẫn kiểm tra quyền)
@Serializable data object AdminDashboardRoute
@Serializable data class AdminOrdersRoute(val status: String? = null)
@Serializable data class AdminOrderDetailRoute(val id: Long)
@Serializable data class AdminProductsRoute(val filter: String? = null)
@Serializable data class AdminProductEditRoute(val id: Long)
@Serializable data object AdminReviewsRoute
@Serializable data object AdminUsersRoute
@Serializable data object AdminCouponsRoute
@Serializable data object AdminCouponCreateRoute
@Serializable data object AdminTaxRoute
