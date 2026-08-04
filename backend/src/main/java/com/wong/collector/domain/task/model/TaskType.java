package com.wong.collector.domain.task.model;

import java.time.Duration;

/**
 * 任务类型。
 */
public enum TaskType {
    PAPER_SEARCH(TaskExecutionProfile.parallel("taskWorkerExecutor", 2, Duration.ofMinutes(10))),
    PAPER_PRE_ANALYSIS(TaskExecutionProfile.parallel("aiParallelExecutor", 10, Duration.ofMinutes(20))),
    PAPER_DOWNLOAD(TaskExecutionProfile.parallel("taskIoExecutor", 4, Duration.ofMinutes(10))),
    // OCR is remote API based; allow safe parallelism across papers.
    PAPER_TRANSFORM(TaskExecutionProfile.parallel("taskIoExecutor", 3, Duration.ofMinutes(45))),
    PAPER_RELATION_EXPAND(TaskExecutionProfile.serial("taskIoExecutor", Duration.ofMinutes(10))),
    PAPER_EMBEDDING(TaskExecutionProfile.parallel("aiParallelExecutor", 15, Duration.ofMinutes(20))),
    MATERIAL_EXTRACTION(TaskExecutionProfile.parallel("aiParallelExecutor", 5, Duration.ofMinutes(120))),
    CLEANUP(TaskExecutionProfile.parallel("taskWorkerExecutor", 1, Duration.ofMinutes(10)));

    private final TaskExecutionProfile profile;

    TaskType(TaskExecutionProfile profile) {
        this.profile = profile;
    }

    public TaskExecutionProfile profile() {
        return profile;
    }
}
