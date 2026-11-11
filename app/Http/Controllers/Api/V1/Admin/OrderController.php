<?php

namespace App\Http\Controllers\Api\V1\Admin;

use App\Exceptions\OrderException;
use App\Http\Controllers\Controller;
use App\Http\Resources\Api\Admin\AdminOrderResource;
use App\Models\Order;
use App\Services\Order\OrderService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;

/**
 * @group Mobile API — Quản trị
 */
class OrderController extends Controller
{
    /**
     * Danh sách đơn hàng
     *
     * @queryParam search string Mã đơn hoặc tên người nhận.
     * @queryParam status string pending|confirmed|shipping|delivered|cancelled
     * @queryParam payment_status string pending|paid|failed
     */
    public function index(Request $request): AnonymousResourceCollection
    {
        $request->validate([
            'search' => 'nullable|string|max:100',
            'status' => 'nullable|in:pending,confirmed,shipping,delivered,cancelled',
            'payment_status' => 'nullable|in:pending,paid,failed',
        ]);

        $orders = Order::with(['user', 'items'])
            ->when($request->input('search'), fn ($q, $search) => $q->where(fn ($w) => $w
                ->whereLike('order_code', "%{$search}%")
                ->orWhereLike('shipping_name', "%{$search}%")))
            ->when($request->input('status'), fn ($q, $status) => $q->where('status', $status))
            ->when($request->input('payment_status'), fn ($q, $status) => $q->where('payment_status', $status))
            ->latest()
            ->paginate(15);

        return AdminOrderResource::collection($orders);
    }

    /**
     * Chi tiết đơn hàng
     */
    public function show(Order $order): AdminOrderResource
    {
        return new AdminOrderResource($order->load(['items.product', 'user']));
    }

    /**
     * Đổi trạng thái đơn
     *
     * Cùng quy tắc với trang admin web: không mở lại đơn đã hủy; đơn VNPay chưa thanh toán không được xác nhận/giao;
     * hủy thì hoàn kho và hoàn lượt mã giảm giá; khách nhận email thông báo.
     *
     * @bodyParam status string required pending|confirmed|shipping|delivered|cancelled
     */
    public function updateStatus(Request $request, Order $order, OrderService $orders): JsonResponse
    {
        $validated = $request->validate([
            'status' => 'required|in:pending,confirmed,shipping,delivered,cancelled',
        ]);

        try {
            $order = $orders->changeStatusByAdmin($order, $validated['status']);
        } catch (OrderException $e) {
            return response()->json(['message' => $e->getMessage()], 422);
        }

        return response()->json([
            'message' => 'Đã cập nhật trạng thái đơn hàng.',
            'order' => new AdminOrderResource($order->load(['items.product', 'user'])),
        ]);
    }
}
