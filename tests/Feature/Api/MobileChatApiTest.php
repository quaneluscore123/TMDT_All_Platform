<?php

namespace Tests\Feature\Api;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Config;
use Illuminate\Support\Facades\Http;
use Tests\TestCase;

class MobileChatApiTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        Config::set('services.gemini.key', '');
        Config::set('services.ai_service.url', null);
    }

    /**
     * @return array<int, array<string, mixed>>
     */
    private function events(string $content): array
    {
        return collect(explode("\n\n", trim($content)))
            ->filter()
            ->map(fn ($line) => json_decode(substr($line, strlen('data: ')), true))
            ->values()
            ->all();
    }

    public function test_guest_gets_streamed_answer(): void
    {
        $response = $this->postJson('/api/v1/chat', ['message' => 'Xin chào']);

        $response->assertOk();
        $this->assertStringStartsWith('text/event-stream', $response->headers->get('Content-Type'));

        $events = $this->events($response->streamedContent());
        $this->assertNotEmpty($events);
        $this->assertTrue(end($events)['done']);
        $this->assertNotSame('', $events[0]['text']);
    }

    public function test_policy_question_is_answered_without_calling_ai(): void
    {
        Http::fake();

        $content = $this->postJson('/api/v1/chat', ['message' => 'Chính sách đổi trả thế nào?'])->streamedContent();

        $this->assertStringContainsString('"done":true', $content);
        Http::assertNothingSent();
    }

    public function test_message_is_validated(): void
    {
        $this->postJson('/api/v1/chat', [])->assertUnprocessable()->assertJsonValidationErrors('message');
        $this->postJson('/api/v1/chat', ['message' => str_repeat('a', 1001)])->assertJsonValidationErrors('message');
    }

    public function test_bearer_token_is_optional_and_accepted(): void
    {
        $token = User::factory()->create()->createToken('mobile')->plainTextToken;

        $response = $this->withToken($token)->postJson('/api/v1/chat', [
            'message' => 'Đơn hàng của tôi thế nào?',
            'history' => [['type' => 'user', 'content' => 'Chào'], ['type' => 'bot', 'content' => 'Chào bạn'], 'rác'],
        ]);

        $response->assertOk();
        $this->assertStringContainsString('"done":true', $response->streamedContent());
    }
}
