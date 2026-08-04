package com.wong.collector.infrastructure.external.core;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 将单个字符串或数组统一反序列化为 List。
 */
public class FlexibleListDeserializer extends JsonDeserializer<List<String>> {

    @Override
    public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.getCurrentToken();
        if (token == JsonToken.START_ARRAY) {
            List<String> values = new ArrayList<>();
            while (p.nextToken() != JsonToken.END_ARRAY) {
                if (p.getCurrentToken() == JsonToken.VALUE_STRING) {
                    values.add(p.getValueAsString());
                }
            }
            return values;
        }
        if (token == JsonToken.VALUE_STRING) {
            return Collections.singletonList(p.getValueAsString());
        }
        return new ArrayList<>();
    }
}
