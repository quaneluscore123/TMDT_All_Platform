package com.socialshop.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.socialshop.app.core.toVnd
import com.socialshop.app.data.AdminRepository
import com.socialshop.app.data.remote.ApiException
import com.socialshop.app.data.remote.TaxSettings
import com.socialshop.app.data.remote.TaxSettingsRequest
import com.socialshop.app.ui.components.BackTopBar
import com.socialshop.app.ui.components.StateContent
import com.socialshop.app.ui.components.SummaryLine
import com.socialshop.app.ui.components.UiState
import com.socialshop.app.ui.components.toUiState
import com.socialshop.app.ui.navigation.LocalSnackbar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminTaxViewModel @Inject constructor(private val repo: AdminRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<TaxSettings>>(UiState.Loading)
    val state = _state.asStateFlow()

    var enabled by mutableStateOf(false)
    var rate by mutableStateOf("")
    var label by mutableStateOf("")
    var onShipping by mutableStateOf(false)
    var errors by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var saving by mutableStateOf(false)
        private set

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = UiState.Loading
        _state.value = repo.taxSettings().onSuccess(::fill).toUiState()
    }

    private fun fill(settings: TaxSettings) {
        enabled = settings.taxEnabled
        rate = settings.taxRate.formatRate()
        label = settings.taxLabel
        onShipping = settings.taxOnShipping
    }

    suspend fun save(): Result<String> {
        val parsedRate = rate.replace(',', '.').toDoubleOrNull()
        if (parsedRate == null || parsedRate < 0 || parsedRate > 100) {
            errors = mapOf("tax_rate" to "Thuế suất phải từ 0 đến 100%.")
            return Result.failure(IllegalArgumentException(errors.values.first()))
        }
        if (label.isBlank()) {
            errors = mapOf("tax_label" to "Nhập tên hiển thị của thuế.")
            return Result.failure(IllegalArgumentException(errors.values.first()))
        }

        saving = true
        errors = emptyMap()
        val result = repo.updateTaxSettings(TaxSettingsRequest(enabled, parsedRate, label.trim(), onShipping))
            .onSuccess { fill(it); _state.value = UiState.Success(it) }
            .onFailure { e -> (e as? ApiException)?.let { api -> errors = api.fieldErrors.mapValues { it.value.firstOrNull().orEmpty() } } }
            .map { it.message ?: "Đã lưu cài đặt thuế." }
        saving = false
        return result
    }
}

@Composable
fun AdminTaxScreen(onBack: () -> Unit) {
    val vm: AdminTaxViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { BackTopBar("Thuế VAT", onBack) }) { padding ->
        Box(Modifier.padding(padding)) {
            StateContent(state, onRetry = { vm.load() }) { saved ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AdminCard {
                        SwitchRow("Thu thuế trên đơn hàng", "Tắt thì đơn mới không cộng thuế.", vm.enabled) { vm.enabled = it }
                        OutlinedTextField(
                            value = vm.rate,
                            onValueChange = { input -> vm.rate = input.filter { it.isDigit() || it == '.' || it == ',' } },
                            label = { Text("Thuế suất (%)") },
                            isError = vm.errors["tax_rate"] != null,
                            supportingText = vm.errors["tax_rate"]?.let { { Text(it) } },
                            singleLine = true,
                            enabled = vm.enabled,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        OutlinedTextField(
                            value = vm.label,
                            onValueChange = { vm.label = it.take(50) },
                            label = { Text("Tên hiển thị (vd. Thuế VAT)") },
                            isError = vm.errors["tax_label"] != null,
                            supportingText = vm.errors["tax_label"]?.let { { Text(it) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SwitchRow("Tính thuế trên phí vận chuyển", "Bật nếu phí ship cũng chịu thuế.", vm.onShipping, enabled = vm.enabled) { vm.onShipping = it }
                    }

                    AdminCard {
                        Text("Ví dụ với cài đặt đang áp dụng", style = MaterialTheme.typography.titleSmall)
                        Text("Đơn 1.000.000₫, giao trong khu vực mặc định", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val example = saved.example
                        SummaryLine("Tạm tính", example.subtotal.toVnd())
                        SummaryLine("Phí vận chuyển", if (example.shippingFee == 0L) "Miễn phí" else example.shippingFee.toVnd())
                        SummaryLine("${saved.taxLabel} (${example.taxRate.formatRate()}%)", example.taxAmount.toVnd())
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        SummaryLine("Khách trả", example.total.toVnd(), bold = true)
                    }

                    Text(
                        "Thay đổi áp dụng ngay cho đơn hàng mới. Đơn đã đặt giữ nguyên số thuế lúc đặt. Phí vận chuyển theo khu vực chỉnh trên trang admin web.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                vm.save().fold(
                                    onSuccess = { snackbar.showSnackbar(it) },
                                    onFailure = { snackbar.showSnackbar(it.message ?: "Không lưu được.") },
                                )
                            }
                        },
                        enabled = !vm.saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (vm.saving) "Đang lưu…" else "Lưu cài đặt thuế") }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
