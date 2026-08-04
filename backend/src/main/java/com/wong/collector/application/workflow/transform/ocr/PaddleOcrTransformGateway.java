package com.wong.collector.application.workflow.transform.ocr;

import java.io.InputStream;
import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wong.collector.application.workflow.transform.MarkdownStorageClient;
import com.wong.collector.application.workflow.transform.MarkdownStorageClient.StoredMarkdown;
import com.wong.collector.application.workflow.transform.ocr.OcrTransformGateway.OcrResult;
import com.wong.collector.application.workflow.transform.ocr.support.OssPdfLoader;
import com.wong.collector.domain.paper.model.TransformProvider;
import com.wong.collector.infrastructure.config.properties.PaddleOcrProperties;
import com.wong.collector.infrastructure.external.paddleocr.dto.PaddleOcrRequest;
import com.wong.collector.infrastructure.external.paddleocr.dto.PaddleOcrResponse;
import com.wong.collector.infrastructure.external.paddleocr.dto.PaddleOcrResponse.LayoutParsingResult;
import com.wong.collector.infrastructure.external.client.PaddleOcrClient;
import com.wong.collector.application.task.NonRetryableTaskException;
import com.wong.collector.application.task.RetryableTaskException;
import com.wong.collector.application.task.RetryAfterSupport;

import com.dtflys.forest.http.ForestResponse;

import java.time.Duration;

@Component
public class PaddleOcrTransformGateway implements OcrTransformGateway {

    private final PaddleOcrProperties properties;
    private final MarkdownStorageClient markdownStorageClient;
    private final OssPdfLoader ossPdfLoader;
    private final ObjectMapper objectMapper;
    private final PaddleOcrClient paddleOcrClient;

    public PaddleOcrTransformGateway(PaddleOcrProperties properties,
                                     MarkdownStorageClient markdownStorageClient,
                                     OssPdfLoader ossPdfLoader,
                                     ObjectMapper objectMapper,
                                     PaddleOcrClient paddleOcrClient) {
        this.properties = properties;
        this.markdownStorageClient = markdownStorageClient;
        this.ossPdfLoader = ossPdfLoader;
        this.objectMapper = objectMapper;
        this.paddleOcrClient = paddleOcrClient;
    }

    @Override
    public boolean supports(TransformProvider provider) {
        return TransformProvider.PADDLE_OCR == provider;
    }

    @Override
    public OcrResult transform(String paperId, String downloadUrl, boolean saveMarkdown) {
        byte[] pdfBytes = ossPdfLoader.loadBytes(downloadUrl);
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalStateException("PDF 内容为空");
        }
        String base64 = Base64.getEncoder().encodeToString(pdfBytes);

        return callPaddleOcr(paperId, base64, saveMarkdown);
    }

    @Override
    public OcrResult transformStream(String paperId, InputStream pdfStream, boolean saveMarkdown) {
        try {
            // 从 InputStream 读取所有字节
            byte[] pdfBytes = pdfStream.readAllBytes();

            if (pdfBytes == null || pdfBytes.length == 0) {
                throw new IllegalStateException("PDF 流内容为空");
            }

            String base64 = Base64.getEncoder().encodeToString(pdfBytes);

            // 复用原有的 OCR 调用逻辑
            return callPaddleOcr(paperId, base64, saveMarkdown);

        } catch (Exception e) {
            throw new IllegalStateException("PaddleOCR 处理流失败: " + e.getMessage(), e);
        }
    }

    /**
     * 提取公共方法：调用 PaddleOCR API
     */
    private OcrResult callPaddleOcr(String paperId, String base64Pdf, boolean saveMarkdown) {
        String token = properties.getToken();
        if (token == null || token.isBlank()) {
            throw new NonRetryableTaskException("未配置 PaddleOCR token，请设置配置项 paddleocr.token");
        }
        String authorization = "token " + token.trim();

        PaddleOcrRequest request = new PaddleOcrRequest();
        request.setFile(base64Pdf);
        request.setFileType(properties.getFileType());
        request.setUseDocOrientationClassify(properties.isUseDocOrientationClassify());
        request.setUseDocUnwarping(properties.isUseDocUnwarping());
        request.setUseChartRecognition(properties.isUseChartRecognition());

        ForestResponse<PaddleOcrResponse> response = paddleOcrClient.parse(authorization, request);

        if (response == null || !response.isSuccess()) {
            int statusCode = response != null ? response.getStatusCode() : 0;
            Duration retryAfter = RetryAfterSupport.parse(response);
            if (statusCode == 429) {
                throw new RetryableTaskException("PaddleOCR 调用被限流/配额耗尽（HTTP 429）", retryAfter);
            }
            if (statusCode == 503 || statusCode == 502 || statusCode == 504 || statusCode == 500) {
                throw new RetryableTaskException("PaddleOCR 服务繁忙/上游异常（HTTP " + statusCode + "）", retryAfter);
            }
            if (statusCode == 401 || statusCode == 403) {
                throw new NonRetryableTaskException("PaddleOCR 鉴权失败（HTTP " + statusCode + "）");
            }
            if (statusCode >= 400 && statusCode < 500) {
                throw new NonRetryableTaskException("PaddleOCR 请求失败（HTTP " + statusCode + "）");
            }
            throw new RetryableTaskException("PaddleOCR HTTP失败（HTTP " + statusCode + "）", retryAfter);
        }

        PaddleOcrResponse body = response.getResult();
        if (body.getErrorCode() != null && body.getErrorCode() != 0) {
            throw new NonRetryableTaskException("PaddleOCR 调用失败: " + body.getErrorMsg());
        }
        if (body.getResult() == null || body.getResult().getLayoutParsingResults() == null
                || body.getResult().getLayoutParsingResults().isEmpty()) {
            throw new RetryableTaskException("PaddleOCR 响应缺少解析结果", Duration.ofSeconds(5));
        }

        String markdown = mergeMarkdown(body.getResult().getLayoutParsingResults());

        // ===== 添加空内容检查 =====
        if (markdown == null || markdown.trim().isEmpty()) {
            throw new RetryableTaskException("PaddleOCR 未识别到文本内容（可能是纯图片PDF），可重试", Duration.ofSeconds(5));
        }

        StoredMarkdown stored = markdownStorageClient.storeRawMarkdown(paperId, markdown, saveMarkdown);

        // ===== 修正 OcrResult 参数顺序：textContent, markdownUrl, checksum =====
        // 原来是：new OcrResult(stored.url(), stored.content(), stored.checksum())
        // 改为：textContent 在前，url 在后
        String content = stored.content();
        if (content == null) {
            content = "";  // 防御性编程，避免 null 导致后续 getBytes() 报错
        }

        return new OcrResult(content, stored.url(), stored.checksum());
    }

    private String mergeMarkdown(List<LayoutParsingResult> pages) {
        if (pages == null || pages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            String pageText = extractPageText(pages.get(i));
            if (pages.size() > 1) {
                sb.append("# Page ").append(i + 1).append("\n\n");
            }
            sb.append(pageText).append("\n\n");
        }
        return sb.toString().trim();
    }

    private String extractPageText(LayoutParsingResult page) {
        if (page == null) {
            return "";
        }
        if (page.getMarkdown() != null && page.getMarkdown().getText() != null
                && !page.getMarkdown().getText().isBlank()) {
            return page.getMarkdown().getText();
        }
        if (page.getPrunedResult() != null) {
            try {
                return objectMapper.writeValueAsString(page.getPrunedResult());
            } catch (JsonProcessingException ignored) {
            }
        }
        return "";
    }
}
