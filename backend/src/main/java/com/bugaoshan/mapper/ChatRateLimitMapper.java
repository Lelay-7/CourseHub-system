package com.bugaoshan.mapper;
import com.bugaoshan.entity.ChatSendRateWindow;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
@Mapper
public interface ChatRateLimitMapper {
    int ensure(@Param("userId") long userId,@Param("start") LocalDateTime start,@Param("seconds") int seconds,@Param("expires") LocalDateTime expires);
    ChatSendRateWindow lock(@Param("userId") long userId,@Param("start") LocalDateTime start,@Param("seconds") int seconds);
    int increment(@Param("userId") long userId,@Param("start") LocalDateTime start,@Param("seconds") int seconds);
    int purgeExpired(@Param("cutoff") LocalDateTime cutoff,@Param("limit") int limit);
}
