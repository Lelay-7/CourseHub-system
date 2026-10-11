package com.bugaoshan.security;

import com.bugaoshan.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class RecoveryCrypto {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();
    public RecoveryCrypto(@Value("${recovery.hmac-secret:}") String secret) { key=secret.getBytes(StandardCharsets.UTF_8); }
    public void requireConfigured() {
        if(key.length < 32) throw new BusinessException(503,"邮件验证服务尚未配置，请稍后重试");
    }
    public String newCode() { return String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000)); }
    public String hash(String id,String code) {
        requireConfigured();
        try {
            Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((id+":"+code).getBytes(StandardCharsets.UTF_8)));
        } catch(java.security.GeneralSecurityException e) { throw new IllegalStateException("Cannot initialize verification cryptography",e); }
    }
    public boolean matches(String id,String code,String expected) {
        return code != null && code.matches("[0-9]{6}") && expected != null
                && MessageDigest.isEqual(hash(id,code).getBytes(StandardCharsets.US_ASCII),expected.getBytes(StandardCharsets.US_ASCII));
    }
    public static String scopeKey(String scope) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
}
