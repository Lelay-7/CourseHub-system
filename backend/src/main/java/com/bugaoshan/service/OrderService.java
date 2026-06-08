package com.bugaoshan.service;

import com.bugaoshan.common.Result;

public interface OrderService {
    Result getOrderList(Long userId, Integer status, Integer page, Integer pageSize);
    Result getOrderDetail(Long orderId, Long userId);
    Result cancelOrder(Long orderId, Long userId);
}
