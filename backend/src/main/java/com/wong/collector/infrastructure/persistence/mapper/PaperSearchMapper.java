package com.wong.collector.infrastructure.persistence.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.wong.collector.infrastructure.persistence.dto.PaperSearchRow;

@Mapper
public interface PaperSearchMapper {

    Long countFtsResults(@Param("tsQuery") String tsQuery,
                         @Param("states") List<String> states,
                         @Param("yearMin") Integer yearMin,
                         @Param("yearMax") Integer yearMax);

    List<PaperSearchRow> searchFts(@Param("tsQuery") String tsQuery,
                                   @Param("states") List<String> states,
                                   @Param("yearMin") Integer yearMin,
                                   @Param("yearMax") Integer yearMax,
                                   @Param("limit") int limit,
                                   @Param("offset") long offset);
}
