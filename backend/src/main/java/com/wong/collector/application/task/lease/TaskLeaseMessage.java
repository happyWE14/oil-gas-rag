package com.wong.collector.application.task.lease;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskLeaseMessage {
    private Long taskId;
    private long startedAtEpochMilli;
}
