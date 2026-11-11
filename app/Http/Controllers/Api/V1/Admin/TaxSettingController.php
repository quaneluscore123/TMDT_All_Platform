<?php

namespace App\Http\Controllers\Api\V1\Admin;

use App\Http\Controllers\Controller;
use App\Models\Setting;
use App\Services\AuditService;
use App\Services\Order\PricingService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * @group Mobile API — Quản trị
 *
 * Thuế VAT áp cho đơn hàng mới (đơn cũ giữ nguyên số thuế lúc đặt). Phí ship theo khu vực vẫn chỉnh trên web.
 */
class TaxSettingController extends Controller
{
    /**
     * Xem cài đặt thuế VAT
     *
     * Kèm ví dụ tính trên đơn 1.000.000₫ để admin kiểm tra.
     */
    public function show(PricingService $pricing): JsonResponse
    {
        return response()->json($this->payload($pricing));
    }

    /**
     * Lưu cài đặt thuế VAT
     *
     * @bodyParam tax_enabled boolean required Bật/tắt thu thuế.
     * @bodyParam tax_rate number required Thuế suất %, 0–100. Example: 8
     * @bodyParam tax_label string required Tên hiển thị. Example: Thuế VAT
     * @bodyParam tax_on_shipping boolean required Tính thuế trên cả phí ship.
     */
    public function update(Request $request, PricingService $pricing): JsonResponse
    {
        $validated = $request->validate([
            'tax_enabled' => 'required|boolean',
            'tax_rate' => 'required|numeric|min:0|max:100',
            'tax_label' => 'required|string|max:50',
            'tax_on_shipping' => 'required|boolean',
        ], [
            'tax_rate.max' => 'Thuế suất tối đa 100%.',
            'tax_rate.min' => 'Thuế suất không được âm.',
        ]);

        $before = [
            'tax_enabled' => Setting::get('tax_enabled'),
            'tax_rate' => Setting::get('tax_rate'),
            'tax_label' => Setting::get('tax_label'),
            'tax_on_shipping' => Setting::get('tax_on_shipping'),
        ];

        $values = [
            'tax_enabled' => $request->boolean('tax_enabled') ? 1 : 0,
            'tax_rate' => round((float) $validated['tax_rate'], 2),
            'tax_label' => trim($validated['tax_label']),
            'tax_on_shipping' => $request->boolean('tax_on_shipping') ? 1 : 0,
        ];

        Setting::put($values);
        AuditService::log('settings_updated', 'Setting', null, ['before' => $before, 'after' => $values, 'source' => 'mobile']);

        return response()->json([
            'message' => 'Đã lưu cài đặt thuế. Đơn hàng mới áp dụng ngay; đơn cũ giữ nguyên số thuế lúc đặt.',
            ...$this->payload($pricing),
        ]);
    }

    /**
     * @return array<string, mixed>
     */
    private function payload(PricingService $pricing): array
    {
        return [
            'tax_enabled' => $pricing->taxEnabled(),
            'tax_rate' => (float) Setting::get('tax_rate', 8),
            'tax_label' => $pricing->taxLabel(),
            'tax_on_shipping' => $pricing->taxOnShipping(),
            'example' => $pricing->breakdown(1000000),
        ];
    }
}
