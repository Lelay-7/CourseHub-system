package com.bugaoshan.service.impl;

import com.bugaoshan.common.PageResult;
import com.bugaoshan.common.Result;
import com.bugaoshan.entity.Banner;
import com.bugaoshan.entity.News;
import com.bugaoshan.entity.User;
import com.bugaoshan.mapper.BannerMapper;
import com.bugaoshan.mapper.NewsMapper;
import com.bugaoshan.mapper.OrderMapper;
import com.bugaoshan.mapper.UserMapper;
import com.bugaoshan.service.HomeService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HomeServiceImpl implements HomeService {

    private final BannerMapper bannerMapper;
    private final NewsMapper newsMapper;
    private final UserMapper userMapper;
    private final OrderMapper orderMapper;

    public HomeServiceImpl(BannerMapper bannerMapper, NewsMapper newsMapper,
                           UserMapper userMapper, OrderMapper orderMapper) {
        this.bannerMapper = bannerMapper;
        this.newsMapper = newsMapper;
        this.userMapper = userMapper;
        this.orderMapper = orderMapper;
    }

    @Override
    public Result getBanners() {
        List<Banner> banners = bannerMapper.selectAll();
        return Result.ok(banners);
    }

    @Override
    public Result getNewsList(String type, Integer page, Integer pageSize) {
        if (page == null) page = 1;
        if (pageSize == null) pageSize = 10;
        int offset = (page - 1) * pageSize;
        List<News> list = newsMapper.selectList(type, offset, pageSize);
        long total = newsMapper.countList(type);
        PageResult<News> pageResult = new PageResult<>(list, total);
        return Result.ok(pageResult);
    }

    @Override
    public Result getRankList() {
        List<User> users = userMapper.selectTopScoreRank(10);
        for (User user : users) {
            user.setPasswordHash(null);
        }
        return Result.ok(users);
    }

    @Override
    public Result getPendingOrderCount(Long userId) {
        long count = orderMapper.countPendingByUserId(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("count", count);
        return Result.ok(data);
    }
}
