package com.wong.collector.interfaces.rest.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wong.collector.application.dto.request.CreatePaperRequest;
import com.wong.collector.application.dto.request.DownloadCompletedRequest;
import com.wong.collector.application.dto.request.EmbeddingCompletedRequest;
import com.wong.collector.application.dto.request.MaterialExtractionRequest;
import com.wong.collector.application.dto.request.PreAnalysisResultRequest;
import com.wong.collector.application.dto.request.TransformCompletedRequest;
import com.wong.collector.application.dto.response.PaperDTO;
import com.wong.collector.application.dto.response.PaperDetailDTO;
import com.wong.collector.application.dto.response.PaperStateEventDTO;
import com.wong.collector.application.service.PaperApplicationService;
import com.wong.collector.application.task.TaskOrchestrator;
import com.wong.collector.domain.common.model.PageResult;
import com.wong.collector.domain.paper.model.PaperState;
import com.wong.collector.domain.paper.model.TransformProvider;
import com.wong.collector.domain.paper.repository.PaperQuery;
import com.wong.collector.domain.task.model.TaskType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/papers")
@RequiredArgsConstructor
@Validated
public class PaperController {

    private final PaperApplicationService paperApplicationService;
    private final TaskOrchestrator taskOrchestrator;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaperDetailDTO createPaper(@Valid @RequestBody CreatePaperRequest request) {
        return paperApplicationService.createPaper(request);
    }

    @GetMapping("/{paperId}")
    public PaperDetailDTO getPaper(@PathVariable String paperId,
                                   @RequestParam(name = "includeChunks", defaultValue = "false") boolean includeChunks) {
        return paperApplicationService.getPaperDetail(paperId, includeChunks);
    }

    /**
     * 通过 DOI 查询论文详情。
     * <p>
     * DOI 本身包含 "/"，不适合作为 path variable；使用 query param 避免歧义与路由问题。
     */
    @GetMapping("/by-doi")
    public PaperDetailDTO getPaperByDoi(@RequestParam String doi,
                                        @RequestParam(name = "includeChunks", defaultValue = "false") boolean includeChunks) {
        return paperApplicationService.getPaperDetailByDoi(doi, includeChunks);
    }

    @GetMapping
    public List<PaperDTO> listByState(@RequestParam PaperState state,
                                      @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limit) {
        return paperApplicationService.listByState(state, limit);
    }

    /**
     * 分页查询论文列表（预留多条件过滤扩展点）。
     * <p>
     * 说明：page 为 1-based；state 可选（为空表示全状态）。
     */
    @GetMapping("/page")
    public PageResult<PaperDTO> page(@RequestParam(required = false) PaperState state,
                                     @RequestParam(defaultValue = "1") @Min(1) int page,
                                     @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return paperApplicationService.pagePapers(new PaperQuery(state, page, size));
    }

    @GetMapping("/{paperId}/events")
    public List<PaperStateEventDTO> listEvents(@PathVariable String paperId,
                                               @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return paperApplicationService.listStateEvents(paperId, limit);
    }

    @PostMapping("/{paperId}/pre-analysis/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completePreAnalysis(@PathVariable String paperId,
                                    @Valid @RequestBody PreAnalysisResultRequest request) {
        paperApplicationService.completePreAnalysis(paperId, request);
    }

    /**
     * 手动触发预分析任务。
     * <p>
     * 该接口会创建预分析任务并异步执行，包括 LLM 相关性分析及后续流程。
     */
    @PostMapping("/{paperId}/pre-analysis/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void startPreAnalysis(@PathVariable String paperId) {
        // 先校验论文存在性
        paperApplicationService.getPaperDetail(paperId, false);
        // 通过任务调度器创建预分析任务
        taskOrchestrator.schedule(TaskType.PAPER_PRE_ANALYSIS, paperId, "手动触发预分析", null);
    }

    @PostMapping("/{paperId}/download/start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void startDownload(@PathVariable String paperId) {
        paperApplicationService.startDownload(paperId);
    }

    @PostMapping("/{paperId}/download/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeDownload(@PathVariable String paperId,
                                 @Valid @RequestBody DownloadCompletedRequest request) {
        paperApplicationService.completeDownload(paperId, request);
    }

    @PostMapping("/{paperId}/transform/start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void startTransform(@PathVariable String paperId,
                               @RequestParam TransformProvider provider) {
        paperApplicationService.startTransform(paperId, provider);
    }

    @PostMapping("/{paperId}/transform/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeTransform(@PathVariable String paperId,
                                  @Valid @RequestBody TransformCompletedRequest request) {
        paperApplicationService.completeTransform(paperId, request);
    }

    @PostMapping("/{paperId}/embedding/start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void startEmbedding(@PathVariable String paperId) {
        paperApplicationService.startEmbedding(paperId);
    }

    @PostMapping("/{paperId}/embedding/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeEmbedding(@PathVariable String paperId,
                                  @Valid @RequestBody EmbeddingCompletedRequest request) {
        paperApplicationService.completeEmbedding(paperId, request);
    }

    @PostMapping("/{paperId}/extraction/start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void startExtraction(@PathVariable String paperId) {
        paperApplicationService.startExtraction(paperId);
    }

    @PostMapping("/{paperId}/extraction/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeExtraction(@PathVariable String paperId,
                                   @Valid @RequestBody MaterialExtractionRequest request) {
        paperApplicationService.completeExtraction(paperId, request);
    }
}
