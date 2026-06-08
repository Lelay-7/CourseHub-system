package com.bugaoshan.mapper;

import com.bugaoshan.entity.SmsCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SmsCodeMapper {

    int insert(SmsCode smsCode);

    SmsCode selectLatestByPhone(@Param("phone") String phone, @Param("type") Integer type);

    int markUsed(@Param("smsId") Long smsId);
}
