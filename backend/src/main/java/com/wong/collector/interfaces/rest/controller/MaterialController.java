package com.wong.collector.interfaces.rest.controller;

import com.wong.collector.application.dto.request.TranslationRequest;
import com.wong.collector.application.dto.response.MaterialGlobalStatsDTO;
import com.wong.collector.application.dto.response.MaterialPaperDTO;
import com.wong.collector.application.dto.response.MaterialSummaryDTO;
import com.wong.collector.application.dto.response.TranslationResponse;
import com.wong.collector.application.service.MaterialApplicationService;
import com.wong.collector.application.service.TranslationService;
import com.wong.collector.domain.common.model.PageResult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
@Validated
public class MaterialController {

    private final MaterialApplicationService materialApplicationService;
    private final TranslationService translationService;

    /**
     * 以材料为单位分页聚合（materialKey = lower(trim(material))）。
     */
    @GetMapping("/page")
    public PageResult<MaterialSummaryDTO> pageMaterials(@RequestParam(defaultValue = "1") @Min(1) int page,
                                                        @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
                                                        @RequestParam(required = false) String q,
                                                        @RequestParam(required = false) Double minConfidence) {
        return materialApplicationService.pageMaterials(q, minConfidence, page, size);
    }

    /**
     * 查看指定材料在各论文中的出现记录（分页）。
     * <p>
     * includeChunks=true 时才会解析 detailJson/source_chunk_ids 并回查 document_chunk。
     */
    @GetMapping("/papers/page")
    public PageResult<MaterialPaperDTO> pageMaterialPapers(@RequestParam String material,
                                                           @RequestParam(defaultValue = "1") @Min(1) int page,
                                                           @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
                                                           @RequestParam(defaultValue = "false") boolean includeChunks,
                                                           @RequestParam(defaultValue = "3") @Min(1) @Max(50) int chunkLimit,
                                                           @RequestParam(required = false) Double minConfidence) {
        return materialApplicationService.pageMaterialPapers(material, minConfidence, includeChunks, chunkLimit, page, size);
    }

    /**
     * 单条翻译接口（支持材料名称、论文标题、证据片段等）
     */
    @PostMapping("/translate")
    public TranslationResponse translate(@RequestBody @Valid TranslationRequest request) {
        return translationService.translate(request);
    }

    /**
     * 批量翻译接口（用于"翻译全部"功能）
     */
    @PostMapping("/translate/batch")
    public List<TranslationResponse> translateBatch(@RequestBody List<TranslationRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        if (requests.size() > 20) {
            throw new IllegalArgumentException("单次批量翻译不能超过20条");
        }
        return translationService.translateBatch(requests);
    }

    /**
     * 清除指定文本的翻译缓存
     */
    @DeleteMapping("/translate/cache")
    public void clearTranslationCache(@RequestParam String text,
                                      @RequestParam TranslationRequest.TranslationType type) {
        translationService.clearCache(text, type);
    }

    /**
     * 获取全局统计数据（材料种类、来源论文数、提取记录数、平均置信度）
     * 来源论文数统计的是 material_extraction 表中不同的 paper_id 数量
     */
    @GetMapping("/stats")
    public MaterialGlobalStatsDTO getGlobalStats() {
        return materialApplicationService.getGlobalStats();
    }
}
