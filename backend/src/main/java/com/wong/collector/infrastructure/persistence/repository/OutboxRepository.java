package com.wong.collector.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wong.collector.infrastructure.persistence.po.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OutboxRepository extends BaseMapper<OutboxEvent> {

    // 解决：无法解析方法 'findTop100ByStatusOrderByCreatedAt'
    default List<OutboxEvent> findTop100ByStatusOrderByCreatedAt(String status) {
        return selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OutboxEvent>()
                        .eq(OutboxEvent::getStatus, status)
                        .orderByAsc(OutboxEvent::getCreatedAt)
                        .last("LIMIT 100")
        );
    }

    @Update("UPDATE event_outbox SET status = 'SENT', sent_at = #{now}, updated_at = #{now} " +
            "WHERE event_id = #{eventId}")
    void markAsSent(@Param("eventId") String eventId, @Param("now") LocalDateTime now);

    default void markAsSent(String eventId) {
        markAsSent(eventId, LocalDateTime.now());
    }

    @Update("UPDATE event_outbox SET retry_count = retry_count + 1, error_message = #{error}, " +
            "updated_at = #{now}, next_retry_at = #{nextRetry} " +
            "WHERE event_id = #{eventId}")
    void incrementRetry(@Param("eventId") String eventId,
                        @Param("error") String error,
                        @Param("now") LocalDateTime now,
                        @Param("nextRetry") LocalDateTime nextRetry);

    default void incrementRetry(String eventId, String error) {
        LocalDateTime now = LocalDateTime.now();
        incrementRetry(eventId, error, now, now.plusMinutes(5));
    }
}
