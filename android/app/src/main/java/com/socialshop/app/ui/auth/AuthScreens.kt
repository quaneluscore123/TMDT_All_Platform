package com.socialshop.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.socialshop.app.data.remote.ApiException
import com.socialshop.app.ui.components.BackTopBar
import com.socialshop.app.ui.navigation.LocalSnackbar
import com.socialshop.app.ui.navigation.repository
import kotlinx.coroutines.launch

@Composable
fun FormField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: String? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboardType,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun SubmitButton(text: String, busy: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        if (busy) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp) else Text(text)
    }
}

@Composable
private fun AuthScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = { BackTopBar(title, onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

@Composable
fun LoginScreen(onBack: () -> Unit, onSuccess: () -> Unit, onRegister: () -> Unit, onForgot: () -> Unit) {
    val repo = repository()
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ApiException?>(null) }

    AuthScaffold("Đăng nhập", onBack) {
        Text("Chào mừng trở lại 👋", style = MaterialTheme.typography.headlineSmall)
        Text("Đăng nhập để mua sắm, theo dõi đơn hàng và lưu sản phẩm yêu thích.", style = MaterialTheme.typography.bodyMedium)
        FormField(email, { email = it }, "Email", error?.fieldError("email"), keyboardType = KeyboardType.Email)
        FormField(password, { password = it }, "Mật khẩu", error?.fieldError("password"), password = true)
        error?.takeIf { it.fieldErrors.isEmpty() }?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
        SubmitButton("Đăng nhập", busy) {
            busy = true
            scope.launch {
                repo.login(email, password)
                    .onSuccess { onSuccess() }
                    .onFailure { error = it as? ApiException ?: ApiException(it.message ?: "Lỗi") }
                busy = false
            }
        }
        TextButton(onClick = onForgot) { Text("Quên mật khẩu?") }
        TextButton(onClick = onRegister) { Text("Chưa có tài khoản? Đăng ký") }
    }
}

@Composable
fun RegisterScreen(onBack: () -> Unit, onSuccess: () -> Unit, onLogin: () -> Unit) {
    val repo = repository()
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var referral by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ApiException?>(null) }

    AuthScaffold("Đăng ký", onBack) {
        Text("Tạo tài khoản SocialShop", style = MaterialTheme.typography.headlineSmall)
        FormField(name, { name = it }, "Họ tên", error?.fieldError("name"))
        FormField(email, { email = it }, "Email", error?.fieldError("email"), keyboardType = KeyboardType.Email)
        FormField(password, { password = it }, "Mật khẩu (tối thiểu 8 ký tự)", error?.fieldError("password"), password = true)
        FormField(confirm, { confirm = it }, "Nhập lại mật khẩu", null, password = true)
        FormField(referral, { referral = it }, "Mã giới thiệu (không bắt buộc)", error?.fieldError("referral_code"))
        error?.takeIf { it.fieldErrors.isEmpty() }?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
        SubmitButton("Đăng ký", busy) {
            if (password != confirm) {
                error = ApiException("Mật khẩu nhập lại không khớp.", fieldErrors = mapOf("password" to listOf("Mật khẩu nhập lại không khớp.")))
                return@SubmitButton
            }
            busy = true
            scope.launch {
                repo.register(name, email, password, confirm, referral)
                    .onSuccess { onSuccess() }
                    .onFailure { error = it as? ApiException ?: ApiException(it.message ?: "Lỗi") }
                busy = false
            }
        }
        TextButton(onClick = onLogin) { Text("Đã có tài khoản? Đăng nhập") }
    }
}

@Composable
fun ForgotPasswordScreen(onBack: () -> Unit) {
    val repo = repository()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var email by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sent by rememberSaveable { mutableStateOf<String?>(null) }

    AuthScaffold("Quên mật khẩu", onBack) {
        Text("Nhập email đã đăng ký, chúng tôi sẽ gửi link đặt lại mật khẩu.", style = MaterialTheme.typography.bodyMedium)
        FormField(email, { email = it }, "Email", keyboardType = KeyboardType.Email)
        sent?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        SubmitButton("Gửi link đặt lại", busy) {
            busy = true
            scope.launch {
                repo.forgotPassword(email)
                    .onSuccess { sent = it.message ?: "Đã gửi email." }
                    .onFailure { snackbar.showSnackbar(it.message ?: "Lỗi") }
                busy = false
            }
        }
    }
}
