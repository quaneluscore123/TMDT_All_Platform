package com.socialshop.app.data

import com.socialshop.app.data.local.cache.CachedHome
import com.socialshop.app.data.remote.ApiException
import com.socialshop.app.data.remote.HomeResponse
import com.socialshop.app.data.remote.HomeSection
import com.socialshop.app.data.remote.Product
import com.socialshop.app.testutil.TestBackend
import com.socialshop.app.testutil.productJson
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopRepositoryTest {

    private val backend = TestBackend()

    @After
    fun tearDown() = backend.shutdown()

    private val homeJson = """
        {"flash_sale": {"ends_at": "2026-12-01T00:00:00+07:00", "products": [${productJson(1, "Áo thun")}]},
         "categories": [{"id": 1, "name": "Áo", "slug": "ao"}],
         "sections": [{"key": "hot", "title": "Đang hot", "products": [${productJson(2, "Giày")}]}]}
    """

    @Test
    fun `home from server is parsed and saved to offline cache`() = runBlocking {
        backend.enqueueJson(homeJson)

        val data = backend.shop.home().getOrThrow()

        assertNull("Dữ liệu mới từ server không phải bản offline", data.cachedAt)
        assertEquals("Áo thun", data.home.flashSale.products.single().name)
        assertEquals("Đang hot", data.home.sections.single().title)
        assertEquals(1, backend.homeCache.saveCount)
        assertEquals(data.home, backend.homeCache.stored?.home)
    }

    @Test
    fun `home falls back to cache when server is down`() = runBlocking {
        val cached = HomeResponse(sections = listOf(HomeSection("hot", "Đang hot", listOf(Product(9, "sp-9", "Cache", price = 1, originalPrice = 1)))))
        backend.homeCache.stored = CachedHome(cached, savedAt = 1_700_000_000_000)
        backend.enqueueJson("""{"message": "Server Error"}""", code = 503)

        val data = backend.shop.home().getOrThrow()

        assertEquals(1_700_000_000_000, data.cachedAt)
        assertEquals("Cache", data.home.sections.single().products.single().name)
    }

    @Test
    fun `home without cache reports the network error`() = runBlocking {
        backend.enqueueJson("""{"message": "Server Error"}""", code = 500)

        val error = backend.shop.home().exceptionOrNull()

        assertTrue(error is ApiException)
        assertEquals(500, (error as ApiException).code)
    }

    @Test
    fun `login stores token and following requests send it as Bearer`() = runBlocking {
        backend.enqueueJson("""{"token": "tok-123", "user": {"id": 5, "name": "An", "email": "an@example.com", "role": "admin"}}""")
        backend.enqueueJson("""{"items": [], "items_count": 0, "subtotal": 0}""") // refreshCartCount sau khi đăng nhập

        val user = backend.shop.login("an@example.com", "secret").getOrThrow()

        assertEquals("admin", user.role)
        assertEquals("tok-123", backend.tokens.currentToken)
        backend.server.takeRequest() // login
        assertEquals("Bearer tok-123", backend.server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `server 401 clears the saved token`() = runBlocking {
        backend.tokens.save("expired")
        backend.enqueueJson("""{"message": "Unauthenticated."}""", code = 401)

        val error = backend.shop.order(1).exceptionOrNull() as ApiException

        assertEquals("Vui lòng đăng nhập để tiếp tục.", error.message)
        assertNull(backend.tokens.currentToken)
    }
}
