package com.bugaoshan.mapper;

import com.bugaoshan.entity.News;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NewsMapper {

    List<News> selectList(@Param("type") String type, @Param("offset") Integer offset, @Param("limit") Integer limit);

    long countList(@Param("type") String type);
}
