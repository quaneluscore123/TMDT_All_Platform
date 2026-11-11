<?php

namespace Tests\Feature\Api;

use App\Models\Category;
use App\Models\Coupon;
use App\Models\Order;
use App\Models\Product;
use App\Models\ProductVariant;
use App\Models\Review;
use App\Models\Setting;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Mail;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class MobileAdminApiTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        Mail::fake();
    }

    private function actingAsAdmin(): User
    {
        $admin = User::factory()->admin()->create();
        Sanctum::actingAs($admin);

        return $admin;
    }

    private function product(array $attributes = []): Product
    {
        return Product::factory()->create($attributes + [
            'category_id' => Category::factory()->create()->id,
            'status' => 'active', 'price' => 400000, 'sale_price' => null, 'stock' => 10,
        ]);
    }

    public function test_admin_endpoints_reject_guests_and_customers(): void
    {
        $this->getJson('/api/v1/admin/dashboard')->assertUnauthorized();

        Sanctum::actingAs(User::factory()->create());
        $this->getJson('/api/v1/admin/dashboard')->assertForbidden();
        $this->getJson('/api/v1/admin/orders')->assertForbidden();
        $this->putJson('/api/v1/admin/settings/tax', [])->assertForbidden();
    }

    public function test_dashboard_reports_revenue_and_vat_excluding_cancelled_orders(): void
    {
        $this->actingAsAdmin();
        Order::factory()->create(['total' => 1080000, 'tax_amount' => 80000, 'status' => 'delivered']);
        Order::factory()->create(['total' => 540000, 'tax_amount' => 40000, 'status' => 'pending']);
        $cancelled = Order::factory()->create(['total' => 999999, 'tax_amount' => 70000, 'status' => 'cancelled']);
        Review::factory()->pending()->create(['order_id' => $cancelled->id]);

        $this->getJson('/api/v1/admin/dashboard')
            ->assertOk()
            ->assertJsonPath('revenue_month', 1620000)
            ->assertJsonPath('tax_month', 120000)
            ->assertJsonPath('orders_month', 3)
            ->assertJsonPath('pending_orders', 1)
            ->assertJsonPath('pending_reviews', 1)
            ->assertJsonCount(7, 'revenue_7_days')
            ->assertJsonStructure(['tax' => ['enabled', 'rate', 'label'], 'top_selling', 'recent_orders']);
    }

    public function test_orders_can_be_filtered_and_show_customer_and_vat(): void
    {
        $this->actingAsAdmin();
        $customer = User::factory()->create(['name' => 'Trần Thị B']);
        $order = Order::factory()->create(['user_id' => $customer->id, 'status' => 'pending', 'tax_rate' => 8, 'tax_amount' => 32000]);
        Order::factory()->create(['status' => 'delivered']);

        $this->getJson('/api/v1/admin/orders?status=pending')
            ->assertOk()
            ->assertJsonCount(1, 'data')
            ->assertJsonPath('data.0.id', $order->id);

        $this->getJson("/api/v1/admin/orders/{$order->id}")
            ->assertOk()
            ->assertJsonPath('data.customer.name', 'Trần Thị B')
            ->assertJsonPath('data.tax_amount', 32000)
            ->assertJsonFragment(['value' => 'confirmed', 'label' => 'Đã xác nhận']);
    }

    public function test_admin_changes_order_status_with_web_rules(): void
    {
        $this->actingAsAdmin();
        $order = Order::factory()->create(['status' => 'pending', 'payment_method' => 'cod']);

        $this->patchJson("/api/v1/admin/orders/{$order->id}/status", ['status' => 'shipping'])
            ->assertOk()
            ->assertJsonPath('order.status', 'shipping');
        $this->assertSame('shipping', $order->fresh()->status);

        // Đơn VNPay chưa thanh toán không được xác nhận
        $unpaid = Order::factory()->create(['payment_method' => 'vnpay', 'payment_status' => 'pending']);
        $this->patchJson("/api/v1/admin/orders/{$unpaid->id}/status", ['status' => 'confirmed'])
            ->assertStatus(422);
        $this->assertSame('pending', $unpaid->fresh()->status);

        // Hủy rồi không mở lại được
        $this->patchJson("/api/v1/admin/orders/{$order->id}/status", ['status' => 'cancelled'])->assertOk();
        $this->patchJson("/api/v1/admin/orders/{$order->id}/status", ['status' => 'pending'])
            ->assertStatus(422)
            ->assertJsonPath('message', 'Không thể mở lại đơn đã hủy.');
    }

    public function test_product_quick_edit_updates_price_and_variant_stock(): void
    {
        $this->actingAsAdmin();
        $product = $this->product(['sale_ends_at' => now()->addDay(), 'sale_price' => 350000]);
        $variant = ProductVariant::create(['product_id' => $product->id, 'size' => 'M', 'color' => 'Đen', 'stock' => 3]);

        $this->patchJson("/api/v1/admin/products/{$product->id}", [
            'price' => 450000,
            'sale_price' => null,
            'variants' => [['id' => $variant->id, 'stock' => 12, 'price' => null]],
        ])->assertOk()->assertJsonPath('product.stock', 12);

        $product->refresh();
        $this->assertSame(450000, $product->price);
        $this->assertNull($product->sale_price);
        $this->assertNull($product->sale_ends_at);
        $this->assertSame(12, $variant->fresh()->stock);

        $this->patchJson("/api/v1/admin/products/{$product->id}", ['price' => 100000, 'sale_price' => 200000])
            ->assertUnprocessable()
            ->assertJsonValidationErrors('sale_price');
    }

    public function test_product_toggle_and_list_include_inactive_products(): void
    {
        $this->actingAsAdmin();
        $product = $this->product();

        $this->postJson("/api/v1/admin/products/{$product->id}/toggle")
            ->assertOk()
            ->assertJsonPath('product.status', 'inactive');

        $this->getJson('/api/v1/admin/products?status=inactive')
            ->assertOk()
            ->assertJsonPath('data.0.id', $product->id);
    }

    public function test_review_moderation(): void
    {
        $this->actingAsAdmin();
        $review = Review::factory()->pending()->create();

        $this->getJson('/api/v1/admin/reviews?status=pending')->assertOk()->assertJsonCount(1, 'data');
        $this->patchJson("/api/v1/admin/reviews/{$review->id}/approve")->assertOk()->assertJsonPath('review.status', 'approved');
        $this->patchJson("/api/v1/admin/reviews/{$review->id}/reject")->assertOk()->assertJsonPath('review.status', 'rejected');
        $this->deleteJson("/api/v1/admin/reviews/{$review->id}")->assertOk();
        $this->assertModelMissing($review);
    }

    public function test_blocking_user_revokes_tokens_but_admins_cannot_be_blocked(): void
    {
        $admin = $this->actingAsAdmin();
        $customer = User::factory()->create();
        $customer->createToken('mobile');

        $this->patchJson("/api/v1/admin/users/{$customer->id}/toggle-status")
            ->assertOk()
            ->assertJsonPath('user.is_active', false);
        $this->assertSame(0, $customer->tokens()->count());

        $this->patchJson("/api/v1/admin/users/{$admin->id}/toggle-status")->assertForbidden();
        $this->assertTrue((bool) $admin->fresh()->is_active);
    }

    public function test_coupon_create_uses_same_validation_as_web(): void
    {
        $this->actingAsAdmin();
        $payload = ['code' => 'sale10', 'type' => 'percent', 'value' => 10, 'min_order_amount' => 0, 'status' => 'active', 'usage_limit' => 0];

        $this->postJson('/api/v1/admin/coupons', $payload)
            ->assertCreated()
            ->assertJsonPath('coupon.code', 'SALE10')
            ->assertJsonPath('coupon.usage_limit', null);

        $this->postJson('/api/v1/admin/coupons', ['code' => 'BIG', 'value' => 150] + $payload)
            ->assertUnprocessable()
            ->assertJsonValidationErrors(['value' => 'Phần trăm giảm không được quá 100%']);

        $this->postJson('/api/v1/admin/coupons', $payload)->assertJsonValidationErrors('code');
    }

    public function test_coupon_list_toggle_and_delete(): void
    {
        $this->actingAsAdmin();
        $coupon = Coupon::factory()->create(['status' => 'active']);

        $this->getJson('/api/v1/admin/coupons')
            ->assertOk()
            ->assertJsonPath('stats.total', 1)
            ->assertJsonPath('data.0.code', $coupon->code);

        $this->patchJson("/api/v1/admin/coupons/{$coupon->id}/toggle")->assertOk()->assertJsonPath('coupon.status', 'inactive');
        $this->deleteJson("/api/v1/admin/coupons/{$coupon->id}")->assertOk();
        $this->assertModelMissing($coupon);
    }

    public function test_tax_settings_apply_to_new_quotes(): void
    {
        $this->actingAsAdmin();

        $this->putJson('/api/v1/admin/settings/tax', [
            'tax_enabled' => true, 'tax_rate' => 10, 'tax_label' => 'VAT', 'tax_on_shipping' => false,
        ])->assertOk()
            ->assertJsonPath('tax_enabled', true)
            ->assertJsonPath('tax_label', 'VAT')
            ->assertJsonPath('example.tax_amount', 100000);

        $this->assertEquals(10, Setting::get('tax_rate'));
        $this->getJson('/api/v1/admin/settings/tax')->assertOk()->assertJsonPath('tax_rate', 10);

        $this->putJson('/api/v1/admin/settings/tax', [
            'tax_enabled' => true, 'tax_rate' => 120, 'tax_label' => 'VAT', 'tax_on_shipping' => false,
        ])->assertJsonValidationErrors(['tax_rate' => 'Thuế suất tối đa 100%.']);
    }
}
