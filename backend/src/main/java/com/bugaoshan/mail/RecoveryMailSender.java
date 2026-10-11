package com.bugaoshan.mail;

public interface RecoveryMailSender {
    void requireConfigured();
    void send(String email,String code,String purpose);
}
