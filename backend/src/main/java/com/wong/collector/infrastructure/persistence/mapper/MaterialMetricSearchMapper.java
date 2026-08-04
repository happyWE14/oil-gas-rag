package com.wong.collector.infrastructure.persistence.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.wong.collector.infrastructure.persistence.dto.MaterialMetricSearchRow;

@Mapper
public interface MaterialMetricSearchMapper {

    Long countMetricResults(@Param("metricKey") String metricKey,
                            @Param("operator") String operator,
                            @Param("value") Double value,
                            @Param("materialKey") String materialKey);

    List<MaterialMetricSearchRow> searchMetrics(@Param("metricKey") String metricKey,
                                                 @Param("operator") String operator,
                                                 @Param("value") Double value,
                                                 @Param("materialKey") String materialKey,
                                                 @Param("limit") int limit,
                                                 @Param("offset") long offset);
}
