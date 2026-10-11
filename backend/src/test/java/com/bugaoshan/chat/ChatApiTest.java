package com.bugaoshan.chat;
import com.bugaoshan.common.*;
import com.bugaoshan.controller.ChatController;
import com.bugaoshan.security.AuthenticatedSession;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class ChatApiTest {
    ChatTestFixture f; MockMvc mvc;
    @BeforeEach void setup() throws Exception {
        f=new ChatTestFixture(); f.seedUsers(); f.friend(1,2);
        var json=new ObjectMapper().findAndRegisterModules().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mvc=MockMvcBuilders.standaloneSetup(new ChatController(f.conversations,f.messages)).setControllerAdvice(new ChatExceptionHandler(),new GlobalExceptionHandler()).setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build(); login(1);
    }
    void login(long id) { var auth=new UsernamePasswordAuthenticationToken(id,null,java.util.List.of()); auth.setDetails(new AuthenticatedSession(id==3?3:1,0)); SecurityContextHolder.getContext().setAuthentication(auth); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void fiveEntriesUseSnakeCaseAndStringIds() throws Exception {
        mvc.perform(post("/api/chat/conversations/private").contentType(MediaType.APPLICATION_JSON).content("{\"target_user_id\":2}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.conversation_id").value("1")).andExpect(jsonPath("$.data.can_send").value(true));
        mvc.perform(get("/api/chat/conversations")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].peer.user_id").value("2"));
        mvc.perform(get("/api/chat/conversations/1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.last_sequence").value("0"));
        mvc.perform(put("/api/chat/conversations/1/read").contentType(MediaType.APPLICATION_JSON).content("{\"last_read_sequence\":\"0\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.last_read_sequence").value("0"));
        mvc.perform(get("/api/chat/unread")).andExpect(status().isOk()).andExpect(jsonPath("$.data.unread_count").value(0));
    }
    @Test void errorsCarryMatchingHttpStatusAndBusinessCode() throws Exception {
        f.conversations.openPrivate(f.actor(1,0),2); login(3);
        mvc.perform(get("/api/chat/conversations/1")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
        mvc.perform(get("/api/chat/conversations/999")).andExpect(status().isNotFound()); login(1);
        mvc.perform(get("/api/chat/conversations/bad")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/chat/conversations").param("cursor","garbage")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/chat/conversations").param("limit","101")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/chat/conversations/1/read").contentType(MediaType.APPLICATION_JSON).content("{\"last_read_sequence\":\"oops\"}"))
            .andExpect(status().isBadRequest());
        f.db.update("DELETE FROM friend");
        mvc.perform(post("/api/chat/conversations/private").contentType(MediaType.APPLICATION_JSON).content("{\"target_user_id\":2}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403));
        SecurityContextHolder.clearContext(); mvc.perform(get("/api/chat/unread")).andExpect(status().isUnauthorized());
    }
    @Test void sendAndHistoryEnforceTypesIdempotencyAndRateStatus() throws Exception {
        f.conversations.openPrivate(f.actor(1,0),2); f.config.setSendPerMinute(1);
        String uuid="11111111-1111-4111-8111-111111111111";
        String body="{\"client_message_id\":\""+uuid+"\",\"message_type\":\"TEXT\",\"text\":\"<script>literal</script>\"}";
        mvc.perform(post("/api/chat/conversations/1/messages").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.sequence").value("1")).andExpect(jsonPath("$.data.sender_id").value("1"));
        mvc.perform(post("/api/chat/conversations/1/messages").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(post("/api/chat/conversations/1/messages").contentType(MediaType.APPLICATION_JSON).content(body.replace("literal","changed")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(409));
        mvc.perform(post("/api/chat/conversations/1/messages").contentType(MediaType.APPLICATION_JSON).content(body.replace(uuid,"22222222-2222-4222-8222-222222222222")))
            .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        mvc.perform(post("/api/chat/conversations/1/messages").contentType(MediaType.APPLICATION_JSON).content(body.replace(uuid,"bad"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/chat/conversations/1/messages").param("after_sequence","0"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].content.text").value("<script>literal</script>"));
        mvc.perform(get("/api/chat/conversations/1/messages").param("before_sequence","1").param("after_sequence","0")).andExpect(status().isBadRequest());
    }
}
