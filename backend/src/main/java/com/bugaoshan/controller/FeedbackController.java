package com.bugaoshan.controller;

import com.bugaoshan.common.Result;
import com.bugaoshan.common.SecurityUtils;
import com.bugaoshan.entity.Feedback;
import com.bugaoshan.mapper.FeedbackMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Set;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "功能建议", "资料问题", "课程信息错误", "其他");

    private final FeedbackMapper feedbackMapper;

    public FeedbackController(FeedbackMapper feedbackMapper) {
        this.feedbackMapper = feedbackMapper;
    }

    /** 学生/教师提交反馈 */
    @PostMapping
    public Result<?> submit(@Valid @RequestBody Feedback feedback) {
        if (feedback == null || !ALLOWED_TYPES.contains(feedback.getType())) {
            throw new com.bugaoshan.common.BusinessException("反馈类型不正确");
        }
        if (feedback.getContent() == null || feedback.getContent().isBlank()) {
            throw new com.bugaoshan.common.BusinessException("反馈内容不能为空");
        }
        String content = feedback.getContent().trim();
        if (content.length() > 1000) {
            throw new com.bugaoshan.common.BusinessException("反馈内容不能超过 1000 字");
        }
        feedback.setContent(content);
        if (feedback.getContact() != null) {
            String contact = feedback.getContact().trim();
            if (contact.length() > 100) {
                throw new com.bugaoshan.common.BusinessException("联系方式不能超过 100 字");
            }
            feedback.setContact(contact.isEmpty() ? null : contact);
        }
        Long userId = SecurityUtils.getCurrentUserId();
        feedback.setUserId(userId);
        feedback.setStatus(0);
        feedback.setCreateTime(LocalDateTime.now());
        feedback.setUpdateTime(LocalDateTime.now());
        feedbackMapper.insert(feedback);
        return Result.ok();
    }

    /** 查看自己的反馈及回复 */
    @GetMapping("/my")
    public Result<?> myFeedback() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.ok(feedbackMapper.selectByUserId(userId));
    }
}
