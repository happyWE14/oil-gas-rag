package com.wong.collector.application.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MaterialSummaryDTO {

    String materialKey;
    String material;
    Long paperCount;
    Long occurrenceCount;
    Double maxConfidence;
}

