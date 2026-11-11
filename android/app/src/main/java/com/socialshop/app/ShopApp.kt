package com.socialshop.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Gốc đồ thị Hilt: các singleton (mạng, TokenStore, repository) khai báo trong di/AppModule. */
@HiltAndroidApp
class ShopApp : Application()
