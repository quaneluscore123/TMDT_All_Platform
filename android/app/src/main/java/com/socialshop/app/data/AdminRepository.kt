package com.socialshop.app.data

import com.socialshop.app.data.remote.AdminApiService
import com.socialshop.app.data.remote.CouponCreateRequest
import com.socialshop.app.data.remote.ProductUpdateRequest
import com.socialshop.app.data.remote.StatusRequest
import com.socialshop.app.data.remote.TaxSettingsRequest
import com.socialshop.app.data.remote.apiCall
import javax.inject.Inject
import javax.inject.Singleton

/** Nguồn dữ liệu cho khu vực Quản trị. Mọi hàm trả [Result] với lỗi đã dịch sang tiếng Việt. */
@Singleton
class AdminRepository @Inject constructor(private val api: AdminApiService) {

    suspend fun dashboard() = apiCall { api.dashboard() }

    suspend fun orders(search: String?, status: String?, page: Int) =
        apiCall { api.orders(search?.ifBlank { null }, status, page) }

    suspend fun order(id: Long) = apiCall { api.order(id).data }

    suspend fun updateOrderStatus(id: Long, status: String) = apiCall { api.updateOrderStatus(id, StatusRequest(status)) }

    suspend fun products(search: String?, filter: String?, page: Int) = apiCall {
        // "low_stock" là bộ lọc riêng, còn lại là trạng thái bán
        if (filter == "low_stock") {
            api.products(search?.ifBlank { null }, lowStock = 1, page = page)
        } else {
            api.products(search?.ifBlank { null }, status = filter, page = page)
        }
    }

    suspend fun product(id: Long) = apiCall { api.product(id).data }

    suspend fun updateProduct(id: Long, request: ProductUpdateRequest) = apiCall { api.updateProduct(id, request) }

    suspend fun toggleProduct(id: Long) = apiCall { api.toggleProduct(id).product }

    suspend fun reviews(status: String?, page: Int) = apiCall { api.reviews(status, page) }

    suspend fun approveReview(id: Long) = apiCall { api.approveReview(id) }

    suspend fun rejectReview(id: Long) = apiCall { api.rejectReview(id) }

    suspend fun deleteReview(id: Long) = apiCall { api.deleteReview(id) }

    suspend fun users(search: String?, status: String?, page: Int) =
        apiCall { api.users(search?.ifBlank { null }, status, page) }

    suspend fun toggleUser(id: Long) = apiCall { api.toggleUser(id) }

    suspend fun coupons(status: String?, page: Int) = apiCall { api.coupons(status, page) }

    suspend fun createCoupon(request: CouponCreateRequest) = apiCall { api.createCoupon(request) }

    suspend fun toggleCoupon(id: Long) = apiCall { api.toggleCoupon(id) }

    suspend fun deleteCoupon(id: Long) = apiCall { api.deleteCoupon(id) }

    suspend fun taxSettings() = apiCall { api.taxSettings() }

    suspend fun updateTaxSettings(request: TaxSettingsRequest) = apiCall { api.updateTaxSettings(request) }
}
