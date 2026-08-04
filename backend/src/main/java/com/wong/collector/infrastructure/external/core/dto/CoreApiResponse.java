package com.wong.collector.infrastructure.external.core.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class CoreApiResponse {

    private List<PaperInfo> results;

    @JsonProperty("totalHits")
    private Long totalCount;

    private Integer limit;
    private Integer offset;
}
