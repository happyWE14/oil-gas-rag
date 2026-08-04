package com.wong.collector.application.workflow.transform.ocr;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import com.dtflys.forest.http.ForestResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wong.collector.application.workflow.transform.MarkdownStorageClient;
import com.wong.collector.application.workflow.transform.ocr.support.OssPdfLoader;
import com.wong.collector.infrastructure.telemetry.TelemetryRecorder;
import com.wong.collector.domain.paper.model.TransformProvider;
import com.wong.collector.infrastructure.config.properties.DeepSeekOcrProperties;
import com.wong.collector.infrastructure.external.client.DeepseekOcrClient;
import com.wong.collector.infrastructure.external.deepseek.dto.DeepseekChatRequest;
import com.wong.collector.infrastructure.external.deepseek.dto.DeepseekChatResponse;
import com.wong.collector.infrastructure.util.ChecksumUtils;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DeepseekOcrTransformGateway implements OcrTransformGateway {

    private static final Pattern DET_PATTERN = Pattern.compile(
            "<\\|ref\\|>(?<rtype>[^<]+)<\\|/ref\\|>\\s*<\\|det\\|>(?<coords>\\[\\[.*?\\]\\])<\\|/det\\|>",
            Pattern.DOTALL
    );

    private final DeepSeekOcrProperties properties;
    private final DeepseekOcrClient client;
    private final MarkdownStorageClient markdownStorageClient;
    private final TelemetryRecorder telemetryRecorder;
    private final OssPdfLoader ossPdfLoader;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(TransformProvider provider) {
        return TransformProvider.DEEPSEEK_OCR == provider;
    }

    @Override
    public OcrResult transform(String paperId, String downloadUrl, boolean saveMarkdown) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("DeepSeek-OCR 未配置 apiKey");
        }
        byte[] pdfBytes = ossPdfLoader.loadBytes(downloadUrl);
        return processPdfBytes(paperId, pdfBytes, saveMarkdown);
    }

    /**
     * 新增：直接处理 InputStream（本地文件流程）
     */
    @Override
    public OcrResult transformStream(String paperId, InputStream pdfStream, boolean saveMarkdown) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("DeepSeek-OCR 未配置 apiKey");
        }

        try {
            // 从 InputStream 读取所有字节（Java 9+ 有 readAllBytes()）
            byte[] pdfBytes = pdfStream.readAllBytes();
            return processPdfBytes(paperId, pdfBytes, saveMarkdown);
        } catch (Exception ex) {
            throw new IllegalStateException("DeepSeek-OCR 读取流失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 提取公共方法：处理 PDF 字节数组
     */
    private OcrResult processPdfBytes(String paperId, byte[] pdfBytes, boolean saveMarkdown) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalStateException("PDF 内容为空");
        }

        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            StringBuilder all = new StringBuilder();

            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, properties.getRenderDpi());
                String b64 = encodeImage(image);
                String content = callDeepseekWithCache(paperId, i, b64);
                List<String> blocks = parseBlocks(content, image.getWidth(), image.getHeight());

                all.append("# Page ").append(i + 1).append("\n");
                for (String block : blocks) {
                    all.append(block).append("\n\n");
                }
            }

            MarkdownStorageClient.StoredMarkdown stored = saveMarkdown
                    ? markdownStorageClient.storeRawMarkdown(paperId, all.toString())
                    : new MarkdownStorageClient.StoredMarkdown(null, all.toString(),
                    ChecksumUtils.md5(all.toString()));

            return new OcrResult(stored.url(), stored.content(), stored.checksum());

        } catch (Exception ex) {
            throw new IllegalStateException("DeepSeek-OCR 处理失败: " + ex.getMessage(), ex);
        }
    }

    private String encodeImage(BufferedImage image) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }

    private String callDeepseek(String base64Image) throws Exception {
        DeepseekChatRequest.ImageUrl imageUrl = new DeepseekChatRequest.ImageUrl();
        imageUrl.setUrl("data:image/png;base64," + base64Image);

        DeepseekChatRequest.Content text = new DeepseekChatRequest.Content();
        text.setType("text");
        text.setText("Free OCR");

        DeepseekChatRequest.Content image = new DeepseekChatRequest.Content();
        image.setType("image_url");
        image.setImage_url(imageUrl);

        DeepseekChatRequest.Message message = new DeepseekChatRequest.Message();
        message.setRole("user");
        message.setContent(List.of(text, image));

        DeepseekChatRequest request = new DeepseekChatRequest();
        request.setModel(properties.getModel());
        request.setMessages(List.of(message));

        ForestResponse<DeepseekChatResponse> response = client.chat("Bearer " + properties.getApiKey(), request);
        if (!response.isSuccess() || response.getResult() == null) {
            throw new IllegalStateException("DeepSeek-OCR HTTP失败: " + response.getStatusCode());
        }
        DeepseekChatResponse body = response.getResult();
        if (body.getChoices() == null || body.getChoices().isEmpty()
                || body.getChoices().get(0).getMessage() == null
                || body.getChoices().get(0).getMessage().getContent() == null) {
            throw new IllegalStateException("DeepSeek-OCR 响应缺少内容");
        }
        return body.getChoices().get(0).getMessage().getContent();
    }

    private String callDeepseekWithCache(String paperId, int pageIndex, String base64Image) throws Exception {
        String key = String.format("ocr:deepseek:%s:page:%d", paperId, pageIndex);
        String cached = telemetryRecorder.getCachedText(key);
        if (cached != null) {
            return cached;
        }
        String content = callDeepseek(base64Image);
        telemetryRecorder.cacheText(key, content, 7 * 24 * 3600);
        return content;
    }

    private List<String> parseBlocks(String content, int width, int height) {
        List<String> blocks = new ArrayList<>();
        List<MatchResult> matches = new ArrayList<>();
        Matcher m = DET_PATTERN.matcher(content);
        while (m.find()) {
            matches.add(m.toMatchResult());
        }
        for (int i = 0; i < matches.size(); i++) {
            MatchResult current = matches.get(i);
            String type = current.group(1).trim();
            String coordsText = current.group(2);
            List<List<Integer>> boxes = parseCoords(coordsText);
            int nextStart = i + 1 < matches.size() ? matches.get(i + 1).start() : content.length();
            String text = content.substring(current.end(), nextStart).trim();
            if (text.startsWith("#")) {
                text = text.replaceFirst("^#+", "").trim();
            }
            StringBuilder line = new StringBuilder();
            line.append("[").append(type).append("] ");
            if (!boxes.isEmpty()) {
                List<String> formatted = new ArrayList<>();
                for (List<Integer> b : boxes) {
                    List<Integer> px = normToPx(b, width, height);
                    formatted.add(String.format("(%d,%d,%d,%d)", px.get(0), px.get(1), px.get(2), px.get(3)));
                }
                line.append(String.join(" ", formatted)).append(" ");
            }
            line.append(text);
            blocks.add(line.toString());
        }
        return blocks;
    }

    private List<List<Integer>> parseCoords(String coordsText) {
        List<List<Integer>> result = new ArrayList<>();
        try {
            JsonNode node = objectMapper.readTree(coordsText);
            if (node.isArray()) {
                for (JsonNode box : node) {
                    if (box.isArray() && box.size() == 4) {
                        List<Integer> b = new ArrayList<>();
                        for (int i = 0; i < 4; i++) {
                            b.add(box.get(i).asInt());
                        }
                        result.add(b);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private List<Integer> normToPx(List<Integer> box, int w, int h) {
        if (box.size() != 4) {
            return box;
        }
        int x1 = clamp(Math.round(box.get(0) / 999f * w), w);
        int y1 = clamp(Math.round(box.get(1) / 999f * h), h);
        int x2 = clamp(Math.round(box.get(2) / 999f * w), w);
        int y2 = clamp(Math.round(box.get(3) / 999f * h), h);
        return List.of(x1, y1, x2, y2);
    }

    private int clamp(int v, int max) {
        return Math.max(0, Math.min(max, v));
    }
}
