<?php

namespace App\Http\Resources\Api\Admin;

use App\Http\Resources\Api\OrderResource;
use App\Models\Order;
use Illuminate\Http\Request;

/**
 * Đơn hàng cho màn quản trị: như OrderResource của khách, thêm thông tin người mua và
 * các trạng thái admin được phép chuyển sang.
 *
 * @mixin Order
 */
class AdminOrderResource extends OrderResource
{
    public function toArray(Request $request): array
    {
        return [
            ...parent::toArray($request),
            'customer' => $this->whenLoaded('user', fn () => $this->user ? [
                'id' => $this->user->id,
                'name' => $this->user->name,
                'email' => $this->user->email,
                'phone' => $this->user->phone,
            ] : null),
            'next_statuses' => $this->nextStatuses(),
        ];
    }

    /**
     * Trạng thái có thể chọn tiếp (khớp quy tắc của OrderService::changeStatusByAdmin).
     *
     * @return array<int, array{value: string, label: string}>
     */
    private function nextStatuses(): array
    {
        if ($this->status === 'cancelled') {
            return [];
        }

        $unpaidOnline = $this->payment_method !== 'cod' && $this->payment_status !== 'paid';

        return collect(self::STATUS_LABELS)
            ->reject(fn ($label, $status) => $status === $this->status)
            ->reject(fn ($label, $status) => $unpaidOnline && in_array($status, ['confirmed', 'shipping', 'delivered'], true))
            ->map(fn ($label, $status) => ['value' => $status, 'label' => $label])
            ->values()
            ->all();
    }
}
