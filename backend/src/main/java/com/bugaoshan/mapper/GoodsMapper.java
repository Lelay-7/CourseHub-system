package com.bugaoshan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bugaoshan.entity.Goods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GoodsMapper extends BaseMapper<Goods> {

    List<Goods> selectList(@Param("categoryId") Long categoryId, @Param("offset") Integer offset, @Param("limit") Integer limit);

    long countList(@Param("categoryId") Long categoryId);

    Goods selectById(@Param("goodsId") Long goodsId);

    int decrementStock(@Param("goodsId") Long goodsId);

    int incrementStock(@Param("goodsId") Long goodsId);

    int deleteById(@Param("goodsId") Long goodsId);

    int updateGoods(Goods goods);

    List<Goods> selectAllForAdmin(@Param("keyword") String keyword,
                                  @Param("status") Integer status,
                                  @Param("offset") Integer offset,
                                  @Param("limit") Integer limit);

    long countAllForAdmin(@Param("keyword") String keyword, @Param("status") Integer status);

    int updateStatus(@Param("goodsId") Long goodsId, @Param("status") Integer status);
}
