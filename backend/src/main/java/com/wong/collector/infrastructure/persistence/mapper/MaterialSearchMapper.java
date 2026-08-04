package com.wong.collector.infrastructure.persistence.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.wong.collector.infrastructure.persistence.dto.MaterialSearchRow;

@Mapper
public interface MaterialSearchMapper {

    Long countFtsResults(@Param("tsQuery") String tsQuery,
                         @Param("minConfidence") Double minConfidence);

    List<MaterialSearchRow> searchFts(@Param("tsQuery") String tsQuery,
                                      @Param("minConfidence") Double minConfidence,
                                      @Param("limit") int limit,
                                      @Param("offset") long offset);
}
