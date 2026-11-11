<?php

namespace App\Http\Controllers\Api\V1\Admin;

use App\Http\Controllers\Controller;
use App\Http\Resources\Api\Admin\AdminUserResource;
use App\Models\User;
use App\Services\AuditService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;

/**
 * @group Mobile API — Quản trị
 */
class UserController extends Controller
{
    /**
     * Danh sách người dùng
     *
     * @queryParam search string Tên hoặc email.
     * @queryParam role string customer|admin
     * @queryParam status string active|blocked
     */
    public function index(Request $request): AnonymousResourceCollection
    {
        $request->validate([
            'search' => 'nullable|string|max:100',
            'role' => 'nullable|in:customer,admin',
            'status' => 'nullable|in:active,blocked',
        ]);

        $users = User::withCount('orders')
            ->when($request->input('search'), fn ($q, $search) => $q->where(fn ($w) => $w
                ->whereLike('name', "%{$search}%")
                ->orWhereLike('email', "%{$search}%")))
            ->when($request->input('role'), fn ($q, $role) => $q->where('role', $role))
            ->when($request->input('status'), fn ($q, $status) => $q->where('is_active', $status === 'active'))
            ->latest()
            ->paginate(20);

        return AdminUserResource::collection($users);
    }

    /**
     * Khóa / mở khóa tài khoản
     *
     * Không khóa được tài khoản quản trị. Khóa thì thu hồi mọi token đăng nhập trên app của người đó.
     */
    public function toggleStatus(User $user): JsonResponse
    {
        if ($user->isAdmin()) {
            return response()->json(['message' => 'Không thể khóa tài khoản quản trị viên!'], 403);
        }

        $user->update(['is_active' => ! $user->is_active]);

        if (! $user->is_active) {
            $user->tokens()->delete();
        }

        AuditService::log($user->is_active ? 'user_unblocked' : 'user_blocked', 'User', $user->id);

        return response()->json([
            'message' => $user->is_active ? 'Đã mở khóa tài khoản.' : 'Đã khóa tài khoản.',
            'user' => new AdminUserResource($user->loadCount('orders')),
        ]);
    }
}
