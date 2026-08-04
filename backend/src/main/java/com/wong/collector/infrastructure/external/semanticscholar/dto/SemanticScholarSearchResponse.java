package com.wong.collector.infrastructure.external.semanticscholar.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Semantic Scholar 搜索返回体。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SemanticScholarSearchResponse {

    /**
     * 总命中数。
     */
    private Long total;

    /**
     * 当前 offset。
     */
    private Integer offset;

    /**
     * 下一页 offset（无更多时为 null）。
     */
    private Integer next;

    /**
     * 论文列表。
     */
    private List<SemanticScholarPaperInfo> data;
}
