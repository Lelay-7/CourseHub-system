package com.bugaoshan.controller;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.common.Result;
import com.bugaoshan.common.SecurityUtils;
import com.bugaoshan.entity.SquareComment;
import com.bugaoshan.entity.SquareLike;
import com.bugaoshan.entity.SquarePost;
import com.bugaoshan.mapper.SquareCommentMapper;
import com.bugaoshan.mapper.SquareLikeMapper;
import com.bugaoshan.mapper.SquarePostMapper;
import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/square")
public class SquareController {

    private final SquarePostMapper squarePostMapper;
    private final SquareCommentMapper commentMapper;
    private final SquareLikeMapper likeMapper;

    public SquareController(SquarePostMapper squarePostMapper,
                            SquareCommentMapper commentMapper,
                            SquareLikeMapper likeMapper) {
        this.squarePostMapper = squarePostMapper;
        this.commentMapper = commentMapper;
        this.likeMapper = likeMapper;
    }

    /** 获取帖子列表（带评论数和点赞数） */
    @GetMapping
    public Result<?> list(@RequestParam(defaultValue = "1") Integer page,
                           @RequestParam(defaultValue = "20") Integer pageSize) {
        int offset = (page - 1) * pageSize;
        List<SquarePost> posts = squarePostMapper.selectAllWithUser(1, offset, pageSize);
        long total = squarePostMapper.countAllWithUser(1);
        Long userId = SecurityUtils.getCurrentUserId();
        List<Map<String, Object>> result = new ArrayList<>();
        for (SquarePost p : posts) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("post_id", p.getPostId());
            item.put("user_id", p.getUserId());
            item.put("nickname", p.getNickname());
            item.put("content", p.getContent());
            item.put("course_name", p.getCourseName());
            item.put("material_name", p.getMaterialName());
            item.put("tags", p.getTags());
            item.put("create_time", p.getCreateTime());
            item.put("comments", commentMapper.countByPostId(p.getPostId()));
            item.put("likes", likeMapper.countByPostId(p.getPostId()));
            item.put("liked", likeMapper.existsByPostIdAndUserId(p.getPostId(), userId));
            result.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", result);
        data.put("total", total);
        return Result.ok(data);
    }

    /** 发布帖子 */
    @PostMapping
    public Result<?> create(@Valid @RequestBody SquarePost post) {
        Long userId = SecurityUtils.getCurrentUserId();
        post.setUserId(userId);
        post.setStatus(1);
        post.setIsDeleted(0);
        post.setCreateTime(LocalDateTime.now());
        post.setUpdateTime(LocalDateTime.now());
        squarePostMapper.insert(post);
        return Result.ok(post);
    }

    /** 删除自己的帖子 */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        SquarePost post = squarePostMapper.selectById(id);
        if (post == null || post.getIsDeleted() == 1) throw new BusinessException("帖子不存在");
        if (!post.getUserId().equals(SecurityUtils.getCurrentUserId())) throw new BusinessException("只能删除自己的帖子");
        squarePostMapper.deleteById(id);
        return Result.ok();
    }

    // ========== 评论 ==========

    /** 获取帖子评论 */
    @GetMapping("/{id}/comments")
    public Result<?> comments(@PathVariable Long id) {
        requireActivePost(id);
        return Result.ok(commentMapper.selectByPostId(id));
    }

    /** 发表评论 */
    @PostMapping("/{id}/comments")
    @Transactional
    public Result<?> addComment(@PathVariable Long id, @Valid @RequestBody SquareComment comment) {
        requireActivePost(id);
        String content = normalizeCommentContent(comment.getContent());
        Long userId = SecurityUtils.getCurrentUserId();
        comment.setPostId(id);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setIsDeleted(0);
        comment.setCreateTime(LocalDateTime.now());
        commentMapper.insert(comment);
        return Result.ok(comment);
    }

    /** 删除自己的评论 */
    @DeleteMapping("/comments/{commentId}")
    @Transactional
    public Result<?> deleteComment(@PathVariable Long commentId) {
        Long userId = SecurityUtils.getCurrentUserId();
        SquareComment comment = commentMapper.selectById(commentId);
        if (comment == null || Integer.valueOf(1).equals(comment.getIsDeleted())) {
            throw new BusinessException("评论不存在");
        }
        if (!userId.equals(comment.getUserId())) {
            throw new BusinessException("只能删除自己的评论");
        }
        if (commentMapper.deleteByIdAndUserId(commentId, userId) != 1) {
            throw new BusinessException("评论状态已变化，请刷新后重试");
        }
        return Result.ok();
    }

    // ========== 点赞 ==========

    /** 切换点赞 */
    @PostMapping("/{id}/like")
    @Transactional
    public Result<?> toggleLike(@PathVariable Long id) {
        requireActivePost(id);
        Long userId = SecurityUtils.getCurrentUserId();
        if (likeMapper.existsByPostIdAndUserId(id, userId)) {
            likeMapper.deleteByPostIdAndUserId(id, userId);
            return Result.ok(Map.of("liked", false, "likes", likeMapper.countByPostId(id)));
        } else {
            SquareLike like = new SquareLike();
            like.setPostId(id);
            like.setUserId(userId);
            like.setCreateTime(LocalDateTime.now());
            try {
                likeMapper.insert(like);
            } catch (DuplicateKeyException ignored) {
                // The unique key keeps concurrent like requests idempotent.
            }
            return Result.ok(Map.of("liked", true, "likes", likeMapper.countByPostId(id)));
        }
    }

    private SquarePost requireActivePost(Long postId) {
        SquarePost post = squarePostMapper.selectById(postId);
        if (post == null || !Integer.valueOf(1).equals(post.getStatus())
                || Integer.valueOf(1).equals(post.getIsDeleted())) {
            throw new BusinessException("帖子不存在或已下架");
        }
        return post;
    }

    private String normalizeCommentContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("评论内容不能为空");
        }
        String normalized = content.trim();
        if (normalized.length() > 500) {
            throw new BusinessException("评论内容不能超过 500 字");
        }
        return normalized;
    }
}
