package com.bugaoshan.mapper;

import com.bugaoshan.entity.RecoveryCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RecoveryMapper {
    LocalDateTime now();
    int insert(RecoveryCode code);
    RecoveryCode selectForUpdate(@Param("requestId") String requestId);
    int invalidatePurpose(@Param("userId") Long userId, @Param("purpose") String purpose);
    int invalidateAll(@Param("userId") Long userId);
    int invalidateRequest(@Param("requestId") String requestId);
    int activate(@Param("requestId") String requestId);
    int wrongAttempt(@Param("requestId") String requestId);
    int consume(@Param("requestId") String requestId);
    int ensureRateLock(@Param("key") String key);
    String lockRate(@Param("key") String key);
    List<LocalDateTime> recentEvents(@Param("key") String key, @Param("cutoff") LocalDateTime cutoff);
    int addRateEvent(@Param("key") String key, @Param("now") LocalDateTime now);
    int pruneRateEvents(@Param("key") String key, @Param("cutoff") LocalDateTime cutoff);
}
