package com.bugaoshan.mapper;
import com.bugaoshan.entity.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;
@Mapper
public interface ChatConversationMapper {
    ChatConversation selectPair(@Param("low") long low,@Param("high") long high);
    ChatConversation selectForMember(@Param("id") long id,@Param("userId") long userId);
    ChatConversation selectForUpdate(@Param("id") long id);
    int insert(ChatConversation row);
    int insertMember(@Param("id") long id,@Param("userId") long userId,@Param("now") LocalDateTime now);
    ChatConversationMember member(@Param("id") long id,@Param("userId") long userId);
    List<ChatConversation> list(@Param("userId") long userId,@Param("limit") int limit,@Param("cursorTime") LocalDateTime time,@Param("cursorId") Long cursorId);
    long unread(@Param("id") long id,@Param("userId") long userId);
    long totalUnread(@Param("userId") long userId);
    ChatMessage lastMessage(@Param("id") long id);
    int messageExists(@Param("id") long id,@Param("sequence") long sequence);
    int markRead(@Param("id") long id,@Param("userId") long userId,@Param("sequence") long sequence,@Param("now") LocalDateTime now);
}
