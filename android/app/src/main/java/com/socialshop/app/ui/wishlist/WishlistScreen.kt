package com.socialshop.app.ui.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.socialshop.app.data.ShopRepository
import com.socialshop.app.data.remote.Product
import com.socialshop.app.ui.components.MessageBox
import com.socialshop.app.ui.components.ProductCard
import com.socialshop.app.ui.components.StateContent
import com.socialshop.app.ui.components.UiState
import com.socialshop.app.ui.components.toUiState
import com.socialshop.app.ui.navigation.repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WishlistViewModel(private val repo: ShopRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<Product>>>(UiState.Loading)
    val state = _state.asStateFlow()
    val token = repo.token

    fun load() = viewModelScope.launch {
        if (!repo.isLoggedIn) return@launch
        if (_state.value !is UiState.Success) _state.value = UiState.Loading
        _state.value = repo.wishlist().map { it.data }.toUiState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(onProduct: (String) -> Unit, onLogin: () -> Unit) {
    val repo = repository()
    val vm: WishlistViewModel = viewModel { WishlistViewModel(repo) }
    val token by vm.token.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()

    // Mỗi lần mở tab đều tải lại (vừa thả tim ở trang sản phẩm)
    LaunchedEffect(token) { vm.load() }

    Scaffold(topBar = { TopAppBar(title = { Text("Yêu thích") }) }) { padding ->
        Box(Modifier.padding(padding)) {
            if (token == null) {
                MessageBox("Đăng nhập để lưu sản phẩm yêu thích.", actionLabel = "Đăng nhập", onAction = onLogin)
                return@Box
            }
            StateContent(state, onRetry = { vm.load() }) { products ->
                if (products.isEmpty()) {
                    MessageBox("Chưa có sản phẩm yêu thích. Nhấn ♡ ở trang sản phẩm để lưu lại.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(160.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(products, key = { it.id }) { ProductCard(it, onClick = { onProduct(it.slug) }) }
                    }
                }
            }
        }
    }
}
