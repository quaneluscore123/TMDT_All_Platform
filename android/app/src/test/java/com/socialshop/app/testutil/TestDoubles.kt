package com.socialshop.app.testutil

import com.socialshop.app.data.AdminRepository
import com.socialshop.app.data.ChatRepository
import com.socialshop.app.data.ShopRepository
import com.socialshop.app.data.local.TokenStorage
import com.socialshop.app.data.local.cache.CachedHome
import com.socialshop.app.data.local.cache.HomeCache
import com.socialshop.app.data.remote.AdminApiService
import com.socialshop.app.data.remote.ApiService
import com.socialshop.app.data.remote.HomeResponse
import com.socialshop.app.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/** Token trong bộ nhớ, sẵn sàng ngay. */
class FakeTokenStorage(initial: String? = null) : TokenStorage {
    private val _token = MutableStateFlow(initial)
    override val token: StateFlow<String?> = _token
    override val currentToken: String? get() = _token.value
    override val ready: StateFlow<Boolean> = MutableStateFlow(true)
    override suspend fun awaitReady() = Unit
    override fun awaitReadyBlocking(timeoutMs: Long) = Unit
    override suspend fun save(token: String) { _token.value = token }
    override suspend fun clear() { _token.value = null }
    override fun clearBlocking() { _token.value = null }
}

/** Cache trang chủ trong bộ nhớ thay cho Room. */
class FakeHomeCache(var stored: CachedHome? = null) : HomeCache {
    var saveCount = 0
        private set

    override suspend fun save(home: HomeResponse, now: Long) {
        saveCount++
        stored = CachedHome(home, now)
    }

    override suspend fun load(): CachedHome? = stored
}

/**
 * Backend giả (MockWebServer) + các lớp thật của app nối vào nó: OkHttp (có interceptor gắn token),
 * Retrofit với đúng Json snake_case của app, repository thật.
 */
class TestBackend(val tokens: FakeTokenStorage = FakeTokenStorage(), val homeCache: FakeHomeCache = FakeHomeCache()) {
    val server = MockWebServer().apply { start() }
    private val baseUrl = server.url("/api/v1/")
    private val client = NetworkModule.createOkHttp(tokens)
    private val retrofit = NetworkModule.createRetrofit(client, baseUrl.toString())

    val api: ApiService = retrofit.create(ApiService::class.java)
    val adminApi: AdminApiService = retrofit.create(AdminApiService::class.java)

    val shop = ShopRepository(api, tokens, homeCache)
    val admin = AdminRepository(adminApi)
    val chat = ChatRepository(client, NetworkModule.json, baseUrl)

    fun enqueueJson(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body.trimIndent()))
    }

    fun enqueueSse(vararg events: String) {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody(events.joinToString("") { "data: $it\n\n" }),
        )
    }

    fun shutdown() = server.shutdown()
}

/** Chờ (thời gian thật) tới khi điều kiện đúng — request đi qua luồng thật của OkHttp. */
suspend fun eventually(timeoutMs: Long = 5_000, condition: () -> Boolean) {
    withContext(Dispatchers.Default) {
        withTimeout(timeoutMs) {
            while (!condition()) delay(10)
        }
    }
}

/** JSON một sản phẩm tối thiểu theo đúng định dạng ProductResource của backend. */
fun productJson(id: Long, name: String, price: Long = 100_000) = """
    {"id": $id, "slug": "sp-$id", "name": "$name", "price": $price, "original_price": $price,
     "is_on_sale": false, "discount_percent": 0, "image_url": null, "rating": 4.5, "reviews_count": 2,
     "stock": 10, "has_variants": false, "category": {"id": 1, "name": "Áo", "slug": "ao"}}
"""
