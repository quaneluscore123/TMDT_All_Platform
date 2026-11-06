# SocialShop — DK Social Commerce

Website thương mại điện tử tích hợp mạng xã hội (Laravel 12, Livewire 4, Tailwind 4, MySQL 8) kèm **app Android** native (Kotlin, Jetpack Compose) dùng chung backend qua Mobile API — xem [android/README.md](android/README.md).

## Chức năng chính

**Khách hàng:** đăng ký / đăng nhập (email, Google, Facebook), tìm kiếm – lọc – sắp xếp sản phẩm (phím tắt `/` hoặc `Ctrl+K`; không có kết quả thì gợi ý sản phẩm gần đúng / được xem nhiều), biến thể size/màu, giỏ hàng, **mua hàng không cần đăng ký** (khách vãng lai nhập email → tự tạo tài khoản, nhận link đặt mật khẩu), checkout 2 bước (nhập thông tin → xem lại & đồng ý điều kiện), mã giảm giá, thanh toán COD / VNPay (sandbox), theo dõi & tự hủy đơn khi còn "Chờ xử lý", đánh giá kèm ảnh (sau khi nhận hàng), wishlist, so sánh tối đa 4 sản phẩm (giá tốt nhất, giảm nhiều nhất, đánh giá cao nhất, bán chạy nhất, size/màu còn hàng, lọc chỉ điểm khác nhau).

**Social & nâng cao:** chia sẻ Facebook / Messenger / Zalo / X / Telegram / Email / bảng chia sẻ của điện thoại, sao chép link (kèm mã giới thiệu), chương trình giới thiệu (referral), chatbot AI Gemini (stream SSE, FAQ fallback cho khách), SEO (JSON-LD, sitemap.xml), email xác nhận / cập nhật đơn qua queue.

**Quản trị (`/admin`):** dashboard doanh thu 7 ngày, top xem / bán chạy; **phân tích dữ liệu** (lượt truy cập, phễu chuyển đổi, nguồn truy cập, tỷ lệ chuyển đổi, ROAS); **chiến dịch quảng cáo** Facebook / YouTube / TikTok với link UTM tự sinh; **cài đặt thuế VAT** (tùy chọn tính trên phí ship) và **phí ship theo khu vực** (34 tỉnh/thành sau sắp xếp 2025); **thông báo quản trị** (đơn mới, đã thanh toán, khách hủy, sắp hết hàng, đánh giá chờ duyệt, cần hoàn tiền); thêm sản phẩm kèm biến thể (tạo nhanh tổ hợp Size × Màu); CRUD sản phẩm – ảnh – biến thể, danh mục, mã giảm giá, FAQ chatbot; quản lý đơn (chặn xác nhận/giao đơn VNPay chưa thanh toán), người dùng, kiểm duyệt đánh giá, nhật ký audit.

**Bảo mật:** VNPay xác thực HMAC-SHA512 + kiểm tra mã merchant, số tiền, `vnp_TransactionStatus`, link thanh toán hết hạn theo đơn, IPN idempotent, thanh toán muộn cho đơn đã hủy không mở lại đơn (ghi vết cần hoàn tiền), trừ kho trong transaction có `lockForUpdate`, đơn VNPay thất bại / quá hạn tự hủy và hoàn kho, RBAC admin, chống IDOR đơn hàng, CSRF + security headers, tài khoản bị khóa bị đăng xuất ngay, chỉ tin proxy khai báo trong `TRUSTED_PROXIES`, rate limit (đăng nhập theo email+IP và theo IP, đăng ký, quên mật khẩu, checkout, đánh giá, chatbot).

## Chạy bằng Docker (khuyến nghị)

Yêu cầu: Docker Desktop.

```bash
cp .env.docker.example .env      # điền VNPAY_*, GEMINI_API_KEY, GOOGLE_* nếu cần
docker compose up -d --build
docker compose exec app composer install
docker compose exec app php artisan key:generate
docker compose exec app php artisan migrate --seed
docker compose exec app php artisan storage:link
docker compose exec app php artisan scribe:generate --no-extraction   # tạo Postman/OpenAPI cho /docs
```

| Dịch vụ | URL |
|---|---|
| Website | http://localhost:8081 |
| Tài liệu API (Scribe) | http://localhost:8081/docs |
| Mailpit (xem email) | http://localhost:8025 |
| phpMyAdmin | http://localhost:8888 |
| ai-service (nội bộ) | `http://ai-service:3000/health` — chỉ truy cập từ trong mạng Docker |

Các container `queue-worker` (gửi email), `scheduler` (hủy đơn VNPay quá hạn mỗi 5 phút, backup DB 02:00) và `ai-service` (microservice chatbot AI) chạy tự động.

### Kiến trúc dịch vụ

```
Trình duyệt ─> nginx ─> app (Laravel) ─┬─> mysql
                                      ├─> mailpit (SMTP)          queue-worker, scheduler: tiến trình nền dùng chung code Laravel
                                      └─> ai-service (Node.js) ─> Google Gemini API
```

`ai-service` là microservice độc lập (thư mục [services/ai-service](services/ai-service/README.md)): giữ khóa Gemini, xác thực bằng `AI_SERVICE_TOKEN`, giới hạn tần suất riêng, có healthcheck. Khi dịch vụ lỗi hoặc không cấu hình `AI_SERVICE_URL`, Laravel tự gọi Gemini trực tiếp / trả lời bằng FAQ.
Debug mặc định **tắt** trong Docker; bật khi phát triển bằng `DOCKER_APP_DEBUG=true` trong `.env`.

## Chạy không dùng Docker

Yêu cầu: PHP 8.2+, Composer, Node 20+, MySQL 8.

```bash
cp .env.example .env             # sửa DB_*, VNPAY_*, GEMINI_API_KEY...
composer install
php artisan key:generate
php artisan migrate --seed
php artisan storage:link
npm install && npm run build
php artisan serve                # http://127.0.0.1:8000
php artisan queue:work           # (terminal khác) gửi email
php artisan schedule:work        # (terminal khác) hủy đơn VNPay quá hạn
```

## Tài khoản demo (sau `migrate --seed`)

| Vai trò | Email | Mật khẩu |
|---|---|---|
| Quản trị | admin@socialshop.vn | password |
| Khách hàng | user@socialshop.vn | password |

Seeder tạo sẵn danh mục thời trang 2 cấp (Thời trang nam/nữ, Giày dép, Túi xách, Đồng hồ, Mỹ phẩm), 30 sản phẩm có ảnh, mã giảm giá, FAQ chatbot, ~20 đơn hàng nhiều trạng thái trong 14 ngày gần nhất và đánh giá (đã duyệt / chờ duyệt) để dashboard và màn kiểm duyệt có dữ liệu.

**Thẻ test VNPay sandbox:** ngân hàng NCB — số thẻ `9704198526191432198`, tên `NGUYEN VAN A`, ngày phát hành `07/15`, OTP `123456`.

## Sao lưu và phục hồi cơ sở dữ liệu

```bash
# Sao lưu (tự chạy 02:00 hằng ngày bởi scheduler) → storage/app/backups/database_YYYY-MM-DD_His.sql
docker compose exec app php artisan db:backup

# Phục hồi từ một bản sao lưu
docker compose exec -T mysql mysql -usocial -psocial_secret social_commerce < storage/app/backups/<ten-file>.sql
```

Không dùng Docker: `php artisan db:backup` cần `mysqldump` (đặt đường dẫn qua `MYSQLDUMP_PATH` trong `.env` nếu không có trong PATH); phục hồi bằng `mysql -u<user> -p <database> < file.sql`.

## Đăng nhập mạng xã hội

- **Google:** điền `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` (Google Cloud Console → OAuth client, redirect `APP_URL/auth/google/callback`).
- **Facebook:** điền `FACEBOOK_CLIENT_ID`, `FACEBOOK_CLIENT_SECRET` (Meta for Developers → Facebook Login, redirect `APP_URL/auth/facebook/callback`). Nút Facebook chỉ hiện khi đã cấu hình. Nếu người dùng từ chối cấp quyền email, hệ thống báo lỗi và yêu cầu đăng ký bằng email.

## Triển khai production (HTTPS)

- Đặt `APP_ENV=production`, `APP_DEBUG=false`, `APP_URL=https://...`, `SESSION_SECURE_COOKIE=true` (cookie phiên chỉ gửi qua HTTPS; mặc định đã có `HttpOnly` và `SameSite=lax`).
- Cấu hình chứng chỉ SSL ở web server và chuyển hướng toàn bộ HTTP → HTTPS.
- Nếu chạy sau reverse proxy / tunnel (ngrok, cloudflared, load balancer), khai báo IP proxy trong `TRUSTED_PROXIES` (ví dụ `127.0.0.1`) để Laravel nhận đúng IP khách và HTTPS. Không đặt `*`: kẻ tấn công sẽ giả được IP để vượt giới hạn đăng nhập.
- Thay `VNPAY_TMN_CODE` / `VNPAY_HASH_SECRET` bằng thông tin production và cập nhật dòng `Sitemap:` trong `public/robots.txt`.

## Kiểm thử

```bash
php artisan test                 # SQLite in-memory, không cần MySQL
vendor/bin/pint --test           # kiểm tra code style
```

CI (GitHub Actions, `.github/workflows/tests.yml`) chạy Pint và toàn bộ test trên PHP 8.2 và 8.4, cùng test của microservice (`cd services/ai-service && npm test`). `.github/workflows/android.yml` build APK debug + release mỗi khi thư mục `android/` thay đổi.

## Mobile API & app Android

Backend mở REST API tại `/api/v1` (Laravel Sanctum, gửi `Authorization: Bearer <token>` và `Accept: application/json`), dùng chung nghiệp vụ với website (giỏ hàng, báo giá VAT/phí ship, mã giảm giá, VNPay, hủy đơn...):

| Nhóm | Endpoint |
|---|---|
| Công khai | `GET home`, `categories`, `products` (search, category, sort, min/max_price, page), `products/{slug}`, `products/{slug}/reviews`, `shipping-config` |
| Xác thực | `POST auth/register`, `auth/login` (trả token), `auth/forgot-password`, `auth/logout` |
| Tài khoản | `GET/PUT me`, `PUT me/password` |
| Giỏ hàng | `GET cart`, `POST cart/items`, `PATCH/DELETE cart/items/{id}`, `DELETE cart` |
| Thanh toán | `POST coupons/apply`, `POST checkout/quote`, `POST orders` (kèm `expected_total`; giá đổi → 409) |
| Đơn hàng | `GET orders`, `GET orders/{id}`, `POST orders/{id}/cancel` |
| Khác | `GET wishlist`, `POST wishlist/{productId}/toggle`, `POST products/{slug}/reviews` |

Test API: `php artisan test tests/Feature/Api`. Hướng dẫn chạy app: [android/README.md](android/README.md).

## Quảng cáo & đo lường

1. Admin → **Chiến dịch quảng cáo** → Tạo chiến dịch (nền tảng, trang đích, chi phí).
2. Sao chép **link UTM** (vd. `.../danh-muc/giay-dep?utm_source=tiktok&utm_medium=cpc&utm_campaign=sneaker-week`) dán vào quảng cáo Facebook / mô tả video YouTube / bio TikTok.
3. Website ghi nhận lượt truy cập, xem sản phẩm, thêm giỏ, đặt hàng theo nguồn (last-click, giữ 30 ngày; không lưu IP).
4. Admin → **Phân tích dữ liệu**: tỷ lệ chuyển đổi = đơn / lượt truy cập; ROAS = doanh thu từ chiến dịch / chi phí (cập nhật chi phí từ trình quản lý quảng cáo).

## Cấu trúc chính

```
app/Http/Controllers      # controller khách hàng + Admin/ + Auth/ + Api/V1 (Mobile API)
app/Http/Resources/Api    # định dạng JSON trả cho app
app/Services              # nghiệp vụ: Catalog (lọc/tìm sản phẩm), Order + Pricing (VAT, ship), Payment (VNPay), Cart, Coupon, Referral, Chatbot, Analytics, Audit
routes/api.php            # Mobile API v1
app/Console/Commands      # orders:expire-unpaid, db:backup
database/seeders          # dữ liệu demo
resources/views           # Blade + Livewire
tests/Feature             # test tính năng
services/ai-service       # microservice chatbot AI (Node.js, Dockerfile, test riêng)
android/                  # app Android (Kotlin, Jetpack Compose)
```
