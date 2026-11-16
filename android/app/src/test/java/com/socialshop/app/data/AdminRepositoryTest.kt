package com.socialshop.app.data

import com.socialshop.app.data.remote.ApiException
import com.socialshop.app.testutil.TestBackend
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AdminRepositoryTest {

    private val backend = TestBackend()

    @After
    fun tearDown() = backend.shutdown()

    @Test
    fun `dashboard parses revenue, VAT and 7 day chart`() = runBlocking {
        backend.enqueueJson(
            """
            {"revenue_month": 84396920, "tax_month": 15920, "orders_month": 19, "orders_today": 3,
             "pending_orders": 3, "pending_reviews": 5, "low_stock_products": 0, "low_stock_threshold": 5,
             "total_products": 40, "total_users": 6,
             "tax": {"enabled": true, "rate": 8, "label": "Thuế VAT"},
             "revenue_7_days": [{"date": "2026-11-10", "label": "10/11", "total": 17800000},
                                {"date": "2026-11-11", "label": "11/11", "total": 3008920}],
             "top_selling": [], "recent_orders": []}
            """,
        )

        val dashboard = backend.admin.dashboard().getOrThrow()

        assertEquals(15_920L, dashboard.taxMonth)
        assertEquals(8.0, dashboard.tax.rate, 0.0)
        // revenue_7_days có chữ số trong tên: từng bị SnakeCase đọc sai thành "revenue7_days"
        assertEquals(listOf("10/11", "11/11"), dashboard.revenue7Days.map { it.label })
    }

    @Test
    fun `rejected status change surfaces server message`() = runBlocking {
        backend.enqueueJson("""{"message": "Không thể mở lại đơn đã hủy."}""", code = 422)

        val error = backend.admin.updateOrderStatus(7, "pending").exceptionOrNull() as ApiException

        assertEquals("Không thể mở lại đơn đã hủy.", error.message)
        val request = backend.server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/admin/orders/7/status", request.path)
        assertEquals("""{"status":"pending"}""", request.body.readUtf8())
    }

    @Test
    fun `low stock filter is sent as a separate query`() = runBlocking {
        backend.enqueueJson("""{"data": [], "meta": {"current_page": 1, "last_page": 1, "total": 0}}""")

        backend.admin.products(search = " ", filter = "low_stock", page = 1).getOrThrow()

        assertEquals("/api/v1/admin/products?low_stock=1&page=1", backend.server.takeRequest().path)
    }
}
