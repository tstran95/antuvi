package com.fbposter.ai;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public class OpenRouterClient extends OpenAiCompatibleClient {

    public OpenRouterClient(String apiKey, String model, PromptSettings settings) {
        super("OpenRouter",
              "https://openrouter.ai/api/v1/chat/completions",
              apiKey, model, settings,
              Map.of("HTTP-Referer", "https://github.com/fb-auto-poster",
                     "X-Title", "FB Auto Poster"),
              0.8, 800, 90);
    }

    @Override
    protected String extractText(String jsonResponse) throws Exception {
        JsonNode root = mapper.readTree(jsonResponse);

        JsonNode choices = root.path("choices");
        if (choices.isEmpty()) {
            JsonNode error = root.path("error").path("message");
            System.out.println("[OpenRouter] Upstream lỗi: " + (error.isMissingNode() ? jsonResponse : error.asText()));
            return null;
        }
        JsonNode textNode = choices.path(0).path("message").path("content");
        if (textNode.isMissingNode()) {
            System.out.println("[OpenRouter] Không tìm thấy text trong response: " + jsonResponse);
            return null;
        }
        return textNode.asText().trim();
    }

    @Override
    protected void diagnose(int status, String body) {
        if (body == null) body = "";

        if (status == 401 || status == 403) {
            System.out.println("[OpenRouter] => API key không hợp lệ.");
            System.out.println("   Vào https://openrouter.ai/keys để kiểm tra/tạo key mới.");
        } else if (status == 402) {
            System.out.println("[OpenRouter] => Hết credit. Vào https://openrouter.ai/credits để nạp.");
        } else if (status == 429) {
            System.out.println("[OpenRouter] => Rate limit. Đợi vài giây rồi thử lại.");
        } else {
            System.out.println("[OpenRouter] => Lỗi không xác định (HTTP " + status + ").");
        }
    }
}
