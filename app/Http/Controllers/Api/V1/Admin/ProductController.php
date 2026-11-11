<?php

namespace App\Http\Controllers\Api\V1\Admin;

use App\Http\Controllers\Controller;
use App\Http\Resources\Api\Admin\AdminProductResource;
use App\Models\Product;
use App\Models\ProductVariant;
use App\Services\AuditService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;
use Illuminate\Support\Facades\DB;

/**
 * @group Mobile API — Quản trị
 *
 * Trên app chỉnh nhanh giá, giá khuyến mãi, tồn kho và trạng thái bán. Thêm sản phẩm, mô tả và ảnh vẫn làm trên web.
 */
class ProductController extends Controller
{
    /**
     * Danh sách sản phẩm
     *
     * @queryParam search string Tên sản phẩm.
     * @queryParam status string active|inactive|out_of_stock
     * @queryParam low_stock boolean Chỉ sản phẩm sắp hết hàng (tồn ≤ 5).
     */
    public function index(Request $request): AnonymousResourceCollection
    {
        $request->validate([
            'search' => 'nullable|string|max:100',
            'status' => 'nullable|in:active,inactive,out_of_stock',
            'low_stock' => 'nullable|boolean',
        ]);

        $products = Product::with(['category', 'images'])
            ->when($request->input('search'), fn ($q, $search) => $q->whereLike('name', "%{$search}%"))
            ->when($request->input('status'), fn ($q, $status) => $q->where('status', $status))
            ->when($request->boolean('low_stock'), fn ($q) => $q->where('stock', '<=', 5)->orderBy('stock'))
            ->latest()
            ->paginate(15);

        return AdminProductResource::collection($products);
    }

    /**
     * Chi tiết sản phẩm (kèm biến thể size/màu)
     */
    public function show(Product $product): AdminProductResource
    {
        return new AdminProductResource($product->load(['category', 'images', 'variants']));
    }

    /**
     * Sửa nhanh giá / tồn kho / trạng thái
     *
     * Sản phẩm có biến thể: gửi `variants` để sửa tồn và giá từng biến thể; tồn kho tổng tự cộng từ biến thể.
     *
     * @bodyParam price integer required Giá gốc.
     * @bodyParam sale_price integer Giá khuyến mãi, phải nhỏ hơn giá gốc; null để bỏ khuyến mãi.
     * @bodyParam stock integer Tồn kho (chỉ áp dụng khi không có biến thể).
     * @bodyParam status string active|inactive|out_of_stock
     * @bodyParam variants object[] [{id, stock, price|null}]
     */
    public function update(Request $request, Product $product): JsonResponse
    {
        $validated = $request->validate([
            'price' => 'required|integer|min:0',
            'sale_price' => 'nullable|integer|min:0|lt:price',
            'stock' => 'nullable|integer|min:0|max:1000000',
            'status' => 'nullable|in:active,inactive,out_of_stock',
            'variants' => 'nullable|array|max:100',
            'variants.*.id' => 'required|integer',
            'variants.*.stock' => 'required|integer|min:0|max:1000000',
            'variants.*.price' => 'nullable|integer|min:0',
        ], [
            'sale_price.lt' => 'Giá khuyến mãi phải nhỏ hơn giá gốc.',
        ]);

        $before = $product->only(['price', 'sale_price', 'stock', 'status']);

        DB::transaction(function () use ($product, $validated) {
            $data = [
                'price' => $validated['price'],
                'sale_price' => $validated['sale_price'] ?? null,
            ];

            if (isset($validated['status'])) {
                $data['status'] = $validated['status'];
            }

            // Bỏ khuyến mãi thì bỏ luôn mốc kết thúc Flash Sale cũ
            if ($data['sale_price'] === null) {
                $data['sale_ends_at'] = null;
            }

            foreach ($validated['variants'] ?? [] as $row) {
                ProductVariant::where('product_id', $product->id)
                    ->whereKey($row['id'])
                    ->update(['stock' => $row['stock'], 'price' => $row['price'] ?? null]);
            }

            if ($product->variants()->exists()) {
                $data['stock'] = (int) $product->variants()->sum('stock');
            } elseif (isset($validated['stock'])) {
                $data['stock'] = $validated['stock'];
            }

            $product->update($data);
        });

        AuditService::log('product_quick_updated', 'Product', $product->id, [
            'before' => $before,
            'after' => $product->only(['price', 'sale_price', 'stock', 'status']),
            'source' => 'mobile',
        ]);

        return response()->json([
            'message' => 'Đã lưu sản phẩm.',
            'product' => new AdminProductResource($product->fresh(['category', 'images', 'variants'])),
        ]);
    }

    /**
     * Bật / tắt bán
     */
    public function toggle(Product $product): JsonResponse
    {
        $product->update(['status' => $product->status === 'active' ? 'inactive' : 'active']);

        return response()->json([
            'message' => $product->status === 'active' ? 'Đã mở bán sản phẩm.' : 'Đã ngừng bán sản phẩm.',
            'product' => new AdminProductResource($product->fresh(['category', 'images'])),
        ]);
    }
}
