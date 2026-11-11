package com.socialshop.app.ui.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.socialshop.app.data.ChatRepository
import com.socialshop.app.data.ChatTurn
import com.socialshop.app.data.ShopRepository
import com.socialshop.app.ui.components.BackTopBar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(val fromUser: Boolean, val text: String, val failed: Boolean = false)

private const val GREETING =
    "Xin chào! Mình là trợ lý của SocialShop 👋 Mình có thể tư vấn sản phẩm, gợi ý chọn size, giải đáp phí ship, " +
        "đổi trả và (khi bạn đã đăng nhập) tra cứu đơn hàng của bạn."

private val SUGGESTIONS = listOf(
    "Sản phẩm nào đang giảm giá?",
    "Mình cao 1m70 nặng 65kg mặc size gì?",
    "Phí ship ra Hà Nội bao nhiêu?",
    "Chính sách đổi trả thế nào?",
    "Đơn hàng gần nhất của tôi đang ở đâu?",
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chat: ChatRepository,
    shop: ShopRepository,
) : ViewModel() {
    val messages = mutableStateListOf(ChatMessage(fromUser = false, text = GREETING))
    var streaming by mutableStateOf(false)
        private set
    val loggedIn = shop.isLoggedIn

    private var job: Job? = null

    fun send(text: String) {
        val question = text.trim()
        if (question.isEmpty() || streaming) return

        // Ngữ cảnh gửi lên: các lượt trước (bỏ lời chào mặc định và câu lỗi)
        val history = messages.drop(1).filterNot { it.failed }.map { ChatTurn(if (it.fromUser) "user" else "bot", it.text) }
        messages += ChatMessage(fromUser = true, text = question)
        messages += ChatMessage(fromUser = false, text = "")
        val replyIndex = messages.lastIndex
        streaming = true

        job = viewModelScope.launch {
            chat.ask(question, history)
                .catch { e ->
                    messages[replyIndex] = ChatMessage(false, e.message ?: "Trợ lý đang bận, vui lòng thử lại.", failed = true)
                }
                .collect { chunk ->
                    messages[replyIndex] = messages[replyIndex].copy(text = messages[replyIndex].text + chunk)
                }
            if (messages[replyIndex].text.isBlank()) {
                messages[replyIndex] = ChatMessage(false, "Mình chưa trả lời được câu này, bạn thử hỏi cách khác nhé.", failed = true)
            }
            streaming = false
        }
    }

    fun clear() {
        job?.cancel()
        streaming = false
        messages.clear()
        messages += ChatMessage(fromUser = false, text = GREETING)
    }
}

@Composable
fun ChatScreen(initialMessage: String?, onBack: () -> Unit) {
    val vm: ChatViewModel = hiltViewModel()
    var input by rememberSaveable { mutableStateOf("") }
    var initialSent by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val lastText = vm.messages.lastOrNull()?.text

    // Mở từ trang sản phẩm: hỏi luôn câu gợi sẵn
    LaunchedEffect(initialMessage) {
        if (!initialSent && !initialMessage.isNullOrBlank()) {
            initialSent = true
            vm.send(initialMessage)
        }
    }
    // Giữ màn hình ở tin nhắn mới nhất khi có tin mới hoặc chữ đang chạy
    LaunchedEffect(vm.messages.size, lastText?.length) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.lastIndex)
    }

    Scaffold(
        topBar = {
            BackTopBar("Trợ lý AI", onBack) {
                IconButton(onClick = vm::clear) { Icon(Icons.Filled.Delete, contentDescription = "Xóa hội thoại") }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(vm.messages) { index, message ->
                    val typing = vm.streaming && index == vm.messages.lastIndex && message.text.isEmpty()
                    Bubble(message, typing)
                }
            }

            if (vm.messages.size <= 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SUGGESTIONS.filter { vm.loggedIn || !it.contains("của tôi") }.forEach { suggestion ->
                        AssistChip(onClick = { vm.send(suggestion) }, label = { Text(suggestion) })
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(1000) },
                    placeholder = { Text("Hỏi về sản phẩm, size, phí ship…") },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { vm.send(input); input = "" }),
                    modifier = Modifier.weight(1f),
                )
                FilledIconButton(
                    onClick = { vm.send(input); input = "" },
                    enabled = input.isNotBlank() && !vm.streaming,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    if (vm.streaming) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gửi")
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage, typing: Boolean) {
    val user = message.fromUser
    Box(Modifier.fillMaxWidth(), contentAlignment = if (user) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            color = when {
                user -> MaterialTheme.colorScheme.primary
                message.failed -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (user) 16.dp else 4.dp,
                bottomEnd = if (user) 4.dp else 16.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                if (typing) "Đang soạn câu trả lời…" else message.text.cleanMarkdown(),
                color = when {
                    user -> MaterialTheme.colorScheme.onPrimary
                    message.failed -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/** AI hay trả markdown (**đậm**, gạch đầu dòng "* "): bỏ ký hiệu để đọc gọn trên điện thoại. */
private fun String.cleanMarkdown(): String = this
    .replace("**", "")
    .replace(Regex("(?m)^\\s*[*-]\\s+"), "• ")
