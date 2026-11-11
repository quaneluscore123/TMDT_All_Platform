package com.socialshop.app.ui.product

import androidx.hilt.navigation.compose.hiltViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.socialshop.app.data.ShopRepository
import com.socialshop.app.data.remote.ProductDetail
import com.socialshop.app.data.remote.Review
import com.socialshop.app.ui.components.BackTopBar
import com.socialshop.app.ui.components.DiscountBadge
import com.socialshop.app.ui.components.PriceRow
import com.socialshop.app.ui.components.ProductCard
import com.socialshop.app.ui.components.ProductImage
import com.socialshop.app.ui.components.QuantityStepper
import com.socialshop.app.ui.components.RatingRow
import com.socialshop.app.ui.components.StateContent
import com.socialshop.app.ui.components.UiState
import com.socialshop.app.ui.components.toUiState
import com.socialshop.app.ui.navigation.LocalSnackbar
import com.socialshop.app.ui.theme.SaleRed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ProductDetailViewModel.Factory::class)
class ProductDetailViewModel @AssistedInject constructor(
    private val repo: ShopRepository,
    @Assisted private val slug: String,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(slug: String): ProductDetailViewModel
    }

    private val _state = MutableStateFlow<UiState<ProductDetail>>(UiState.Loading)
    val state = _state.asStateFlow()
    private val _reviews = MutableStateFlow<List<Review>>(emptyList())
    val reviews = _reviews.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = UiState.Loading
        _state.value = repo.product(slug).toUiState()
        repo.reviews(slug).onSuccess { _reviews.value = it.data.take(3) }
    }

    suspend fun toggleWishlist(productId: Long): Result<String> = repo.toggleWishlist(productId).map { result ->
        _state.update { s -> if (s is UiState.Success) UiState.Success(s.data.copy(isWishlisted = result.isWishlisted)) else s }
        result.message ?: ""
    }

    suspend fun addToCart(productId: Long, variantId: Long?, quantity: Int) =
        repo.addToCart(productId, variantId, quantity)

    val isLoggedIn get() = repo.isLoggedIn
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductDetailScreen(
    slug: String,
    onBack: () -> Unit,
    onProduct: (String) -> Unit,
    onLogin: () -> Unit,
    onCart: () -> Unit,
    onCheckout: () -> Unit,
    onReviews: (name: String, canReview: Boolean) -> Unit,
) {
    val vm = hiltViewModel<ProductDetailViewModel, ProductDetailViewModel.Factory>(key = "product-$slug") { it.create(slug) }
    val state by vm.state.collectAsStateWithLifecycle()
    val reviews by vm.reviews.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val product = (state as? UiState.Success)?.data
    var selectedVariantId by remember(product?.id) { mutableStateOf<Long?>(null) }
    var quantity by remember(product?.id) { mutableIntStateOf(1) }
    var busy by remember { mutableStateOf(false) }

    fun addToCart(thenCheckout: Boolean) {
        val p = product ?: return
        if (!vm.isLoggedIn) { onLogin(); return }
        if (p.hasVariants && selectedVariantId == null) {
            scope.launch { snackbar.showSnackbar("Vui lòng chọn phân loại (size/màu).") }
            return
        }
        busy = true
        scope.launch {
            vm.addToCart(p.id, selectedVariantId, quantity)
                .onSuccess {
                    if (thenCheckout) onCheckout()
                    else {
                        val result = snackbar.showSnackbar("Đã thêm vào giỏ hàng!", actionLabel = "Xem giỏ")
                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) onCart()
                    }
                }
                .onFailure { snackbar.showSnackbar(it.message ?: "Không thêm được vào giỏ.") }
            busy = false
        }
    }

    Scaffold(
        topBar = {
            BackTopBar(product?.name ?: "Sản phẩm", onBack) {
                if (product != null) {
                    IconButton(onClick = {
                        val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, "${product.name} ${product.webUrl.orEmpty()}")
                        }
                        context.startActivity(android.content.Intent.createChooser(share, "Chia sẻ sản phẩm"))
                    }) { Icon(Icons.Filled.Share, contentDescription = "Chia sẻ") }
                    IconButton(onClick = {
                        if (!vm.isLoggedIn) onLogin()
                        else scope.launch {
                            vm.toggleWishlist(product.id)
                                .onSuccess { snackbar.showSnackbar(it) }
                                .onFailure { snackbar.showSnackbar(it.message ?: "Lỗi") }
                        }
                    }) {
                        Icon(
                            if (product.isWishlisted) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Yêu thích",
                            tint = if (product.isWishlisted) SaleRed else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (product != null) {
                val variant = product.variants.firstOrNull { it.id == selectedVariantId }
                val available = variant?.stock ?: if (product.hasVariants) product.totalStock else product.stock
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { addToCart(false) },
                            enabled = !busy && available > 0,
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text("Thêm vào giỏ") }
                        Button(
                            onClick = { addToCart(true) },
                            enabled = !busy && available > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) { Text(if (available > 0) "Mua ngay" else "Hết hàng") }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            StateContent(state, onRetry = { vm.load() }) { p ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ImageGallery(p)

                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        p.brand?.let { Text(it.uppercase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(p.name, style = MaterialTheme.typography.titleLarge)
                        val variant = p.variants.firstOrNull { it.id == selectedVariantId }
                        PriceRow(variant?.price ?: p.price, p.originalPrice, p.isOnSale && (variant == null || variant.price == p.price), large = true)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RatingRow(p.rating, p.reviewsCount)
                            if (p.soldCount > 0) Text("  ·  Đã bán ${p.soldCount}", style = MaterialTheme.typography.bodySmall)
                            p.viewsCount?.let { Text("  ·  $it lượt xem", style = MaterialTheme.typography.bodySmall) }
                        }

                        if (p.hasVariants) {
                            Spacer(Modifier.height(4.dp))
                            Text("Phân loại", style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                p.variants.forEach { v ->
                                    FilterChip(
                                        selected = v.id == selectedVariantId,
                                        onClick = { selectedVariantId = v.id; quantity = 1 },
                                        enabled = v.stock > 0,
                                        label = { Text(if (v.stock > 0) v.label else "${v.label} (hết)") },
                                    )
                                }
                            }
                        }

                        val maxQty = (variant?.stock ?: if (p.hasVariants) p.totalStock else p.stock).coerceIn(1, 99)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Số lượng", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(96.dp))
                            QuantityStepper(quantity, { quantity = it.coerceIn(1, maxQty) }, max = maxQty)
                            Spacer(Modifier.width(12.dp))
                            val stockText = variant?.stock ?: if (p.hasVariants) null else p.stock
                            stockText?.let { Text("Còn $it sản phẩm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("Mô tả sản phẩm", style = MaterialTheme.typography.titleMedium)
                        Text(
                            p.description?.let { AnnotatedString.fromHtml(it) } ?: AnnotatedString("Chưa có mô tả."),
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Đánh giá (${p.reviewsCount})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onReviews(p.name, p.canReview) }) {
                                Text(if (p.canReview) "Viết đánh giá" else "Xem tất cả")
                            }
                        }
                        if (reviews.isEmpty()) {
                            Text("Chưa có đánh giá nào.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            reviews.forEach { ReviewItem(it) }
                        }
                    }

                    RelatedRow("Sản phẩm tương tự", p.related, onProduct)
                    RelatedRow("Thường được mua cùng", p.boughtTogether, onProduct)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ImageGallery(p: ProductDetail) {
    val images = p.images.ifEmpty { listOfNotNull(p.imageUrl) }
    val pager = rememberPagerState { images.size.coerceAtLeast(1) }
    Box {
        HorizontalPager(pager, modifier = Modifier.fillMaxWidth().aspectRatio(1f)) { page ->
            ProductImage(images.getOrNull(page), Modifier.fillMaxSize())
        }
        if (p.isOnSale && p.discountPercent > 0) {
            DiscountBadge(p.discountPercent, Modifier.align(Alignment.TopStart).padding(12.dp))
        }
        if (images.size > 1) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            ) {
                Text("${pager.currentPage + 1}/${images.size}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun RelatedRow(title: String, products: List<com.socialshop.app.data.remote.Product>, onProduct: (String) -> Unit) {
    if (products.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(products, key = { it.id }) { ProductCard(it, onClick = { onProduct(it.slug) }, width = 150.dp) }
    }
}
