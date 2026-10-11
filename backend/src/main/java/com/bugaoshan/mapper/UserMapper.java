package com.bugaoshan.mapper;

import com.bugaoshan.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    User selectByUsername(@Param("username") String username);

    User selectCredentialSnapshotByUsername(@Param("username") String username);

    User selectByPhone(@Param("phone") String phone);

    User selectById(@Param("userId") Long userId);

    User selectForUpdate(@Param("userId") Long userId);

    int updateLastLogin(@Param("userId") Long userId);

    int changePassword(@Param("userId") Long userId, @Param("passwordHash") String passwordHash,
                       @Param("version") Integer version);

    int bindRecoveryEmail(@Param("userId") Long userId, @Param("email") String email);

    int insert(User user);

    int updateById(User user);

    int updateScoreBalance(@Param("userId") Long userId, @Param("scoreBalance") Integer scoreBalance, @Param("totalScore") Integer totalScore);

    int deductScoreIfSufficient(@Param("userId") Long userId, @Param("amount") Integer amount);

    int creditScore(@Param("userId") Long userId, @Param("amount") Integer amount);

    int earnScore(@Param("userId") Long userId, @Param("amount") Integer amount);

    int updateAvatar(@Param("userId") Long userId, @Param("avatarUrl") String avatarUrl,
                     @Param("avatarStorageKey") String avatarStorageKey);

    List<User> selectByUserType(@Param("userType") Integer userType);

    List<User> selectTopScoreRank(@Param("limit") Integer limit);

    List<User> selectAdminPage(@Param("keyword") String keyword,
                               @Param("userType") Integer userType,
                               @Param("status") Integer status,
                               @Param("offset") Integer offset,
                               @Param("limit") Integer limit);

    long countAdminPage(@Param("keyword") String keyword,
                        @Param("userType") Integer userType,
                        @Param("status") Integer status);

    int updateStatus(@Param("userId") Long userId, @Param("status") Integer status);
}
