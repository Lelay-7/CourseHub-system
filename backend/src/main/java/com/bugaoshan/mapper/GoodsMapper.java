package com.bugaoshan.mapper;

import com.bugaoshan.entity.Goods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GoodsMapper {

    List<Goods> selectList(@Param("categoryId") Long categoryId, @Param("offset") Integer offset, @Param("limit") Integer limit);

    long countList(@Param("categoryId") Long categoryId);

    Goods selectById(@Param("goodsId") Long goodsId);

    int decrementStock(@Param("goodsId") Long goodsId);
}
