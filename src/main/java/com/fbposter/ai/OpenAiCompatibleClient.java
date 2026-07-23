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
import java.util.Map;
import java.util.Random;

public class OpenAiCompatibleClient implements AiClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    protected final ObjectMapper mapper = new ObjectMapper();
    protected final String apiKey;
    protected final String model;
    protected final PromptSettings settings;

    private final String providerName;
    private final String endpoint;
    private final Map<String, String> extraHeaders;
    private final double temperature;
    private final int maxTokens;
    private final int timeoutSeconds;
    private final Random random = new Random();

    private static final String[] POST_STYLES = {
            "Viết như đang tâm sự thân mật với một người bạn.",
            "Viết theo phong cách phân tích chuyên sâu, có luận điểm rõ ràng.",
            "Viết với giọng hài hước, dí dỏm, gần gũi.",
            "Viết như một câu chuyện kể đầy cảm hứng.",
            "Viết ngắn gọn, đi thẳng vào vấn đề như tin nóng.",
            "Viết với giọng điệu bí ẩn, huyền bí, lôi cuốn.",
            "Viết như một lời khuyên chân thành từ chuyên gia.",
    };

    public OpenAiCompatibleClient(String providerName,
                                  String endpoint,
                                  String apiKey,
                                  String model,
                                  PromptSettings settings,
                                  Map<String, String> extraHeaders,
                                  double temperature,
                                  int maxTokens,
                                  int timeoutSeconds) {
        this.providerName = providerName;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.settings = settings;
        this.extraHeaders = extraHeaders;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeoutSeconds = timeoutSeconds;
    }

    public OpenAiCompatibleClient(String providerName,
                                  String endpoint,
                                  String apiKey,
                                  String model,
                                  PromptSettings settings) {
        this(providerName, endpoint, apiKey, model, settings, Map.of(), 0.8, 800, 60);
    }

    @Override
    public String rewrite(Article article) {
        String prompt = buildPrompt(article);
        try {
            String body = buildRequestBody(prompt);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            extraHeaders.forEach(reqBuilder::header);

            HttpResponse<String> resp = http.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                System.out.println("[" + providerName + "] Lỗi HTTP " + resp.statusCode() + ": " + resp.body());
                diagnose(resp.statusCode(), resp.body());
                return null;
            }
            return extractText(resp.body());
        } catch (Exception e) {
            System.out.println("[" + providerName + "] Lỗi gọi API: " + e.getMessage());
            return null;
        }
    }

    protected String buildSystemMessage() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        return "Bạn là chuyên gia tử vi phong thủy, viết bài Facebook tiếng Việt chuyên sâu. "
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
                + "CHỈ trả về bài đăng hoàn chỉnh.";
    }

    protected String buildRequestBody(String prompt) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);

        ArrayNode messages = root.putArray("messages");
        ObjectNode sysMsg = messages.addObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", buildSystemMessage());

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);

        root.put("temperature", temperature);
        root.put("max_tokens", maxTokens);

        return mapper.writeValueAsString(root);
    }

    protected String extractText(String jsonResponse) throws Exception {
        JsonNode root = mapper.readTree(jsonResponse);
        JsonNode textNode = root
                .path("choices").path(0)
                .path("message").path("content");
        if (textNode.isMissingNode()) {
            System.out.println("[" + providerName + "] Không tìm thấy text trong response: " + jsonResponse);
            return null;
        }
        String raw = textNode.asText().trim();

        if (raw.contains("<think>")) {
            int endTag = raw.indexOf("</think>");
            if (endTag > 0) {
                raw = raw.substring(endTag + "</think>".length()).trim();
            }
        }

        return raw;
    }

    protected void diagnose(int status, String body) {
        if (status == 401) {
            System.out.println("[" + providerName + "] => API key không hợp lệ.");
        } else if (status == 402) {
            System.out.println("[" + providerName + "] => Hết credit.");
        } else if (status == 429) {
            System.out.println("[" + providerName + "] => Rate limit. Đợi vài giây rồi thử lại.");
        } else {
            System.out.println("[" + providerName + "] => Lỗi không xác định (HTTP " + status + ").");
        }
    }

    protected String buildPrompt(Article article) {
        String emojiRule = settings.useEmoji()
                ? "- Dùng emoji hợp lý để bài sinh động."
                : "- KHÔNG dùng emoji.";
        String hashtagRule = settings.hashtagCount() > 0
                ? "- Kết thúc bằng " + settings.hashtagCount() + " hashtag liên quan."
                : "- KHÔNG thêm hashtag.";
        String extra = (settings.extraInstruction() == null || settings.extraInstruction().isBlank())
                ? ""
                : "- " + settings.extraInstruction().trim();

        String style = POST_STYLES[random.nextInt(POST_STYLES.length)];
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

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

    protected String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
