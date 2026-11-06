package com.socialshop.app

import android.app.Application
import com.socialshop.app.data.ShopRepository
import com.socialshop.app.data.local.TokenStore
import com.socialshop.app.data.remote.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** DI thủ công: một repository dùng chung cho toàn app. */
class ShopApp : Application() {

    lateinit var repository: ShopRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val tokenStore = TokenStore(this, appScope)
        repository = ShopRepository(NetworkModule.createApi(tokenStore), tokenStore)
    }
}
