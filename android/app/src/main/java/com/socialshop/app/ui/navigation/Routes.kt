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
