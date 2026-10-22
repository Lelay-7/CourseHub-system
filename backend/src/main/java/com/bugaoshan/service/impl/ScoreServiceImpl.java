package com.bugaoshan.service.impl;

import com.bugaoshan.common.PageResult;
import com.bugaoshan.common.BusinessException;
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
    public Result getScoreDetails(Long userId, Integer page, Integer pageSize, String month) {
        page = page == null ? 1 : Math.max(page, 1);
        pageSize = pageSize == null ? 10 : Math.min(Math.max(pageSize, 1), 100);
        if (month != null && !month.isBlank()) month = parseMonth(month).toString();
        int offset = (page - 1) * pageSize;
        List<ScoreRecord> list = scoreRecordMapper.selectByUserId(userId, offset, pageSize, month);
        long total = scoreRecordMapper.countByUserId(userId, month);
        PageResult<ScoreRecord> pageResult = new PageResult<>(list, total);
        return Result.ok(pageResult);
    }

    @Override
    public Result getScoreSummary(Long userId, String month) {
        var period = parseMonth(month);
        Map<String, Object> totals = scoreRecordMapper.summarizeMonth(userId,
                period.atDay(1).atStartOfDay(), period.plusMonths(1).atDay(1).atStartOfDay());
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (String key : List.of("earned", "spent", "refunded")) {
            Object value = totals == null ? null : totals.getOrDefault(key, totals.get(key.toUpperCase(java.util.Locale.ROOT)));
            result.put(key, value instanceof Number ? ((Number) value).longValue() : 0L);
        }
        return Result.ok(result);
    }

    private java.time.YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) return java.time.YearMonth.now(java.time.ZoneId.of("Asia/Shanghai"));
        try {
            if (!month.matches("\\d{4}-\\d{2}")) throw new IllegalArgumentException();
            return java.time.YearMonth.parse(month);
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException ex) {
            throw new BusinessException("月份格式应为 YYYY-MM");
        }
    }
}
