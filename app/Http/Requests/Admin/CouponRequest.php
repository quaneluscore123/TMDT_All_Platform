<?php

namespace App\Http\Requests\Admin;

use App\Models\Coupon;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;
use Illuminate\Validation\Validator;

/**
 * Tạo/sửa mã giảm giá — dùng chung cho trang admin web và Mobile API admin.
 */
class CouponRequest extends FormRequest
{
    public function authorize(): bool
    {
        return (bool) $this->user()?->isAdmin();
    }

    /**
     * Viết hoa mã trước khi kiểm tra trùng: "sale10" và "SALE10" là cùng một mã (không phụ thuộc collation DB).
     */
    protected function prepareForValidation(): void
    {
        if (is_string($this->input('code'))) {
            $this->merge(['code' => strtoupper(trim($this->input('code')))]);
        }
    }

    /**
     * @return array<string, mixed>
     */
    public function rules(): array
    {
        $coupon = $this->route('coupon');

        return [
            'code' => [
                'required', 'string', 'max:50', 'alpha_dash',
                Rule::unique('coupons', 'code')->ignore($coupon instanceof Coupon ? $coupon->id : null),
            ],
            'type' => 'required|in:percent,fixed',
            'value' => 'required|integer|min:1',
            'max_discount' => 'nullable|integer|min:0',
            'min_order_amount' => 'required|integer|min:0',
            'start_at' => 'nullable|date',
            'end_at' => 'nullable|date|after_or_equal:start_at',
            'usage_limit' => 'nullable|integer|min:0',
            'status' => 'required|in:active,inactive',
        ];
    }

    public function after(): array
    {
        return [
            function (Validator $validator) {
                if ($validator->errors()->has('value') || $validator->errors()->has('type')) {
                    return;
                }

                $value = (int) $this->input('value');

                if ($this->input('type') === 'percent' && $value > 100) {
                    $validator->errors()->add('value', 'Phần trăm giảm không được quá 100%');
                }

                if ($this->input('type') === 'fixed' && $value < 1000) {
                    $validator->errors()->add('value', 'Số tiền giảm tối thiểu 1,000₫');
                }
            },
        ];
    }

    /**
     * Dữ liệu đã chuẩn hóa để lưu: mã viết hoa, giới hạn lượt dùng 0 = không giới hạn.
     *
     * @return array<string, mixed>
     */
    public function couponData(): array
    {
        $data = $this->validated();
        $data['code'] = strtoupper($data['code']);

        if (array_key_exists('usage_limit', $data) && (int) $data['usage_limit'] === 0) {
            $data['usage_limit'] = null;
        }

        return $data;
    }
}
