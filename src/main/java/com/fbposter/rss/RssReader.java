package com.fbposter.rss;

import com.fbposter.model.Article;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.jsoup.Jsoup;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Đọc các nguồn RSS và trả về danh sách Article.
 */
public class RssReader {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public List<Article> readAll(List<String> feedUrls) {
        List<Article> result = new ArrayList<>();
        for (String url : feedUrls) {
            try {
                result.addAll(readFeed(url));
            } catch (Exception e) {
                System.out.println("[RSS] Lỗi đọc feed " + url + ": " + e.getMessage());
            }
        }
        return result;
    }

    private List<Article> readFeed(String feedUrl) throws Exception {
        System.out.println("[RSS] Đang đọc: " + feedUrl);

        // Tải nội dung feed qua HttpClient (kèm User-Agent để tránh bị chặn)
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(feedUrl))
                .header("User-Agent", "Mozilla/5.0 (compatible; FbAutoPoster/1.0)")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + resp.statusCode());
        }

        List<Article> articles = new ArrayList<>();
        try (XmlReader reader = new XmlReader(new java.io.ByteArrayInputStream(resp.body()))) {
            SyndFeed feed = new SyndFeedInput().build(reader);
            for (SyndEntry entry : feed.getEntries()) {
                String title = entry.getTitle() == null ? "" : entry.getTitle().trim();
                String link = entry.getLink() == null ? "" : entry.getLink().trim();

                String rawContent = "";
                if (entry.getDescription() != null && entry.getDescription().getValue() != null) {
                    rawContent = entry.getDescription().getValue();
                } else if (!entry.getContents().isEmpty()) {
                    rawContent = entry.getContents().get(0).getValue();
                }
                if (rawContent == null) rawContent = "";

                // Làm sạch HTML -> lấy text thuần
                String content = Jsoup.parse(rawContent).text().trim();

                // Trích ảnh: ưu tiên enclosure của feed, sau đó tới thẻ <img> trong nội dung
                String imageUrl = extractImage(entry, rawContent);

                if (!title.isBlank() && !link.isBlank()) {
                    articles.add(new Article(title, link, content, imageUrl));
                }
            }
        }
        System.out.println("[RSS] -> lấy được " + articles.size() + " bài");
        return articles;
    }

    /**
     * Tìm URL ảnh cho bài: (1) enclosure kiểu image trong feed,
     * (2) thẻ <img> đầu tiên trong nội dung HTML. Trả về null nếu không có.
     */
    private String extractImage(SyndEntry entry, String rawContentHtml) {
        // (1) enclosure (nhiều feed VN như VnExpress đính ảnh ở đây)
        if (entry.getEnclosures() != null) {
            for (var enc : entry.getEnclosures()) {
                String type = enc.getType();
                String url = enc.getUrl();
                if (url != null && !url.isBlank()
                        && (type == null || type.startsWith("image"))) {
                    return url.trim();
                }
            }
        }
        // (2) thẻ <img> trong nội dung
        try {
            var img = Jsoup.parse(rawContentHtml).selectFirst("img[src]");
            if (img != null) {
                String src = img.attr("src").trim();
                if (!src.isBlank()) return src;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
