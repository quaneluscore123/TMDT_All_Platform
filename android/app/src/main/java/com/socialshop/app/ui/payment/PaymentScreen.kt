package com.socialshop.app.ui.payment

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.socialshop.app.BuildConfig
import com.socialshop.app.core.resolveBackendUrl
import com.socialshop.app.ui.components.BackTopBar
import java.net.URI

/**
 * Cổng VNPay chạy trong WebView. Sau khi khách thanh toán/hủy, VNPay chuyển về
 * {APP_URL}/payment/vnpay/return — server cập nhật đơn ngay tại đó, rồi chuyển tiếp sang trang
 * đơn hàng của website. Bắt được trang đích đó (hoặc trang kết quả) là đóng WebView, quay về
 * màn hình chi tiết đơn để tải lại trạng thái.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PaymentScreen(url: String, onFinished: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var returned by remember { mutableStateOf(false) }
    val backendHost = remember { URI(BuildConfig.BASE_URL).host }

    fun isBackendPage(target: String): Boolean = runCatching { URI(target).host == backendHost }.getOrDefault(false)

    BackHandler { onFinished() }

    Scaffold(
        topBar = {
            BackTopBar("Thanh toán VNPay", onBack = onFinished) {
                TextButton(onClick = onFinished) { Text(if (returned) "Xong" else "Đóng") }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val target = request.url.toString()
                                // APP_URL local (localhost) → đổi sang host backend mà thiết bị truy cập được
                                val host = request.url.host
                                if (host == "localhost" || host == "127.0.0.1") {
                                    view.loadUrl(target.resolveBackendUrl() ?: target)
                                    return true
                                }
                                // Server đã xử lý /payment/vnpay/return và chuyển tiếp → kết thúc
                                if (returned && isBackendPage(target) && !target.contains("/payment/vnpay/")) {
                                    onFinished()
                                    return true
                                }
                                return false
                            }

                            override fun onPageStarted(view: WebView, pageUrl: String, favicon: Bitmap?) {
                                loading = true
                                if (pageUrl.contains("/payment/vnpay/return")) returned = true
                            }

                            override fun onPageFinished(view: WebView, pageUrl: String) {
                                loading = false
                            }
                        }
                        loadUrl(url)
                    }
                },
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}
