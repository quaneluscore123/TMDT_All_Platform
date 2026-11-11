package com.socialshop.app.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Mobile API quản trị: /api/v1/admin/... — server trả 403 nếu tài khoản không phải admin. */
interface AdminApiService {

    @GET("admin/dashboard")
    suspend fun dashboard(): AdminDashboard

    @GET("admin/orders")
    suspend fun orders(
        @Query("search") search: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
    ): Paged<AdminOrder>

    @GET("admin/orders/{id}")
    suspend fun order(@Path("id") id: Long): DataEnvelope<AdminOrder>

    @PATCH("admin/orders/{id}/status")
    suspend fun updateOrderStatus(@Path("id") id: Long, @Body body: StatusRequest): AdminOrderEnvelope

    @GET("admin/products")
    suspend fun products(
        @Query("search") search: String? = null,
        @Query("status") status: String? = null,
        @Query("low_stock") lowStock: Int? = null,
        @Query("page") page: Int = 1,
    ): Paged<AdminProduct>

    @GET("admin/products/{id}")
    suspend fun product(@Path("id") id: Long): DataEnvelope<AdminProduct>

    @PATCH("admin/products/{id}")
    suspend fun updateProduct(@Path("id") id: Long, @Body body: ProductUpdateRequest): AdminProductEnvelope

    @POST("admin/products/{id}/toggle")
    suspend fun toggleProduct(@Path("id") id: Long): AdminProductEnvelope

    @GET("admin/reviews")
    suspend fun reviews(@Query("status") status: String? = null, @Query("page") page: Int = 1): Paged<AdminReview>

    @PATCH("admin/reviews/{id}/approve")
    suspend fun approveReview(@Path("id") id: Long): AdminReviewEnvelope

    @PATCH("admin/reviews/{id}/reject")
    suspend fun rejectReview(@Path("id") id: Long): AdminReviewEnvelope

    @DELETE("admin/reviews/{id}")
    suspend fun deleteReview(@Path("id") id: Long): MessageResponse

    @GET("admin/users")
    suspend fun users(
        @Query("search") search: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
    ): Paged<AdminUser>

    @PATCH("admin/users/{id}/toggle-status")
    suspend fun toggleUser(@Path("id") id: Long): AdminUserEnvelope

    @GET("admin/coupons")
    suspend fun coupons(@Query("status") status: String? = null, @Query("page") page: Int = 1): CouponPage

    @POST("admin/coupons")
    suspend fun createCoupon(@Body body: CouponCreateRequest): AdminCouponEnvelope

    @PATCH("admin/coupons/{id}/toggle")
    suspend fun toggleCoupon(@Path("id") id: Long): AdminCouponEnvelope

    @DELETE("admin/coupons/{id}")
    suspend fun deleteCoupon(@Path("id") id: Long): MessageResponse

    @GET("admin/settings/tax")
    suspend fun taxSettings(): TaxSettings

    @PUT("admin/settings/tax")
    suspend fun updateTaxSettings(@Body body: TaxSettingsRequest): TaxSettings
}
