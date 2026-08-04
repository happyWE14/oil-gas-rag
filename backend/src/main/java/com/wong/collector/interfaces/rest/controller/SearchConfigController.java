package com.wong.collector.interfaces.rest.controller;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import com.wong.collector.application.dto.request.CreateSearchConfigRequest;
import com.wong.collector.application.dto.request.UpdateSearchConfigRequest;
import com.wong.collector.application.dto.response.SearchConfigDTO;
import com.wong.collector.application.service.SearchApplicationService;
import com.wong.collector.application.task.TaskOrchestrator;
import com.wong.collector.domain.task.model.TaskType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 搜索配置管理接口。
 */
@RestController
@RequestMapping("/api/search-configs")
@RequiredArgsConstructor
@Validated
public class SearchConfigController {

    private final SearchApplicationService searchApplicationService;
    private final TaskOrchestrator taskOrchestrator;
    private final ObjectMapper objectMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SearchConfigDTO create(@Valid @RequestBody CreateSearchConfigRequest request) {
        return searchApplicationService.createConfig(request);
    }

    @PutMapping("/{id}")
    public SearchConfigDTO update(@PathVariable Long id,
                                  @Valid @RequestBody UpdateSearchConfigRequest request) {
        return searchApplicationService.updateConfig(id, request);
    }

    @GetMapping
    public List<SearchConfigDTO> list(@RequestParam(required = false) String status) {
        return searchApplicationService.listConfigs(status);
    }

    @GetMapping("/{id}")
    public SearchConfigDTO detail(@PathVariable Long id) {
        return searchApplicationService.getConfig(id);
    }

    @GetMapping("/{id}/stats")
    public SearchConfigDTO stats(@PathVariable Long id) {
        return searchApplicationService.getConfigStats(id);
    }

    @PostMapping("/{id}/pause")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pause(@PathVariable Long id) {
        searchApplicationService.pause(id);
    }

    @PostMapping("/{id}/resume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resume(@PathVariable Long id) {
        searchApplicationService.resume(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        searchApplicationService.deleteConfig(id);
    }

    @PostMapping("/{id}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void trigger(@PathVariable Long id) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of("configId", id));
        taskOrchestrator.schedule(TaskType.PAPER_SEARCH, null, "搜索配置-" + id, payload);
    }
}
