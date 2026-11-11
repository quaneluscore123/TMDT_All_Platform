package com.socialshop.app.ui.orders

import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.socialshop.app.core.toDisplayDate
import com.socialshop.app.core.toVnd
import com.socialshop.app.data.ShopRepository
import com.socialshop.app.data.remote.Order
import com.socialshop.app.ui.components.BackTopBar
import com.socialshop.app.ui.components.MessageBox
import com.socialshop.app.ui.components.StateContent
import com.socialshop.app.ui.components.UiState
import com.socialshop.app.ui.components.toUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OrdersPage(val orders: List<Order>, val page: Int, val lastPage: Int)

@HiltViewModel
class OrdersViewModel @Inject constructor(private val repo: ShopRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<OrdersPage>>(UiState.Loading)
    val state = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = UiState.Loading
        _state.value = repo.orders(1).map { OrdersPage(it.data, it.meta.currentPage, it.meta.lastPage) }.toUiState()
    }

    fun loadMore() = viewModelScope.launch {
        val current = (_state.value as? UiState.Success)?.data ?: return@launch
        if (current.page >= current.lastPage) return@launch
        repo.orders(current.page + 1).onSuccess {
            _state.value = UiState.Success(OrdersPage(current.orders + it.data, it.meta.currentPage, it.meta.lastPage))
        }
    }
}

@Composable
fun OrdersScreen(onBack: () -> Unit, onOrder: (Long) -> Unit) {
    val vm: OrdersViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { BackTopBar("Đơn hàng của tôi", onBack) }) { padding ->
        Box(Modifier.padding(padding)) {
            StateContent(state, onRetry = { vm.load() }) { page ->
                if (page.orders.isEmpty()) {
                    MessageBox("Bạn chưa có đơn hàng nào.")
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(page.orders, key = { it.id }) { order -> OrderCard(order) { onOrder(order.id) } }
                        if (page.page < page.lastPage) {
                            item {
                                TextButton(onClick = { vm.loadMore() }, modifier = Modifier.fillMaxWidth()) { Text("Xem thêm") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderCard(order: Order, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(order.orderCode, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                StatusChip(order.status, order.statusLabel)
            }
            Text(order.createdAt.toDisplayDate(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                order.items.take(2).joinToString(", ") { it.name } + if (order.items.size > 2) "…" else "",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
            Row {
                Text("${order.itemsCount} sản phẩm · ${order.paymentStatusLabel}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(order.total.toVnd(), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
fun StatusChip(status: String, label: String) {
    val (bg, fg) = when (status) {
        "pending" -> Color(0xFFFEF3C7) to Color(0xFF92400E)
        "confirmed" -> Color(0xFFE0F2FE) to Color(0xFF075985)
        "shipping" -> Color(0xFFEDE9FE) to Color(0xFF5B21B6)
        "delivered" -> Color(0xFFD1FAE5) to Color(0xFF065F46)
        else -> Color(0xFFF5F5F4) to Color(0xFF57534E)
    }
    Surface(color = bg, shape = MaterialTheme.shapes.small) {
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}
