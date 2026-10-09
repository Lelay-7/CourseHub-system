package com.bugaoshan.mapper;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.entity.Goods;
import com.bugaoshan.entity.Order;
import com.bugaoshan.entity.User;
import com.bugaoshan.service.impl.MallServiceImpl;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ExchangeConcurrencyTest {

    @Test
    void simultaneousRequestsCannotExceedOneExchangePerUser() throws Exception {
        Configuration mappings = BusinessSqlContractTest.mapperConfiguration("GoodsMapper");
        ReentrantLock goodsRow = new ReentrantLock();
        CyclicBarrier unlockedCounts = new CyclicBarrier(2);
        AtomicInteger orders = new AtomicInteger();
        AtomicInteger balance = new AtomicInteger(1000);
        AtomicInteger stock = new AtomicInteger(10);
        Goods goods = new Goods();
        goods.setGoodsId(2L);
        goods.setGoodsName("Book");
        goods.setPriceScore(80);
        goods.setStock(10);
        goods.setStatus(1);
        goods.setExchangeLimit(1);

        // This double implements row locking from the actual emitted mapper SQL.
        // A transaction holds its simulated row lock until the service call ends.
        GoodsMapper goodsMapper = mock(GoodsMapper.class, invocation -> {
            String method = invocation.getMethod().getName();
            if (method.startsWith("selectById")) {
                String sql = mappings.getMappedStatement("com.bugaoshan.mapper.GoodsMapper." + method)
                        .getBoundSql(Map.of("goodsId", 2L)).getSql();
                if (sql.toUpperCase().contains("FOR UPDATE")) goodsRow.lock();
                return goods;
            }
            if ("decrementStock".equals(method)) {
                stock.decrementAndGet();
                return 1;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        OrderMapper orderMapper = mock(OrderMapper.class, invocation -> {
            if ("countByUserIdAndGoodsId".equals(invocation.getMethod().getName())) {
                int snapshot = orders.get();
                if (!goodsRow.isHeldByCurrentThread()) unlockedCounts.await(3, TimeUnit.SECONDS);
                return (long) snapshot;
            }
            if ("insert".equals(invocation.getMethod().getName())) {
                Order order = invocation.getArgument(0);
                order.setOrderId((long) orders.incrementAndGet());
                return 1;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        UserMapper userMapper = mock(UserMapper.class, invocation -> {
            if ("selectById".equals(invocation.getMethod().getName())) {
                User user = new User();
                user.setUserId(8L);
                user.setStatus(1);
                user.setScoreBalance(balance.get());
                user.setTotalScore(1000);
                return user;
            }
            if ("deductScoreIfSufficient".equals(invocation.getMethod().getName())) {
                balance.addAndGet(-80);
                return 1;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        MallServiceImpl service = new MallServiceImpl(goodsMapper, orderMapper, userMapper,
                mock(ScoreRecordMapper.class));
        Callable<Boolean> request = () -> {
            try {
                service.exchangeGoods(8L, 2L);
                return true;
            } catch (BusinessException expectedLimit) {
                return false;
            } finally {
                if (goodsRow.isHeldByCurrentThread()) goodsRow.unlock();
            }
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(request);
            Future<Boolean> second = executor.submit(request);
            int successfulRequests = (first.get(5, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(5, TimeUnit.SECONDS) ? 1 : 0);

            assertEquals(1, successfulRequests);
            assertEquals(1, orders.get());
            assertEquals(920, balance.get());
            assertEquals(9, stock.get());
        } finally {
            executor.shutdownNow();
        }
    }
}
