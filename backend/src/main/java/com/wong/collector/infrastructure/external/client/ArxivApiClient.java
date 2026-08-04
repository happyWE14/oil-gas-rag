package com.wong.collector.infrastructure.external.client;

import com.dtflys.forest.annotation.BaseRequest;
import com.dtflys.forest.annotation.Get;
import com.dtflys.forest.annotation.Query;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Role;

import java.util.List;

/**
 * arXiv 查询接口。
 */
@BaseRequest(baseURL = "${ARXIV_API_BASE_URL}")
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public interface ArxivApiClient {

    @Get(url = "/query")
    String query(@Query("search_query") String searchQuery,
                 @Query("id_list") List<String> idList,
                 @Query("start") Integer start,
                 @Query("max_results") Integer maxResults,
                 @Query("sortBy") String sortBy,
                 @Query("sortOrder") String sortOrder);
}
