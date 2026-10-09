package com.bugaoshan.service.impl;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.common.PageResult;
import com.bugaoshan.common.Result;
import com.bugaoshan.entity.Goods;
import com.bugaoshan.entity.Order;
import com.bugaoshan.entity.ScoreRecord;
import com.bugaoshan.entity.User;
import com.bugaoshan.mapper.GoodsMapper;
import com.bugaoshan.mapper.OrderMapper;
import com.bugaoshan.mapper.ScoreRecordMapper;
import com.bugaoshan.mapper.UserMapper;
import com.bugaoshan.service.MallService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MallServiceImpl implements MallService {

    private final GoodsMapper goodsMapper;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final ScoreRecordMapper scoreRecordMapper;

    public MallServiceImpl(GoodsMapper goodsMapper, OrderMapper orderMapper,
                           UserMapper userMapper, ScoreRecordMapper scoreRecordMapper) {
        this.goodsMapper = goodsMapper;
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
        this.scoreRecordMapper = scoreRecordMapper;
    }

    @Override
    public Result getGoodsList(Long categoryId, Integer page, Integer pageSize) {
        if (page == null) page = 1;
        if (pageSize == null) pageSize = 10;
        int offset = (page - 1) * pageSize;
        List<Goods> list = goodsMapper.selectList(categoryId, offset, pageSize);
        long total = goodsMapper.countList(categoryId);
        PageResult<Goods> pageResult = new PageResult<>(list, total);
        return Result.ok(pageResult);
    }

    @Override
    public Result getGoodsDetail(Long goodsId) {
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null) {
            throw new BusinessException("商品不存在");
        }
        return Result.ok(goods);
    }

    @Override
    @Transactional
    public Result exchangeGoods(Long userId, Long goodsId) {
        // Serialize allowance checks before stock and balance changes; keep goods -> user lock order.
        Goods goods = goodsMapper.selectByIdForUpdate(goodsId);
        if (goods == null || goods.getStatus() != 1) {
            throw new BusinessException("商品不存在或已下架");
        }
        Integer exchangeLimit = goods.getExchangeLimit();
        if (exchangeLimit != null && exchangeLimit > 0
                && orderMapper.countByUserIdAndGoodsId(userId, goodsId) >= exchangeLimit) {
            throw new BusinessException("已达到该商品的兑换次数上限");
        }
        if (goods.getStock() == null || goods.getStock() <= 0) {
            throw new BusinessException("库存不足");
        }

        User user = userMapper.selectById(userId);
        if (user.getScoreBalance() < goods.getPriceScore()) {
            throw new BusinessException("积分不足");
        }

        int decremented = goodsMapper.decrementStock(goodsId);
        if (decremented == 0) {
            throw new BusinessException("库存不足");
        }

        if (userMapper.deductScoreIfSufficient(userId, goods.getPriceScore()) != 1) {
            throw new BusinessException("积分不足");
        }
        User updatedUser = userMapper.selectById(userId);
        int newBalance = updatedUser.getScoreBalance();

        String orderNo = generateOrderNo();
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setGoodsId(goodsId);
        order.setGoodsName(goods.getGoodsName());
        order.setScoreAmount(goods.getPriceScore());
        order.setStatus(1); // 待发货
        order.setIsDeleted(0);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.insert(order);

        ScoreRecord record = new ScoreRecord();
        record.setUserId(userId);
        record.setChangeType(2);
        record.setChangeAmount(-goods.getPriceScore());
        record.setBalanceAfter(newBalance);
        record.setSourceDesc("兑换商品：" + goods.getGoodsName());
        record.setRelatedId(order.getOrderId());
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        scoreRecordMapper.insert(record);

        Map<String, Object> data = new HashMap<>();
        data.put("orderId", order.getOrderId());
        data.put("orderNo", orderNo);
        data.put("score_balance", newBalance);
        return Result.ok(data);
    }

    @Override
    public Result getExchangeList(Long userId, Integer page, Integer pageSize) {
        if (page == null) page = 1;
        if (pageSize == null) pageSize = 10;
        int offset = (page - 1) * pageSize;
        List<Order> list = orderMapper.selectByUserId(userId, null, offset, pageSize);
        long total = orderMapper.countByUserId(userId, null);
        PageResult<Order> pageResult = new PageResult<>(list, total);
        return Result.ok(pageResult);
    }

    private String generateOrderNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String suffix = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase();
        return "BG" + date + suffix;
    }
}
