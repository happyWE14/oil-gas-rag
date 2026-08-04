package com.wong.collector.infrastructure.config;

import java.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.wong.collector.domain.task.model.TaskType;

@Configuration
public class TaskExecutionConfig {

    @Bean
    public ThreadPoolTaskExecutor taskWorkerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("task-worker-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }

    @Bean
    public ThreadPoolTaskExecutor taskIoExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("task-io-");
        executor.setCorePoolSize(6);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(200);
        executor.initialize();
        return executor;
    }

    @Bean
    public ThreadPoolTaskExecutor aiSerialExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("task-ai-");
        int serialAiTypes = (int) Arrays.stream(TaskType.values())
            .map(TaskType::profile)
            .filter(profile -> profile != null
                && profile.maxConcurrency() == 1
                && "aiSerialExecutor".equals(profile.executorBean()))
            .count();
        int poolSize = Math.max(serialAiTypes, 1);
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(20);
        executor.initialize();
        return executor;
    }

    @Bean
    public ThreadPoolTaskExecutor aiParallelExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("task-ai-parallel-");
        executor.setCorePoolSize(15);
        executor.setMaxPoolSize(15);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }

    @Bean
    public TaskScheduler taskRetryScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("task-scheduler-");
        scheduler.initialize();
        return scheduler;
    }
}
