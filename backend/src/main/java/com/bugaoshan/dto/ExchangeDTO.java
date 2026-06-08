package com.bugaoshan.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExchangeDTO {
    @NotNull(message = "商品ID不能为空")
    private Long goodsId;
}
