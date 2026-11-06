<?php

namespace App\Services\Order;

use App\Exceptions\CouponException;
use App\Exceptions\OrderException;
use App\Exceptions\PriceChangedException;
use App\Mail\OrderConfirmedMail;
use App\Mail\OrderStatusChangedMail;
use App\Models\Cart;
use App\Models\CouponUsage;
use App\Models\Order;
use App\Models\Payment;
use App\Models\Product;
use App\Models\ProductVariant;
use App\Models\User;
use App\Services\AdminNotifier;
use App\Services\Analytics\AnalyticsService;
use App\Services\AuditService;
use App\Services\BaseService;
use App\Services\Cart\CartService;
use App\Services\Coupon\CouponService;
use App\Services\Referral\ReferralService;
use Illuminate\Auth\Access\AuthorizationException;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Mail;
use Illuminate\Support\Str;

class OrderService extends BaseService
{
    public function __construct(
        private CartService $cartService,
        private CouponService $couponService,
        private ReferralService $referralService,
        private PricingService $pricingService,
        private AnalyticsService $analytics
    ) {}

    /**
     * Tạo đơn hàng mới từ giỏ hàng hiện tại của user.
     * Toàn bộ quá trình được bọc trong DB Transaction.
     *
     * @throws OrderException Khi giỏ hàng rỗng hoặc tồn kho không đủ.
     */
    public function placeOrder(User $user, array $data): Order
    {
        return DB::transaction(function () use ($user, $data) {
            // 0. Khóa giỏ hàng của user: request thứ 2 (double-click) phải chờ request đầu commit,
            //    lúc đó giỏ đã trống → không tạo đơn trùng
            Cart::where('user_id', $user->id)->lockForUpdate()->first();

            // 1. Lấy giỏ hàng đầy đủ
            $cart = $this->cartService->getCartWithItems($user->id);

            if ($cart->items->isEmpty()) {
                throw new OrderException('Giỏ hàng của bạn đang trống.');
            }

            // 2. Kiểm tra tồn kho & tính subtotal (lấy giá từ DB thực, không tin giỏ hàng)
            [$itemsToCreate, $subtotal] = $this->priceCartLines($cart, lock: true);

            // 3. Xử lý Coupon: web giữ mã trong session, app di động (API không session) truyền coupon_code
            $discount = 0;
            $coupon = null;
            $couponCode = array_key_exists('coupon_code', $data) ? $data['coupon_code'] : session('coupon_code');

            if ($couponCode) {
                try {
                    $result = $this->couponService->validateAndCalculate($couponCode, $subtotal, $user);
                    $coupon = $result['coupon'];
                    $discount = $result['discount'];
                } catch (CouponException $e) {
                    session()->forget('coupon_code');
                    throw $e;
                }
            }

            // 4. Phí ship + VAT theo cấu hình Admin → Cài đặt (freeship xét trên tạm tính, VAT trên giá trị sau giảm giá)
            $pricing = $this->pricingService->breakdown($subtotal, $discount, $data['shipping_province'] ?? null);
            $shippingFee = $pricing['shipping_fee'];
            $total = $pricing['total'];

            // 4b. Khách chỉ trả đúng số tiền đã thấy ở bước xác nhận: lệch báo giá → không tạo đơn (rollback)
            if (isset($data['expected_total']) && (int) $data['expected_total'] !== $total) {
                throw new PriceChangedException((int) $data['expected_total'], $total);
            }

            // 5. Tạo Order (gắn nguồn truy cập / chiến dịch quảng cáo đưa khách tới — last-click)
            $attribution = $this->analytics->attribution();
            $order = Order::create([
                'user_id' => $user->id,
                'campaign_id' => $attribution['campaign_id'],
                'traffic_source' => $attribution['source'],
                'order_code' => 'ORD-'.date('Ymd').'-'.strtoupper(Str::random(4)),
                'subtotal' => $subtotal,
                'discount' => $discount,
                'shipping_fee' => $shippingFee,
                'tax_rate' => $pricing['tax_rate'],
                'tax_amount' => $pricing['tax_amount'],
                'total' => $total,
                'status' => 'pending',
                'payment_method' => $data['payment_method'],
                'payment_status' => 'pending',
                'shipping_name' => $data['shipping_name'],
                'shipping_phone' => $data['shipping_phone'],
                'shipping_address' => $data['shipping_address'],
                'shipping_province' => $data['shipping_province'] ?? null,
                'note' => $data['note'] ?? null,
            ]);

            // 6. Tạo OrderItems
            $order->items()->createMany(array_map(fn ($line) => array_diff_key($line, ['cart_item_id' => true]), $itemsToCreate));

            // 7. Redeem Coupon (tăng used_count + tạo CouponUsage)
            if ($coupon) {
                $this->couponService->redeem($coupon, $user, $order);
            }

            // 8. Trừ tồn kho
            foreach ($itemsToCreate as $item) {
                if (! empty($item['variant_id'])) {
                    ProductVariant::where('id', $item['variant_id'])
                        ->decrement('stock', $item['quantity']);
                }

                Product::where('id', $item['product_id'])
                    ->decrement('stock', $item['quantity']);
            }

            // 9. Xóa giỏ hàng
            $this->cartService->clearCart($user->id);

            // 10. Lưu vết Referral (nếu có)
            $this->referralService->attachOrderToReferral($order);

            // 10b. Ghi vết giao dịch
            AuditService::log('order_created', 'Order', $order->id, [
                'order_code' => $order->order_code,
                'total' => $order->total,
            ]);
            $this->analytics->track('purchase', ['order_id' => $order->id, 'value' => $order->total]);
            app(AdminNotifier::class)->newOrder($order);

            // 11. Xóa coupon khỏi session
            session()->forget('coupon_code');

            // 12. Gửi email xác nhận đơn hàng / hóa đơn
            // Với COD: gửi ngay khi đặt hàng thành công
            // Với VNPay: PaymentService sẽ gửi hóa đơn thanh toán sau khi khách thanh toán thành công
            if (($data['payment_method'] ?? 'cod') === 'cod') {
                try {
                    Mail::to($user->email)->queue(new OrderConfirmedMail($order));
                } catch (\Exception $e) {
                    \Log::error('Không thể gửi email xác nhận đơn hàng: '.$e->getMessage());
                }
            }

            return $order;
        });
    }

    /**
     * Lấy danh sách đơn hàng của một user.
     */
    public function getOrdersForUser(int $userId)
    {
        return Order::where('user_id', $userId)
            ->with('items.product')
            ->latest()
            ->paginate(10);
    }

    /**
     * Lấy chi tiết 1 đơn hàng, đảm bảo thuộc về user (tránh IDOR).
     *
     * @throws AuthorizationException
     */
    public function getOrderForUser(int $orderId, int $userId): Order
    {
        return Order::where('id', $orderId)
            ->where('user_id', $userId)
            ->with('items.product')
            ->firstOrFail();
    }

    /**
     * Cập nhật trạng thái đơn hàng (dùng bởi Admin hoặc hệ thống thanh toán).
     */
    public function updateStatus(Order $order, string $status): Order
    {
        $order->update(['status' => $status]);

        return $order;
    }

    /**
     * Báo giá cho trang xác nhận đơn: tính đúng như lúc đặt hàng (giá hiện hành của sản phẩm/biến thể,
     * mã giảm giá, phí ship theo tỉnh, VAT) nhưng không tạo đơn, không khóa dữ liệu.
     *
     * @return array{lines: array<int, array>, subtotal: int, discount: int, coupon_error: ?string, pricing: array, total: int}
     *
     * @throws OrderException Khi giỏ trống, hết hàng hoặc thiếu size/màu.
     */
    public function quote(User $user, ?string $province, ?string $couponCode = null): array
    {
        $cart = $this->cartService->getCartWithItems($user->id);

        if ($cart->items->isEmpty()) {
            throw new OrderException('Giỏ hàng của bạn đang trống.');
        }

        [$lines, $subtotal] = $this->priceCartLines($cart, lock: false);

        $discount = 0;
        $couponError = null;
        $couponCode ??= session('coupon_code');
        if ($couponCode) {
            try {
                $discount = $this->couponService->validateAndCalculate($couponCode, $subtotal, $user)['discount'];
            } catch (CouponException $e) {
                // Không tự gỡ mã: báo cho khách; bước đặt hàng sẽ chặn và yêu cầu xác nhận lại
                $couponError = $e->getMessage();
            }
        }

        $pricing = $this->pricingService->breakdown($subtotal, $discount, $province);

        return [
            'lines' => $lines,
            'subtotal' => $subtotal,
            'discount' => $pricing['discount'],
            'coupon_error' => $couponError,
            'pricing' => $pricing,
            'total' => $pricing['total'],
        ];
    }

    /**
     * Định giá từng dòng giỏ từ dữ liệu hiện hành + kiểm tra tồn kho, trạng thái, size/màu.
     *
     * @return array{0: array<int, array>, 1: int} [dòng đơn, tạm tính]
     */
    private function priceCartLines(Cart $cart, bool $lock): array
    {
        $subtotal = 0;
        $itemsToCreate = [];

        $productIds = $cart->items->pluck('product_id');
        $products = Product::whereIn('id', $productIds)->withCount('variants')
            ->when($lock, fn ($q) => $q->lockForUpdate())->get()->keyBy('id');

        $variantIds = $cart->items->pluck('variant_id')->filter();
        $variants = ! $variantIds->isEmpty()
            ? ProductVariant::whereIn('id', $variantIds)->when($lock, fn ($q) => $q->lockForUpdate())->get()->keyBy('id')
            : collect();

        foreach ($cart->items as $cartItem) {
            $product = $products->get($cartItem->product_id);
            $variant = $cartItem->variant_id
                ? $variants->get($cartItem->variant_id)
                : null;

            if (! $product || $product->status !== 'active') {
                throw new OrderException("Sản phẩm \"{$cartItem->product->name}\" hiện không còn kinh doanh.");
            }

            // Giỏ cũ / giỏ khách vãng lai có thể chứa sản phẩm (nay) có size/màu nhưng chưa chọn biến thể
            if (! $variant && $product->variants_count > 0) {
                throw new OrderException("Vui lòng chọn size/màu cho \"{$product->name}\": xóa sản phẩm khỏi giỏ và thêm lại từ trang sản phẩm.");
            }

            $availableStock = $variant ? $variant->stock : $product->stock;
            $stockOwnerLabel = $variant
                ? $product->name.' ('.$variant->label().')'
                : $product->name;

            if ($availableStock < $cartItem->quantity) {
                throw new OrderException(
                    "Sản phẩm \"{$stockOwnerLabel}\" chỉ còn {$availableStock} trong kho, không đủ số lượng yêu cầu."
                );
            }

            $unitPrice = $variant ? $variant->unitPrice() : $product->effectivePrice();
            $lineTotal = $unitPrice * $cartItem->quantity;
            $subtotal += $lineTotal;

            $itemsToCreate[] = [
                'cart_item_id' => $cartItem->id,
                'product_id' => $product->id,
                'variant_id' => $cartItem->variant_id,
                'product_name' => $product->name,
                'size' => $variant?->size,
                'color' => $variant?->color,
                'price' => $unitPrice,
                'cost_price' => $product->cost_price, // snapshot giá vốn tại thời điểm đặt
                'quantity' => $cartItem->quantity,
                'subtotal' => $lineTotal,
            ];
        }

        return [$itemsToCreate, $subtotal];
    }

    /**
     * Hủy đơn: hoàn kho, hoàn lượt coupon, hủy referral, ghi audit.
     * Khóa dòng order để không bao giờ hoàn kho 2 lần (admin + IPN + job hết hạn chạy song song).
     *
     * @return bool false nếu đơn đã bị hủy trước đó (không làm gì).
     */
    public function cancelOrder(Order $order, string $reason, array $extra = []): bool
    {
        $cancelled = DB::transaction(function () use ($order, $reason, $extra) {
            $locked = Order::whereKey($order->id)->lockForUpdate()->first();

            if (! $locked || $locked->status === 'cancelled') {
                return false;
            }

            $oldStatus = $locked->status;
            $locked->update(array_merge(['status' => 'cancelled'], $extra));

            // Vô hiệu giao dịch VNPay đang chờ: IPN đến sau sẽ không đánh dấu đơn đã hủy là "đã thanh toán"
            Payment::where('order_id', $locked->id)
                ->where('method', 'vnpay')
                ->where('status', 'pending')
                ->update(['status' => 'failed']);

            foreach ($locked->items as $item) {
                if ($item->variant_id) {
                    ProductVariant::where('id', $item->variant_id)
                        ->increment('stock', $item->quantity);
                }

                Product::where('id', $item->product_id)
                    ->increment('stock', $item->quantity);
            }

            $usage = CouponUsage::where('order_id', $locked->id)->first();
            if ($usage) {
                $usage->coupon?->decrement('used_count');
                $usage->delete();
            }

            $this->referralService->cancelReferral($locked);

            AuditService::log('order_status_changed', 'Order', $locked->id, [
                'from' => $oldStatus,
                'to' => 'cancelled',
                'reason' => $reason,
            ]);

            return $oldStatus;
        });

        if ($cancelled === false) {
            return false;
        }

        $order->refresh();
        $this->notifyStatusChanged($order, $cancelled);

        // Khách hủy thanh toán / bỏ dở VNPay: giỏ hàng được trả lại nguyên như trước khi đặt
        if (in_array($reason, ['payment_failed', 'payment_expired'], true)) {
            $this->cartService->restoreFromOrder($order);
        }

        return true;
    }

    /**
     * Khách tự hủy đơn: chỉ khi đơn còn "Chờ xử lý" và chưa thanh toán online.
     *
     * @throws OrderException
     */
    public function cancelByCustomer(Order $order): void
    {
        if (! $this->customerCanCancel($order)) {
            throw new OrderException('Đơn hàng này không thể hủy (đã được xác nhận hoặc đã thanh toán).');
        }

        if ($this->cancelOrder($order, 'customer')) {
            app(AdminNotifier::class)->orderCancelledByCustomer($order);
        }
    }

    public function customerCanCancel(Order $order): bool
    {
        return $order->status === 'pending' && $order->payment_status !== 'paid';
    }

    /**
     * Hủy các đơn VNPay quá hạn chưa thanh toán để nhả tồn kho.
     *
     * @return int số đơn đã hủy
     */
    public function expireUnpaidOnlineOrders(int $minutes): int
    {
        $count = 0;

        Order::query()
            ->where('payment_method', 'vnpay')
            ->where('payment_status', 'pending')
            ->where('status', 'pending')
            ->where('created_at', '<', now()->subMinutes($minutes))
            ->orderBy('id')
            ->each(function (Order $order) use (&$count) {
                Payment::where('order_id', $order->id)
                    ->where('status', 'pending')
                    ->update(['status' => 'failed']);

                if ($this->cancelOrder($order, 'payment_expired', ['payment_status' => 'failed'])) {
                    $count++;
                }
            });

        return $count;
    }

    public function notifyStatusChanged(Order $order, string $oldStatus): void
    {
        try {
            Mail::to($order->user->email)->queue(new OrderStatusChangedMail($order, $oldStatus));
        } catch (\Exception $e) {
            \Log::error('Không thể gửi email cập nhật đơn hàng: '.$e->getMessage());
        }
    }
}
