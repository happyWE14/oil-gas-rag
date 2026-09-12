package com.wong.collector.application.workflow.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.infrastructure.config.properties.EmbeddingBusinessProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DocumentChunkSplitter {

    /**
     * 与 Spring AI TokenTextSplitter 默认使用的 CL100K_BASE 保持一致。
     * Encoding 是线程安全的，可以在 Spring 单例 Bean 中复用。
     */
    private static final Encoding TOKEN_ENCODING = Encodings.newDefaultEncodingRegistry()
        .getEncoding(EncodingType.CL100K_BASE);

    private static final double MIN_SEMANTIC_BOUNDARY_RATIO = 0.60;
    private static final int TABLE_MAX_MULTIPLIER = 2;

    private final EmbeddingBusinessProperties properties;

    public List<Document> split(String content, Paper paper) {
        String cleaned = stripAnnotations(content);
        List<String> blocks = markdownAwareBlocks(cleaned);

        int chunkSize = Math.max(50, properties.getChunking().getChunkSize());
        int configuredOverlap = Math.max(0, properties.getChunking().getChunkOverlap());
        // overlap 必须显著小于 chunkSize；默认 400/80 即 20% overlap。
        int overlap = Math.min(configuredOverlap, Math.max(0, chunkSize / 2));

        Map<String, Object> metadata = Map.of(
            "paperId", paper.getId().getValue(),
            "title", paper.getTitle().value()
        );

        List<Document> result = new ArrayList<>();
        for (String block : blocks) {
            if (block == null || block.isBlank()) {
                continue;
            }

            if (isTableBlock(block)) {
                result.addAll(splitTableBlock(block, metadata, chunkSize));
            } else {
                result.addAll(splitTextBlock(block, metadata, chunkSize, overlap));
            }
        }
        return result;
    }

    /**
     * 普通正文：按 token 预算做滑动窗口切分。
     *
     * 例如 chunkSize=400、overlap=80：相邻 chunk 保留约 80 token 上下文，
     * 避免材料名、实验条件和指标值恰好落在两个 chunk 的边界两侧。
     */
    private List<Document> splitTextBlock(
        String block,
        Map<String, Object> metadata,
        int chunkSize,
        int overlap
    ) {
        List<Document> result = new ArrayList<>();
        if (countTokens(block) <= chunkSize) {
            result.add(new Document(block.trim(), metadata));
            return result;
        }

        int start = 0;
        while (start < block.length()) {
            int end = findMaxEndWithinTokenBudget(block, start, chunkSize);
            if (end <= start) {
                end = nextCodePointIndex(block, start);
            }

            // 尽量在句号、分号、换行等自然边界收尾，但不为了边界把 chunk 缩得太小。
            if (end < block.length()) {
                int preferredEnd = findPreferredBoundary(block, start, end);
                if (preferredEnd > start) {
                    String preferred = block.substring(start, preferredEnd);
                    if (countTokens(preferred) >= Math.max(1, (int) (chunkSize * MIN_SEMANTIC_BOUNDARY_RATIO))) {
                        end = preferredEnd;
                    }
                }
            }

            String chunk = block.substring(start, end).trim();
            if (!chunk.isBlank()) {
                result.add(new Document(chunk, metadata));
            }

            if (end >= block.length()) {
                break;
            }

            int nextStart = findOverlapStart(block, start, end, overlap);
            // 防止极端文本导致游标不前进。
            if (nextStart <= start) {
                nextStart = end;
            }
            start = skipLeadingWhitespace(block, nextStart);
        }
        return result;
    }

    /**
     * 表格采用单独策略：400 token 是正文的目标 chunk 大小，不是“必须砍断表格”的硬限制。
     *
     * 1. 中小表格：最多允许到 chunkSize * 2（默认 800 token），整体保留；
     * 2. 超大表格：按完整数据行拆分，并在每个子表中重复表头和分隔行；
     * 3. 单行本身超限时仍优先完整保留该行，避免把一条材料-条件-指标记录截成两半。
     */
    private List<Document> splitTableBlock(
        String block,
        Map<String, Object> metadata,
        int chunkSize
    ) {
        List<Document> result = new ArrayList<>();
        int tableTokenBudget = Math.max(chunkSize, chunkSize * TABLE_MAX_MULTIPLIER);

        if (countTokens(block) <= tableTokenBudget) {
            result.add(new Document(block.trim(), metadata));
            return result;
        }

        List<String> lines = block.lines()
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .toList();

        if (lines.size() < 3 || !isTableSeparator(lines.get(1))) {
            // 不是标准 Markdown 表格时不要冒险按表格行处理，退回普通正文切分。
            int fallbackOverlap = Math.min(
                Math.max(0, properties.getChunking().getChunkOverlap()),
                Math.max(0, chunkSize / 2)
            );
            return splitTextBlock(block, metadata, chunkSize, fallbackOverlap);
        }

        String header = lines.get(0);
        String separator = lines.get(1);
        String tablePrefix = header + "\n" + separator;

        StringBuilder current = new StringBuilder(tablePrefix);
        int dataRowsInCurrent = 0;

        for (int i = 2; i < lines.size(); i++) {
            String row = lines.get(i);
            String candidate = current + "\n" + row;

            if (dataRowsInCurrent > 0 && countTokens(candidate) > tableTokenBudget) {
                result.add(new Document(current.toString().trim(), metadata));
                current = new StringBuilder(tablePrefix);
                dataRowsInCurrent = 0;
            }

            // 即使单行很长，也完整保留；下一轮会从新的子表开始。
            current.append("\n").append(row);
            dataRowsInCurrent++;
        }

        if (dataRowsInCurrent > 0) {
            result.add(new Document(current.toString().trim(), metadata));
        }

        return result;
    }

    /**
     * 在字符区间 [start, text.length] 上二分，找到 token 数不超过预算的最大 end。
     * 使用字符索引而不是直接切 token id，可以避免最终 chunk 把 Unicode 字符拆坏。
     */
    private int findMaxEndWithinTokenBudget(String text, int start, int tokenBudget) {
        int low = start + 1;
        int high = text.length();
        int best = nextCodePointIndex(text, start);

        while (low <= high) {
            int rawMid = low + (high - low) / 2;
            int mid = safeCodePointBoundary(text, rawMid);
            if (mid <= start) {
                mid = nextCodePointIndex(text, start);
            }

            int tokens = countTokens(text.substring(start, mid));
            if (tokens <= tokenBudget) {
                best = mid;
                // 按 rawMid 推进，保证遇到 surrogate pair 时二分仍一定前进。
                low = rawMid + 1;
            } else {
                high = rawMid - 1;
            }
        }
        return safeCodePointBoundary(text, best);
    }

    /**
     * 从当前 chunk 末尾向前找约 overlap 个 token 的起点，形成真正的滑动重叠。
     */
    private int findOverlapStart(String text, int chunkStart, int chunkEnd, int overlapTokens) {
        if (overlapTokens <= 0) {
            return chunkEnd;
        }

        int low = chunkStart;
        int high = chunkEnd;
        int best = chunkEnd;

        // 找“最靠前、但后缀 token 数仍 <= overlapTokens”的位置。
        while (low <= high) {
            int rawMid = low + (high - low) / 2;
            int mid = safeCodePointBoundary(text, rawMid);
            if (mid < chunkStart) {
                mid = chunkStart;
            }

            int tokens = countTokens(text.substring(mid, chunkEnd));
            if (tokens <= overlapTokens) {
                best = mid;
                high = rawMid - 1;
            } else {
                low = rawMid + 1;
            }
        }

        return moveForwardToWordBoundary(text, best, chunkEnd);
    }

    /**
     * 优先在句号、问号、感叹号、分号或换行处结束 chunk。
     */
    private int findPreferredBoundary(String text, int start, int end) {
        int min = start + (int) ((end - start) * MIN_SEMANTIC_BOUNDARY_RATIO);
        for (int i = end - 1; i >= min; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '.' || c == '?' || c == '!' || c == ';'
                || c == '。' || c == '？' || c == '！' || c == '；') {
                return i + 1;
            }
        }
        return end;
    }

    private int countTokens(String text) {
        return TOKEN_ENCODING.countTokensOrdinary(text == null ? "" : text);
    }

    private int moveForwardToWordBoundary(String text, int index, int upperBound) {
        int i = safeCodePointBoundary(text, index);
        while (i < upperBound) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                return skipLeadingWhitespace(text, i);
            }
            if (isPunctuation(c)) {
                return skipLeadingWhitespace(text, nextCodePointIndex(text, i));
            }
            i = nextCodePointIndex(text, i);
        }
        return index;
    }

    private boolean isPunctuation(char c) {
        return c == '.' || c == ',' || c == ';' || c == ':' || c == '?' || c == '!'
            || c == '。' || c == '，' || c == '；' || c == '：' || c == '？' || c == '！';
    }

    private int skipLeadingWhitespace(String text, int index) {
        int i = Math.max(0, Math.min(index, text.length()));
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    /**
     * 如果 index 正好落在 UTF-16 surrogate pair 中间，就回退到完整 code point 的边界。
     */
    private int safeCodePointBoundary(String text, int index) {
        int i = Math.max(0, Math.min(index, text.length()));
        if (i > 0 && i < text.length()
            && Character.isLowSurrogate(text.charAt(i))
            && Character.isHighSurrogate(text.charAt(i - 1))) {
            return i - 1;
        }
        return i;
    }

    private int nextCodePointIndex(String text, int index) {
        if (index >= text.length()) {
            return text.length();
        }
        return index + Character.charCount(text.codePointAt(index));
    }

    /**
     * 去掉 OCR 段落中的标注（整行或行内），保留 Markdown 空行结构。
     */
    private String stripAnnotations(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\\r?\\n", -1)) {
            String trimmed = line.trim();
            // 整行标注，如 [text] (x,x,x,x)
            if (trimmed.matches("^\\[[^]]+]\\s*\\(.*\\)$")) {
                continue;
            }

            // 去除行内 [xxx](...) 片段，但保留原有 Markdown 行结构。
            String cleaned = line.replaceAll("\\[[^]]+]\\s*\\([^)]*\\)", "").stripTrailing();
            sb.append(cleaned).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 按 Markdown 结构做第一层粗分块：标题、表格、代码块、段落。
     */
    private List<String> markdownAwareBlocks(String content) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inTable = false;
        boolean inFence = false;

        String[] lines = content.split("\\r?\\n", -1);
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

            boolean tableLine = isTableLine(trimmed);
            boolean header = trimmed.startsWith("#");

            if (tableLine) {
                if (!inTable) {
                    flushBlock(blocks, current);
                    inTable = true;
                }
                current.append(line).append("\n");
                continue;
            }

            if (inTable) {
                flushBlock(blocks, current);
                inTable = false;
            }

            if (header) {
                flushBlock(blocks, current);
                current.append(line).append("\n");
                continue;
            }

            current.append(line).append("\n");
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
        String block = current.toString().trim();
        if (!block.isBlank()) {
            blocks.add(block);
        }
        current.setLength(0);
    }

    private boolean isTableBlock(String block) {
        String[] lines = block.split("\\r?\\n");
        int tableLines = 0;
        for (String line : lines) {
            if (isTableLine(line.trim())) {
                tableLines++;
            }
        }
        return tableLines >= 2;
    }

    private boolean isTableLine(String trimmed) {
        return trimmed.startsWith("|") && trimmed.indexOf('|', 1) >= 0;
    }

    private boolean isTableSeparator(String line) {
        String trimmed = line.trim();
        if (!isTableLine(trimmed)) {
            return false;
        }
        String withoutPipes = trimmed.replace("|", "").replace(":", "").replace("-", "").trim();
        return withoutPipes.isEmpty() && trimmed.contains("---");
    }
}
