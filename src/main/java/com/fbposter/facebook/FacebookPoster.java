package com.fbposter.facebook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Đăng bài lên Facebook Page qua Graph API.
 * Gọi: POST /{pageId}/feed  với message (+ link tùy chọn).
 */
public class FacebookPoster {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String pageId;
    private final String accessToken;
    private final String apiVersion;

    public FacebookPoster(String pageId, String accessToken, String apiVersion) {
        this.pageId = pageId;
        this.accessToken = accessToken;
        this.apiVersion = apiVersion;
    }

    /**
     * Đăng 1 bài. Trả về post id nếu thành công, null nếu lỗi.
     * @param message nội dung bài đăng
     * @param link    link nguồn (có thể null) - Facebook sẽ hiện preview
     */
    public String post(String message, String link) {
        try {
            String url = String.format("https://graph.facebook.com/%s/%s/feed", apiVersion, pageId);

            StringBuilder form = new StringBuilder();
            form.append("message=").append(enc(message));
            if (link != null && !link.isBlank()) {
                form.append("&link=").append(enc(link));
            }
            form.append("&access_token=").append(enc(accessToken));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(45))
                    .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                System.out.println("[Facebook] Lỗi HTTP " + resp.statusCode() + ": " + resp.body());
                return null;
            }

            JsonNode root = mapper.readTree(resp.body());
            String postId = root.path("id").asText(null);
            System.out.println("[Facebook] Đã đăng, post id = " + postId);
            return postId;
        } catch (Exception e) {
            System.out.println("[Facebook] Lỗi đăng bài: " + e.getMessage());
            return null;
        }
    }

    /**
     * Đăng bài KÈM ẢNH lên Page: POST /{pageId}/photos với url ảnh + caption.
     * Facebook tự tải ảnh từ imageUrl. Trả về post id nếu thành công, null nếu lỗi.
     */
    public String postPhoto(String caption, String imageUrl) {
        try {
            String url = String.format("https://graph.facebook.com/%s/%s/photos", apiVersion, pageId);

            StringBuilder form = new StringBuilder();
            form.append("url=").append(enc(imageUrl));
            form.append("&caption=").append(enc(caption));
            form.append("&access_token=").append(enc(accessToken));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                System.out.println("[Facebook] Lỗi đăng ảnh HTTP " + resp.statusCode() + ": " + resp.body());
                return null;
            }

            JsonNode root = mapper.readTree(resp.body());
            // API /photos trả về "post_id" (bài trên timeline) và "id" (ảnh)
            String postId = root.path("post_id").asText(null);
            if (postId == null) postId = root.path("id").asText(null);
            System.out.println("[Facebook] Đã đăng ảnh, post id = " + postId);
            return postId;
        } catch (Exception e) {
            System.out.println("[Facebook] Lỗi đăng ảnh: " + e.getMessage());
            return null;
        }
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
