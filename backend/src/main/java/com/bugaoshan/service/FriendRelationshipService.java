package com.bugaoshan.service;

import com.bugaoshan.entity.User;
import com.bugaoshan.mapper.FriendMapper;
import com.bugaoshan.mapper.UserMapper;
import org.springframework.transaction.PlatformTransactionManager;
import com.bugaoshan.common.BusinessException;
import com.bugaoshan.entity.Friend;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;

@Service
public class FriendRelationshipService {
    public record LockedUsers(long lowerId, long higherId, User lower, User higher) {
        public User user(long id) { return id == lowerId ? lower : id == higherId ? higher : null; }
    }
    public record AddFriendResult(boolean acceptedExistingRequest) {}
    private final UserMapper users;
    private final FriendMapper friends;
    private final TransactionTemplate tx;
    public FriendRelationshipService(UserMapper users, FriendMapper friends, PlatformTransactionManager manager) {
        this.users=users; this.friends=friends; tx=new TransactionTemplate(manager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public LockedUsers lockUsers(long actorId, long targetId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("User locks require a transaction");
        long low=Math.min(actorId,targetId),high=Math.max(actorId,targetId);
        User lower=users.selectForUpdate(low);
        User higher=low==high?lower:users.selectForUpdate(high);
        return new LockedUsers(low,high,lower,higher);
    }
    public boolean isAccepted(long actorId, long targetId) {
        Friend pair=friends.selectPair(Math.min(actorId,targetId),Math.max(actorId,targetId));
        return pair!=null && Integer.valueOf(1).equals(pair.getStatus());
    }
    public AddFriendResult addFriend(long actorId, long targetId) {
        if(actorId==targetId) throw new BusinessException("不能添加自己");
        return tx.execute(status->{
            var locked=lockUsers(actorId,targetId); active(locked.user(actorId)); active(locked.user(targetId));
            Friend pair=friends.selectPairForUpdate(locked.lowerId(),locked.higherId());
            if(pair!=null) {
                if(Integer.valueOf(1).equals(pair.getStatus())) throw new BusinessException("已经是好友");
                if(pair.getUserId()==actorId) throw new BusinessException("已发送申请，等待对方同意");
                if(friends.updateStatus(pair.getFriendId(),actorId,1)!=1) throw new BusinessException("好友申请已处理");
                return new AddFriendResult(true);
            }
            Friend f=new Friend(); f.setUserId(actorId); f.setTargetId(targetId); f.setStatus(0);
            f.setCreateTime(LocalDateTime.now()); f.setUpdateTime(f.getCreateTime()); friends.insert(f);
            return new AddFriendResult(false);
        });
    }
    public void accept(long actorId, long friendId) {
        tx.executeWithoutResult(status->{
            Friend hint=participant(actorId,friendId);
            var locked=lockUsers(hint.getUserId(),hint.getTargetId()); active(locked.user(actorId));
            active(locked.user(hint.getUserId())); active(locked.user(hint.getTargetId()));
            Friend pair=friends.selectPairForUpdate(locked.lowerId(),locked.higherId());
            if(pair==null || pair.getFriendId()!=friendId || pair.getTargetId()!=actorId || friends.updateStatus(friendId,actorId,1)!=1)
                throw new BusinessException("好友申请不存在或已处理");
        });
    }
    public void remove(long actorId, long friendId) {
        tx.executeWithoutResult(status->{
            Friend hint=participant(actorId,friendId);
            var locked=lockUsers(hint.getUserId(),hint.getTargetId()); active(locked.user(actorId));
            Friend pair=friends.selectPairForUpdate(locked.lowerId(),locked.higherId());
            if(pair==null || pair.getFriendId()!=friendId || friends.deleteForParticipant(friendId,actorId)!=1)
                throw new BusinessException("好友关系不存在或无权操作");
        });
    }
    private Friend participant(long actorId,long friendId) {
        Friend f=friends.selectById(friendId);
        if(f==null || (f.getUserId()!=actorId && f.getTargetId()!=actorId)) throw new BusinessException("好友关系不存在或无权操作");
        return f;
    }
    private void active(User user) {
        if(user==null || !Integer.valueOf(1).equals(user.getStatus())) throw new BusinessException("用户不存在或不可用");
    }
}
