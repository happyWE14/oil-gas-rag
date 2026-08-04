package com.wong.collector.domain.paper.model;

import com.wong.collector.domain.common.exception.DomainException;

import lombok.Getter;

/**
 * 材料提取结果。
 */
@Getter
public class MaterialExtraction {

    private final String material;
    private final String property;
    private final String method;
    private final double confidence;
    /**
     * 材料属性的结构化JSON，保留完整细节。
     */
    private final String detailJson;

    private MaterialExtraction(String material, String property,
                               String method, double confidence, String detailJson) {
        if (material == null || material.isBlank()) {
            throw new DomainException("Material name required");
        }
        this.material = material;
        this.property = property;
        this.method = method;
        this.confidence = confidence;
        this.detailJson = detailJson;
    }

    public static MaterialExtraction of(String material, String property,
                                        String method, double confidence, String detailJson) {
        return new MaterialExtraction(material, property, method, confidence, detailJson);
    }

    public String getDetailJson() {
        return detailJson;
    }
}
