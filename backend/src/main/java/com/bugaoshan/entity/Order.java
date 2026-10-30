package com.bugaoshan.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Order {
    private Long orderId;
    private String orderNo;
    private Long userId;
    private Long goodsId;
    private String goodsName;
    private Integer scoreAmount;
    private Integer status;
    private String remark;
    private String fulfillmentNote;
    private LocalDateTime cancelTime;
    private LocalDateTime completeTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
