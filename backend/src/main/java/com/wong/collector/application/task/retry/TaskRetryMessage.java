package com.wong.collector.application.task.retry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskRetryMessage {
    private Long taskId;
    private int attempt;
}
