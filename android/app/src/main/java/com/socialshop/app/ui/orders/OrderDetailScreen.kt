package com.socialshop.app.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.socialshop.app.core.toDisplayDate
import com.socialshop.app.core.toVnd
import com.socialshop.app.data.ShopRepository
import com.socialshop.app.data.remote.Order
import com.socialshop.app.ui.components.BackTopBar
import com.socialshop.app.ui.components.ProductImage
import com.socialshop.app.ui.components.StateContent
import com.socialshop.app.ui.components.SummaryLine
import com.socialshop.app.ui.components.UiState
import com.socialshop.app.ui.components.clickableRow
import com.socialshop.app.ui.components.toUiState
import com.socialshop.app.ui.navigation.LocalSnackbar
import com.socialshop.app.ui.navigation.repository
import com.socialshop.app.ui.theme.SuccessGreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OrderDetailViewModel(private val repo: ShopRepository, private val orderId: Long) : ViewModel() {
    private val _state = MutableStateFlow<UiState<Order>>(UiState.Loading)
    val state = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = UiState.Loading
        _state.value = repo.order(orderId).toUiState()
    }

    suspend fun cancel() = repo.cancelOrder(orderId).onSuccess {
        _state.value = UiState.Success(it)
        repo.refreshCartCount()
    }
}

@Composable
fun OrderDetailScreen(orderId: Long, justPlaced: Boolean, onBack: () -> Unit, onProduct: (String) -> Unit) {
    val repo = repository()
    val vm: OrderDetailViewModel = viewModel(key = "order-$orderId") { OrderDetailViewModel(repo, orderId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var confirmCancel by remember { mutableStateOf(false) }

    Scaffold(topBar = { BackTopBar("Chi tiết đơn hàng", onBack) }) { padding ->
        Box(Modifier.padding(padding)) {
            StateContent(state, onRetry = { vm.load() }) { order ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (justPlaced) {
                        Card(colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.12f))) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("🎉 Đặt hàng thành công!", style = MaterialTheme.typography.titleMedium, color = SuccessGreen)
                                Text(
                                    when {
                                        order.paymentMethod == "vnpay" && order.paymentStatus == "paid" -> "Thanh toán VNPay thành công. Chúng tôi sẽ sớm giao hàng cho bạn."
                                        order.paymentMethod == "vnpay" -> "Đơn hàng chưa được thanh toán. Nếu bạn đã hủy giao dịch, sản phẩm đã được trả về giỏ hàng."
                                        else -> "Bạn sẽ thanh toán khi nhận hàng. Email xác nhận đã được gửi."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }

                    Block {
                        Row {
                            Text(order.orderCode, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            StatusChip(order.status, order.statusLabel)
                        }
                        Text("Đặt lúc ${order.createdAt.toDisplayDate()}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Thanh toán: ${if (order.paymentMethod == "vnpay") "VNPay" else "COD"} · ${order.paymentStatusLabel}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Block("Địa chỉ nhận hàng") {
                        Text("${order.shippingName} · ${order.shippingPhone}", style = MaterialTheme.typography.bodyMedium)
                        Text(listOfNotNull(order.shippingAddress, order.shippingProvince).joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                        order.note?.let { Text("Ghi chú: $it", style = MaterialTheme.typography.bodySmall) }
                    }

                    Block("Sản phẩm") {
                        order.items.forEach { item ->
                            Row(Modifier.fillMaxWidth().clickableRow { item.productSlug?.let(onProduct) }.padding(vertical = 4.dp)) {
                                ProductImage(item.imageUrl, Modifier.size(56.dp).clip(MaterialTheme.shapes.small))
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                    Text(
                                        listOfNotNull(item.variantLabel, "${item.price.toVnd()} x${item.quantity}").joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(item.subtotal.toVnd(), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    Block("Thanh toán") {
                        SummaryLine("Tạm tính", order.subtotal.toVnd())
                        if (order.discount > 0) SummaryLine("Giảm giá", "-" + order.discount.toVnd(), valueColor = SuccessGreen)
                        SummaryLine("Phí vận chuyển", if (order.shippingFee == 0L) "Miễn phí" else order.shippingFee.toVnd())
                        if (order.taxAmount > 0) SummaryLine("Thuế VAT", order.taxAmount.toVnd())
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        SummaryLine("Tổng cộng", order.total.toVnd(), bold = true)
                    }

                    if (order.canCancel) {
                        OutlinedButton(onClick = { confirmCancel = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Hủy đơn hàng", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Hủy đơn hàng?") },
            text = { Text("Đơn hàng sẽ bị hủy và không thể khôi phục.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    scope.launch {
                        vm.cancel()
                            .onSuccess { snackbar.showSnackbar("Đã hủy đơn hàng.") }
                            .onFailure { snackbar.showSnackbar(it.message ?: "Không hủy được đơn.") }
                    }
                }) { Text("Hủy đơn", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Không") } },
        )
    }
}

@Composable
private fun Block(title: String? = null, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            content()
        }
    }
}
