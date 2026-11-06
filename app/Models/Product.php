<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Str;

class Product extends Model
{
    use HasFactory;

    protected $fillable = [
        'name', 'slug', 'sku', 'description', 'price', 'sale_price', 'sale_ends_at', 'cost_price',
        'stock', 'views_count', 'brand', 'status', 'category_id',
    ];

    protected function casts(): array
    {
        return [
            'price' => 'integer',
            'sale_price' => 'integer',
            'cost_price' => 'integer',
            'sale_ends_at' => 'datetime',
            'stock' => 'integer',
            'views_count' => 'integer',
        ];
    }

    public function getRouteKeyName(): string
    {
        return 'slug';
    }

    // ─── Relationships ────────────────────────────────────────────────────────

    public function category()
    {
        return $this->belongsTo(Category::class);
    }

    public function images()
    {
        return $this->hasMany(ProductImage::class)->orderBy('sort_order');
    }

    public function primaryImage()
    {
        return $this->hasOne(ProductImage::class)
            ->where('is_primary', true)
            ->orWhere(function ($q) {
                $q->where('is_primary', false)->orderBy('sort_order')->limit(1);
            });
    }

    public function orderItems()
    {
        return $this->hasMany(OrderItem::class);
    }

    public function wishlistItems()
    {
        return $this->hasMany(WishlistItem::class);
    }

    public function wishlistedBy()
    {
        return User::whereHas('wishlist.items', function ($q) {
            $q->where('product_id', $this->id);
        });
    }

    public function reviews()
    {
        return $this->hasMany(Review::class)->with('user')->latest();
    }

    public function variants()
    {
        return $this->hasMany(ProductVariant::class);
    }

    /** Chỉ hiện lượt xem khi đủ lớn — sản phẩm mới vài lượt xem hiện ra dễ phản tác dụng. */
    public const MIN_VIEWS_TO_DISPLAY = 20;

    public function showsViewCount(): bool
    {
        return (int) $this->views_count >= self::MIN_VIEWS_TO_DISPLAY;
    }

    /**
     * 489 → "489", 1.250 → "1,2K", 15.000 → "15K".
     */
    public function formattedViews(): string
    {
        $views = (int) $this->views_count;

        if ($views < 1000) {
            return (string) $views;
        }

        // Làm tròn xuống để không phóng đại số liệu (1.250 → 1,2K)
        $k = $views / 1000;
        $short = $k < 10 ? floor($k * 10) / 10 : floor($k);

        return str_replace('.', ',', rtrim(rtrim(number_format($short, 1, '.', ''), '0'), '.')).'K';
    }

    public function hasVariants(): bool
    {
        return $this->variants()->exists();
    }

    // ─── Scopes ───────────────────────────────────────────────────────────────

    public function scopeActive($query)
    {
        return $query->where('status', 'active');
    }

    public function scopeInStock($query)
    {
        return $query->where('stock', '>', 0);
    }

    // ─── Accessors / Helpers ──────────────────────────────────────────────────

    /**
     * Sinh slug không trùng ("T-Shirt" và "T Shirt" cùng ra "t-shirt" → thêm hậu tố -2, -3...).
     */
    public static function uniqueSlug(string $name, ?int $ignoreId = null): string
    {
        $base = Str::slug($name) ?: 'san-pham';
        $slug = $base;
        $i = 2;

        while (static::where('slug', $slug)
            ->when($ignoreId, fn ($q) => $q->whereKeyNot($ignoreId))
            ->exists()) {
            $slug = $base.'-'.$i++;
        }

        return $slug;
    }

    public function effectivePrice(): int
    {
        return $this->isOnSale() ? $this->sale_price : $this->price;
    }

    /**
     * Lợi nhuận gộp trên 1 sản phẩm theo giá bán hiện hành (null khi chưa nhập giá vốn).
     */
    public function grossProfit(): ?int
    {
        return $this->cost_price === null ? null : $this->effectivePrice() - $this->cost_price;
    }

    /**
     * Biên lợi nhuận gộp (%) = (giá bán − giá vốn) / giá bán.
     */
    public function grossMarginPercentage(): ?float
    {
        $price = $this->effectivePrice();

        return $this->cost_price === null || $price <= 0 ? null : round(($price - $this->cost_price) / $price * 100, 1);
    }

    /**
     * Đang khuyến mãi: có giá giảm thấp hơn giá gốc và (nếu có hạn) chưa quá thời điểm kết thúc.
     */
    public function isOnSale(): bool
    {
        return $this->sale_price !== null
            && $this->sale_price < $this->price
            && ($this->sale_ends_at === null || $this->sale_ends_at->isFuture());
    }

    /**
     * Sản phẩm thuộc Flash Sale: khuyến mãi có mốc kết thúc thật trong tương lai.
     */
    public function scopeFlashSale($query)
    {
        return $query->whereNotNull('sale_price')
            ->whereColumn('sale_price', '<', 'price')
            ->where('sale_ends_at', '>', now());
    }

    public function getImageUrlAttribute(): ?string
    {
        // Dùng quan hệ đã eager-load nếu có (tránh N+1 khi render danh sách product card)
        if ($this->relationLoaded('images')) {
            $img = $this->images->firstWhere('is_primary', true) ?? $this->images->first();
        } else {
            $img = $this->images()->where('is_primary', true)->first() ?? $this->images()->first();
        }

        return $img?->url;
    }

    public function getAverageRatingAttribute(): float
    {
        if (array_key_exists('approved_rating_avg', $this->attributes)) {
            return round((float) $this->attributes['approved_rating_avg'], 1);
        }

        return round($this->reviews()->where('status', 'approved')->avg('rating') ?? 0, 1);
    }

    public function getReviewsCountAttribute($value): int
    {
        if (array_key_exists('reviews_count', $this->attributes)) {
            return (int) $value;
        }

        return $this->reviews()->where('status', 'approved')->count();
    }

    /**
     * Nạp sẵn dữ liệu cho product card (ảnh, số đánh giá đã duyệt, điểm TB) bằng 1 truy vấn gộp.
     */
    public function scopeWithCardData($query)
    {
        $approved = fn ($q) => $q->where('status', 'approved');

        return $query
            ->with('images')
            ->withCount('variants')
            ->withCount(['reviews as reviews_count' => $approved])
            ->withAvg(['reviews as approved_rating_avg' => $approved], 'rating');
    }
}
