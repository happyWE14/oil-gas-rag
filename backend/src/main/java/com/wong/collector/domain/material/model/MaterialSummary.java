package com.wong.collector.domain.material.model;

/**
 * Material summary read model (grouped by materialKey).
 */
public record MaterialSummary(
    String materialKey,
    String material,
    long paperCount,
    long occurrenceCount,
    Double maxConfidence
) {

    public MaterialSummary {
        if (materialKey == null || materialKey.isBlank()) {
            throw new IllegalArgumentException("materialKey is required");
        }
        if (material == null || material.isBlank()) {
            throw new IllegalArgumentException("material is required");
        }
        if (paperCount < 0) {
            throw new IllegalArgumentException("paperCount must be >= 0");
        }
        if (occurrenceCount < 0) {
            throw new IllegalArgumentException("occurrenceCount must be >= 0");
        }
        if (maxConfidence != null && maxConfidence < 0) {
            throw new IllegalArgumentException("maxConfidence must be >= 0");
        }
    }
}

