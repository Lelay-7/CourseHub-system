package com.bugaoshan.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.util.Arrays;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] additionalOrigins;

    public CorsConfig(@Value("${app.cors.additional-origins:}") String additionalOrigins) {
        this.additionalOrigins = Arrays.stream(additionalOrigins.split(","))
                .map(String::trim).filter(origin -> !origin.isEmpty()).distinct().toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(additionalOrigins)
                .allowedOriginPatterns(
                        "http://localhost:3000",
                        "http://127.0.0.1:3000",
                        "https://bugaoshan.com",
                        "https://www.bugaoshan.com"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
