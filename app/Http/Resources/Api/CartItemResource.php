<?php

namespace App\Http\Resources\Api;

use App\Models\CartItem;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/** @mixin CartItem */
class CartItemResource extends JsonResource
{
    public function toArray(Request $request): array
    {
        $unit = $this->variant?->unitPrice() ?? $this->product->effectivePrice();

        return [
            'id' => $this->id,
            'product_id' => $this->product_id,
            'product_slug' => $this->product->slug,
            'name' => $this->product->name,
            'image_url' => $this->product->image_url,
            'variant_id' => $this->variant_id,
            'variant_label' => $this->variant?->label(),
            'quantity' => $this->quantity,
            'unit_price' => $unit,
            'subtotal' => $unit * $this->quantity,
            'stock' => $this->variant ? $this->variant->stock : $this->product->stock,
        ];
    }
}
