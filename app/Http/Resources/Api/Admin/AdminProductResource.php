<?php

namespace App\Http\Resources\Api\Admin;

use App\Models\Product;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/** @mixin Product */
class AdminProductResource extends JsonResource
{
    public const STATUS_LABELS = [
        'active' => 'Đang bán',
        'inactive' => 'Ngừng bán',
        'out_of_stock' => 'Hết hàng',
    ];

    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'slug' => $this->slug,
            'name' => $this->name,
            'brand' => $this->brand,
            'image_url' => $this->image_url,
            'category_name' => $this->whenLoaded('category', fn () => $this->category?->name),
            'price' => (int) $this->price,
            'sale_price' => $this->sale_price,
            'effective_price' => $this->effectivePrice(),
            'is_on_sale' => $this->isOnSale(),
            'sale_ends_at' => $this->sale_ends_at?->toIso8601String(),
            'cost_price' => $this->cost_price,
            'gross_margin_percent' => $this->grossMarginPercentage(),
            'stock' => (int) $this->stock,
            'status' => $this->status,
            'status_label' => self::STATUS_LABELS[$this->status] ?? $this->status,
            'views_count' => (int) $this->views_count,
            'variants' => $this->whenLoaded('variants', fn () => $this->variants->map(fn ($v) => [
                'id' => $v->id,
                'size' => $v->size,
                'color' => $v->color,
                'stock' => (int) $v->stock,
                'price' => $v->price !== null ? (int) $v->price : null,
            ])->values()),
        ];
    }
}
