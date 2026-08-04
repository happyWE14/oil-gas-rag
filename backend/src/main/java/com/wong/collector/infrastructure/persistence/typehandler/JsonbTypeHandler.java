package com.wong.collector.infrastructure.persistence.typehandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * PostgreSQL JSONB 类型处理器
 * 支持 Java Map/String 映射到 PostgreSQL JSONB
 */
@MappedJdbcTypes(JdbcType.OTHER)
@MappedTypes({Map.class, String.class})  // 同时支持 Map 和 String 类型
public class JsonbTypeHandler extends BaseTypeHandler<Object> {

    private static final String PG_JSONB_TYPE = "jsonb";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        PGobject jsonObject = new PGobject();
        jsonObject.setType(PG_JSONB_TYPE);

        try {
            String jsonValue;
            // 如果是 String 类型，直接使用；否则（Map等）序列化为 JSON 字符串
            if (parameter instanceof String) {
                jsonValue = (String) parameter;
            } else {
                jsonValue = objectMapper.writeValueAsString(parameter);
            }
            jsonObject.setValue(jsonValue);
            ps.setObject(i, jsonObject);
        } catch (JsonProcessingException e) {
            throw new SQLException("Error converting parameter to JSON: " + parameter, e);
        }
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}
