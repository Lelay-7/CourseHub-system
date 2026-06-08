package com.bugaoshan.mapper;

import com.bugaoshan.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    User selectByUsername(@Param("username") String username);

    User selectByPhone(@Param("phone") String phone);

    User selectById(@Param("userId") Long userId);

    int insert(User user);

    int updateById(User user);

    int updateScoreBalance(@Param("userId") Long userId, @Param("scoreBalance") Integer scoreBalance, @Param("totalScore") Integer totalScore);

    int updateAvatar(@Param("userId") Long userId, @Param("avatarUrl") String avatarUrl);

    List<User> selectByUserType(@Param("userType") Integer userType);

    List<User> selectTopScoreRank(@Param("limit") Integer limit);
}
