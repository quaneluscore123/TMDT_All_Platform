<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Resources\Api\CategoryResource;
use App\Http\Resources\Api\ProductDetailResource;
use App\Http\Resources\Api\ProductResource;
use App\Http\Resources\Api\ReviewResource;
use App\Models\Category;
use App\Models\Product;
use App\Models\ShippingZone;
use App\Models\WishlistItem;
use App\Services\Analytics\AnalyticsService;
use App\Services\Catalog\ProductFilter;
use App\Services\Order\PricingService;
use App\Services\RecommendationService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;
use Illuminate\Validation\Rule;

/**
 * @group Mobile API — Danh mục & sản phẩm
 *
 * Các endpoint công khai; gửi kèm Bearer token (nếu có) để nhận is_wishlisted / can_review.
 */
class CatalogController extends Controller
{
    public function __construct(
        private ProductFilter $filter,
        private RecommendationService $recommendations
    ) {}

    /**
     * Trang chủ
     *
     * @unauthenticated
     */
    public function home(): JsonResponse
    {
        $flashSale = Product::with('category')->withCardData()
            ->where('status', 'active')
            ->flashSale()
            ->orderBy('sale_ends_at')
            ->limit(8)
            ->get();

        $newProducts = Product::with('category')->withCardData()
            ->where('status', 'active')
            ->latest()
            ->limit(8)
            ->get();

        $sections = collect([
            ['key' => 'hot', 'title' => 'Đang hot', 'products' => $this->recommendations->hot()],
            ['key' => 'best', 'title' => 'Bán chạy', 'products' => $this->recommendations->bestSellers()],
            ['key' => 'rated', 'title' => 'Đánh giá cao', 'products' => $this->recommendations->topRated()],
            ['key' => 'new', 'title' => 'Hàng mới về', 'products' => $newProducts],
        ])->filter(fn ($s) => $s['products']->isNotEmpty())
            ->map(fn ($s) => [
                'key' => $s['key'],
                'title' => $s['title'],
                'products' => ProductResource::collection($s['products']->loadMissing('category')),
            ])->values();

        return response()->json([
            'flash_sale' => [
                'ends_at' => $flashSale->min('sale_ends_at')?->toIso8601String(),
                'products' => ProductResource::collection($flashSale),
            ],
            'categories' => CategoryResource::collection($this->categoriesWithCounts()->whereNull('parent_id')->values()),
            'sections' => $sections,
        ]);
    }

    /**
     * Danh mục
     *
     * @unauthenticated
     */
    public function categories(): AnonymousResourceCollection
    {
        return CategoryResource::collection($this->categoriesWithCounts());
    }

    /**
     * Danh sách / tìm kiếm sản phẩm
     *
     * @unauthenticated
     *
     * @queryParam search string Từ khóa. Example: áo thun
     * @queryParam category integer ID danh mục.
     * @queryParam min_price integer Giá thấp nhất.
     * @queryParam max_price integer Giá cao nhất.
     * @queryParam brand string Thương hiệu.
     * @queryParam sort string latest|price_asc|price_desc|name|popular|best_selling|top_rated|most_viewed
     */
    public function products(Request $request): AnonymousResourceCollection
    {
        $request->validate([
            'sort' => ['nullable', Rule::in(ProductFilter::SORTS)],
            'min_price' => 'nullable|integer|min:0',
            'max_price' => 'nullable|integer|min:0',
            'category' => 'nullable|integer',
            'per_page' => 'nullable|integer|min:1|max:50',
        ]);

        $query = Product::with('category')->withCardData()->where('status', 'active');

        $this->filter->applySearch($query, $request->input('search') ?: $request->input('q'));
        $this->filter->applyCategory($query, $request->input('category'));
        $this->filter->applyPriceRange($query, $request->input('min_price'), $request->input('max_price'));

        if ($request->filled('brand')) {
            $query->where('brand', $request->input('brand'));
        }

        $this->filter->applySort($query, $request->input('sort', 'latest'));

        return ProductResource::collection(
            $query->paginate($request->integer('per_page', 12))->withQueryString()
        );
    }

    /**
     * Chi tiết sản phẩm
     *
     * @unauthenticated
     */
    public function show(Request $request, Product $product): ProductDetailResource
    {
        abort_if($product->status !== 'active', 404, 'Sản phẩm không tồn tại.');

        $user = $request->user('sanctum');

        if ($user?->role !== 'admin') {
            Product::whereKey($product->id)->increment('views_count');
            $product->views_count = (int) $product->views_count + 1;
        }
        app(AnalyticsService::class)->track('product_view', ['product_id' => $product->id]);

        $product->load([
            'category',
            'images' => fn ($q) => $q->orderByDesc('is_primary')->orderBy('sort_order'),
            'variants' => fn ($q) => $q->orderBy('size')->orderBy('color'),
        ]);

        $soldCount = (int) $product->orderItems()
            ->whereHas('order', fn ($o) => $o->where('status', '!=', 'cancelled'))
            ->sum('quantity');

        $isWishlisted = false;
        $canReview = false;
        if ($user) {
            $isWishlisted = WishlistItem::whereHas('wishlist', fn ($q) => $q->where('user_id', $user->id))
                ->where('product_id', $product->id)
                ->exists();
            $canReview = ! $user->reviews()->where('product_id', $product->id)->exists()
                && $user->orders()
                    ->whereHas('items', fn ($q) => $q->where('product_id', $product->id))
                    ->where('status', 'delivered')
                    ->exists();
        }

        return (new ProductDetailResource($product))->withExtra([
            'sold_count' => $soldCount,
            'is_wishlisted' => $isWishlisted,
            'can_review' => $canReview,
            'related' => ProductResource::collection($this->recommendations->similarTo($product)),
            'bought_together' => ProductResource::collection($this->recommendations->boughtTogether($product)),
        ]);
    }

    /**
     * Đánh giá đã duyệt của sản phẩm
     *
     * @unauthenticated
     */
    public function reviews(Product $product): AnonymousResourceCollection
    {
        abort_if($product->status !== 'active', 404, 'Sản phẩm không tồn tại.');

        return ReviewResource::collection(
            $product->reviews()->where('status', 'approved')->with('user')->latest()->paginate(10)
        );
    }

    /**
     * Tỉnh/thành giao hàng & cấu hình phí
     *
     * @unauthenticated
     */
    public function shippingConfig(PricingService $pricing): JsonResponse
    {
        return response()->json([
            'provinces' => ShippingZone::allProvinces(),
            'tax_label' => $pricing->taxLabel(),
            'tax_rate' => $pricing->taxRate(),
            'payment_methods' => [
                ['code' => 'cod', 'label' => 'Thanh toán khi nhận hàng (COD)'],
                ['code' => 'vnpay', 'label' => 'Thanh toán online qua VNPay'],
            ],
        ]);
    }

    /**
     * Danh mục đang hoạt động, danh mục cha đếm cả sản phẩm của danh mục con.
     */
    private function categoriesWithCounts()
    {
        $categories = Category::withCount(['products' => fn ($q) => $q->where('status', 'active')])
            ->where('status', 'active')
            ->orderBy('sort_order')
            ->orderBy('name')
            ->get();

        $byParent = $categories->groupBy('parent_id');
        $total = function (Category $category) use (&$total, $byParent): int {
            return $category->products_count
                + $byParent->get($category->id, collect())->sum(fn (Category $child) => $total($child));
        };
        $categories->each(fn (Category $c) => $c->total_products_count = $total($c));

        return $categories;
    }
}
