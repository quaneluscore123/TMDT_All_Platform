<?php

namespace App\Http\Resources\Api\Admin;

use App\Models\Coupon;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/** @mixin Coupon */
class AdminCouponResource extends JsonResource
{
    public function toArray(Request $request): array
    {
        $expired = $this->end_at !== null && $this->end_at->isPast();

        return [
            'id' => $this->id,
            'code' => $this->code,
            'type' => $this->type,
            'value' => (int) $this->value,
            'max_discount' => $this->max_discount !== null ? (int) $this->max_discount : null,
            'min_order_amount' => (int) $this->min_order_amount,
            'start_at' => $this->start_at?->toIso8601String(),
            'end_at' => $this->end_at?->toIso8601String(),
            'usage_limit' => $this->usage_limit !== null ? (int) $this->usage_limit : null,
            'used_count' => (int) ($this->usages_count ?? $this->used_count ?? 0),
            'status' => $this->status,
            'is_expired' => $expired,
            'created_at' => $this->created_at?->toIso8601String(),
        ];
    }
}
