package com.wong.collector.interfaces.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 负责维护 WebSocket 会话并广播实时事件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveUpdateBroadcaster {

    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final int sendTimeLimitMs = 5000;
    private final int bufferSizeLimitBytes = 64 * 1024;

    public void register(WebSocketSession session) {
        if (session == null) {
            return;
        }
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, sendTimeLimitMs, bufferSizeLimitBytes));
        log.info("WebSocket connected, total sessions: {}", sessions.size());
    }

    public void remove(WebSocketSession session) {
        if (session != null) {
            sessions.remove(session.getId());
            log.info("WebSocket disconnected, total sessions: {}", sessions.size());
        }
    }

    public void send(WebSocketSession session, LiveUpdateMessage message) {
        if (session == null || message == null) {
            return;
        }
        WebSocketSession target = sessions.get(session.getId());
        if (target == null) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize live message: {}", e.getMessage());
            return;
        }
        try {
            target.sendMessage(new TextMessage(payload));
        } catch (Exception ex) {
            handleSendFailure(session.getId(), ex);
        }
    }

    public void broadcast(LiveUpdateMessage message) {
        if (sessions.isEmpty() || message == null) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize live message: {}", e.getMessage());
            return;
        }
        sessions.entrySet().removeIf(entry -> entry.getValue() == null || !entry.getValue().isOpen());
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            String sessionId = entry.getKey();
            WebSocketSession session = entry.getValue();
            if (session == null || !session.isOpen()) {
                sessions.remove(sessionId);
                continue;
            }
            try {
                // Avoid sharing a single message instance across sessions.
                // Some WebSocket implementations may mutate message buffers during writes.
                session.sendMessage(new TextMessage(payload));
            } catch (Exception ex) {
                handleSendFailure(sessionId, ex);
            }
        }
    }

    private void handleSendFailure(String sessionId, Exception ex) {
        // WebSocketSession is not thread-safe for concurrent sends. Under load this can surface as
        // "The remote endpoint was in state [TEXT_PARTIAL_WRITING] ...". Always isolate failures.
        log.warn("Send live message failed, removing session {}: {}", sessionId, ex.getMessage());
        WebSocketSession session = sessions.remove(sessionId);
        if (session == null) {
            return;
        }
        try {
            session.close();
        } catch (IOException closeEx) {
            log.debug("Close session failed: {}", closeEx.getMessage());
        }
    }
}
