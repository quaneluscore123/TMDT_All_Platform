<?php

use App\Http\Controllers\Api\V1\AuthController;
use App\Http\Controllers\Api\V1\CartController;
use App\Http\Controllers\Api\V1\CatalogController;
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
    });
});
