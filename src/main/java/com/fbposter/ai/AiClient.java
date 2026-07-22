package com.fbposter.ai;

import com.fbposter.model.Article;

/**
 * Strategy interface xử lý viết lại bài viết thành bài đăng Facebook.
 * Mỗi AI provider (Groq, DeepSeek, Gemini) implement interface này.
 */
public interface AiClient {

    /**
     * Viết lại 1 bài viết thành nội dung post tiếng Việt (kèm hashtag).
     *
     * @param article bài viết gốc từ RSS
     * @return nội dung bài đăng Facebook đã viết lại, hoặc {@code null} nếu lỗi
     */
    String rewrite(Article article);
}
