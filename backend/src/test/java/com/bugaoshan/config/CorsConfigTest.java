package com.bugaoshan.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(CorsConfig.class)
@TestPropertySource(properties="app.cors.additional-origins=http://localhost:3001,http://127.0.0.1:3001")
class CorsConfigTest {
    @Autowired CorsConfig config;
    static class Registry extends CorsRegistry {
        Map<String, CorsConfiguration> configurations() { return getCorsConfigurations(); }
    }
    private CorsConfiguration rules() {
        var registry = new Registry(); config.addCorsMappings(registry);
        return registry.configurations().get("/**");
    }
    private MockHttpServletResponse request(String origin, String method, boolean preflight) throws Exception {
        var request = new MockHttpServletRequest(preflight ? "OPTIONS" : method, "/api/auth/login");
        request.setScheme("http"); request.setServerName("127.0.0.1"); request.setServerPort(8081);
        request.addHeader("Origin", origin);
        if (preflight) {
            request.addHeader("Access-Control-Request-Method", method);
            request.addHeader("Access-Control-Request-Headers", "content-type,authorization");
        }
        var response = new MockHttpServletResponse();
        new DefaultCorsProcessor().processRequest(rules(), request, response);
        return response;
    }
    @Test void acceptsConfiguredPreviewBrowserPostsAndWrites() throws Exception {
        for (String origin : new String[]{"http://localhost:3001","http://127.0.0.1:3001"}) {
            for (String method : new String[]{"POST","PUT","DELETE"}) {
                var response = request(origin, method, false);
                assertEquals(200,response.getStatus());
                assertEquals(origin,response.getHeader("Access-Control-Allow-Origin"));
            }
        }
    }
    @Test void acceptsConfiguredPreviewPreflight() throws Exception {
        assertEquals(200,request("http://localhost:3001","POST",true).getStatus());
    }
    @Test void keepsExistingOriginAndRejectsUnconfiguredOrigins() throws Exception {
        assertEquals(200,request("http://localhost:3000","POST",false).getStatus());
        assertEquals(200,request("https://bugaoshan.com","POST",false).getStatus());
        for (String origin : new String[]{"https://untrusted.example","http://localhost:3009","http://localhost:3001.evil.example"}) {
            assertEquals(403,request(origin,"POST",false).getStatus());
        }
    }
}
