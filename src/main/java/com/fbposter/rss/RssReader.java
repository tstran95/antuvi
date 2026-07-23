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

    private final boolean fetchFullPage;

    public RssReader() {
        this(false);
    }

    /**
     * @param fetchFullPage nếu true: truy cập link bài viết để lấy nội dung đầy đủ + ảnh
     *                      (chậm hơn nhưng bài viết chất lượng hơn)
     */
    public RssReader(boolean fetchFullPage) {
        this.fetchFullPage = fetchFullPage;
    }

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
                    Article article = new Article(title, link, content, imageUrl);
                    if (fetchFullPage) {
                        article = enrichFromPage(article);
                    }
                    articles.add(article);
                }
            }
        }
        System.out.println("[RSS] -> lấy được " + articles.size() + " bài"
                + (fetchFullPage ? " (đã fetch nội dung đầy đủ)" : ""));
        return articles;
    }

    /**
     * Fetch trang bài viết đầy đủ từ link, trích xuất nội dung text + ảnh chính.
     * Nếu fetch thất bại -> giữ nguyên dữ liệu RSS gốc.
     */
    private Article enrichFromPage(Article rssArticle) {
        String url = rssArticle.getLink();
        if (url == null || url.isBlank()) return rssArticle;

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (compatible; FbAutoPoster/1.0)")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) return rssArticle;

            var doc = Jsoup.parse(resp.body());

            // Trích nội dung text từ các vùng phổ biến
            String fullContent = extractPageContent(doc);
            if (fullContent == null || fullContent.isBlank()) return rssArticle;

            // Trích ảnh chính: ưu tiên ảnh trong vùng nội dung
            String pageImage = extractPageImage(doc);
            String image = pageImage != null ? pageImage : rssArticle.getImageUrl();

            return new Article(rssArticle.getTitle(), rssArticle.getLink(), fullContent, image);
        } catch (Exception e) {
            System.out.println("[RSS] Không fetch được trang: " + url + " (" + e.getMessage() + ")");
            return rssArticle; // fallback RSS gốc
        }
    }

    /**
     * Trích xuất nội dung text từ trang web, thử nhiều selector phổ biến.
     */
    private String extractPageContent(org.jsoup.nodes.Document doc) {
        // Thử các vùng nội dung chính theo thứ tự ưu tiên
        String[] selectors = {
                "#container .panel",       // kabala.vn
                "article",                  // chuẩn HTML5
                ".entry-content",           // WordPress
                ".post-content",            // phổ biến
                ".article-content",         // báo chí VN
                ".content",                 // generic
                "main",                     // HTML5
                "#content",                 // WordPress cũ
        };
        for (String sel : selectors) {
            var el = doc.selectFirst(sel);
            if (el != null) {
                String text = el.text().trim();
                if (text.length() > 100) { // đủ dài mới nhận
                    return text;
                }
            }
        }
        // Fallback: toàn bộ body text
        var body = doc.selectFirst("body");
        return body != null ? body.text().trim() : null;
    }

    /**
     * Trích URL ảnh chính từ trang web.
     * Ưu tiên: og:image -> twitter:image -> ảnh lớn trong nội dung.
     * Validate kích thước tối thiểu 400px để loại icon/thumbnail nhỏ.
     */
    private String extractPageImage(org.jsoup.nodes.Document doc) {
        // (1) og:image meta — chất lượng cao nhất, thiết kế cho social sharing
        String ogSrc = metaContent(doc, "meta[property=og:image]");
        if (ogSrc != null && isGoodImageUrl(ogSrc)) return ogSrc;

        // (2) twitter:image — nhiều site VN có twitter card nhưng thiếu og:image
        String twSrc = metaContent(doc, "meta[name=twitter:image]");
        if (twSrc == null) twSrc = metaContent(doc, "meta[property=twitter:image]");
        if (twSrc != null && isGoodImageUrl(twSrc)) return twSrc;

        // (3) Ảnh lớn trong vùng nội dung — ưu tiên ảnh có width/height lớn
        String[] contentSelectors = {
                ".entry-content img", ".post-content img",
                "article img", ".article-content img",
                ".content img", ".panel img", "main img"
        };
        String bestSrc = null;
        int bestSize = 0;
        for (String sel : contentSelectors) {
            for (var img : doc.select(sel)) {
                String src = img.absUrl("src");
                if (src == null || src.isBlank() || !isGoodImageUrl(src)) continue;
                int w = attrInt(img, "width");
                int h = attrInt(img, "height");
                int size = Math.max(w, h);
                if (size == 0) size = 500;
                if (size > bestSize) {
                    bestSize = size;
                    bestSrc = src;
                }
            }
        }
        return bestSrc;
    }

    private String metaContent(org.jsoup.nodes.Document doc, String cssQuery) {
        var el = doc.selectFirst(cssQuery);
        if (el == null) return null;
        String val = el.attr("content").trim();
        return val.isBlank() ? null : val;
    }

    private int attrInt(org.jsoup.nodes.Element el, String attr) {
        try {
            return Integer.parseInt(el.attr(attr).replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean isGoodImageUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String lower = url.toLowerCase();
        return !lower.contains("logo") && !lower.contains("/icon")
                && !lower.contains("/icons/") && !lower.contains("avatar")
                && !lower.contains("12congiap") && !lower.contains("loading")
                && !lower.contains("spinner") && !lower.contains("placeholder")
                && !lower.contains("1x1") && !lower.contains("pixel")
                && !lower.endsWith(".gif") && !lower.endsWith(".svg");
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
