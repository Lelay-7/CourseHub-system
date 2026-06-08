package com.bugaoshan.mapper;

import com.bugaoshan.entity.Banner;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BannerMapper {

    List<Banner> selectAll();
}
