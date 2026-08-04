package com.wong.collector.infrastructure.telemetry;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.wong.collector.domain.paper.event.PaperStateChangedEvent;
import com.wong.collector.domain.paper.model.PaperState;
import com.wong.collector.domain.task.model.TaskRecord;
import com.wong.collector.domain.task.repository.TaskRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 领域事件 -> Telemetry 事件桥接，不影响主链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaperStateTelemetryBridge {

    private final TelemetryRecorder telemetryRecorder;
    private final TaskRepository taskRepository;

    @EventListener
    public void onPaperStateChanged(PaperStateChangedEvent event) {
        String paperId = event.getPaperId() == null ? null : event.getPaperId().getValue();
        if (paperId == null) {
            return;
        }
        TaskRecord recentTask = findRecentTask(paperId);
        String operator = recentTask != null && recentTask.getCreatedBy() != null
            ? recentTask.getCreatedBy() : "system";
        String reason = deriveReason(event, recentTask);
        Long taskId = recentTask != null && recentTask.getId() != null
            ? recentTask.getId().getValue() : null;
        try {
            telemetryRecorder.recordPaperState(
                paperId,
                event.getFrom().name(),
                event.getTo().name(),
                operator,
                reason,
                event.occurredOn(),
                taskId
            );
        } catch (Exception ex) {
            log.debug("record paper state telemetry failed, paperId={}", paperId, ex);
        }
    }

    private TaskRecord findRecentTask(String paperId) {
        return taskRepository.findRecentByPaperId(paperId, 1).stream().findFirst().orElse(null);
    }

    private String deriveReason(PaperStateChangedEvent event, TaskRecord task) {
        if (task == null) {
            return null;
        }
        if (event.getTo() == PaperState.FAILED && task.getErrorMessage() != null) {
            return task.getErrorMessage();
        }
        return task.getDescription();
    }
}

