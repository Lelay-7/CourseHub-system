package com.bugaoshan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bugaoshan.entity.Material;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface MaterialMapper extends BaseMapper<Material> {

    List<Material> selectList(@Param("materialType") Integer materialType,
                              @Param("courseId") Long courseId,
                              @Param("keyword") String keyword,
                              @Param("priceFilter") String priceFilter,
                              @Param("sortBy") String sortBy,
                              @Param("sortOrder") String sortOrder,
                              @Param("offset") Integer offset,
                              @Param("limit") Integer limit);

    long countList(@Param("materialType") Integer materialType,
                   @Param("courseId") Long courseId,
                   @Param("keyword") String keyword,
                   @Param("priceFilter") String priceFilter);

    Material selectById(@Param("materialId") Long materialId);

    int insert(Material material);

    int incrementDownloadCount(@Param("materialId") Long materialId);

    int updateStatus(@Param("materialId") Long materialId, @Param("status") Integer status);

    int transitionStatus(@Param("materialId") Long materialId,
                         @Param("expectedStatus") Integer expectedStatus,
                         @Param("status") Integer status);

    int rejectPending(@Param("materialId") Long materialId, @Param("rejectReason") String rejectReason);

    int resubmitRejected(@Param("materialId") Long materialId);

    List<Map<String, Object>> selectCategories();

    List<Material> selectByUploaderId(@Param("uploaderId") Long uploaderId);

    List<Material> selectAllForAdmin(@Param("status") Integer status,
                                      @Param("offset") Integer offset,
                                      @Param("limit") Integer limit);

    long countAllForAdmin(@Param("status") Integer status);
}
