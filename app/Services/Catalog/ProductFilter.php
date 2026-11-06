<?php

namespace App\Services\Catalog;

use App\Models\Category;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Support\Str;

/**
 * Tìm kiếm / lọc / sắp xếp sản phẩm dùng chung cho trang web và API di động.
 */
class ProductFilter
{
    public const SORTS = ['latest', 'price_asc', 'price_desc', 'name', 'popular', 'best_selling', 'top_rated', 'most_viewed'];

    public function applySearch(Builder $query, ?string $search): void
    {
        if ($search === null || trim($search) === '') {
            return;
        }

        $trimmed = trim($search);
        $variations = array_unique(array_filter([
            $trimmed,
            mb_strtolower($trimmed, 'UTF-8'),
            mb_strtoupper($trimmed, 'UTF-8'),
            mb_convert_case($trimmed, MB_CASE_TITLE, 'UTF-8'),
        ]));
        $slugTerm = Str::slug($trimmed);

        $query->where(function ($q) use ($variations, $slugTerm, $trimmed) {
            // 1. Khớp tên sản phẩm (hỗ trợ có dấu, không dấu, chữ hoa, chữ thường)
            foreach ($variations as $var) {
                $q->orWhereLike('name', "%{$var}%");
            }

            // 2. Khớp mã định danh slug (ao-polo-nam, ao-thun-unisex...)
            if (! empty($slugTerm)) {
                $q->orWhere('slug', 'like', "{$slugTerm}-%")
                    ->orWhere('slug', 'like', "%-{$slugTerm}-%")
                    ->orWhere('slug', 'like', "%-{$slugTerm}")
                    ->orWhere('slug', '=', $slugTerm);
            }

            // 3. Khớp tên hoặc slug của Danh mục (Áo nam, Áo nữ, Đồng hồ, Giày dép...)
            $q->orWhereHas('category', function ($cq) use ($variations, $slugTerm) {
                $cq->where(function ($sub) use ($variations, $slugTerm) {
                    foreach ($variations as $var) {
                        $sub->orWhereLike('name', "%{$var}%");
                    }
                    if (! empty($slugTerm)) {
                        $sub->orWhere('slug', 'like', "{$slugTerm}-%")
                            ->orWhere('slug', 'like', "%-{$slugTerm}-%")
                            ->orWhere('slug', 'like', "%-{$slugTerm}")
                            ->orWhere('slug', '=', $slugTerm);
                    }
                });
            });

            // 4. Khớp mô tả chi tiết: từ khóa ngắn (<= 3 ký tự như "áo", "ao") phải khớp trọn vẹn từ
            // tránh hiện tượng "áo" bị khớp nhầm vào chữ "thông báo" trong mô tả đồng hồ
            if (mb_strlen($trimmed) <= 3) {
                foreach ($variations as $var) {
                    $q->orWhereLike('description', "{$var} %")
                        ->orWhereLike('description', "% {$var} %")
                        ->orWhereLike('description', "% {$var},%")
                        ->orWhereLike('description', "% {$var}.%");
                }
            } else {
                foreach ($variations as $var) {
                    $q->orWhereLike('description', "%{$var}%");
                }
            }
        });
    }

    /**
     * Lọc theo danh mục: danh mục gốc gồm cả các danh mục con trực tiếp.
     */
    public function applyCategory(Builder $query, int|string|null $categoryId): void
    {
        if ($categoryId === null || $categoryId === '') {
            return;
        }

        $category = Category::find($categoryId);

        if ($category && $category->parent_id === null) {
            $categoryIds = Category::query()
                ->where('status', 'active')
                ->where(function ($q) use ($category) {
                    $q->where('id', $category->id)
                        ->orWhere('parent_id', $category->id);
                })
                ->pluck('id');

            $query->whereIn('category_id', $categoryIds);
        } else {
            $query->where('category_id', $categoryId);
        }
    }

    public function applyPriceRange(Builder $query, mixed $min, mixed $max): void
    {
        if ($min !== null && $min !== '') {
            $query->where('price', '>=', $min);
        }

        if ($max !== null && $max !== '') {
            $query->where('price', '<=', $max);
        }
    }

    public function applySort(Builder $query, string $sortBy): void
    {
        $soldCount = ['orderItems as sold_count' => fn ($q) => $q->whereHas('order', fn ($o) => $o->where('status', '!=', 'cancelled'))];

        switch ($sortBy) {
            case 'price_asc':
                $query->orderBy('price', 'asc');
                break;
            case 'price_desc':
                $query->orderBy('price', 'desc');
                break;
            case 'name':
                $query->orderBy('name', 'asc');
                break;
            case 'popular':
                $query->withSum($soldCount, 'quantity')
                    ->orderByDesc('sold_count')->orderByDesc('views_count');
                break;
            case 'best_selling':
                $query->withSum($soldCount, 'quantity')
                    ->orderByDesc('sold_count');
                break;
            case 'top_rated':
                $query->orderByDesc('approved_rating_avg')->orderByDesc('reviews_count');
                break;
            case 'most_viewed':
                $query->orderByDesc('views_count');
                break;
            default:
                $query->latest();
        }
    }

    /**
     * @return array<int, int>
     */
    public function categoryIdsWithDescendants(Category $category): array
    {
        $ids = [(int) $category->id];
        $queue = [(int) $category->id];

        while ($queue) {
            $children = Category::query()
                ->where('status', 'active')
                ->whereIn('parent_id', $queue)
                ->pluck('id')
                ->map(fn ($id) => (int) $id)
                ->all();

            $queue = array_values(array_diff($children, $ids));
            $ids = array_merge($ids, $queue);
        }

        return $ids;
    }
}
