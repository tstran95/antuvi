package com.fbposter.ai;

public class GrokClient extends OpenAiCompatibleClient {

    public GrokClient(String apiKey, String model, PromptSettings settings) {
        super("Grok",
              "https://api.x.ai/v1/chat/completions",
              apiKey, model, settings);
    }

    @Override
    protected void diagnose(int status, String body) {
        if (body == null) body = "";

        if (status == 401) {
            System.out.println("[Grok] => API key không hợp lệ.");
            System.out.println("   Vào https://console.x.ai để kiểm tra/tạo key mới.");
        } else if (status == 402) {
            System.out.println("[Grok] => Hết credit. Vào https://console.x.ai để nạp thêm.");
        } else if (status == 429) {
            System.out.println("[Grok] => Rate limit. Đợi vài giây rồi thử lại.");
        } else {
            System.out.println("[Grok] => Lỗi không xác định (HTTP " + status + ").");
        }
    }
}
