package com.socialshop.app.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Mobile API v1 của backend Laravel (routes/api.php). */
interface ApiService {

    // ─── Công khai ────────────────────────────────────────────────────────────
    @GET("home")
    suspend fun home(): HomeResponse

    @GET("categories")
    suspend fun categories(): DataEnvelope<List<Category>>

    @GET("products")
    suspend fun products(
        @Query("search") search: String? = null,
        @Query("category") categoryId: Long? = null,
        @Query("sort") sort: String? = null,
        @Query("min_price") minPrice: Long? = null,
        @Query("max_price") maxPrice: Long? = null,
        @Query("page") page: Int = 1,
    ): Paged<Product>

    @GET("products/{slug}")
    suspend fun product(@Path("slug") slug: String): DataEnvelope<ProductDetail>

    @GET("products/{slug}/reviews")
    suspend fun reviews(@Path("slug") slug: String, @Query("page") page: Int = 1): Paged<Review>

    @GET("shipping-config")
    suspend fun shippingConfig(): ShippingConfig

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: EmailRequest): MessageResponse

    // ─── Cần đăng nhập ───────────────────────────────────────────────────────
    @POST("auth/logout")
    suspend fun logout(): MessageResponse

    @GET("me")
    suspend fun me(): DataEnvelope<User>

    @PUT("me")
    suspend fun updateProfile(@Body body: ProfileUpdateRequest): DataEnvelope<User>

    @PUT("me/password")
    suspend fun changePassword(@Body body: PasswordChangeRequest): MessageResponse

    @GET("cart")
    suspend fun cart(): Cart

    @POST("cart/items")
    suspend fun addToCart(@Body body: AddToCartRequest): Cart

    @PATCH("cart/items/{id}")
    suspend fun updateCartItem(@Path("id") id: Long, @Body body: QuantityRequest): Cart

    @DELETE("cart/items/{id}")
    suspend fun removeCartItem(@Path("id") id: Long): Cart

    @POST("coupons/apply")
    suspend fun applyCoupon(@Body body: CouponRequest): CouponResult

    @POST("checkout/quote")
    suspend fun quote(@Body body: QuoteRequest): Quote

    @POST("orders")
    suspend fun placeOrder(@Body body: PlaceOrderRequest): PlaceOrderResponse

    @GET("orders")
    suspend fun orders(@Query("page") page: Int = 1): Paged<Order>

    @GET("orders/{id}")
    suspend fun order(@Path("id") id: Long): DataEnvelope<Order>

    @POST("orders/{id}/cancel")
    suspend fun cancelOrder(@Path("id") id: Long): OrderEnvelope

    @GET("wishlist")
    suspend fun wishlist(@Query("page") page: Int = 1): Paged<Product>

    @POST("wishlist/{productId}/toggle")
    suspend fun toggleWishlist(@Path("productId") productId: Long): WishlistToggle

    @POST("products/{slug}/reviews")
    suspend fun submitReview(@Path("slug") slug: String, @Body body: ReviewRequest): ReviewCreated
}
