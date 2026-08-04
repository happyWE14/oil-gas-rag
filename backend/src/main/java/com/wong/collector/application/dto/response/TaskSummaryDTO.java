package com.wong.collector.application.dto.response;

import java.util.Map;

import lombok.Builder;
import lombok.Value;

/**
 * 任务执行概览。
 */
@Value
@Builder
public class TaskSummaryDTO {
    Map<String, Long> waiting;
    Map<String, Long> running;
    Map<String, Long> failed;
    Map<String, Long> succeed;
    Map<String, Long> canceled;
    Map<String, Long> backlogThreshold;
}
