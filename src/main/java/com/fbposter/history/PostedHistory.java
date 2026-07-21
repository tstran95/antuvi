package com.fbposter.history;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Lưu danh sách link đã đăng để không đăng trùng.
 * Dữ liệu ghi ra file posted-history.json (dạng mảng URL).
 */
public class PostedHistory {

    private final File file;
    private final ObjectMapper mapper = new ObjectMapper();
    private Set<String> postedLinks = new HashSet<>();

    public PostedHistory(String path) {
        this.file = new File(path);
        load();
    }

    private void load() {
        if (!file.exists()) {
            System.out.println("[History] Chưa có lịch sử, tạo mới.");
            return;
        }
        try {
            String[] arr = mapper.readValue(file, String[].class);
            postedLinks = new HashSet<>();
            for (String s : arr) postedLinks.add(s);
            System.out.println("[History] Đã tải " + postedLinks.size() + " link đã đăng.");
        } catch (Exception e) {
            System.out.println("[History] Lỗi đọc lịch sử: " + e.getMessage());
        }
    }

    public boolean isPosted(String link) {
        return postedLinks.contains(link);
    }

    public void markPosted(String link) {
        postedLinks.add(link);
        save();
    }

    private void save() {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, postedLinks);
        } catch (Exception e) {
            System.out.println("[History] Lỗi lưu lịch sử: " + e.getMessage());
        }
    }
}
