package com.wong.collector.domain.paper.repository;

import com.wong.collector.domain.paper.model.PaperState;

/**
 * Paper 列表查询对象（预留多条件过滤扩展点）。
 * <p>
 * 当前仅支持：
 * <ul>
 *   <li>state：可选；为空表示全状态。</li>
 *   <li>page/size：分页参数（page 为 1-based）。</li>
 * </ul>
 * 后续需要新增过滤条件时，优先扩展此类与仓储实现，避免层层改方法签名。
 */
public record PaperQuery(PaperState state, int page, int size) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 200;

    public PaperQuery {
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

