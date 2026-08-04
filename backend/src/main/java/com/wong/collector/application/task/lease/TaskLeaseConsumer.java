package com.wong.collector.application.task.lease;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.wong.collector.application.task.scheduler.LocalRetryExecutor;
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
    topic = RocketMqTaskDeferredScheduler.LEASE_TOPIC,
    consumerGroup = "task-lease-consumer"
)
public class TaskLeaseConsumer implements RocketMQListener<TaskLeaseMessage> {

    private final LocalRetryExecutor localRetryExecutor;

    @Override
    public void onMessage(TaskLeaseMessage message) {
        if (!isValidMessage(message)) {
            log.error("Drop invalid lease message: {}", message);
            return;
        }
        
        log.debug("Received lease check message: taskId={} startedAt={}", 
            message.getTaskId(), message.getStartedAtEpochMilli());
        localRetryExecutor.executeLeaseCheck(TaskId.of(message.getTaskId()), message.getStartedAtEpochMilli());
    }
    
    private boolean isValidMessage(TaskLeaseMessage message) {
        if (message == null) {
            return false;
        }
        if (message.getTaskId() == null || message.getTaskId() <= 0) {
            return false;
        }
        if (message.getStartedAtEpochMilli() <= 0) {
            return false;
        }
        return true;
    }
}
