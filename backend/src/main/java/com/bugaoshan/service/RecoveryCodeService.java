package com.bugaoshan.service;

import com.bugaoshan.entity.RecoveryCode;
import com.bugaoshan.entity.User;
import com.bugaoshan.mapper.RecoveryMapper;
import com.bugaoshan.security.RecoveryCrypto;
import org.springframework.stereotype.Service;
import java.util.Objects;

@Service
public class RecoveryCodeService {
    private final RecoveryMapper mapper;
    private final RecoveryCrypto crypto;
    public RecoveryCodeService(RecoveryMapper mapper,RecoveryCrypto crypto) { this.mapper=mapper; this.crypto=crypto; }
    public record Delivery(String requestId,Long userId,String email,String code,String purpose) {
        @Override public String toString() { return "RecoveryDelivery[requestId="+requestId+"]"; }
    }
    public void requireConfigured() { crypto.requireConfigured(); }
    /** User row is already locked by caller; supersession and reservation share its transaction. */
    public Delivery prepare(User user,String purpose,String email,String id) {
        String code=crypto.newCode(); var now=mapper.now();
        mapper.invalidatePurpose(user.getUserId(),purpose);
        var record=new RecoveryCode(); record.setRequestId(id); record.setUserId(user.getUserId());
        record.setPurpose(purpose); record.setEmail(email); record.setCredentialVersion(user.getCredentialVersion());
        record.setCodeHash(crypto.hash(id,code)); record.setExpiresAt(now.plusMinutes(10)); record.setCreatedAt(now);
        mapper.insert(record);
        return new Delivery(id,user.getUserId(),email,code,purpose);
    }
    public boolean activate(String id) { return mapper.activate(id)==1; }
    public void fail(String id) { mapper.invalidateRequest(id); }
    public void invalidateAll(Long userId) { mapper.invalidateAll(userId); }
    public void invalidateReset(Long userId) { mapper.invalidatePurpose(userId,"RESET"); }

    /** Return false instead of throwing so wrong-attempt updates commit in the caller's transaction. */
    public boolean consume(User user,String purpose,String email,String id,String code) {
        var record=mapper.selectForUpdate(id);
        if(record==null || !Objects.equals(record.getUserId(),user.getUserId())
                || !purpose.equals(record.getPurpose()) || !email.equals(record.getEmail())
                || !Objects.equals(record.getCredentialVersion(),user.getCredentialVersion())
                || !"ACTIVE".equals(record.getSendStatus()) || record.getConsumedAt()!=null
                || record.getAttempts()>=5 || !record.getExpiresAt().isAfter(mapper.now())) return false;
        if(!crypto.matches(id,code,record.getCodeHash())) { mapper.wrongAttempt(id); return false; }
        return mapper.consume(id)==1;
    }
}
