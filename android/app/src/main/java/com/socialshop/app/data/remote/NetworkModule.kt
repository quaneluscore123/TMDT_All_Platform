package com.socialshop.app.data.remote

import com.socialshop.app.BuildConfig
import com.socialshop.app.data.local.TokenStore
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
        isLenient = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    /** Gốc API, vd. http://10.0.2.2:8000/api/v1/ */
    val apiBaseUrl: String = BuildConfig.BASE_URL + "api/v1/"

    fun createApi(tokenStore: TokenStore): ApiService {
        val authInterceptor = Interceptor { chain ->
            val token = tokenStore.currentToken
            val request = chain.request().newBuilder()
                // Laravel trả lỗi dạng JSON (401/422) thay vì redirect sang trang HTML
                .header("Accept", "application/json")
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .build()

            val response = chain.proceed(request)
            // Token bị thu hồi / hết hạn → xóa để app quay về trạng thái khách
            if (response.code == 401 && token != null) tokenStore.clearBlocking()
            response
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                }
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(apiBaseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}
