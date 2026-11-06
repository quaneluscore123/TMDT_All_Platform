<?php

use App\Http\Middleware\EnsureUserIsActive;
use App\Http\Middleware\IsAdmin;
use App\Http\Middleware\SecurityHeaders;
use App\Http\Middleware\TrackReferral;
use App\Http\Middleware\TrackVisit;
use Illuminate\Foundation\Application;
use Illuminate\Foundation\Configuration\Exceptions;
use Illuminate\Foundation\Configuration\Middleware;
use Illuminate\Http\Exceptions\ThrottleRequestsException;
use Illuminate\Http\Request;

return Application::configure(basePath: dirname(__DIR__))
    ->withRouting(
        web: __DIR__.'/../routes/web.php',
        api: __DIR__.'/../routes/api.php',
        commands: __DIR__.'/../routes/console.php',
        health: '/up',
    )
    ->withMiddleware(function (Middleware $middleware): void {
        // Chỉ tin X-Forwarded-* từ proxy khai báo trong TRUSTED_PROXIES (vd ngrok: 127.0.0.1).
        // Tin '*' cho phép giả IP để vượt rate limit đăng nhập.
        $trustedProxies = array_filter(array_map('trim', explode(',', (string) env('TRUSTED_PROXIES', ''))));
        if ($trustedProxies) {
            $middleware->trustProxies(at: $trustedProxies);
        }
        $middleware->append(SecurityHeaders::class);
        $middleware->alias([
            'admin' => IsAdmin::class,
        ]);
        $middleware->web(append: [
            EnsureUserIsActive::class,
            TrackReferral::class,
            TrackVisit::class,
        ]);
    })
    ->withExceptions(function (Exceptions $exceptions): void {
        $exceptions->shouldRenderJsonWhen(
            fn (Request $request) => $request->is('api/*') || $request->expectsJson(),
        );

        // Thông báo tiếng Việt khi bị chặn đăng nhập (throttle:login)
        $exceptions->render(function (ThrottleRequestsException $e, Request $request) {
            if ($request->is('login') && $request->isMethod('POST')) {
                if ($request->expectsJson()) {
                    return response()->json([
                        'success' => false,
                        'message' => 'Bạn đã nhập sai nhiều lần. Vui lòng thử lại sau ít phút.',
                    ], 429);
                }

                return redirect()
                    ->route('login')
                    ->withErrors(['email' => 'Bạn đã nhập sai nhiều lần. Vui lòng thử lại sau ít phút.'])
                    ->withInput($request->only('email'));
            }
        });
    })->create();
