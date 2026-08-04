package com.wong.collector.domain.material.repository;

import java.util.Locale;

/**
 * Material->papers paged query.
 */
public record MaterialPaperQuery(
    String materialKey,
    Double minConfidence,
    boolean includeChunks,
    int chunkLimit,
    int page,
    int size
) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 200;

    public static final int DEFAULT_CHUNK_LIMIT = 3;
    public static final int MAX_CHUNK_LIMIT = 50;

    public MaterialPaperQuery {
        materialKey = normalizeKey(materialKey);
        if (materialKey == null) {
            throw new IllegalArgumentException("material is required");
        }
        if (minConfidence != null && minConfidence < 0) {
            throw new IllegalArgumentException("minConfidence must be >= 0");
        }
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
        if (chunkLimit < 1 || chunkLimit > MAX_CHUNK_LIMIT) {
            throw new IllegalArgumentException("chunkLimit must be between 1 and " + MAX_CHUNK_LIMIT);
        }
    }

    /**
     * 计算 offset（0-based），用于 SQL 的 limit/offset。
     */
    public long offset() {
        return (long) (page - 1) * (long) size;
    }

    private static String normalizeKey(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}

