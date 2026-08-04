package com.wong.collector.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "download.accelerator")
public class DownloadAcceleratorProperties {
    /**
     * 是否启用加速下载。
     */
    private boolean enabled = false;
    /**
     * 直接下载失败时是否回退到加速。
     */
    private boolean fallbackOnFailure = true;
    /**
     * 获取 token 的基础域名，由 Forest 占位符使用。
     */
    private String baseUrl = "https://doget-api.oopscloud.xyz/api";
    /**
     * 下载 URL 模板，包含 %s 作为 token 占位。
     */
    private String downloadUrlTemplate = "https://doget-api.oopscloud.xyz/api/download?token=%s";
}
