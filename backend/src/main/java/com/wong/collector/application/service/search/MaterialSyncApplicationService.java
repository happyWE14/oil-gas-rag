package com.wong.collector.application.service.search;

import com.wong.collector.domain.common.event.EventPublisher;
import com.wong.collector.domain.paper.event.MaterialExtractedEvent; // 需要创建这个事件类
import com.wong.collector.infrastructure.persistence.po.MaterialExtractionPO;
import com.wong.collector.infrastructure.persistence.po.OutboxEvent;
import com.wong.collector.infrastructure.persistence.repository.MaterialExtractionRepository;
import com.wong.collector.infrastructure.persistence.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaterialSyncApplicationService {
    private final MaterialExtractionRepository pgRepo;
    private final OutboxRepository outboxRepo;
    private final MaterialSearchService searchService; // 注意：这里不直接调用ES

    @Transactional
    public void updateMaterialWithSync(String paperId, List<MaterialExtractionPO> extractions) {
        // 1. 保存到 PG
        extractions.forEach(pgRepo::insert);

        // 2. 写入 Outbox（保证事务一致性）
        extractions.forEach(ext -> {
            OutboxEvent outbox = new OutboxEvent();
            outbox.setEventId(UUID.randomUUID().toString());
            outbox.setEventType("MATERIAL_EXTRACTED");
            outbox.setAggregateId(paperId);
            outbox.setPayload("{\"extractionId\":" + ext.getId() + "}");
            outbox.setStatus("PENDING");
            outboxRepo.insert(outbox);
        });
    }
}
