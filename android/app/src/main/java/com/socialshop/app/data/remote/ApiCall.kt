package com.socialshop.app.data.remote

import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

/** Lỗi từ API đã chuyển thành thông điệp tiếng Việt hiển thị được cho người dùng. */
class ApiException(
    override val message: String,
    val code: Int = 0,
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    val currentTotal: Long? = null,
) : Exception(message) {
    fun fieldError(field: String): String? = fieldErrors[field]?.firstOrNull()
}

/** Gọi API, gom mọi lỗi (mạng, HTTP, JSON) thành [Result] chứa [ApiException]. */
suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    Result.failure(e.toApiException())
} catch (e: IOException) {
    Result.failure(ApiException("Không kết nối được máy chủ. Kiểm tra mạng và thử lại."))
} catch (e: Exception) {
    Result.failure(ApiException(e.message ?: "Đã có lỗi xảy ra."))
}

private fun HttpException.toApiException(): ApiException {
    val raw = response()?.errorBody()?.string()
    val body = raw?.let { runCatching { NetworkModule.json.decodeFromString(ApiErrorBody.serializer(), it) }.getOrNull() }
    val fallback = when (code()) {
        401 -> "Vui lòng đăng nhập để tiếp tục."
        403 -> "Bạn không có quyền thực hiện thao tác này."
        404 -> "Không tìm thấy dữ liệu."
        429 -> "Bạn thao tác quá nhanh. Vui lòng thử lại sau ít phút."
        in 500..599 -> "Máy chủ đang gặp sự cố. Vui lòng thử lại sau."
        else -> "Đã có lỗi xảy ra (${code()})."
    }
    val message = body?.errors?.values?.firstOrNull()?.firstOrNull()
        ?: body?.message?.takeIf { it.isNotBlank() && it != "Unauthenticated." && !it.startsWith("Too Many") }
        ?: fallback

    return ApiException(
        message = message,
        code = code(),
        fieldErrors = body?.errors.orEmpty(),
        currentTotal = body?.currentTotal,
    )
}
