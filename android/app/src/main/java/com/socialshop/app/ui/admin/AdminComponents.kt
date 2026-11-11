package com.socialshop.app.ui.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.socialshop.app.data.remote.Paged
import com.socialshop.app.ui.components.MessageBox
import com.socialshop.app.ui.components.StateContent
import com.socialshop.app.ui.components.UiState
import com.socialshop.app.ui.navigation.LocalSnackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─── ViewModel danh sách phân trang dùng chung cho các màn quản trị ─────────────

data class PagedItems<T>(val items: List<T>, val page: Int, val lastPage: Int, val total: Int) {
    val hasMore: Boolean get() = page < lastPage
}

/**
 * Tải trang 1 khi đổi bộ lọc (hủy lượt tải cũ), "Xem thêm" nối trang sau, và phát thông báo một lần
 * ([messages]) để màn hình hiện Snackbar sau mỗi thao tác.
 */
abstract class PagedAdminViewModel<T> : ViewModel() {
    private val _state = MutableStateFlow<UiState<PagedItems<T>>>(UiState.Loading)
    val state: StateFlow<UiState<PagedItems<T>>> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var loadJob: Job? = null
    private var loadingMore = false

    protected abstract suspend fun fetch(page: Int): Result<Paged<T>>

    protected abstract fun idOf(item: T): Long

    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = fetch(1).fold(
                onSuccess = { UiState.Success(PagedItems(it.data, it.meta.currentPage, it.meta.lastPage, it.meta.total)) },
                onFailure = { UiState.Error(it.message ?: "Đã có lỗi xảy ra.") },
            )
        }
    }

    fun loadMore() {
        val current = (_state.value as? UiState.Success)?.data ?: return
        if (!current.hasMore || loadingMore) return
        loadingMore = true
        viewModelScope.launch {
            fetch(current.page + 1)
                .onSuccess { next ->
                    val known = current.items.map(::idOf).toSet()
                    _state.value = UiState.Success(
                        PagedItems(current.items + next.data.filterNot { idOf(it) in known }, next.meta.currentPage, next.meta.lastPage, next.meta.total),
                    )
                }
                .onFailure { notify(it.message ?: "Không tải thêm được.") }
            loadingMore = false
        }
    }

    protected fun replace(item: T) = update { list -> list.map { if (idOf(it) == idOf(item)) item else it } }

    protected fun remove(id: Long) = update { list -> list.filterNot { idOf(it) == id } }

    private fun update(transform: (List<T>) -> List<T>) {
        val current = (_state.value as? UiState.Success)?.data ?: return
        _state.value = UiState.Success(current.copy(items = transform(current.items)))
    }

    protected fun notify(message: String) {
        _messages.tryEmit(message)
    }

    /** Chạy một thao tác ghi; thành công → [onSuccess] + thông báo, thất bại → thông báo lỗi. */
    protected fun <R> perform(action: suspend () -> Result<R>, successMessage: (R) -> String?, onSuccess: (R) -> Unit = {}) {
        viewModelScope.launch {
            action()
                .onSuccess { result ->
                    onSuccess(result)
                    successMessage(result)?.let(::notify)
                }
                .onFailure { notify(it.message ?: "Thao tác không thành công.") }
        }
    }
}

/** Hiện các thông báo một lần của ViewModel lên Snackbar chung của app. */
@Composable
fun CollectMessages(messages: SharedFlow<String>) {
    val snackbar = LocalSnackbar.current
    LaunchedEffect(messages) {
        messages.collect { snackbar.showSnackbar(it) }
    }
}

// ─── Thành phần giao diện ───────────────────────────────────────────────────────

@Composable
fun <T> PagedAdminList(
    state: UiState<PagedItems<T>>,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    emptyMessage: String,
    key: (T) -> Any,
    header: LazyListScope.(PagedItems<T>) -> Unit = {},
    itemContent: @Composable (T) -> Unit,
) {
    StateContent(state, onRetry = onRetry) { page ->
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            header(page)
            if (page.items.isEmpty()) {
                item { MessageBox(emptyMessage, Modifier.padding(top = 32.dp)) }
            }
            items(page.items, key = key) { itemContent(it) }
            if (page.hasMore) {
                item {
                    // Tự tải trang sau khi cuộn tới cuối danh sách
                    LaunchedEffect(page.page) { onLoadMore() }
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(Modifier.padding(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FilterRow(options: List<Pair<String?, String>>, selected: String?, onSelect: (String?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, onSearch: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange(""); onSearch() }) { Icon(Icons.Filled.Clear, contentDescription = "Xóa") }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun Pill(text: String, background: Color, foreground: Color) {
    Surface(color = background, shape = MaterialTheme.shapes.small) {
        Text(text, color = foreground, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

val PillGreen = Color(0xFFD1FAE5) to Color(0xFF065F46)
val PillAmber = Color(0xFFFEF3C7) to Color(0xFF92400E)
val PillRed = Color(0xFFFEE2E2) to Color(0xFF991B1B)
val PillGray = Color(0xFFF5F5F4) to Color(0xFF57534E)
val PillBlue = Color(0xFFE0F2FE) to Color(0xFF075985)

@Composable
fun Pill(text: String, colors: Pair<Color, Color>) = Pill(text, colors.first, colors.second)

@Composable
fun AdminCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    if (onClick != null) {
        Card(onClick = onClick, colors = colors, modifier = modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { content() } }
    } else {
        Card(colors = colors, modifier = modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { content() } }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier, hint: String? = null, accent: Color? = null, onClick: (() -> Unit)? = null) {
    AdminCard(modifier = modifier, onClick = onClick) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accent ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Hộp thoại xác nhận cho thao tác khó hoàn tác (hủy đơn, khóa tài khoản, xóa...). */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Không") } },
    )
}
