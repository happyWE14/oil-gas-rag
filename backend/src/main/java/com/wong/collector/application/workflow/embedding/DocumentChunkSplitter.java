package com.wong.collector.application.workflow.embedding;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.infrastructure.config.properties.EmbeddingBusinessProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DocumentChunkSplitter {

    private final EmbeddingBusinessProperties properties;

    public List<Document> split(String content, Paper paper) {
        String cleaned = stripAnnotations(content);
        List<String> blocks = markdownAwareBlocks(cleaned);
        int chunkSize = properties.getChunking().getChunkSize();
        int overlap = Math.min(properties.getChunking().getChunkOverlap(), Math.max(0, chunkSize / 3));

        TokenTextSplitter splitter = new TokenTextSplitter(
            chunkSize,
            overlap,
            5,
            10000,
            true
        );

        List<Document> result = new ArrayList<>();
        for (String block : blocks) {
            if (block.isBlank()) {
                continue;
            }
            Document document = new Document(block, Map.of(
                "paperId", paper.getId().getValue(),
                "title", paper.getTitle().value()
            ));
            // 表格优先整体保留，若长度超限再拆分
            if (isTableBlock(block) && block.length() <= chunkSize * 2) {
                result.add(document);
            } else {
                result.addAll(splitter.split(document));
            }
        }
        return result;
    }

    /**
     * 去掉 OCR 段落中的标注（整行或行内），仅保留正文用于向量化。
     */
    private String stripAnnotations(String content) {
        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\\r?\\n")) {
            String trimmed = line.trim();
            // 整行标注，如 [text] (x,x,x,x)
            if (trimmed.matches("^\\[[^]]+]\\s*\\(.*\\)$")) {
                continue;
            }
            // 去除行内的 [xxx](...) 片段
            String cleaned = trimmed.replaceAll("\\[[^]]+]\\s*\\([^)]*\\)", "").trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            sb.append(cleaned).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 按 Markdown 结构分块，尽量保持表格和标题下的内容完整。
     */
    private List<String> markdownAwareBlocks(String content) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inTable = false;
        boolean inFence = false;

        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                inFence = !inFence;
                current.append(line).append("\n");
                continue;
            }
            if (inFence) {
                current.append(line).append("\n");
                continue;
            }

            boolean isTableLine = trimmed.startsWith("|") && trimmed.contains("|");
            boolean isHeader = trimmed.startsWith("#");
            boolean isSeparator = trimmed.matches("^\\|?\\s*-{3,}.*");

            if (isTableLine || inTable && isSeparator) {
                if (!inTable) {
                    // flush previous block
                    flushBlock(blocks, current);
                    inTable = true;
                }
                current.append(line).append("\n");
                continue;
            }

            if (inTable) {
                // table结束
                flushBlock(blocks, current);
                inTable = false;
            }

            if (isHeader) {
                flushBlock(blocks, current);
                current.append(line).append("\n");
                continue;
            }

            current.append(line).append("\n");
            // 段落空行作为分段
            if (trimmed.isEmpty()) {
                flushBlock(blocks, current);
            }
        }
        flushBlock(blocks, current);
        return blocks;
    }

    private void flushBlock(List<String> blocks, StringBuilder current) {
        if (current.isEmpty()) {
            return;
        }
        blocks.add(current.toString().trim());
        current.setLength(0);
    }

    private boolean isTableBlock(String block) {
        String[] lines = block.split("\\r?\\n");
        int tableLines = 0;
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("|") && t.contains("|")) {
                tableLines++;
            }
        }
        return tableLines >= 2;
    }
}
