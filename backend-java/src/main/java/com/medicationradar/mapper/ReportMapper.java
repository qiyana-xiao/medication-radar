package com.medicationradar.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medicationradar.entity.Report;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface ReportMapper extends BaseMapper<Report> {
    long countOwned(@Param("userId") Long userId, @Param("filter") String filter,
                    @Param("query") String query);

    List<Report> selectOwnedPage(@Param("userId") Long userId, @Param("filter") String filter,
                                 @Param("query") String query, @Param("limit") int limit,
                                 @Param("offset") int offset);

    Map<String, Object> selectTotals(@Param("userId") Long userId);

    List<Map<String, Object>> selectRiskDistribution(@Param("userId") Long userId);

    List<Map<String, Object>> selectTopDrugs(@Param("userId") Long userId);

    List<Map<String, Object>> selectTrend(@Param("userId") Long userId);

    Map<String, Object> selectGlobalTotals();

    List<Map<String, Object>> selectGlobalRiskDistribution();

    List<Map<String, Object>> selectGlobalTopDrugs();

    List<Map<String, Object>> selectGlobalTrend();
}
