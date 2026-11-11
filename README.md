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
| Chatbot AI | `POST chat` (`message`, `history`) → `text/event-stream`, mỗi dòng `data: {"text": "..."}`, sự kiện cuối `"done": true`. Khách vãng lai dùng được; gửi kèm token thì trợ lý tra được đơn của chính khách đó |

**API quản trị** (`/api/v1/admin/...`, chỉ tài khoản `role=admin`, khách thường nhận 403), dùng chung quy tắc với trang admin web:

| Nhóm | Endpoint |
|---|---|
| Tổng quan | `GET admin/dashboard` — doanh thu và **thuế VAT** tháng này (không tính đơn hủy), đơn hôm nay/chờ xử lý, đánh giá chờ duyệt, sắp hết hàng, doanh thu 7 ngày, bán chạy, đơn mới |
| Đơn hàng | `GET admin/orders` (search, status, payment_status), `GET admin/orders/{id}`, `PATCH admin/orders/{id}/status` — không mở lại đơn đã hủy, đơn VNPay chưa thanh toán không được xác nhận/giao, hủy thì hoàn kho |
| Sản phẩm | `GET admin/products` (search, status, low_stock), `GET/PATCH admin/products/{id}` (giá, giá khuyến mãi, tồn kho, tồn từng biến thể, trạng thái), `POST admin/products/{id}/toggle` |
| Đánh giá | `GET admin/reviews` (status), `PATCH admin/reviews/{id}/approve`, `.../reject`, `DELETE admin/reviews/{id}` |
| Người dùng | `GET admin/users` (search, role, status), `PATCH admin/users/{id}/toggle-status` — khóa thì thu hồi token app; không khóa được admin |
| Mã giảm giá | `GET admin/coupons` (kèm thống kê), `POST admin/coupons`, `PATCH admin/coupons/{id}/toggle`, `DELETE admin/coupons/{id}` |
| Thuế VAT | `GET/PUT admin/settings/tax` — bật/tắt, thuế suất, tên hiển thị, tính trên phí ship; trả kèm ví dụ tính trên đơn 1.000.000₫ |

Test API: `php artisan test tests/Feature/Api`. Hướng dẫn chạy app: xem mục [Chạy app Android từ đầu đến cuối](#chạy-app-android-từ-đầu-đến-cuối-windows) bên dưới; chức năng và cấu trúc code app: [android/README.md](android/README.md).

## Chạy app Android từ đầu đến cuối (Windows)

Mô hình chạy local: **MySQL trong Docker** (cổng `3307`) + **Laravel chạy trực tiếp bằng PHP** (cổng `8000`) + **app trên máy ảo Android**. Máy ảo gọi backend qua địa chỉ `10.0.2.2` (= `localhost` của máy tính).

```
Máy ảo Android ──http://10.0.2.2:8000/api/v1──► php artisan serve ──127.0.0.1:3307──► MySQL (Docker)
```

### Yêu cầu

| Công cụ | Ghi chú |
|---|---|
| PHP 8.2+ và Composer | `php -v`, `composer -V` |
| Docker Desktop | chỉ dùng để chạy MySQL |
| Android Studio (Ladybug trở lên) | kèm Android SDK 36 và một máy ảo (AVD) |
| JDK 17+ | Android Studio đã có sẵn JBR; chạy `gradlew` ngoài terminal cần `java` trong PATH |

### Bước 0 — Cài đặt lần đầu (chỉ làm một lần)

Ở thư mục gốc repo:

```powershell
copy .env.example .env
composer install
php artisan key:generate
php artisan storage:link
```

Sửa phần database trong `.env` cho khớp với MySQL trong Docker (thông tin lấy từ `docker-compose.yml`):

```dotenv
DB_CONNECTION=mysql
DB_HOST=127.0.0.1
DB_PORT=3307
DB_DATABASE=social_commerce
DB_USERNAME=social
DB_PASSWORD=social_secret
```

Tạo `android/local.properties` (Android Studio tự tạo khi mở project; file này không commit):

```properties
sdk.dir=C:/Users/<ten-ban>/AppData/Local/Android/Sdk
# Emulator: 10.0.2.2 là máy host. Máy thật cùng Wi-Fi: dùng IP LAN, vd http://192.168.1.10:8000/
# Production: https://ten-mien-cua-ban/
tmdt.apiBaseUrl=http://10.0.2.2:8000/
```

Bỏ trống `tmdt.apiBaseUrl` thì mặc định là `http://10.0.2.2:8000/`. HTTP thường (cleartext) chỉ được bật khi URL bắt đầu bằng `http://`.

Nên thêm `platform-tools` vào PATH để gõ được lệnh `adb` (mở terminal mới sau khi chạy):

```powershell
[Environment]::SetEnvironmentVariable("Path", $env:Path + ";$env:LOCALAPPDATA\Android\Sdk\platform-tools", "User")
```

### Bước 1 — Bật MySQL

Mở **Docker Desktop**, đợi hiện *Engine running*, rồi:

```powershell
docker compose up -d mysql
docker ps                    # đợi cột STATUS của container MySQL hiện (healthy)
```

### Bước 2 — Tạo bảng và dữ liệu mẫu (lần đầu, hoặc khi muốn làm mới dữ liệu)

```powershell
php artisan migrate --seed
```

Muốn xoá sạch và seed lại từ đầu: `php artisan migrate:fresh --seed`.

### Bước 3 — Chạy backend (terminal 1, để chạy suốt)

```powershell
php artisan serve --host=0.0.0.0 --port=8000
```

Kiểm tra: mở <http://127.0.0.1:8000/api/v1/products> trên trình duyệt phải thấy JSON.

### Bước 4 — Bật máy ảo

Android Studio → **Device Manager** → bấm ▶ ở máy ảo. Hoặc bằng lệnh:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -list-avds          # xem tên máy ảo
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd <ten-may-ao>
```

Đợi tới màn hình chính Android, rồi kiểm tra:

```powershell
adb devices                  # phải thấy: emulator-5554   device   (không phải "offline")
```

### Bước 5 — Build và cài app (terminal 2)

```powershell
cd android
.\gradlew.bat installDebug
```

Hoặc mở thư mục `android/` bằng Android Studio (**File → Open**) → đợi Gradle sync → bấm **Run ▶**. Cài xong mở app **SocialShop** trên máy ảo.

Tài khoản demo sau khi seed: khách hàng `user@socialshop.vn` / `password`; quản trị `admin@socialshop.vn` / `password` (vào **Tài khoản → Quản trị cửa hàng**). Nút **Hỏi AI** ở trang chủ mở trợ lý AI.

### Những lần chạy sau

Chỉ cần: bật Docker Desktop → `docker compose up -d mysql` → `php artisan serve --host=0.0.0.0 --port=8000` → bật máy ảo → `.\gradlew.bat installDebug` (hoặc mở thẳng app đã cài nếu không sửa code Android).

### Xử lý lỗi thường gặp

| Lỗi | Nguyên nhân | Cách sửa |
|---|---|---|
| `SQLSTATE[HY000] [2002] No connection could be made because the target machine actively refused it ... Port: 3307` | MySQL chưa chạy: Docker Desktop đang tắt, hoặc chưa chạy `docker compose up -d mysql` | Bật Docker Desktop → `docker compose up -d mysql` → đợi `(healthy)` rồi chạy lại lệnh artisan |
| `failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine` | Docker Desktop chưa mở | Mở Docker Desktop, đợi *Engine running* |
| `Conflict. The container name "/socialshop-mysql" is already in use` | Máy đã có container trùng tên từ **một project khác** (cũng dùng tên `socialshop-*`) | **Đừng** `docker start` container cũ đó — đó là database của project khác, migrate vào sẽ trộn dữ liệu. Tạo file `docker-compose.override.yml` (chỉ dùng trên máy mình, thêm vào `.git/info/exclude`) để đổi tên container, xem bên dưới |
| `Bind for 0.0.0.0:3307 failed: port is already allocated` | Một MySQL khác đang chiếm cổng 3307 | `docker ps` xem container nào, `docker stop <tên>` rồi bật lại MySQL của project này |
| `DeviceException: No online devices found` / `Device is OFFLINE` | Máy ảo chưa khởi động xong, hoặc bị kẹt khi khôi phục snapshot | Đợi tới màn hình chính. Vẫn `offline` → tắt máy ảo, **Cold Boot Now** (Device Manager → ⋮) hoặc chạy emulator với `-no-snapshot-load` |
| `adb : The term 'adb' is not recognized` | `platform-tools` chưa có trong PATH | Thêm vào PATH (Bước 0) hoặc gọi đường dẫn đầy đủ `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe` |
| App mở được nhưng trống / báo lỗi mạng | Backend chưa chạy, hoặc chạy `php artisan serve` thiếu `--host=0.0.0.0` | Chạy đúng lệnh ở Bước 3, kéo xuống để làm mới trong app |
| Máy thật không kết nối được backend | Sai IP hoặc Windows Firewall chặn cổng 8000 | Đặt `tmdt.apiBaseUrl` = IP LAN (`ipconfig`), cho phép cổng 8000 qua firewall, cài lại app |
| Trợ lý AI trả lời ngắn/chung chung, log có `Chat streaming error ... 400 Bad Request` | Khóa `GEMINI_API_KEY` sai hoặc `GEMINI_MODEL` không tồn tại → server dùng câu trả lời dự phòng | Kiểm tra `GEMINI_API_KEY`, `GEMINI_MODEL` trong `.env` (hoặc cấu hình `AI_SERVICE_URL`); xem `storage/logs/laravel.log` |
| Không thấy mục "Quản trị cửa hàng" | Đang đăng nhập tài khoản khách | Đăng xuất, đăng nhập `admin@socialshop.vn` |
| Cửa sổ máy ảo quá to | — | Kéo góc dưới phải cửa sổ để thu nhỏ, hoặc bật *Settings → Tools → Emulator → Launch in the Running Devices tool window*, hoặc chạy emulator với `-scale 0.6` |

**Đổi tên container MySQL khi bị trùng với project khác** — tạo `docker-compose.override.yml` ở thư mục gốc:

```yaml
services:
  mysql:
    container_name: tmdt-mysql
```

```powershell
echo docker-compose.override.yml >> .git\info\exclude
docker stop socialshop-mysql         # tạm dừng MySQL của project kia (không mất dữ liệu)
docker compose up -d mysql           # tạo MySQL riêng cho project này
```

Hai MySQL cùng dùng cổng 3307 nên mỗi lúc chỉ bật một cái. Quay lại project kia: `docker stop tmdt-mysql` rồi `docker start socialshop-mysql`.

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
