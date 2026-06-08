package com.bugaoshan.service;

import com.bugaoshan.common.Result;
import com.bugaoshan.dto.FavoriteDTO;

public interface FavoriteService {
    Result getFavoriteList(Long userId, String type);
    Result addFavorite(Long userId, FavoriteDTO dto);
    Result removeFavorite(Long userId, FavoriteDTO dto);
}
