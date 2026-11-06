<?php

namespace App\Http\Resources\Api;

use App\Models\Product;
use Illuminate\Http\Request;

/**
 * Chi tiết sản phẩm: thêm mô tả, bộ ảnh, biến thể size/màu và các thông tin theo người xem.
 *
 * @mixin Product
 */
class ProductDetailResource extends ProductResource
{
    /** @var array<string, mixed> */
    private array $extra = [];

    public function withExtra(array $extra): static
    {
        $this->extra = $extra;

        return $this;
    }

    public function toArray(Request $request): array
    {
        $variants = $this->relationLoaded('variants') ? $this->variants : collect();

        return array_merge(parent::toArray($request), [
            'sku' => $this->sku,
            'description' => $this->description,
            'images' => $this->images->map(fn ($image) => $image->url)->values(),
            'variants' => $variants->map(fn ($v) => [
                'id' => $v->id,
                'size' => $v->size,
                'color' => $v->color,
                'label' => $v->label(),
                'stock' => $v->stock,
                'price' => $v->unitPrice(),
            ])->values(),
            'has_variants' => $variants->isNotEmpty(),
            'total_stock' => $variants->isNotEmpty() ? (int) $variants->sum('stock') : $this->stock,
            'views_count' => $this->showsViewCount() ? $this->views_count : null,
            'web_url' => route('products.show', $this->resource),
        ], $this->extra);
    }
}
