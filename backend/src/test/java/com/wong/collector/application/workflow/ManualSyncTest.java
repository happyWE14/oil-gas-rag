package com.wong.collector.application.workflow;

import com.wong.collector.application.service.MaterialMetricMigrationService;
import com.wong.collector.application.service.MaterialMetricMigrationService.MigrationResult;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE) // 不启动 Web 容器
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
@ActiveProfiles("dev") // 使用 dev profile，避免加载 test 的 H2 配置
@TestPropertySource(properties = {
        // 强制使用 PostgreSQL（覆盖任何 H2 配置）
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/paper_collector",
        "spring.datasource.username=postgres",

        // 禁用 Elasticsearch（避免版本冲突错误）
        "spring.elasticsearch.enabled=false",
        "spring.elasticsearch.restclient.enabled=false",

        // 禁用 Flyway（如果不需要）
        "spring.flyway.enabled=false",

        // 确保使用真实数据库
        "spring.sql.init.continue-on-error=false"
})
// 显式排除 ES 自动配置（双重保险）
@EnableAutoConfiguration(exclude = {
        ElasticsearchRestClientAutoConfiguration.class,
        ElasticsearchRepositoriesAutoConfiguration.class
})
public class ManualSyncTest {

    @Autowired
    private MaterialMetricMigrationService migrationService;

    @Test
    public void runMigration() {
        log.info("========================================");
        log.info("开始执行存量数据同步");
        log.info("========================================");

        try {
            // 执行同步（false = 同步等待完成）
            MigrationResult result = migrationService.migrateAll(false);

            log.info("========================================");
            log.info("同步完成！");
            log.info("处理论文数: {}", result.papersProcessed());
            log.info("处理材料数: {}", result.materialsProcessed());
            log.info("失败数: {}", result.failedCount());
            log.info("耗时: {} ms", result.durationMs());
            log.info("========================================");

            // 简单断言验证
            if (result.papersProcessed() == 0) {
                log.warn("警告：没有处理任何论文，请检查 material_extraction 表是否有数据");
            } else {
                log.info("成功处理了 {} 篇论文", result.papersProcessed());
            }

        } catch (Exception e) {
            log.error("同步过程中发生错误", e);
            throw e; // 抛出异常让测试失败
        }
    }
}
