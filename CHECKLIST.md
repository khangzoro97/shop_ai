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
- [x] 12 test backend, frontend build và lint sạch.

---

## 1. Chặn lỗi nghiêm trọng (P0)

- [x] **Đưa code vào git** (nhánh `main`, đã có commit đầu tiên, `.gitignore` loại `node_modules`, `target`, `storage`, `.env`).
  - [ ] Còn lại: đẩy lên một repo riêng tư (GitHub hoặc Cursor) để có bản sao lưu bên ngoài máy.
- [x] **Sửa nội dung sai sự thật.** Ảnh Unsplash được đánh dấu `sample` và ghi "Ảnh minh họa", không còn tên shop bịa. Tiêu đề tự đổi thành "Ảnh mẫu theo phong cách" khi chưa có tác phẩm thật. Có test `PortfolioSeedTests` chặn việc tái phạm.
  - [ ] Còn lại: thay ảnh minh họa bằng ảnh thật bạn làm cho khách (có sự đồng ý của họ), lưu với `sample = false`. Ảnh minh họa hiện có logo các thương hiệu thật (Act+Acre, Curology...), nên thay càng sớm càng tốt.
- [x] **Thanh kéo trước/sau** chỉ hiện khi có một cặp ảnh thật (`beforeUrl` có giá trị và `sample = false`). Khi chưa có, hero hiện ảnh tĩnh kèm nhãn "Ảnh minh họa".
  - [ ] Còn lại: thêm ít nhất 1 cặp trước/sau thật cho hero.
- [x] **Số Zalo, email và ưu đãi làm thử** được chuyển sang `frontend/.env` (mẫu ở `frontend/.env.example`). Khi chưa cấu hình, nút Zalo, link email và các câu nhắc tới Zalo tự ẩn; không còn số giả trên trang.
  - [ ] Còn lại: tạo `frontend/.env` với `VITE_ZALO_PHONE`, `VITE_CONTACT_EMAIL` thật.
- [x] **Banner làm thử miễn phí** chỉ hiện khi `VITE_FREE_TRIAL_IMAGES > 0` **và** đã có kênh liên hệ. Ngược lại, banner chuyển sang ưu đãi "Tặng 30 credit" (đã có thật trong hệ thống).
  - [ ] Còn lại: chốt số ảnh làm thử (mặc định 5; đặt `0` để tắt ưu đãi).

## 2. Tài khoản và bảo mật (P0)

- [ ] **Thêm đăng nhập.** Hiện mọi người dùng chung một tài khoản "Shop Demo" (`CurrentAccount.java`), nên ai cũng xem được ảnh của người khác và dùng chung credit.
  - Gợi ý: đăng nhập bằng Google hoặc OTP số điện thoại/Zalo, Spring Security kèm JWT.
  - Xác minh số điện thoại trước khi tặng 30 credit, để tránh một người tạo nhiều tài khoản lấy credit miễn phí.
- [ ] **Phân quyền ADMIN và CUSTOMER** (cần cho phần quản trị ở mục 6).
- [ ] **Tắt H2 console và giới hạn actuator** ở môi trường production. Profile mặc định đang bật `/h2-console`.
- [ ] **Giới hạn số lần gọi (rate limit)** cho `POST /api/generations`, ví dụ dùng Bucket4j. Mỗi lần gọi đều tốn tiền API thật.
- [ ] **Giới hạn kích thước và kiểm tra định dạng ảnh tải lên ở backend.** Hiện request được phép tới 30MB, và chỉ kiểm tra tiền tố `data:image/`.
- [ ] **Chặn SSRF:** `ImageLoader` chỉ nên tải ảnh từ domain của fal (`*.fal.media`) và kho lưu trữ của mình, không tải link tùy ý.
- [ ] Chạy CORS với domain thật và chỉ dùng HTTPS.

## 3. Dữ liệu và hạ tầng (P0)

- [ ] **Chuyển hẳn sang PostgreSQL.** Profile mặc định đang dùng H2 trong bộ nhớ, nên khởi động lại là mất toàn bộ tài khoản, credit và lịch sử.
- [ ] **Quản lý thay đổi database bằng Flyway** thay cho `ddl-auto: update`.
- [ ] **Chuyển kho file sang Cloudflare R2 hoặc S3.** Hiện file lưu trên ổ đĩa server (`LocalFileStorage`), không dùng được khi chạy nhiều server.
- [ ] **Sao chép kết quả của các quy trình gọi AI một lần** (ảnh sáng tạo, thử đồ, video) về kho lưu trữ của mình. Hiện chúng vẫn là link của fal, có thể hết hạn.
- [ ] **Sửa lỗi có thể hoàn credit 2 lần.** Trong `GenerationService.failAndRefund`, việc hoàn credit và lưu trạng thái job không nằm chung một transaction. Nếu lưu job thất bại, lần kiểm tra sau có thể hoàn thêm một lần nữa. Gộp hai việc vào cùng một transaction.
- [ ] **Khóa tác vụ chạy định kỳ** (ví dụ ShedLock) trước khi chạy hơn 1 server, để hai server không cùng xử lý một job.
- [ ] Triển khai lên server: VPS hoặc Railway/Render cho backend, Vercel/Netlify hoặc Nginx cho frontend.
- [ ] Mua tên miền, cài SSL, sao lưu database hằng ngày.
- [ ] Giám sát lỗi bằng Sentry, kèm log tập trung.
- [ ] Đặt **giới hạn chi tiêu và cảnh báo chi phí** trên tài khoản fal.ai.

## 4. Chất lượng AI (P0)

- [ ] **Chạy thử với `FAL_KEY` thật** cho cả 4 quy trình. Đối chiếu input và output với tài liệu hiện tại của fal: tên model trong `application.yml` và cách dựng input trong `WorkflowInputBuilder.java` chưa được thử với API thật.
- [ ] **Làm 20–30 sản phẩm mỹ phẩm thật** để đánh giá: tách nền có sạch không, bóng đổ có tự nhiên không, ánh sáng sản phẩm có khớp bối cảnh không.
- [ ] **Tính giá vốn thực tế** cho mỗi ảnh và mỗi video, rồi so với giá bán credit để chốt lại bảng giá.
- [ ] Viết **hướng dẫn chụp ảnh đầu vào** cho shop (đủ sáng, nền trơn, sản phẩm rõ nét) và hiển thị ngay trong Studio.
- [ ] Thêm nút **"Làm lại"** cho cùng ảnh đầu vào. Khách thường muốn thử vài phiên bản.

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
