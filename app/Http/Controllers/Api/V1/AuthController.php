<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Requests\Auth\RegisterRequest;
use App\Http\Resources\Api\UserResource;
use App\Models\User;
use App\Services\AuditService;
use App\Services\Auth\AuthService;
use App\Services\Referral\ReferralService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;

/**
 * @group Mobile API — Xác thực
 *
 * Đăng nhập bằng email/mật khẩu, nhận Bearer token (Laravel Sanctum) cho app di động.
 */
class AuthController extends Controller
{
    public function __construct(
        private AuthService $authService,
        private ReferralService $referralService
    ) {}

    /**
     * Đăng ký
     *
     * @unauthenticated
     */
    public function register(RegisterRequest $request): JsonResponse
    {
        $user = $this->authService->register($request->validated());

        if ($code = $request->input('referral_code')) {
            $this->referralService->createReferral($user, $code);
        }

        return $this->tokenResponse($user, $request, 201);
    }

    /**
     * Đăng nhập
     *
     * @unauthenticated
     */
    public function login(Request $request): JsonResponse
    {
        $credentials = $request->validate([
            'email' => 'required|email',
            'password' => 'required|string',
            'device_name' => 'nullable|string|max:100',
        ], [
            'email.required' => 'Vui lòng nhập email.',
            'email.email' => 'Email không hợp lệ.',
            'password.required' => 'Vui lòng nhập mật khẩu.',
        ]);

        $user = User::where('email', $credentials['email'])->first();

        if (! $user || ! $user->password || ! Hash::check($credentials['password'], $user->password)) {
            AuditService::log('login_failed', 'User', $user?->id, ['email' => $credentials['email'], 'channel' => 'mobile'], $user?->id);

            return response()->json([
                'message' => 'Email hoặc mật khẩu không chính xác.',
                'errors' => ['email' => ['Email hoặc mật khẩu không chính xác.']],
            ], 422);
        }

        if (! $user->is_active) {
            AuditService::log('login_blocked', 'User', $user->id, ['email' => $user->email, 'channel' => 'mobile'], $user->id);

            return response()->json(['message' => 'Tài khoản của bạn đã bị khóa. Vui lòng liên hệ hỗ trợ.'], 403);
        }

        AuditService::log('login_success', 'User', $user->id, ['channel' => 'mobile'], $user->id);

        return $this->tokenResponse($user, $request);
    }

    /**
     * Quên mật khẩu (gửi link đặt lại qua email)
     *
     * @unauthenticated
     */
    public function forgotPassword(Request $request): JsonResponse
    {
        $request->validate(['email' => 'required|email']);

        $this->authService->sendResetLink((string) $request->input('email'));

        // Không tiết lộ email có tồn tại hay không
        return response()->json(['message' => 'Nếu email tồn tại trong hệ thống, chúng tôi đã gửi link đặt lại mật khẩu.']);
    }

    /**
     * Đăng xuất (thu hồi token hiện tại)
     */
    public function logout(Request $request): JsonResponse
    {
        AuditService::log('logout', 'User', $request->user()->id, ['channel' => 'mobile']);
        $request->user()->currentAccessToken()?->delete();

        return response()->json(['message' => 'Đã đăng xuất.']);
    }

    private function tokenResponse(User $user, Request $request, int $status = 200): JsonResponse
    {
        $token = $user->createToken($request->input('device_name') ?: 'android')->plainTextToken;

        return response()->json([
            'token' => $token,
            'user' => new UserResource($user),
        ], $status);
    }
}
