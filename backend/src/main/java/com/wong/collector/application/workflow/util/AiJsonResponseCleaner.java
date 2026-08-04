package com.wong.collector.application.workflow.util;

/**
 * Normalize LLM responses into a clean JSON object string.
 */
public final class AiJsonResponseCleaner {

    private AiJsonResponseCleaner() {
    }

    public static String extractJsonObject(String response) {
        if (response == null) {
            return "";
        }
        String cleaned = response.replaceAll("<think>[\\s\\S]*?</think>", "");
        cleaned = cleaned.replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return cleaned;
    }
}

