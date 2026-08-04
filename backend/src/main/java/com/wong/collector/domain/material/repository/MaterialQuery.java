package com.wong.collector.domain.material.repository;

/**
 * Material summary paged query.
 */
public record MaterialQuery(String q, Double minConfidence, int page, int size) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 200;

    public MaterialQuery {
        if (q != null) {
            q = q.trim();
            if (q.isEmpty()) {
                q = null;
            }
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
    }

    /**
     * 计算 offset（0-based），用于 SQL 的 limit/offset。
     */
    public long offset() {
        return (long) (page - 1) * (long) size;
    }
}

