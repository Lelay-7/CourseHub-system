package com.bugaoshan.service.impl;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.entity.Order;
import com.bugaoshan.mapper.GoodsMapper;
import com.bugaoshan.mapper.OrderMapper;
import com.bugaoshan.mapper.ScoreRecordMapper;
import com.bugaoshan.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private OrderMapper orderMapper;
    @Mock private UserMapper userMapper;
    @Mock private ScoreRecordMapper scoreRecordMapper;
    @Mock private GoodsMapper goodsMapper;

    @Test
    void cancellationDoesNotRefundWhenStatusTransitionLosesRace() {
        Order order = new Order();
        order.setOrderId(4L);
        order.setUserId(8L);
        order.setGoodsId(2L);
        order.setScoreAmount(50);
        order.setStatus(1);
        when(orderMapper.selectById(4L)).thenReturn(order);
        when(orderMapper.updateStatusIfCurrent(eq(4L), eq(1), eq(4), any(), eq(null))).thenReturn(0);
        OrderServiceImpl service = new OrderServiceImpl(orderMapper, userMapper, scoreRecordMapper, goodsMapper);

        assertThrows(BusinessException.class, () -> service.cancelOrder(4L, 8L));
        verify(userMapper, never()).creditScore(any(), any());
    }

    @Test
    void userCanDeleteOwnCancelledOrder() {
        Order order = new Order();
        order.setOrderId(5L);
        order.setUserId(8L);
        order.setStatus(4);
        when(orderMapper.selectById(5L)).thenReturn(order);
        when(orderMapper.softDelete(5L, 8L)).thenReturn(1);
        OrderServiceImpl service = new OrderServiceImpl(orderMapper, userMapper, scoreRecordMapper, goodsMapper);

        var result = service.deleteOrder(5L, 8L);

        assertEquals(200, result.getCode());
        verify(orderMapper).softDelete(5L, 8L);
    }

    @Test
    void userCannotDeletePendingOrder() {
        Order order = new Order();
        order.setOrderId(6L);
        order.setUserId(8L);
        order.setStatus(1);
        when(orderMapper.selectById(6L)).thenReturn(order);
        OrderServiceImpl service = new OrderServiceImpl(orderMapper, userMapper, scoreRecordMapper, goodsMapper);

        assertThrows(BusinessException.class, () -> service.deleteOrder(6L, 8L));
        verify(orderMapper, never()).softDelete(any(), any());
    }
}
