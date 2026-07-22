package com.fbposter.ai;

import com.fbposter.model.Article;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;

/**
 * Gọi Google Gemini API (miễn phí) để viết lại bài thành bài đăng Facebook.
 * <p>
 * Sử dụng Google GenAI Java SDK thay vì REST API thủ công, nhằm hỗ trợ
 * cả key định dạng cũ {@code AIza...} (Standard) lẫn key định dạng mới
 * {@code AQ...} (Auth Key). SDK tự xử lý khác biệt giữa 2 loại key.
 */
public class GeminiClient implements AiClient {

    private final Client client;
    private final String model;
    private final String apiKey;
    private final PromptSettings settings;

    public GeminiClient(String apiKey, String model, PromptSettings settings) {
        this.apiKey = apiKey;
        this.model = model;
        this.settings = settings;

        // Key "AQ." là OAuth credential, không dùng được như API key.
        // Khi gặp key AQ (hoặc không có key), thử dùng Application Default Credentials.
        boolean isAqKey = apiKey != null && apiKey.startsWith("AQ.");
        boolean hasAdc = System.getenv("GOOGLE_APPLICATION_CREDENTIALS") != null;

        if (isAqKey && hasAdc) {
            System.out.println("[Gemini] Key \"AQ.\" không dùng làm API key được. "
                    + "Dùng service account từ GOOGLE_APPLICATION_CREDENTIALS.");
            this.client = new Client();
        } else if (isAqKey) {
            // Vẫn thử tạo client với API key để SDK báo lỗi rõ ràng,
            // diagnose() sẽ hướng dẫn người dùng cách khắc phục.
            System.out.println("[Gemini] Cảnh báo: Key \"AQ.\" thường không hoạt động như API key.");
            System.out.println("[Gemini] => Xem hướng dẫn trong HUONG_DAN.md hoặc lỗi bên dưới.");
            this.client = Client.builder().apiKey(apiKey).build();
        } else {
            this.client = Client.builder().apiKey(apiKey).build();
        }
    }

    /**
     * Viết lại 1 bài viết thành nội dung post tiếng Việt (kèm hashtag).
     * Trả về {@code null} nếu lỗi.
     */
    public String rewrite(Article article) {
        String prompt = buildPrompt(article);
        try {
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .temperature(0.8f)
                    .maxOutputTokens(800)
                    .build();

            GenerateContentResponse response = client.models.generateContent(model, prompt, config);
            String text = response.text();
            if (text == null || text.isBlank()) {
                System.out.println("[Gemini] Phản hồi rỗng từ model.");
                return null;
            }
            return text.trim();
        } catch (Exception e) {
            String errMsg = e.getMessage();
            System.out.println("[Gemini] Lỗi gọi API: " + errMsg);
            diagnose(errMsg != null ? errMsg : "");
            return null;
        }
    }

    /**
     * In gợi ý xử lý dựa trên nội dung lỗi, giúp phân biệt
     * "key sai định dạng" với "tài khoản bị Google chặn" với
     * "chưa bật Generative Language API".
     */
    private void diagnose(String msg) {
        String lower = msg.toLowerCase();
        boolean aqKey = apiKey != null && apiKey.startsWith("AQ.");

        // Lỗi 403 / quyền — thường gặp nhất với AQ key
        if (msg.contains("403") || msg.contains("Forbidden")
                || lower.contains("permission_denied")
                || lower.contains("api_key_service_blocked")
                || lower.contains("access_token_type_unsupported")) {

            if (aqKey) {
                System.out.println("[Gemini] ============================================================");
                System.out.println("[Gemini] => Key \"AQ.\" là OAuth credential, KHÔNG phải API key.");
                System.out.println("[Gemini]    AI Studio hiện tại CHỈ tạo key dạng này — không dùng được");
                System.out.println("[Gemini]    với Gemini REST API qua cơ chế API key (x-goog-api-key).");
                System.out.println("[Gemini]");
                System.out.println("[Gemini] => Cách khắc phục (làm 1 trong 2 cách):");
                System.out.println("[Gemini]");
                System.out.println("[Gemini]    CÁCH A — Lấy key API \"AIza...\" từ GOOGLE CLOUD CONSOLE:");
                System.out.println("[Gemini]     1. Vào https://console.cloud.google.com/apis/credentials");
                System.out.println("[Gemini]     2. Create Credentials -> API key");
                System.out.println("[Gemini]     3. Copy key (dạng AIza...) thay vào config.properties");
                System.out.println("[Gemini]     4. (Tùy chọn) Restrict key -> chọn Generative Language API");
                System.out.println("[Gemini]");
                System.out.println("[Gemini]    CÁCH B — Dùng OAuth / Service Account (dài hạn hơn):");
                System.out.println("[Gemini]     1. Vào Cloud Console -> IAM & Admin -> Service Accounts");
                System.out.println("[Gemini]     2. Tạo service account -> tạo JSON key -> tải về");
                System.out.println("[Gemini]     3. Đặt env: GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json");
                System.out.println("[Gemini]     4. Xóa dòng gemini.api.key trong config.properties");
                System.out.println("[Gemini]        (SDK sẽ tự dùng service account để lấy OAuth token)");
                System.out.println("[Gemini] ============================================================");
            } else {
                System.out.println("[Gemini] => Key không có quyền. Kiểm tra:");
                System.out.println("   1. Generative Language API đã được enable?");
                System.out.println("   2. Key có bị restrict IP/API không?");
            }

        // Lỗi 401 — key sai hoặc hết hạn
        } else if (msg.contains("401") || lower.contains("unauthorized")
                || lower.contains("unauthenticated")
                || lower.contains("api key not valid")) {
            System.out.println("[Gemini] => Key không hợp lệ hoặc đã hết hạn.");
            System.out.println("   Kiểm tra lại gemini.api.key trong config.properties.");
            System.out.println("   Vào https://aistudio.google.com/app/apikey để tạo key mới nếu cần.");

        // Lỗi 404 — sai tên model
        } else if (msg.contains("404") || lower.contains("not found")
                || lower.contains("not_found")) {
            System.out.println("[Gemini] => Không tìm thấy model \"" + model + "\".");
            System.out.println("   Kiểm tra gemini.model trong config.properties.");
            System.out.println("   Các model khả dụng: gemini-2.5-flash, gemini-2.5-pro, gemini-2.0-flash...");

        // Lỗi quota / giới hạn
        } else if (lower.contains("quota") || lower.contains("limit")
                || lower.contains("resource_exhausted") || lower.contains("429")) {
            System.out.println("[Gemini] => Đã vượt quota hoặc rate limit. Chờ vài phút rồi thử lại.");
            System.out.println("   Free tier: ~1,500 requests/ngày với gemini-2.0-flash.");

        // Lỗi timeout / mạng
        } else if (lower.contains("timeout") || lower.contains("timed out")
                || lower.contains("connect") || lower.contains("unreachable")) {
            System.out.println("[Gemini] => Lỗi kết nối mạng hoặc timeout. Kiểm tra internet, thử lại sau.");

        // Lỗi billing
        } else if (lower.contains("billing")) {
            System.out.println("[Gemini] => Cần bật billing cho Google Cloud project.");
            System.out.println("   Vào https://console.cloud.google.com/billing → liên kết billing account.");
            System.out.println("   Free tier Gemini vẫn MIỄN PHÍ, billing chỉ để xác minh danh tính.");

        // Lỗi khác
        } else {
            System.out.println("[Gemini] => Lỗi không xác định. Kiểm tra key, model, và kết nối mạng.");
        }
    }

    private String buildPrompt(Article article) {
        String emojiRule = settings.useEmoji()
                ? "- Dùng emoji hợp lý để bài sinh động."
                : "- KHÔNG dùng emoji.";
        String hashtagRule = settings.hashtagCount() > 0
                ? "- Kết thúc bằng " + settings.hashtagCount() + " hashtag liên quan."
                : "- KHÔNG thêm hashtag.";
        String extra = (settings.extraInstruction() == null || settings.extraInstruction().isBlank())
                ? ""
                : "- " + settings.extraInstruction().trim();

        return """
                Bạn là biên tập viên nội dung cho một trang Facebook tiếng Việt về chủ đề: %s.
                Hãy VIẾT LẠI bài dưới đây thành một bài đăng Facebook hấp dẫn, tự nhiên, KHÔNG sao chép nguyên văn.

                Yêu cầu:
                - Viết bằng tiếng Việt, giọng văn %s.
                - Độ dài khoảng %d-%d câu.
                - Mở đầu bằng một câu thu hút.
                %s
                %s
                - KHÔNG bịa thêm thông tin không có trong bài gốc.
                - CHỈ trả về nội dung bài đăng, không thêm lời giải thích.
                %s

                TIÊU ĐỀ GỐC: %s

                NỘI DUNG GỐC:
                %s
                """.formatted(
                        settings.topic(),
                        settings.tone(),
                        settings.minSentences(), settings.maxSentences(),
                        emojiRule,
                        hashtagRule,
                        extra,
                        article.getTitle(),
                        truncate(article.getContent(), 4000));
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
