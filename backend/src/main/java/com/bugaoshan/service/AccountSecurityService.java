package com.bugaoshan.service;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.entity.User;
import com.bugaoshan.mail.RecoveryMailSender;
import com.bugaoshan.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
public class AccountSecurityService {
    private static final Logger log=LoggerFactory.getLogger(AccountSecurityService.class);
    private static final String RESET_MESSAGE="如果账号与邮箱匹配，验证码将发送至该邮箱";
    private static final String CODE_ERROR="验证码错误、已失效或尝试次数过多，请重新发送";
    private final UserMapper users;
    private final RecoveryCodeService codes;
    private final RecoveryRateLimiter rates;
    private final RecoveryMailSender mail;
    private final Executor executor;
    private final BCryptPasswordEncoder encoder;
    private final TransactionTemplate tx;
    public AccountSecurityService(UserMapper users,RecoveryCodeService codes,RecoveryRateLimiter rates,
            RecoveryMailSender mail,@Qualifier("recoveryExecutor") Executor executor,
            BCryptPasswordEncoder encoder,PlatformTransactionManager manager) {
        this.users=users; this.codes=codes; this.rates=rates; this.mail=mail; this.executor=executor;
        this.encoder=encoder; this.tx=new TransactionTemplate(manager);
        // Explicit user/scope row locks serialize security decisions. RR's empty-range gap locks
        // can deadlock independent accounts when they insert their first event or code.
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public record SecurityInfo(String recoveryEmailMasked,boolean recoveryEmailVerified) {}
    public record CodeResponse(String requestId,String message) {}
    private record ResetTicket(boolean allowed,Long userId,Integer version) {}
    public SecurityInfo security(Long userId) {
        User user=active(users.selectById(userId));
        boolean verified=user.getRecoveryEmail()!=null && user.getRecoveryEmailVerifiedAt()!=null;
        return new SecurityInfo(verified?mask(user.getRecoveryEmail()):null,verified);
    }
    public CodeResponse requestBindingCode(Long userId,String password,String email,String ip) {
        available(); String normalized=email(email); String id=UUID.randomUUID().toString();
        var delivery=tx.execute(status -> {
            User user=active(users.selectForUpdate(userId)); originalPassword(user,password);
            if(!rates.reserve(List.of(limit("user:BIND:"+userId),limit("email:BIND:"+normalized),ipLimit(ip))))
                throw new BusinessException(429,"发送过于频繁，请稍后重试");
            return codes.prepare(user,"BIND",normalized,id);
        });
        if(!send(delivery)) throw new BusinessException(503,"验证码发送失败或请求已失效，请稍后重试");
        return new CodeResponse(id,"验证码已提交发送，请查看邮箱");
    }
    public void bindEmail(Long userId,String password,String email,String id,String code) {
        String normalized=email(email); String failure;
        try {
            failure=tx.execute(status -> {
                User user=active(users.selectForUpdate(userId)); originalPassword(user,password);
                if(!codes.consume(user,"BIND",normalized,id,code)) return CODE_ERROR;
                if(users.bindRecoveryEmail(userId,normalized)!=1) throw new BusinessException("绑定失败，请重试");
                codes.invalidateReset(userId);
                return null;
            });
        } catch(DuplicateKeyException e) { throw new BusinessException(409,"该找回邮箱已被其他账号使用"); }
        if(failure!=null) throw new BusinessException(failure);
    }
    public void changePassword(Long userId,String oldPassword,String newPassword) {
        password(newPassword);
        tx.executeWithoutResult(status -> {
            User user=active(users.selectForUpdate(userId)); originalPassword(user,oldPassword);
            updatePassword(user,newPassword);
        });
    }
    public CodeResponse requestResetCode(String account,String email,String ip) {
        available(); String normalizedAccount=account(account), normalizedEmail=email(email);
        String id=UUID.randomUUID().toString();
        // Every input performs the same indexed snapshot query; matching and SMTP stay in the queue.
        // Capture the version before responding so an old queued request cannot survive a password change.
        ResetTicket ticket=tx.execute(status -> {
            boolean allowed=rates.reserve(List.of(
                    limit("account:RESET:"+normalizedAccount.toLowerCase(Locale.ROOT)),ipLimit(ip)));
            User snapshot=users.selectCredentialSnapshotByUsername(normalizedAccount);
            return new ResetTicket(allowed,snapshot==null?null:snapshot.getUserId(),
                    snapshot==null?null:snapshot.getCredentialVersion());
        });
        if(ticket.allowed()) {
            try { executor.execute(() -> deliverReset(normalizedEmail,id,ticket.userId(),ticket.version())); }
            catch(RejectedExecutionException e) { log.warn("Recovery queue full; request={}",id); }
        }
        return new CodeResponse(id,RESET_MESSAGE);
    }
    private void deliverReset(String email,String id,Long userId,Integer version) {
        try {
            var delivery=tx.execute(status -> {
                if(userId==null) return null;
                User user=users.selectForUpdate(userId);
                if(!recoverable(user,email) || !Objects.equals(version,user.getCredentialVersion())) return null;
                // Also limits canonical user identity when database collation equates account spellings.
                if(!rates.reserve(List.of(limit("user:RESET:"+user.getUserId())))) return null;
                return codes.prepare(user,"RESET",email,id);
            });
            if(delivery!=null) send(delivery);
        } catch(RuntimeException e) { log.warn("Recovery request failed; request={}, type={}",id,e.getClass().getSimpleName()); }
    }
    public void resetPassword(String account,String email,String id,String code,String newPassword) {
        password(newPassword); String normalizedAccount=account(account), normalizedEmail=email(email);
        String failure=tx.execute(status -> {
            User found=users.selectByUsername(normalizedAccount);
            if(found==null) return CODE_ERROR;
            User user=users.selectForUpdate(found.getUserId());
            if(!recoverable(user,normalizedEmail)) return CODE_ERROR;
            if(!codes.consume(user,"RESET",normalizedEmail,id,code)) return CODE_ERROR;
            updatePassword(user,newPassword);
            return null;
        });
        if(failure!=null) throw new BusinessException(failure);
    }
    private void updatePassword(User user,String password) {
        if(users.changePassword(user.getUserId(),encoder.encode(password),user.getCredentialVersion())!=1)
            throw new BusinessException(409,"账号状态已改变，请重试");
        codes.invalidateAll(user.getUserId());
    }
    private boolean send(RecoveryCodeService.Delivery delivery) {
        try {
            mail.send(delivery.email(),delivery.code(),delivery.purpose());
            // Follow the same user -> code lock order as password changes and supersession.
            // Otherwise UPDATE's user subquery can deadlock a code-first activation with a change.
            return Boolean.TRUE.equals(tx.execute(status -> {
                users.selectForUpdate(delivery.userId());
                return codes.activate(delivery.requestId());
            }));
        }
        catch(RuntimeException e) {
            codes.fail(delivery.requestId());
            log.warn("Recovery delivery failed; request={}, type={}",delivery.requestId(),e.getClass().getSimpleName());
            return false;
        }
    }
    private void available() { mail.requireConfigured(); codes.requireConfigured(); }
    private User active(User user) {
        if(user==null || !Objects.equals(user.getStatus(),1)) throw new BusinessException(401,"登录已失效，请重新登录");
        return user;
    }
    private boolean recoverable(User user,String email) {
        return user!=null && Objects.equals(user.getStatus(),1) && user.getRecoveryEmailVerifiedAt()!=null
                && email.equals(user.getRecoveryEmail());
    }
    private void originalPassword(User user,String password) {
        if(password==null || !encoder.matches(password,user.getPasswordHash())) throw new BusinessException("原密码不正确");
    }
    private static RecoveryRateLimiter.Limit limit(String scope) { return new RecoveryRateLimiter.Limit(scope,5,60); }
    private static RecoveryRateLimiter.Limit ipLimit(String ip) { return new RecoveryRateLimiter.Limit("ip:"+ip,20,0); }
    private static String email(String input) {
        String email=input==null?"":input.trim().toLowerCase(Locale.ROOT);
        if(email.length()>254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new BusinessException("邮箱格式不正确");
        return email;
    }
    private static String account(String input) {
        String account=input==null?"":input.trim();
        if(account.length()<3 || account.length()>20) throw new BusinessException("登录账号长度需在3-20之间");
        return account;
    }
    private static void password(String password) {
        if(password==null || password.isBlank() || password.length()<6 || password.length()>20)
            throw new BusinessException("密码长度需在6-20之间");
    }
    private static String mask(String email) {
        int at=email.indexOf('@'); return email.substring(0,1)+"***"+email.substring(at);
    }
}
