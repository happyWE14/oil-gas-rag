package com.wong.collector.interfaces.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 简单的文本 WebSocket 处理器，支持心跳和推送。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveUpdateWebSocketHandler extends TextWebSocketHandler {

    private final LiveUpdateBroadcaster broadcaster;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        broadcaster.register(session);
        broadcaster.send(session, LiveUpdateMessage.system("ready"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if ("ping".equalsIgnoreCase(payload.trim())) {
            broadcaster.send(session, LiveUpdateMessage.pong());
            return;
        }
        log.debug("Ignore inbound WS message: {}", payload);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        broadcaster.remove(session);
    }
}
