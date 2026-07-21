package com.fbposter.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fbposter.model.Article;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Gọi Google Gemini API (miễn phí) để viết lại bài thành bài đăng Facebook.
 */
public class GeminiClient {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final String model;
    private final PromptSettings settings;

    public GeminiClient(String apiKey, String model, PromptSettings settings) {
        this.apiKey = apiKey;
        this.model = model;
        this.settings = settings;
    }

    /**
     * Viết lại 1 bài viết thành nội dung post tiếng Việt (kèm hashtag).
     * Trả về null nếu lỗi.
     */
    public String rewrite(Article article) {
        String prompt = buildPrompt(article);
        try {
            String body = buildRequestBody(prompt);
            String url = String.format(ENDPOINT, model, apiKey);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                System.out.println("[Gemini] Lỗi HTTP " + resp.statusCode() + ": " + resp.body());
                return null;
            }
            return extractText(resp.body());
        } catch (Exception e) {
            System.out.println("[Gemini] Lỗi gọi API: " + e.getMessage());
            return null;
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

    private String buildRequestBody(String prompt) throws Exception {
        ObjectNode root = mapper.createObjectNode();

        ArrayNode contents = root.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", prompt);

        // Cấu hình sinh nội dung
        ObjectNode genConfig = root.putObject("generationConfig");
        genConfig.put("temperature", 0.8);
        genConfig.put("maxOutputTokens", 800);

        return mapper.writeValueAsString(root);
    }

    private String extractText(String jsonResponse) throws Exception {
        JsonNode root = mapper.readTree(jsonResponse);
        JsonNode textNode = root
                .path("candidates").path(0)
                .path("content").path("parts").path(0)
                .path("text");
        if (textNode.isMissingNode()) {
            System.out.println("[Gemini] Không tìm thấy text trong response: " + jsonResponse);
            return null;
        }
        return textNode.asText().trim();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
