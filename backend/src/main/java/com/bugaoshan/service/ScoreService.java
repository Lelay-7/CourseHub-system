package com.bugaoshan.service;

import com.bugaoshan.common.Result;

public interface ScoreService {
    Result getScoreBalance(Long userId);
    Result getScoreDetails(Long userId, Integer page, Integer pageSize, String month);
    Result getScoreSummary(Long userId, String month);
}
