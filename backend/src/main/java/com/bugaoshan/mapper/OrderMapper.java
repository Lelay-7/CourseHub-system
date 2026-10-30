package com.bugaoshan.mapper;

import com.bugaoshan.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper {

    List<Order> selectByUserId(@Param("userId") Long userId, @Param("status") Integer status, @Param("offset") Integer offset, @Param("limit") Integer limit);

    long countByUserId(@Param("userId") Long userId, @Param("status") Integer status);

    Order selectById(@Param("orderId") Long orderId);

    int insert(Order order);

    int updateStatus(@Param("orderId") Long orderId, @Param("status") Integer status, @Param("cancelTime") LocalDateTime cancelTime, @Param("completeTime") LocalDateTime completeTime);

    int updateStatusIfCurrent(@Param("orderId") Long orderId,
                              @Param("expectedStatus") Integer expectedStatus,
                              @Param("status") Integer status,
                              @Param("cancelTime") LocalDateTime cancelTime,
                              @Param("completeTime") LocalDateTime completeTime);

    int completeIfProcessable(@Param("orderId") Long orderId);

    int completeWithNote(@Param("orderId") Long orderId, @Param("note") String note);

    int softDelete(@Param("orderId") Long orderId, @Param("userId") Long userId);

    long countByUserIdAndGoodsId(@Param("userId") Long userId, @Param("goodsId") Long goodsId);

    long countPendingByUserId(@Param("userId") Long userId);

    List<Order> selectAll(@Param("status") Integer status, @Param("offset") Integer offset, @Param("limit") Integer limit);
    long countAll(@Param("status") Integer status);
}
