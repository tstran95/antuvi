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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Gọi Groq API (OpenAI-compatible, có free tier thật) để viết lại bài thành bài đăng Facebook.
 * <p>
 * Endpoint: {@code https://api.groq.com/openai/v1/chat/completions}
 * <br>
 * Đăng ký lấy key tại: <a href="https://console.groq.com/keys">console.groq.com/keys</a>
 * <br>
 * Free tier: 30 req/phút, 14,400 req/ngày — rất thoải mái cho nhu cầu đăng bài tự động.
 */
public class GroqClient implements AiClient {

    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final String model;
    private final PromptSettings settings;

    public GroqClient(String apiKey, String model, PromptSettings settings) {
        this.apiKey = apiKey;
        this.model = model;
        this.settings = settings;
    }

    @Override
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
                System.out.println("[Groq] Lỗi HTTP " + resp.statusCode() + ": " + resp.body());
                diagnose(resp.statusCode(), resp.body());
                return null;
            }
            return extractText(resp.body());
        } catch (Exception e) {
            System.out.println("[Groq] Lỗi gọi API: " + e.getMessage());
            return null;
        }
    }

    private void diagnose(int status, String body) {
        if (body == null) body = "";

        if (status == 401) {
            System.out.println("[Groq] => API key không hợp lệ.");
            System.out.println("   Vào https://console.groq.com/keys để kiểm tra/tạo key mới.");
        } else if (status == 429) {
            System.out.println("[Groq] => Rate limit (30 req/phút free). Đợi vài giây rồi thử lại.");
        } else if (status == 503 || status == 502) {
            System.out.println("[Groq] => Server Groq đang bận. Đợi vài phút rồi thử lại.");
        } else {
            System.out.println("[Groq] => Lỗi không xác định (HTTP " + status + "). Kiểm tra key và endpoint.");
        }
    }

    private String buildRequestBody(String prompt) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);

        ArrayNode messages = root.putArray("messages");
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        ObjectNode sysMsg = messages.addObject();
        sysMsg.put("role", "system");
        sysMsg.put("content",
                "Bạn là chuyên gia tử vi phong thủy, viết bài Facebook tiếng Việt chuyên sâu. "
                        + "HÔM NAY LÀ " + today + ". "
                        + "Nếu bài gốc có ngày KHÁC hôm nay, hãy TÍNH KHOẢNG CÁCH "
                        + "(vd: 'còn 3 ngày nữa, vào 25/07...' hoặc 'hôm qua, 21/07...'). "
                        + "Tuyệt đối KHÔNG ĐƯỢC dùng năm 2025 hay các năm cũ. "
                        + "CHỈ dùng số liệu/chỉ số CÓ THẬT từ bài gốc, "
                        + "KHÔNG tự bịa ra số lẻ vô nghĩa. "
                        + "Dựa vào chủ đề bài gốc, VIẾT BÀI HOÀN CHỈNH với phân tích chi tiết, "
                        + "có mở bài - thân bài - kết bài. "
                        + "Được phép dùng kiến thức tử vi phong thủy để bổ sung. "
                        + "Viết tự nhiên, hấp dẫn, dài 8-12 câu. "
                        + "CHỈ trả về bài đăng hoàn chỉnh.");

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);

        root.put("temperature", 0.7);
        root.put("max_tokens", 2000);

        return mapper.writeValueAsString(root);
    }

    private String extractText(String jsonResponse) throws Exception {
        JsonNode root = mapper.readTree(jsonResponse);
        JsonNode textNode = root
                .path("choices").path(0)
                .path("message").path("content");
        if (textNode.isMissingNode()) {
            System.out.println("[Groq] Không tìm thấy text trong response: " + jsonResponse);
            return null;
        }
        String raw = textNode.asText().trim();

        // Một số model (Qwen) dùng reasoning mode -> output có <think>...</think>
        if (raw.contains("<think>")) {
            int endTag = raw.indexOf("</think>");
            if (endTag > 0) {
                raw = raw.substring(endTag + "</think>".length()).trim();
            }
        }

        return raw.trim();
    }

    private final java.util.Random random = new java.util.Random();

    private static final String[] POST_STYLES = {
            "Viết như đang tâm sự thân mật với một người bạn.",
            "Viết theo phong cách phân tích chuyên sâu, có luận điểm rõ ràng.",
            "Viết với giọng hài hước, dí dỏm, gần gũi.",
            "Viết như một câu chuyện kể đầy cảm hứng.",
            "Viết ngắn gọn, đi thẳng vào vấn đề như tin nóng.",
            "Viết với giọng điệu bí ẩn, huyền bí, lôi cuốn.",
            "Viết như một lời khuyên chân thành từ chuyên gia.",
    };

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

        // Random style để mỗi bài có giọng văn khác nhau
        String style = POST_STYLES[random.nextInt(POST_STYLES.length)];
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        return """
                Bạn là biên tập viên nội dung cho một trang Facebook tiếng Việt về chủ đề: %s.
                Hãy VIẾT LẠI bài dưới đây thành một bài đăng Facebook hấp dẫn, tự nhiên, KHÔNG sao chép nguyên văn.
                QUAN TRỌNG: Hôm nay là %s. Nếu bài gốc nói về ngày khác, hãy tính khoảng cách (vd: "3 ngày nữa, 25/07...") thay vì ghi sai ngày.

                Yêu cầu:
                - Viết bằng tiếng Việt, giọng văn %s.
                - %s
                - Độ dài khoảng %d-%d câu.
                - Mở đầu bằng một câu thu hút.
                %s
                %s
                - Được phép bổ sung kiến thức tử vi, phong thủy liên quan để bài viết sâu sắc hơn.
                - CHỈ trả về bài đăng hoàn chỉnh, không thêm lời giải thích.
                %s

                TIÊU ĐỀ GỐC: %s

                NỘI DUNG GỐC:
                %s
                """.formatted(
                        settings.topic(),
                        today,
                        settings.tone(),
                        style,
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
