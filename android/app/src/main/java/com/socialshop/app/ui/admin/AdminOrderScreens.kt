package com.socialshop.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.socialshop.app.core.resolveBackendUrl
import com.socialshop.app.core.toDisplayDate
import com.socialshop.app.core.toVnd
import com.socialshop.app.data.AdminRepository
import com.socialshop.app.data.remote.AdminOrder
import com.socialshop.app.data.remote.StatusOption
import com.socialshop.app.ui.components.BackTopBar
import com.socialshop.app.ui.components.StateContent
import com.socialshop.app.ui.components.SummaryLine
import com.socialshop.app.ui.components.UiState
import com.socialshop.app.ui.components.toUiState
import com.socialshop.app.ui.navigation.LocalSnackbar
import com.socialshop.app.ui.orders.StatusChip
import com.socialshop.app.ui.theme.SuccessGreen
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val ORDER_FILTERS = listOf(
    null to "Tất cả",
    "pending" to "Chờ xử lý",
    "confirmed" to "Đã xác nhận",
    "shipping" to "Đang giao",
    "delivered" to "Đã giao",
    "cancelled" to "Đã hủy",
)

// ─── Danh sách đơn ──────────────────────────────────────────────────────────────

@HiltViewModel(assistedFactory = AdminOrdersViewModel.Factory::class)
class AdminOrdersViewModel @AssistedInject constructor(
    private val repo: AdminRepository,
    @Assisted initialStatus: String?,
) : PagedAdminViewModel<AdminOrder>() {
    @AssistedFactory
    interface Factory {
        fun create(initialStatus: String?): AdminOrdersViewModel
    }

    var status by mutableStateOf(initialStatus)
        private set
    var search by mutableStateOf("")

    init { reload() }

    override suspend fun fetch(page: Int) = repo.orders(search, status, page)

    override fun idOf(item: AdminOrder) = item.id

    fun selectStatus(value: String?) {
        status = value
        reload()
    }
}

@Composable
fun AdminOrdersScreen(initialStatus: String?, onBack: () -> Unit, onOrder: (Long) -> Unit) {
    val vm = hiltViewModel<AdminOrdersViewModel, AdminOrdersViewModel.Factory>(key = "admin-orders-$initialStatus") { it.create(initialStatus) }
    val state by vm.state.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)

    Scaffold(topBar = { BackTopBar("Quản lý đơn hàng", onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(vm.search, { vm.search = it }, "Mã đơn hoặc tên người nhận", onSearch = vm::reload)
            FilterRow(ORDER_FILTERS, vm.status, vm::selectStatus)
            Spacer(Modifier.height(4.dp))
            PagedAdminList(
                state = state,
                onRetry = vm::reload,
                onLoadMore = vm::loadMore,
                emptyMessage = "Không có đơn hàng nào.",
                key = { it.id },
                header = { page -> item { Text("${page.total} đơn hàng", style = MaterialTheme.typography.bodySmall) } },
            ) { order -> AdminOrderRow(order) { onOrder(order.id) } }
        }
    }
}

// ─── Chi tiết đơn & đổi trạng thái ──────────────────────────────────────────────

@HiltViewModel(assistedFactory = AdminOrderDetailViewModel.Factory::class)
class AdminOrderDetailViewModel @AssistedInject constructor(
    private val repo: AdminRepository,
    @Assisted private val orderId: Long,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(orderId: Long): AdminOrderDetailViewModel
    }

    private val _state = MutableStateFlow<UiState<AdminOrder>>(UiState.Loading)
    val state = _state.asStateFlow()

    var updating by mutableStateOf(false)
        private set

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = UiState.Loading
        _state.value = repo.order(orderId).toUiState()
    }

    suspend fun changeStatus(status: String): Result<String> {
        updating = true
        val result = repo.updateOrderStatus(orderId, status)
            .onSuccess { _state.value = UiState.Success(it.order) }
            .map { it.message ?: "Đã cập nhật trạng thái." }
        updating = false
        return result
    }
}

@Composable
fun AdminOrderDetailScreen(orderId: Long, onBack: () -> Unit) {
    val vm = hiltViewModel<AdminOrderDetailViewModel, AdminOrderDetailViewModel.Factory>(key = "admin-order-$orderId") { it.create(orderId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var confirm by remember { mutableStateOf<StatusOption?>(null) }

    Scaffold(topBar = { BackTopBar("Chi tiết đơn hàng", onBack) }) { padding ->
        Box(Modifier.padding(padding)) {
            StateContent(state, onRetry = { vm.load() }) { order ->
                OrderDetailContent(order, updating = vm.updating, onStatus = { confirm = it })
            }
        }
    }

    confirm?.let { option ->
        val cancelling = option.value == "cancelled"
        ConfirmDialog(
            title = if (cancelling) "Hủy đơn hàng?" else "Chuyển trạng thái?",
            message = if (cancelling) {
                "Đơn sẽ bị hủy, hàng được hoàn về kho và lượt dùng mã giảm giá được hoàn lại. Không thể mở lại đơn đã hủy."
            } else {
                "Chuyển đơn sang \"${option.label}\". Khách hàng sẽ nhận email thông báo."
            },
            confirmLabel = if (cancelling) "Hủy đơn" else "Xác nhận",
            destructive = cancelling,
            onConfirm = {
                scope.launch {
                    vm.changeStatus(option.value).fold(
                        onSuccess = { snackbar.showSnackbar(it) },
                        onFailure = { snackbar.showSnackbar(it.message ?: "Không cập nhật được trạng thái.") },
                    )
                }
            },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun OrderDetailContent(order: AdminOrder, updating: Boolean, onStatus: (StatusOption) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AdminCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(order.orderCode, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip(order.status, order.statusLabel)
            }
            Text(order.createdAt.toDisplayDate(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Thanh toán: ${order.paymentMethod.uppercase()}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Pill(order.paymentStatusLabel, if (order.paymentStatus == "paid") PillGreen else if (order.paymentStatus == "failed") PillRed else PillAmber)
            }
        }

        if (order.nextStatuses.isNotEmpty()) {
            AdminCard {
                Text("Cập nhật trạng thái", style = MaterialTheme.typography.titleSmall)
                if (order.paymentMethod != "cod" && order.paymentStatus != "paid") {
                    Text(
                        "Đơn thanh toán online chưa nhận tiền — chỉ có thể hủy hoặc giữ chờ xử lý.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    order.nextStatuses.forEach { option ->
                        if (option.value == "cancelled") {
                            OutlinedButton(
                                onClick = { onStatus(option) },
                                enabled = !updating,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) { Text("Hủy đơn") }
                        } else {
                            Button(onClick = { onStatus(option) }, enabled = !updating) { Text(option.label) }
                        }
                    }
                }
            }
        }

        AdminCard {
            Text("Người mua", style = MaterialTheme.typography.titleSmall)
            order.customer?.let {
                Text("${it.name} · ${it.email}", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text("Giao tới", style = MaterialTheme.typography.titleSmall)
            Text("${order.shippingName} · ${order.shippingPhone}", style = MaterialTheme.typography.bodyMedium)
            Text(listOfNotNull(order.shippingAddress, order.shippingProvince).joinToString(", "), style = MaterialTheme.typography.bodyMedium)
            order.note?.takeIf { it.isNotBlank() }?.let { Text("Ghi chú: $it", style = MaterialTheme.typography.bodySmall) }
        }

        AdminCard {
            Text("Sản phẩm (${order.itemsCount})", style = MaterialTheme.typography.titleSmall)
            order.items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp)) else Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = item.imageUrl.resolveBackendUrl(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        item.variantLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text("${item.price.toVnd()} × ${item.quantity}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(item.subtotal.toVnd(), style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        AdminCard {
            SummaryLine("Tạm tính", order.subtotal.toVnd())
            if (order.discount > 0) SummaryLine("Giảm giá", "-" + order.discount.toVnd(), valueColor = SuccessGreen)
            SummaryLine("Phí vận chuyển", if (order.shippingFee == 0L) "Miễn phí" else order.shippingFee.toVnd())
            SummaryLine(
                if (order.taxRate > 0) "Thuế VAT (${order.taxRate.formatRate()}%)" else "Thuế VAT",
                order.taxAmount.toVnd(),
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            SummaryLine("Tổng cộng", order.total.toVnd(), bold = true)
        }
    }
}
