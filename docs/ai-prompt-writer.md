# Phân tích: dùng AI viết prompt chi tiết trước khi gọi fal.ai

Ngày: 14/10/2026

## Hai ý tưởng

1. **Từ option sang prompt:** khi khách chọn option trên web (tính năng, vibe, tỉ lệ, chuyển động...), một AI khác (ví dụ OpenAI) viết prompt chi tiết cho ảnh hoặc video. Sau đó prompt này mới được gửi sang fal.ai.
2. **Từ mô tả tự do sang prompt:** khách tự gõ mô tả trên web, AI viết lại thành prompt chi tiết rồi mới gửi sang fal.ai.

## Kết luận

Cả hai đều khả thi và rất rẻ, nhưng cải thiện hai thứ khác nhau:

- **Độ chi tiết và độ khớp của bối cảnh:** tăng rõ, nhất là với video dài và khi khách gõ tiếng Việt.
- **Độ chính xác của sản phẩm (nhãn, chữ, màu bao bì):** không tăng, có thể giảm nếu làm ẩu. Điều này do pipeline quyết định (tách nền, Bria giữ nguyên ảnh thật, kiểm tra chữ bằng OCR), không do prompt.

Nên làm ý tưởng 2. Ý tưởng 1 chỉ nên làm có chọn lọc.

## Về kỹ thuật

- **Không cần thêm nhà cung cấp.** fal có sẵn `openrouter/router`, gọi được GPT, Claude, Gemini bằng chính FAL key. Bản `openrouter/router/vision` còn **nhìn được ảnh sản phẩm**. Gọi thẳng OpenAI cũng được, nhưng thêm một key và một hóa đơn.
- **Chi phí:** khoảng 0,001–0,005 USD mỗi lần viết prompt với model nhỏ, có ảnh thì nhỉnh hơn. So với 0,04 USD cho một ảnh hay khoảng 0,4 USD cho một video 5 giây thì không đáng kể.
- **Thời gian chờ:** thêm 1–5 giây.
- **Gắn vào hệ thống:** job đã chạy theo nhiều bước nối tiếp (tách nền, tạo ảnh, OCR, nối clip video...) qua `WorkflowInputBuilder.firstStage` và `nextStage`. Có hai cách gắn:
  - Thêm bước `PROMPT` ở đầu job.
  - Thêm một API riêng cho nút "Gợi ý prompt" trên giao diện.

## Ý tưởng 1: từ option sang prompt

| Tính năng | Lợi ích | Lý do |
|---|---|---|
| Video, nhất là 20–30 giây | **Lớn** | Hiện các đoạn sau dùng chung một câu "tiếp tục cảnh quay" (`CONTINUE_PROMPT`). AI có thể viết kịch bản riêng cho từng đoạn (cận nhãn, đặt vào bối cảnh, kết thúc), nên video có câu chuyện và không lặp lại. |
| Ảnh giữ nguyên nhãn (Bria product shot) | Vừa | Prompt chỉ quyết định bối cảnh, sản phẩm vẫn là ảnh thật. Nếu AI nhìn được ảnh sản phẩm (ví dụ "chai serum trắng nắp vàng"), nó chọn được màu và đạo cụ hợp hơn. Gần như không có rủi ro cho sản phẩm. |
| Ảnh sáng tạo (nano-banana) | Vừa, có rủi ro | Bối cảnh phong phú hơn. Nhưng prompt càng dài, AI càng dễ vẽ lại sản phẩm và đổi chữ trên nhãn. Phải cấm AI tả chữ, logo và tên thương hiệu. |
| Thử đồ (FASHN), chỉnh ánh sáng, phóng to | Không có | Các model này chỉ nhận lựa chọn cố định (loại đồ, hướng sáng, hệ số phóng), không nhận mô tả tự do. |
| Người mẫu cầm sản phẩm | Ít | Chỉ có một câu hướng dẫn cách cầm. |

Điểm cần cân nhắc:
- **Vibe có sẵn đã ổn định.** 23 vibe trong `Vibes.java` là prompt tiếng Anh viết sẵn và đã chạy thử với key thật.
- **Cái giá của sự đa dạng.** Nếu mỗi lần chọn vibe lại nhờ AI viết lại, kết quả sẽ đa dạng hơn nhưng khó đoán hơn: cùng một vibe có lúc rất đẹp, có lúc lệch.
- **Nút "Làm lại".** Nút này sẽ ra prompt khác, trừ khi lưu lại prompt AI đã viết.

## Ý tưởng 2: từ mô tả tự do sang prompt

Đây là phần **đáng làm nhất**:

- **Tiếng Việt.** Hiện câu khách gõ, thường là tiếng Việt, được nối nguyên văn vào sau prompt tiếng Anh. Model ảnh và video hiểu tiếng Việt kém hơn hẳn tiếng Anh, nhất là các ý tinh tế như "tông be ấm, kiểu Hàn".
- **Câu ngắn và mơ hồ.** Khách gõ "để trên bàn đá, có hoa". AI viết lại thành mô tả đủ chất liệu, ánh sáng, góc máy và độ sâu trường ảnh.
- **Mâu thuẫn.** Khách chọn vibe "Tết" nhưng gõ "phong cách tối giản Bắc Âu"; AI gộp thành một ý thống nhất.

## Rủi ro và cách xử lý

| Rủi ro | Cách xử lý |
|---|---|
| AI bịa chi tiết về sản phẩm ("nắp vàng", "chữ ROSE") làm model vẽ sai | Luật cứng trong prompt hệ thống: không tả nhãn, chữ, logo, màu bao bì. Dùng bản nhìn được ảnh để AI chỉ tả thứ nó thấy. Giữ cảnh báo OCR đang có. |
| Khách lách nội dung cấm qua ô mô tả | Đặt luật trong prompt hệ thống; fal vẫn có bộ lọc an toàn riêng. |
| Kết quả không lặp lại được | Lưu prompt cuối cùng vào job, để "Làm lại" dùng đúng prompt cũ. |
| Khách không biết AI đã viết gì | Dùng nút "✦ AI viết prompt giúp": prompt chi tiết hiện trong ô mô tả, khách sửa được rồi mới bấm Tạo. |

## Cách đo hiệu quả

Không nên đoán. Chọn khoảng 10 sản phẩm thật, tạo mỗi sản phẩm hai bản (prompt hiện tại và prompt do AI viết) với cùng ảnh và cùng vibe, rồi so:

1. **Tỉ lệ bị cảnh báo đổi chữ từ OCR.** Đây là thước đo khách quan cho độ chính xác của sản phẩm.
2. **Chấm mắt** độ đẹp và độ khớp với ý khách.

Chi phí ước tính: khoảng 1–2 USD cho ảnh, cộng vài USD nếu đo cả video.

## Đề xuất thứ tự làm

1. **Nút "AI viết prompt giúp"** cho ô mô tả:
   - Dịch và mở rộng mô tả, giữ luật về sản phẩm.
   - Dùng LLM có nhìn ảnh qua fal OpenRouter.
   - Lưu prompt cuối cùng vào job.
2. **Kịch bản cho video dài:** AI viết prompt riêng cho từng đoạn, thay cho câu "tiếp tục cảnh quay" dùng chung.
3. **Đo** bằng OCR và chấm mắt như trên, rồi mới quyết định có bật tự động cho các vibe ảnh hay không.

---

# Phần 2: Form "chế độ đơn giản" cho khách, kiểu ô Lyrics của Suno

## Ý tưởng

Thay form nhiều bước hiện tại bằng một **ô mô tả lớn** làm trung tâm, giống ô Lyrics của Suno:

- **Ô soạn thảo lớn.** Placeholder: "Mô tả ảnh hoặc video bạn muốn, hoặc để trống để AI tự chọn bối cảnh hợp với sản phẩm".
- **Nút ✦ (AI viết giúp).**
  - Ô trống: AI nhìn ảnh sản phẩm và tự viết mô tả.
  - Ô có chữ: AI mở rộng và làm rõ ý khách.
- **Hoàn tác và làm lại:** quay về câu khách gõ nếu không thích bản AI viết. Đây là chức năng bắt buộc khi có nút ✦.
- **Thư viện mẫu (biểu tượng sách):** thay cho bộ chọn vibe. Chọn một mẫu sẽ **chèn đoạn mô tả mẫu vào ô** để khách sửa tiếp, thay vì là một lựa chọn riêng.
- **Phóng to toàn màn hình** để viết mô tả dài, ví dụ kịch bản video.

Điểm mấu chốt: **ô mô tả là nguồn duy nhất cho nội dung sáng tạo.** Không còn nhiều chỗ cùng quyết định bối cảnh nên không còn xung đột.

## Các cấu hình đang gây xung đột trên form hiện tại

| Cấu hình | Xung đột với | Ở chế độ đơn giản |
|---|---|---|
| Bộ chọn vibe (23 kiểu) và kiểu chuyển động video (8 kiểu) | Ô mô tả: chọn "Tết" nhưng gõ "tối giản Bắc Âu" | **Bỏ.** Chuyển thành thư viện mẫu, chèn chữ vào ô mô tả. |
| Ảnh bối cảnh mẫu | Vibe và ô mô tả (hiện đang phải làm mờ vibe khi có ảnh này) | **Ẩn.** Chỉ có ở chế độ nâng cao. |
| Bố cục (sản phẩm ở giữa, trái, phải) | Câu mô tả kiểu "đặt sản phẩm bên trái" | **Bỏ.** AI đọc vị trí từ mô tả; mặc định ở giữa. |
| Ô "Tên sản phẩm" | Không xung đột, nhưng khách hay bỏ trống | **Bỏ.** AI nhìn ảnh và tự điền tên sản phẩm để tách nền chính xác hơn. |
| Chọn "Ảnh giữ nguyên nhãn" hay "Ảnh sáng tạo" | Khách khó hiểu khác biệt | **Mặc định giữ nguyên nhãn.** "Sáng tạo" chuyển sang nâng cao. |
| Kiểu nhạc nền (6 kiểu) | Câu mô tả kiểu "nhạc Tết rộn ràng" | Thay bằng **công tắc "Có nhạc nền"**. AI chọn kiểu nhạc theo mô tả. |
| Độ phân giải 360p | 360p làm nhòe chữ trên nhãn, đi ngược mục tiêu giữ nhãn | **Ẩn 360p.** Chỉ còn "Tiêu chuẩn (720p)" và "Nét cao (1080p)". |

## Cấu hình phải giữ dạng nút bấm (không để AI đoán)

Các lựa chọn **ảnh hưởng đến giá** phải để khách tự chọn và thấy giá trước khi bấm Tạo. Tuyệt đối không để AI suy ra từ câu mô tả.

- Loại kết quả: **Ảnh sản phẩm | Người mẫu | Video**, chia thành tab theo nhu cầu thay cho 5 thẻ tính năng. Các ô tải ảnh tự đổi theo tab (Người mẫu cần thêm ảnh người).
- Nơi đăng: **Shopee | TikTok | Facebook**, tự quy ra tỉ lệ 1:1, 9:16, 3:4. Khách nghĩ theo sàn, không nghĩ theo tỉ lệ.
- Số phương án (ảnh).
- Thời lượng và độ nét (video).
- Công tắc nhạc nền (+1 credit).

## Luồng đề xuất

1. Chọn tab (Ảnh sản phẩm, Người mẫu hoặc Video) và tải ảnh lên.
2. Gõ mô tả bằng tiếng Việt, hoặc bấm ✦ để AI viết, hoặc chọn từ thư viện mẫu.
3. Chọn nơi đăng và các lựa chọn liên quan đến giá. Nút Tạo hiện tổng credit.
4. Khi bấm Tạo, backend gọi LLM **dịch mô tả tiếng Việt thành cấu hình kỹ thuật**: prompt tiếng Anh, vị trí sản phẩm, tên sản phẩm, kiểu nhạc, kịch bản từng đoạn video. Kết quả **lưu vào job**, để "Làm lại" dùng đúng cấu hình cũ.
5. Thẻ kết quả hiện **tóm tắt tiếng Việt** những gì AI đã hiểu, ví dụ "Bối cảnh: bàn đá cẩm thạch, hoa hồng pastel · Nhạc: nhẹ nhàng". Khách không cần đọc prompt tiếng Anh.

## Luồng chi tiết của nút ✦ (gợi ý, sửa, rồi mới tạo)

Khách gõ một câu đơn giản, AI viết lại chi tiết **ngay trong ô nhập**, khách sửa thoải mái, ưng ý mới bấm Tạo.

1. **Khách gõ ý ngắn**, ví dụ: "serum trên bàn đá, có hoa, ánh nắng sáng".
2. **Bấm ✦ "AI viết chi tiết".**
   - Ô nhập bị khóa và hiện hiệu ứng đang viết (chữ chạy dần hoặc vòng xoay) để khách không gõ chèn vào lúc AI đang trả kết quả.
   - Backend gọi `POST /prompts/assist` với loại kết quả (ảnh hoặc video), câu khách gõ và ảnh sản phẩm nếu đã tải lên.
3. **AI thay nội dung ô bằng bản chi tiết tiếng Việt**, ví dụ: "Chai serum đặt trên mặt bàn đá cẩm thạch trắng vân xám, vài cành hoa hồng pastel phía sau hơi mờ, nắng sớm chiếu xiên từ cửa sổ bên trái tạo bóng mềm, tông màu kem ấm, phong cách ảnh quảng cáo mỹ phẩm cao cấp."
   - Với video: viết theo từng cảnh, ví dụ "Cảnh 1 (0–8 giây): ... Cảnh 2 (8–16 giây): ...", khớp với thời lượng đã chọn.
   - AI **không tả chữ, logo, màu bao bì**; sản phẩm chỉ được gọi chung ("chai serum").
4. **Khách sửa trực tiếp** trên đoạn text đó: xóa, thêm hoặc đổi chi tiết. Có thể bấm ✦ lần nữa để AI viết lại từ bản đã sửa.
5. **Hoàn tác** (↶) quay về đúng câu khách gõ trước khi bấm ✦; **làm lại** (↷) lấy lại bản AI viết.
6. **Ưng ý thì bấm Tạo.** Nội dung trong ô lúc này là mô tả cuối cùng. Credit chỉ bị trừ ở bước này; bấm ✦ không mất credit.

Các chi tiết cần có:

- **Không bắt buộc bấm ✦.** Khách vẫn có thể gõ rồi bấm Tạo luôn, hoặc để trống để AI tự chọn bối cảnh.
- **Chưa tải ảnh vẫn bấm ✦ được** (AI viết dựa trên chữ), nhưng có ảnh thì AI tả bối cảnh hợp sản phẩm hơn.
- **Tăng giới hạn ô nhập** từ 500 lên khoảng 1.500 ký tự, có bộ đếm, vì bản AI viết dài hơn câu khách gõ.
- **Lỗi hoặc quá chậm** (quá khoảng 15 giây): giữ nguyên câu khách gõ, báo "AI chưa viết được, bạn vẫn có thể tạo với mô tả hiện tại".
- **Giới hạn số lần bấm ✦** mỗi phút để tránh phá.
- **Lưu cả hai bản** vào job: câu khách gõ ban đầu và mô tả cuối cùng. Cách này giúp đo xem AI gợi ý có làm kết quả tốt hơn không.

Ô soạn thảo luôn chứa **tiếng Việt** để khách đọc và sửa được. Chỉ đến lúc tạo, backend mới chuyển sang tiếng Anh và tham số kỹ thuật. Như vậy cần hai lần gọi LLM: một lần khi bấm ✦ (không bắt buộc) và một lần khi bấm Tạo. Tổng chi phí vẫn dưới 0,01 USD.

## Chế độ đơn giản và chế độ nâng cao

- **Mặc định:** mọi tài khoản thấy chế độ đơn giản.
- **Chế độ nâng cao:** bật bằng một công tắc "Nâng cao", lưu trong trình duyệt, cho shop rành kỹ thuật. Không nên khóa theo vai trò `CUSTOMER`, vì có shop muốn tự chỉnh từng thông số.
- **Admin:** xem thêm prompt tiếng Anh thật và các bước của job để gỡ lỗi.

## Rủi ro và cách xử lý

| Rủi ro | Cách xử lý |
|---|---|
| LLM lỗi hoặc chậm làm hỏng lần tạo | Dự phòng: dùng mẫu mặc định của tab cộng câu khách gõ, như hiện nay. Job vẫn chạy. |
| AI hiểu sai ý khách | Hiện tóm tắt tiếng Việt trên thẻ kết quả. Sau này có thể thêm bước "xem trước cách AI hiểu" trước khi trừ credit. |
| Bấm ✦ liên tục để phá | Giới hạn số lần mỗi phút như các API khác. Không trừ credit vì chi phí rất nhỏ. |
| Khách sửa bản AI viết thành nội dung mâu thuẫn hoặc thêm tả chữ trên nhãn | Bước dịch khi bấm Tạo vẫn áp luật về sản phẩm và gộp mâu thuẫn; cảnh báo OCR vẫn chạy như cũ. |
| Mất khả năng kiểm soát chi tiết | Chế độ nâng cao vẫn giữ nguyên form hiện tại. |

## Phạm vi công việc ước tính

- **Backend:**
  - API `POST /prompts/assist` cho nút ✦ (LLM có nhìn ảnh qua fal OpenRouter).
  - Bước `PROMPT` đầu job để dịch mô tả thành cấu hình kỹ thuật.
  - Cột lưu cấu hình và tóm tắt.
  - Test với provider giả.
- **Frontend:**
  - Ô soạn thảo có thanh công cụ (hoàn tác, làm lại, ✦, thư viện mẫu, toàn màn hình).
  - Tab loại kết quả, chọn nơi đăng.
  - Công tắc Đơn giản / Nâng cao.
  - Tóm tắt trên thẻ kết quả.
- **Đo hiệu quả:** so cùng bộ 10 sản phẩm giữa chế độ đơn giản và form hiện tại, dùng tỉ lệ cảnh báo OCR và chấm mắt (xem Phần 1).
