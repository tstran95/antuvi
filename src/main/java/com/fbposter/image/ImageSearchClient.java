package com.fbposter.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Tìm ảnh chất lượng cao từ Pexels API dựa trên tiêu đề bài viết.
 * Free: 200 req/tháng. Ảnh miễn phí bản quyền, không cần ghi nguồn.
 */
public class ImageSearchClient {

    private static final String ENDPOINT = "https://api.pexels.com/v1/search";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;

    private static final Map<String, String> KEYWORD_MAP = Map.ofEntries(
            Map.entry("tử vi", "horoscope astrology"),
            Map.entry("phong thủy", "feng shui"),
            Map.entry("con giáp", "zodiac"),
            Map.entry("vận mệnh", "destiny fortune"),
            Map.entry("tâm linh", "spiritual meditation"),
            Map.entry("may mắn", "lucky fortune"),
            Map.entry("tài lộc", "wealth prosperity"),
            Map.entry("tình duyên", "love romance"),
            Map.entry("sức khỏe", "health wellness"),
            Map.entry("công danh", "career success"),
            Map.entry("sự nghiệp", "career business"),
            Map.entry("gia đạo", "family harmony"),
            Map.entry("tuổi tý", "mouse zodiac"),
            Map.entry("tuổi sửu", "ox zodiac"),
            Map.entry("tuổi dần", "tiger zodiac"),
            Map.entry("tuổi mão", "cat rabbit zodiac"),
            Map.entry("tuổi thìn", "dragon zodiac"),
            Map.entry("tuổi tỵ", "snake zodiac"),
            Map.entry("tuổi ngọ", "horse zodiac"),
            Map.entry("tuổi mùi", "goat zodiac"),
            Map.entry("tuổi thân", "monkey zodiac"),
            Map.entry("tuổi dậu", "rooster zodiac"),
            Map.entry("tuổi tuất", "dog zodiac"),
            Map.entry("tuổi hợi", "pig zodiac")
    );

    public ImageSearchClient(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Tìm ảnh phù hợp với tiêu đề bài viết. Trả về URL ảnh chất lượng cao, hoặc null.
     */
    public String search(String articleTitle) {
        if (!isConfigured() || articleTitle == null || articleTitle.isBlank()) return null;

        String query = buildQuery(articleTitle);
        try {
            String url = ENDPOINT
                    + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&per_page=5&orientation=landscape&size=large";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", apiKey)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                System.out.println("[Pexels] Lỗi HTTP " + resp.statusCode());
                return null;
            }

            return extractBestImage(resp.body());
        } catch (Exception e) {
            System.out.println("[Pexels] Lỗi tìm ảnh: " + e.getMessage());
            return null;
        }
    }

    private String buildQuery(String title) {
        String lower = title.toLowerCase();

        for (var entry : KEYWORD_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return "spiritual nature zen";
    }

    private String extractBestImage(String jsonResponse) throws Exception {
        JsonNode root = mapper.readTree(jsonResponse);
        JsonNode photos = root.path("photos");
        if (!photos.isArray() || photos.isEmpty()) return null;

        // Random 1 trong top 5 kết quả để đa dạng ảnh
        int idx = (int) (Math.random() * Math.min(photos.size(), 5));
        JsonNode photo = photos.get(idx);

        // Ưu tiên large2x (1880px) -> large (940px) -> original
        JsonNode src = photo.path("src");
        String result = src.path("large2x").asText(null);
        if (result == null || result.isBlank()) result = src.path("large").asText(null);
        if (result == null || result.isBlank()) result = src.path("original").asText(null);

        if (result != null && !result.isBlank()) {
            System.out.println("[Pexels] Tìm được ảnh: " + result.substring(0, Math.min(80, result.length())) + "...");
        }
        return result;
    }
}
