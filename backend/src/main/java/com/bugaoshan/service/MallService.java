package com.bugaoshan.service;

import com.bugaoshan.common.Result;

public interface MallService {
    Result getGoodsList(Long categoryId, Integer page, Integer pageSize, String sortBy);
    default Result getGoodsList(Long categoryId, Integer page, Integer pageSize) {
        return getGoodsList(categoryId, page, pageSize, "create_time");
    }
    Result getGoodsDetail(Long goodsId);
    Result exchangeGoods(Long userId, Long goodsId);
    Result getExchangeList(Long userId, Integer page, Integer pageSize);
}
