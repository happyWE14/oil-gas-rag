package com.wong.collector.application.workflow.transform;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.springframework.stereotype.Component;

import cn.hutool.crypto.digest.DigestUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarkdownStorageClient {

    private final FileStorageService fileStorageService;

    public StoredMarkdown download(String paperId, String zipUrl, boolean uploadToOss) {
        Path tempZip = null;
        Path tempDir = null;
        try {
            tempZip = Files.createTempFile("ocr_" + paperId, ".zip");
            try (InputStream in = openFromOss(zipUrl);
                 FileOutputStream out = new FileOutputStream(tempZip.toFile())) {
                in.transferTo(out);
            }
            tempDir = Files.createTempDirectory("ocr_md_");
            unzip(tempZip, tempDir);
            Path markdown = findMarkdownFile(tempDir);
            String content = stripAnnotations(Files.readString(markdown, StandardCharsets.UTF_8));
            String checksum = DigestUtil.md5Hex(content);
            if (uploadToOss) {
                try (InputStream mdStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
                    FileInfo info = fileStorageService.of(mdStream)
                        .setOriginalFilename(paperId + ".md")
                        .setPath("paper/" + paperId + "/")
                        .upload();
                    return new StoredMarkdown(info.getUrl(), content, checksum);
                }
            }
            return new StoredMarkdown(null, content, checksum);
        } catch (Exception ex) {
            throw new IllegalStateException("存储Markdown失败: " + ex.getMessage(), ex);
        } finally {
            cleanupPath(tempDir);
            cleanupPath(tempZip);
        }
    }

    public StoredMarkdown storeRawMarkdown(String paperId, String content) {
        return storeRawMarkdown(paperId, content, true);
    }

    public StoredMarkdown storeRawMarkdown(String paperId, String content, boolean uploadToOss) {
        String cleaned = stripAnnotations(content);
        String checksum = DigestUtil.md5Hex(cleaned);
        if (!uploadToOss) {
            return new StoredMarkdown(null, cleaned, checksum);
        }
        try (InputStream mdStream = new ByteArrayInputStream(cleaned.getBytes(StandardCharsets.UTF_8))) {
            FileInfo info = fileStorageService.of(mdStream)
                .setOriginalFilename(paperId + ".md")
                .setPath("paper/" + paperId + "/")
                .upload();
            return new StoredMarkdown(info.getUrl(), cleaned, checksum);
        } catch (Exception ex) {
            throw new IllegalStateException("存储Markdown失败: " + ex.getMessage(), ex);
        }
    }

    public String readContent(String url) {
        try (InputStream in = openFromOss(url)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("读取Markdown失败: " + ex.getMessage(), ex);
        }
    }

    private InputStream openFromOss(String url) throws Exception {
        byte[] data = fileStorageService.download(url).bytes();
        return new ByteArrayInputStream(data);
    }

    private void unzip(Path source, Path targetDir) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(source.toFile()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path outFile = targetDir.resolve(entry.getName()).normalize();
                if (!outFile.startsWith(targetDir)) {
                    throw new IllegalStateException("ZIP entry outside target directory");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(outFile);
                } else {
                    Files.createDirectories(outFile.getParent());
                    try (FileOutputStream fos = new FileOutputStream(outFile.toFile())) {
                        zip.transferTo(fos);
                    }
                }
            }
        }
    }

    private Path findMarkdownFile(Path dir) throws Exception {
        try (var stream = Files.walk(dir)) {
            return stream
                .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".md"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ZIP 中未找到 Markdown"));
        }
    }

    private void cleanupPath(Path path) {
        if (path == null) {
            return;
        }
        try {
            if (Files.isDirectory(path)) {
                try (var walk = Files.walk(path)) {
                    walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                        }
                    });
                }
            } else {
                Files.deleteIfExists(path);
            }
        } catch (Exception ignored) {
        }
    }

    public record StoredMarkdown(String url, String content, String checksum) {}

    /**
     * 去除 OCR 输出中的块标注行，如 [equation] (...), [text] (...), [title] (...)，仅保留正文。
     */
    private String stripAnnotations(String content) {
        // 按行处理，过滤掉以 [xxx] (...) 形式的行
        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.matches("^\\[[^]]+\\]\\s*\\(.*\\)$")) {
                continue;
            }
            sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }
}
