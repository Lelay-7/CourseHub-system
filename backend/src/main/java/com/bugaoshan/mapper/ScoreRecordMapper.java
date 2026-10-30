package com.bugaoshan.mapper;

import com.bugaoshan.entity.ScoreRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ScoreRecordMapper {

    List<ScoreRecord> selectByUserId(@Param("userId") Long userId, @Param("offset") Integer offset, @Param("limit") Integer limit, @Param("month") String month);

    long countByUserId(@Param("userId") Long userId, @Param("month") String month);

    java.util.Map<String, Object> summarizeMonth(@Param("userId") Long userId,
            @Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    int insert(ScoreRecord record);
}
