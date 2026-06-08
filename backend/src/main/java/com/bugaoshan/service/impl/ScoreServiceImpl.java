package com.bugaoshan.service.impl;

import com.bugaoshan.common.PageResult;
import com.bugaoshan.common.Result;
import com.bugaoshan.entity.ScoreRecord;
import com.bugaoshan.entity.User;
import com.bugaoshan.mapper.ScoreRecordMapper;
import com.bugaoshan.mapper.UserMapper;
import com.bugaoshan.service.ScoreService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScoreServiceImpl implements ScoreService {

    private final UserMapper userMapper;
    private final ScoreRecordMapper scoreRecordMapper;

    public ScoreServiceImpl(UserMapper userMapper, ScoreRecordMapper scoreRecordMapper) {
        this.userMapper = userMapper;
        this.scoreRecordMapper = scoreRecordMapper;
    }

    @Override
    public Result getScoreBalance(Long userId) {
        User user = userMapper.selectById(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("balance", user.getScoreBalance());
        data.put("total_score", user.getTotalScore());
        return Result.ok(data);
    }

    @Override
    public Result getScoreDetails(Long userId, Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        List<ScoreRecord> list = scoreRecordMapper.selectByUserId(userId, offset, pageSize);
        long total = scoreRecordMapper.countByUserId(userId);
        PageResult<ScoreRecord> pageResult = new PageResult<>(list, total);
        return Result.ok(pageResult);
    }
}
