package com.bugaoshan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExchangeDTO {
    @NotNull(message = "商品ID不能为空")
    @JsonProperty("goodsId")
    private Long goodsId;
}
