package com.socialshop.app.data

import com.socialshop.app.data.remote.ApiErrorBody
import com.socialshop.app.data.remote.ApiException
import com.socialshop.app.data.remote.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Một lượt hội thoại gửi lên server làm ngữ cảnh: type = "user" | "bot". */
@Serializable
data class ChatTurn(val type: String, val content: String)

@Serializable
private data class ChatRequest(val message: String, val history: List<ChatTurn>)

/**
 * Trợ lý AI: POST /api/v1/chat trả về text/event-stream ("data: {json}" mỗi dòng). Đọc từng dòng và
 * phát ra từng đoạn chữ để UI hiện dần như đang gõ. Có token thì OkHttp tự gắn → trợ lý tra được đơn của khách.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {
    fun ask(message: String, history: List<ChatTurn>): Flow<String> = flow {
        val body = json.encodeToString(ChatRequest.serializer(), ChatRequest(message, history.takeLast(20)))
        val request = Request.Builder()
            .url(NetworkModule.apiBaseUrl + "chat")
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val call = client.newCall(request)
        // Rời màn chat / hủy lượt hỏi → hủy luôn request đang đọc dở
        currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        val response = try {
            call.execute()
        } catch (e: IOException) {
            throw ApiException("Không kết nối được máy chủ. Kiểm tra mạng và thử lại.")
        }

        response.use {
            if (!it.isSuccessful) throw it.toApiException()

            val source = it.body?.source() ?: return@use
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = try {
                    source.readUtf8Line()
                } catch (e: IOException) {
                    break // mất kết nối giữa chừng: giữ phần đã nhận
                } ?: break

                if (!line.startsWith("data: ")) continue
                val event = runCatching { json.parseToJsonElement(line.removePrefix("data: ")) as JsonObject }.getOrNull() ?: continue

                event["text"]?.jsonPrimitive?.contentOrNull?.let { text -> emit(text) }
                if (event["done"]?.jsonPrimitive?.booleanOrNull == true) break
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun okhttp3.Response.toApiException(): ApiException {
        val parsed = body?.string()?.let { raw ->
            runCatching { NetworkModule.json.decodeFromString(ApiErrorBody.serializer(), raw) }.getOrNull()
        }
        val message = parsed?.errors?.values?.firstOrNull()?.firstOrNull()
            ?: parsed?.message?.takeIf { it.isNotBlank() && !it.startsWith("Too Many") }
            ?: when (code) {
                429 -> "Bạn hỏi hơi nhanh. Vui lòng đợi một chút rồi hỏi tiếp."
                in 500..599 -> "Trợ lý đang bận. Vui lòng thử lại sau."
                else -> "Không gửi được câu hỏi ($code)."
            }
        return ApiException(message, code)
    }
}
