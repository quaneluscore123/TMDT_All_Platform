<?php

namespace App\Http\Controllers;

use App\Http\Controllers\Concerns\StreamsServerSentEvents;
use App\Services\ChatStreamService;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;

class ChatController extends Controller
{
    use StreamsServerSentEvents;

    public function stream(Request $request, ChatStreamService $chat)
    {
        $message = $request->input('message', '');

        if (! is_string($message) || trim($message) === '') {
            return response()->json(['error' => 'Message is required'], 400);
        }

        if (mb_strlen($message) > 1000) {
            return response()->json(['error' => 'Tin nhắn tối đa 1000 ký tự.'], 422);
        }

        $history = $chat->sanitizeHistory($request->input('history', []));
        $userId = Auth::id();

        return $this->sseResponse(fn () => $chat->events($message, $history, $userId));
    }
}
