package com.bugaoshan.controller;

import com.bugaoshan.common.GlobalExceptionHandler;
import com.bugaoshan.entity.User;
import com.bugaoshan.mapper.UserMapper;
import com.bugaoshan.service.AuthService;
import com.bugaoshan.storage.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserProfileUpdateTest {

    @Mock private AuthService authService;
    @Mock private UserMapper userMapper;
    @Mock private FileStorageService fileStorageService;
    private MockMvc mvc;
    private User current;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(12L, null));
        current = new User();
        current.setUserId(12L);
        current.setUsername("student01");
        current.setNickname("同学甲");
        current.setDepartment("原院系");
        current.setPhone("13900000000");
        current.setEmail("old@example.com");
        lenient().when(userMapper.selectById(12L)).thenReturn(current);
        ObjectMapper json = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mvc = MockMvcBuilders.standaloneSetup(new UserController(authService, userMapper, fileStorageService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blankContactFieldsAreStoredAsNullSoMultipleUsersCanLeaveThemUnset() throws Exception {
        update("{\"nickname\":\"同学甲\",\"phone\":\"  \",\"email\":\"\"}")
                .andExpect(status().isOk());

        User saved = savedUser();
        assertNull(saved.getPhone());
        assertNull(saved.getEmail());
    }

    @Test
    void acceptsExistingCamelCaseFormFieldsWithoutChangingTheAccount() throws Exception {
        update("{\"nickname\":\"同学乙\",\"className\":\"计科246\",\"studentNo\":\"2024051224\",\"officeLocation\":\"B507\",\"username\":\"other\"}")
                .andExpect(status().isOk());

        User saved = savedUser();
        assertEquals("计科246", saved.getClassName());
        assertEquals("2024051224", saved.getStudentNo());
        assertEquals("B507", saved.getOfficeLocation());
        assertEquals("student01", saved.getUsername());
    }

    @Test
    void acceptsSnakeCaseFieldsUsedByTheConfiguredJsonContract() throws Exception {
        update("{\"nickname\":\"同学甲\",\"class_name\":\"计科246\",\"student_no\":\"2024051224\",\"office_location\":\"B507\"}")
                .andExpect(status().isOk());

        User saved = savedUser();
        assertEquals("计科246", saved.getClassName());
        assertEquals("2024051224", saved.getStudentNo());
        assertEquals("B507", saved.getOfficeLocation());
    }

    @Test
    void omittedFieldsRemainUnchangedInAPartialProfileUpdate() throws Exception {
        update("{\"nickname\":\"同学乙\"}").andExpect(status().isOk());

        User saved = savedUser();
        assertEquals("13900000000", saved.getPhone());
        assertEquals("old@example.com", saved.getEmail());
        assertEquals("原院系", saved.getDepartment());
    }

    @Test
    void rejectsBlankNicknameBeforeWriting() throws Exception {
        update("{\"nickname\":\"  \"}").andExpect(status().isBadRequest());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void rejectsInvalidEmailBeforeWriting() throws Exception {
        update("{\"email\":\"not-an-email\"}").andExpect(status().isBadRequest());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void rejectsInvalidPhoneBeforeWriting() throws Exception {
        update("{\"phone\":\"123\"}").andExpect(status().isBadRequest());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void rejectsOversizedNicknameBeforeWriting() throws Exception {
        update("{\"nickname\":\"" + "甲".repeat(21) + "\"}")
                .andExpect(status().isBadRequest());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void anotherUsersPhoneProducesAReadableConflict() throws Exception {
        User owner = new User();
        owner.setUserId(99L);
        lenient().when(userMapper.selectByPhone("13800000000")).thenReturn(owner);

        update("{\"phone\":\"13800000000\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(409));
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void concurrentPhoneCollisionAlsoProducesAReadableConflict() throws Exception {
        doThrow(new DuplicateKeyException("duplicate phone"))
                .when(userMapper).updateById(any(User.class));

        update("{\"phone\":\"13800000000\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void missingProfileProducesAnExplicitBusinessError() throws Exception {
        when(userMapper.selectById(12L)).thenReturn(null);

        update("{\"nickname\":\"同学甲\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(404));
    }

    private org.springframework.test.web.servlet.ResultActions update(String json) throws Exception {
        return mvc.perform(put("/api/user/profile").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private User savedUser() {
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(saved.capture());
        return saved.getValue();
    }
}
