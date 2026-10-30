package com.bugaoshan.mapper;

import com.bugaoshan.entity.MaterialReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MaterialReportMapper {
    int insert(MaterialReport report);

    List<MaterialReport> selectByMaterialId(@Param("materialId") Long materialId);

    List<MaterialReport> selectAll(@Param("status") Integer status, @Param("offset") Integer offset, @Param("limit") Integer limit);

    long countAll(@Param("status") Integer status);

    MaterialReport selectForUpdate(@Param("reportId") Long reportId);
    List<MaterialReport> selectByReporter(@Param("reporterId") Long reporterId,
            @Param("offset") Integer offset, @Param("limit") Integer limit);
    long countByReporter(@Param("reporterId") Long reporterId);
    int resolvePending(@Param("reportId") Long reportId, @Param("status") Integer status,
            @Param("action") String action, @Param("reason") String reason, @Param("actorId") Long actorId);

    boolean existsPending(@Param("materialId") Long materialId, @Param("reporterId") Long reporterId);

    int updateStatus(@Param("reportId") Long reportId, @Param("status") Integer status);
}
