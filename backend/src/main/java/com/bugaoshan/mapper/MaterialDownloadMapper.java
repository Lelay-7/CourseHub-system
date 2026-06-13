package com.bugaoshan.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MaterialDownloadMapper {
    int insertIgnore(@Param("materialId") Long materialId,
                     @Param("userId") Long userId,
                     @Param("scoreCost") Integer scoreCost);

    java.util.List<java.util.Map<String, Object>> selectByUserId(@Param("userId") Long userId,
                                                                  @Param("offset") Integer offset,
                                                                  @Param("limit") Integer limit);

    long countByUserId(@Param("userId") Long userId);
}
