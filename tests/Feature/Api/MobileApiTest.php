<?php

namespace Tests\Feature\Api;

use App\Models\Category;
use App\Models\Coupon;
use App\Models\Order;
use App\Models\Product;
use App\Models\ProductVariant;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class MobileApiTest extends TestCase
{
    use RefreshDatabase;

    private Product $product;

    protected function setUp(): void
    {
        parent::setUp();
        $this->product = Product::factory()->create([
            'category_id' => Category::factory()->create(['status' => 'active'])->id,
            'name' => 'Áo thun basic',
            'status' => 'active', 'price' => 400000, 'sale_price' => null, 'stock' => 10,
        ]);
    }

    private function checkoutPayload(array $overrides = []): array
    {
        return $overrides + [
            'shipping_name' => 'Nguyễn Văn A',
            'shipping_phone' => '0901234567',
            'shipping_address' => 'Số 1 Lê Lợi',
            'shipping_province' => 'Quảng Ninh',
            'payment_method' => 'cod',
            'agree_terms' => true,
        ];
    }

    public function test_register_login_and_logout_with_token(): void
    {
        $this->postJson('/api/v1/auth/register', [
            'name' => 'Khách App', 'email' => 'app@example.com',
            'password' => 'password123', 'password_confirmation' => 'password123',
        ])->assertCreated()->assertJsonStructure(['token', 'user' => ['id', 'email']]);

        $token = $this->postJson('/api/v1/auth/login', ['email' => 'app@example.com', 'password' => 'password123'])
            ->assertOk()->json('token');

        $this->withToken($token)->getJson('/api/v1/me')->assertOk()->assertJsonPath('data.email', 'app@example.com');
        $this->withToken($token)->postJson('/api/v1/auth/logout')->assertOk();

        $this->app['auth']->forgetGuards();
        $this->withToken($token)->getJson('/api/v1/me')->assertUnauthorized();
    }

    public function test_login_rejects_wrong_password_and_blocked_account(): void
    {
        User::factory()->create(['email' => 'a@example.com', 'password' => 'password123']);
        User::factory()->create(['email' => 'b@example.com', 'password' => 'password123', 'is_active' => false]);

        $this->postJson('/api/v1/auth/login', ['email' => 'a@example.com', 'password' => 'sai'])
            ->assertStatus(422)->assertJsonPath('message', 'Email hoặc mật khẩu không chính xác.');
        $this->postJson('/api/v1/auth/login', ['email' => 'b@example.com', 'password' => 'password123'])
            ->assertForbidden();
    }

    public function test_blocked_user_token_is_revoked(): void
    {
        $user = User::factory()->create(['is_active' => false]);
        Sanctum::actingAs($user);

        $this->getJson('/api/v1/cart')->assertForbidden();
    }

    public function test_catalog_endpoints_are_public(): void
    {
        $this->getJson('/api/v1/home')->assertOk()->assertJsonStructure(['flash_sale' => ['products'], 'categories', 'sections']);
        $this->getJson('/api/v1/categories')->assertOk();
        $this->getJson('/api/v1/shipping-config')->assertOk()->assertJsonFragment(['code' => 'cod']);

        $this->getJson('/api/v1/products?search=áo')
            ->assertOk()
            ->assertJsonPath('data.0.slug', $this->product->slug)
            ->assertJsonStructure(['data', 'meta' => ['current_page', 'last_page'], 'links']);

        $this->getJson('/api/v1/products/'.$this->product->slug)
            ->assertOk()
            ->assertJsonPath('data.id', $this->product->id)
            ->assertJsonPath('data.is_wishlisted', false);

        $this->getJson('/api/v1/products?sort=hacker')->assertStatus(422);
    }

    public function test_inactive_product_is_hidden(): void
    {
        $this->product->update(['status' => 'inactive']);

        $this->getJson('/api/v1/products/'.$this->product->slug)->assertNotFound();
    }

    public function test_cart_requires_variant_and_tracks_stock(): void
    {
        $user = User::factory()->create();
        Sanctum::actingAs($user);
        $variant = ProductVariant::create(['product_id' => $this->product->id, 'size' => 'M', 'color' => 'Đen', 'stock' => 2]);

        $this->postJson('/api/v1/cart/items', ['product_id' => $this->product->id])
            ->assertStatus(422)->assertJsonPath('message', fn ($m) => str_contains($m, 'chọn size/màu'));

        $itemId = $this->postJson('/api/v1/cart/items', ['product_id' => $this->product->id, 'variant_id' => $variant->id, 'quantity' => 2])
            ->assertOk()
            ->assertJsonPath('items_count', 2)
            ->assertJsonPath('subtotal', 800000)
            ->json('items.0.id');

        $this->patchJson("/api/v1/cart/items/{$itemId}", ['quantity' => 3])->assertStatus(422);
        $this->patchJson("/api/v1/cart/items/{$itemId}", ['quantity' => 1])->assertOk()->assertJsonPath('subtotal', 400000);
        $this->deleteJson("/api/v1/cart/items/{$itemId}")->assertOk()->assertJsonPath('items_count', 0);
    }

    public function test_cannot_touch_another_users_cart_item(): void
    {
        $owner = User::factory()->create();
        Sanctum::actingAs($owner);
        $itemId = $this->postJson('/api/v1/cart/items', ['product_id' => $this->product->id])->json('items.0.id');

        Sanctum::actingAs(User::factory()->create());
        $this->deleteJson("/api/v1/cart/items/{$itemId}")->assertNotFound();
    }

    public function test_quote_then_place_cod_order_with_coupon(): void
    {
        $user = User::factory()->create();
        Sanctum::actingAs($user);
        Coupon::factory()->create(['code' => 'GIAM50K', 'type' => 'fixed', 'value' => 50000, 'min_order_amount' => 0, 'usage_limit' => null, 'status' => 'active']);

        $this->postJson('/api/v1/cart/items', ['product_id' => $this->product->id, 'quantity' => 1])->assertOk();

        $this->postJson('/api/v1/coupons/apply', ['code' => 'giam50k'])->assertOk()->assertJsonPath('discount', 50000);

        $quote = $this->postJson('/api/v1/checkout/quote', ['shipping_province' => 'Quảng Ninh', 'coupon_code' => 'GIAM50K'])
            ->assertOk()->assertJsonPath('discount', 50000)->json();

        $response = $this->postJson('/api/v1/orders', $this->checkoutPayload([
            'coupon_code' => 'GIAM50K', 'expected_total' => $quote['total'],
        ]))->assertCreated()->assertJsonPath('payment_url', null);

        $order = Order::sole();
        $this->assertSame($quote['total'], $order->total);
        $this->assertSame(50000, $order->discount);
        $this->assertSame(9, $this->product->fresh()->stock);
        $response->assertJsonPath('order.id', $order->id);

        $this->getJson('/api/v1/cart')->assertJsonPath('items_count', 0);
        $this->getJson('/api/v1/orders')->assertOk()->assertJsonPath('data.0.order_code', $order->order_code);
        $this->getJson("/api/v1/orders/{$order->id}")->assertOk()->assertJsonPath('data.can_cancel', true);

        $this->postJson("/api/v1/orders/{$order->id}/cancel")->assertOk()->assertJsonPath('order.status', 'cancelled');
        $this->assertSame(10, $this->product->fresh()->stock);
    }

    public function test_price_change_between_quote_and_order_returns_conflict(): void
    {
        Sanctum::actingAs(User::factory()->create());
        $this->postJson('/api/v1/cart/items', ['product_id' => $this->product->id])->assertOk();
        $quoted = $this->postJson('/api/v1/checkout/quote', ['shipping_province' => 'Quảng Ninh'])->json('total');

        $this->product->update(['price' => 450000]);

        $this->postJson('/api/v1/orders', $this->checkoutPayload(['expected_total' => $quoted]))
            ->assertStatus(409)
            ->assertJsonPath('quoted_total', $quoted);
        $this->assertSame(0, Order::count());
    }

    public function test_order_requires_terms_and_valid_province(): void
    {
        Sanctum::actingAs(User::factory()->create());

        $this->postJson('/api/v1/orders', $this->checkoutPayload(['agree_terms' => false, 'shipping_province' => 'Atlantis', 'expected_total' => 1]))
            ->assertStatus(422)
            ->assertJsonValidationErrors(['agree_terms', 'shipping_province']);
    }

    public function test_user_cannot_view_someone_elses_order(): void
    {
        $order = Order::factory()->create(['user_id' => User::factory()->create()->id]);
        Sanctum::actingAs(User::factory()->create());

        $this->getJson("/api/v1/orders/{$order->id}")->assertNotFound();
    }

    public function test_wishlist_toggle_and_list(): void
    {
        Sanctum::actingAs(User::factory()->create());

        $this->postJson("/api/v1/wishlist/{$this->product->id}/toggle")->assertOk()->assertJsonPath('is_wishlisted', true);
        $this->getJson('/api/v1/wishlist')->assertOk()->assertJsonPath('data.0.id', $this->product->id);
        $this->getJson('/api/v1/products/'.$this->product->slug)->assertJsonPath('data.is_wishlisted', true);
        $this->postJson("/api/v1/wishlist/{$this->product->id}/toggle")->assertOk()->assertJsonPath('is_wishlisted', false);
    }

    public function test_review_requires_delivered_order(): void
    {
        $user = User::factory()->create();
        Sanctum::actingAs($user);

        $this->postJson('/api/v1/products/'.$this->product->slug.'/reviews', ['rating' => 5])->assertStatus(422);

        $order = Order::factory()->create(['user_id' => $user->id, 'status' => 'delivered']);
        $order->items()->create([
            'product_id' => $this->product->id, 'product_name' => $this->product->name,
            'price' => 400000, 'quantity' => 1, 'subtotal' => 400000,
        ]);

        $this->postJson('/api/v1/products/'.$this->product->slug.'/reviews', ['rating' => 5, 'comment' => 'Đẹp'])
            ->assertCreated()->assertJsonPath('review.status', 'pending');
    }

    public function test_profile_update_and_change_password(): void
    {
        $user = User::factory()->create(['password' => 'password123']);
        Sanctum::actingAs($user);

        $this->putJson('/api/v1/me', ['name' => 'Tên mới', 'phone' => '0911111111'])
            ->assertOk()->assertJsonPath('data.name', 'Tên mới');

        $this->putJson('/api/v1/me/password', ['current_password' => 'sai', 'password' => 'newpass123', 'password_confirmation' => 'newpass123'])
            ->assertStatus(422);
        $this->putJson('/api/v1/me/password', ['current_password' => 'password123', 'password' => 'newpass123', 'password_confirmation' => 'newpass123'])
            ->assertOk();
    }

    public function test_protected_endpoints_require_token(): void
    {
        $this->getJson('/api/v1/cart')->assertUnauthorized();
        $this->postJson('/api/v1/orders', [])->assertUnauthorized();
    }
}
