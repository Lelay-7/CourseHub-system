package com.bugaoshan.controller;

import com.bugaoshan.common.Result;
import com.bugaoshan.common.SecurityUtils;
import com.bugaoshan.dto.ExchangeDTO;
import com.bugaoshan.service.MallService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mall")
public class MallController {

    private final MallService mallService;

    public MallController(MallService mallService) {
        this.mallService = mallService;
    }

    @GetMapping("/goods")
    public Result<?> goods(@RequestParam(required = false) Long categoryId,
                           @RequestParam(required = false, defaultValue = "1") Integer page,
                           @RequestParam(required = false, defaultValue = "10") Integer pageSize,
                           @RequestParam(required = false) String sortBy) {
        return mallService.getGoodsList(categoryId, page, pageSize, sortBy);
    }

    @GetMapping("/goods/{id}")
    public Result<?> goodsDetail(@PathVariable Long id) {
        return mallService.getGoodsDetail(id);
    }

    @PostMapping("/exchange")
    public Result<?> exchange(@Valid @RequestBody ExchangeDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return mallService.exchangeGoods(userId, dto.getGoodsId());
    }

    @GetMapping("/exchanges")
    public Result<?> exchanges(@RequestParam(required = false, defaultValue = "1") Integer page,
                               @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        Long userId = SecurityUtils.getCurrentUserId();
        return mallService.getExchangeList(userId, page, pageSize);
    }
}
