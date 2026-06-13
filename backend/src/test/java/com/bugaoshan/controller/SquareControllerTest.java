package com.bugaoshan.controller;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.entity.SquareComment;
import com.bugaoshan.entity.SquareLike;
import com.bugaoshan.entity.SquarePost;
import com.bugaoshan.mapper.SquareCommentMapper;
import com.bugaoshan.mapper.SquareLikeMapper;
import com.bugaoshan.mapper.SquarePostMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SquareControllerTest {

    @Mock private SquarePostMapper postMapper;
    @Mock private SquareCommentMapper commentMapper;
    @Mock private SquareLikeMapper likeMapper;

    private SquareController controller;

    @BeforeEach
    void setUp() {
        controller = new SquareController(postMapper, commentMapper, likeMapper);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void commentListRejectsMissingPost() {
        when(postMapper.selectById(3L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> controller.comments(3L));

        verify(commentMapper, never()).selectByPostId(3L);
    }

    @Test
    void addCommentRejectsInactivePost() {
        SquarePost post = activePost();
        post.setStatus(0);
        when(postMapper.selectById(3L)).thenReturn(post);

        assertThrows(BusinessException.class,
                () -> controller.addComment(3L, comment("hello")));

        verify(commentMapper, never()).insert(any(SquareComment.class));
    }

    @Test
    void addCommentRejectsBlankContent() {
        when(postMapper.selectById(3L)).thenReturn(activePost());

        assertThrows(BusinessException.class,
                () -> controller.addComment(3L, comment("   ")));

        verify(commentMapper, never()).insert(any(SquareComment.class));
    }

    @Test
    void addCommentRejectsContentOverFiveHundredCharacters() {
        when(postMapper.selectById(3L)).thenReturn(activePost());

        assertThrows(BusinessException.class,
                () -> controller.addComment(3L, comment("x".repeat(501))));

        verify(commentMapper, never()).insert(any(SquareComment.class));
    }

    @Test
    void userCannotDeleteAnotherUsersComment() {
        SquareComment comment = comment("hello");
        comment.setCommentId(5L);
        comment.setUserId(9L);
        comment.setIsDeleted(0);
        when(commentMapper.selectById(5L)).thenReturn(comment);

        assertThrows(BusinessException.class, () -> controller.deleteComment(5L));

        verify(commentMapper, never()).deleteByIdAndUserId(5L, 7L);
    }

    @Test
    void likeRejectsMissingPost() {
        when(postMapper.selectById(3L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> controller.toggleLike(3L));

        verify(likeMapper, never()).insert(any(SquareLike.class));
    }

    @Test
    void existingLikeIsRemovedOnlyForCurrentUser() {
        when(postMapper.selectById(3L)).thenReturn(activePost());
        when(likeMapper.existsByPostIdAndUserId(3L, 7L)).thenReturn(true);

        controller.toggleLike(3L);

        verify(likeMapper).deleteByPostIdAndUserId(3L, 7L);
        verify(likeMapper, never()).insert(any(SquareLike.class));
    }

    private SquarePost activePost() {
        SquarePost post = new SquarePost();
        post.setPostId(3L);
        post.setStatus(1);
        post.setIsDeleted(0);
        return post;
    }

    private SquareComment comment(String content) {
        SquareComment comment = new SquareComment();
        comment.setContent(content);
        return comment;
    }
}
