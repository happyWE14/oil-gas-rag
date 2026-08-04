package com.wong.collector.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wong.collector.infrastructure.persistence.po.TaskRecordPO;

@Mapper
public interface TaskRecordMapper extends BaseMapper<TaskRecordPO> {

    @Update("""
        UPDATE task_record
        SET status = #{status},
            start_time = #{startTime},
            error_message = NULL,
            error_category = NULL,
            error_reason = NULL,
            next_retry_at = NULL,
            update_time = NOW()
        WHERE id = #{id}
          AND status = #{expectedStatus}
        """)
    int startIfWaiting(@Param("id") Long id,
                       @Param("expectedStatus") String expectedStatus,
                       @Param("status") String status,
                       @Param("startTime") java.time.LocalDateTime startTime);
}
