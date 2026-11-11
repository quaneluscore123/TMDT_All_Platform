package com.socialshop.app.ui.products

import androidx.hilt.navigation.compose.hiltViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.socialshop.app.data.ShopRepository
import com.socialshop.app.data.remote.Category
import com.socialshop.app.data.remote.Product
import com.socialshop.app.ui.components.BackTopBar
import com.socialshop.app.ui.components.LoadingBox
import com.socialshop.app.ui.components.MessageBox
import com.socialshop.app.ui.components.ProductCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val SORT_OPTIONS = listOf(
    "latest" to "Mới nhất",
    "popular" to "Phổ biến",
    "best_selling" to "Bán chạy",
    "price_asc" to "Giá tăng",
    "price_desc" to "Giá giảm",
    "top_rated" to "Đánh giá cao",
)

data class ProductListState(
    val items: List<Product> = emptyList(),
    val page: Int = 0,
    val lastPage: Int = 1,
    val total: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val canLoadMore get() = page < lastPage
}

@HiltViewModel(assistedFactory = ProductListViewModel.Factory::class)
class ProductListViewModel @AssistedInject constructor(
    private val repo: ShopRepository,
    @Assisted("search") initialSearch: String?,
    @Assisted("category") initialCategory: Long?,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(@Assisted("search") initialSearch: String?, @Assisted("category") initialCategory: Long?): ProductListViewModel
    }

    var search by mutableStateOf(initialSearch)
        private set
    var categoryId by mutableStateOf(initialCategory)
        private set
    var sort by mutableStateOf("latest")
        private set

    private val _state = MutableStateFlow(ProductListState())
    val state = _state.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories = _categories.asStateFlow()

    private var job: Job? = null

    init {
        reload()
        viewModelScope.launch { repo.categories().onSuccess { list -> _categories.value = list.filter { it.parentId == null } } }
    }

    fun apply(search: String? = this.search, categoryId: Long? = this.categoryId, sort: String = this.sort) {
        this.search = search?.ifBlank { null }
        this.categoryId = categoryId
        this.sort = sort
        reload()
    }

    fun reload() {
        job?.cancel()
        _state.value = ProductListState(loading = true)
        loadPage(1)
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || !s.canLoadMore) return
        _state.update { it.copy(loading = true) }
        loadPage(s.page + 1)
    }

    private fun loadPage(page: Int) {
        job = viewModelScope.launch {
            repo.products(search, categoryId, sort, page)
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            items = if (page == 1) result.data else it.items + result.data,
                            page = result.meta.currentPage,
                            lastPage = result.meta.lastPage,
                            total = result.meta.total,
                            loading = false,
                            error = null,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }
}

@Composable
fun ProductListScreen(
    initialSearch: String?,
    categoryId: Long?,
    title: String?,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onProduct: (String) -> Unit,
) {
    val vm = hiltViewModel<ProductListViewModel, ProductListViewModel.Factory>(key = "products-$initialSearch-$categoryId") {
        it.create(initialSearch, categoryId)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(initialSearch.orEmpty()) }
    val focus = LocalFocusManager.current
    val grid = rememberLazyGridState()

    val nearEnd by remember {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= grid.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd && state.items.isNotEmpty()) vm.loadMore() }

    Column(Modifier.fillMaxSize()) {
        if (canGoBack) BackTopBar(title ?: "Sản phẩm", onBack)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Tìm kiếm sản phẩm") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = ""; vm.apply(search = null) }) { Icon(Icons.Filled.Clear, contentDescription = "Xóa") }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.apply(search = query) }),
        )

        if (categoryId == null && categories.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(selected = vm.categoryId == null, onClick = { vm.apply(categoryId = null) }, label = { Text("Tất cả") })
                }
                items(categories, key = { it.id }) { c ->
                    FilterChip(selected = vm.categoryId == c.id, onClick = { vm.apply(categoryId = c.id) }, label = { Text(c.name) })
                }
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SORT_OPTIONS, key = { it.first }) { (key, label) ->
                FilterChip(selected = vm.sort == key, onClick = { vm.apply(sort = key) }, label = { Text(label) })
            }
        }

        when {
            state.items.isEmpty() && state.loading -> LoadingBox()
            state.items.isEmpty() && state.error != null -> MessageBox(state.error!!, actionLabel = "Thử lại", onAction = vm::reload)
            state.items.isEmpty() -> MessageBox("Không tìm thấy sản phẩm phù hợp.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                state = grid,
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("${state.total} sản phẩm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(state.items, key = { it.id }) { ProductCard(it, onClick = { onProduct(it.slug) }) }
                if (state.loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}
