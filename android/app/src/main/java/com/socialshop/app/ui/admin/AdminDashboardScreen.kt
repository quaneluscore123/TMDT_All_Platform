package com.socialshop.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.socialshop.app.core.toDisplayDate
import com.socialshop.app.core.toVnd
import com.socialshop.app.data.AdminRepository
import com.socialshop.app.data.remote.AdminDashboard
import com.socialshop.app.data.remote.AdminOrder
import com.socialshop.app.data.remote.DailyRevenue
import com.socialshop.app.ui.components.BackTopBar
import com.socialshop.app.ui.components.StateContent
import com.socialshop.app.ui.components.UiState
import com.socialshop.app.ui.components.toUiState
import com.socialshop.app.ui.orders.StatusChip
import com.socialshop.app.ui.theme.Orange500
import com.socialshop.app.ui.theme.SaleRed
import com.socialshop.app.ui.theme.SuccessGreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(private val repo: AdminRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<AdminDashboard>>(UiState.Loading)
    val state = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    init { load() }

    fun load(pull: Boolean = false) = viewModelScope.launch {
        if (pull) _refreshing.value = true else _state.value = UiState.Loading
        _state.value = repo.dashboard().toUiState()
        _refreshing.value = false
    }
}

/** Điều hướng từ màn tổng quan tới các khu vực quản trị. */
data class AdminNav(
    val orders: (status: String?) -> Unit,
    val order: (id: Long) -> Unit,
    val products: (filter: String?) -> Unit,
    val reviews: () -> Unit,
    val users: () -> Unit,
    val coupons: () -> Unit,
    val tax: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(onBack: () -> Unit, nav: AdminNav) {
    val vm: AdminDashboardViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            BackTopBar("Quản trị cửa hàng", onBack) {
                IconButton(onClick = { vm.load(pull = true) }) { Icon(Icons.Filled.Refresh, contentDescription = "Tải lại") }
            }
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.load(pull = true) }, modifier = Modifier.padding(padding).fillMaxSize()) {
            StateContent(state, onRetry = { vm.load() }) { data -> DashboardContent(data, nav) }
        }
    }
}

@Composable
private fun DashboardContent(data: AdminDashboard, nav: AdminNav) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Doanh thu tháng này", data.revenueMonth.toVnd(), Modifier.weight(1f), hint = "${data.ordersMonth} đơn · không tính đơn hủy", accent = SuccessGreen)
            StatCard(
                "${data.tax.label} tháng này",
                data.taxMonth.toVnd(),
                Modifier.weight(1f),
                hint = if (data.tax.enabled) "Thuế suất ${data.tax.rate.formatRate()}%" else "Đang tắt thu thuế",
                onClick = nav.tax,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Đơn hôm nay", data.ordersToday.toString(), Modifier.weight(1f), onClick = { nav.orders(null) })
            StatCard(
                "Chờ xử lý",
                data.pendingOrders.toString(),
                Modifier.weight(1f),
                hint = "đơn cần xác nhận",
                accent = if (data.pendingOrders > 0) Orange500 else null,
                onClick = { nav.orders("pending") },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                "Đánh giá chờ duyệt",
                data.pendingReviews.toString(),
                Modifier.weight(1f),
                accent = if (data.pendingReviews > 0) Orange500 else null,
                onClick = nav.reviews,
            )
            StatCard(
                "Sắp hết hàng",
                data.lowStockProducts.toString(),
                Modifier.weight(1f),
                hint = "tồn ≤ ${data.lowStockThreshold}",
                accent = if (data.lowStockProducts > 0) SaleRed else null,
                onClick = { nav.products("low_stock") },
            )
        }

        AdminCard {
            Text("Doanh thu 7 ngày", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            RevenueBars(data.revenue7Days)
        }

        AdminCard {
            MenuItem(Icons.AutoMirrored.Filled.List, "Đơn hàng", "${data.ordersMonth} đơn trong tháng") { nav.orders(null) }
            HorizontalDivider()
            MenuItem(Icons.Filled.ShoppingCart, "Sản phẩm", "${data.totalProducts} sản phẩm") { nav.products(null) }
            HorizontalDivider()
            MenuItem(Icons.Filled.ThumbUp, "Đánh giá", "${data.pendingReviews} chờ duyệt", nav.reviews)
            HorizontalDivider()
            MenuItem(Icons.Filled.Face, "Người dùng", "${data.totalUsers} tài khoản", nav.users)
            HorizontalDivider()
            MenuItem(Icons.Filled.Star, "Mã giảm giá", "Tạo, bật/tắt mã", nav.coupons)
            HorizontalDivider()
            MenuItem(Icons.Filled.Settings, "Thuế VAT", if (data.tax.enabled) "${data.tax.label} ${data.tax.rate.formatRate()}%" else "Đang tắt", nav.tax)
        }

        if (data.topSelling.isNotEmpty()) {
            SectionTitle("Bán chạy nhất")
            AdminCard {
                data.topSelling.forEachIndexed { index, product ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}.", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = 8.dp))
                        Text(product.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${product.sold} đã bán", style = MaterialTheme.typography.labelLarge)
                            Text(product.revenue.toVnd(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (data.recentOrders.isNotEmpty()) {
            SectionTitle("Đơn mới nhất")
            data.recentOrders.forEach { order -> AdminOrderRow(order) { nav.order(order.id) } }
        }
    }
}

@Composable
private fun RevenueBars(days: List<DailyRevenue>) {
    val max = days.maxOfOrNull { it.total }?.takeIf { it > 0 } ?: 1L
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { day ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(day.total.compactVnd(), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Box(
                    Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth()
                        .height((100f * day.total / max).dp.coerceAtLeast(2.dp))
                        .background(if (day.total > 0) Orange500 else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
                )
                Text(day.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    // Cả dòng bấm được (không chỉ icon) để dễ chạm trên điện thoại
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = title, onClick = onClick).padding(vertical = 14.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
fun AdminOrderRow(order: AdminOrder, onClick: () -> Unit) {
    AdminCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(order.orderCode, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            StatusChip(order.status, order.statusLabel)
        }
        Spacer(Modifier.size(4.dp))
        Text(
            listOfNotNull(order.customer?.name ?: order.shippingName, order.createdAt.toDisplayDate()).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${order.paymentMethod.uppercase()} · ${order.paymentStatusLabel}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Text(order.total.toVnd(), style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** 8.0 → "8", 8.5 → "8.5" */
fun Double.formatRate(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

/** 1250000 → "1,3tr", 45000 → "45k" (nhãn ngắn cho biểu đồ). */
private fun Long.compactVnd(): String = when {
    this >= 1_000_000 -> "%.1ftr".format(this / 1_000_000.0).replace('.', ',').replace(",0tr", "tr")
    this >= 1_000 -> "${this / 1_000}k"
    this == 0L -> "0"
    else -> toString()
}
