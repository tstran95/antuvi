# FB Auto Poster

Ứng dụng Java tự động: **đọc RSS → dùng AI (Gemini) viết lại bài → đăng lên Facebook Page** hàng ngày.

## Cấu trúc
```
fb-auto-poster/
├── pom.xml                     # Maven + dependencies
├── HUONG_DAN.md                # Cách lấy token Facebook & API key
├── .github/workflows/daily.yml # Chạy tự động hàng ngày (miễn phí)
└── src/main/
    ├── java/com/fbposter/
    │   ├── App.java            # Luồng chạy chính
    │   ├── Config.java         # Đọc cấu hình (file + ENV)
    │   ├── model/Article.java
    │   ├── rss/RssReader.java      # Đọc RSS
    │   ├── ai/GeminiClient.java    # AI viết lại bài
    │   ├── facebook/FacebookPoster.java  # Đăng Graph API
    │   └── history/PostedHistory.java    # Chống đăng trùng
    └── resources/config.properties  # ĐIỀN TOKEN & API KEY Ở ĐÂY
```

## Bước 1 — Điền cấu hình
Mở `src/main/resources/config.properties` và điền theo **HUONG_DAN.md**:
- `facebook.page.id`, `facebook.page.access.token`
- `gemini.api.key`
- `sources.rss`

## Bước 2 — Build
```bash
mvn clean package
```
Sinh ra file `target/fb-auto-poster.jar`.

## Bước 3 — Chạy thử (KHÔNG đăng thật)
```bash
java -jar target/fb-auto-poster.jar --dry-run
```
Chỉ cần `gemini.api.key` + `sources.rss`. App sẽ in ra nội dung AI viết lại để bạn xem trước.

## Bước 4 — Chạy thật (đăng lên Facebook)
```bash
java -jar target/fb-auto-poster.jar
```
App tự động: bài nào có ảnh sẽ đăng kèm ảnh, bài không có ảnh đăng dạng link.

## (Tùy chọn) Lấy Page token vĩnh viễn
```bash
java -jar target/fb-auto-poster.jar --get-page-token
```
Xem HUONG_DAN.md mục 6 (cần điền `facebook.app.id`, `facebook.app.secret`,
`facebook.user.access.token` trước).

## Tùy chỉnh giọng văn AI
Sửa trong `config.properties` (không cần đụng code):
`page.topic`, `page.tone`, `post.min.sentences`, `post.max.sentences`,
`post.use.emoji`, `post.hashtag.count`, `post.include.source.link`,
`post.extra.instruction`.

## Bước 5 — Tự động hàng ngày trên GitHub (miễn phí)
1. Đẩy code lên 1 repo GitHub (**private** để an toàn).
2. Vào **Settings → Secrets and variables → Actions → New repository secret**, thêm:
   - `FACEBOOK_PAGE_ID`
   - `FACEBOOK_PAGE_ACCESS_TOKEN`
   - `GEMINI_API_KEY`
   - `SOURCES_RSS` (vd: `https://vnexpress.net/rss/tin-moi-nhat.rss`)
3. Vào tab **Actions**, bật workflow. Nó sẽ chạy mỗi ngày lúc 7h sáng (giờ VN),
   hoặc bấm **Run workflow** để chạy tay.

> Đổi giờ chạy: sửa dòng `cron` trong `.github/workflows/daily.yml`
> (dùng giờ UTC; giờ VN = UTC + 7).

## Lưu ý
- **Không commit** `config.properties` (đã có trong `.gitignore`). Trên GitHub dùng Secrets.
- Token Page nên là loại **dài hạn** (xem HUONG_DAN.md mục 2.3).
- App tự lưu link đã đăng vào `posted-history.json` để không đăng trùng.
