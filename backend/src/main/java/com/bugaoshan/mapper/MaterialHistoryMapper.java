package com.bugaoshan.mapper;

import com.bugaoshan.entity.Material;
import com.bugaoshan.entity.MaterialAuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface MaterialHistoryMapper {
    int insertSnapshot(Material material);
    List<Material> selectRevisions(@Param("materialId") Long materialId);
    Material selectRevision(@Param("materialId") Long materialId,@Param("revision") Integer revision);
    int insertAudit(MaterialAuditLog log);
    List<MaterialAuditLog> selectAuditHistory(@Param("materialId") Long materialId);
}
