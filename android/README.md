# SocialShop — App Android

App mua sắm native (Kotlin + Jetpack Compose, Material 3) dùng chung dữ liệu với website qua **Mobile API v1** (`/api/v1`, xác thực Bearer token Laravel Sanctum).

## Chức năng

- **Trang chủ:** danh mục, Flash Sale có đếm ngược, các nhóm gợi ý (Đang hot, Bán chạy, Đánh giá cao, Hàng mới về), kéo để làm mới.
- **Sản phẩm:** tìm kiếm (có dấu/không dấu như web), lọc theo danh mục, sắp xếp (mới nhất, phổ biến, bán chạy, giá, đánh giá), cuộn vô hạn.
- **Chi tiết:** bộ ảnh vuốt ngang, chọn size/màu theo tồn kho, số lượng, thêm vào giỏ / mua ngay, yêu thích, chia sẻ, đánh giá, sản phẩm tương tự & thường mua cùng.
- **Giỏ hàng** lưu trên server (đồng bộ với website khi đăng nhập cùng tài khoản).
- **Thanh toán:** thông tin giao hàng, chọn tỉnh/thành (tìm không dấu), mã giảm giá, báo giá đúng như web (phí ship theo khu vực + VAT), COD hoặc **VNPay** (mở cổng thanh toán trong WebView). Giá thay đổi giữa lúc xem và lúc đặt → server trả 409, app tự lấy báo giá mới để khách xác nhận lại.
- **Đơn hàng:** danh sách, chi tiết, hủy đơn khi còn "Chờ xử lý".
- **Tài khoản:** đăng ký (kèm mã giới thiệu), đăng nhập, quên mật khẩu, sửa thông tin, đổi mật khẩu (đăng xuất các thiết bị khác), chia sẻ mã giới thiệu, đăng xuất.
- **Trợ lý AI:** nút "Hỏi AI" ở trang chủ, trang chi tiết sản phẩm (hỏi luôn về sản phẩm đang xem) và trong Tài khoản. Câu trả lời hiện dần như đang gõ (đọc luồng SSE từ `POST /api/v1/chat`), có câu hỏi gợi ý, nhớ ngữ cảnh 20 lượt. Cùng trợ lý với website: tư vấn sản phẩm, chọn size, phí ship, chính sách đổi trả; đã đăng nhập thì tra được đơn hàng của mình.
- **Quản trị cửa hàng** (chỉ hiện khi đăng nhập tài khoản admin, vd. `admin@socialshop.vn` / `password`):
  - **Tổng quan:** doanh thu và **thuế VAT** tháng này, đơn hôm nay, đơn chờ xử lý, đánh giá chờ duyệt, sản phẩm sắp hết hàng, biểu đồ doanh thu 7 ngày, top bán chạy, đơn mới nhất.
  - **Đơn hàng:** tìm theo mã/tên, lọc trạng thái, xem chi tiết (người mua, sản phẩm, tạm tính – giảm giá – phí ship – VAT – tổng), đổi trạng thái hoặc hủy đơn (có xác nhận; khách nhận email).
  - **Sản phẩm:** tìm, lọc (đang bán/ngừng bán/hết hàng/sắp hết), bật/tắt bán; sửa nhanh giá, giá khuyến mãi, tồn kho và tồn/giá từng biến thể size-màu. Thêm sản phẩm mới, mô tả và ảnh vẫn làm trên web.
  - **Đánh giá:** duyệt, từ chối, xóa.
  - **Người dùng:** tìm, lọc, khóa/mở khóa (khóa thì người đó bị đăng xuất khỏi app).
  - **Mã giảm giá:** thống kê, bật/tắt, xóa, tạo mã mới (theo % hoặc số tiền, đơn tối thiểu, số ngày hiệu lực, giới hạn lượt dùng).
  - **Thuế VAT:** bật/tắt, thuế suất, tên hiển thị, tính trên phí ship, kèm ví dụ tính trên đơn 1.000.000₫.

## Cấu trúc

```
app/src/main/java/com/socialshop/app/
├── ShopApp.kt, MainActivity.kt # @HiltAndroidApp / @AndroidEntryPoint
├── di/                         # Hilt: AppModule (OkHttp, Retrofit, ApiService, AdminApiService, TokenStorage, Json),
│                               #       CacheModule (Room database, HomeCache)
├── core/Format.kt              # định dạng tiền VND, ngày giờ, đổi URL localhost → host backend
├── data/
│   ├── remote/                 # ApiService + AdminApiService (Retrofit), DTO (kotlinx.serialization, snake_case), xử lý lỗi
│   ├── local/
│   │   ├── TokenStorage.kt     # interface giữ token (unit test dùng bản giả)
│   │   ├── TokenStore.kt       # DataStore, đọc bất đồng bộ, token được mã hóa
│   │   ├── TokenCipher.kt      # AES-256-GCM với khóa trong Android Keystore
│   │   └── cache/              # Room: bảng cache trang chủ (danh mục, nhóm, sản phẩm), DAO, HomeCache
│   ├── ShopRepository.kt       # dữ liệu phía khách, giữ phiên & số món trong giỏ; trang chủ có fallback offline
│   ├── AdminRepository.kt      # dữ liệu khu vực quản trị
│   └── ChatRepository.kt       # đọc luồng SSE của trợ lý AI thành Flow<String>
└── ui/                         # Compose: navigation (type-safe routes), theme, components, từng màn hình + ViewModel
    ├── chat/                   # màn Trợ lý AI
    └── admin/                  # tổng quan, đơn hàng, sản phẩm, đánh giá, người dùng, mã giảm giá, thuế VAT
```

**Dependency injection:** dùng Hilt. ViewModel đánh dấu `@HiltViewModel` và lấy bằng `hiltViewModel()`. ViewModel cần tham số từ route (slug, id đơn...) dùng assisted injection: `hiltViewModel<VM, VM.Factory> { it.create(arg) }`.

## Bảo mật phiên đăng nhập

- Token Sanctum được **mã hóa AES-256-GCM** trước khi ghi vào DataStore. Khóa nằm trong **Android Keystore** (phần cứng bảo mật/TEE), không đọc ra được kể cả khi lấy được file dữ liệu của app.
- Token của bản app cũ (lưu dạng rõ) được tự mã hóa lại ở lần mở đầu tiên, không phải đăng nhập lại.
- Đọc token **bất đồng bộ** lúc mở app (không `runBlocking` trên luồng chính). Màn hình chờ đọc xong mới hiện; OkHttp chờ trên luồng nền để request đầu tiên không bị gửi như khách.
- Server trả 401 (token bị thu hồi, tài khoản bị khóa) → app tự xóa token và quay về trạng thái khách.

## Chế độ offline

Trang chủ (danh mục, Flash Sale, các nhóm sản phẩm) được lưu vào **Room** mỗi lần tải thành công, chia thành các bảng `cached_categories`, `cached_sections`, `cached_products`, `cache_meta` và ghi trong một transaction. Mất mạng hoặc server lỗi 5xx thì app hiện bản lưu gần nhất kèm dòng "Không có kết nối — đang xem dữ liệu lưu lúc …". Kéo xuống để tải lại khi có mạng. Các thao tác cần server (giỏ hàng, đặt hàng, quản trị) vẫn báo lỗi mạng như bình thường.

## Kiểm thử

```bash
cd android
./gradlew testDebugUnitTest     # báo cáo: app/build/reports/tests/testDebugUnitTest/index.html
```

Unit test chạy trên JVM với **MockWebServer** đóng vai backend, trả JSON đúng định dạng API thật, nên kiểm tra luôn cả việc đọc JSON snake_case. Hiện có 19 test:

| File | Kiểm tra |
|---|---|
| `ShopRepositoryTest` | lưu cache trang chủ; mất mạng → dùng cache; không có cache → báo lỗi; đăng nhập lưu token và gửi Bearer; 401 → xóa token |
| `ChatRepositoryTest` | đọc luồng SSE từng đoạn, dừng ở `done`; gửi 20 lượt hội thoại gần nhất kèm token; lỗi 422 thành thông báo dễ đọc |
| `AdminRepositoryTest` | parse dashboard (VAT, biểu đồ 7 ngày); lỗi đổi trạng thái đơn; bộ lọc sắp hết hàng |
| `ViewModelTest` | admin đổi trạng thái đơn (thành công / bị từ chối); sửa sản phẩm (validate tại chỗ, gửi tồn từng biến thể); chat hiện câu trả lời stream / lỗi; trang chủ offline |

CI (`.github/workflows/android.yml`) chạy unit test trước khi build APK.

## Chạy app

Hướng dẫn chạy từ đầu đến cuối (MySQL, backend, máy ảo, cài app) và bảng xử lý lỗi thường gặp: xem mục **[Chạy app Android từ đầu đến cuối](../README.md#chạy-app-android-từ-đầu-đến-cuối-windows)** trong README gốc.

Trỏ app tới backend bằng `tmdt.apiBaseUrl` trong `android/local.properties` (mặc định `http://10.0.2.2:8000/`). Tài khoản demo: `user@socialshop.vn` / `password`.

## Build bản phát hành

```bash
./gradlew assembleRelease      # R8 thu gọn + làm rối mã; APK ở app/build/outputs/apk/release/
```

Bản release cần ký bằng keystore riêng (cấu hình `signingConfigs` hoặc ký qua Android Studio → Generate Signed Bundle/APK). Không commit file `.jks`/`.keystore`.

## Lưu ý VNPay

Cổng VNPay chuyển khách về `VNPAY_RETURN_URL` của backend; server cập nhật đơn ngay tại đó. App bắt trang này trong WebView rồi quay về màn chi tiết đơn để tải lại trạng thái. Khi chạy local mà `APP_URL` là `localhost`, app tự đổi sang host trong `tmdt.apiBaseUrl`.
