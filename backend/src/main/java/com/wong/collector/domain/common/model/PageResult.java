package com.wong.collector.domain.common.model;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 通用分页返回结构。
 * <p>
 * 说明：
 * <ul>
 *   <li>page 使用 1-based（即第一页 page=1）。</li>
 *   <li>totalPages 按 total/size 计算，保持与 total、size 一致。</li>
 *   <li>items 使用不可变 List，避免被意外修改。</li>
 * </ul>
 */
public record PageResult<T>(
        List<T> items,
        int page,
        int size,
        long total,
        long totalPages) {

    public PageResult {
        items = items == null ? List.of() : List.copyOf(items);
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be >= 1");
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must be >= 0");
        }
        long expected = computeTotalPages(total, size);
        if (totalPages != expected) {
            throw new IllegalArgumentException("totalPages must match total and size");
        }
    }

    public static long computeTotalPages(long total, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be >= 1");
        }
        return total <= 0 ? 0 : (total + size - 1) / size;
    }

    public static <T> PageResult<T> of(List<T> items, int page, int size, long total) {
        return new PageResult<>(items, page, size, total, computeTotalPages(total, size));
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return of(List.of(), page, size, 0);
    }

    /**
     * 映射分页 items（保持 page/size/total 不变）。
     */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        // 显式指定 map 的目标泛型参数，避免 `? extends R` 在链式调用中被推导为捕获类型（导致 List<?> 无法赋值给 List<R>）。
        List<R> mapped = items.stream().<R>map(mapper).toList();
        return PageResult.of(mapped, page, size, total);
    }
}
