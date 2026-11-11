<?php

use App\Http\Controllers\Api\V1\Admin;
use App\Http\Controllers\Api\V1\AuthController;
use App\Http\Controllers\Api\V1\CartController;
use App\Http\Controllers\Api\V1\CatalogController;
use App\Http\Controllers\Api\V1\ChatController;
use App\Http\Controllers\Api\V1\OrderController;
use App\Http\Controllers\Api\V1\ProfileController;
use App\Http\Controllers\Api\V1\ReviewController;
use App\Http\Controllers\Api\V1\WishlistController;
use App\Http\Middleware\EnsureApiUserIsActive;
use Illuminate\Support\Facades\Route;

/*
|--------------------------------------------------------------------------
| Mobile API v1 (app Android) — prefix /api/v1, xác thực Bearer token Sanctum
|--------------------------------------------------------------------------
*/

Route::prefix('v1')->name('api.v1.')->group(function () {
    // Công khai
    Route::get('/home', [CatalogController::class, 'home'])->name('home');
    Route::get('/categories', [CatalogController::class, 'categories'])->name('categories');
    Route::get('/products', [CatalogController::class, 'products'])->name('products.index');
    Route::get('/products/{product}', [CatalogController::class, 'show'])->name('products.show');
    Route::get('/products/{product}/reviews', [CatalogController::class, 'reviews'])->name('products.reviews');
    Route::get('/shipping-config', [CatalogController::class, 'shippingConfig'])->name('shipping-config');

    Route::post('/auth/register', [AuthController::class, 'register'])->middleware('throttle:register')->name('auth.register');
    Route::post('/auth/login', [AuthController::class, 'login'])->middleware('throttle:login')->name('auth.login');
    Route::post('/auth/forgot-password', [AuthController::class, 'forgotPassword'])->middleware('throttle:password')->name('auth.forgot-password');

    // Chatbot AI: khách vãng lai dùng được; gửi kèm Bearer token thì trợ lý tra được đơn của chính khách đó
    Route::post('/chat', [ChatController::class, 'stream'])->middleware('throttle:chat')->name('chat');

    // Cần đăng nhập
    Route::middleware(['auth:sanctum', EnsureApiUserIsActive::class])->group(function () {
        Route::post('/auth/logout', [AuthController::class, 'logout'])->name('auth.logout');

        Route::get('/me', [ProfileController::class, 'show'])->name('me');
        Route::put('/me', [ProfileController::class, 'update'])->name('me.update');
        Route::put('/me/password', [ProfileController::class, 'changePassword'])->name('me.password');

        Route::get('/cart', [CartController::class, 'index'])->name('cart.index');
        Route::post('/cart/items', [CartController::class, 'store'])->name('cart.store');
        Route::patch('/cart/items/{item}', [CartController::class, 'update'])->whereNumber('item')->name('cart.update');
        Route::delete('/cart/items/{item}', [CartController::class, 'destroy'])->whereNumber('item')->name('cart.destroy');
        Route::delete('/cart', [CartController::class, 'clear'])->name('cart.clear');

        Route::post('/coupons/apply', [OrderController::class, 'applyCoupon'])->name('coupons.apply');
        Route::post('/checkout/quote', [OrderController::class, 'quote'])->name('checkout.quote');
        Route::post('/orders', [OrderController::class, 'store'])->middleware('throttle:checkout')->name('orders.store');
        Route::get('/orders', [OrderController::class, 'index'])->name('orders.index');
        Route::get('/orders/{order}', [OrderController::class, 'show'])->whereNumber('order')->name('orders.show');
        Route::post('/orders/{order}/cancel', [OrderController::class, 'cancel'])->whereNumber('order')->middleware('throttle:10,1')->name('orders.cancel');

        Route::get('/wishlist', [WishlistController::class, 'index'])->name('wishlist.index');
        Route::post('/wishlist/{product}/toggle', [WishlistController::class, 'toggle'])->whereNumber('product')->name('wishlist.toggle');

        Route::post('/products/{product}/reviews', [ReviewController::class, 'store'])->middleware('throttle:reviews')->name('reviews.store');

        // Quản trị (role=admin)
        Route::middleware('admin')->prefix('admin')->name('admin.')->group(function () {
            Route::get('/dashboard', Admin\DashboardController::class)->name('dashboard');

            Route::get('/orders', [Admin\OrderController::class, 'index'])->name('orders.index');
            Route::get('/orders/{order}', [Admin\OrderController::class, 'show'])->whereNumber('order')->name('orders.show');
            Route::patch('/orders/{order}/status', [Admin\OrderController::class, 'updateStatus'])->whereNumber('order')->name('orders.status');

            Route::get('/products', [Admin\ProductController::class, 'index'])->name('products.index');
            Route::get('/products/{product:id}', [Admin\ProductController::class, 'show'])->whereNumber('product')->name('products.show');
            Route::patch('/products/{product:id}', [Admin\ProductController::class, 'update'])->whereNumber('product')->name('products.update');
            Route::post('/products/{product:id}/toggle', [Admin\ProductController::class, 'toggle'])->whereNumber('product')->name('products.toggle');

            Route::get('/reviews', [Admin\ReviewController::class, 'index'])->name('reviews.index');
            Route::patch('/reviews/{review}/approve', [Admin\ReviewController::class, 'approve'])->whereNumber('review')->name('reviews.approve');
            Route::patch('/reviews/{review}/reject', [Admin\ReviewController::class, 'reject'])->whereNumber('review')->name('reviews.reject');
            Route::delete('/reviews/{review}', [Admin\ReviewController::class, 'destroy'])->whereNumber('review')->name('reviews.destroy');

            Route::get('/users', [Admin\UserController::class, 'index'])->name('users.index');
            Route::patch('/users/{user}/toggle-status', [Admin\UserController::class, 'toggleStatus'])->whereNumber('user')->name('users.toggle-status');

            Route::get('/coupons', [Admin\CouponController::class, 'index'])->name('coupons.index');
            Route::post('/coupons', [Admin\CouponController::class, 'store'])->name('coupons.store');
            Route::patch('/coupons/{coupon}/toggle', [Admin\CouponController::class, 'toggle'])->whereNumber('coupon')->name('coupons.toggle');
            Route::delete('/coupons/{coupon}', [Admin\CouponController::class, 'destroy'])->whereNumber('coupon')->name('coupons.destroy');

            Route::get('/settings/tax', [Admin\TaxSettingController::class, 'show'])->name('settings.tax');
            Route::put('/settings/tax', [Admin\TaxSettingController::class, 'update'])->name('settings.tax.update');
        });
    });
});
