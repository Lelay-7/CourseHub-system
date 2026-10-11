package com.bugaoshan.common;
public class ChatException extends RuntimeException {
    private final int status;
    private final Long retryAfterSeconds;
    public ChatException(int status,String message) { this(status,message,null); }
    public ChatException(int status,String message,Long retryAfterSeconds) { super(message); this.status=status; this.retryAfterSeconds=retryAfterSeconds; }
    public int status() { return status; }
    public Long retryAfterSeconds() { return retryAfterSeconds; }
}
