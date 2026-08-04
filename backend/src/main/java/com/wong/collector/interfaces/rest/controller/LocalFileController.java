package com.wong.collector.interfaces.rest.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Pattern;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/local-file")
@Validated
public class LocalFileController {

    @Value("${paper.storage.path:./data/papers}")
    private String basePath;

    @Value("${paper.storage.pattern:{paperId}/{paperId}.pdf}")
    private String filePattern;

    @Value("${local-file.open-folder-enabled:false}")
    private boolean openFolderEnabled;

    private Path getFilePath(String paperId) {
        String fileName = filePattern.replace("{paperId}", paperId);
        Path base = Paths.get(basePath).toAbsolutePath().normalize();
        Path path = base.resolve(fileName).normalize();
        if (!path.startsWith(base)) {
            throw new SecurityException("Resolved path escapes the storage directory");
        }
        return path;
    }

    /**
     * 获取本地 PDF 文件流
     * 前端通过 /api/local-file/{paperId} 访问，浏览器会自动用 PDF 阅读器打开
     */
    @GetMapping("/{paperId}")
    public ResponseEntity<Resource> getLocalFile(
            @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String paperId) {
        try {
            String fileName = filePattern.replace("{paperId}", paperId);
            Path filePath = getFilePath(paperId);

            File file = filePath.toFile();

            // 检查文件是否存在且可读
            if (!file.exists() || !file.canRead()) {
                // 尝试另一种命名格式（带下划线）
                if (!fileName.contains("_")) {
                    String altFileName = "paper_" + paperId + ".pdf";
                    File altFile = Paths.get(basePath).toAbsolutePath().normalize().resolve(altFileName).toFile();
                    if (altFile.exists() && altFile.canRead()) {
                        file = altFile;
                    } else {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(null);
                    }
                } else {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                }
            }

            Resource resource = new FileSystemResource(file);

            // 设置响应头，让浏览器内联显示 PDF
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + file.getName() + "\"")
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 检查文件是否存在（用于前端图标显示）
     */
    @GetMapping("/{paperId}/exists")
    public ResponseEntity<Boolean> checkFileExists(
            @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String paperId) {
        try {
            Path filePath = getFilePath(paperId);

            // 也检查带下划线的格式
            String altFileName = "paper_" + paperId + ".pdf";
            Path altPath = Paths.get(basePath).toAbsolutePath().normalize().resolve(altFileName);

            boolean exists = filePath.toFile().exists() || altPath.toFile().exists();
            return ResponseEntity.ok(exists);
        } catch (Exception e) {
            return ResponseEntity.ok(false);
        }
    }

    /**
     * 打开文件所在文件夹（Windows 资源管理器）
     * 注意：此方法仅在服务器本地运行有效，如果部署在远程服务器则无法打开客户端文件夹
     */
    @PostMapping("/{paperId}/open-folder")
    public ResponseEntity<String> openFolder(
            @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String paperId) {
        if (!openFolderEnabled) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("本地文件夹操作未启用");
        }
        try {
            Path filePath = getFilePath(paperId);

            File folder = filePath.getParent().toFile();
            if (!folder.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("文件夹不存在");
            }

            // 使用 Desktop API 打开文件夹
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(folder);
                return ResponseEntity.ok("已打开文件夹");
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("当前环境不支持打开文件夹");
            }
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("打开失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件信息（大小、修改时间等）
     */
    @GetMapping("/{paperId}/info")
    public ResponseEntity<FileInfo> getFileInfo(
            @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String paperId) {
        try {
            Path filePath = getFilePath(paperId);

            File file = filePath.toFile();
            if (!file.exists()) {
                // 尝试带下划线版本
                File altFile = Paths.get(basePath).toAbsolutePath().normalize()
                        .resolve("paper_" + paperId + ".pdf").toFile();
                if (altFile.exists()) {
                    file = altFile;
                } else {
                    return ResponseEntity.notFound().build();
                }
            }

            FileInfo info = new FileInfo();
            info.setFileName(file.getName());
            info.setFileSize(file.length());
            info.setLastModified(file.lastModified());
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 内部类：文件信息 DTO
    public static class FileInfo {
        private String fileName;
        private long fileSize;
        private long lastModified;

        // Getters and Setters
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
        public long getLastModified() { return lastModified; }
        public void setLastModified(long lastModified) { this.lastModified = lastModified; }
    }
}
