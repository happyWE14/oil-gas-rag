package com.wong.collector.infrastructure.external.client;

import com.dtflys.forest.annotation.BaseRequest;
import com.dtflys.forest.annotation.Get;
import com.dtflys.forest.annotation.Header;
import com.dtflys.forest.annotation.Query;
import com.dtflys.forest.annotation.Var;
import com.dtflys.forest.http.ForestResponse;
import com.wong.collector.infrastructure.external.semanticscholar.dto.SemanticScholarCitationBatch;
import com.wong.collector.infrastructure.external.semanticscholar.dto.SemanticScholarPaperInfo;
import com.wong.collector.infrastructure.external.semanticscholar.dto.SemanticScholarReferenceBatch;
import com.wong.collector.infrastructure.external.semanticscholar.dto.SemanticScholarSearchResponse;

/**
 * Semantic Scholar Academic Graph API 客户端。
 */
@BaseRequest(baseURL = "${SEMANTIC_SCHOLAR_BASE_URL}")
public interface SemanticScholarApiClient {

    /**
     * 论文搜索接口。
     *
     * @param apiKey  API Key（Header: x-api-key），可为空
     * @param query   查询语句
     * @param limit   单次返回条数
     * @param offset  起始偏移
     * @param fields  需要返回的字段列表（逗号分隔）
     * @param publicationTypes  publicationTypes 过滤（可选）
     * @param minCitationCount  minCitationCount 过滤（可选）
     * @param publicationDateOrYear  publicationDateOrYear 过滤（可选）
     * @param year  year 过滤（可选）
     * @param venue  venue 过滤（可选）
     * @param fieldsOfStudy  fieldsOfStudy 过滤（可选）
     */
    @Get(url = "/paper/search?openAccessPdf")
    ForestResponse<SemanticScholarSearchResponse> searchPapers(
        @Header("x-api-key") String apiKey,
        @Query("query") String query,
        @Query("limit") Integer limit,
        @Query("offset") Integer offset,
        @Query("fields") String fields,
        @Query("publicationTypes") String publicationTypes,
        @Query("minCitationCount") String minCitationCount,
        @Query("publicationDateOrYear") String publicationDateOrYear,
        @Query("year") String year,
        @Query("venue") String venue,
        @Query("fieldsOfStudy") String fieldsOfStudy
    );

    /**
     * 论文搜索接口（不限制 openAccessPdf）。
     */
    @Get(url = "/paper/search")
    ForestResponse<SemanticScholarSearchResponse> searchPapersAll(
        @Header("x-api-key") String apiKey,
        @Query("query") String query,
        @Query("limit") Integer limit,
        @Query("offset") Integer offset,
        @Query("fields") String fields,
        @Query("publicationTypes") String publicationTypes,
        @Query("minCitationCount") String minCitationCount,
        @Query("publicationDateOrYear") String publicationDateOrYear,
        @Query("year") String year,
        @Query("venue") String venue,
        @Query("fieldsOfStudy") String fieldsOfStudy
    );

    /**
     * 获取论文详情。
     *
     * @param paperId 支持 `DOI:<doi>` / `ARXIV:<id>` / Semantic Scholar ID 等（见 swagger.json 说明）。
     */
    @Get(url = "/paper/{paper_id}")
    ForestResponse<SemanticScholarPaperInfo> getPaper(
        @Header("x-api-key") String apiKey,
        @Var("paper_id") String paperId,
        @Query("fields") String fields
    );

    /**
     * 获取论文引用（citations）。
     */
    @Get(url = "/paper/{paper_id}/citations")
    ForestResponse<SemanticScholarCitationBatch> getCitations(
        @Header("x-api-key") String apiKey,
        @Var("paper_id") String paperId,
        @Query("publicationDateOrYear") String publicationDateOrYear,
        @Query("offset") Integer offset,
        @Query("limit") Integer limit,
        @Query("fields") String fields
    );

    /**
     * 获取论文参考文献（references）。
     */
    @Get(url = "/paper/{paper_id}/references")
    ForestResponse<SemanticScholarReferenceBatch> getReferences(
        @Header("x-api-key") String apiKey,
        @Var("paper_id") String paperId,
        @Query("publicationDateOrYear") String publicationDateOrYear,
        @Query("offset") Integer offset,
        @Query("limit") Integer limit,
        @Query("fields") String fields
    );
}
