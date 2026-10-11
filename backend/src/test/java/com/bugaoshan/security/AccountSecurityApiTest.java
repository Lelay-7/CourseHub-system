package com.bugaoshan.security;

import com.bugaoshan.common.GlobalExceptionHandler;
import com.bugaoshan.controller.AccountSecurityController;
import com.bugaoshan.entity.User;
import com.bugaoshan.mapper.UserMapper;
import com.bugaoshan.service.AccountSecurityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AccountSecurityApiTest {
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void setup() {
        context=new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(TestConfig.class,SecurityConfig.class,AccountSecurityController.class,GlobalExceptionHandler.class);
        context.refresh(); mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { context.close(); }
    @Test void anonymousResetUsesSnakeCaseAndDoesNotRequireOrRefreshAStaleToken() throws Exception {
        mvc.perform(post("/api/auth/password-reset/code").header("Authorization","Bearer stale")
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"student01\",\"email\":\"safe@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.request_id").value("11111111-1111-4111-8111-111111111111"))
                .andExpect(jsonPath("$.data.requestId").doesNotExist()).andExpect(jsonPath("$.data.code").doesNotExist());
        mvc.perform(post("/api/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"student01\",\"email\":\"safe@example.com\",\"request_id\":\"11111111-1111-4111-8111-111111111111\",\"code\":\"123456\",\"new_password\":\"new-pass\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(context.getBean(AccountSecurityService.class)).resetPassword("student01","safe@example.com",
                "11111111-1111-4111-8111-111111111111","123456","new-pass");
    }
    @Test void securityRequiresAuthenticationAndOnlyReturnsMaskedEmail() throws Exception {
        mvc.perform(get("/api/user/security")).andExpect(status().isUnauthorized());
        String token=context.getBean(JwtUtil.class).generateAccessToken(42L,1,0);
        mvc.perform(get("/api/user/security").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.recovery_email_masked").value("s***@example.com"))
                .andExpect(jsonPath("$.data.recovery_email_verified").value(true))
                .andExpect(jsonPath("$.data.recovery_email").doesNotExist());
    }
    @Test void malformedResetIsRejectedBeforeAccountSecurityService() throws Exception {
        mvc.perform(post("/api/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"student01\",\"email\":\"safe@example.com\",\"request_id\":\"bad\",\"code\":\"123\",\"new_password\":\"tiny\"}"))
                .andExpect(status().isBadRequest());
        verify(context.getBean(AccountSecurityService.class),never()).resetPassword(any(),any(),any(),any(),any());
    }
    @Configuration @EnableWebMvc
    static class TestConfig implements WebMvcConfigurer {
        @Bean JwtUtil jwtUtil() { return new JwtUtil("test-secret-key-that-is-long-enough-for-hs256",60000,120000); }
        @Bean UserMapper userMapper() {
            var mapper=mock(UserMapper.class); var user=new User(); user.setUserId(42L); user.setStatus(1); user.setUserType(1);
            when(mapper.selectById(42L)).thenReturn(user); return mapper;
        }
        @Bean AccountSecurityService accountSecurityService() {
            var service=mock(AccountSecurityService.class);
            when(service.requestResetCode(eq("student01"),eq("safe@example.com"),anyString()))
                    .thenReturn(new AccountSecurityService.CodeResponse("11111111-1111-4111-8111-111111111111","如果账号与邮箱匹配，验证码将发送至该邮箱"));
            when(service.security(42L)).thenReturn(new AccountSecurityService.SecurityInfo("s***@example.com",true));
            return service;
        }
        @Override public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
            converters.add(new MappingJackson2HttpMessageConverter(new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)));
        }
    }
}
