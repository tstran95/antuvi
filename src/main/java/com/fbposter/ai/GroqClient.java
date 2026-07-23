package com.fbposter.ai;

import java.util.Map;

public class GroqClient extends OpenAiCompatibleClient {

    public GroqClient(String apiKey, String model, PromptSettings settings) {
        super("Groq",
              "https://api.groq.com/openai/v1/chat/completions",
              apiKey, model, settings,
              Map.of(),
              0.7, 2000, 60);
    }

    @Override
    protected void diagnose(int status, String body) {
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
}
