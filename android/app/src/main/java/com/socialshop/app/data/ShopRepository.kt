package com.socialshop.app.data

import com.socialshop.app.data.local.TokenStore
import com.socialshop.app.data.remote.AddToCartRequest
import com.socialshop.app.data.remote.ApiService
import com.socialshop.app.data.remote.AuthResponse
import com.socialshop.app.data.remote.Cart
import com.socialshop.app.data.remote.CouponRequest
import com.socialshop.app.data.remote.EmailRequest
import com.socialshop.app.data.remote.LoginRequest
import com.socialshop.app.data.remote.PasswordChangeRequest
import com.socialshop.app.data.remote.PlaceOrderRequest
import com.socialshop.app.data.remote.ProfileUpdateRequest
import com.socialshop.app.data.remote.QuantityRequest
import com.socialshop.app.data.remote.QuoteRequest
import com.socialshop.app.data.remote.RegisterRequest
import com.socialshop.app.data.remote.ReviewRequest
import com.socialshop.app.data.remote.ShippingConfig
import com.socialshop.app.data.remote.User
import com.socialshop.app.data.remote.apiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Điểm truy cập dữ liệu duy nhất cho UI. Giữ trạng thái phiên (người dùng, số món trong giỏ)
 * để thanh điều hướng và các màn hình cùng đọc.
 */
class ShopRepository(
    private val api: ApiService,
    private val tokenStore: TokenStore,
) {
    val token: StateFlow<String?> = tokenStore.token

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _cartCount = MutableStateFlow(0)
    val cartCount: StateFlow<Int> = _cartCount.asStateFlow()

    private var shippingConfigCache: ShippingConfig? = null

    val isLoggedIn: Boolean get() = tokenStore.currentToken != null

    // ─── Phiên đăng nhập ────────────────────────────────────────────────────────

    suspend fun restoreSession() {
        if (!isLoggedIn) return
        apiCall { api.me().data }.onSuccess { _user.value = it }
        refreshCartCount()
    }

    suspend fun login(email: String, password: String) =
        apiCall { api.login(LoginRequest(email.trim(), password)) }.onSuccess { onAuthenticated(it) }.map { it.user }

    suspend fun register(name: String, email: String, password: String, confirm: String, referral: String?) =
        apiCall {
            api.register(RegisterRequest(name.trim(), email.trim(), password, confirm, referral?.trim()?.ifBlank { null }))
        }.onSuccess { onAuthenticated(it) }.map { it.user }

    suspend fun forgotPassword(email: String) = apiCall { api.forgotPassword(EmailRequest(email.trim())) }

    suspend fun logout() {
        apiCall { api.logout() }
        tokenStore.clear()
        _user.value = null
        _cartCount.value = 0
    }

    /** Gọi khi interceptor đã xóa token (401) để dọn trạng thái hiển thị. */
    fun onSessionExpired() {
        _user.value = null
        _cartCount.value = 0
    }

    private suspend fun onAuthenticated(response: AuthResponse) {
        tokenStore.save(response.token)
        _user.value = response.user
        refreshCartCount()
    }

    suspend fun updateProfile(name: String, phone: String?, address: String?) =
        apiCall { api.updateProfile(ProfileUpdateRequest(name.trim(), phone?.trim(), address?.trim())).data }
            .onSuccess { _user.value = it }

    suspend fun changePassword(current: String, new: String, confirm: String) =
        apiCall { api.changePassword(PasswordChangeRequest(current, new, confirm)) }

    // ─── Danh mục & sản phẩm ──────────────────────────────────────────────────────

    suspend fun home() = apiCall { api.home() }

    suspend fun categories() = apiCall { api.categories().data }

    suspend fun products(search: String?, categoryId: Long?, sort: String?, page: Int) =
        apiCall { api.products(search = search?.ifBlank { null }, categoryId = categoryId, sort = sort, page = page) }

    suspend fun product(slug: String) = apiCall { api.product(slug).data }

    suspend fun reviews(slug: String, page: Int = 1) = apiCall { api.reviews(slug, page) }

    suspend fun submitReview(slug: String, rating: Int, comment: String?) =
        apiCall { api.submitReview(slug, ReviewRequest(rating, comment?.ifBlank { null })) }

    suspend fun shippingConfig() = shippingConfigCache?.let { Result.success(it) }
        ?: apiCall { api.shippingConfig() }.onSuccess { shippingConfigCache = it }

    // ─── Giỏ hàng ───────────────────────────────────────────────────────────────

    suspend fun cart() = apiCall { api.cart() }.onSuccess(::syncCart)

    suspend fun addToCart(productId: Long, variantId: Long?, quantity: Int) =
        apiCall { api.addToCart(AddToCartRequest(productId, variantId, quantity)) }.onSuccess(::syncCart)

    suspend fun updateCartItem(itemId: Long, quantity: Int) =
        apiCall { api.updateCartItem(itemId, QuantityRequest(quantity)) }.onSuccess(::syncCart)

    suspend fun removeCartItem(itemId: Long) = apiCall { api.removeCartItem(itemId) }.onSuccess(::syncCart)

    suspend fun refreshCartCount() {
        if (isLoggedIn) cart()
    }

    private fun syncCart(cart: Cart) {
        _cartCount.value = cart.itemsCount
    }

    // ─── Thanh toán & đơn hàng ───────────────────────────────────────────────────

    suspend fun applyCoupon(code: String) = apiCall { api.applyCoupon(CouponRequest(code.trim())) }

    suspend fun quote(province: String?, couponCode: String?) =
        apiCall { api.quote(QuoteRequest(province, couponCode?.ifBlank { null })) }

    suspend fun placeOrder(request: PlaceOrderRequest) =
        apiCall { api.placeOrder(request) }.onSuccess { _cartCount.value = 0 }

    suspend fun orders(page: Int = 1) = apiCall { api.orders(page) }

    suspend fun order(id: Long) = apiCall { api.order(id).data }

    suspend fun cancelOrder(id: Long) = apiCall { api.cancelOrder(id).order }

    // ─── Yêu thích ──────────────────────────────────────────────────────────────

    suspend fun wishlist(page: Int = 1) = apiCall { api.wishlist(page) }

    suspend fun toggleWishlist(productId: Long) = apiCall { api.toggleWishlist(productId) }
}
