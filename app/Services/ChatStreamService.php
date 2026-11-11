<?php

namespace App\Services;

use GuzzleHttp\Client;
use Illuminate\Support\Facades\Log;

/**
 * Luồng trả lời chatbot dùng chung cho web (POST /chat/stream) và app (POST /api/v1/chat).
 *
 * Sinh ra các sự kiện {text?: string, done?: true}; controller chỉ việc đóng gói thành SSE.
 * Thứ tự ưu tiên: câu hỏi chính sách (trả lời cố định) → microservice ai-service → Gemini trực tiếp
 * → câu trả lời dự phòng từ dữ liệu cửa hàng khi không có khóa AI hoặc AI lỗi.
 */
class ChatStreamService
{
    public function __construct(private ChatbotService $chatbot) {}

    /**
     * @param  array<int, array{type: string, content: string}>  $history  đã qua sanitizeHistory()
     * @return \Generator<int, array{text?: string, done?: bool}>
     */
    public function events(string $message, array $history, ?int $userId): \Generator
    {
        // Câu hỏi chính sách (đổi trả, hoàn tiền, bảo hành, khiếu nại): trả lời cố định, không gọi AI
        if ($policy = $this->chatbot->policyAnswer($message)) {
            yield ['text' => $policy, 'done' => true];

            return;
        }

        // Guest ($userId = null) chỉ kèm context sản phẩm, không lộ đơn hàng
        $contextParts = $this->chatbot->buildContext($message, $userId);

        $apiKey = $this->chatbot->getApiKey();
        $serviceUrl = config('services.ai_service.url');

        if (! $serviceUrl && (empty($apiKey) || $apiKey === 'test')) {
            yield $this->fallback($message, $userId);

            return;
        }

        try {
            // Ưu tiên microservice ai-service (giữ khóa Gemini); không cấu hình thì gọi Gemini trực tiếp
            if ($serviceUrl) {
                $url = rtrim($serviceUrl, '/').'/v1/chat/stream';
                $headers = ['Authorization' => 'Bearer '.config('services.ai_service.token')];
            } else {
                $url = "https://generativelanguage.googleapis.com/v1beta/models/{$this->chatbot->getModel()}:streamGenerateContent?alt=sse";
                $headers = ['x-goog-api-key' => $apiKey];
            }

            $contents = [];
            foreach ($history as $msg) {
                $contents[] = [
                    'role' => $msg['type'] === 'user' ? 'user' : 'model',
                    'parts' => [['text' => $msg['content']]],
                ];
            }

            $response = app(Client::class)->post($url, [
                'json' => [
                    'systemInstruction' => [
                        'parts' => [['text' => $this->chatbot->getSystemPrompt().$contextParts]],
                    ],
                    'contents' => $contents,
                    'generationConfig' => [
                        'temperature' => 0.7,
                        'maxOutputTokens' => 768,
                        'thinkingConfig' => ['thinkingBudget' => 0],
                    ],
                ],
                'headers' => $headers,
                'timeout' => 20,
                'stream' => true,
            ]);

            if ($response->getStatusCode() !== 200) {
                throw new \RuntimeException('Gemini API returned status: '.$response->getStatusCode());
            }
        } catch (\Throwable $e) {
            Log::error('Chat streaming error', ['message' => $e->getMessage()]);

            yield $this->fallback($message, $userId);

            return;
        }

        $body = $response->getBody();
        $buffer = '';

        try {
            while (! $body->eof()) {
                $chunk = $body->read(4096);
                if ($chunk === '') {
                    break;
                }
                $buffer .= $chunk;

                while (($pos = strpos($buffer, "\n")) !== false) {
                    $line = trim(substr($buffer, 0, $pos));
                    $buffer = substr($buffer, $pos + 1);

                    if (! str_starts_with($line, 'data: ')) {
                        continue;
                    }

                    $jsonStr = substr($line, 6);
                    if ($jsonStr === '[DONE]') {
                        yield ['done' => true];

                        return;
                    }

                    $data = json_decode($jsonStr, true);
                    $text = is_array($data) ? ($data['candidates'][0]['content']['parts'][0]['text'] ?? null) : null;

                    if (is_string($text)) {
                        yield ['text' => $text];
                    }
                }
            }
        } catch (\Throwable $e) {
            // Mất kết nối giữa chừng: giữ phần đã trả lời, kết thúc lượt bình thường
            Log::warning('Chat stream interrupted', ['message' => $e->getMessage()]);
        } finally {
            $body->close();
        }

        yield ['done' => true];
    }

    /**
     * Chỉ giữ 20 lượt gần nhất, đúng cấu trúc {type: user|bot, content: string ≤ 2000 ký tự};
     * phần tử sai định dạng bị bỏ qua thay vì làm lỗi 500.
     *
     * @return array<int, array{type: string, content: string}>
     */
    public function sanitizeHistory(mixed $history): array
    {
        if (! is_array($history)) {
            return [];
        }

        $clean = [];

        foreach ($history as $msg) {
            if (! is_array($msg)
                || ! in_array($msg['type'] ?? null, ['user', 'bot'], true)
                || ! is_string($msg['content'] ?? null)) {
                continue;
            }

            $clean[] = [
                'type' => $msg['type'],
                'content' => mb_substr($msg['content'], 0, 2000),
            ];
        }

        return array_slice($clean, -20);
    }

    /**
     * @return array{text: string, done: true}
     */
    private function fallback(string $message, ?int $userId): array
    {
        return ['text' => $this->chatbot->sendMessage($message, [], $userId), 'done' => true];
    }
}
