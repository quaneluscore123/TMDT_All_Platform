package com.socialshop.app.ui

import com.socialshop.app.data.local.cache.CachedHome
import com.socialshop.app.data.remote.HomeResponse
import com.socialshop.app.testutil.MainDispatcherRule
import com.socialshop.app.testutil.TestBackend
import com.socialshop.app.testutil.eventually
import com.socialshop.app.ui.admin.AdminOrderDetailViewModel
import com.socialshop.app.ui.admin.AdminProductEditViewModel
import com.socialshop.app.ui.chat.ChatViewModel
import com.socialshop.app.ui.components.UiState
import com.socialshop.app.ui.home.HomeViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val backend = TestBackend()

    @After
    fun tearDown() {
        runCatching { backend.shutdown() }
    }

    private fun orderJson(status: String, label: String, next: String) = """
        {"id": 12, "order_code": "ORD-1", "status": "$status", "status_label": "$label",
         "payment_method": "cod", "payment_status": "pending", "payment_status_label": "Chưa thanh toán",
         "subtotal": 1000000, "shipping_fee": 0, "tax_rate": 8, "tax_amount": 80000, "total": 1080000,
         "next_statuses": $next}
    """

    // ─── Admin: đổi trạng thái đơn ──────────────────────────────────────────────

    @Test
    fun `admin order status change updates state with server result`() = runBlocking {
        backend.enqueueJson("""{"data": ${orderJson("pending", "Chờ xử lý", """[{"value":"confirmed","label":"Đã xác nhận"}]""")}}""")
        val vm = AdminOrderDetailViewModel(backend.admin, 12)
        eventually { vm.state.value is UiState.Success }

        backend.enqueueJson("""{"message": "Đã cập nhật trạng thái đơn hàng.", "order": ${orderJson("confirmed", "Đã xác nhận", "[]")}}""")
        val message = vm.changeStatus("confirmed").getOrThrow()

        assertEquals("Đã cập nhật trạng thái đơn hàng.", message)
        val order = (vm.state.value as UiState.Success).data
        assertEquals("confirmed", order.status)
        assertEquals(80_000L, order.taxAmount)
        assertFalse(vm.updating)
    }

    @Test
    fun `admin order keeps old state when server rejects`() = runBlocking {
        backend.enqueueJson("""{"data": ${orderJson("cancelled", "Đã hủy", "[]")}}""")
        val vm = AdminOrderDetailViewModel(backend.admin, 12)
        eventually { vm.state.value is UiState.Success }

        backend.enqueueJson("""{"message": "Không thể mở lại đơn đã hủy."}""", code = 422)
        val result = vm.changeStatus("pending")

        assertEquals("Không thể mở lại đơn đã hủy.", result.exceptionOrNull()?.message)
        assertEquals("cancelled", (vm.state.value as UiState.Success).data.status)
    }

    // ─── Admin: sửa nhanh sản phẩm ──────────────────────────────────────────────

    private fun productJson(stock: Int) = """
        {"id": 3, "slug": "ao", "name": "Áo", "price": 200000, "sale_price": null, "effective_price": 200000,
         "stock": $stock, "status": "active", "status_label": "Đang bán",
         "variants": [{"id": 31, "size": "M", "color": "Đen", "stock": $stock, "price": null}]}
    """

    private val productWithVariant = """{"data": ${productJson(4)}}"""

    @Test
    fun `product edit validates locally without calling server`() = runBlocking {
        backend.enqueueJson(productWithVariant)
        val vm = AdminProductEditViewModel(backend.admin, 3)
        eventually { vm.state.value is UiState.Success }

        vm.price = ""
        vm.variants.single().stock = "abc"
        val result = vm.save()

        assertTrue(result.isFailure)
        assertEquals("Nhập giá gốc hợp lệ.", vm.errors["price"])
        assertTrue(vm.errors.containsKey("variants"))
        assertEquals("Chỉ có request tải sản phẩm, không gửi lưu", 1, backend.server.requestCount)
    }

    @Test
    fun `product edit sends variant stock and drops total stock`() = runBlocking {
        backend.enqueueJson(productWithVariant)
        val vm = AdminProductEditViewModel(backend.admin, 3)
        eventually { vm.state.value is UiState.Success }
        backend.server.takeRequest()

        vm.salePrice = "150000"
        vm.variants.single().stock = "9"
        backend.enqueueJson("""{"message": "Đã lưu sản phẩm.", "product": ${productJson(9)}}""")
        vm.save()

        val body = backend.server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"sale_price\":150000"))
        assertTrue(body, body.contains("\"variants\":[{\"id\":31,\"stock\":9"))
        // Có biến thể thì tồn tổng do server tự cộng: app không gửi "stock" ở cấp sản phẩm (null bị bỏ khỏi JSON)
        assertFalse(body, body.substringBefore("\"variants\"").contains("\"stock\""))
    }

    // ─── Trợ lý AI ──────────────────────────────────────────────────────────────

    @Test
    fun `chat shows streamed answer in one bot bubble`() = runBlocking {
        backend.enqueueSse("""{"text":"Size "}""", """{"text":"M nhé!"}""", """{"done":true}""")
        val vm = ChatViewModel(backend.chat, backend.shop)

        vm.send("Cao 1m70 mặc size gì?")
        eventually { !vm.streaming }

        assertEquals(3, vm.messages.size) // lời chào + câu hỏi + trả lời
        assertEquals("Cao 1m70 mặc size gì?", vm.messages[1].text)
        assertEquals("Size M nhé!", vm.messages[2].text)
        assertFalse(vm.messages[2].failed)
    }

    @Test
    fun `chat marks failed answer when server errors`() = runBlocking {
        backend.enqueueJson("""{"message": "Too Many Attempts."}""", code = 429)
        val vm = ChatViewModel(backend.chat, backend.shop)

        vm.send("Xin chào")
        eventually { !vm.streaming }

        assertTrue(vm.messages.last().failed)
        assertEquals("Bạn hỏi hơi nhanh. Vui lòng đợi một chút rồi hỏi tiếp.", vm.messages.last().text)
    }

    // ─── Trang chủ offline ──────────────────────────────────────────────────────

    @Test
    fun `home shows cached data when offline`() = runBlocking {
        backend.homeCache.stored = CachedHome(HomeResponse(), savedAt = 42)
        backend.server.shutdown() // mất mạng

        val vm = HomeViewModel(backend.shop)
        eventually { vm.state.value !is UiState.Loading }

        val state = vm.state.value
        assertTrue("Phải hiện bản cache thay vì báo lỗi: $state", state is UiState.Success)
        assertEquals(42L, (state as UiState.Success).data.cachedAt)
    }
}
