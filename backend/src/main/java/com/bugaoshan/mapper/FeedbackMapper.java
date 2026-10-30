package com.bugaoshan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bugaoshan.entity.Feedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FeedbackMapper extends BaseMapper<Feedback> {

    List<Feedback> selectAllWithUser(@Param("status") Integer status, @Param("offset") int offset, @Param("limit") int limit);
    long countAll(@Param("status") Integer status);

    int updateReply(@Param("feedbackId") Long feedbackId,
                    @Param("reply") String reply,
                    @Param("replyTime") java.time.LocalDateTime replyTime);

    List<Feedback> selectByUserId(@Param("userId") Long userId);
}
