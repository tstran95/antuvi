package com.fbposter.ai;

public class DeepSeekClient extends OpenAiCompatibleClient {

    public DeepSeekClient(String apiKey, String model, PromptSettings settings) {
        super("DeepSeek",
              "https://api.deepseek.com/v1/chat/completions",
              apiKey, model, settings);
    }

    @Override
    protected void diagnose(int status, String body) {
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
}
