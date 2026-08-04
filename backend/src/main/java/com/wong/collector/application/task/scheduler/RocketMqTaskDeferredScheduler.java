package com.wong.collector.application.task.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import com.wong.collector.application.task.lease.TaskLeaseMessage;
import com.wong.collector.application.task.retry.TaskRetryMessage;
import com.wong.collector.domain.task.model.TaskId;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(name = "paper.event.mode", havingValue = "rocketmq")
@ConditionalOnBean(RocketMQTemplate.class)
public class RocketMqTaskDeferredScheduler implements TaskDeferredScheduler {

    public static final String RETRY_TOPIC = "task-retry";
    public static final String LEASE_TOPIC = "task-lease-check";
    private static final Duration MIN_LEASE_DELAY = Duration.ofSeconds(1);
    
    private final RocketMQTemplate rocketMQTemplate;
    private final TaskScheduler taskScheduler;
    private final LocalRetryExecutor localRetryExecutor;
    
    public RocketMqTaskDeferredScheduler(RocketMQTemplate rocketMQTemplate,
                                          TaskScheduler taskScheduler,
                                          @Lazy LocalRetryExecutor localRetryExecutor) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.taskScheduler = taskScheduler;
        this.localRetryExecutor = localRetryExecutor;
    }

    @Override
    public void scheduleRetry(TaskId taskId, int attempt, Duration delay) {
        Duration safeDelay = normalizeDelay(delay);
        
        if (safeDelay.isZero()) {
            log.debug("Immediate retry via local executor: taskId={} attempt={}", taskId.getValue(), attempt);
            localRetryExecutor.executeRetry(taskId);
            return;
        }
        
        TaskRetryMessage payload = new TaskRetryMessage(taskId.getValue(), attempt);
        String keys = taskId.getValue() + ":" + attempt;
        Message<TaskRetryMessage> message = MessageBuilder.withPayload(payload)
            .setHeader(RocketMQHeaders.KEYS, keys)
            .build();
        
        int delayLevel = computeRetryDelayLevel(safeDelay);
        log.info("Scheduling retry via MQ: taskId={} attempt={} delayLevel={}", taskId.getValue(), attempt, delayLevel);
        
        asyncSendDelayed(RETRY_TOPIC, message, delayLevel)
            .exceptionally(ex -> {
                log.warn("MQ send failed, using local fallback: taskId={}", taskId.getValue(), ex);
                fallbackScheduleRetry(taskId, safeDelay);
                return null;
            });
    }
    
    private void fallbackScheduleRetry(TaskId taskId, Duration delay) {
        try {
            Instant runAt = Instant.now().plus(delay);
            taskScheduler.schedule(() -> localRetryExecutor.executeRetry(taskId), runAt);
        } catch (Exception e) {
            log.error("Fallback schedule retry failed: taskId={}", taskId.getValue(), e);
        }
    }

    @Override
    public void scheduleLeaseCheck(TaskId taskId, long startedAtEpochMilli, Duration delay) {
        Duration safeDelay = normalizeDelay(delay);
        if (safeDelay.compareTo(MIN_LEASE_DELAY) < 0) {
            safeDelay = MIN_LEASE_DELAY;
        }
        
        TaskLeaseMessage payload = new TaskLeaseMessage(taskId.getValue(), startedAtEpochMilli);
        String keys = String.valueOf(taskId.getValue());
        Message<TaskLeaseMessage> message = MessageBuilder.withPayload(payload)
            .setHeader(RocketMQHeaders.KEYS, keys)
            .build();
        
        int delayLevel = computeLeaseDelayLevel(safeDelay);
        log.debug("Scheduling lease check via MQ: taskId={} delayLevel={}", taskId.getValue(), delayLevel);
        
        final Duration finalDelay = safeDelay;
        asyncSendDelayed(LEASE_TOPIC, message, delayLevel)
            .exceptionally(ex -> {
                log.warn("MQ lease check send failed, using local fallback: taskId={}", taskId.getValue(), ex);
                fallbackScheduleLeaseCheck(taskId, startedAtEpochMilli, finalDelay);
                return null;
            });
    }
    
    private void fallbackScheduleLeaseCheck(TaskId taskId, long startedAtEpochMilli, Duration delay) {
        try {
            Instant runAt = Instant.now().plus(delay);
            taskScheduler.schedule(() -> localRetryExecutor.executeLeaseCheck(taskId, startedAtEpochMilli), runAt);
        } catch (Exception e) {
            log.error("Fallback schedule lease check failed: taskId={}", taskId.getValue(), e);
        }
    }
    
    private <T> CompletableFuture<Void> asyncSendDelayed(String topic, Message<T> message, int delayLevel) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            rocketMQTemplate.asyncSend(topic, message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.debug("MQ message sent: topic={} msgId={}", topic, sendResult.getMsgId());
                    future.complete(null);
                }

                @Override
                public void onException(Throwable e) {
                    log.error("MQ asyncSend failed: topic={}", topic, e);
                    future.completeExceptionally(e);
                }
            }, 3000, delayLevel);
        } catch (Exception e) {
            log.error("MQ asyncSend exception: topic={}", topic, e);
            future.completeExceptionally(e);
        }
        return future;
    }
    
    private Duration normalizeDelay(Duration delay) {
        if (delay == null || delay.isNegative()) {
            return Duration.ZERO;
        }
        return delay;
    }
    
    private int computeRetryDelayLevel(Duration delay) {
        if (delay.isZero()) {
            return 0;
        }
        long seconds = delay.getSeconds();
        if (seconds <= 1) return 1;
        if (seconds <= 5) return 2;
        if (seconds <= 10) return 3;
        if (seconds <= 30) return 4;
        if (seconds <= 60) return 5;
        if (seconds <= 120) return 6;
        if (seconds <= 180) return 7;
        if (seconds <= 240) return 8;
        if (seconds <= 300) return 9;
        if (seconds <= 360) return 10;
        if (seconds <= 420) return 11;
        if (seconds <= 480) return 12;
        if (seconds <= 540) return 13;
        if (seconds <= 600) return 14;
        if (seconds <= 1200) return 15;
        if (seconds <= 1800) return 16;
        if (seconds <= 3600) return 17;
        return 18;
    }
    
    private int computeLeaseDelayLevel(Duration timeout) {
        if (timeout == null) {
            return 14;
        }
        long seconds = timeout.getSeconds();
        if (seconds <= 60) return 5;
        if (seconds <= 120) return 6;
        if (seconds <= 180) return 7;
        if (seconds <= 300) return 9;
        if (seconds <= 600) return 14;
        if (seconds <= 1200) return 15;
        if (seconds <= 1800) return 16;
        if (seconds <= 3600) return 17;
        return 18;
    }
    
}
