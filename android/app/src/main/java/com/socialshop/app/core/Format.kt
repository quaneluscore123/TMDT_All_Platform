package com.socialshop.app.core

import com.socialshop.app.BuildConfig
import java.net.URI
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val vndFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"))

/** 400000 → "400.000₫" (giống website). */
fun Long.toVnd(): String = vndFormat.format(this) + "₫"

private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

fun String?.toDisplayDate(): String = this?.let {
    runCatching { OffsetDateTime.parse(it).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(dateFormat) }.getOrNull()
} ?: ""

private val backendOrigin: String = URI(BuildConfig.BASE_URL).let { "${it.scheme}://${it.rawAuthority}" }

/**
 * Backend sinh URL ảnh theo APP_URL (thường là http://localhost:8000 khi chạy local), emulator không
 * truy cập được → đổi origin localhost/127.0.0.1 sang origin của BASE_URL.
 */
fun String?.resolveBackendUrl(): String? {
    if (this.isNullOrBlank()) return null
    return runCatching {
        val uri = URI(this)
        if (uri.host == "localhost" || uri.host == "127.0.0.1") {
            backendOrigin + this.removePrefix("${uri.scheme}://${uri.rawAuthority}")
        } else {
            this
        }
    }.getOrDefault(this)
}
