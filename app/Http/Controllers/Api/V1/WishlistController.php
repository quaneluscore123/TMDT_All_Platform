<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Resources\Api\ProductResource;
use App\Models\Product;
use App\Models\Wishlist;
use App\Models\WishlistItem;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;

/**
 * @group Mobile API — Yêu thích
 */
class WishlistController extends Controller
{
    /**
     * Danh sách yêu thích
     */
    public function index(Request $request): AnonymousResourceCollection
    {
        $userId = $request->user()->id;

        $products = Product::whereHas('wishlistItems', fn ($q) => $q->whereHas('wishlist', fn ($w) => $w->where('user_id', $userId)))
            ->where('status', 'active')
            ->with('category')
            ->withCardData()
            ->latest()
            ->paginate(20);

        return ProductResource::collection($products);
    }

    /**
     * Thêm / bỏ yêu thích
     */
    public function toggle(Request $request, int $product): JsonResponse
    {
        $product = Product::where('status', 'active')->findOrFail($product);
        $wishlist = Wishlist::firstOrCreate(['user_id' => $request->user()->id]);

        $existing = WishlistItem::where('wishlist_id', $wishlist->id)
            ->where('product_id', $product->id)
            ->first();

        if ($existing) {
            $existing->delete();
        } else {
            WishlistItem::create(['wishlist_id' => $wishlist->id, 'product_id' => $product->id]);
        }

        return response()->json([
            'is_wishlisted' => ! $existing,
            'wishlist_count' => $wishlist->items()->count(),
            'message' => $existing ? 'Đã bỏ yêu thích' : 'Đã thêm vào yêu thích',
        ]);
    }
}
