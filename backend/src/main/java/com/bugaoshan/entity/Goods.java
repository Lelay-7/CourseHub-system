package com.bugaoshan.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Goods {
    private Long goodsId;
    private String goodsName;
    private Integer category;
    private String imageUrls;
    private Integer priceScore;
    private Integer stock;
    private Integer exchangeLimit;
    private String description;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
}
