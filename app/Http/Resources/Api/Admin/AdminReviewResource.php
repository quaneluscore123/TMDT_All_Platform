<?php

namespace App\Http\Resources\Api\Admin;

use App\Models\Review;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/** @mixin Review */
class AdminReviewResource extends JsonResource
{
    public const STATUS_LABELS = [
        'pending' => 'Chờ duyệt',
        'approved' => 'Đã duyệt',
        'rejected' => 'Đã từ chối',
    ];

    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'rating' => (int) $this->rating,
            'comment' => $this->comment,
            'image_url' => $this->image ? asset('storage/'.$this->image) : null,
            'status' => $this->status,
            'status_label' => self::STATUS_LABELS[$this->status] ?? $this->status,
            'user_name' => $this->user?->name ?? 'Khách hàng',
            'product_id' => $this->product_id,
            'product_name' => $this->product?->name,
            'product_slug' => $this->product?->slug,
            'created_at' => $this->created_at?->toIso8601String(),
        ];
    }
}
