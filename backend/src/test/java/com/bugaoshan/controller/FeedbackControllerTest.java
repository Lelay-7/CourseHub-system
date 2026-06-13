package com.bugaoshan.controller;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.entity.Feedback;
import com.bugaoshan.mapper.FeedbackMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FeedbackControllerTest {

    @Mock private FeedbackMapper feedbackMapper;
    private FeedbackController controller;

    @BeforeEach
    void setUp() {
        controller = new FeedbackController(feedbackMapper);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsUnsupportedFeedbackType() {
        assertThrows(BusinessException.class,
                () -> controller.submit(feedback("security", "hello")));

        verify(feedbackMapper, never()).insert(any(Feedback.class));
    }

    @Test
    void rejectsBlankFeedbackContent() {
        assertThrows(BusinessException.class,
                () -> controller.submit(feedback("功能建议", "   ")));

        verify(feedbackMapper, never()).insert(any(Feedback.class));
    }

    @Test
    void rejectsFeedbackContentOverOneThousandCharacters() {
        assertThrows(BusinessException.class,
                () -> controller.submit(feedback("功能建议", "x".repeat(1001))));

        verify(feedbackMapper, never()).insert(any(Feedback.class));
    }

    @Test
    void trimsValidFeedbackBeforeInsert() {
        controller.submit(feedback("功能建议", "  improve search  "));

        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackMapper).insert(captor.capture());
        assertEquals("improve search", captor.getValue().getContent());
        assertEquals(7L, captor.getValue().getUserId());
    }

    private Feedback feedback(String type, String content) {
        Feedback feedback = new Feedback();
        feedback.setType(type);
        feedback.setContent(content);
        return feedback;
    }
}
