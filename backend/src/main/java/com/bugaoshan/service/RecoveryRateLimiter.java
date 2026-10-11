package com.bugaoshan.service;

import com.bugaoshan.mapper.RecoveryMapper;
import com.bugaoshan.security.RecoveryCrypto;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class RecoveryRateLimiter {
    private final RecoveryMapper mapper;
    public RecoveryRateLimiter(RecoveryMapper mapper) { this.mapper=mapper; }
    public record Limit(String scope,int hourly,int intervalSeconds) {}

    /** Caller holds a transaction. Lock every scope in digest order to avoid cross-scope deadlocks. */
    public boolean reserve(List<Limit> limits) {
        var sorted=limits.stream().sorted(Comparator.comparing(l -> RecoveryCrypto.scopeKey(l.scope()))).toList();
        for(var limit:sorted) {
            String key=RecoveryCrypto.scopeKey(limit.scope());
            mapper.ensureRateLock(key); mapper.lockRate(key);
        }
        // Read DB time after waiting for locks, so a contending request cannot use an obsolete timestamp.
        LocalDateTime now=mapper.now();
        for(var limit:sorted) {
            var events=mapper.recentEvents(RecoveryCrypto.scopeKey(limit.scope()),now.minusHours(1));
            if(events.size()>=limit.hourly() || (!events.isEmpty() && limit.intervalSeconds()>0
                    && events.getFirst().isAfter(now.minusSeconds(limit.intervalSeconds())))) return false;
        }
        for(var limit:sorted) {
            String key=RecoveryCrypto.scopeKey(limit.scope());
            mapper.pruneRateEvents(key,now.minusHours(1)); mapper.addRateEvent(key,now);
        }
        return true;
    }
}
