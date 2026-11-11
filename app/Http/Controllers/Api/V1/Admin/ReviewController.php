<?php

namespace App\Http\Controllers\Api\V1\Admin;

use App\Http\Controllers\Controller;
use App\Http\Resources\Api\Admin\AdminReviewResource;
use App\Models\Review;
use App\Services\AuditService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;

/**
 * @group Mobile API — Quản trị
 */
class ReviewController extends Controller
{
    /**
     * Danh sách đánh giá
     *
     * @queryParam status string pending|approved|rejected
     * @queryParam search string Nội dung, tên sản phẩm hoặc tên khách.
     */
    public function index(Request $request): AnonymousResourceCollection
    {
        $request->validate([
            'status' => 'nullable|in:pending,approved,rejected',
            'search' => 'nullable|string|max:100',
        ]);

        $reviews = Review::with(['user', 'product'])
            ->when($request->input('search'), fn ($q, $search) => $q->where(fn ($w) => $w
                ->whereLike('comment', "%{$search}%")
                ->orWhereHas('product', fn ($pq) => $pq->whereLike('name', "%{$search}%"))
                ->orWhereHas('user', fn ($uq) => $uq->whereLike('name', "%{$search}%"))))
            ->when($request->input('status'), fn ($q, $status) => $q->where('status', $status))
            ->latest()
            ->paginate(15);

        return AdminReviewResource::collection($reviews);
    }

    /**
     * Duyệt đánh giá
     */
    public function approve(Review $review): JsonResponse
    {
        return $this->setStatus($review, 'approved', 'Đã duyệt đánh giá.');
    }

    /**
     * Từ chối đánh giá
     */
    public function reject(Review $review): JsonResponse
    {
        return $this->setStatus($review, 'rejected', 'Đã từ chối đánh giá.');
    }

    /**
     * Xóa đánh giá
     */
    public function destroy(Review $review): JsonResponse
    {
        $review->delete();
        AuditService::log('review_deleted', 'Review', $review->id);

        return response()->json(['message' => 'Đã xóa đánh giá.']);
    }

    private function setStatus(Review $review, string $status, string $message): JsonResponse
    {
        $review->update(['status' => $status]);
        AuditService::log('review_'.$status, 'Review', $review->id);

        return response()->json([
            'message' => $message,
            'review' => new AdminReviewResource($review->load(['user', 'product'])),
        ]);
    }
}
