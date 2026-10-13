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
- [x] 31 test backend chạy trên PostgreSQL thật, frontend build và lint sạch.

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
  - [ ] Còn lại: đăng nhập bằng Google (cần tạo OAuth Client ID trên Google Cloud) và chức năng quên mật khẩu (cần dịch vụ gửi email).
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
  - Bối cảnh được tạo đúng kích thước khung cuối (làm tròn lên bội số 16), không phải phóng to từ ảnh vuông.
  - Ảnh sáng tạo nhắc AI giữ nguyên hình dáng, chữ, logo và màu sản phẩm, và trả về JPEG.
  - Video có thêm negative prompt chống méo nhãn và biến dạng sản phẩm.
- [ ] **Chạy thử với `FAL_KEY` thật.** Key đã nằm trong `backend/.env` (không đưa lên git), nhưng tài khoản fal đang hết số dư nên các lượt gửi đều bị từ chối (credit của khách đã tự hoàn đúng). Nạp 5–10 USD rồi chạy lại bộ thử: 3 ảnh giữ nguyên nhãn, 1 ảnh sáng tạo, 1 thử đồ và 1 video (khoảng 0,5–0,7 USD).
- [ ] **Làm 20–30 sản phẩm mỹ phẩm thật** để đánh giá: tách nền có sạch không, bóng đổ có tự nhiên không, ánh sáng sản phẩm có khớp bối cảnh không.
- [x] **Giá vốn theo bảng giá fal** (tháng 10/2026, tỉ giá khoảng 26.300đ/USD). Giá bán một credit từ 1.453đ (gói Pro) đến 1.980đ (gói 99K).

  | Tính năng | Credit | Giá vốn fal | Giá vốn (đ) | Biên lãi gộp |
  |---|---|---|---|---|
  | Ảnh giữ nguyên nhãn | 2 | tách nền ~0,004 USD + bối cảnh 0,025 USD/megapixel (0,036–0,052 USD) | 1.050–1.480 | 49–73% |
  | Ảnh sáng tạo | 2 | 0,0398 USD/ảnh | 1.050 | 64–74% |
  | Thử đồ ảo | 3 | 0,07 USD/lượt | 1.840 | 58–69% |
  | Video 5 giây | 20 | 0,056 USD/giây | 7.360 | 75–81% |

  Mỗi số điện thoại xác minh nhận 30 credit, tốn tối đa khoảng 22.000đ giá vốn. Lượt "Làm lại" bị trừ credit như lượt mới, nên không làm giảm biên lãi. Giá trên chưa tính lượt AI lỗi (đã hoàn credit nhưng fal vẫn có thể tính tiền).
  - [ ] Còn lại: đối chiếu với hóa đơn fal thật sau đợt chạy thử, nhất là thời gian tính phí của bước tách nền.
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
