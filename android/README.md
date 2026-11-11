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
├── di/AppModule.kt             # Hilt: OkHttp, Retrofit, ApiService, AdminApiService, TokenStore, Json
├── core/Format.kt              # định dạng tiền VND, ngày giờ, đổi URL localhost → host backend
├── data/
│   ├── remote/                 # ApiService + AdminApiService (Retrofit), DTO (kotlinx.serialization, snake_case), xử lý lỗi
│   ├── local/TokenStore.kt     # lưu token bằng DataStore
│   ├── ShopRepository.kt       # dữ liệu phía khách, giữ phiên & số món trong giỏ
│   ├── AdminRepository.kt      # dữ liệu khu vực quản trị
│   └── ChatRepository.kt       # đọc luồng SSE của trợ lý AI thành Flow<String>
└── ui/                         # Compose: navigation (type-safe routes), theme, components, từng màn hình + ViewModel
    ├── chat/                   # màn Trợ lý AI
    └── admin/                  # tổng quan, đơn hàng, sản phẩm, đánh giá, người dùng, mã giảm giá, thuế VAT
```

**Dependency injection:** dùng Hilt. ViewModel đánh dấu `@HiltViewModel` và lấy bằng `hiltViewModel()`. ViewModel cần tham số từ route (slug, id đơn...) dùng assisted injection: `hiltViewModel<VM, VM.Factory> { it.create(arg) }`.

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
