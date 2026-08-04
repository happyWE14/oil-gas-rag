package com.wong.collector.infrastructure.external.mineru.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MinerUSubmitRequest {
    private String url;
    private String dataId;
    private String seed;
    private Boolean isOcr;
    private Boolean enableFormula;
    private Boolean enableTable;
    private String modelVersion;
}
