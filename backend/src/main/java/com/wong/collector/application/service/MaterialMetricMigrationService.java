package com.wong.collector.application.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.wong.collector.application.event.handler.MaterialMetricSyncListener;
import com.wong.collector.domain.paper.model.MaterialExtraction;
import com.wong.collector.infrastructure.persistence.mapper.MaterialExtractionMapper;
import com.wong.collector.infrastructure.persistence.mapper.MaterialMetricMapper;
import com.wong.collector.infrastructure.persistence.mapper.MaterialObservationMapper;
import com.wong.collector.infrastructure.persistence.po.MaterialExtractionPO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 材料指标数据迁移服务（用于存量数据初始化）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialMetricMigrationService {

    private final MaterialExtractionMapper materialExtractionMapper;
    private final MaterialMetricSyncListener syncListener;
    private final MaterialMetricMapper materialMetricMapper;
    private final MaterialObservationMapper materialObservationMapper;

    // 分页大小
    private static final int PAGE_SIZE = 50;

    /**
     * 执行全量迁移（谨慎使用，会清空目标表）
     *
     * @param async 是否异步执行
     * @return 迁移统计信息
     */
    // 注意：不加 @Transactional，内部方法单独控制事务
    public MigrationResult migrateAll(boolean async) {
        if (async) {
            CompletableFuture.runAsync(this::doMigration);
            return MigrationResult.accepted();
        }
        return doMigration();
    }

    /**
     * 实际迁移逻辑
     */
    // 非只读事务，允许 DDL 操作
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public MigrationResult doMigration() {
        log.info("Starting material_metric migration...");
        long startTime = System.currentTimeMillis();

        // 清空目标表（幂等性）
        materialMetricMapper.truncateTable();
        materialObservationMapper.truncateTable();
        log.info("Truncated target tables");

        int page = 0;
        AtomicInteger totalPapers = new AtomicInteger(0);
        AtomicInteger totalMaterials = new AtomicInteger(0);
        AtomicInteger failedPapers = new AtomicInteger(0);

        while (true) {
            // 注意：这里需要新开只读事务查询数据
            List<MaterialExtractionPO> batch = fetchBatch(page);

            if (batch.isEmpty()) {
                break;
            }

            log.info("Processing batch {} with {} records", page, batch.size());

            for (MaterialExtractionPO po : batch) {
                try {
                    MaterialExtraction extraction = MaterialExtraction.of(
                            po.getMaterial(),
                            po.getProperty(),
                            po.getMethod(),
                            po.getConfidence() == null ? 0.0 : po.getConfidence(),
                            po.getDetailJson()
                    );

                    // 使用 Listener 的处理逻辑，确保一致性
                    syncListener.processMaterialForMigration(po.getPaperId(), extraction);

                    totalMaterials.incrementAndGet();
                } catch (Exception e) {
                    log.error("Failed to migrate paper {}, material {}: {}",
                            po.getPaperId(), po.getMaterial(), e.getMessage());
                    failedPapers.incrementAndGet();
                }
            }

            totalPapers.addAndGet(batch.size());
            page++;

            // 每处理 10 页强制 GC 一次
            if (page % 10 == 0) {
                System.gc();
                log.info("Processed {} papers so far...", totalPapers.get());
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Migration completed in {}ms. Papers: {}, Materials: {}, Failed: {}",
                duration, totalPapers.get(), totalMaterials.get(), failedPapers.get());

        return new MigrationResult(totalPapers.get(), totalMaterials.get(), failedPapers.get(), duration);
    }

    /**
     * 单独查询批次（只读事务）
     */
    @Transactional(readOnly = true)
    public List<MaterialExtractionPO> fetchBatch(int page) {
        return materialExtractionMapper.selectBatchWithDetailJson(PAGE_SIZE, page * PAGE_SIZE);
    }

    /**
     * 单论文重同步（用于修复特定论文的数据）
     */
    @Transactional(rollbackFor = Exception.class)
    public void resyncPaper(String paperId, List<MaterialExtraction> materials) {
        log.info("Resyncing paper {} with {} materials", paperId, materials.size());

        // 先删除旧数据
        materialMetricMapper.deleteByPaperId(paperId);
        materialObservationMapper.deleteByPaperId(paperId);

        // 重新处理
        for (MaterialExtraction material : materials) {
            try {
                syncListener.processMaterialForMigration(paperId, material);
            } catch (Exception e) {
                log.error("Failed to resync material {} for paper {}: {}",
                        material.getMaterial(), paperId, e.getMessage());
            }
        }
    }

    public record MigrationResult(long papersProcessed, long materialsProcessed,
                                  long failedCount, long durationMs) {
        public static MigrationResult accepted() {
            return new MigrationResult(-1, -1, -1, -1);
        }

        public boolean isAsync() {
            return papersProcessed == -1;
        }
    }
}
