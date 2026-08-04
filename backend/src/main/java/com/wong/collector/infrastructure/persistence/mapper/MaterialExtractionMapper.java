package com.wong.collector.infrastructure.persistence.mapper;

import java.util.List;

import com.wong.collector.infrastructure.persistence.dto.MaterialGlobalStatsRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wong.collector.infrastructure.persistence.dto.MaterialPaperRow;
import com.wong.collector.infrastructure.persistence.dto.MaterialSummaryRow;
import com.wong.collector.infrastructure.persistence.po.MaterialExtractionPO;

@Mapper
public interface MaterialExtractionMapper extends BaseMapper<MaterialExtractionPO> {

    Long countDistinctMaterialKey(@Param("q") String q, @Param("minConfidence") Double minConfidence);

    List<MaterialSummaryRow> pageMaterialSummary(@Param("q") String q,
                                                 @Param("minConfidence") Double minConfidence,
                                                 @Param("limit") int limit,
                                                 @Param("offset") long offset);

    Long countMaterialPapers(@Param("materialKey") String materialKey, @Param("minConfidence") Double minConfidence);

    List<MaterialPaperRow> pageMaterialPapers(@Param("materialKey") String materialKey,
                                              @Param("minConfidence") Double minConfidence,
                                              @Param("limit") int limit,
                                              @Param("offset") long offset);

    MaterialGlobalStatsRow selectGlobalStats();

    // 分页查询有 detail_json 的存量数据
    List<MaterialExtractionPO> selectBatchWithDetailJson(@Param("limit") int limit, @Param("offset") int offset);
}
