package com.socialshop.app.di

import android.content.Context
import com.socialshop.app.data.ShopRepository
import com.socialshop.app.data.local.TokenStore
import com.socialshop.app.data.remote.AdminApiService
import com.socialshop.app.data.remote.ApiService
import com.socialshop.app.data.remote.NetworkModule
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Scope sống cùng tiến trình app (ghi DataStore khi interceptor xóa token...). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context, @ApplicationScope scope: CoroutineScope): TokenStore =
        TokenStore(context, scope)

    @Provides
    @Singleton
    fun provideJson(): Json = NetworkModule.json

    @Provides
    @Singleton
    fun provideOkHttp(tokenStore: TokenStore): OkHttpClient = NetworkModule.createOkHttp(tokenStore)

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit = NetworkModule.createRetrofit(client)

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)

    @Provides
    @Singleton
    fun provideAdminApiService(retrofit: Retrofit): AdminApiService = retrofit.create(AdminApiService::class.java)
}

/** Cho các composable không có ViewModel riêng (gốc điều hướng, màn tài khoản) lấy repository từ Hilt. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepositoryEntryPoint {
    fun shopRepository(): ShopRepository
}
