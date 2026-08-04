package com.wong.collector.infrastructure.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 搜索业务参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "paper.search")
public class SearchBusinessProperties {

    /**
     * 连续空页阈值，超过后暂停/完成搜索。
     */
    private int maxEmptyPages = 3;

    /**
     * 是否启用自动调度。
     */
    private boolean autoScheduleEnabled = false;

    /**
     * 自动调度任务的固定延迟（毫秒）。
     */
    private long schedulerFixedDelayMs = 60000L;

    /**
     * 每次调度完成后距离下次运行的间隔（分钟）。
     */
    private long schedulerIntervalMinutes = 10L;
}
