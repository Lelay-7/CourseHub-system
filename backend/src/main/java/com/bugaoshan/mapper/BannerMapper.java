package com.bugaoshan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bugaoshan.entity.Banner;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BannerMapper extends BaseMapper<Banner> {

    List<Banner> selectAll();

    List<Banner> selectAllForAdmin();

    int deleteById(@Param("bannerId") Long bannerId);
}
