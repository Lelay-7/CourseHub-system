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

    long countPendingByUserId(@Param("userId") Long userId);
}
