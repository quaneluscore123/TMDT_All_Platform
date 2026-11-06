<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Resources\Api\ReviewResource;
use App\Models\Product;
use App\Models\Review;
use App\Services\AdminNotifier;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * @group Mobile API — Đánh giá
 */
class ReviewController extends Controller
{
    /**
     * Gửi đánh giá (chỉ khi đã nhận hàng; chờ admin duyệt)
     */
    public function store(Request $request, Product $product): JsonResponse
    {
        $user = $request->user();

        if ($user->reviews()->where('product_id', $product->id)->exists()) {
            return response()->json(['message' => 'Bạn đã đánh giá sản phẩm này rồi.'], 422);
        }

        $completedOrder = $user->orders()
            ->whereHas('items', fn ($q) => $q->where('product_id', $product->id))
            ->where('status', 'delivered')
            ->latest()
            ->first();

        if (! $completedOrder) {
            return response()->json(['message' => 'Bạn cần mua và nhận hàng sản phẩm này trước khi đánh giá.'], 422);
        }

        $validated = $request->validate([
            'rating' => 'required|integer|min:1|max:5',
            'comment' => 'nullable|string|max:1000',
            'image' => 'nullable|mimes:jpg,jpeg,png,webp|max:2048',
        ], [
            'rating.required' => 'Vui lòng chọn số sao.',
            'image.mimes' => 'Hình ảnh phải là JPG, PNG hoặc WebP.',
            'image.max' => 'Hình ảnh tối đa 2MB.',
        ]);

        if ($request->hasFile('image')) {
            $validated['image'] = $request->file('image')->store('reviews', 'public');
        }

        $review = Review::create($validated + [
            'user_id' => $user->id,
            'product_id' => $product->id,
            'order_id' => $completedOrder->id,
            'status' => 'pending',
        ]);
        app(AdminNotifier::class)->newReview($review);

        return response()->json([
            'message' => 'Đánh giá của bạn đã được gửi và đang chờ duyệt!',
            'review' => new ReviewResource($review->load('user')),
        ], 201);
    }
}
