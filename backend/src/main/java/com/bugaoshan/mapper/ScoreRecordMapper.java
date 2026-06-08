package com.bugaoshan.mapper;

import com.bugaoshan.entity.ScoreRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ScoreRecordMapper {

    List<ScoreRecord> selectByUserId(@Param("userId") Long userId, @Param("offset") Integer offset, @Param("limit") Integer limit);

    long countByUserId(@Param("userId") Long userId);

    int insert(ScoreRecord record);
}
