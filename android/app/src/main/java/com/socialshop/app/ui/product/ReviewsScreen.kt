package com.socialshop.app.ui.product

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.socialshop.app.core.toDisplayDate
import com.socialshop.app.data.ShopRepository
import com.socialshop.app.data.remote.Review
import com.socialshop.app.ui.components.BackTopBar
import com.socialshop.app.ui.components.MessageBox
import com.socialshop.app.ui.components.Stars
import com.socialshop.app.ui.components.StateContent
import com.socialshop.app.ui.components.UiState
import com.socialshop.app.ui.components.toUiState
import com.socialshop.app.ui.navigation.LocalSnackbar
import com.socialshop.app.ui.navigation.repository
import com.socialshop.app.ui.theme.StarYellow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReviewsViewModel(private val repo: ShopRepository, private val slug: String) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<Review>>>(UiState.Loading)
    val state = _state.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _state.value = repo.reviews(slug).map { it.data }.toUiState()
    }

    suspend fun submit(rating: Int, comment: String) = repo.submitReview(slug, rating, comment)
}

@Composable
fun ReviewsScreen(slug: String, productName: String, canReview: Boolean, onBack: () -> Unit) {
    val repo = repository()
    val vm: ReviewsViewModel = viewModel(key = "reviews-$slug") { ReviewsViewModel(repo, slug) }
    val state by vm.state.collectAsStateWithLifecycle()
    var submitted by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = { BackTopBar("Đánh giá", onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(productName, style = MaterialTheme.typography.titleMedium) }
            if (canReview && !submitted) {
                item { ReviewForm(vm, onSubmitted = { submitted = true }) }
            }
            if (submitted) {
                item { Text("Cảm ơn bạn! Đánh giá đang chờ duyệt.", color = MaterialTheme.colorScheme.secondary) }
            }
            item {
                StateContent(state, onRetry = { vm.load() }) { reviews ->
                    if (reviews.isEmpty()) {
                        MessageBox("Chưa có đánh giá nào.", Modifier.padding(0.dp))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { reviews.forEach { ReviewItem(it) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewForm(vm: ReviewsViewModel, onSubmitted: () -> Unit) {
    var rating by remember { mutableIntStateOf(5) }
    var comment by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Viết đánh giá của bạn", style = MaterialTheme.typography.titleSmall)
            Row {
                (1..5).forEach { i ->
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "$i sao",
                        tint = if (i <= rating) StarYellow else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(36.dp).clickable { rating = i },
                    )
                }
            }
            OutlinedTextField(
                value = comment,
                onValueChange = { if (it.length <= 1000) comment = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Chia sẻ cảm nhận về sản phẩm") },
                minLines = 3,
            )
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        vm.submit(rating, comment)
                            .onSuccess { onSubmitted() }
                            .onFailure { snackbar.showSnackbar(it.message ?: "Không gửi được đánh giá.") }
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Gửi đánh giá") }
        }
    }
}

@Composable
fun ReviewItem(review: Review) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(review.userName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(review.createdAt.toDisplayDate(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Stars(review.rating)
        review.comment?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}
