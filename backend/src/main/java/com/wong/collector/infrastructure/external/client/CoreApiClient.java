package com.wong.collector.infrastructure.external.client;

import com.dtflys.forest.annotation.BaseRequest;
import com.dtflys.forest.annotation.Get;
import com.dtflys.forest.annotation.GetRequest;
import com.dtflys.forest.annotation.Header;
import com.dtflys.forest.annotation.Var;
import com.dtflys.forest.annotation.Query;
import com.dtflys.forest.http.ForestResponse;

import java.io.InputStream;

/**
 * Core API 客户端接口
 * 使用 Forest 框架进行 HTTP 请求
 */
@BaseRequest(baseURL = "${CORE_API_BASE_URL}")
public interface CoreApiClient {

    @Get(url = "/search/works/")
    ForestResponse<com.wong.collector.infrastructure.external.core.dto.CoreApiResponse> searchWorks(
        @Header("Authorization") String authorization,
        @Query("q") String query,
        @Query("limit") Integer limit,
        @Query("offset") Integer offset,
        @Query("sort") String sort
    );

    @Get(url = "/works/{workId}")
    ForestResponse<com.wong.collector.infrastructure.external.core.dto.PaperInfo> getWorkById(
        @Header("Authorization") String authorization,
        @Var("workId") String workId
    );

    @GetRequest(url = "${downloadUrl}")
    InputStream downloadPaper(@Var("downloadUrl") String downloadUrl);

    @GetRequest(url = "${downloadUrl}")
    ForestResponse<InputStream> downloadPaperResponse(@Var("downloadUrl") String downloadUrl);
}

