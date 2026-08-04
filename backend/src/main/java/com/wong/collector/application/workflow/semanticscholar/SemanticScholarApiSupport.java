package com.wong.collector.application.workflow.semanticscholar;

import java.time.Duration;

import com.dtflys.forest.http.ForestResponse;
import com.wong.collector.application.task.NonRetryableTaskException;
import com.wong.collector.application.task.RetryAfterSupport;
import com.wong.collector.application.task.RetryableTaskException;

public final class SemanticScholarApiSupport {

    private SemanticScholarApiSupport() {
    }

    public static void assertSuccess(ForestResponse<?> resp) {
        if (resp == null) {
            throw new RetryableTaskException("Semantic Scholar 响应为空（可能是网络问题）", Duration.ofSeconds(2));
        }
        if (resp.isSuccess()) {
            return;
        }
        int statusCode = resp.getStatusCode();
        Duration retryAfter = RetryAfterSupport.parse(resp.getHeaderValue("Retry-After"));
        if (statusCode == 401 || statusCode == 403) {
            throw new NonRetryableTaskException("Semantic Scholar 鉴权失败（HTTP " + statusCode + "）：请检查 x-api-key 是否有效");
        }
        if (statusCode == 400) {
            throw new NonRetryableTaskException("Semantic Scholar 参数错误（HTTP 400）：请检查 query/fields/filters 是否符合 swagger.json");
        }
        if (statusCode == 404) {
            throw new NonRetryableTaskException("Semantic Scholar 资源不存在（HTTP 404）");
        }
        if (statusCode == 408) {
            throw new RetryableTaskException("Semantic Scholar 请求超时（HTTP 408）", retryAfter);
        }
        if (statusCode == 429) {
            throw new RetryableTaskException("Semantic Scholar 调用被限流（HTTP 429）", retryAfter);
        }
        if (statusCode == 0 || statusCode >= 500) {
            throw new RetryableTaskException("Semantic Scholar 上游异常（HTTP " + statusCode + "）", retryAfter);
        }
        if (statusCode >= 400) {
            throw new NonRetryableTaskException("Semantic Scholar 请求失败（HTTP " + statusCode + "）");
        }
        throw new RetryableTaskException("Semantic Scholar HTTP失败（HTTP " + statusCode + "）", retryAfter);
    }
}
