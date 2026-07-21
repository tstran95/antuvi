package com.fbposter;

import com.fbposter.ai.GeminiClient;
import com.fbposter.ai.PromptSettings;
import com.fbposter.facebook.FacebookPoster;
import com.fbposter.facebook.FacebookTokenHelper;
import com.fbposter.history.PostedHistory;
import com.fbposter.model.Article;
import com.fbposter.rss.RssReader;
import com.fbposter.telegram.TelegramNotifier;

import java.util.List;

/**
 * Điểm khởi chạy: RSS -> lọc bài mới -> Gemini viết lại -> đăng Facebook -> lưu lịch sử.
 *
 * Chạy:  java -jar target/fb-auto-poster.jar
 * Thêm cờ --dry-run để CHỈ chạy thử (không đăng lên Facebook thật).
 */
public class App {

    public static void main(String[] args) {
        List<String> flags = List.of(args);
        Config config = new Config();

        // Chế độ đặc biệt: lấy Page token vĩnh viễn rồi thoát
        if (flags.contains("--get-page-token")) {
            new FacebookTokenHelper().run(
                    config.facebookAppId(),
                    config.facebookAppSecret(),
                    config.facebookUserToken());
            return;
        }

        boolean dryRun = flags.contains("--dry-run");
        if (dryRun) {
            System.out.println("===== CHẾ ĐỘ DRY-RUN: chỉ in kết quả, KHÔNG đăng lên Facebook =====");
        }

        // Kiểm tra cấu hình tối thiểu
        if (isBlank(config.geminiApiKey())) {
            System.out.println("[LỖI] Thiếu gemini.api.key. Hãy điền vào config.properties.");
            return;
        }
        if (!dryRun && (isBlank(config.facebookPageId()) || isBlank(config.facebookToken()))) {
            System.out.println("[LỖI] Thiếu facebook.page.id hoặc access.token. Hãy điền vào config.properties.");
            return;
        }
        if (config.rssSources().isEmpty()) {
            System.out.println("[LỖI] Chưa cấu hình sources.rss.");
            return;
        }

        RssReader rss = new RssReader();
        PromptSettings promptSettings = new PromptSettings(
                config.pageTopic(),
                config.pageTone(),
                config.postMinSentences(),
                config.postMaxSentences(),
                config.postUseEmoji(),
                config.postHashtagCount(),
                config.postExtraInstruction());
        GeminiClient gemini = new GeminiClient(config.geminiApiKey(), config.geminiModel(), promptSettings);
        FacebookPoster facebook = new FacebookPoster(
                config.facebookPageId(), config.facebookToken(), config.facebookApiVersion());
        PostedHistory history = new PostedHistory("posted-history.json");
        TelegramNotifier telegram = new TelegramNotifier(
                config.telegramBotToken(), config.telegramChatId());

        // 1. Đọc RSS
        List<Article> articles = rss.readAll(config.rssSources());
        System.out.println("[App] Tổng số bài lấy được: " + articles.size());

        int posted = 0;
        int max = config.maxPostsPerRun();

        // 2. Duyệt từng bài
        for (Article article : articles) {
            if (posted >= max) {
                System.out.println("[App] Đã đạt giới hạn " + max + " bài/lần chạy. Dừng.");
                break;
            }
            if (history.isPosted(article.getLink())) {
                continue; // bỏ qua bài đã đăng
            }

            System.out.println("\n[App] Xử lý: " + article.getTitle());

            // 3. AI viết lại
            String rewritten = gemini.rewrite(article);
            if (isBlank(rewritten)) {
                System.out.println("[App] -> Bỏ qua (AI không trả về nội dung).");
                continue;
            }

            // 4. Chuẩn bị nội dung + đăng lên Facebook (hoặc in ra nếu dry-run)
            boolean includeLink = config.postIncludeSourceLink();

            if (dryRun) {
                System.out.println("---- NỘI DUNG SẼ ĐĂNG ----");
                System.out.println(rewritten);
                if (article.hasImage()) {
                    System.out.println("[Ảnh] " + article.getImageUrl());
                }
                if (includeLink) {
                    System.out.println("Nguồn: " + article.getLink());
                }
                System.out.println("---------------------------");
                history.markPosted(article.getLink());
                posted++;
                continue;
            }

            String postId;
            if (article.hasImage()) {
                // Bài có ảnh: đăng ảnh, chèn link nguồn vào caption (nếu bật)
                String caption = includeLink
                        ? rewritten + "\n\nNguồn: " + article.getLink()
                        : rewritten;
                postId = facebook.postPhoto(caption, article.getImageUrl());
            } else {
                // Không có ảnh: đăng dạng feed, dùng link để Facebook hiện preview
                String link = includeLink ? article.getLink() : null;
                postId = facebook.post(rewritten, link);
            }

            if (postId != null) {
                history.markPosted(article.getLink());
                posted++;
                telegram.send("✅ Đã đăng bài mới lên Facebook:\n"
                        + article.getTitle()
                        + "\n\nNguồn: " + article.getLink());
            } else {
                System.out.println("[App] -> Đăng thất bại, sẽ thử lại lần chạy sau.");
                telegram.send("⚠️ Đăng THẤT BẠI bài:\n" + article.getTitle());
            }
        }

        System.out.println("\n[App] Hoàn tất. Đã xử lý/đăng " + posted + " bài.");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
