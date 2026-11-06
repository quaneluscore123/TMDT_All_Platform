package com.socialshop.app.ui.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.socialshop.app.core.toVnd
import com.socialshop.app.data.ShopRepository
import com.socialshop.app.data.remote.Cart
import com.socialshop.app.data.remote.CartItem
import com.socialshop.app.ui.components.MessageBox
import com.socialshop.app.ui.components.ProductImage
import com.socialshop.app.ui.components.QuantityStepper
import com.socialshop.app.ui.components.StateContent
import com.socialshop.app.ui.components.UiState
import com.socialshop.app.ui.components.toUiState
import com.socialshop.app.ui.navigation.LocalSnackbar
import com.socialshop.app.ui.navigation.repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CartViewModel(private val repo: ShopRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<Cart>>(UiState.Loading)
    val state = _state.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    val token = repo.token

    fun load() = viewModelScope.launch {
        if (!repo.isLoggedIn) return@launch
        if (_state.value !is UiState.Success) _state.value = UiState.Loading
        _state.value = repo.cart().toUiState()
    }

    suspend fun changeQuantity(item: CartItem, quantity: Int) = mutate { repo.updateCartItem(item.id, quantity) }

    suspend fun remove(item: CartItem) = mutate { repo.removeCartItem(item.id) }

    private suspend fun mutate(block: suspend () -> Result<Cart>): Result<Cart> {
        _busy.value = true
        val result = block()
        result.onSuccess { _state.value = UiState.Success(it) }
        _busy.value = false
        return result
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    onProduct: (String) -> Unit,
    onCheckout: () -> Unit,
    onLogin: () -> Unit,
    onShop: () -> Unit,
) {
    val repo = repository()
    val vm: CartViewModel = viewModel { CartViewModel(repo) }
    val token by vm.token.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current

    // Tải lại mỗi khi mở tab hoặc vừa đăng nhập
    LaunchedEffect(token) { vm.load() }

    val cart = (state as? UiState.Success)?.data

    Scaffold(
        topBar = { TopAppBar(title = { Text("Giỏ hàng" + (cart?.let { " (${it.itemsCount})" } ?: "")) }) },
        bottomBar = {
            if (token != null && cart != null && cart.items.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Tạm tính", style = MaterialTheme.typography.bodySmall)
                            Text(cart.subtotal.toVnd(), style = MaterialTheme.typography.titleLarge)
                        }
                        Button(
                            onClick = onCheckout,
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.height(48.dp),
                        ) { Text("Thanh toán") }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (token == null) {
                MessageBox("Đăng nhập để xem giỏ hàng và đặt hàng.", actionLabel = "Đăng nhập", onAction = onLogin)
                return@Box
            }
            StateContent(state, onRetry = { vm.load() }) { data ->
                if (data.items.isEmpty()) {
                    MessageBox("Giỏ hàng của bạn đang trống.", actionLabel = "Tiếp tục mua sắm", onAction = onShop)
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(data.items, key = { it.id }) { item ->
                            CartRow(
                                item = item,
                                enabled = !busy,
                                onClick = { onProduct(item.productSlug) },
                                onQuantity = { q ->
                                    scope.launch {
                                        vm.changeQuantity(item, q).onFailure { snackbar.showSnackbar(it.message ?: "Lỗi") }
                                    }
                                },
                                onRemove = {
                                    scope.launch {
                                        vm.remove(item).onFailure { snackbar.showSnackbar(it.message ?: "Lỗi") }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartRow(
    item: CartItem,
    enabled: Boolean,
    onClick: () -> Unit,
    onQuantity: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp)) {
            ProductImage(item.imageUrl, Modifier.size(84.dp).clip(MaterialTheme.shapes.small))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.variantLabel?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(item.unitPrice.toVnd(), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QuantityStepper(item.quantity, onQuantity, max = item.stock.coerceIn(1, 99), enabled = enabled)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onRemove, enabled = enabled) {
                        Icon(Icons.Filled.Delete, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.error)
                    }
                }
                if (item.quantity > item.stock) {
                    Text("Chỉ còn ${item.stock} sản phẩm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
