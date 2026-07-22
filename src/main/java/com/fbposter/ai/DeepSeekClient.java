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
 * Gọi DeepSeek API (OpenAI-compatible, có free tier) để viết lại bài thành bài đăng Facebook.
 * <p>
 * Endpoint: {@code https://api.deepseek.com/v1/chat/completions}
 * <br>
 * Đăng ký lấy key tại: <a href="https://platform.deepseek.com/api_keys">platform.deepseek.com/api_keys</a>
 */
public class DeepSeekClient implements AiClient {

    private static final String ENDPOINT = "https://api.deepseek.com/v1/chat/completions";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final String model;
    private final PromptSettings settings;

    public DeepSeekClient(String apiKey, String model, PromptSettings settings) {
        this.apiKey = apiKey;
        this.model = model;
        this.settings = settings;
    }

    /**
     * Viết lại 1 bài viết thành nội dung post tiếng Việt (kèm hashtag).
     * Trả về {@code null} nếu lỗi.
     */
    public String rewrite(Article article) {
        String prompt = buildPrompt(article);
        try {
            String body = buildRequestBody(prompt);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                System.out.println("[DeepSeek] Lỗi HTTP " + resp.statusCode() + ": " + resp.body());
                diagnose(resp.statusCode(), resp.body());
                return null;
            }
            return extractText(resp.body());
        } catch (Exception e) {
            System.out.println("[DeepSeek] Lỗi gọi API: " + e.getMessage());
            return null;
        }
    }

    private void diagnose(int status, String body) {
        if (body == null) body = "";

        if (status == 401) {
            System.out.println("[DeepSeek] => API key không hợp lệ hoặc đã hết hạn.");
            System.out.println("   Vào https://platform.deepseek.com/api_keys để kiểm tra/tạo key mới.");
        } else if (status == 402 || body.contains("Insufficient Balance") || body.contains("insufficient_balance")) {
            System.out.println("[DeepSeek] => Tài khoản hết credit. Vào https://platform.deepseek.com/top_up để nạp.");
            System.out.println("   (DeepSeek tặng credit miễn phí cho người dùng mới — tạo tài khoản mới nếu cần.)");
        } else if (status == 403) {
            System.out.println("[DeepSeek] => Bị từ chối truy cập. Kiểm tra key hoặc IP bị chặn.");
        } else if (status == 429) {
            System.out.println("[DeepSeek] => Rate limit. Đợi vài giây rồi thử lại.");
        } else if (status == 503 || status == 502) {
            System.out.println("[DeepSeek] => Server DeepSeek đang bận. Đợi vài phút rồi thử lại.");
        } else {
            System.out.println("[DeepSeek] => Lỗi không xác định (HTTP " + status + "). Kiểm tra key và kết nối.");
        }
    }

    private String buildRequestBody(String prompt) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);

        ArrayNode messages = root.putArray("messages");
        ObjectNode sysMsg = messages.addObject();
        sysMsg.put("role", "system");
        sysMsg.put("content",
                "Bạn là một biên tập viên nội dung Facebook chuyên nghiệp, "
                        + "viết bài bằng tiếng Việt hấp dẫn và tự nhiên. "
                        + "CHỈ trả về nội dung bài đăng, không thêm bất kỳ lời giải thích nào.");

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);

        root.put("temperature", 0.8);
        root.put("max_tokens", 800);

        return mapper.writeValueAsString(root);
    }

    private String extractText(String jsonResponse) throws Exception {
        JsonNode root = mapper.readTree(jsonResponse);
        JsonNode textNode = root
                .path("choices").path(0)
                .path("message").path("content");
        if (textNode.isMissingNode()) {
            System.out.println("[DeepSeek] Không tìm thấy text trong response: " + jsonResponse);
            return null;
        }
        return textNode.asText().trim();
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
