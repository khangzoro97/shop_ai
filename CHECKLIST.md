# Fortfio: Checklist hoàn thiện để bán dịch vụ

Rà soát ngày 13/10/2026. Mô hình đã chốt: **làm dịch vụ trước, công cụ sau**, tập trung vào **shop mỹ phẩm và thời trang ở Việt Nam**.

Mức ưu tiên:
- **P0**: bắt buộc xong trước khi nhận tiền của khách đầu tiên.
- **P1**: nên xong trong tháng đầu bán hàng.
- **P2**: làm khi đã có khách trả tiền đều đặn.

---

## Hiện trạng: những gì đã có

- [x] Trang portfolio: hero có thanh kéo trước/sau, dịch vụ, tác phẩm, bảng giá, hỏi đáp, nút Zalo nổi.
- [x] AI Studio với 4 quy trình: ảnh giữ nguyên nhãn (tách nền + tạo bối cảnh + ghép), ảnh sáng tạo, thử đồ ảo, video 5 giây.
- [x] Ra ảnh đúng 3 tỉ lệ sàn: 1:1, 3:4, 9:16.
- [x] Hệ thống credit: trừ an toàn khi nhiều yêu cầu cùng lúc, tự hoàn credit khi AI lỗi hoặc quá 15 phút.
- [x] Chế độ giả lập (mock) khi chưa có `FAL_KEY`, nên phát triển không tốn tiền API.
- [x] Đăng nhập, xác minh số điện thoại, phân quyền và các lớp bảo vệ cơ bản (chi tiết ở mục 2).
- [x] PostgreSQL + Flyway, hỗ trợ R2/S3, bộ triển khai Docker kèm HTTPS và sao lưu (chi tiết ở mục 3).
- [x] 34 test backend chạy trên PostgreSQL thật, frontend build và lint sạch.

---

## Cải thiện màn Home và Studio (làm trước)

Tổng hợp từ bản đánh giá UI/UX ngày 14/10/2026 (`docs/ux-review.md`, điểm 5,5/10). Hai lỗi chặn doanh thu là OTP không gửi được và chưa có thanh toán; chúng nằm ở mục 2 và mục 5, ở đây chỉ ghi phần giao diện đi kèm.

### Studio: chặn khách mới (P0)

- [x] **Hết đường cụt khi thiếu credit.** Khi số dư nhỏ hơn giá lượt tạo, thẻ `CreditShortfall` hiện ngay trên nút Tạo (nút bị khóa, không đợi server báo lỗi), dùng chung cho chế độ đơn giản và nâng cao. Chưa xác minh SĐT: nút "Xác minh SĐT, nhận 30 credit" mở thẻ OTP, cuộn tới và đặt con trỏ vào ô số điện thoại, kèm nút "Nhắn Zalo hỗ trợ". Đã xác minh: nút "Nhắn Zalo mua credit".
  - [ ] Còn lại: thêm nút "Nạp credit" khi có trang mua gói (mục 5).
- [x] **Cách khách nhận credit khi chưa có SMS/ZNS:** chọn hướng không đổi giá vốn. Sau khi bấm Gửi mã ở profile `prod` (không có mã hiện trên màn hình), thẻ OTP ghi "Chưa nhận được mã? Nhắn Zalo cho Fortfio kèm số ..., team gửi mã cho bạn". Admin đọc mã trong log backend rồi gửi qua Zalo (xem mục 2).
- [x] **Màn hình đầu trên mobile thấy ô tải ảnh** (đã kiểm tra ở 390x844, ô tải ảnh bắt đầu ở khoảng 600px). Khung chào chỉ còn nhãn "AI Studio" và số dư; tiêu đề và mô tả chỉ hiện từ màn 768px. Thẻ xác minh SĐT thu thành một dòng "Xác minh SĐT, nhận 30 credit · Nhận ngay" trên mobile, mở sẵn trên desktop, có nút thu gọn.
- [x] **Nút "Đơn giản / Nâng cao" không còn xuống dòng** (`whitespace-nowrap`, chữ phụ rút gọn thành "Kể AI nghe" / "Tự chỉnh thông số").
- [x] **Nút Zalo nổi được ẩn ở `/studio` và `/dang-nhap`** (Studio có link Zalo trong thẻ thiếu credit và thẻ OTP), đã bỏ hiệu ứng nhấp nháy. Các nút trong thẻ OTP và thẻ thiếu credit cao tối thiểu 44px.

### Home: niềm tin và nội dung (P0)

- [x] **Hero có trước/sau thật** do chính hệ thống tạo, dùng thanh kéo `BeforeAfter` có sẵn. Hiện dùng sản phẩm mẫu FORTFIO LAB, gắn nhãn "Ví dụ do Fortfio tạo"; hero tự ưu tiên cặp trước/sau của khách (`sample=false`) khi có.
  - [ ] Thêm cặp trước/sau của khách thật đã đồng ý (qua trang quản trị Portfolio).
- [x] **Bỏ toàn bộ ảnh thương hiệu nước ngoài** ở phần Tác phẩm. Thay bằng 5 cặp trước/sau và 1 video 10 giây có nhạc, tạo bằng chính AI Studio (`frontend/public/portfolio/`, migration `V7__demo_portfolio.sql`, `DataSeeder`). Trên điện thoại, chạm vào ảnh để xem ảnh gốc.
- [x] **Cập nhật nội dung theo tính năng hiện tại** (`HomePage.tsx`):
  - "Video ngắn 5 giây" thành "Video 4–30 giây, có nhạc, AI viết kịch bản".
  - Thêm thẻ "Người mẫu cầm sản phẩm".
  - Bước 2 "Chọn vibe & tỉ lệ sàn" thành "Kể AI nghe bạn muốn gì".
  - Dòng giá "Video 5 giây = 20 credit" ghi rõ 720p.
- [x] **Nói giá bằng tiền đồng:** ghi "khoảng 3.300đ/ảnh" (gói Shop) cạnh số credit ở bảng giá và ở nút Tạo. Bảng giá dùng chung `frontend/src/pricing.ts`.
- [x] **Gộp ưu đãi dùng thử:** hiện có 3 ưu đãi chồng nhau (30 credit, team làm thử 5 ảnh, gói "Dùng thử 99K"). Giữ 30 credit làm ưu đãi chính, "team làm thử" là lựa chọn phụ cho người không rành công nghệ, đổi tên gói 99K thành "Gói nhỏ".
- [x] **Nút "Bắt đầu ngay" ở bảng giá** trong lúc chưa có trang mua gói: nút "Mua qua Zalo" và dòng hướng dẫn nhắn tên gói để nhận thông tin chuyển khoản (xem mục 5).
- [x] **Làm rõ cam kết "Giữ nguyên nhãn 100%"** ở hero, thanh khuyến mãi và dải số liệu: chỉ đúng với ảnh sản phẩm giữ nguyên nhãn, video và ảnh người mẫu vẫn có thể méo chữ nhỏ.
- [x] **Chân trang tạo niềm tin:** link Điều khoản, Bảo mật, Hoàn tiền (`PolicyPage.tsx`, các trang `/dieu-khoan`, `/bao-mat`, `/hoan-tien`); tên, mã số thuế, địa chỉ lấy từ `VITE_BUSINESS_*` trong `frontend/.env`.
  - [ ] Điền `VITE_BUSINESS_NAME`, `VITE_BUSINESS_TAX_ID`, `VITE_BUSINESS_ADDRESS` sau khi đăng ký hộ kinh doanh.
  - [ ] Đổi `VITE_CONTACT_EMAIL` sang email theo tên miền thay cho gmail cá nhân.
  - [ ] Nhờ người có chuyên môn pháp lý duyệt nội dung 3 trang chính sách (mục 7), nhất là điều khoản hoàn tiền gói credit trong 7 ngày.

### Thương hiệu Chụp Xịn

- [x] Đổi tên hiển thị trên website thành **Chụp Xịn**, logo mới (`components/Logo.tsx`, `public/favicon.svg`, bộ ảnh trong `public/brand/`), tiêu đề và mô tả SEO, ảnh chia sẻ mạng xã hội (`og-image.png`). Mã nguồn bên trong (package `com.fortfio`, khóa localStorage `fortfio.*`) giữ nguyên để không đăng xuất người dùng.
- [ ] Mua tên miền `chupxin.vn` và `chupxin.com`, rồi đổi `og:image` trong `index.html` sang đường dẫn tuyệt đối (Facebook và Zalo cần URL đầy đủ).
- [ ] Tra cứu và đăng ký nhãn hiệu "Chụp Xịn" tại Cục Sở hữu trí tuệ.
- [ ] Tạo Zalo OA, Facebook page, TikTok cùng tên; dùng `logo-mark-512.png` làm ảnh đại diện.
- [ ] (Tùy chọn) Tạo lại ảnh mẫu với nhãn sản phẩm mới thay cho FORTFIO LAB, khoảng $1,4 phí fal.

### Home: gọn trang trên mobile (P1)

- [x] **Rút ngắn trang chủ:** từ 12.257px còn 8.298px trên điện thoại 390px (khoảng 10 màn hình). Bỏ dải số liệu, gộp 3 bước vào phần Dịch vụ, Tác phẩm thành dải vuốt ngang trên mobile, giảm khoảng cách giữa các phần.
- [x] **Thanh nút chính dính ở cuối màn hình** trên mobile ("Tạo ảnh miễn phí" + Zalo): hiện khi đã cuộn qua nút ở hero, ẩn khi tới phần kêu gọi cuối trang. Nút Zalo nổi được ẩn trên trang chủ mobile để không chồng lên thanh này.
- [x] **Vùng bấm tối thiểu 44px:** logo, nút AI Studio, nút menu, nút lọc Tác phẩm, câu hỏi FAQ, link chân trang (xếp 2 cột trên mobile). Bỏ các link "Thử ngay" ở từng thẻ dịch vụ.
- [x] **Bớt chuyển động cùng lúc** trên mobile: khối màu trôi, sticker bay chỉ chạy từ màn hình md trở lên; ẩn dải tên sàn chạy ngang và huy hiệu xoay trên mobile. Sửa tràn ngang 1px (do dải tên sàn bị xoay nghiêng, không phải sticker).
- [x] **Tách rõ hai hướng mua:** phần "Chọn cách hợp với shop bạn" gồm "Tự làm trên AI Studio" (30 credit) và "Gửi ảnh, team làm hộ" (làm thử miễn phí, gói content tháng), thay cho banner ưu đãi và thẻ "Gói content tháng". Nút phụ ở hero đổi thành "Gửi ảnh, team làm hộ".

### Studio: tăng tỉ lệ dùng thật (P1)

- [x] **Cho thử trước, đăng ký sau:** mục "Thử ngay" trên trang chủ (nút chính ở hero dẫn xuống đây). Khách tải 1 ảnh sản phẩm, chọn 1 trong 3 bối cảnh, nhận ảnh xem trước 720px có watermark "CHỤP XỊN · ẢNH THỬ" (tab giữ nguyên nhãn, 1 phương án, không trừ credit). Đăng nhập xong, ảnh thử tự chuyển vào lịch sử AI Studio kèm ảnh gốc. Giới hạn: 2 lần/IP/24 giờ và tối đa 30 lần/ngày cho cả trang (khoảng 0,05 USD mỗi lần, dưới 2 USD/ngày), lưu trong bảng `trial_run` nên khởi động lại không mất đếm.
  - [ ] Còn lại: chỉnh `TRIAL_MAX_PER_DAY` theo ngân sách quảng cáo; đặt `TRIAL_ENABLED=false` nếu cần tắt nhanh.
- [x] **Đăng nhập Google một chạm và "Quên mật khẩu"** (xem mục 2). Nút "Tiếp tục với Google" chỉ hiện khi có `GOOGLE_CLIENT_ID`. Quên mật khẩu gửi đường dẫn dùng 1 lần, hết hạn sau 30 phút, đặt xong tự đăng nhập.
  - [ ] Còn lại: tạo OAuth Client ID (Google Cloud Console, loại Web application, origin `https://<domain>`) rồi đặt `GOOGLE_CLIENT_ID`; đặt `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` (Brevo, Amazon SES, hoặc Gmail với mật khẩu ứng dụng). Khi chưa có SMTP, đường dẫn đặt lại chỉ được ghi vào log backend.
  - [ ] Còn lại: đăng nhập Zalo cần tạo ứng dụng trên Zalo for Developers và được duyệt quyền lấy thông tin người dùng.
- [x] **Cảnh báo ảnh đầu vào trước khi trừ credit:** ngay sau khi tải ảnh, AI (Gemini 2.5 Flash, khoảng 0,001 USD mỗi ảnh, tối đa 20 lần/phút mỗi tài khoản) nhận ra banner có chữ, ảnh nhiều sản phẩm, đồ đang mặc trên người, ảnh có người; Studio hiện cảnh báo vàng kèm nút chuyển sang tab phù hợp. Mô tả có người trong tab giữ nguyên nhãn cũng được cảnh báo. Chỉ là gợi ý, không chặn tạo ảnh.
- [x] **Đo phễu chuyển đổi:** bảng `funnel_event` (vào trang, bấm "Tạo ảnh" hoặc thử ảnh, bấm Zalo, tải ảnh) cùng dữ liệu tài khoản (đăng ký, xác minh SĐT, tạo ảnh đầu tiên). Admin xem ở trang `/quan-tri` theo 7, 30 hoặc 90 ngày.
  - [ ] Còn lại: thêm bước nạp tiền khi có thanh toán online (mục 5).

---

## 1. Chặn lỗi nghiêm trọng (P0)

- [x] **Đưa code vào git** (nhánh `main`, đã có commit đầu tiên, `.gitignore` loại `node_modules`, `target`, `storage`, `.env`).
  - [x] Đã đẩy lên GitHub ([khangzoro97/shop_ai](https://github.com/khangzoro97/shop_ai)).
  - [ ] Còn lại: chuyển repo sang chế độ riêng tư (hiện đang công khai).
- [x] **Sửa nội dung sai sự thật.** Ảnh Unsplash được đánh dấu `sample` và ghi "Ảnh minh họa", không còn tên shop bịa. Tiêu đề tự đổi thành "Ảnh mẫu theo phong cách" khi chưa có tác phẩm thật. Có test `PortfolioSeedTests` chặn việc tái phạm.
  - [ ] Còn lại: thay ảnh minh họa bằng ảnh thật bạn làm cho khách (có sự đồng ý của họ), lưu với `sample = false`. Ảnh minh họa hiện có logo các thương hiệu thật (Act+Acre, Curology...), nên thay càng sớm càng tốt.
- [x] **Thanh kéo trước/sau** chỉ hiện khi có một cặp ảnh thật (`beforeUrl` có giá trị và `sample = false`). Khi chưa có, hero hiện ảnh tĩnh kèm nhãn "Ảnh minh họa".
  - [ ] Còn lại: thêm ít nhất 1 cặp trước/sau thật cho hero.
- [x] **Số Zalo, email và ưu đãi làm thử** được chuyển sang `frontend/.env` (mẫu ở `frontend/.env.example`). Khi chưa cấu hình, nút Zalo, link email và các câu nhắc tới Zalo tự ẩn; không còn số giả trên trang.
  - [x] Đã tạo `frontend/.env` với số Zalo và email thật.
  - [ ] Còn lại: khai báo 3 biến này trên nơi build khi đưa web lên hosting.
- [x] **Banner làm thử miễn phí** chỉ hiện khi `VITE_FREE_TRIAL_IMAGES > 0` **và** đã có kênh liên hệ. Ngược lại, banner chuyển sang ưu đãi "Tặng 30 credit" (đã có thật trong hệ thống).
  - [ ] Còn lại: chốt số ảnh làm thử (mặc định 5; đặt `0` để tắt ưu đãi).

## 2. Tài khoản và bảo mật (P0)

- [x] **Đăng nhập bằng email và mật khẩu** (Spring Security kèm JWT, token 7 ngày, mật khẩu băm bcrypt). Đã bỏ tài khoản chung "Shop Demo": mỗi shop chỉ thấy ảnh và credit của mình. Trang `/dang-nhap` gồm đăng ký và đăng nhập; vào `/studio` khi chưa đăng nhập sẽ được chuyển sang đó.
  - [ ] Còn lại: đặt `JWT_SECRET` (ít nhất 32 ký tự ngẫu nhiên) khi chạy thật. Profile `prod` không khởi động nếu thiếu. Khi chạy dev mà không đặt, mỗi lần khởi động lại backend mọi người sẽ bị đăng xuất.
  - [x] Đăng nhập bằng Google và quên mật khẩu đã có (xem "Studio: tăng tỉ lệ dùng thật").
  - [ ] Còn lại: đặt `GOOGLE_CLIENT_ID` và SMTP khi chạy thật. Đặt lại mật khẩu chưa đăng xuất các thiết bị khác: token cũ vẫn dùng được tới khi hết hạn (7 ngày).
- [x] **Xác minh số điện thoại bằng OTP rồi mới tặng 30 credit.** Tài khoản mới có 0 credit. Mỗi số chỉ xác minh được cho một tài khoản (ràng buộc unique trong database). Mã 6 số, hết hạn sau 5 phút, sai 5 lần thì hủy mã, phải chờ 60 giây mới gửi lại, tối đa 5 mã mỗi giờ.
  - [ ] Còn lại: nối nhà cung cấp SMS (eSMS, SpeedSMS) hoặc Zalo ZNS bằng cách viết một class mới thay cho `LoggingOtpSender`. Hiện mã chỉ được ghi vào log backend. Ở môi trường dev mã hiện luôn trên màn hình; ở profile `prod` thì không, admin có thể đọc log rồi gửi mã cho khách qua Zalo trong thời gian đầu.
- [x] **Phân quyền ADMIN và CUSTOMER.** `/api/admin/**` chỉ dành cho ADMIN (hiện có `GET /api/admin/accounts`). Tài khoản admin được tạo khi khởi động từ biến môi trường `ADMIN_EMAIL` và `ADMIN_PASSWORD`.
- [x] **Bỏ H2 console và giới hạn actuator.** H2 đã được gỡ hẳn (xem mục 3); actuator chỉ mở `/actuator/health`, không kèm chi tiết.
- [x] **Giới hạn số lần gọi:** 6 lần tạo ảnh mỗi phút cho mỗi shop, 10 lần đăng ký hoặc đăng nhập mỗi phút cho mỗi IP.
  - [ ] Còn lại: bộ đếm đang nằm trong bộ nhớ của một server. Chuyển sang Redis (ví dụ Bucket4j) trước khi chạy nhiều server.
- [x] **Kiểm tra ảnh tải lên ở backend:** chỉ nhận ảnh JPG, PNG, WebP tải lên từ máy (kiểm tra cả chữ ký file, không chỉ tiền tố), tối đa 6MB mỗi ảnh và 20MB mỗi request. Không còn nhận link `https://` do người dùng nhập.
- [x] **Chặn SSRF:** `ImageLoader` chỉ tải ảnh từ `fal.media` và `*.fal.media`, không đi theo chuyển hướng (redirect). Danh sách domain nằm ở `fortfio.uploads.allowed-remote-hosts`.
- [x] **CORS theo domain thật và chỉ dùng HTTPS:** `docker-compose.prod.yml` đặt CORS bằng `https://$DOMAIN`, Caddy tự chuyển http sang https và gửi header HSTS. Chỉ còn chờ có tên miền (xem mục 3).
- [ ] Ảnh kết quả trong `/files/...` vẫn xem được nếu có link (đường dẫn là UUID nên không đoán được). Nếu khách cần bảo mật hơn, dùng link có chữ ký và thời hạn khi chuyển sang R2/S3.

## 3. Dữ liệu và hạ tầng (P0)

- [x] **Chuyển hẳn sang PostgreSQL.** Đã bỏ H2. Khi phát triển trên máy, chạy `docker compose up -d postgres` ở thư mục gốc trước khi bật backend. Test tự bật một Postgres riêng bằng Testcontainers (cần Docker đang chạy). Đã thử: khởi động lại backend thì tài khoản, credit và lịch sử vẫn còn.
- [x] **Quản lý database bằng Flyway** (`backend/src/main/resources/db/migration`). Hibernate chỉ còn kiểm tra schema (`ddl-auto: validate`). Muốn đổi cấu trúc bảng thì thêm file `V2__...sql`, không sửa file `V1` đã chạy.
- [x] **Hỗ trợ Cloudflare R2 hoặc S3** (`S3FileStorage`), bật bằng `STORAGE_TYPE=s3` cùng các biến `S3_*` và `STORAGE_PUBLIC_URL`. Đã thử với một giả lập S3: ảnh được ghi và đọc lại để ghép, link công khai mở được.
  - [ ] Còn lại: tạo bucket R2, bật truy cập công khai (hoặc gắn domain như `media.fortfio.vn`), tạo API token rồi điền vào `.env` trên server.
- [x] **Sao chép kết quả cuối về kho lưu trữ của mình** (ảnh sáng tạo, thử đồ, video) ngay khi AI trả về, chỉ từ các domain fal được phép, tối đa 150MB. Nếu tải về thất bại, hệ thống giữ link của fal và ghi log lỗi.
- [x] **Sửa lỗi hoàn credit 2 lần.** Lưu job và hoàn credit giờ nằm trong cùng một transaction, và job có cột `version` (khóa lạc quan). Một bản job cũ không thể ghi đè trạng thái mới để hoàn thêm lần nữa. Có test cho cả trường hợp bản job cũ và hai lượt xử lý chạy song song.
- [x] **Khóa tác vụ định kỳ bằng ShedLock** (bảng `shedlock`): chỉ một server được xử lý danh sách job tại một thời điểm.
- [x] **Bộ triển khai lên VPS** (`docker-compose.prod.yml`): Postgres, backend, Caddy (phục vụ frontend, chuyển tiếp `/api` và `/files`, tự cấp và gia hạn HTTPS) và dịch vụ sao lưu database. Đã chạy thử toàn bộ trên máy: HTTPS, tự chuyển từ http sang https, header bảo mật, đăng nhập admin, Flyway và sao lưu đều hoạt động.
  - [ ] Còn lại: thuê VPS (2 vCPU, 4GB RAM là đủ lúc đầu), cài Docker, chép `.env.production.example` thành `.env` rồi điền giá trị, sau đó chạy `docker compose -f docker-compose.prod.yml up -d --build`.
- [ ] **Mua tên miền** và trỏ DNS (bản ghi A) về IP của VPS. Caddy tự lo phần SSL khi `DOMAIN` đúng và cổng 80, 443 đang mở.
- [x] **Sao lưu database hằng ngày:** dịch vụ `db-backup` dump và nén database mỗi ngày vào `./backups`, giữ 14 ngày.
  - [ ] Còn lại: chép thư mục backup ra ngoài server (ví dụ rclone lên R2 hoặc Google Drive), và thử khôi phục một lần để chắc bản sao lưu dùng được.
- [x] **Sentry đã được tích hợp vào backend,** chỉ hoạt động khi có `SENTRY_DSN`.
  - [ ] Còn lại: tạo project trên sentry.io, điền `SENTRY_DSN`; thêm Sentry cho frontend nếu cần; gom log tập trung khi có nhiều server.
- [ ] Đặt **giới hạn chi tiêu và cảnh báo chi phí** trên tài khoản fal.ai (làm trên trang quản lý của fal, không cần code).

## 4. Chất lượng AI (P0)

- [x] **Đối chiếu input với tài liệu fal** (schema OpenAPI của từng model): cả 5 model trong `application.yml` đều tồn tại và tên tham số khớp. Đã chỉnh thêm:
  - Tách nền dùng bản `General Use (Heavy)` ở độ phân giải 2048 để viền sản phẩm sạch hơn.
  - Bối cảnh được tạo đúng tỉ lệ khung cuối nhưng dưới 1 megapixel, rồi phóng lên. fal tính tiền theo từng megapixel bắt đầu dùng, nên khung 9:16 từ 2–3 megapixel giảm còn 1 (0,025 USD). Sản phẩm vẫn là ảnh thật ở độ phân giải đầy đủ; đã thử thực tế, phông nền không bị mờ thấy rõ.
  - Ảnh sáng tạo nhắc AI giữ nguyên hình dáng, chữ, logo và màu sản phẩm, và trả về JPEG.
  - Video có thêm negative prompt chống méo nhãn và biến dạng sản phẩm.
  - Khi ghép ảnh, tự xóa các mảnh rời nhỏ hơn 30% vật thể lớn nhất mà bước tách nền giữ nhầm. Bộ nhiều sản phẩm có kích thước gần nhau vẫn được giữ đủ.
- [x] **Chạy thử với `FAL_KEY` thật** (tổng chi khoảng 0,9 USD). Cả 4 quy trình đều chạy được, kết quả được chép về kho lưu trữ của mình.
  - Đã sửa một lỗi chỉ lộ ra khi chạy thật: danh sách domain được phép tải về bị viết sai cú pháp YAML, nên mọi ảnh từ `v3b.fal.media` bị chặn và job ghép ảnh thất bại. Đã thêm test đọc cấu hình thật.
  - Ảnh giữ nguyên nhãn, ảnh chụp tốt (serum trên nền trơn): nhãn và chữ giữ đúng 100%, bối cảnh đẹp, mất khoảng 15 giây.
  - Ảnh giữ nguyên nhãn, ảnh chụp xấu: chụp từ trên xuống trên nền xương rồng thì bước tách nền giữ lại cả xương rồng dính liền với sản phẩm, và sản phẩm bị nghiêng. Chụp trên mặt bàn bóng gương thì phần phản chiếu cũng bị giữ, khiến sản phẩm trông lơ lửng. Không sửa được bằng code, nên hướng dẫn chụp ảnh đã ghi rõ hai điểm này. Khi làm dịch vụ, cần kiểm tra ảnh khách gửi trước khi chạy.
  - Ảnh sáng tạo: bối cảnh rất tự nhiên, nhưng AI **sửa chữ nhỏ** trên bao bì ("200 ml" thành "340 ml", "FRAGRANCE-FREE" thành "FRAGANCE FREE"). Điều này khẳng định chỉ nên dùng ảnh giữ nguyên nhãn cho ảnh đăng bán.
  - Thử đồ: áo mặc lên người mẫu đẹp, hình in giữ đúng, nhưng còn sót một chút áo cũ ở vai. Mất khoảng 17 giây.
  - Video: 5 giây, mất khoảng 3 phút để tạo. Nhãn không bị méo, chữ nhỏ hơi mờ. Video giữ tỉ lệ của ảnh gốc, chưa theo tỉ lệ sàn.
- [x] **Nâng cấp Studio theo khảo sát fal (nhóm 1)**, đã chạy thử với key thật (khoảng 0,31 USD):
  - Ảnh giữ nguyên nhãn chuyển sang Bria product shot: AI vẽ bóng đổ, phản chiếu và ánh sáng khớp với sản phẩm thay vì dán sản phẩm lên phông. Sản phẩm luôn được tách nền trước, vì bước tách nền có sẵn của Bria giữ lại vải và giấy quanh sản phẩm. Bộ ghép ảnh cũ vẫn còn, bật lại bằng `fortfio.fal.exact-engine: composite` (khi đó chỉ được 1 phương án).
  - Ô "Tên sản phẩm" (không bắt buộc): khi có tên, AI tách đúng sản phẩm đó và bỏ đồ trang trí, tay cầm, phản chiếu. Ảnh kem nằm giữa chậu xương rồng trước đây hỏng nay ra sạch.
  - Ảnh bối cảnh mẫu (không bắt buộc): khách tải một ảnh bối cảnh mình thích, AI đặt sản phẩm vào bối cảnh tương tự thay vì dùng vibe có sẵn.
  - Chọn vị trí sản phẩm (giữa, lệch trái, lệch phải) và số phương án (1, 2 hoặc 4) cho mỗi lượt. Credit nhân theo số phương án; kết quả có ảnh thu nhỏ để chọn phương án muốn tải về.
  - Ảnh sáng tạo được tự động đọc chữ (OCR) trên ảnh gốc và ảnh kết quả. Nếu thiếu số hoặc chữ trên bao bì, kết quả hiện cảnh báo vàng. Bước kiểm tra lỗi thì job vẫn thành công, chỉ không có cảnh báo.
  - Migration `V3` thêm các lựa chọn mới vào yêu cầu tạo ảnh.
  - fal tự giới hạn ảnh Bria khoảng 1 megapixel (ví dụ 1024x1024, 768x1365 cho 9:16), nên ảnh ra nhỏ hơn khung 1200x1200 mình yêu cầu. Vẫn đủ rõ cho sàn, nhưng nên theo dõi khi làm sản phẩm thật.
- [x] **Nhóm 2: cầm sản phẩm, chỉnh ánh sáng, phóng to, thử đồ FASHN** (chạy thử với key thật khoảng 0,78 USD):
  - **Người mẫu cầm sản phẩm** (Bria product holding): khách tải ảnh người mẫu và ảnh sản phẩm, chọn cách cầm (trước ngực, cạnh mặt, đưa về phía máy). Gương mặt, trang phục và bối cảnh giữ nguyên. AI vẽ lại sản phẩm, nên chữ nhỏ trên nhãn có thể méo; ảnh toàn thân thì sản phẩm nhỏ, không đọc được nhãn. Kết quả luôn được OCR và cảnh báo.
  - **Thử đồ chuyển sang FASHN v1.6:** so sánh trên cùng ảnh, Kolors để sót tay áo hoodie cũ và cắt chữ "ORIGINAL" thành "RIC"; FASHN mặc áo gọn, giữ đủ hình in. FASHN vẫn có lúc sót mũ áo cũ hoặc viết sai chữ, nên có chọn loại đồ, cho tạo 2–4 phương án và OCR chữ trên áo. Chế độ `segmentation_free: false` xóa đồ cũ sạch hơn nhưng cắt áo ngắn theo áo cũ, nên giữ mặc định. Kolors vẫn dùng được bằng cách đổi model trong `application.yml` (chỉ 1 phương án).
  - **Chỉnh ánh sáng** (Bria relight, 9 kiểu như nắng chiều, spotlight, ánh trăng) và **phóng to 2K/4K** (Topaz) là nút "Chỉnh thêm" trên ảnh đã tạo, áp dụng cho phương án đang chọn và tạo ra kết quả mới. Relight vẽ lại cả ảnh (chữ nhỏ có thể đổi) nên có OCR. Phóng to tính theo cạnh dài đích (2048 hoặc 4096px), nên giá có trần; ảnh đã đủ lớn sẽ bị từ chối.
  - Topaz không khôi phục được chữ quá nhỏ: cả 3 model (High Fidelity V3, Standard V2, Text Refine) đều làm méo chữ cao khoảng 10px. Dùng High Fidelity V3 vì chữ lớn đẹp nhất, và ghi rõ giới hạn này trong Studio.
- [x] **Nhóm 3: nâng cấp video** (chạy thử với key thật khoảng 5,2 USD, gồm cả khảo sát model):
  - **So sánh model trên cùng ảnh 9:16, 5 giây, 720p, không tiếng** (giá theo hóa đơn fal): Seedance 1 Pro Fast 0,10 USD; Seedance 1.5 Pro 0,13 USD; Kling 2.5 Turbo 0,35 USD; Vidu Q3 Turbo 0,39 USD (360p 0,18 USD); Seedance 2.0 Mini 0,76 USD; Vidu Q3 0,77 USD. Chọn **Vidu Q3 Turbo**: là model duy nhất giữ đúng bố cục ảnh gốc, máy quay di chuyển theo prompt, chữ trên nhãn nét. Kling và Seedance tự dựng lại sản phẩm (đổi tư thế chai); Seedance 1.5 làm mất chữ trên nhãn giữa video. Vidu có đủ 360p, 720p, 1080p và tối đa 16 giây mỗi đoạn. Seedance vẫn dùng được bằng cách đổi model trong `application.yml`.
  - **Tỉ lệ 9:16, 3:4, 1:1:** Vidu tạo video theo tỉ lệ ảnh đầu vào. Ảnh khác tỉ lệ được Bria Expand vẽ thêm bối cảnh (sản phẩm giữ nguyên) trước khi tạo video.
  - **Thời lượng 5, 10, 20, 30 giây hoặc tự nhập 4–30 giây:** video dài hơn 16 giây được chia thành các đoạn gần bằng nhau. Mỗi đoạn bắt đầu từ khung cuối của đoạn trước (ffmpeg trích khung), rồi được nối lại; đã kiểm tra chỗ nối liền mạch. Video 20 giây mất khoảng 2 phút.
  - **Nhạc nền 6 kiểu** (sôi động, nhẹ nhàng, sang trọng, dễ thương, lễ hội, điện ảnh), do CassetteAI sáng tác riêng, không lời, không lo bản quyền. Nhạc được ghép vào video và tự cắt theo độ dài video. ElevenLabs Music hay hơn nhưng tính 0,6 USD mỗi phút bắt đầu, quá đắt cho clip ngắn.
  - **Giới hạn:** ở 360p, chữ nhỏ trên nhãn bị nhòe. Kiểu chuyển động xoay quanh sản phẩm có thể để lộ mặt sau, và AI tự vẽ chữ ở mặt đó. Các đoạn nối tiếp giữ nhãn quay về phía máy.
- [x] **Chế độ đơn giản cho khách** (thiết kế trong `docs/ai-prompt-writer.md`, phần 2), là chế độ mặc định của Studio; công tắc "Nâng cao" giữ nguyên form cũ:
  - Ba tab Ảnh sản phẩm, Người mẫu (cầm sản phẩm hoặc mặc thử), Video. Khách gõ mô tả tiếng Việt; chỉ các lựa chọn ảnh hưởng giá còn là nút bấm (nơi đăng, số phương án, thời lượng, 720p/1080p, công tắc nhạc). Ẩn vibe, ảnh bối cảnh mẫu, bố cục, tên sản phẩm, 360p.
  - Ô mô tả có hoàn tác/làm lại, mẫu gợi ý, phóng to toàn màn hình, bộ đếm 3.000 ký tự và nút ✦ "AI viết chi tiết" (`POST /api/prompts/assist`, miễn phí, 10 lần/phút, khoảng 3–6 giây). Ảnh được viết theo các dòng có nhãn: bối cảnh, bề mặt, 3–5 đồ vật xung quanh (chất liệu, màu, vị trí), ánh sáng, tông màu, góc máy, phong cách. Video là kịch bản: ý tưởng, bối cảnh cố định, rồi từng cảnh khớp với số đoạn, mỗi cảnh có timeline 2–3 mốc giây. Mỗi đoạn video được dặn là một cú máy liền mạch, không cắt cảnh và máy quay không vòng ra sau sản phẩm (thử thật: trước khi dặn, video bị cắt cảnh; khi máy quay xoay quanh, mặt nhãn bị khuất).
  - Khi bấm Tạo, bước đầu của job là gọi LLM (Gemini 2.5 Flash qua fal OpenRouter, có nhìn ảnh) để dịch mô tả thành prompt tiếng Anh, vị trí sản phẩm, tên sản phẩm, kiểu nhạc và kịch bản từng đoạn video. Bước này mất khoảng 10–25 giây nên chạy nền; kết quả lưu vào job để "Làm lại" dùng lại. LLM lỗi thì job vẫn chạy với prompt mặc định cộng mô tả gốc. Thẻ kết quả hiện "AI hiểu: ..." bằng tiếng Việt.
  - Đã chạy thử với key thật: "serum trên bàn đá, có hoa, nắng sớm, đặt lệch phải" cho ra đúng bàn đá, hoa, vệt nắng qua cửa sổ, sản phẩm lệch phải, nhãn giữ nguyên.
- [ ] **Đo hiệu quả chế độ đơn giản** trên 10 sản phẩm thật (tỉ lệ cảnh báo OCR và chấm mắt so với form nâng cao), như đề xuất trong `docs/ai-prompt-writer.md`.
- [ ] **Làm 20–30 sản phẩm mỹ phẩm thật** để đánh giá thêm: tách nền có sạch không, bóng đổ có tự nhiên không, ánh sáng sản phẩm có khớp bối cảnh không.
- [ ] Video nên được tạo theo tỉ lệ 9:16 cho TikTok (cắt hoặc đặt ảnh sản phẩm vào khung 9:16 trước khi gửi).
- [x] **Giá vốn theo hóa đơn fal thật** (tháng 10/2026, tỉ giá khoảng 26.300đ/USD). Giá bán một credit từ 1.453đ (gói Pro) đến 1.980đ (gói 99K).

  | Tính năng | Credit | Giá vốn fal | Giá vốn (đ) | Biên lãi gộp |
  |---|---|---|---|---|
  | Ảnh giữ nguyên nhãn (Bria product shot) | 2 | 0,04 USD/phương án + tách nền ~0,0005 USD | 1.065 | 63–73% |
  | Ảnh giữ nguyên nhãn, có điền tên sản phẩm | 2 | thêm 0,03 USD tách nền có hướng dẫn | 1.840 | 37–54% |
  | Ảnh giữ nguyên nhãn (bộ ghép cũ, dự phòng) | 2 | bối cảnh 1 megapixel 0,025 USD + tách nền ~0,0005 USD | 680 | 77–83% |
  | Ảnh sáng tạo | 2 | 0,0398 USD/ảnh + OCR ~0,0013 USD/ảnh | 1.120 | 61–72% |
  | Người mẫu cầm sản phẩm | 2 | 0,04 USD + OCR ~0,0013 USD | 1.120 | 61–72% |
  | Thử đồ ảo (FASHN v1.6) | 3 | 0,075 USD/phương án + OCR | 2.010 | 54–66% |
  | Chỉnh ánh sáng | 2 | 0,04 USD + OCR | 1.120 | 61–72% |
  | Phóng to 2K | 3 | ~0,02 USD/megapixel đầu ra, tối đa 4,2 MP | tối đa 2.210 | 49–62% |
  | Phóng to 4K | 12 | ~0,02 USD/megapixel đầu ra, tối đa 16,8 MP | tối đa 8.840 | 49–63% |
  | Video 360p (Vidu Q3 Turbo), mỗi giây | 2 | 0,035 USD/giây | 920 | 68–77% |
  | Video 720p, mỗi giây | 4 | 0,077 USD/giây | 2.025 | 65–74% |
  | Video 1080p, mỗi giây | 6 | 0,112 USD/giây | 2.950 | 66–75% |
  | Nhạc nền cho video | +1 | 0,02 USD mỗi phút bắt đầu | 526 | 64–73% |
  | Mở rộng ảnh sang tỉ lệ video (khi cần, đã tính trong giá video) | 0 | 0,04 USD | 1.050 | — |
  | Ví dụ: video 30 giây 1080p có nhạc | 181 | ~3,42 USD | ~90.000 | 66–75% |
  | Chế độ đơn giản: AI đọc mô tả (mỗi lượt Tạo) và nút ✦ (mỗi lần bấm, miễn phí) | 0 | ~0,001 USD mỗi lần | ~30 | — |
  | Ảnh giữ nguyên nhãn ở chế độ đơn giản | 2 | như dòng "có điền tên sản phẩm" (AI tự điền tên) + ~0,001 USD | ~1.870 | 36–53% |

  Khi chọn nhiều phương án, credit và giá vốn cùng nhân theo số phương án nên biên lãi không đổi. Tách nền có hướng dẫn tính theo lượt, không nhân theo số phương án, nên biên lãi khi điền tên sản phẩm tăng lên khi chọn 2 hoặc 4 phương án. Mỗi số điện thoại xác minh nhận 30 credit, tốn tối đa khoảng 18.000đ giá vốn. Lượt "Làm lại" bị trừ credit như lượt mới, nên không làm giảm biên lãi. Giá trên chưa tính lượt AI lỗi: khi bước ghép ảnh thất bại, credit được hoàn cho khách nhưng fal vẫn tính tiền.
- [x] **Hướng dẫn chụp ảnh đầu vào** hiện ngay dưới ô tải ảnh trong Studio, có bản riêng cho ảnh sản phẩm và cho thử đồ.
- [x] **Nút "Làm lại"** trên mỗi kết quả: tạo lại với cùng ảnh, vibe, tỉ lệ và yêu cầu thêm, không cần tải ảnh lên lại. Ảnh gốc của mỗi yêu cầu được lưu vào kho lưu trữ (migration `V2`), nên các yêu cầu tạo trước bản cập nhật này không có nút. Có giới hạn tốc độ chung với nút tạo, không cho làm lại khi yêu cầu còn đang chạy.

## 5. Thanh toán (P0)

- [ ] **Tích hợp PayOS** (chuyển khoản qua mã QR). Webhook xác nhận thanh toán xong thì tự cộng credit.
- [ ] Thêm bảng **lịch sử giao dịch credit** (nạp, trừ, hoàn) để đối soát và trả lời khi khách khiếu nại.
- [ ] Làm **trang mua gói**. Hiện nút "Bắt đầu ngay" ở bảng giá chỉ dẫn sang Studio.
- [ ] **Chốt chính sách credit:** credit có hạn sử dụng không, có hoàn tiền không.
- [ ] Nếu bán **gói dịch vụ tháng** (làm hộ): chuẩn bị mẫu báo giá, hợp đồng và cách nhận thanh toán (chuyển khoản, xuất hóa đơn).

## 6. Quản trị cho mô hình làm dịch vụ (P1)

- [ ] **Trang admin:** danh sách khách, đơn hàng, job AI; cộng hoặc trừ credit thủ công.
- [ ] **Chế độ agency:** admin tạo ảnh thay cho một shop cụ thể, rồi gửi cho shop duyệt.
- [ ] **Trang duyệt và tải bài cho khách:** khách xem bộ ảnh, đánh dấu "Duyệt" hoặc "Cần sửa", và tải tất cả cùng lúc (file zip).
- [ ] **Quản lý portfolio trong trang admin** (thêm, sửa, xóa, sắp xếp). Hiện portfolio chỉ được nạp cứng trong `DataSeeder.java`.
- [ ] Gửi thông báo khi job xong hoặc đơn hàng thay đổi trạng thái, qua email hoặc Zalo OA.

## 7. Pháp lý tại Việt Nam (P0, nên hỏi thêm luật sư hoặc kế toán)

- [ ] **Đăng ký kinh doanh** (hộ kinh doanh hoặc công ty) để thu tiền, xuất hóa đơn và kê khai thuế.
- [ ] **Thông báo website thương mại điện tử với Bộ Công Thương** (online.gov.vn) nếu website có chức năng đặt hàng hoặc thanh toán trực tuyến.
- [ ] **Chính sách bảo mật dữ liệu cá nhân** theo Nghị định 13/2023/NĐ-CP. Website thu số điện thoại, email và ảnh có thể chứa khuôn mặt người mẫu.
- [ ] Viết **Điều khoản sử dụng**, gồm:
  - Khách chịu trách nhiệm về quyền với ảnh và người mẫu họ tải lên.
  - Không dùng ảnh AI để quảng cáo sai công dụng mỹ phẩm.
  - Bên nào giữ bản quyền ảnh kết quả.
  - Chính sách hoàn tiền.
- [ ] **Lưu ý gắn nhãn nội dung do AI tạo** khi khách đăng lên TikTok, Facebook (theo quy định của từng nền tảng).
- [ ] Thêm **link đến các chính sách** ở footer và ở bước đăng ký hoặc thanh toán.

## 8. Website và marketing (P1)

- [ ] **SEO:** meta description riêng cho từng trang, ảnh chia sẻ Open Graph (ảnh xem trước khi gửi link qua Zalo hoặc Facebook), `sitemap.xml`, `robots.txt`.
- [ ] **Thay favicon** (hiện vẫn là logo mặc định của Vite) và thay `frontend/README.md` mặc định.
- [ ] **Trang 404,** và xử lý lỗi khi không tải được danh sách tính năng (`api.workflows()` trong `StudioPage.tsx` chưa có `catch`).
- [ ] **Gắn công cụ đo lường:** GA4, Meta Pixel, TikTok Pixel; đo số lượt bấm nút Zalo và nút "Tạo ảnh".
- [ ] **Trang case study:** số liệu trước/sau của khách thật (tỉ lệ bấm vào, số đơn), có sự đồng ý của khách.
- [ ] Kiểm tra trên **điện thoại Android tầm trung** (phần lớn chủ shop dùng): tốc độ tải, animation có bị giật không.
- [ ] Rà khả năng truy cập: độ tương phản chữ trên nền gradient, điều hướng bằng bàn phím.

## 9. Tính năng theo lộ trình đã thống nhất (P1 đến P2)

- [ ] **P1 – Tạo cả bộ ảnh cho một trang sản phẩm:** một ảnh đầu vào ra đủ ảnh bìa, ảnh chi tiết và đủ tỉ lệ cho các sàn.
- [ ] **P1 – Mẫu chèn chữ tiếng Việt** (công dụng, thành phần, khuyến mãi) bằng template, không để AI tự viết chữ.
- [ ] **P2 – Video review có giọng đọc và phụ đề tiếng Việt** cho TikTok Shop.
- [ ] **P2 – Thư viện người mẫu Việt/châu Á cố định** để làm lookbook đồng bộ.
- [ ] **P2 – Phản chiếu sản phẩm trên mặt bàn, chỉnh ánh sáng cho khớp, cho phép chọn vị trí đặt sản phẩm** khi ghép ảnh.
- [ ] **P2 – Mở cho shop tự dùng** (công cụ tự phục vụ) khi quy trình làm dịch vụ đã ổn định.

## 10. Quy trình bán hàng (P0, không cần code)

- [ ] Lập danh sách **50 shop mỹ phẩm hoặc skincare** trên TikTok Shop và Shopee, có khoảng 100–2.000 đơn mỗi tháng và ảnh sản phẩm còn xấu.
- [ ] **Làm trước, chào sau:** làm sẵn 3 ảnh và 1 video mẫu từ ảnh trên gian hàng của họ, rồi gửi qua Zalo hoặc inbox.
- [ ] Soạn **mẫu tin nhắn chào hàng**, mẫu báo giá và **quy trình nhận đơn** (nhận ảnh, làm, gửi duyệt, sửa, giao, thu tiền).
- [ ] Liên hệ **đối tác giới thiệu khách:** xưởng gia công mỹ phẩm (OEM), agency vận hành gian hàng, agency KOC/livestream; chia hoa hồng 10–20%.
- [ ] Mở **kênh TikTok** đăng video "ảnh điện thoại thành ảnh studio".
- [ ] **Mục tiêu 30 ngày:** 50 shop được tiếp cận, 10 shop dùng thử, 3 shop trả tiền. Nếu không đạt thì hỏi lý do các shop đã từ chối trước khi viết thêm code.

---

## Thứ tự gợi ý trong 4 tuần

| Tuần | Việc chính |
|------|-----------|
| 1 | Git, Postgres + Flyway, chạy thử fal với key thật trên 20–30 sản phẩm, chốt giá vốn và bảng giá |
| 2 | Đăng nhập, rate limit, kho file R2, PayOS + lịch sử credit |
| 3 | Pháp lý (chính sách, điều khoản, đăng ký kinh doanh), thay nội dung thật, SEO, đo lường, triển khai lên domain |
| 4 | Trang admin và chế độ agency, bắt đầu tiếp cận 50 shop |
