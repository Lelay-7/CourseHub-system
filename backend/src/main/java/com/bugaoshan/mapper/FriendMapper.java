package com.bugaoshan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bugaoshan.entity.Friend;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface FriendMapper extends BaseMapper<Friend> {

    Friend selectByUserAndTarget(@Param("userId") Long userId, @Param("targetId") Long targetId);
    Friend selectPair(@Param("lowerId") long lowerId, @Param("higherId") long higherId);
    Friend selectPairForUpdate(@Param("lowerId") long lowerId, @Param("higherId") long higherId);
    int deleteForParticipant(@Param("friendId") long friendId, @Param("userId") long userId);

    List<Map<String, Object>> selectMyFriends(@Param("userId") Long userId);

    List<Map<String, Object>> selectPendingRequests(@Param("userId") Long userId);

    int updateStatus(@Param("friendId") Long friendId, @Param("targetId") Long targetId, @Param("status") Integer status);

    int deleteByUserAndTarget(@Param("userId") Long userId, @Param("targetId") Long targetId);
}
