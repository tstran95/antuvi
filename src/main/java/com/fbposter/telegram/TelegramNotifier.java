package com.fbposter.telegram;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Gửi thông báo qua Telegram Bot API.
 * Nếu chưa cấu hình bot token / chat id thì tự động bỏ qua (không lỗi).
 */
public class TelegramNotifier {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private final String botToken;
    private final String chatId;
    private final boolean enabled;

    public TelegramNotifier(String botToken, String chatId) {
        this.botToken = botToken;
        this.chatId = chatId;
        this.enabled = botToken != null && !botToken.isBlank()
                && chatId != null && !chatId.isBlank();
        if (!enabled) {
            System.out.println("[Telegram] Chưa cấu hình bot token / chat id -> bỏ qua thông báo.");
        }
    }

    /**
     * Gửi 1 tin nhắn. Không ném lỗi ra ngoài để không làm hỏng luồng đăng bài.
     */
    public void send(String message) {
        if (!enabled) return;
        try {
            String url = String.format("https://api.telegram.org/bot%s/sendMessage", botToken);
            String body = "chat_id=" + enc(chatId)
                    + "&text=" + enc(message)
                    + "&disable_web_page_preview=true";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                System.out.println("[Telegram] Lỗi gửi HTTP " + resp.statusCode() + ": " + resp.body());
            }
        } catch (Exception e) {
            System.out.println("[Telegram] Lỗi gửi thông báo: " + e.getMessage());
        }
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
