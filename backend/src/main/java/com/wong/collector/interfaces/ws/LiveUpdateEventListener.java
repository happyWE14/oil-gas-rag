package com.wong.collector.interfaces.ws;

import com.wong.collector.domain.paper.event.PaperStateChangedEvent;
import com.wong.collector.domain.task.event.TaskStatusChangedEvent;
import com.wong.collector.interfaces.ws.dto.PaperStateChangePayload;
import com.wong.collector.interfaces.ws.dto.TaskStatusChangePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 订阅领域事件并推送至 WebSocket 客户端。
 */
@Component
@RequiredArgsConstructor
public class LiveUpdateEventListener {

    private final LiveUpdateBroadcaster broadcaster;

    @EventListener
    public void onPaperStateChanged(PaperStateChangedEvent event) {
        PaperStateChangePayload payload = PaperStateChangePayload.builder()
            .paperId(event.getPaperId() == null ? null : event.getPaperId().getValue())
            .from(event.getFrom())
            .to(event.getTo())
            .current(event.getState())
            .occurredOn(event.occurredOn())
            .build();
        broadcaster.broadcast(LiveUpdateMessage.of(event.eventName(), payload, event.occurredOn()));
    }

    @EventListener
    public void onTaskStatusChanged(TaskStatusChangedEvent event) {
        TaskStatusChangePayload payload = TaskStatusChangePayload.builder()
            .taskId(event.getTaskId() == null ? null : event.getTaskId().getValue())
            .taskType(event.getTaskType())
            .from(event.getFrom())
            .to(event.getTo())
            .taskName(event.getTaskName())
            .paperId(event.getPaperId())
            .occurredOn(event.occurredOn())
            .build();
        broadcaster.broadcast(LiveUpdateMessage.of(event.eventName(), payload, event.occurredOn()));
    }
}
