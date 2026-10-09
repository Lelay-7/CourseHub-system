package com.bugaoshan.service.impl;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.entity.Goods;
import com.bugaoshan.entity.User;
import com.bugaoshan.mapper.GoodsMapper;
import com.bugaoshan.mapper.OrderMapper;
import com.bugaoshan.mapper.ScoreRecordMapper;
import com.bugaoshan.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MallServiceImplTest {

    @Mock private GoodsMapper goodsMapper;
    @Mock private OrderMapper orderMapper;
    @Mock private UserMapper userMapper;
    @Mock private ScoreRecordMapper scoreRecordMapper;

    @Test
    void exchangeFailsWhenAtomicBalanceDeductionAffectsNoRows() {
        Goods goods = new Goods();
        goods.setGoodsId(2L);
        goods.setGoodsName("book");
        goods.setPriceScore(80);
        goods.setStock(2);
        goods.setStatus(1);
        goods.setExchangeLimit(1);
        User user = new User();
        user.setUserId(8L);
        user.setScoreBalance(100);
        user.setTotalScore(100);
        when(goodsMapper.selectByIdForUpdate(2L)).thenReturn(goods);
        when(userMapper.selectById(8L)).thenReturn(user);
        when(goodsMapper.decrementStock(2L)).thenReturn(1);
        when(userMapper.deductScoreIfSufficient(8L, 80)).thenReturn(0);
        MallServiceImpl service = new MallServiceImpl(goodsMapper, orderMapper, userMapper, scoreRecordMapper);

        assertThrows(BusinessException.class, () -> service.exchangeGoods(8L, 2L));
    }

    @Test
    void generatedOrderNumberHasLargeRandomSuffix() throws Exception {
        MallServiceImpl service = new MallServiceImpl(goodsMapper, orderMapper, userMapper, scoreRecordMapper);
        Method method = MallServiceImpl.class.getDeclaredMethod("generateOrderNo");
        method.setAccessible(true);

        String orderNo = (String) method.invoke(service);

        assertTrue(orderNo.matches("BG\\d{8}[A-F0-9]{12}"));
    }

    @Test
    void exchangeLimitIsCheckedBeforeStockAndScoreChanges() {
        Goods goods = new Goods();
        goods.setGoodsId(2L);
        goods.setStatus(1);
        goods.setStock(5);
        goods.setExchangeLimit(1);
        when(goodsMapper.selectByIdForUpdate(2L)).thenReturn(goods);
        when(orderMapper.countByUserIdAndGoodsId(8L, 2L)).thenReturn(1L);
        MallServiceImpl service = new MallServiceImpl(goodsMapper, orderMapper, userMapper, scoreRecordMapper);

        assertThrows(BusinessException.class, () -> service.exchangeGoods(8L, 2L));

        verify(goodsMapper, never()).decrementStock(2L);
        verify(userMapper, never()).deductScoreIfSufficient(8L, goods.getPriceScore());
    }
}
