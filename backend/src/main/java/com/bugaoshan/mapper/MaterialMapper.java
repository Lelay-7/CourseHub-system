package com.bugaoshan.mapper;

import com.bugaoshan.entity.Material;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface MaterialMapper {

    List<Material> selectList(@Param("materialType") Integer materialType,
                              @Param("courseId") Long courseId,
                              @Param("keyword") String keyword,
                              @Param("sortBy") String sortBy,
                              @Param("sortOrder") String sortOrder,
                              @Param("offset") Integer offset,
                              @Param("limit") Integer limit);

    long countList(@Param("materialType") Integer materialType,
                   @Param("courseId") Long courseId,
                   @Param("keyword") String keyword);

    Material selectById(@Param("materialId") Long materialId);

    int insert(Material material);

    int incrementDownloadCount(@Param("materialId") Long materialId);

    int updateStatus(@Param("materialId") Long materialId, @Param("status") Integer status);

    List<Map<String, Object>> selectCategories();
}
