package com.socialshop.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.socialshop.app.core.resolveBackendUrl
import com.socialshop.app.core.toDisplayDate
import com.socialshop.app.data.AdminRepository
import com.socialshop.app.data.remote.AdminReview
import com.socialshop.app.ui.components.BackTopBar
import com.socialshop.app.ui.components.Stars
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

private val REVIEW_FILTERS = listOf(
    "pending" to "Chờ duyệt",
    "approved" to "Đã duyệt",
    "rejected" to "Đã từ chối",
    null to "Tất cả",
)

@HiltViewModel
class AdminReviewsViewModel @Inject constructor(private val repo: AdminRepository) : PagedAdminViewModel<AdminReview>() {
    var status by mutableStateOf<String?>("pending")
        private set

    init { reload() }

    override suspend fun fetch(page: Int) = repo.reviews(status, page)

    override fun idOf(item: AdminReview) = item.id

    fun selectStatus(value: String?) {
        status = value
        reload()
    }

    fun approve(review: AdminReview) = perform({ repo.approveReview(review.id) }, { it.message }) { afterModeration(it.review) }

    fun reject(review: AdminReview) = perform({ repo.rejectReview(review.id) }, { it.message }) { afterModeration(it.review) }

    fun delete(review: AdminReview) = perform({ repo.deleteReview(review.id) }, { it.message ?: "Đã xóa đánh giá." }) { remove(review.id) }

    /** Đang lọc theo trạng thái khác trạng thái mới → bỏ khỏi danh sách; xem "Tất cả" → cập nhật tại chỗ. */
    private fun afterModeration(review: AdminReview) = if (status == null || status == review.status) replace(review) else remove(review.id)
}

@Composable
fun AdminReviewsScreen(onBack: () -> Unit) {
    val vm: AdminReviewsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<AdminReview?>(null) }
    CollectMessages(vm.messages)

    Scaffold(topBar = { BackTopBar("Duyệt đánh giá", onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Spacer(Modifier.height(8.dp))
            FilterRow(REVIEW_FILTERS, vm.status, vm::selectStatus)
            Spacer(Modifier.height(4.dp))
            PagedAdminList(
                state = state,
                onRetry = vm::reload,
                onLoadMore = vm::loadMore,
                emptyMessage = if (vm.status == "pending") "Không còn đánh giá nào chờ duyệt." else "Không có đánh giá nào.",
                key = { it.id },
            ) { review ->
                ReviewCard(review, onApprove = { vm.approve(review) }, onReject = { vm.reject(review) }, onDelete = { deleting = review })
            }
        }
    }

    deleting?.let { review ->
        ConfirmDialog(
            title = "Xóa đánh giá?",
            message = "Đánh giá của ${review.userName} sẽ bị xóa vĩnh viễn.",
            confirmLabel = "Xóa",
            destructive = true,
            onConfirm = { vm.delete(review) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ReviewCard(review: AdminReview, onApprove: () -> Unit, onReject: () -> Unit, onDelete: () -> Unit) {
    AdminCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(review.productName ?: "Sản phẩm đã xóa", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text("${review.userName} · ${review.createdAt.toDisplayDate()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Pill(review.statusLabel, if (review.status == "approved") PillGreen else if (review.status == "rejected") PillRed else PillAmber)
        }
        Stars(review.rating, Modifier.padding(vertical = 4.dp))
        review.comment?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        review.imageUrl?.let {
            AsyncImage(
                model = it.resolveBackendUrl(),
                contentDescription = "Ảnh đánh giá",
                contentScale = ContentScale.Crop,
                modifier = Modifier.padding(top = 6.dp).size(96.dp).clip(RoundedCornerShape(8.dp)),
            )
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (review.status != "approved") FilledTonalButton(onClick = onApprove) { Text("Duyệt") }
            if (review.status != "rejected") {
                OutlinedButton(onClick = onReject, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Từ chối") }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Xóa đánh giá", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
