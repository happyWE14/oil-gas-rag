package com.wong.collector.infrastructure.persistence;

import java.time.LocalDateTime;

import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;

/**
 * 自动填充通用时间字段，避免数据库非空约束报错。
 */
@Component
public class MyBatisMetaHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        fillIfNull("createTime", now, metaObject);
        fillIfNull("updateTime", now, metaObject);
        fillIfNull("createdAt", now, metaObject);
        fillIfNull("updatedAt", now, metaObject);
        fillIfNull("startTime", now, metaObject);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        setFieldValByName("updateTime", now, metaObject);
        setFieldValByName("updatedAt", now, metaObject);
    }

    private void fillIfNull(String fieldName, Object value, MetaObject metaObject) {
        Object current = getFieldValByName(fieldName, metaObject);
        if (current == null) {
            setFieldValByName(fieldName, value, metaObject);
        }
    }
}
