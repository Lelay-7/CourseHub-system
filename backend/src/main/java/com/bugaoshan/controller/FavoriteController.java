package com.bugaoshan.controller;

import com.bugaoshan.common.Result;
import com.bugaoshan.common.SecurityUtils;
import com.bugaoshan.dto.FavoriteDTO;
import com.bugaoshan.service.FavoriteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/favorite")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping("/list")
    public Result<?> list(@RequestParam(required = false, defaultValue = "course") String type) {
        Long userId = SecurityUtils.getCurrentUserId();
        return favoriteService.getFavoriteList(userId, type);
    }

    @PostMapping("/add")
    public Result<?> add(@Valid @RequestBody FavoriteDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return favoriteService.addFavorite(userId, dto);
    }

    @PostMapping("/remove")
    public Result<?> remove(@Valid @RequestBody FavoriteDTO dto) {
        Long userId = SecurityUtils.getCurrentUserId();
        return favoriteService.removeFavorite(userId, dto);
    }
}
