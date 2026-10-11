package com.bugaoshan.mapper;
import com.bugaoshan.entity.ChatMessage;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;
@Mapper
public interface ChatMessageMapper {
    ChatMessage idempotent(@Param("userId") long userId,@Param("clientId") String clientId);
    int insert(ChatMessage row);
    int advance(@Param("id") long id,@Param("sequence") long sequence,@Param("now") LocalDateTime now);
    List<ChatMessage> page(@Param("id") long id,@Param("before") Long before,@Param("after") Long after,@Param("limit") int limit);
}
