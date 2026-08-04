package com.wong.collector.infrastructure.search.worker;

import com.wong.collector.application.service.search.MaterialSearchService;
import com.wong.collector.domain.search.repository.MaterialSearchRepository;
import com.wong.collector.infrastructure.persistence.po.MaterialExtractionPO;
import com.wong.collector.infrastructure.persistence.po.OutboxEvent;
import com.wong.collector.infrastructure.persistence.repository.MaterialExtractionRepository;
import com.wong.collector.infrastructure.persistence.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.baomidou.mybatisplus.extension.ddl.DdlScriptErrorHandler.PrintlnLogErrorHandler.log;

@Component
@RequiredArgsConstructor
public class EsSyncWorker {
    private final OutboxRepository outboxRepo;
    private final MaterialSearchService searchService; // 使用新的service

    @Scheduled(fixedDelay = 5000)
    public void syncPendingEvents() {
        List<OutboxEvent> events = outboxRepo.findTop100ByStatusOrderByCreatedAt("PENDING");

        events.forEach(event -> {
            try {
                // 新开事务查询数据，避免污染
                searchService.syncByPaperId(event.getAggregateId());
                outboxRepo.markAsSent(event.getEventId());
            } catch (Exception e) {
                log.error("Sync failed", e);
                outboxRepo.incrementRetry(event.getEventId(), e.getMessage());
            }
        });
    }
}
