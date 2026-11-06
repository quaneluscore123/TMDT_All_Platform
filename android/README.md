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

## Cấu trúc

```
app/src/main/java/com/socialshop/app/
├── ShopApp.kt, MainActivity.kt
├── core/Format.kt              # định dạng tiền VND, ngày giờ, đổi URL localhost → host backend
├── data/
│   ├── remote/                 # ApiService (Retrofit), DTO (kotlinx.serialization, snake_case), xử lý lỗi
│   ├── local/TokenStore.kt     # lưu token bằng DataStore
│   └── ShopRepository.kt       # nguồn dữ liệu duy nhất cho UI, giữ phiên & số món trong giỏ
└── ui/                         # Compose: navigation (type-safe routes), theme, components, từng màn hình + ViewModel
```

## Chạy app

Yêu cầu: Android Studio (Ladybug trở lên) hoặc JDK 17+ và Android SDK 36.

1. Chạy backend Laravel ở thư mục gốc repo, mở cho máy ảo truy cập:

   ```bash
   php artisan migrate --seed
   php artisan serve --host=0.0.0.0 --port=8000
   ```

2. Trỏ app tới backend trong `android/local.properties` (file này không commit):

   ```properties
   sdk.dir=/đường/dẫn/Android/Sdk
   # Emulator: 10.0.2.2 là máy host. Máy thật cùng Wi-Fi: dùng IP LAN, vd http://192.168.1.10:8000/
   # Production: https://ten-mien-cua-ban/
   tmdt.apiBaseUrl=http://10.0.2.2:8000/
   ```

   Bỏ trống thì mặc định là `http://10.0.2.2:8000/`. HTTP thường (cleartext) chỉ được bật khi URL bắt đầu bằng `http://`.

3. Build & cài:

   ```bash
   cd android
   ./gradlew installDebug        # hoặc mở thư mục android/ bằng Android Studio rồi Run
   ```

Tài khoản demo sau khi seed: `user@socialshop.vn` / `password`.

## Build bản phát hành

```bash
./gradlew assembleRelease      # R8 thu gọn + làm rối mã; APK ở app/build/outputs/apk/release/
```

Bản release cần ký bằng keystore riêng (cấu hình `signingConfigs` hoặc ký qua Android Studio → Generate Signed Bundle/APK). Không commit file `.jks`/`.keystore`.

## Lưu ý VNPay

Cổng VNPay chuyển khách về `VNPAY_RETURN_URL` của backend; server cập nhật đơn ngay tại đó. App bắt trang này trong WebView rồi quay về màn chi tiết đơn để tải lại trạng thái. Khi chạy local mà `APP_URL` là `localhost`, app tự đổi sang host trong `tmdt.apiBaseUrl`.
