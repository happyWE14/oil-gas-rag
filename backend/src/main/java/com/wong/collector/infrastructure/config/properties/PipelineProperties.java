package com.wong.collector.infrastructure.config.properties;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.wong.collector.domain.task.model.TaskType;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "pipeline")
public class PipelineProperties {

    /**
     * 事件驱动的阶段定义列表，匹配事件 simpleName。
     */
    private List<Stage> stages = new ArrayList<>();

    @Data
    public static class Stage {
        /**
         * 触发事件 simpleName（例如 PaperPreRelevantEvent）。
         */
        private String onEvent;
        /**
         * 阶段对应的任务类型。
         */
        private TaskType taskType;
        /**
         * 任务描述。
         */
        private String description;
        /**
         * 是否启用该阶段。
         */
        private boolean enabled = true;
    }
}
