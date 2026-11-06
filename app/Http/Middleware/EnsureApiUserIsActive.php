<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

/**
 * Bản API của EnsureUserIsActive: tài khoản bị khóa → thu hồi token đang dùng, trả 403.
 */
class EnsureApiUserIsActive
{
    public function handle(Request $request, Closure $next): Response
    {
        $user = $request->user();

        if ($user && ! $user->is_active) {
            $user->currentAccessToken()?->delete();

            return response()->json(['message' => 'Tài khoản của bạn đã bị khóa. Vui lòng liên hệ hỗ trợ.'], 403);
        }

        return $next($request);
    }
}
