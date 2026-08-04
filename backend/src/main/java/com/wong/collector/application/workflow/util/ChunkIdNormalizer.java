package com.wong.collector.application.workflow.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalize model-returned chunk id tokens into plain DB `document_chunk.id` strings.
 * <p>
 * Accepts formats like: {@code [ChunkId-123]}, {@code ChunkId-123}, {@code 123}.
 */
public final class ChunkIdNormalizer {

    private static final Pattern CHUNK_ID_TOKEN = Pattern.compile("(?i)chunkid-(\\d+)");
    private static final Pattern DIGITS_ONLY = Pattern.compile("^\\d+$");

    private ChunkIdNormalizer() {
    }

    public static String normalize(String token) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        Matcher matcher = CHUNK_ID_TOKEN.matcher(trimmed);
        if (matcher.find()) {
            return Long.toString(Long.parseLong(matcher.group(1)));
        }
        if (DIGITS_ONLY.matcher(trimmed).matches()) {
            return Long.toString(Long.parseLong(trimmed));
        }
        return null;
    }
}
