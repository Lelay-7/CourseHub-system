package com.bugaoshan.service.impl;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.common.PageResult;
import com.bugaoshan.common.Result;
import com.bugaoshan.entity.Order;
import com.bugaoshan.entity.ScoreRecord;
import com.bugaoshan.entity.User;
import com.bugaoshan.mapper.OrderMapper;
import com.bugaoshan.mapper.ScoreRecordMapper;
import com.bugaoshan.mapper.UserMapper;
import com.bugaoshan.service.OrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final ScoreRecordMapper scoreRecordMapper;

    public OrderServiceImpl(OrderMapper orderMapper, UserMapper userMapper,
                            ScoreRecordMapper scoreRecordMapper) {
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
        this.scoreRecordMapper = scoreRecordMapper;
    }

    @Override
    public Result getOrderList(Long userId, Integer status, Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        List<Order> list = orderMapper.selectByUserId(userId, status, offset, pageSize);
        long total = orderMapper.countByUserId(userId, status);
        PageResult<Order> pageResult = new PageResult<>(list, total);
        return Result.ok(pageResult);
    }

    @Override
    public Result getOrderDetail(Long orderId, Long userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权查看该订单");
        }
        return Result.ok(order);
    }

    @Override
    @Transactional
    public Result cancelOrder(Long orderId, Long userId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != 1) {
            throw new BusinessException("订单状态不正确，无法取消");
        }

        orderMapper.updateStatus(orderId, 4, LocalDateTime.now(), null);

        User user = userMapper.selectById(userId);
        int newBalance = user.getScoreBalance() + order.getScoreAmount();
        userMapper.updateScoreBalance(userId, newBalance, user.getTotalScore());

        ScoreRecord record = new ScoreRecord();
        record.setUserId(userId);
        record.setChangeType(3);
        record.setChangeAmount(order.getScoreAmount());
        record.setBalanceAfter(newBalance);
        record.setSourceDesc("取消订单退款：" + order.getOrderNo());
        record.setRelatedId(orderId);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        scoreRecordMapper.insert(record);

        Map<String, Object> data = new HashMap<>();
        data.put("score_balance", newBalance);
        return Result.ok(data);
    }
}
