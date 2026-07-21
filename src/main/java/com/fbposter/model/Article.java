package com.fbposter.model;

/**
 * Một bài viết lấy từ nguồn RSS.
 */
public class Article {
    private final String title;
    private final String link;
    private final String content;  // đã làm sạch HTML
    private final String imageUrl; // ảnh đại diện (có thể null)

    public Article(String title, String link, String content, String imageUrl) {
        this.title = title;
        this.link = link;
        this.content = content;
        this.imageUrl = imageUrl;
    }

    public String getTitle()    { return title; }
    public String getLink()     { return link; }
    public String getContent()  { return content; }
    public String getImageUrl() { return imageUrl; }

    public boolean hasImage() {
        return imageUrl != null && !imageUrl.isBlank();
    }

    @Override
    public String toString() {
        return "Article{title='" + title + "', link='" + link + "', image=" + imageUrl + "}";
    }
}
