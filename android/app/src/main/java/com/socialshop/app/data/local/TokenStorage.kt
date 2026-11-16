package com.socialshop.app.data.local

import kotlinx.coroutines.flow.StateFlow

/**
 * Nơi giữ Bearer token Sanctum. Tách interface để repository và OkHttp không phụ thuộc Android
 * (unit test dùng bản giả trong bộ nhớ).
 */
interface TokenStorage {
    /** Token hiện tại, null = khách. Chỉ đúng sau khi [awaitReady] trả về. */
    val token: StateFlow<String?>

    /** Bản sao đọc đồng bộ cho OkHttp interceptor. */
    val currentToken: String?

    /** Đã đọc xong token đã lưu chưa (UI chờ cờ này rồi mới hiện nội dung cần phiên đăng nhập). */
    val ready: StateFlow<Boolean>

    /** Chờ tới khi đọc xong token đã lưu (không chặn luồng). */
    suspend fun awaitReady()

    /** Dùng trên luồng nền của OkHttp: chờ tối đa [timeoutMs] để không gửi request như khách khi app vừa mở. */
    fun awaitReadyBlocking(timeoutMs: Long)

    suspend fun save(token: String)

    suspend fun clear()

    /** Dùng từ luồng OkHttp khi server trả 401. */
    fun clearBlocking()
}
