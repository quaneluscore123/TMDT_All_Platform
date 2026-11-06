<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Resources\Api\UserResource;
use App\Services\AuditService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\Rules\Password;

/**
 * @group Mobile API — Tài khoản
 */
class ProfileController extends Controller
{
    /**
     * Thông tin tài khoản
     */
    public function show(Request $request): UserResource
    {
        return new UserResource($request->user());
    }

    /**
     * Cập nhật thông tin
     */
    public function update(Request $request): UserResource
    {
        $validated = $request->validate([
            'name' => 'required|string|max:255',
            'phone' => 'nullable|string|max:20',
            'address' => 'nullable|string|max:500',
            'marketing_consent' => 'nullable|boolean',
        ]);

        $user = $request->user();
        $user->update(collect($validated)->except('marketing_consent')->all());

        if ($request->has('marketing_consent')) {
            $user->setMarketingConsent($request->boolean('marketing_consent'));
        }

        return new UserResource($user->fresh());
    }

    /**
     * Đổi mật khẩu
     */
    public function changePassword(Request $request): JsonResponse
    {
        $request->validate([
            'current_password' => 'required|string',
            'password' => ['required', 'confirmed', Password::min(8)],
        ], [
            'current_password.required' => 'Vui lòng nhập mật khẩu hiện tại.',
            'password.confirmed' => 'Xác nhận mật khẩu mới không khớp.',
            'password.min' => 'Mật khẩu mới phải có ít nhất 8 ký tự.',
        ]);

        $user = $request->user();

        if (! Hash::check($request->input('current_password'), (string) $user->password)) {
            return response()->json([
                'message' => 'Mật khẩu hiện tại không chính xác.',
                'errors' => ['current_password' => ['Mật khẩu hiện tại không chính xác.']],
            ], 422);
        }

        $user->update(['password' => Hash::make($request->input('password'))]);

        // Đăng xuất các thiết bị khác, giữ phiên hiện tại
        $currentId = $user->currentAccessToken()?->id;
        $user->tokens()->when($currentId, fn ($q) => $q->whereKeyNot($currentId))->delete();

        AuditService::log('password_changed', 'User', $user->id, ['channel' => 'mobile']);

        return response()->json(['message' => 'Đổi mật khẩu thành công!']);
    }
}
