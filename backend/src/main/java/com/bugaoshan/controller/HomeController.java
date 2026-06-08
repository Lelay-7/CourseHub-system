package com.bugaoshan.controller;

import com.bugaoshan.common.Result;
import com.bugaoshan.common.SecurityUtils;
import com.bugaoshan.service.HomeService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/home")
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping("/banners")
    public Result<?> banners() {
        return homeService.getBanners();
    }

    @GetMapping("/news")
    public Result<?> news(@RequestParam(required = false) String type,
                          @RequestParam(required = false, defaultValue = "1") Integer page,
                          @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return homeService.getNewsList(type, page, pageSize);
    }

    @GetMapping("/rank")
    public Result<?> rank() {
        return homeService.getRankList();
    }

    @GetMapping("/order/count")
    public Result<?> orderCount() {
        Long userId = SecurityUtils.getCurrentUserId();
        return homeService.getPendingOrderCount(userId);
    }
}
