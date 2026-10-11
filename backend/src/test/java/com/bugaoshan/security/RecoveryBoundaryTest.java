package com.bugaoshan.security;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.dto.AccountSecurityDTO;
import com.bugaoshan.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryBoundaryTest {
    @Test void codeHashIsKeyedAndBoundToTheRequestAndCodeFormat() {
        var crypto=new RecoveryCrypto("independent-verification-key-at-least-32-bytes");
        String hash=crypto.hash("request-one","012345");
        assertEquals(64,hash.length()); assertNotEquals("012345",hash);
        assertTrue(crypto.matches("request-one","012345",hash));
        assertFalse(crypto.matches("request-two","012345",hash));
        assertFalse(crypto.matches("request-one","12345",hash));
        assertFalse(new RecoveryCrypto("another-independent-key-at-least-32-bytes").matches("request-one","012345",hash));
        assertThrows(BusinessException.class,() -> new RecoveryCrypto("").hash("request","012345"));
        for(int i=0;i<30;i++) assertTrue(crypto.newCode().matches("[0-9]{6}"));
    }
    @Test void recoveryStateAndCredentialVersionAreNotExposedByOrdinaryUserJson() throws Exception {
        User user=new User(); user.setRecoveryEmail("private@example.com"); user.setCredentialVersion(4);
        user.setPasswordHash("private-hash");
        var mapper=new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        var json=mapper.readTree(mapper.writeValueAsString(user));
        assertFalse(json.has("recovery_email")); assertFalse(json.has("credential_version")); assertFalse(json.has("password_hash"));
    }
    @Test void securityDtoAcceptsTheSnakeCaseFrontendContract() throws Exception {
        var dto=new ObjectMapper().readValue("{\"old_password\":\"old-pass\",\"new_password\":\"new-pass\"}",AccountSecurityDTO.Password.class);
        assertEquals("old-pass",dto.oldPassword()); assertEquals("new-pass",dto.newPassword());
    }
}
