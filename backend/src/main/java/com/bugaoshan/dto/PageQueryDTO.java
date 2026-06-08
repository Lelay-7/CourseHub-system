package com.bugaoshan.dto;

import lombok.Data;

@Data
public class PageQueryDTO {
    private Integer page = 1;
    private Integer pageSize = 20;
}
