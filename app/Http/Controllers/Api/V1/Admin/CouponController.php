<?php

namespace App\Http\Controllers\Api\V1\Admin;

use App\Http\Controllers\Controller;
use App\Http\Requests\Admin\CouponRequest;
use App\Http\Resources\Api\Admin\AdminCouponResource;
use App\Models\Coupon;
use App\Models\CouponUsage;
use App\Services\AuditService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * @group Mobile API — Quản trị
 */
class CouponController extends Controller
{
    /**
     * Danh sách mã giảm giá (kèm thống kê)
     *
     * @queryParam search string Mã.
     * @queryParam status string active|inactive|expired
     */
    public function index(Request $request): JsonResponse
    {
        $request->validate([
            'search' => 'nullable|string|max:50',
            'status' => 'nullable|in:active,inactive,expired',
        ]);

        $coupons = Coupon::withCount('usages')
            ->when($request->input('search'), fn ($q, $search) => $q->whereLike('code', "%{$search}%"))
            ->when($request->input('status'), fn ($q, $status) => $status === 'expired'
                ? $q->where('end_at', '<', now())
                : $q->where('status', $status))
            ->latest()
            ->paginate(20);

        return AdminCouponResource::collection($coupons)->additional([
            'stats' => [
                'total' => Coupon::count(),
                'active' => Coupon::where('status', 'active')->count(),
                'expired' => Coupon::where('end_at', '<', now())->count(),
                'used' => CouponUsage::count(),
            ],
        ])->response();
    }

    /**
     * Tạo mã giảm giá
     *
     * @bodyParam code string required Chữ, số, gạch ngang/dưới; tự viết hoa. Example: SALE50K
     * @bodyParam type string required percent|fixed
     * @bodyParam value integer required % (≤ 100) hoặc số tiền (≥ 1.000₫).
     * @bodyParam min_order_amount integer required Đơn tối thiểu.
     * @bodyParam status string required active|inactive
     */
    public function store(CouponRequest $request): JsonResponse
    {
        $coupon = Coupon::create($request->couponData());
        AuditService::log('coupon_created', 'Coupon', $coupon->id, ['code' => $coupon->code, 'source' => 'mobile']);

        return response()->json([
            'message' => 'Đã tạo mã giảm giá.',
            'coupon' => new AdminCouponResource($coupon->loadCount('usages')),
        ], 201);
    }

    /**
     * Bật / tắt mã
     */
    public function toggle(Coupon $coupon): JsonResponse
    {
        $coupon->update(['status' => $coupon->status === 'active' ? 'inactive' : 'active']);

        return response()->json([
            'message' => $coupon->status === 'active' ? 'Đã bật mã giảm giá.' : 'Đã tắt mã giảm giá.',
            'coupon' => new AdminCouponResource($coupon->loadCount('usages')),
        ]);
    }

    /**
     * Xóa mã
     */
    public function destroy(Coupon $coupon): JsonResponse
    {
        $coupon->delete();
        AuditService::log('coupon_deleted', 'Coupon', $coupon->id, ['code' => $coupon->code, 'source' => 'mobile']);

        return response()->json(['message' => 'Đã xóa mã giảm giá.']);
    }
}
