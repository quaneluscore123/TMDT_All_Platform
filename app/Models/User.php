<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;
use Laravel\Sanctum\HasApiTokens;

class User extends Authenticatable
{
    use HasApiTokens, HasFactory, Notifiable;

    protected $fillable = [
        'name',
        'email',
        'password',
        'avatar',
        'phone',
        'address',
        'provider',
        'provider_id',
        'role',
        'is_active',
        'referral_code',
        'marketing_consent',
        'marketing_consent_at',
    ];

    protected $hidden = [
        'password',
        'remember_token',
    ];

    protected function casts(): array
    {
        return [
            'email_verified_at' => 'datetime',
            'password' => 'hashed',
            'marketing_consent' => 'boolean',
            'marketing_consent_at' => 'datetime',
        ];
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    public function isAdmin(): bool
    {
        return $this->role === 'admin';
    }

    /** Bật/tắt đồng ý nhận email tiếp thị, lưu thời điểm thay đổi làm bằng chứng. */
    public function setMarketingConsent(bool $consent): void
    {
        if ($this->marketing_consent === $consent) {
            return;
        }

        $this->forceFill(['marketing_consent' => $consent, 'marketing_consent_at' => now()])->save();
    }

    // ─── Relationships ────────────────────────────────────────────────────────

    public function orders()
    {
        return $this->hasMany(Order::class);
    }

    public function cart()
    {
        return $this->hasOne(Cart::class);
    }

    public function retentionEmails()
    {
        return $this->hasMany(RetentionEmail::class);
    }

    public function wishlist()
    {
        return $this->hasOne(Wishlist::class);
    }

    public function wishlistedProducts()
    {
        if (! $this->wishlist) {
            return Product::query()->whereRaw('1 = 0');
        }

        return $this->wishlist->products();
    }

    public function reviews()
    {
        return $this->hasMany(Review::class);
    }
}
