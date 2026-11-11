package com.socialshop.app.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.socialshop.app.core.toDisplayDate
import com.socialshop.app.data.AdminRepository
import com.socialshop.app.data.remote.AdminUser
import com.socialshop.app.ui.components.BackTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

private val USER_FILTERS = listOf(null to "Tất cả", "active" to "Đang hoạt động", "blocked" to "Đã khóa")

@HiltViewModel
class AdminUsersViewModel @Inject constructor(private val repo: AdminRepository) : PagedAdminViewModel<AdminUser>() {
    var status by mutableStateOf<String?>(null)
        private set
    var search by mutableStateOf("")

    init { reload() }

    override suspend fun fetch(page: Int) = repo.users(search, status, page)

    override fun idOf(item: AdminUser) = item.id

    fun selectStatus(value: String?) {
        status = value
        reload()
    }

    fun toggle(user: AdminUser) = perform({ repo.toggleUser(user.id) }, { it.message }) { replace(it.user) }
}

@Composable
fun AdminUsersScreen(onBack: () -> Unit) {
    val vm: AdminUsersViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<AdminUser?>(null) }
    CollectMessages(vm.messages)

    Scaffold(topBar = { BackTopBar("Người dùng", onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(vm.search, { vm.search = it }, "Tên hoặc email", onSearch = vm::reload)
            FilterRow(USER_FILTERS, vm.status, vm::selectStatus)
            Spacer(Modifier.height(4.dp))
            PagedAdminList(
                state = state,
                onRetry = vm::reload,
                onLoadMore = vm::loadMore,
                emptyMessage = "Không tìm thấy người dùng.",
                key = { it.id },
                header = { page -> item { Text("${page.total} tài khoản", style = MaterialTheme.typography.bodySmall) } },
            ) { user -> UserRow(user, onToggle = { confirm = user }) }
        }
    }

    confirm?.let { user ->
        ConfirmDialog(
            title = if (user.isActive) "Khóa tài khoản?" else "Mở khóa tài khoản?",
            message = if (user.isActive) {
                "${user.name} sẽ bị đăng xuất khỏi app và không đăng nhập được cho tới khi mở khóa."
            } else {
                "${user.name} sẽ đăng nhập và mua hàng lại được."
            },
            confirmLabel = if (user.isActive) "Khóa" else "Mở khóa",
            destructive = user.isActive,
            onConfirm = { vm.toggle(user) },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun UserRow(user: AdminUser, onToggle: () -> Unit) {
    val isAdmin = user.role == "admin"
    AdminCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(42.dp)) {
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(user.name.firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(user.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                    if (isAdmin) {
                        Spacer(Modifier.size(6.dp))
                        Pill("Admin", PillBlue)
                    }
                }
                Text(user.email, style = MaterialTheme.typography.bodySmall)
                Text(
                    "${user.ordersCount} đơn · tham gia ${user.createdAt.toDisplayDate().substringBefore(' ')}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isAdmin) {
                Pill("Hoạt động", PillGreen)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(checked = user.isActive, onCheckedChange = { onToggle() })
                    Text(if (user.isActive) "Hoạt động" else "Đã khóa", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
