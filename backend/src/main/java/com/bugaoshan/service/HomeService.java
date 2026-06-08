package com.bugaoshan.service;

import com.bugaoshan.common.Result;

public interface HomeService {
    Result getBanners();
    Result getNewsList(String type, Integer page, Integer pageSize);
    Result getRankList();
    Result getPendingOrderCount(Long userId);
}
