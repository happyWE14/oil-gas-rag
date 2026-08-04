package com.wong.collector.application.workflow.transform.ocr;

import org.springframework.stereotype.Component;

import com.wong.collector.application.workflow.transform.MarkdownStorageClient;
import com.wong.collector.application.workflow.transform.MinerUClient;
import com.wong.collector.application.workflow.transform.MarkdownStorageClient.StoredMarkdown;
import com.wong.collector.application.workflow.transform.ocr.OcrTransformGateway.OcrResult;
import com.wong.collector.domain.paper.model.TransformProvider;
import com.wong.collector.infrastructure.external.mineru.dto.MinerUTaskStatusResponse;

import lombok.RequiredArgsConstructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
@RequiredArgsConstructor
public class MineruOcrTransformGateway implements OcrTransformGateway {

    private final MinerUClient minerUClient;
    private final MarkdownStorageClient markdownStorageClient;

    @Override
    public boolean supports(TransformProvider provider) {
        return TransformProvider.MINER_U == provider;
    }

    @Override
    public OcrResult transform(String paperId, String downloadUrl, boolean saveMarkdown) {
        String taskId = minerUClient.submitTask(downloadUrl, paperId);
        MinerUTaskStatusResponse.DataBody result = minerUClient.pollResult(taskId, 60, 5000);
        if (!"done".equalsIgnoreCase(result.getState()) || result.getFullZipUrl() == null) {
            throw new IllegalStateException("解析失败: " + result.getState());
        }
        StoredMarkdown stored = markdownStorageClient.download(paperId, result.getFullZipUrl(), saveMarkdown);
        return new OcrResult(stored.url(), stored.content(), stored.checksum());
    }

    @Override
    public OcrResult transformStream(String paperId, InputStream pdfStream, boolean saveMarkdown) {
        try {
            // 创建临时文件（系统临时目录）
            Path tempFile = Files.createTempFile("mineru_" + paperId + "_", ".pdf");

            try {
                // 将 InputStream 写入临时文件
                Files.copy(pdfStream, tempFile, StandardCopyOption.REPLACE_EXISTING);

                // 生成 file:// URL
                String fileUrl = tempFile.toUri().toString(); // 例如: file:///C:/Users/.../temp/mineru_xxx.pdf

                // 复用原有的 transform 方法
                // 注意：这要求 MinerU 服务能访问本地文件系统（本地部署模式）
                // 如果 MinerU 是远程 SaaS 服务，此方法会失败，需要先上传到 OSS/MinIO 获取 HTTP URL
                return transform(paperId, fileUrl, saveMarkdown);

            } finally {
                // 清理临时文件（MinerU 处理完成后删除）
                // 由于 pollResult 是同步阻塞的，这里执行时 MinerU 已经处理完毕
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception e) {
                    // 忽略清理错误
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("MinerU 处理本地文件流失败: " + e.getMessage(), e);
        }
    }
}
