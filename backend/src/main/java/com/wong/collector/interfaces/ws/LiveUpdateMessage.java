package com.wong.collector.interfaces.ws;

import java.time.Instant;

import lombok.Getter;

/**
 * WebSocket 推送的统一消息格式。
 */
@Getter
public class LiveUpdateMessage {

    private final String type;
    private final Object data;
    private final Instant timestamp;

    private LiveUpdateMessage(String type, Object data, Instant timestamp) {
        this.type = type;
        this.data = data;
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    public static LiveUpdateMessage of(String type, Object data, Instant ts) {
        return new LiveUpdateMessage(type, data, ts);
    }

    public static LiveUpdateMessage system(String message) {
        return new LiveUpdateMessage("system.info", new SystemPayload(message), Instant.now());
    }

    public static LiveUpdateMessage pong() {
        return new LiveUpdateMessage("system.pong", new SystemPayload("pong"), Instant.now());
    }

    private record SystemPayload(String message) {}
}
