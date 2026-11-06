<?php

namespace App\Http\Controllers\Api\V1;

use App\Exceptions\CartException;
use App\Http\Controllers\Controller;
use App\Http\Resources\Api\CartItemResource;
use App\Models\ProductVariant;
use App\Services\Analytics\AnalyticsService;
use App\Services\Cart\CartService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * @group Mobile API — Giỏ hàng
 *
 * Giỏ hàng lưu trên server theo tài khoản (đồng bộ với website).
 */
class CartController extends Controller
{
    public function __construct(
        private CartService $cartService
    ) {}

    /**
     * Xem giỏ hàng
     */
    public function index(Request $request): JsonResponse
    {
        return $this->cartResponse($request->user()->id);
    }

    /**
     * Thêm sản phẩm vào giỏ
     *
     * @bodyParam product_id integer required
     * @bodyParam variant_id integer Bắt buộc nếu sản phẩm có size/màu.
     * @bodyParam quantity integer Mặc định 1.
     */
    public function store(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'product_id' => 'required|integer|exists:products,id',
            'variant_id' => 'nullable|integer|exists:product_variants,id',
            'quantity' => 'nullable|integer|min:1|max:99',
        ]);

        $variantId = isset($validated['variant_id']) ? (int) $validated['variant_id'] : null;

        if ($variantId !== null && ProductVariant::whereKey($variantId)->value('product_id') !== (int) $validated['product_id']) {
            return response()->json(['message' => 'Biến thể không hợp lệ.'], 422);
        }

        try {
            $this->cartService->addItem(
                $request->user()->id,
                (int) $validated['product_id'],
                (int) ($validated['quantity'] ?? 1),
                $variantId
            );
        } catch (CartException $e) {
            return response()->json(['message' => $e->getMessage()], 422);
        }

        app(AnalyticsService::class)->track('add_to_cart', [
            'product_id' => (int) $validated['product_id'],
            'value' => (int) ($validated['quantity'] ?? 1),
        ]);

        return $this->cartResponse($request->user()->id, 'Đã thêm sản phẩm vào giỏ hàng!');
    }

    /**
     * Đổi số lượng một dòng
     */
    public function update(Request $request, int $item): JsonResponse
    {
        $validated = $request->validate(['quantity' => 'required|integer|min:1|max:99']);

        try {
            $this->cartService->updateItem($request->user()->id, $item, (int) $validated['quantity']);
        } catch (CartException $e) {
            return response()->json(['message' => $e->getMessage()], 422);
        }

        return $this->cartResponse($request->user()->id);
    }

    /**
     * Xóa một dòng
     */
    public function destroy(Request $request, int $item): JsonResponse
    {
        $this->cartService->removeItem($request->user()->id, $item);

        return $this->cartResponse($request->user()->id, 'Đã xóa sản phẩm khỏi giỏ hàng!');
    }

    /**
     * Xóa toàn bộ giỏ
     */
    public function clear(Request $request): JsonResponse
    {
        $this->cartService->clearCart($request->user()->id);

        return $this->cartResponse($request->user()->id);
    }

    private function cartResponse(int $userId, ?string $message = null): JsonResponse
    {
        $cart = $this->cartService->getCartWithItems($userId);
        $items = CartItemResource::collection($cart->items)->resolve();

        return response()->json([
            'message' => $message,
            'items' => $items,
            'items_count' => (int) $cart->items->sum('quantity'),
            'subtotal' => (int) collect($items)->sum('subtotal'),
        ]);
    }
}
