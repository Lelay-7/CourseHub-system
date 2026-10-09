package com.bugaoshan.security;

import com.bugaoshan.entity.User;
import com.bugaoshan.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityResponseTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class, SecurityConfig.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void close() { context.close(); }

    @Test
    void anonymousBusinessRequestIsUnauthorizedRatherThanForbidden() throws Exception {
        mvc.perform(get("/api/test")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidAccessTokenIsUnauthorizedSoClientCanRenewItsSession() throws Exception {
        mvc.perform(get("/api/test").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedStudentWithoutAdminRoleIsForbidden() throws Exception {
        String token = context.getBean(JwtUtil.class).generateAccessToken(42L, 1);
        mvc.perform(get("/api/admin/test").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Configuration
    @EnableWebMvc
    static class TestConfig {
        @Bean JwtUtil jwtUtil() {
            return new JwtUtil("test-secret-key-that-is-long-enough-for-hs256", 60_000, 120_000);
        }
        @Bean UserMapper userMapper() {
            UserMapper mapper = mock(UserMapper.class);
            User user = new User();
            user.setUserId(42L);
            user.setUserType(1);
            user.setStatus(1);
            when(mapper.selectById(42L)).thenReturn(user);
            return mapper;
        }
        @Bean TestEndpoints endpoints() { return new TestEndpoints(); }
    }

    @RestController
    static class TestEndpoints {
        @GetMapping({"/api/test", "/api/admin/test"})
        String test() { return "ok"; }
    }
}
