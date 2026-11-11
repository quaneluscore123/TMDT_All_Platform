package com.socialshop.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.socialshop.app.core.resolveBackendUrl
import com.socialshop.app.core.toVnd
import com.socialshop.app.data.AdminRepository
import com.socialshop.app.data.remote.AdminProduct
import com.socialshop.app.data.remote.ProductUpdateRequest
import com.socialshop.app.data.remote.VariantUpdate
import com.socialshop.app.ui.components.BackTopBar
import com.socialshop.app.ui.components.StateContent
import com.socialshop.app.ui.components.UiState
import com.socialshop.app.ui.components.toUiState
import com.socialshop.app.ui.navigation.LocalSnackbar
import com.socialshop.app.ui.theme.SaleRed
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val PRODUCT_FILTERS = listOf(
    null to "Tất cả",
    "active" to "Đang bán",
    "inactive" to "Ngừng bán",
    "out_of_stock" to "Hết hàng",
    "low_stock" to "Sắp hết",
)

private fun statusColors(status: String) = when (status) {
    "active" -> PillGreen
    "out_of_stock" -> PillRed
    else -> PillGray
}

// ─── Danh sách sản phẩm ─────────────────────────────────────────────────────────

@HiltViewModel(assistedFactory = AdminProductsViewModel.Factory::class)
class AdminProductsViewModel @AssistedInject constructor(
    private val repo: AdminRepository,
    @Assisted initialFilter: String?,
) : PagedAdminViewModel<AdminProduct>() {
    @AssistedFactory
    interface Factory {
        fun create(initialFilter: String?): AdminProductsViewModel
    }

    var filter by mutableStateOf(initialFilter)
        private set
    var search by mutableStateOf("")

    init { reload() }

    override suspend fun fetch(page: Int) = repo.products(search, filter, page)

    override fun idOf(item: AdminProduct) = item.id

    fun selectFilter(value: String?) {
        filter = value
        reload()
    }

    fun toggle(product: AdminProduct) = perform(
        action = { repo.toggleProduct(product.id) },
        successMessage = { if (it.status == "active") "Đã mở bán \"${it.name}\"." else "Đã ngừng bán \"${it.name}\"." },
        onSuccess = ::replace,
    )

    /** Màn sửa trả về sản phẩm đã lưu → cập nhật dòng tương ứng mà không tải lại cả danh sách. */
    fun onEdited(product: AdminProduct) = replace(product)
}

@Composable
fun AdminProductsScreen(
    initialFilter: String?,
    edited: AdminProduct?,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val vm = hiltViewModel<AdminProductsViewModel, AdminProductsViewModel.Factory>(key = "admin-products-$initialFilter") { it.create(initialFilter) }
    val state by vm.state.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)
    LaunchedEffect(edited) { edited?.let(vm::onEdited) }

    Scaffold(topBar = { BackTopBar("Quản lý sản phẩm", onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(vm.search, { vm.search = it }, "Tên sản phẩm", onSearch = vm::reload)
            FilterRow(PRODUCT_FILTERS, vm.filter, vm::selectFilter)
            Spacer(Modifier.height(4.dp))
            PagedAdminList(
                state = state,
                onRetry = vm::reload,
                onLoadMore = vm::loadMore,
                emptyMessage = "Không có sản phẩm nào.",
                key = { it.id },
                header = { page ->
                    item {
                        Text(
                            "${page.total} sản phẩm · Thêm sản phẩm mới, mô tả và ảnh làm trên trang admin web.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            ) { product -> ProductRow(product, onClick = { onEdit(product.id) }, onToggle = { vm.toggle(product) }) }
        }
    }
}

@Composable
private fun ProductRow(product: AdminProduct, onClick: () -> Unit, onToggle: () -> Unit) {
    AdminCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = product.imageUrl.resolveBackendUrl(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)),
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(product.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                product.categoryName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(product.effectivePrice.toVnd(), style = MaterialTheme.typography.titleSmall, color = if (product.isOnSale) SaleRed else MaterialTheme.colorScheme.onSurface)
                    if (product.isOnSale) {
                        Text(
                            "  " + product.price.toVnd(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(product.statusLabel, statusColors(product.status))
                    Text(
                        "Tồn: ${product.stock}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (product.stock <= 5) SaleRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(checked = product.status == "active", onCheckedChange = { onToggle() })
        }
    }
}

// ─── Sửa nhanh sản phẩm ─────────────────────────────────────────────────────────

class VariantDraft(val id: Long, val label: String, stock: String, price: String) {
    var stock by mutableStateOf(stock)
    var price by mutableStateOf(price)
}

@HiltViewModel(assistedFactory = AdminProductEditViewModel.Factory::class)
class AdminProductEditViewModel @AssistedInject constructor(
    private val repo: AdminRepository,
    @Assisted private val productId: Long,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(productId: Long): AdminProductEditViewModel
    }

    private val _state = MutableStateFlow<UiState<AdminProduct>>(UiState.Loading)
    val state = _state.asStateFlow()

    var price by mutableStateOf("")
    var salePrice by mutableStateOf("")
    var stock by mutableStateOf("")
    var status by mutableStateOf("active")
    val variants = mutableStateListOf<VariantDraft>()
    var errors by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var saving by mutableStateOf(false)
        private set

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = UiState.Loading
        _state.value = repo.product(productId).onSuccess(::fill).toUiState()
    }

    private fun fill(product: AdminProduct) {
        price = product.price.toString()
        salePrice = product.salePrice?.toString().orEmpty()
        stock = product.stock.toString()
        status = product.status
        variants.clear()
        product.variants.orEmpty().forEach { variants += VariantDraft(it.id, it.label, it.stock.toString(), it.price?.toString().orEmpty()) }
    }

    /** Lưu; thành công trả về sản phẩm mới để màn danh sách cập nhật. */
    suspend fun save(): Result<AdminProduct> {
        val local = buildMap {
            if (price.toLongOrNull() == null) put("price", "Nhập giá gốc hợp lệ.")
            val sale = salePrice.trim().takeIf { it.isNotEmpty() }
            if (sale != null && sale.toLongOrNull() == null) put("sale_price", "Giá khuyến mãi không hợp lệ.")
            if (variants.isEmpty() && stock.toIntOrNull() == null) put("stock", "Nhập tồn kho hợp lệ.")
            variants.forEach { if (it.stock.toIntOrNull() == null) put("variants", "Tồn kho của \"${it.label}\" không hợp lệ.") }
        }
        if (local.isNotEmpty()) {
            errors = local
            return Result.failure(IllegalArgumentException(local.values.first()))
        }

        saving = true
        errors = emptyMap()
        val request = ProductUpdateRequest(
            price = price.toLong(),
            salePrice = salePrice.trim().toLongOrNull(),
            stock = if (variants.isEmpty()) stock.toInt() else null,
            status = status,
            variants = variants.takeIf { it.isNotEmpty() }?.map { VariantUpdate(it.id, it.stock.toInt(), it.price.trim().toLongOrNull()) },
        )
        val result = repo.updateProduct(productId, request)
            .onSuccess { fill(it.product); _state.value = UiState.Success(it.product) }
            .onFailure { e ->
                (e as? com.socialshop.app.data.remote.ApiException)?.let { api ->
                    errors = api.fieldErrors.mapValues { it.value.firstOrNull().orEmpty() }
                }
            }
            .map { it.product }
        saving = false
        return result
    }
}

@Composable
fun AdminProductEditScreen(productId: Long, onBack: () -> Unit, onSaved: (AdminProduct) -> Unit) {
    val vm = hiltViewModel<AdminProductEditViewModel, AdminProductEditViewModel.Factory>(key = "admin-product-$productId") { it.create(productId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { BackTopBar("Sửa sản phẩm", onBack) }) { padding ->
        Box(Modifier.padding(padding)) {
            StateContent(state, onRetry = { vm.load() }) { product ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AdminCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = product.imageUrl.resolveBackendUrl(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                            )
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(product.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    listOfNotNull(product.categoryName, product.brand, "${product.viewsCount} lượt xem").joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                product.grossMarginPercent?.let {
                                    Text("Biên lợi nhuận gộp: ${it.formatRate()}%", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    AdminCard {
                        Text("Giá bán", style = MaterialTheme.typography.titleSmall)
                        MoneyField("Giá gốc (₫)", vm.price, { vm.price = it }, vm.errors["price"])
                        MoneyField("Giá khuyến mãi (₫) — để trống nếu không giảm", vm.salePrice, { vm.salePrice = it }, vm.errors["sale_price"])
                        if (product.saleEndsAt != null && vm.salePrice.isNotBlank()) {
                            Text("Đang có hạn Flash Sale — bỏ giá khuyến mãi sẽ kết thúc Flash Sale.", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    AdminCard {
                        Text("Tồn kho", style = MaterialTheme.typography.titleSmall)
                        if (vm.variants.isEmpty()) {
                            MoneyField("Số lượng tồn", vm.stock, { vm.stock = it }, vm.errors["stock"])
                        } else {
                            Text("Tồn kho tổng tự cộng từ các biến thể.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            vm.variants.forEachIndexed { index, variant ->
                                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                Text(variant.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MoneyField("Tồn", variant.stock, { variant.stock = it }, null, Modifier.weight(1f))
                                    MoneyField("Giá riêng (₫)", variant.price, { variant.price = it }, null, Modifier.weight(1.4f))
                                }
                            }
                            vm.errors.entries.firstOrNull { it.key.startsWith("variants") }?.let {
                                Text(it.value, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    AdminCard {
                        Text("Trạng thái", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("active" to "Đang bán", "inactive" to "Ngừng bán", "out_of_stock" to "Hết hàng").forEach { (value, label) ->
                                FilterChip(selected = vm.status == value, onClick = { vm.status = value }, label = { Text(label) })
                            }
                        }
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                vm.save().fold(
                                    onSuccess = { snackbar.showSnackbar("Đã lưu sản phẩm."); onSaved(it) },
                                    onFailure = { snackbar.showSnackbar(it.message ?: "Không lưu được.") },
                                )
                            }
                        },
                        enabled = !vm.saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (vm.saving) "Đang lưu…" else "Lưu thay đổi") }
                }
            }
        }
    }
}

@Composable
fun MoneyField(label: String, value: String, onChange: (String) -> Unit, error: String?, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter(Char::isDigit)) },
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.padding(top = 4.dp),
    )
}
