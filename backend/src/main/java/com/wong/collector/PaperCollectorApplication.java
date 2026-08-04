package com.wong.collector;

import org.dromara.x.file.storage.spring.EnableFileStorage;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 论文收集器主启动类
 * 基于CORE API的论文搜索和收集服务
 *
 * @author Wong
 */
@MapperScan("com.wong.collector.infrastructure.persistence.mapper")
@MapperScan("com.wong.collector.infrastructure.persistence.repository")
@EnableScheduling
@EnableAsync
@EnableFileStorage
@SpringBootApplication(
        exclude = {
                OpenAiEmbeddingAutoConfiguration.class  // 排除 OpenAI Embedding 自动配置
        }
)
public class PaperCollectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaperCollectorApplication.class, args);
    }
}
