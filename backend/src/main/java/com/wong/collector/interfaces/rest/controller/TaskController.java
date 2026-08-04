package com.wong.collector.interfaces.rest.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wong.collector.application.dto.request.CreateTaskRequest;
import com.wong.collector.application.dto.request.TaskStatusUpdateRequest;
import com.wong.collector.application.dto.response.TaskDTO;
import com.wong.collector.application.dto.response.TaskRetryResultDTO;
import com.wong.collector.application.dto.response.TaskSummaryDTO;
import com.wong.collector.application.service.TaskApplicationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * 任务跟踪接口。
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Validated
public class TaskController {

    private final TaskApplicationService taskApplicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDTO createTask(@Valid @RequestBody CreateTaskRequest request) {
        return taskApplicationService.createTask(request);
    }

    @PutMapping("/{id}/status")
    public TaskDTO updateStatus(@PathVariable Long id,
                                @Valid @RequestBody TaskStatusUpdateRequest request) {
        return taskApplicationService.updateStatus(id, request);
    }

    @GetMapping("/{id}")
    public TaskDTO getTask(@PathVariable Long id) {
        return taskApplicationService.getTask(id);
    }

    @GetMapping
    public List<TaskDTO> listTasks(@RequestParam(required = false) String status,
                                   @RequestParam(required = false) String paperId,
                                   @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit) {
        return taskApplicationService.listTasks(status, paperId, limit);
    }

    @GetMapping("/summary")
    public TaskSummaryDTO summary() {
        return taskApplicationService.summary();
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskRetryResultDTO retry(@PathVariable Long id,
                                   @RequestParam(defaultValue = "3") @Min(1) @Max(100) int extraAttempts) {
        return taskApplicationService.retry(id, extraAttempts);
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void cancel(@PathVariable Long id,
                       @RequestBody(required = false) TaskStatusUpdateRequest request) {
        taskApplicationService.cancel(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable Long id) {
        taskApplicationService.deleteTask(id);
    }
}
