package com.wong.collector.application.workflow.download;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.dtflys.forest.http.ForestResponse;
import com.wong.collector.infrastructure.config.properties.DownloadAcceleratorProperties;
import com.wong.collector.infrastructure.external.client.CoreApiClient;
import com.wong.collector.infrastructure.external.client.FileUrlTransferClient;
import com.wong.collector.infrastructure.external.download.dto.TokenResponse;
import com.wong.collector.application.task.NonRetryableTaskException;
import com.wong.collector.application.task.RetryableTaskException;
import com.wong.collector.application.task.RetryAfterSupport;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaperDownloadExecutor {

    private final CoreApiClient coreApiClient;
    private final FileUrlTransferClient fileUrlTransferClient;
    private final DownloadAcceleratorProperties acceleratorProperties;

    // 注入本地存储配置（沿用你的 application.yml 配置项）
    @Value("${dromara.x-file-storage.local-plus.base-path:./data}")
    private String basePath;

    @Value("${dromara.x-file-storage.local-plus.domain:http://localhost:8080/api/file}")
    private String domain;

    public DownloadResult download(String paperId, String sourceUrl) {
        try (InputStream stream = openStream(sourceUrl)) {
            // 构建存储目录：basePath/paper/{paperId}/
            Path dirPath = Paths.get(basePath, "paper", paperId);

            // 确保目录存在（自动创建父目录）
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            // 文件完整路径：basePath/paper/{paperId}/{paperId}.pdf
            Path filePath = dirPath.resolve(paperId + ".pdf");

            // 保存文件（覆盖已存在的）
            Files.copy(stream, filePath, StandardCopyOption.REPLACE_EXISTING);

            // 获取文件大小
            long size = Files.size(filePath);

            // 构建访问 URL（与 FileStorage 返回格式保持一致）
            String url = domain.endsWith("/")
                    ? domain + "paper/" + paperId + "/" + paperId + ".pdf"
                    : domain + "/paper/" + paperId + "/" + paperId + ".pdf";

            log.info("论文下载成功: paperId={}, 路径={}, 大小={} bytes", paperId, filePath, size);

            return new DownloadResult(url, size);

        } catch (NonRetryableTaskException | RetryableTaskException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("download paper failed, id={} url={}", paperId, sourceUrl, ex);
            throw new RetryableTaskException("下载论文失败: " + ex.getMessage(), ex);
        }
    }

    // 下面的 openStream 和 downloadStream 方法保持不变...

    private InputStream openStream(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new NonRetryableTaskException("下载链接为空");
        }
        // 优先直接下载
        try {
            InputStream direct = downloadStream(sourceUrl);
            return direct;
        } catch (NonRetryableTaskException | RetryableTaskException ex) {
            if (!acceleratorProperties.isEnabled() || !acceleratorProperties.isFallbackOnFailure()) {
                throw ex;
            }
            log.warn("direct download failed, fallback to accelerator: {}", ex.getMessage());
        } catch (Exception ex) {
            if (!acceleratorProperties.isEnabled() || !acceleratorProperties.isFallbackOnFailure()) {
                throw new RetryableTaskException("直接下载失败: " + ex.getMessage(), ex);
            }
            log.warn("direct download failed, fallback to accelerator: {}", ex.getMessage());
        }

        if (!acceleratorProperties.isEnabled()) {
            throw new RetryableTaskException("未启用加速且直接下载失败");
        }

        ForestResponse<TokenResponse> response = fileUrlTransferClient.getDownloadToken(sourceUrl);
        if (response == null || !response.isSuccess() || response.getResult() == null) {
            int status = response == null ? 0 : response.getStatusCode();
            Duration retryAfter = RetryAfterSupport.parse(response);
            if (status == 429 || status >= 500) {
                throw new RetryableTaskException("加速下载获取 token 失败（HTTP " + status + "）", retryAfter);
            }
            if (status >= 400 && status < 500) {
                throw new NonRetryableTaskException("加速下载获取 token 失败（HTTP " + status + "）");
            }
            throw new RetryableTaskException("加速下载获取 token 失败（HTTP " + status + "）", retryAfter);
        }
        TokenResponse body = response.getResult();
        if (body.getCode() != 0 || StrUtil.isBlank(body.getData())) {
            throw new NonRetryableTaskException("加速下载获取 token 失败 code=" + body.getCode() + " msg=" + body.getMsg());
        }
        String token = body.getData();
        String finalUrl = String.format(acceleratorProperties.getDownloadUrlTemplate(), token);
        return downloadStream(finalUrl);
    }

    private InputStream downloadStream(String url) {
        ForestResponse<InputStream> response = coreApiClient.downloadPaperResponse(url);
        if (response == null) {
            throw new RetryableTaskException("下载失败：响应为空");
        }
        if (response.isSuccess()) {
            InputStream stream = response.getResult();
            if (stream == null) {
                throw new RetryableTaskException("下载失败：下载流为空");
            }
            return stream;
        }
        int status = response.getStatusCode();
        Duration retryAfter = RetryAfterSupport.parse(response);
        if (status == 404 || status == 410) {
            throw new NonRetryableTaskException("下载链接无效（HTTP " + status + "）: " + url);
        }
        if (status == 401 || status == 403) {
            throw new NonRetryableTaskException("下载被拒绝（HTTP " + status + "）: " + url);
        }
        if (status == 429) {
            throw new RetryableTaskException("下载被限流（HTTP 429）", retryAfter);
        }
        if (status >= 500) {
            throw new RetryableTaskException("下载上游异常（HTTP " + status + "）", retryAfter);
        }
        if (status >= 400) {
            throw new NonRetryableTaskException("下载请求失败（HTTP " + status + "）: " + url);
        }
        throw new RetryableTaskException("下载失败（HTTP " + status + "）: " + url, retryAfter);
    }

    public record DownloadResult(String ossUrl, long fileSize) {}
}
