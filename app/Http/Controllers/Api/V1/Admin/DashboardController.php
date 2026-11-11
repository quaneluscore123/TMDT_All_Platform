<?php

namespace App\Http\Controllers\Api\V1\Admin;

use App\Http\Controllers\Controller;
use App\Http\Resources\Api\Admin\AdminOrderResource;
use App\Models\Order;
use App\Models\OrderItem;
use App\Models\Product;
use App\Models\Review;
use App\Models\Setting;
use App\Models\User;
use App\Services\AdminNotifier;
use App\Services\Order\PricingService;
use Carbon\Carbon;
use Illuminate\Http\JsonResponse;
use Illuminate\Support\Facades\DB;

/**
 * @group Mobile API — Quản trị
 *
 * Chỉ tài khoản role=admin. Gửi `Authorization: Bearer <token>` của admin.
 */
class DashboardController extends Controller
{
    /**
     * Tổng quan
     *
     * Doanh thu và thuế VAT tháng này (không tính đơn hủy), số đơn, việc cần xử lý, doanh thu 7 ngày, bán chạy.
     */
    public function __invoke(PricingService $pricing): JsonResponse
    {
        $now = Carbon::now();
        $monthStart = $now->copy()->startOfMonth();

        $monthOrders = Order::where('created_at', '>=', $monthStart);
        $monthValid = (clone $monthOrders)->where('status', '!=', 'cancelled');

        $revenueByDay = Order::where('status', '!=', 'cancelled')
            ->where('created_at', '>=', $now->copy()->subDays(6)->startOfDay())
            ->select(DB::raw('DATE(created_at) as date'), DB::raw('SUM(total) as total'))
            ->groupBy(DB::raw('DATE(created_at)'))
            ->pluck('total', 'date');

        $revenue7Days = collect(range(6, 0))->map(function (int $daysAgo) use ($now, $revenueByDay) {
            $date = $now->copy()->subDays($daysAgo);

            return [
                'date' => $date->toDateString(),
                'label' => $date->format('d/m'),
                'total' => (int) ($revenueByDay[$date->toDateString()] ?? 0),
            ];
        })->values();

        $topSelling = OrderItem::query()
            ->select('product_id', DB::raw('SUM(quantity) as sold'), DB::raw('SUM(subtotal) as revenue'))
            ->whereHas('order', fn ($q) => $q->where('status', '!=', 'cancelled'))
            ->groupBy('product_id')
            ->orderByDesc('sold')
            ->take(5)
            ->with('product:id,name,slug')
            ->get()
            ->map(fn ($row) => [
                'product_id' => $row->product_id,
                'name' => $row->product?->name ?? 'Sản phẩm đã xóa',
                'slug' => $row->product?->slug,
                'sold' => (int) $row->sold,
                'revenue' => (int) $row->revenue,
            ]);

        $lowStockThreshold = (int) Setting::get('low_stock_threshold', AdminNotifier::DEFAULT_LOW_STOCK);

        return response()->json([
            'revenue_month' => (int) (clone $monthValid)->sum('total'),
            'tax_month' => (int) (clone $monthValid)->sum('tax_amount'),
            'orders_month' => (clone $monthOrders)->count(),
            'orders_today' => Order::whereDate('created_at', $now->toDateString())->count(),
            'pending_orders' => Order::where('status', 'pending')->count(),
            'pending_reviews' => Review::where('status', 'pending')->count(),
            'low_stock_products' => Product::where('status', 'active')->where('stock', '<=', $lowStockThreshold)->count(),
            'low_stock_threshold' => $lowStockThreshold,
            'total_products' => Product::count(),
            'total_users' => User::count(),
            'tax' => [
                'enabled' => $pricing->taxEnabled(),
                'rate' => $pricing->taxRate(),
                'label' => $pricing->taxLabel(),
            ],
            'revenue_7_days' => $revenue7Days,
            'top_selling' => $topSelling,
            'recent_orders' => AdminOrderResource::collection(Order::with('user')->latest()->take(5)->get()),
        ]);
    }
}
