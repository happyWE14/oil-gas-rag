package com.wong.collector.application.workflow.transform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.wong.collector.application.dto.request.TransformCompletedRequest;
import com.wong.collector.application.service.PaperApplicationService;
import com.wong.collector.application.workflow.transform.ocr.OcrTransformGateway;
import com.wong.collector.application.workflow.transform.ocr.OcrTransformGateway.OcrResult;
import com.wong.collector.domain.common.exception.NotFoundException;
import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.PaperContent;
import com.wong.collector.domain.paper.model.PaperId;
import com.wong.collector.domain.paper.model.TextSourceType;
import com.wong.collector.domain.paper.model.TransformProvider;
import com.wong.collector.domain.paper.repository.PaperContentRepository;
import com.wong.collector.domain.paper.repository.PaperRepository;
import com.wong.collector.infrastructure.config.properties.TransformBusinessProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaperTransformExecutor {

    private final PaperRepository paperRepository;
    private final PaperApplicationService paperApplicationService;
    private final List<OcrTransformGateway> gateways;
    private final PaperContentRepository paperContentRepository;
    private final TransformBusinessProperties transformBusinessProperties;

    @Value("${dromara.x-file-storage.local-plus.base-path:./data}")
    private String basePath;

    public String transform(String paperId) {
        Paper paper = paperRepository.findById(PaperId.of(paperId))
                .orElseThrow(() -> new NotFoundException("Paper not found"));

        if (paper.getDownloadInfo() == null) {
            throw new IllegalStateException("未下载完成");
        }

        // 构造本地PDF路径（与下载时保存的路径一致）
        Path pdfPath = Paths.get(basePath, "paper", paperId, paperId + ".pdf");

        if (!Files.exists(pdfPath)) {
            log.error("PDF文件不存在: {}", pdfPath);
            throw new IllegalStateException("本地PDF文件不存在: " + pdfPath);
        }

        if (paper.getTransformInfo() == null || paper.getTransformInfo().getProvider() == null) {
            throw new IllegalStateException("转换信息缺失");
        }

        TransformProvider provider = paper.getTransformInfo().getProvider();
        OcrTransformGateway gateway = resolveGateway(provider);

        try (InputStream pdfStream = Files.newInputStream(pdfPath)) {
            // 关键修改：直接传入 InputStream，不再使用 URL
            OcrResult stored = gateway.transformStream(paperId, pdfStream,
                    transformBusinessProperties.isSaveMarkdown());

            byte[] contentBytes = stored.textContent().getBytes(StandardCharsets.UTF_8);
            PaperContent content = PaperContent.create(
                    PaperId.of(paperId),
                    stored.textContent(),
                    mapSourceType(provider),
                    StandardCharsets.UTF_8.name(),
                    stored.checksum(),
                    contentBytes.length
            );
            paperContentRepository.saveNewVersion(content);

            TransformCompletedRequest request = new TransformCompletedRequest();
            request.setMarkdownPath(stored.markdownUrl());
            paperApplicationService.completeTransform(paperId, request);

            return stored.markdownUrl();

        } catch (IOException e) {
            log.error("读取PDF文件失败: {}", pdfPath, e);
            throw new IllegalStateException("读取PDF文件失败: " + e.getMessage(), e);
        }
    }

    private OcrTransformGateway resolveGateway(TransformProvider provider) {
        return gateways.stream()
                .filter(g -> g.supports(provider))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No OCR gateway for provider: " + provider));
    }

    private TextSourceType mapSourceType(TransformProvider provider) {
        return switch (provider) {
            case MINER_U, DEEPSEEK_OCR, PADDLE_OCR -> TextSourceType.OCR;
            default -> TextSourceType.OTHER;
        };
    }
}
