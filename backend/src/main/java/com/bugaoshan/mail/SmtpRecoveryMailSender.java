package com.bugaoshan.mail;

import com.bugaoshan.common.BusinessException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpRecoveryMailSender implements RecoveryMailSender {
    private final JavaMailSender sender;
    private final boolean enabled;
    private final String from;
    private final String host;
    public SmtpRecoveryMailSender(ObjectProvider<JavaMailSender> sender,
            @Value("${recovery.enabled:false}") boolean enabled,
            @Value("${recovery.from:}") String from,@Value("${spring.mail.host:}") String host) {
        this.sender=sender.getIfAvailable(); this.enabled=enabled; this.from=from; this.host=host;
    }
    @Override public void requireConfigured() {
        if(!enabled || sender==null || from.isBlank() || host.isBlank())
            throw new BusinessException(503,"邮件服务尚未配置，请稍后重试");
    }
    @Override public void send(String email,String code,String purpose) {
        requireConfigured(); var message=new SimpleMailMessage();
        message.setFrom(from); message.setTo(email); message.setSubject("CourseHub 账号验证");
        message.setText(("BIND".equals(purpose)?"绑定找回邮箱":"找回密码")+"的验证码："+code
                +"。10分钟内有效，请勿向任何人提供。若非本人操作，请忽略此邮件。");
        sender.send(message);
    }
}
