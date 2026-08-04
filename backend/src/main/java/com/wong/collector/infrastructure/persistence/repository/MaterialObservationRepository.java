package com.wong.collector.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wong.collector.infrastructure.persistence.po.MaterialObservationPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MaterialObservationRepository extends BaseMapper<MaterialObservationPO> {

    /**
     * 【修复】使用 IN 子句替代 ANY 数组操作符，兼容 Java List<String>
     * 使用 <foreach> 动态生成 (key1,key2,key3) 形式的 SQL
     */
    @Select("<script>" +
            "SELECT * FROM material_observation " +
            "WHERE paper_id = #{paperId} " +
            "AND material_key IN " +
            "<foreach item='key' index='index' collection='materialKeys' open='(' separator=',' close=')'>" +
            "#{key}" +
            "</foreach>" +
            "</script>")
    List<MaterialObservationPO> findByPaperIdAndMaterialKeys(@Param("paperId") String paperId,
                                                             @Param("materialKeys") List<String> materialKeys);

    /**
     * 单条查询 - 用于单一材料查询
     */
    @Select("SELECT * FROM material_observation WHERE paper_id = #{paperId} AND material_key = #{materialKey}")
    List<MaterialObservationPO> findByPaperAndMaterial(@Param("paperId") String paperId,
                                                       @Param("materialKey") String materialKey);

    /**
     * 【备选方案】使用 MyBatis-Plus Wrapper 实现（无需注解，最类型安全）
     * 如果上面的注解方案遇到问题，可改为在 Service 中调用此方法
     */
    default List<MaterialObservationPO> selectByPaperIdAndMaterialKeys(String paperId, List<String> materialKeys) {
        if (materialKeys == null || materialKeys.isEmpty()) {
            return List.of();
        }
        return selectList(new QueryWrapper<MaterialObservationPO>()
                .eq("paper_id", paperId)
                .in("material_key", materialKeys));
    }
}
