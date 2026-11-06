package com.socialshop.app.ui.checkout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.socialshop.app.core.toVnd
import com.socialshop.app.data.ShopRepository
import com.socialshop.app.data.remote.ApiException
import com.socialshop.app.data.remote.Cart
import com.socialshop.app.data.remote.PlaceOrderRequest
import com.socialshop.app.data.remote.PlaceOrderResponse
import com.socialshop.app.data.remote.Quote
import com.socialshop.app.data.remote.ShippingConfig
import com.socialshop.app.ui.components.BackTopBar
import com.socialshop.app.ui.components.LoadingBox
import com.socialshop.app.ui.components.MessageBox
import com.socialshop.app.ui.components.SummaryLine
import com.socialshop.app.ui.navigation.LocalSnackbar
import com.socialshop.app.ui.navigation.repository
import com.socialshop.app.ui.theme.SuccessGreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CheckoutViewModel(private val repo: ShopRepository) : ViewModel() {
    var name by mutableStateOf(repo.user.value?.name.orEmpty())
    var phone by mutableStateOf(repo.user.value?.phone.orEmpty())
    var address by mutableStateOf(repo.user.value?.address.orEmpty())
    var notes by mutableStateOf("")
    var province by mutableStateOf<String?>(null)
        private set
    var paymentMethod by mutableStateOf("cod")
    var agreeTerms by mutableStateOf(false)
    var couponInput by mutableStateOf("")
    var appliedCoupon by mutableStateOf<String?>(null)
        private set
    var fieldErrors by mutableStateOf<Map<String, List<String>>>(emptyMap())
        private set

    private val _config = MutableStateFlow<ShippingConfig?>(null)
    val config = _config.asStateFlow()
    private val _cart = MutableStateFlow<Cart?>(null)
    val cart = _cart.asStateFlow()
    private val _quote = MutableStateFlow<Quote?>(null)
    val quote = _quote.asStateFlow()
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError = _loadError.asStateFlow()
    private val _placing = MutableStateFlow(false)
    val placing = _placing.asStateFlow()

    private var quoteJob: Job? = null

    init { load() }

    fun load() = viewModelScope.launch {
        _loadError.value = null
        val config = repo.shippingConfig()
        val cart = repo.cart()
        config.onSuccess { _config.value = it }
        cart.onSuccess { _cart.value = it }
        (config.exceptionOrNull() ?: cart.exceptionOrNull())?.let { _loadError.value = it.message }
        refreshQuote()
    }

    fun chooseProvince(value: String) {
        province = value
        refreshQuote()
    }

    fun refreshQuote() {
        quoteJob?.cancel()
        quoteJob = viewModelScope.launch {
            repo.quote(province, appliedCoupon).onSuccess { _quote.value = it }
        }
    }

    suspend fun applyCoupon(): Result<String> {
        val code = couponInput.trim().uppercase()
        if (code.isEmpty()) return Result.failure(ApiException("Nhập mã giảm giá."))
        return repo.applyCoupon(code).map {
            appliedCoupon = it.code
            refreshQuote()
            it.message ?: "Đã áp dụng mã ${it.code}"
        }
    }

    fun removeCoupon() {
        appliedCoupon = null
        couponInput = ""
        refreshQuote()
    }

    fun validate(): String? {
        val errors = buildMap<String, List<String>> {
            if (name.isBlank()) put("shipping_name", listOf("Vui lòng nhập họ tên người nhận."))
            if (!Regex("^0\\d{9,10}$").matches(phone.trim())) put("shipping_phone", listOf("Số điện thoại không hợp lệ."))
            if (address.isBlank()) put("shipping_address", listOf("Vui lòng nhập địa chỉ."))
            if (province == null) put("shipping_province", listOf("Vui lòng chọn tỉnh/thành."))
        }
        fieldErrors = errors
        return when {
            errors.isNotEmpty() -> errors.values.first().first()
            !agreeTerms -> "Vui lòng đồng ý Điều kiện giao dịch chung."
            _quote.value == null -> "Đang tính tổng tiền, vui lòng đợi."
            else -> null
        }
    }

    suspend fun placeOrder(): Result<PlaceOrderResponse> {
        val quote = _quote.value ?: return Result.failure(ApiException("Chưa có báo giá."))
        _placing.value = true
        val result = repo.placeOrder(
            PlaceOrderRequest(
                shippingName = name.trim(),
                shippingPhone = phone.trim(),
                shippingAddress = address.trim(),
                shippingProvince = province.orEmpty(),
                shippingNotes = notes.trim().ifBlank { null },
                paymentMethod = paymentMethod,
                couponCode = appliedCoupon,
                expectedTotal = quote.total,
                agreeTerms = agreeTerms,
            ),
        )
        result.onFailure { e ->
            if (e is ApiException) {
                fieldErrors = e.fieldErrors
                if (e.code == 409) refreshQuote() // giá vừa đổi → lấy báo giá mới để khách xác nhận lại
            }
        }
        _placing.value = false
        return result
    }
}

@Composable
fun CheckoutScreen(onBack: () -> Unit, onPlaced: (orderId: Long, paymentUrl: String?) -> Unit) {
    val repo = repository()
    val vm: CheckoutViewModel = viewModel { CheckoutViewModel(repo) }
    val config by vm.config.collectAsStateWithLifecycle()
    val cart by vm.cart.collectAsStateWithLifecycle()
    val quote by vm.quote.collectAsStateWithLifecycle()
    val loadError by vm.loadError.collectAsStateWithLifecycle()
    val placing by vm.placing.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var showProvinces by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { BackTopBar("Thanh toán", onBack) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tổng thanh toán", style = MaterialTheme.typography.bodySmall)
                        Text(quote?.total?.toVnd() ?: "—", style = MaterialTheme.typography.titleLarge)
                    }
                    Button(
                        onClick = {
                            val error = vm.validate()
                            if (error != null) {
                                scope.launch { snackbar.showSnackbar(error) }
                            } else {
                                scope.launch {
                                    vm.placeOrder()
                                        .onSuccess { onPlaced(it.order.id, it.paymentUrl) }
                                        .onFailure { snackbar.showSnackbar(it.message ?: "Đặt hàng thất bại.") }
                                }
                            }
                        },
                        enabled = !placing && cart?.items?.isNotEmpty() == true,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.height(48.dp),
                    ) {
                        if (placing) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                        else Text("Đặt hàng")
                    }
                }
            }
        },
    ) { padding ->
        val currentCart = cart
        when {
            loadError != null && currentCart == null ->
                MessageBox(loadError!!, Modifier.padding(padding), actionLabel = "Thử lại", onAction = { vm.load() })
            currentCart == null || config == null -> LoadingBox(Modifier.padding(padding))
            currentCart.items.isEmpty() -> MessageBox("Giỏ hàng của bạn đang trống.", Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Section("Thông tin giao hàng") {
                    Field(vm.name, { vm.name = it }, "Họ tên người nhận", vm.fieldErrors["shipping_name"])
                    Field(vm.phone, { vm.phone = it }, "Số điện thoại", vm.fieldErrors["shipping_phone"], KeyboardType.Phone)
                    OutlinedTextField(
                        value = vm.province ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tỉnh / Thành phố") },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                        isError = vm.fieldErrors["shipping_province"] != null,
                        supportingText = vm.fieldErrors["shipping_province"]?.let { { Text(it.first()) } },
                        enabled = false,
                        modifier = Modifier.fillMaxWidth().clickable { showProvinces = true },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    Field(vm.address, { vm.address = it }, "Địa chỉ (số nhà, đường, phường/xã)", vm.fieldErrors["shipping_address"])
                    Field(vm.notes, { vm.notes = it }, "Ghi chú (không bắt buộc)", null)
                }

                Section("Sản phẩm (${currentCart.itemsCount})") {
                    currentCart.items.forEach { item ->
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                Text(
                                    listOfNotNull(item.variantLabel, "x${item.quantity}").joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(item.subtotal.toVnd(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Section("Mã giảm giá") {
                    val applied = vm.appliedCoupon
                    if (applied != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Đã áp dụng: $applied", color = SuccessGreen, modifier = Modifier.weight(1f))
                            TextButton(onClick = vm::removeCoupon) { Text("Gỡ mã") }
                        }
                        quote?.couponError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = vm.couponInput,
                                onValueChange = { vm.couponInput = it.uppercase() },
                                placeholder = { Text("Nhập mã") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.padding(4.dp))
                            OutlinedButton(onClick = {
                                scope.launch {
                                    vm.applyCoupon()
                                        .onSuccess { snackbar.showSnackbar(it) }
                                        .onFailure { snackbar.showSnackbar(it.message ?: "Mã không hợp lệ.") }
                                }
                            }) { Text("Áp dụng") }
                        }
                    }
                }

                Section("Phương thức thanh toán") {
                    config!!.paymentMethods.forEach { method ->
                        Row(
                            Modifier.fillMaxWidth().selectable(
                                selected = vm.paymentMethod == method.code,
                                onClick = { vm.paymentMethod = method.code },
                                role = Role.RadioButton,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = vm.paymentMethod == method.code, onClick = null)
                            Spacer(Modifier.padding(4.dp))
                            Text(method.label)
                        }
                    }
                }

                Section("Chi tiết thanh toán") {
                    val q = quote
                    if (q == null) {
                        Text("Đang tính…")
                    } else {
                        SummaryLine("Tạm tính", q.subtotal.toVnd())
                        if (q.discount > 0) SummaryLine("Giảm giá", "-" + q.discount.toVnd(), valueColor = SuccessGreen)
                        SummaryLine(
                            "Phí vận chuyển" + (q.shippingZone?.let { " ($it)" } ?: ""),
                            if (vm.province == null) "Chọn tỉnh/thành" else if (q.shippingFee == 0L) "Miễn phí" else q.shippingFee.toVnd(),
                        )
                        if (q.taxAmount > 0) SummaryLine("${q.taxLabel} (${q.taxRate.toPercent()})", q.taxAmount.toVnd())
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        SummaryLine("Tổng cộng", q.total.toVnd(), bold = true)
                    }
                }

                Row(
                    Modifier.fillMaxWidth().clickable { vm.agreeTerms = !vm.agreeTerms },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = vm.agreeTerms, onCheckedChange = { vm.agreeTerms = it })
                    Text("Tôi đã đọc và đồng ý với Điều kiện giao dịch chung của SocialShop", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showProvinces) {
        ProvincePicker(
            provinces = config?.provinces.orEmpty(),
            onPick = { vm.chooseProvince(it); showProvinces = false },
            onDismiss = { showProvinces = false },
        )
    }
}

/** "Hà Nội" / "ha noi" / "HA NOI" → "ha noi": tìm tỉnh không cần gõ dấu. */
private fun String.foldVietnamese(): String =
    java.text.Normalizer.normalize(trim().lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('đ', 'd')

private fun Double.toPercent(): String = if (this % 1.0 == 0.0) "${this.toInt()}%" else "$this%"

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    errors: List<String>?,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = errors != null,
        supportingText = errors?.let { { Text(it.first()) } },
        singleLine = keyboardType != KeyboardType.Text,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProvincePicker(provinces: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    val normalized = remember(filter) { filter.foldVietnamese() }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } },
        title = { Text("Chọn tỉnh / thành phố") },
        text = {
            Column {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = { Text("Tìm nhanh") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(provinces.filter { normalized.isEmpty() || it.foldVietnamese().contains(normalized) }) { p ->
                        Text(p, Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 12.dp))
                    }
                }
            }
        },
    )
}
