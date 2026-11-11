package com.socialshop.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.socialshop.app.core.toDisplayDate
import com.socialshop.app.core.toVnd
import com.socialshop.app.data.AdminRepository
import com.socialshop.app.data.remote.AdminCoupon
import com.socialshop.app.data.remote.ApiException
import com.socialshop.app.data.remote.CouponCreateRequest
import com.socialshop.app.data.remote.CouponStats
import com.socialshop.app.data.remote.Paged
import com.socialshop.app.ui.components.BackTopBar
import com.socialshop.app.ui.navigation.LocalSnackbar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private val COUPON_FILTERS = listOf(null to "Tất cả", "active" to "Đang bật", "inactive" to "Đang tắt", "expired" to "Hết hạn")

fun AdminCoupon.describe(): String {
    val discount = if (type == "percent") {
        "Giảm $value%" + (maxDiscount?.let { " (tối đa ${it.toVnd()})" } ?: "")
    } else {
        "Giảm ${value.toVnd()}"
    }
    return if (minOrderAmount > 0) "$discount · đơn từ ${minOrderAmount.toVnd()}" else discount
}

// ─── Danh sách mã ───────────────────────────────────────────────────────────────

@HiltViewModel
class AdminCouponsViewModel @Inject constructor(private val repo: AdminRepository) : PagedAdminViewModel<AdminCoupon>() {
    var status by mutableStateOf<String?>(null)
        private set
    var stats by mutableStateOf(CouponStats())
        private set

    init { reload() }

    override suspend fun fetch(page: Int): Result<Paged<AdminCoupon>> =
        repo.coupons(status, page).onSuccess { stats = it.stats }.map { Paged(it.data, it.meta) }

    override fun idOf(item: AdminCoupon) = item.id

    fun selectStatus(value: String?) {
        status = value
        reload()
    }

    fun toggle(coupon: AdminCoupon) = perform({ repo.toggleCoupon(coupon.id) }, { it.message }) { replace(it.coupon) }

    fun delete(coupon: AdminCoupon) = perform({ repo.deleteCoupon(coupon.id) }, { it.message ?: "Đã xóa mã giảm giá." }) { remove(coupon.id) }
}

@Composable
fun AdminCouponsScreen(created: Boolean, onBack: () -> Unit, onCreate: () -> Unit) {
    val vm: AdminCouponsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<AdminCoupon?>(null) }
    CollectMessages(vm.messages)
    // Vừa tạo mã ở màn con → tải lại để mã mới hiện đầu danh sách
    LaunchedEffect(created) { if (created) vm.reload() }

    Scaffold(
        topBar = { BackTopBar("Mã giảm giá", onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreate, icon = { Icon(Icons.Filled.Add, contentDescription = null) }, text = { Text("Tạo mã") })
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.padding(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Tổng", vm.stats.total.toString(), Modifier.weight(1f))
                StatCard("Đang bật", vm.stats.active.toString(), Modifier.weight(1f))
                StatCard("Đã dùng", vm.stats.used.toString(), Modifier.weight(1f))
            }
            FilterRow(COUPON_FILTERS, vm.status, vm::selectStatus)
            Spacer(Modifier.height(4.dp))
            PagedAdminList(
                state = state,
                onRetry = vm::reload,
                onLoadMore = vm::loadMore,
                emptyMessage = "Chưa có mã giảm giá nào.",
                key = { it.id },
            ) { coupon -> CouponRow(coupon, onToggle = { vm.toggle(coupon) }, onDelete = { deleting = coupon }) }
        }
    }

    deleting?.let { coupon ->
        ConfirmDialog(
            title = "Xóa mã ${coupon.code}?",
            message = "Khách sẽ không dùng được mã này nữa. Lịch sử đơn đã dùng mã vẫn được giữ.",
            confirmLabel = "Xóa",
            destructive = true,
            onConfirm = { vm.delete(coupon) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun CouponRow(coupon: AdminCoupon, onToggle: () -> Unit, onDelete: () -> Unit) {
    AdminCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(coupon.code, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                    if (coupon.isExpired) Pill("Hết hạn", PillRed)
                }
                Text(coupon.describe(), style = MaterialTheme.typography.bodyMedium)
                Text(
                    buildString {
                        append("Đã dùng ${coupon.usedCount}")
                        append(coupon.usageLimit?.let { "/$it" } ?: " (không giới hạn)")
                        coupon.endAt?.let { append(" · hết hạn ${it.toDisplayDate().substringBefore(' ')}") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(checked = coupon.status == "active", onCheckedChange = { onToggle() })
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Xóa mã", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

// ─── Tạo mã mới ─────────────────────────────────────────────────────────────────

@HiltViewModel
class AdminCouponCreateViewModel @Inject constructor(private val repo: AdminRepository) : ViewModel() {
    var code by mutableStateOf("")
    var type by mutableStateOf("percent")
    var value by mutableStateOf("")
    var maxDiscount by mutableStateOf("")
    var minOrder by mutableStateOf("0")
    var validDays by mutableStateOf("30")
    var usageLimit by mutableStateOf("")
    var active by mutableStateOf(true)
    var errors by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var saving by mutableStateOf(false)
        private set

    suspend fun save(): Result<AdminCoupon> {
        val local = buildMap {
            if (!code.trim().matches(Regex("[A-Za-z0-9_-]{1,50}"))) put("code", "Mã chỉ gồm chữ, số, gạch ngang/dưới (tối đa 50 ký tự).")
            val v = value.toLongOrNull()
            when {
                v == null || v < 1 -> put("value", "Nhập mức giảm.")
                type == "percent" && v > 100 -> put("value", "Phần trăm giảm không được quá 100%")
                type == "fixed" && v < 1000 -> put("value", "Số tiền giảm tối thiểu 1,000₫")
            }
        }
        if (local.isNotEmpty()) {
            errors = local
            return Result.failure(IllegalArgumentException(local.values.first()))
        }

        saving = true
        errors = emptyMap()
        val today = LocalDate.now()
        val request = CouponCreateRequest(
            code = code.trim().uppercase(),
            type = type,
            value = value.toLong(),
            maxDiscount = if (type == "percent") maxDiscount.toLongOrNull() else null,
            minOrderAmount = minOrder.toLongOrNull() ?: 0,
            startAt = today.toString(),
            endAt = validDays.toLongOrNull()?.takeIf { it > 0 }?.let { today.plusDays(it).toString() },
            usageLimit = usageLimit.toIntOrNull()?.takeIf { it > 0 },
            status = if (active) "active" else "inactive",
        )
        val result = repo.createCoupon(request)
            .onFailure { e -> (e as? ApiException)?.let { api -> errors = api.fieldErrors.mapValues { it.value.firstOrNull().orEmpty() } } }
            .map { it.coupon }
        saving = false
        return result
    }
}

@Composable
fun AdminCouponCreateScreen(onBack: () -> Unit, onCreated: () -> Unit) {
    val vm: AdminCouponCreateViewModel = hiltViewModel()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { BackTopBar("Tạo mã giảm giá", onBack) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = vm.code,
                onValueChange = { vm.code = it.uppercase() },
                label = { Text("Mã (vd. SALE50K)") },
                isError = vm.errors["code"] != null,
                supportingText = vm.errors["code"]?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Loại giảm", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = vm.type == "percent", onClick = { vm.type = "percent" }, label = { Text("Theo %") })
                FilterChip(selected = vm.type == "fixed", onClick = { vm.type = "fixed" }, label = { Text("Số tiền cố định") })
            }
            MoneyField(if (vm.type == "percent") "Phần trăm giảm (1–100)" else "Số tiền giảm (₫, tối thiểu 1.000)", vm.value, { vm.value = it }, vm.errors["value"])
            if (vm.type == "percent") {
                MoneyField("Giảm tối đa (₫) — để trống nếu không giới hạn", vm.maxDiscount, { vm.maxDiscount = it }, vm.errors["max_discount"])
            }
            MoneyField("Đơn tối thiểu (₫)", vm.minOrder, { vm.minOrder = it }, vm.errors["min_order_amount"])
            MoneyField("Hiệu lực (số ngày kể từ hôm nay) — để trống nếu không hết hạn", vm.validDays, { vm.validDays = it }, vm.errors["end_at"])
            MoneyField("Số lượt dùng tối đa — để trống nếu không giới hạn", vm.usageLimit, { vm.usageLimit = it }, vm.errors["usage_limit"])
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bật mã ngay", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = vm.active, onCheckedChange = { vm.active = it })
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        vm.save().fold(
                            onSuccess = { snackbar.showSnackbar("Đã tạo mã ${it.code}."); onCreated() },
                            onFailure = { snackbar.showSnackbar(it.message ?: "Không tạo được mã.") },
                        )
                    }
                },
                enabled = !vm.saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (vm.saving) "Đang tạo…" else "Tạo mã") }
        }
    }
}
