package com.bugaoshan.mapper;

import com.bugaoshan.entity.ContactAuth;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ContactAuthMapper {

    ContactAuth selectByUserId(@Param("userId") Long userId);
}
