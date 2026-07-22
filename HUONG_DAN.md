# Hướng dẫn liên kết Facebook Page & AI vào ứng dụng

Bạn chỉ cần điền 4 giá trị vào file `src/main/resources/config.properties`:
`facebook.page.id`, `facebook.page.access.token`, `gemini.api.key`, `sources.rss`.

Dưới đây là cách lấy từng cái.

---

## 1. Lấy PAGE ID (dễ nhất)

**Cách A:** Vào Page của bạn → **Settings / About (Giới thiệu)** → kéo xuống mục
**"Page transparency" / "Minh bạch trang"** → sẽ thấy **Page ID** (dãy số).

**Cách B:** Vào https://lookup-id.com/ , dán link Page → ra Page ID.

➡️ Điền vào: `facebook.page.id=...`

---

## 2. Lấy PAGE ACCESS TOKEN (quan trọng nhất)

> Token là "chìa khóa" để app thay bạn đăng bài lên Page.

### Bước 2.1 — Tạo Facebook App (giao diện MỚI theo use case)
1. Vào https://developers.facebook.com/ → đăng nhập → **My Apps → Create App**.
2. Màn hình **"What do you want your app to do?"**: kéo xuống chọn **"Other"** → **Next**.
   (Chọn "Other" để bước sau chọn được type Business — mở đầy đủ quyền Pages.)
3. Màn hình **"Select an app type"**: chọn **Business** → **Next**.
4. Màn hình **"Details"**: nhập **App name** + **contact email**,
   phần **Business portfolio** để trống cũng được → **Create app** → nhập mật khẩu xác nhận.
5. Vào được **Dashboard** của app là xong, làm tiếp Bước 2.2.

### Bước 2.2 — Lấy token bằng Graph API Explorer
1. Vào https://developers.facebook.com/tools/explorer/
2. Ở góc phải, mục **Meta App**: chọn app vừa tạo.
3. Mục **User or Page**: chọn **Get User Access Token** (chọn cái này trước; chọn Page token đôi khi ẩn bớt quyền).
4. Thêm quyền: bấm ô **"Add a permission"** → **GÕ TÊN quyền vào ô tìm kiếm** (đừng cuộn tìm bằng mắt),
   lần lượt thêm 3 quyền — chúng nằm trong nhóm **"Events Groups Pages"**:
   - `pages_manage_posts`  (đăng bài)
   - `pages_read_engagement`
   - `pages_show_list`
5. Bấm **Generate Access Token** → đồng ý các popup (nhớ chọn Page của bạn khi được hỏi).
6. Copy chuỗi token dài hiện ra.

> ❓ **Không tìm thấy / quyền bị mờ không chọn được?**
> - Gõ đúng tên quyền vào ô tìm kiếm trong dropdown (vd `pages_manage_posts`).
> - App phải là loại **Business**, và bạn phải là **Admin của Page**.
> - Dòng ghi *"cần App Review / Advanced Access"* **không sao cả**: bạn là Admin/Developer
>   của app (app ở chế độ **Development**) nên vẫn tự lấy token cho Page của mình được.
>   App Review chỉ cần khi phục vụ người dùng khác.

### Bước 2.3 — Đổi sang token DÀI HẠN (khỏi phải lấy lại mỗi 1-2 tiếng)
Token ở trên chỉ sống ~1 giờ. Làm thêm bước này để có token sống ~60 ngày:

1. Vào **Access Token Debugger**: https://developers.facebook.com/tools/debug/accesstoken/
2. Dán token ngắn vào → bấm **Debug** → bấm **Extend Access Token**.
3. Copy token dài hạn mới.

> 💡 Muốn token **không bao giờ hết hạn**: dùng token dài hạn ở trên gọi API
> `GET /me/accounts` → lấy `access_token` của Page trong kết quả. Token Page này
> thường sống rất lâu. (Khi dựng code xong mình sẽ hướng dẫn bạn chạy 1 lệnh để lấy.)

➡️ Điền vào: `facebook.page.access.token=...`

---

## 3. Lấy GEMINI API KEY (AI miễn phí)

> ⚠️ **QUAN TRỌNG (2026):** Google AI Studio (aistudio.google.com) hiện tại
> **CHỈ tạo key dạng `AQ...`** — đây là OAuth credential, **KHÔNG dùng được**
> với Gemini REST API. Bạn phải lấy key từ **Google Cloud Console** theo hướng dẫn bên dưới.

### Cách lấy key hoạt động (AIza...)

1. Vào https://console.cloud.google.com/apis/credentials (đăng nhập Google).
2. Bấm **CREATE CREDENTIALS** (nút trên cùng) → chọn **API key**.
3. Copy key hiện ra (dạng `AIza....`).
4. (Khuyến nghị) Bấm vào key vừa tạo → mục **API restrictions** → chọn
   **Generative Language API** → Save. Việc này giúp key an toàn hơn.

➡️ Điền vào: `gemini.api.key=...`

> 💡 **Nếu Google Cloud Console cũng chỉ cho ra key `AQ.`:**
> Dùng Phương án B bên dưới (service account + OAuth).

### Phương án B: Dùng Service Account (nếu key AIza không có)

1. Vào https://console.cloud.google.com/ → **IAM & Admin → Service Accounts**.
2. **Create Service Account** → đặt tên → **Create**.
3. Role: chọn **Vertex AI User** (hoặc để trống nếu không cần) → **Done**.
4. Bấm vào service account vừa tạo → tab **Keys** → **Add Key → Create New Key → JSON**.
5. File JSON sẽ tự động tải về.
6. Đặt biến môi trường (Windows):
   ```
   set GOOGLE_APPLICATION_CREDENTIALS=C:\path\to\service-account-key.json
   ```
   Hoặc xóa dòng `gemini.api.key` trong config, app sẽ tự dùng service account.

> Gói free của Gemini đủ để đăng vài chục bài/ngày. Không cần thẻ tín dụng.

---

## 4. Chọn nguồn RSS

Điền các link RSS cách nhau bằng dấu phẩy vào `sources.rss`. Ví dụ:

```
sources.rss=https://vnexpress.net/rss/tin-moi-nhat.rss,https://tuoitre.vn/rss/tin-moi-nhat.rss
```

Cách tìm RSS của 1 trang: thường là `tên-trang/rss` hoặc search "tên trang + rss".

---

## 5. Kiểm tra nhanh token có hoạt động không

Sau khi có token, mở trình duyệt dán (thay `PAGE_ID` và `TOKEN`):

```
https://graph.facebook.com/v21.0/PAGE_ID?fields=name&access_token=TOKEN
```

Nếu trả về tên Page của bạn → ✅ token OK, sẵn sàng đăng bài.

---

## 6. Lấy PAGE TOKEN VĨNH VIỄN (không hết hạn) — dùng công cụ có sẵn

Thay vì cứ 60 ngày phải lấy token lại, làm 1 lần để có token gần như vĩnh viễn:

### Bước 6.1 — Lấy App ID & App Secret
Vào Facebook App → **Settings → Basic** → copy **App ID** và **App Secret**.

### Bước 6.2 — Lấy User token ngắn hạn
Vào Graph API Explorer (như mục 2.2) nhưng chọn **Get User Access Token**,
tick quyền `pages_show_list` và `pages_manage_posts` → Generate → copy token.

### Bước 6.3 — Điền vào config.properties
```
facebook.app.id=...
facebook.app.secret=...
facebook.user.access.token=...   (user token ngắn hạn ở 6.2)
```

### Bước 6.4 — Chạy công cụ
```bash
mvn clean package
java -jar target/fb-auto-poster.jar --get-page-token
```
Màn hình sẽ in ra **Page ID** và **Page Token** (thường không hết hạn).
Copy 2 giá trị đó vào `facebook.page.id` và `facebook.page.access.token`.
Xong! Từ giờ app đăng bài bằng token này.

---

## 7. Nhận THÔNG BÁO TELEGRAM khi đăng thành công (tùy chọn)

### Bước 7.1 — Tạo bot & lấy Bot Token
1. Mở Telegram, tìm **@BotFather** → nhắn `/newbot`.
2. Đặt tên + username cho bot → BotFather trả về **Bot Token** (dạng `123456:ABC...`).
   ➡️ Điền vào `telegram.bot.token`.

### Bước 7.2 — Lấy Chat ID
1. Nhắn 1 tin bất kỳ cho bot vừa tạo (hoặc thêm bot vào group rồi nhắn).
2. Mở trình duyệt, vào (thay TOKEN):
   ```
   https://api.telegram.org/botTOKEN/getUpdates
   ```
3. Tìm `"chat":{"id":...}` → đó là **Chat ID** (có thể là số âm nếu là group).
   ➡️ Điền vào `telegram.chat.id`.

Từ giờ mỗi khi đăng Facebook thành công, bạn sẽ nhận tin nhắn Telegram. ✅

---

## ⚠️ Lưu ý bảo mật
- **KHÔNG** đưa file `config.properties` (chứa token) lên GitHub public.
  File đã được `.gitignore` bỏ qua sẵn.
- Khi chạy trên **GitHub Actions**, ta sẽ lưu token vào **Repository Secrets**
  thay vì file (mình sẽ hướng dẫn ở bước triển khai).
