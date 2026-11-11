<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Concerns\StreamsServerSentEvents;
use App\Http\Controllers\Controller;
use App\Services\ChatStreamService;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\StreamedResponse;

/**
 * @group Mobile API — Chatbot AI
 *
 * Cùng trợ lý với website: câu hỏi chính sách trả lời cố định; còn lại gọi Gemini (qua ai-service) kèm
 * dữ liệu sản phẩm. Gửi kèm Bearer token thì trợ lý tra được đơn hàng của chính khách đó.
 */
class ChatController extends Controller
{
    use StreamsServerSentEvents;

    /**
     * Hỏi trợ lý (stream)
     *
     * Trả về `text/event-stream`: mỗi dòng `data: {"text": "..."}` là một đoạn câu trả lời, sự kiện cuối có `"done": true`.
     *
     * @bodyParam message string required Câu hỏi, tối đa 1000 ký tự. Example: Áo hoodie nào đang giảm giá?
     * @bodyParam history object[] Tối đa 20 lượt trước đó, mỗi phần tử {type: user|bot, content}.
     */
    public function stream(Request $request, ChatStreamService $chat): StreamedResponse
    {
        $validated = $request->validate([
            'message' => 'required|string|max:1000',
            'history' => 'nullable|array',
        ], [
            'message.required' => 'Vui lòng nhập câu hỏi.',
            'message.max' => 'Tin nhắn tối đa 1000 ký tự.',
        ]);

        $message = trim($validated['message']);
        abort_if($message === '', 422, 'Vui lòng nhập câu hỏi.');

        // Route công khai: token hợp lệ (và tài khoản còn hoạt động) thì trả lời theo khách đó, không thì như khách vãng lai
        $user = $request->user('sanctum');
        $userId = $user?->is_active ? $user->id : null;

        $history = $chat->sanitizeHistory($validated['history'] ?? []);

        return $this->sseResponse(fn () => $chat->events($message, $history, $userId));
    }
}
