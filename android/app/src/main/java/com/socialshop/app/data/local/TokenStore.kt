package com.socialshop.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "session")

/**
 * Lưu Bearer token Sanctum. Giữ bản sao trong bộ nhớ để OkHttp interceptor đọc đồng bộ.
 */
class TokenStore(private val context: Context, private val scope: CoroutineScope) {

    private val key = stringPreferencesKey("api_token")
    private val _token = MutableStateFlow<String?>(null)

    val token: StateFlow<String?> = _token.asStateFlow()

    @Volatile
    var currentToken: String? = null
        private set

    init {
        // Nạp token đã lưu trước khi gọi API đầu tiên (đọc DataStore rất nhanh)
        val saved = runBlocking { context.dataStore.data.first()[key] }
        currentToken = saved
        _token.value = saved
    }

    suspend fun save(token: String) {
        currentToken = token
        _token.value = token
        context.dataStore.edit { it[key] = token }
    }

    suspend fun clear() {
        currentToken = null
        _token.value = null
        context.dataStore.edit { it.remove(key) }
    }

    /** Dùng từ luồng OkHttp khi server trả 401. */
    fun clearBlocking() {
        currentToken = null
        _token.value = null
        scope.launch { context.dataStore.edit { it.remove(key) } }
    }
}
