package com.wong.collector.application.task;

import java.io.IOException;
import java.time.Duration;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.wong.collector.application.workflow.download.DownloadException;
import com.wong.collector.domain.task.model.FailureReason;
import com.dtflys.forest.exceptions.ForestNetworkException;
import com.dtflys.forest.http.ForestResponse;

@Component
public class TaskFailureClassifier {

    public FailureDecision classify(Throwable throwable) {
        if (throwable == null) {
            return FailureDecision.permanent(FailureReason.UNKNOWN);
        }

        Throwable cursor = throwable;
        for (int depth = 0; depth < 10 && cursor != null; depth++) {
            if (cursor instanceof NonRetryableTaskException) {
                return FailureDecision.permanent(FailureReason.INVALID_REQUEST);
            }
            if (cursor instanceof RetryableTaskException retryable) {
                return FailureDecision.serverError(retryable.getRecommendedDelay());
            }
            if (cursor instanceof DownloadException) {
                return FailureDecision.networkError(null);
            }
            if (cursor instanceof ForestNetworkException forest) {
                Integer status = forest.getStatusCode();
                ForestResponse<?> response = forest.getResponse();
                return classifyHttpStatus(status == null ? 0 : status, RetryAfterSupport.parse(response));
            }
            if (cursor instanceof WebClientResponseException webClient) {
                int status = safeStatusCode(webClient.getStatusCode() == null ? 0 : webClient.getStatusCode().value());
                return classifyHttpStatus(status,
                    RetryAfterSupport.parse(webClient.getHeaders() == null ? null : webClient.getHeaders().getFirst("Retry-After")));
            }
            if (cursor instanceof HttpStatusCodeException httpClient) {
                int status = safeStatusCode(httpClient.getStatusCode() == null ? 0 : httpClient.getStatusCode().value());
                return classifyHttpStatus(status,
                    RetryAfterSupport.parse(httpClient.getResponseHeaders() == null ? null : httpClient.getResponseHeaders().getFirst("Retry-After")));
            }
            if (cursor instanceof OptimisticLockingFailureException) {
                return FailureDecision.concurrencyConflict();
            }
            if (cursor instanceof IOException) {
                return FailureDecision.networkError(Duration.ofSeconds(2));
            }
            cursor = cursor.getCause();
        }

        String name = throwable.getClass().getName().toLowerCase();
        if (name.contains("timeout") || name.contains("connect") || name.contains("socket")) {
            return FailureDecision.networkError(Duration.ofSeconds(2));
        }
        return FailureDecision.permanent(FailureReason.UNKNOWN);
    }

    private FailureDecision classifyHttpStatus(int status, Duration retryAfter) {
        if (status == 0) {
            return FailureDecision.networkError(retryAfter);
        }
        if (status == 429) {
            return FailureDecision.rateLimited(retryAfter);
        }
        if (status == 408 || status >= 500) {
            return FailureDecision.serverError(retryAfter);
        }
        if (status == 401 || status == 403) {
            return FailureDecision.permanent(FailureReason.AUTH_FAILURE);
        }
        if (status == 404) {
            return FailureDecision.permanent(FailureReason.NOT_FOUND);
        }
        return FailureDecision.permanent(FailureReason.INVALID_REQUEST);
    }

    private int safeStatusCode(int status) {
        return Math.max(0, status);
    }
}
