package com.socialshop.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private val Context.dataStore by preferencesDataStore(name = "session")

/**
 * Lưu Bearer token Sanctum trong DataStore, **đã mã hóa** bằng khóa Android Keystore ([TokenCipher]).
 *
 * Đọc token đã lưu **bất đồng bộ** khi khởi tạo (không chặn luồng chính lúc mở app); UI chờ [ready],
 * OkHttp chờ [awaitReadyBlocking] trên luồng nền. Token dạng cũ (chưa mã hóa) được tự chuyển sang dạng mã hóa.
 */
class TokenStore(
    private val context: Context,
    private val scope: CoroutineScope,
    private val cipher: TokenCipher = TokenCipher(),
) : TokenStorage {

    private val encryptedKey = stringPreferencesKey("api_token_enc")
    private val legacyKey = stringPreferencesKey("api_token")

    private val _token = MutableStateFlow<String?>(null)
    override val token: StateFlow<String?> = _token.asStateFlow()

    @Volatile
    override var currentToken: String? = null
        private set

    private val _ready = MutableStateFlow(false)
    override val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val loaded = CompletableDeferred<Unit>()
    private val loadedLatch = CountDownLatch(1)

    init {
        scope.launch {
            try {
                val prefs = context.dataStore.data.first()
                val saved = prefs[encryptedKey]?.let { stored ->
                    cipher.decrypt(stored) ?: run {
                        // Khóa Keystore không còn (vd. khôi phục backup sang máy khác) → bỏ token, đăng nhập lại
                        context.dataStore.edit { it.remove(encryptedKey) }
                        null
                    }
                } ?: prefs[legacyKey]?.also { plain ->
                    // Bản app cũ lưu token dạng rõ → mã hóa lại và xóa bản rõ
                    context.dataStore.edit {
                        it[encryptedKey] = cipher.encrypt(plain)
                        it.remove(legacyKey)
                    }
                }
                currentToken = saved
                _token.value = saved
            } finally {
                markReady()
            }
        }
    }

    private fun markReady() {
        _ready.value = true
        loaded.complete(Unit)
        loadedLatch.countDown()
    }

    override suspend fun awaitReady() = loaded.await()

    override fun awaitReadyBlocking(timeoutMs: Long) {
        loadedLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    override suspend fun save(token: String) {
        currentToken = token
        _token.value = token
        context.dataStore.edit {
            it[encryptedKey] = cipher.encrypt(token)
            it.remove(legacyKey)
        }
    }

    override suspend fun clear() {
        currentToken = null
        _token.value = null
        context.dataStore.edit {
            it.remove(encryptedKey)
            it.remove(legacyKey)
        }
    }

    override fun clearBlocking() {
        currentToken = null
        _token.value = null
        scope.launch { clear() }
    }
}
