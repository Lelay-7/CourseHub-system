package com.bugaoshan.service;
import com.bugaoshan.mapper.ChatRateLimitMapper;
import com.bugaoshan.config.ChatConfig;
import com.bugaoshan.common.ChatException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
@Service
public class ChatSendRateLimiter {
    private final ChatRateLimitMapper mapper;
    private final ChatConfig config;
    private final Clock clock;
    public ChatSendRateLimiter(ChatRateLimitMapper mapper,ChatConfig config,@Qualifier("chatClock") Clock clock) { this.mapper=mapper; this.config=config; this.clock=clock; }
    public void reserve(long userId,Instant now) {
        if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Quota requires send transaction");
        for(int seconds:new int[]{60,3600}) {
            long epoch=Math.floorDiv(now.getEpochSecond(),seconds)*seconds;
            var start=LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch),ZoneOffset.UTC);
            var expires=start.plusSeconds(seconds).plusHours(config.getRateRetentionHours());
            mapper.ensure(userId,start,seconds,expires); var row=mapper.lock(userId,start,seconds);
            int limit=seconds==60?config.getSendPerMinute():config.getSendPerHour();
            if(row.getUsedCount()>=limit) throw new ChatException(429,"发送过于频繁，请稍后重试",Math.max(1,epoch+seconds-now.getEpochSecond()));
            mapper.increment(userId,start,seconds);
        }
    }
    public int purgeExpired(Instant cutoff,int batchSize) {
        if(batchSize<1 || batchSize>1000) throw new IllegalArgumentException("Cleanup batch must be 1-1000");
        return mapper.purgeExpired(LocalDateTime.ofInstant(cutoff,ZoneOffset.UTC),batchSize);
    }
    @Scheduled(fixedDelay=600000,initialDelay=600000) public void cleanup() { purgeExpired(clock.instant(),config.getCleanupBatchSize()); }
}
