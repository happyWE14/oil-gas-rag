package com.wong.collector.infrastructure.persistence.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * PgVector 类型处理器：将 PG 的 vector 类型映射为 List<Double>
 */
@MappedJdbcTypes(JdbcType.OTHER)
@MappedTypes(List.class)
public class PgVectorTypeHandler extends BaseTypeHandler<List<Double>> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<Double> parameter, JdbcType jdbcType) throws SQLException {
        // 方法 A：使用 PGobject（推荐，类型最安全）
        PGobject pgObject = new PGobject();
        pgObject.setType("vector");

        StringBuilder sb = new StringBuilder("[");
        for (int j = 0; j < parameter.size(); j++) {
            if (j > 0) sb.append(",");
            sb.append(parameter.get(j));
        }
        sb.append("]");

        pgObject.setValue(sb.toString());
        ps.setObject(i, pgObject);

        // 方法 B：如果方法 A 不行，试试这个（某些驱动版本适用）
        // ps.setObject(i, sb.toString(), Types.OTHER);
    }

    @Override
    public List<Double> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return parseVector(value);
    }

    @Override
    public List<Double> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return parseVector(value);
    }

    @Override
    public List<Double> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return parseVector(value);
    }

    private List<Double> parseVector(String value) {
        if (value == null || value.isEmpty() || value.equals("null")) {
            return null;
        }
        String trimmed = value.replace("[", "").replace("]", "");
        if (trimmed.isEmpty()) return new ArrayList<>();

        String[] parts = trimmed.split(",");
        List<Double> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            result.add(Double.parseDouble(part.trim()));
        }
        return result;
    }
}
