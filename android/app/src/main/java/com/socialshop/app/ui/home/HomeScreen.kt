package com.socialshop.app.ui.home

import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.socialshop.app.data.ShopRepository
import com.socialshop.app.data.remote.Category
import com.socialshop.app.data.remote.HomeResponse
import com.socialshop.app.data.remote.Product
import com.socialshop.app.ui.components.ProductCard
import com.socialshop.app.ui.components.ProductImage
import com.socialshop.app.ui.components.StateContent
import com.socialshop.app.ui.components.UiState
import com.socialshop.app.ui.components.toUiState
import com.socialshop.app.ui.theme.Orange500
import com.socialshop.app.ui.theme.SaleRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.OffsetDateTime

@HiltViewModel
class HomeViewModel @Inject constructor(private val repo: ShopRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<HomeResponse>>(UiState.Loading)
    val state = _state.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    init { load() }

    fun load(pull: Boolean = false) = viewModelScope.launch {
        if (pull) _refreshing.value = true else _state.value = UiState.Loading
        _state.value = repo.home().toUiState()
        _refreshing.value = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onProduct: (String) -> Unit,
    onCategory: (Long, String) -> Unit,
    onSearch: (String) -> Unit,
) {
    val vm: HomeViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        SearchHeader(onSearch)
        StateContent(state, onRetry = { vm.load() }) { home ->
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.load(pull = true) }) {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (home.categories.isNotEmpty()) {
                        item { CategoryStrip(home.categories, onCategory) }
                    }
                    if (home.flashSale.products.isNotEmpty()) {
                        item { FlashSaleSection(home.flashSale.endsAt, home.flashSale.products, onProduct) }
                    }
                    home.sections.forEach { section ->
                        item(key = section.key) { ProductRow(section.title, section.products, onProduct) }
                    }
                    if (home.sections.isEmpty() && home.flashSale.products.isEmpty()) {
                        item {
                            Text(
                                "Cửa hàng chưa có sản phẩm.",
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHeader(onSearch: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("SocialShop", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Tìm áo, giày, đồng hồ…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSearch(query.trim()) }),
        )
    }
}

@Composable
private fun CategoryStrip(categories: List<Category>, onCategory: (Long, String) -> Unit) {
    Column {
        SectionTitle("Danh mục")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(categories, key = { it.id }) { category ->
                Card(
                    onClick = { onCategory(category.id, category.name) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.size(width = 96.dp, height = 116.dp),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ProductImage(category.imageUrl, Modifier.size(56.dp).clip(CircleShape))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            category.name,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlashSaleSection(endsAt: String?, products: List<Product>, onProduct: (String) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⚡ Flash Sale", style = MaterialTheme.typography.titleLarge, color = SaleRed, modifier = Modifier.weight(1f))
            endsAt?.let { Countdown(it) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(products, key = { it.id }) { ProductCard(it, onClick = { onProduct(it.slug) }, width = 160.dp) }
        }
    }
}

@Composable
private fun Countdown(endsAt: String) {
    val end = remember(endsAt) { runCatching { OffsetDateTime.parse(endsAt) }.getOrNull() } ?: return
    var remaining by remember { mutableLongStateOf(0L) }
    LaunchedEffect(end) {
        while (true) {
            remaining = Duration.between(OffsetDateTime.now(), end).seconds.coerceAtLeast(0)
            if (remaining == 0L) break
            delay(1000)
        }
    }
    val h = remaining / 3600
    val m = (remaining % 3600) / 60
    val s = remaining % 60
    Box(Modifier.clip(MaterialTheme.shapes.small)) {
        Text(
            "Kết thúc sau %02d:%02d:%02d".format(h, m, s),
            style = MaterialTheme.typography.labelLarge,
            color = Orange500,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ProductRow(title: String, products: List<Product>, onProduct: (String) -> Unit) {
    Column {
        SectionTitle(title)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(products, key = { it.id }) { ProductCard(it, onClick = { onProduct(it.slug) }, width = 160.dp) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}
