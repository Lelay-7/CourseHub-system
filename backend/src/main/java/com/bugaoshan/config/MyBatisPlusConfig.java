package com.bugaoshan.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.bugaoshan.mapper")
public class MyBatisPlusConfig {
}
