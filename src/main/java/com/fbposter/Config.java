package com.fbposter;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Đọc cấu hình từ config.properties.
 * Nếu có biến môi trường (ENV) tương ứng thì ưu tiên ENV.
 * -> Nhờ vậy khi chạy trên GitHub Actions ta dùng Secrets (ENV) mà không cần file.
 */
public class Config {

    private final Properties props = new Properties();

    public Config() {
        try (InputStream in = getClass().getResourceAsStream("/config.properties")) {
            if (in != null) {
                props.load(in);
            } else {
                System.out.println("[Config] Không tìm thấy config.properties, dùng biến môi trường.");
            }
        } catch (Exception e) {
            System.out.println("[Config] Lỗi đọc config.properties: " + e.getMessage());
        }
    }

    /**
     * Lấy giá trị: ưu tiên ENV (tên viết HOA, thay . thành _), sau đó tới file.
     * VD: key "facebook.page.id" -> ENV "FACEBOOK_PAGE_ID"
     */
    public String get(String key) {
        String envKey = key.toUpperCase().replace('.', '_');
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isBlank()) {
            return envVal.trim();
        }
        String val = props.getProperty(key);
        return val == null ? null : val.trim();
    }

    public String get(String key, String defaultValue) {
        String v = get(key);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, String.valueOf(defaultValue)));
    }

    public List<String> getList(String key) {
        String v = get(key, "");
        return Arrays.stream(v.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }

    // ---- Các key hay dùng ----
    public String facebookPageId()      { return get("facebook.page.id"); }
    public String facebookToken()       { return get("facebook.page.access.token"); }
    public String facebookApiVersion()  { return get("facebook.api.version", "v21.0"); }
    public String geminiApiKey()        { return get("gemini.api.key"); }
    public String geminiModel()         { return get("gemini.model", "gemini-2.0-flash"); }

    // ---- DeepSeek ----
    public String deepseekApiKey()      { return get("deepseek.api.key"); }
    public String deepseekModel()       { return get("deepseek.model", "deepseek-chat"); }

    // ---- Groq ----
    public String groqApiKey()          { return get("groq.api.key"); }
    public String groqModel()           { return get("groq.model", "llama-3.1-8b-instant"); }

    // ---- OpenRouter ----
    public String openrouterApiKey()    { return get("openrouter.api.key"); }
    public String openrouterModel()     { return get("openrouter.model", "google/gemini-2.0-flash-001"); }

    // ---- Grok (xAI) ----
    public String grokApiKey()          { return get("grok.api.key"); }
    public String grokModel()           { return get("grok.model", "grok-2"); }

    // ---- AI Provider selector ----
    /** Trả về provider đang chọn: "grok" | "groq" | "openrouter" | "deepseek" | "gemini" */
    public String aiProvider()          { return get("ai.provider", "groq"); }
    public List<String> rssSources()    { return getList("sources.rss"); }
    public int maxPostsPerRun()         { return getInt("max.posts.per.run", 3); }

    // ---- Tùy chỉnh giọng văn AI ----
    public String pageTopic()           { return get("page.topic", "tin tức tổng hợp"); }
    public String pageTone()            { return get("page.tone", "thân thiện, gần gũi, dễ đọc"); }
    public int postMinSentences()       { return getInt("post.min.sentences", 3); }
    public int postMaxSentences()       { return getInt("post.max.sentences", 6); }
    public boolean postUseEmoji()       { return getBoolean("post.use.emoji", true); }
    public int postHashtagCount()       { return getInt("post.hashtag.count", 5); }
    public boolean postIncludeSourceLink() { return getBoolean("post.include.source.link", true); }
    public String postExtraInstruction(){ return get("post.extra.instruction", ""); }

    // ---- Facebook token helper (dùng cho --get-page-token) ----
    public String facebookAppId()       { return get("facebook.app.id"); }
    public String facebookAppSecret()   { return get("facebook.app.secret"); }
    public String facebookUserToken()   { return get("facebook.user.access.token"); }

    // ---- Pexels (ảnh stock miễn phí) ----
    public String pexelsApiKey()        { return get("pexels.api.key"); }

    // ---- Telegram ----
    public String telegramBotToken()    { return get("telegram.bot.token"); }
    public String telegramChatId()      { return get("telegram.chat.id"); }
}
