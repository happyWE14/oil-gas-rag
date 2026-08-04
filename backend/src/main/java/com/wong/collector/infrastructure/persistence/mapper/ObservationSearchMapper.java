package com.wong.collector.infrastructure.persistence.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.wong.collector.infrastructure.persistence.dto.ObservationSearchRow;

@Mapper
public interface ObservationSearchMapper {

    Long countFtsResults(@Param("tsQuery") String tsQuery,
                         @Param("type") String type);

    List<ObservationSearchRow> searchFts(@Param("tsQuery") String tsQuery,
                                          @Param("type") String type,
                                          @Param("limit") int limit,
                                          @Param("offset") long offset);
}
