package com.wong.collector.application.task;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.wong.collector.domain.task.model.TaskRecord;
import com.wong.collector.domain.task.repository.TaskRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 应用启动后恢复尚未完成的任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskRecoveryRunner implements ApplicationRunner {

    private final TaskRepository taskRepository;
    private final TaskOrchestrator taskOrchestrator;

    @Override
    public void run(ApplicationArguments args) {
        List<TaskRecord> recoverable = taskRepository.findRecoverable(200);
        if (recoverable.isEmpty()) {
            return;
        }
        log.info("recover {} task(s) on startup", recoverable.size());
        recoverable.forEach(record -> taskOrchestrator.recover(record.getId()));
    }
}
