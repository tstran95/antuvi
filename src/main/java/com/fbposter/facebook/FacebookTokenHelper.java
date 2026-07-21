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
 * Công cụ lấy Page Access Token KHÔNG hết hạn.
 *
 * Nguyên lý (theo Facebook):
 *  1) Đổi user token ngắn hạn -> user token dài hạn (~60 ngày) bằng app id + app secret.
 *  2) Gọi /me/accounts bằng user token dài hạn -> token của từng Page.
 *     Page token sinh từ user token dài hạn thường KHÔNG hết hạn.
 */
public class FacebookTokenHelper {

    private static final String GRAPH = "https://graph.facebook.com/v21.0";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Chạy toàn bộ quy trình và in ra Page token vĩnh viễn.
     */
    public void run(String appId, String appSecret, String shortUserToken) {
        if (isBlank(appId) || isBlank(appSecret) || isBlank(shortUserToken)) {
            System.out.println("[Token] Thiếu thông tin. Cần điền vào config.properties:");
            System.out.println("        facebook.app.id, facebook.app.secret, facebook.user.access.token");
            System.out.println("        (Xem HUONG_DAN.md mục 6)");
            return;
        }

        try {
            // Bước 1: đổi sang user token dài hạn
            System.out.println("[Token] Đang đổi sang user token dài hạn...");
            String longUserToken = exchangeLongLivedUserToken(appId, appSecret, shortUserToken);
            if (longUserToken == null) {
                System.out.println("[Token] Không lấy được user token dài hạn. Kiểm tra lại app id/secret/token.");
                return;
            }
            System.out.println("[Token] OK. Đang lấy danh sách Page...");

            // Bước 2: lấy page token
            printPageTokens(longUserToken);
        } catch (Exception e) {
            System.out.println("[Token] Lỗi: " + e.getMessage());
        }
    }

    private String exchangeLongLivedUserToken(String appId, String appSecret, String shortToken) throws Exception {
        String url = GRAPH + "/oauth/access_token"
                + "?grant_type=fb_exchange_token"
                + "&client_id=" + enc(appId)
                + "&client_secret=" + enc(appSecret)
                + "&fb_exchange_token=" + enc(shortToken);

        JsonNode root = getJson(url);
        if (root == null) return null;
        String token = root.path("access_token").asText(null);
        return token;
    }

    private void printPageTokens(String longUserToken) throws Exception {
        String url = GRAPH + "/me/accounts?fields=name,id,access_token&access_token=" + enc(longUserToken);
        JsonNode root = getJson(url);
        if (root == null) return;

        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) {
            System.out.println("[Token] Không tìm thấy Page nào. Đảm bảo user token có quyền pages_show_list.");
            return;
        }

        System.out.println("\n========== PAGE TOKEN (dùng token này, thường KHÔNG hết hạn) ==========");
        for (JsonNode page : data) {
            System.out.println("Tên Page : " + page.path("name").asText());
            System.out.println("Page ID  : " + page.path("id").asText());
            System.out.println("Token    : " + page.path("access_token").asText());
            System.out.println("---------------------------------------------------------------------");
        }
        System.out.println("=> Copy 'Page ID' vào facebook.page.id và 'Token' vào facebook.page.access.token");
        System.out.println("=====================================================================\n");
    }

    private JsonNode getJson(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            System.out.println("[Token] Lỗi HTTP " + resp.statusCode() + ": " + resp.body());
            return null;
        }
        return mapper.readTree(resp.body());
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
