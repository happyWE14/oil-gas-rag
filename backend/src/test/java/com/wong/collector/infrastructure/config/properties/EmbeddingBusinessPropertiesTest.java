package com.wong.collector.infrastructure.config.properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EmbeddingBusinessPropertiesTest {

    @Test
    void rejectsVectorsThatDoNotMatchTheConfiguredDimension() {
        EmbeddingBusinessProperties properties = new EmbeddingBusinessProperties();
        properties.setDimensions(3);

        assertDoesNotThrow(() -> properties.requireExpectedDimensions(new float[] {1, 2, 3}));
        assertThrows(IllegalStateException.class,
            () -> properties.requireExpectedDimensions(new float[] {1, 2}));
    }
}
