<?php

namespace App\Http\Controllers\Concerns;

use Symfony\Component\HttpFoundation\StreamedResponse;

/**
 * Đóng gói một chuỗi sự kiện thành response text/event-stream ("data: {json}\n\n" mỗi sự kiện).
 */
trait StreamsServerSentEvents
{
    /**
     * @param  callable(): iterable<array<string, mixed>>  $events  gọi lúc bắt đầu stream (lazy)
     */
    protected function sseResponse(callable $events): StreamedResponse
    {
        return response()->stream(function () use ($events) {
            $live = $this->streamsLive();

            if ($live) {
                while (ob_get_level() > 0) {
                    @ob_end_clean();
                }
                ob_implicit_flush(true);
            }

            foreach ($events() as $event) {
                echo 'data: '.json_encode($event)."\n\n";

                if ($live) {
                    if (ob_get_level() > 0) {
                        ob_flush();
                    }
                    flush();
                }
            }
        }, 200, [
            'Content-Type' => 'text/event-stream',
            'Cache-Control' => 'no-cache, no-store, must-revalidate',
            'Connection' => 'keep-alive',
            'X-Accel-Buffering' => 'no',
            'Pragma' => 'no-cache',
        ]);
    }

    /**
     * Đẩy từng sự kiện ra client ngay; khi chạy test thì giữ trong buffer để kiểm tra nội dung stream.
     */
    private function streamsLive(): bool
    {
        return ! app()->runningUnitTests();
    }
}
