<?php

namespace App\Http\Controllers\Api\V1;

use App\Exceptions\CouponException;
use App\Exceptions\OrderException;
use App\Exceptions\PriceChangedException;
use App\Http\Controllers\Controller;
use App\Http\Resources\Api\OrderResource;
use App\Models\ShippingZone;
use App\Services\Analytics\AnalyticsService;
use App\Services\Cart\CartService;
use App\Services\Coupon\CouponService;
use App\Services\Order\OrderService;
use App\Services\Order\PricingService;
use App\Services\Payment\PaymentService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;
use Illuminate\Validation\Rule;

/**
 * @group Mobile API — Thanh toán & đơn hàng
 *
 * Luồng: POST /checkout/quote (báo giá theo tỉnh + mã giảm giá) → POST /orders với expected_total
 * đúng bằng tổng đã báo giá. Giá đổi giữa hai bước → 409 kèm tổng mới để khách xác nhận lại.
 */
class OrderController extends Controller
{
    public function __construct(
        private OrderService $orderService,
        private PaymentService $paymentService,
        private CartService $cartService
    ) {}

    /**
     * Kiểm tra mã giảm giá
     */
    public function applyCoupon(Request $request, CouponService $couponService): JsonResponse
    {
        $validated = $request->validate(['code' => 'required|string|max:50']);

        $cart = $this->cartService->getCartWithItems($request->user()->id);
        if ($cart->items->isEmpty()) {
            return response()->json(['message' => 'Giỏ hàng của bạn đang trống.'], 422);
        }

        try {
            $result = $couponService->applyCoupon(strtoupper(trim($validated['code'])), $cart->totalPrice(), $request->user());
        } catch (CouponException $e) {
            return response()->json(['message' => $e->getMessage(), 'errors' => ['code' => [$e->getMessage()]]], 422);
        }

        return response()->json($result);
    }

    /**
     * Báo giá đơn hàng
     *
     * Tính đúng như lúc đặt: giá hiện hành, mã giảm giá, phí ship theo tỉnh và VAT.
     */
    public function quote(Request $request, PricingService $pricing): JsonResponse
    {
        $validated = $request->validate([
            'shipping_province' => ['nullable', 'string', Rule::in(ShippingZone::allProvinces())],
            'coupon_code' => 'nullable|string|max:50',
        ]);

        try {
            $quote = $this->orderService->quote(
                $request->user(),
                $validated['shipping_province'] ?? null,
                $this->normalizeCoupon($validated['coupon_code'] ?? null)
            );
        } catch (OrderException $e) {
            return response()->json(['message' => $e->getMessage()], 422);
        }

        return response()->json([
            'subtotal' => $quote['subtotal'],
            'discount' => $quote['discount'],
            'coupon_error' => $quote['coupon_error'],
            'shipping_fee' => $quote['pricing']['shipping_fee'],
            'shipping_zone' => $quote['pricing']['shipping_zone'],
            'tax_rate' => $quote['pricing']['tax_rate'],
            'tax_label' => $pricing->taxLabel(),
            'tax_amount' => $quote['pricing']['tax_amount'],
            'total' => $quote['total'],
        ]);
    }

    /**
     * Đặt hàng
     *
     * @bodyParam agree_terms boolean required Phải là true (đồng ý Điều kiện giao dịch chung).
     * @bodyParam expected_total integer required Tổng tiền khách đã thấy ở bước báo giá.
     */
    public function store(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'shipping_name' => 'required|string|max:255',
            'shipping_phone' => 'required|string|max:20',
            'shipping_address' => 'required|string|max:500',
            'shipping_province' => ['required', 'string', Rule::in(ShippingZone::allProvinces())],
            'shipping_notes' => 'nullable|string|max:500',
            'payment_method' => 'required|in:cod,vnpay',
            'coupon_code' => 'nullable|string|max:50',
            'expected_total' => 'required|integer|min:0',
            'agree_terms' => 'accepted',
            'marketing_consent' => 'nullable|boolean',
        ], [
            'agree_terms.accepted' => 'Vui lòng đồng ý Điều kiện giao dịch chung.',
            'shipping_province.in' => 'Tỉnh/thành không hợp lệ.',
        ]);

        $user = $request->user();

        if ($request->boolean('marketing_consent')) {
            $user->setMarketingConsent(true);
        }

        // Bước "bắt đầu thanh toán" của phễu (web ghi ở bước review) — ghi trước sự kiện purchase
        app(AnalyticsService::class)->track('begin_checkout', ['value' => (int) $validated['expected_total']]);

        try {
            $order = $this->orderService->placeOrder($user, [
                'shipping_name' => $validated['shipping_name'],
                'shipping_phone' => $validated['shipping_phone'],
                'shipping_address' => $validated['shipping_address'],
                'shipping_province' => $validated['shipping_province'],
                'note' => $validated['shipping_notes'] ?? null,
                'payment_method' => $validated['payment_method'],
                'coupon_code' => $this->normalizeCoupon($validated['coupon_code'] ?? null),
                'expected_total' => (int) $validated['expected_total'],
            ]);
        } catch (PriceChangedException $e) {
            return response()->json([
                'message' => sprintf(
                    'Giá đơn hàng vừa thay đổi từ %s₫ thành %s₫. Vui lòng kiểm tra và xác nhận lại.',
                    number_format($e->quotedTotal, 0, ',', '.'),
                    number_format($e->currentTotal, 0, ',', '.')
                ),
                'quoted_total' => $e->quotedTotal,
                'current_total' => $e->currentTotal,
            ], 409);
        } catch (OrderException|CouponException $e) {
            return response()->json(['message' => $e->getMessage()], 422);
        }

        $payment = $this->paymentService->createPayment($order, $request->ip() ?? '127.0.0.1');

        return response()->json([
            'message' => 'Đặt hàng thành công!',
            'order' => new OrderResource($order->load('items.product')),
            'payment_url' => $payment->redirect_url ?? null,
        ], 201);
    }

    /**
     * Đơn hàng của tôi
     */
    public function index(Request $request): AnonymousResourceCollection
    {
        return OrderResource::collection($this->orderService->getOrdersForUser($request->user()->id));
    }

    /**
     * Chi tiết đơn hàng
     */
    public function show(Request $request, int $order): OrderResource
    {
        return new OrderResource($this->orderService->getOrderForUser($order, $request->user()->id));
    }

    /**
     * Hủy đơn (khi còn "Chờ xử lý" và chưa thanh toán)
     */
    public function cancel(Request $request, int $order): JsonResponse
    {
        $model = $this->orderService->getOrderForUser($order, $request->user()->id);

        try {
            $this->orderService->cancelByCustomer($model);
        } catch (OrderException $e) {
            return response()->json(['message' => $e->getMessage()], 422);
        }

        return response()->json([
            'message' => 'Đã hủy đơn hàng.',
            'order' => new OrderResource($model->fresh('items.product')),
        ]);
    }

    private function normalizeCoupon(?string $code): ?string
    {
        $code = $code !== null ? strtoupper(trim($code)) : null;

        return $code !== '' ? $code : null;
    }
}
