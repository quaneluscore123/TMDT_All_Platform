package com.socialshop.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.socialshop.app.BuildConfig
import com.socialshop.app.data.remote.ApiException
import com.socialshop.app.ui.auth.FormField
import com.socialshop.app.ui.auth.SubmitButton
import com.socialshop.app.ui.components.BackTopBar
import com.socialshop.app.ui.components.MessageBox
import com.socialshop.app.ui.components.clickableRow
import com.socialshop.app.ui.navigation.LocalSnackbar
import com.socialshop.app.ui.navigation.repository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onOrders: () -> Unit,
    onEditProfile: () -> Unit,
    onChangePassword: () -> Unit,
) {
    val repo = repository()
    val token by repo.token.collectAsStateWithLifecycle()
    val user by repo.user.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(topBar = { TopAppBar(title = { Text("Tài khoản") }) }) { padding ->
        Box(Modifier.padding(padding)) {
            if (token == null) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Bạn chưa đăng nhập", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.size(8.dp))
                    Text("Đăng nhập để theo dõi đơn hàng và nhận ưu đãi.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.size(24.dp))
                    Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Đăng nhập") }
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth()) { Text("Tạo tài khoản") }
                }
                return@Box
            }

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(56.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    user?.name?.firstOrNull()?.uppercase() ?: "?",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Spacer(Modifier.size(16.dp))
                        Column {
                            Text(user?.name ?: "…", style = MaterialTheme.typography.titleMedium)
                            Text(user?.email.orEmpty(), style = MaterialTheme.typography.bodySmall)
                            user?.phone?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    MenuRow(Icons.AutoMirrored.Filled.List, "Đơn hàng của tôi", onOrders)
                    HorizontalDivider()
                    MenuRow(Icons.Filled.Edit, "Thông tin cá nhân", onEditProfile)
                    HorizontalDivider()
                    MenuRow(Icons.Filled.Lock, "Đổi mật khẩu", onChangePassword)
                    user?.referralCode?.let { code ->
                        HorizontalDivider()
                        MenuRow(Icons.Filled.Share, "Mời bạn bè (mã: $code)") {
                            val link = "${BuildConfig.BASE_URL}register?ref=$code"
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, "Mua sắm cùng mình trên SocialShop! Đăng ký bằng mã $code: $link")
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Chia sẻ mã giới thiệu"))
                        }
                    }
                }

                OutlinedButton(onClick = { scope.launch { repo.logout() } }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Đăng xuất")
                }
                Text(
                    "Phiên bản ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickableRow(onClick),
    )
}

@Composable
fun EditProfileScreen(onBack: () -> Unit) {
    val repo = repository()
    val user by repo.user.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val current = user

    Scaffold(topBar = { BackTopBar("Thông tin cá nhân", onBack) }) { padding ->
        if (current == null) {
            MessageBox("Vui lòng đăng nhập.", Modifier.padding(padding))
            return@Scaffold
        }
        var name by remember { mutableStateOf(current.name) }
        var phone by remember { mutableStateOf(current.phone.orEmpty()) }
        var address by remember { mutableStateOf(current.address.orEmpty()) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<ApiException?>(null) }

        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormField(name, { name = it }, "Họ tên", error?.fieldError("name"))
            FormField(current.email, {}, "Email (không đổi được)")
            FormField(phone, { phone = it }, "Số điện thoại", error?.fieldError("phone"), keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
            FormField(address, { address = it }, "Địa chỉ mặc định", error?.fieldError("address"))
            SubmitButton("Lưu thay đổi", busy) {
                busy = true
                scope.launch {
                    repo.updateProfile(name, phone.ifBlank { null }, address.ifBlank { null })
                        .onSuccess { snackbar.showSnackbar("Cập nhật thông tin thành công!"); onBack() }
                        .onFailure { error = it as? ApiException; snackbar.showSnackbar(it.message ?: "Lỗi") }
                    busy = false
                }
            }
        }
    }
}

@Composable
fun ChangePasswordScreen(onBack: () -> Unit) {
    val repo = repository()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ApiException?>(null) }

    Scaffold(topBar = { BackTopBar("Đổi mật khẩu", onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FormField(current, { current = it }, "Mật khẩu hiện tại", error?.fieldError("current_password"), password = true)
            FormField(new, { new = it }, "Mật khẩu mới (tối thiểu 8 ký tự)", error?.fieldError("password"), password = true)
            FormField(confirm, { confirm = it }, "Nhập lại mật khẩu mới", null, password = true)
            SubmitButton("Đổi mật khẩu", busy) {
                busy = true
                scope.launch {
                    repo.changePassword(current, new, confirm)
                        .onSuccess { snackbar.showSnackbar(it.message ?: "Đổi mật khẩu thành công!"); onBack() }
                        .onFailure { error = it as? ApiException; snackbar.showSnackbar(it.message ?: "Lỗi") }
                    busy = false
                }
            }
        }
    }
}
