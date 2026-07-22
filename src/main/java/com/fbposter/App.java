package com.fbposter;

import com.fbposter.ai.AiClient;
import com.fbposter.ai.DeepSeekClient;
import com.fbposter.ai.GeminiClient;
import com.fbposter.ai.GrokClient;
import com.fbposter.ai.GroqClient;
import com.fbposter.ai.OpenRouterClient;
import com.fbposter.ai.PromptSettings;
import com.fbposter.facebook.FacebookPoster;
import com.fbposter.facebook.FacebookTokenHelper;
import com.fbposter.history.PostedHistory;
import com.fbposter.model.Article;
import com.fbposter.rss.RssReader;
import com.fbposter.telegram.TelegramNotifier;

import java.util.Collections;
import java.util.List;

/**
 * Điểm khởi chạy: RSS -> lọc bài mới -> AI viết lại -> đăng Facebook -> lưu lịch sử.
 * <p>
 * Dùng Strategy Pattern để chọn AI provider qua config {@code ai.provider}:
 * <ul>
 *   <li>{@code groq} — Groq (miễn phí 30 req/phút, model mặc định llama-3.1-8b-instant)</li>
 *   <li>{@code deepseek} — DeepSeek (có free tier, model mặc định deepseek-chat)</li>
 *   <li>{@code gemini} — Google Gemini (cần billing, model mặc định gemini-2.0-flash)</li>
 * </ul>
 * <p>
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

        // ---- Khởi tạo AI Client (Strategy Pattern) ----
        AiClient ai = createAiClient(config);
        if (ai == null) {
            return; // createAiClient đã in thông báo lỗi
        }

        // Kiểm tra cấu hình Facebook
        if (!dryRun && (isBlank(config.facebookPageId()) || isBlank(config.facebookToken()))) {
            System.out.println("[LỖI] Thiếu facebook.page.id hoặc access.token. Hãy điền vào config.properties.");
            return;
        }
        if (config.rssSources().isEmpty()) {
            System.out.println("[LỖI] Chưa cấu hình sources.rss.");
            return;
        }

        RssReader rss = new RssReader(true); // fetch bài đầy đủ + ảnh từ link
        PromptSettings promptSettings = new PromptSettings(
                config.pageTopic(),
                config.pageTone(),
                config.postMinSentences(),
                config.postMaxSentences(),
                config.postUseEmoji(),
                config.postHashtagCount(),
                config.postExtraInstruction());
        FacebookPoster facebook = new FacebookPoster(
                config.facebookPageId(), config.facebookToken(), config.facebookApiVersion());
        PostedHistory history = new PostedHistory("posted-history.json");
        TelegramNotifier telegram = new TelegramNotifier(
                config.telegramBotToken(), config.telegramChatId());

        // 1. Đọc RSS
        List<Article> articles = rss.readAll(config.rssSources());
        System.out.println("[App] Tổng số bài lấy được: " + articles.size());

        // Random thứ tự để đa dạng nội dung (tránh luôn theo 12 con giáp)
        Collections.shuffle(articles);

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

            // 3. AI viết lại (qua Strategy interface)
            String rewritten = ai.rewrite(article);
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
                    if (isLowQualityImage(article.getImageUrl())) {
                        System.out.println("[Ảnh nhỏ - sẽ dùng link share thay vì upload] " + article.getImageUrl());
                    } else {
                        System.out.println("[Ảnh] " + article.getImageUrl());
                    }
                }
                if (includeLink) {
                    System.out.println("Nguồn: " + article.getLink());
                }
                System.out.println("---------------------------");
                posted++;
                continue;
            }

            String postId;
            // Nếu ảnh nhỏ/chất lượng kém (icon zodiac), đăng dạng link share
            // để Facebook tự lấy og:image chất lượng cao từ trang nguồn
            if (article.hasImage() && !isLowQualityImage(article.getImageUrl())) {
                String caption = includeLink
                        ? rewritten + "\n\nNguồn: " + article.getLink()
                        : rewritten;
                postId = facebook.postPhoto(caption, article.getImageUrl());
            } else {
                // Link share: Facebook tự scrape og:image từ trang nguồn (chất lượng cao hơn)
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

    // ---- Factory method: Strategy Pattern ----

    /**
     * Tạo {@link AiClient} dựa trên {@code ai.provider} trong config.
     * Trả về {@code null} nếu thiếu cấu hình.
     */
    private static AiClient createAiClient(Config config) {
        String provider = config.aiProvider().toLowerCase();
        PromptSettings promptSettings = new PromptSettings(
                config.pageTopic(),
                config.pageTone(),
                config.postMinSentences(),
                config.postMaxSentences(),
                config.postUseEmoji(),
                config.postHashtagCount(),
                config.postExtraInstruction());

        return switch (provider) {
            case "openrouter" -> {
                if (isBlank(config.openrouterApiKey())) {
                    System.out.println("[LỖI] Chọn ai.provider=openrouter nhưng thiếu openrouter.api.key.");
                    yield null;
                }
                System.out.println("[App] Dùng AI: OpenRouter (model: " + config.openrouterModel() + ")");
                yield new OpenRouterClient(config.openrouterApiKey(), config.openrouterModel(), promptSettings);
            }
            case "grok" -> {
                if (isBlank(config.grokApiKey())) {
                    System.out.println("[LỖI] Chọn ai.provider=grok nhưng thiếu grok.api.key.");
                    yield null;
                }
                System.out.println("[App] Dùng AI: Grok / xAI (model: " + config.grokModel() + ")");
                yield new GrokClient(config.grokApiKey(), config.grokModel(), promptSettings);
            }
            case "groq" -> {
                if (isBlank(config.groqApiKey())) {
                    System.out.println("[LỖI] Chọn ai.provider=groq nhưng thiếu groq.api.key.");
                    yield null;
                }
                System.out.println("[App] Dùng AI: Groq (model: " + config.groqModel() + ")");
                yield new GroqClient(config.groqApiKey(), config.groqModel(), promptSettings);
            }
            case "deepseek" -> {
                if (isBlank(config.deepseekApiKey())) {
                    System.out.println("[LỖI] Chọn ai.provider=deepseek nhưng thiếu deepseek.api.key.");
                    yield null;
                }
                System.out.println("[App] Dùng AI: DeepSeek (model: " + config.deepseekModel() + ")");
                yield new DeepSeekClient(config.deepseekApiKey(), config.deepseekModel(), promptSettings);
            }
            case "gemini" -> {
                if (isBlank(config.geminiApiKey())) {
                    System.out.println("[LỖI] Chọn ai.provider=gemini nhưng thiếu gemini.api.key.");
                    yield null;
                }
                System.out.println("[App] Dùng AI: Gemini (model: " + config.geminiModel() + ")");
                yield new GeminiClient(config.geminiApiKey(), config.geminiModel(), promptSettings);
            }
            default -> {
                System.out.println("[LỖI] ai.provider='" + provider + "' không hợp lệ. "
                        + "Chọn: openrouter | groq | deepseek | gemini");
                yield null;
            }
        };
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Ảnh icon zodiac nhỏ (~100px) hoặc ảnh chất lượng thấp -> nên dùng link share */
    private static boolean isLowQualityImage(String imageUrl) {
        if (imageUrl == null) return true;
        return imageUrl.contains("12congiap")   // icon zodiac nhỏ ~100x100
                || imageUrl.contains("/icon/")
                || imageUrl.contains("/icons/")
                || imageUrl.contains("avatar")
                || imageUrl.contains("loading");
    }
}
