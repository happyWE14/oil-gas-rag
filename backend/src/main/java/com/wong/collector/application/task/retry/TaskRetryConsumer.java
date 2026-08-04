package com.wong.collector.application.task.retry;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import com.wong.collector.application.task.TaskOrchestrator;
import com.wong.collector.application.task.TaskOrchestrator.DispatchOutcome;
import com.wong.collector.application.task.TaskOrchestrator.DispatchStatus;
import com.wong.collector.application.task.scheduler.RocketMqTaskDeferredScheduler;
import com.wong.collector.domain.task.model.TaskId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "paper.event.mode", havingValue = "rocketmq")
@ConditionalOnBean(RocketMQTemplate.class)
@RocketMQMessageListener(
    topic = RocketMqTaskDeferredScheduler.RETRY_TOPIC,
    consumerGroup = "task-retry-consumer"
)
public class TaskRetryConsumer implements RocketMQListener<TaskRetryMessage> {

    private final TaskOrchestrator taskOrchestrator;

    @Override
    public void onMessage(TaskRetryMessage message) {
        if (!isValidMessage(message)) {
            log.error("Drop invalid retry message: {}", message);
            return;
        }
        
        log.info("Received retry message: taskId={} attempt={}", message.getTaskId(), message.getAttempt());
        try {
            DispatchOutcome outcome = taskOrchestrator.resumeFromRetryMessage(
                TaskId.of(message.getTaskId()), 
                message.getAttempt()
            );
            if (outcome.status() == DispatchStatus.NOOP) {
                log.debug("Retry message acked without action: taskId={} reason={}", 
                    message.getTaskId(), outcome.reason());
            } else if (outcome.status() == DispatchStatus.BLOCKED) {
                log.warn("Task blocked: taskId={} reason={}", message.getTaskId(), outcome.reason());
            }
        } catch (DataAccessException e) {
            log.error("Infrastructure failure during retry, will trigger MQ retry: taskId={}", message.getTaskId(), e);
            throw new RuntimeException("Infrastructure failure, triggering MQ retry", e);
        } catch (RuntimeException e) {
            log.error("Unexpected error during retry, will trigger MQ retry: taskId={}", message.getTaskId(), e);
            throw e;
        }
    }
    
    private boolean isValidMessage(TaskRetryMessage message) {
        if (message == null) {
            return false;
        }
        if (message.getTaskId() == null || message.getTaskId() <= 0) {
            return false;
        }
        if (message.getAttempt() < 0) {
            return false;
        }
        return true;
    }
}
