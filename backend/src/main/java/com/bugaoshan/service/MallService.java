package com.bugaoshan.service;

import com.bugaoshan.common.Result;

public interface MallService {
    Result getGoodsList(Long categoryId, Integer page, Integer pageSize);
    Result getGoodsDetail(Long goodsId);
    Result exchangeGoods(Long userId, Long goodsId);
    Result getExchangeList(Long userId, Integer page, Integer pageSize);
}
