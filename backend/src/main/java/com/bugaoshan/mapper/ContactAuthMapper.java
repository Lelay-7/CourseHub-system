package com.bugaoshan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bugaoshan.entity.ContactAuth;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ContactAuthMapper extends BaseMapper<ContactAuth> {

    ContactAuth selectByUserId(@Param("userId") Long userId);

    int updateAuth(ContactAuth auth);
}
