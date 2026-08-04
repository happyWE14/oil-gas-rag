package com.wong.collector.interfaces.rest.controller;

import com.wong.collector.application.dto.request.DeepResearchRequest;
import com.wong.collector.application.dto.request.KeywordSearchRequest;
import com.wong.collector.application.dto.request.MaterialSearchRequest;
import com.wong.collector.application.dto.response.DeepResearchCreated;
import com.wong.collector.application.dto.response.KeywordSearchResponse;
import com.wong.collector.application.dto.response.MaterialSearchResponse;
import com.wong.collector.application.dto.search.ResearchSession;
import com.wong.collector.application.service.search.DeepResearchService;
import com.wong.collector.application.service.search.KeywordSearchService;
import com.wong.collector.application.service.search.MaterialSearchApplicationService;
import com.wong.collector.application.service.search.MaterialSearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Validated
public class SearchController {

    private final KeywordSearchService keywordSearchService;
    private final DeepResearchService deepResearchService;
    private final MaterialSearchApplicationService materialSearchService;

    // 原有方法保持不变...
    @PostMapping("/keyword")
    public KeywordSearchResponse keywordSearch(@RequestBody @Valid KeywordSearchRequest request) {
        return keywordSearchService.search(request);
    }

    @PostMapping("/deep-research")
    public DeepResearchCreated createDeepResearch(@RequestBody @Valid DeepResearchRequest request) {
        ResearchSession session = deepResearchService.create(request.query());
        return new DeepResearchCreated(session.id(), "/api/search/deep-research/" + session.id() + "/stream");
    }

    @GetMapping(value = "/deep-research/{researchId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDeepResearch(@PathVariable String researchId) {
        SseEmitter emitter = new SseEmitter(300_000L);

        ResearchSession session = deepResearchService.getSession(researchId);
        if (session == null) {
            emitter.completeWithError(new IllegalArgumentException("Research session not found: " + researchId));
            return emitter;
        }

        emitter.onCompletion(() -> {});
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitter.complete());

        CompletableFuture.runAsync(() -> deepResearchService.execute(researchId, emitter));

        return emitter;
    }

    // ==================== 新增 ES 材料搜索接口 ====================

    /**
     * 材料高级搜索（支持嵌套指标查询）
     */
    @PostMapping("/materials")
    public ResponseEntity<Map<String, Object>> searchMaterials(
            @RequestBody @Valid MaterialSearchRequest request) {

        Page<MaterialSearchResponse> page = materialSearchService.searchMaterials(request);

        // 手动组装，避免 PageImpl 警告
        Map<String, Object> result = new HashMap<>();
        result.put("content", page.getContent());
        result.put("totalElements", page.getTotalElements());
        result.put("totalPages", page.getTotalPages());
        result.put("size", page.getSize());
        result.put("number", page.getNumber());

        return ResponseEntity.ok(result);
    }

    /**
     * 关键词自动补全（IK分词）
     */
    @GetMapping("/materials/suggest")
    public ResponseEntity<java.util.List<String>> suggestKeywords(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(materialSearchService.suggest(keyword, size));
    }

    /**
     * 根据论文ID同步材料到ES（手动触发）
     */
    @PostMapping("/materials/sync/{paperId}")
    public ResponseEntity<Void> syncPaperMaterials(@PathVariable String paperId) {
        materialSearchService.syncByPaperId(paperId);
        return ResponseEntity.accepted().build();
    }

    /**
     * 全量同步所有材料到ES（异步触发）
     */
    @PostMapping("/materials/sync-all")
    public ResponseEntity<Map<String, Object>> syncAllMaterials() {
        // 异步执行避免超时
        CompletableFuture.runAsync(() -> materialSearchService.syncAllMaterials());

        Map<String, Object> result = new HashMap<>();
        result.put("message", "全量同步任务已启动");
        result.put("status", "PROCESSING");
        result.put("timestamp", LocalDateTime.now());
        return ResponseEntity.accepted().body(result);
    }
}
