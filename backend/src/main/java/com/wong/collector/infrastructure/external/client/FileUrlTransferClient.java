package com.wong.collector.infrastructure.external.client;

import com.dtflys.forest.annotation.BaseRequest;
import com.dtflys.forest.annotation.Get;
import com.dtflys.forest.annotation.Query;
import com.dtflys.forest.http.ForestResponse;
import com.wong.collector.infrastructure.external.download.dto.TokenResponse;

@BaseRequest(baseURL = "${DOWNLOAD_ACCELERATOR_BASE_URL}")
public interface FileUrlTransferClient {

    @Get("/get_download_token")
    ForestResponse<TokenResponse> getDownloadToken(@Query("url") String url);
}
