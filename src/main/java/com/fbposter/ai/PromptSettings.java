package com.fbposter.ai;

/**
 * Các tùy chọn giọng văn cho AI, lấy từ config.properties.
 */
public record PromptSettings(
        String topic,
        String tone,
        int minSentences,
        int maxSentences,
        boolean useEmoji,
        int hashtagCount,
        String extraInstruction
) {}
