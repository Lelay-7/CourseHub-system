package com.bugaoshan.config;
import lombok.Data;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;
@Data @Validated @Configuration @EnableScheduling @ConfigurationProperties(prefix="app.chat")
public class ChatConfig {
    @Min(1) @Max(10000) private int sendPerMinute=30;
    @Min(1) @Max(100000) private int sendPerHour=300;
    @Min(1) @Max(48) private int rateRetentionHours=48;
    @Min(1) @Max(1000) private int cleanupBatchSize=500;
    @Bean("chatClock") public Clock chatClock() { return Clock.systemUTC(); }
}
