<?php

namespace App\Http\Resources\Api;

use App\Models\Product;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * Dữ liệu product card (danh sách, trang chủ, yêu thích).
 *
 * @mixin Product
 */
class ProductResource extends JsonResource
{
    public function toArray(Request $request): array
    {
        $onSale = $this->isOnSale();
        $price = $this->effectivePrice();

        return [
            'id' => $this->id,
            'slug' => $this->slug,
            'name' => $this->name,
            'brand' => $this->brand,
            'price' => $price,
            'original_price' => $this->price,
            'is_on_sale' => $onSale,
            'discount_percent' => $onSale && $this->price > 0 ? (int) round(($this->price - $price) / $this->price * 100) : 0,
            'sale_ends_at' => $onSale ? $this->sale_ends_at?->toIso8601String() : null,
            'image_url' => $this->image_url,
            'rating' => $this->average_rating,
            'reviews_count' => $this->reviews_count,
            'stock' => $this->stock,
            'has_variants' => isset($this->variants_count) ? $this->variants_count > 0 : $this->hasVariants(),
            'category' => $this->whenLoaded('category', fn () => $this->category ? [
                'id' => $this->category->id,
                'name' => $this->category->name,
                'slug' => $this->category->slug,
            ] : null),
        ];
    }
}
