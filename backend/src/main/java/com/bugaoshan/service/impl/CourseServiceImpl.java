package com.bugaoshan.service.impl;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.common.PageResult;
import com.bugaoshan.common.Result;
import com.bugaoshan.dto.CourseReviewDTO;
import com.bugaoshan.dto.CourseReviewWithUser;
import com.bugaoshan.entity.Course;
import com.bugaoshan.entity.CourseReview;
import com.bugaoshan.entity.CourseSelection;
import com.bugaoshan.entity.Favorite;
import com.bugaoshan.entity.ScoreRecord;
import com.bugaoshan.entity.User;
import com.bugaoshan.mapper.CourseMapper;
import com.bugaoshan.mapper.CourseReviewMapper;
import com.bugaoshan.mapper.CourseSelectionMapper;
import com.bugaoshan.mapper.FavoriteMapper;
import com.bugaoshan.mapper.ScoreRecordMapper;
import com.bugaoshan.mapper.UserMapper;
import com.bugaoshan.service.CourseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CourseServiceImpl implements CourseService {

    private final CourseMapper courseMapper;
    private final CourseReviewMapper courseReviewMapper;
    private final CourseSelectionMapper courseSelectionMapper;
    private final FavoriteMapper favoriteMapper;
    private final UserMapper userMapper;
    private final ScoreRecordMapper scoreRecordMapper;

    public CourseServiceImpl(CourseMapper courseMapper, CourseReviewMapper courseReviewMapper,
                             CourseSelectionMapper courseSelectionMapper,
                             FavoriteMapper favoriteMapper, UserMapper userMapper,
                             ScoreRecordMapper scoreRecordMapper) {
        this.courseMapper = courseMapper;
        this.courseReviewMapper = courseReviewMapper;
        this.courseSelectionMapper = courseSelectionMapper;
        this.favoriteMapper = favoriteMapper;
        this.userMapper = userMapper;
        this.scoreRecordMapper = scoreRecordMapper;
    }

    @Override
    public Result getCategories() {
        List<Map<String, Object>> categories = courseMapper.selectCategories();
        return Result.ok(categories);
    }

    @Override
    public Result getCourseList(Long categoryId, Integer courseType, BigDecimal creditMin, BigDecimal creditMax,
                                 String teacherName, String keyword, String timeSlot, String sortBy, String sortOrder,
                                 Integer page, Integer pageSize) {
        if (page == null) page = 1;
        if (pageSize == null) pageSize = 10;
        int offset = (page - 1) * pageSize;
        String normalizedSortBy = sortBy != null
                && Set.of("selection_count", "avg_rating", "credit", "create_time").contains(sortBy)
                ? sortBy : "create_time";
        String normalizedTimeSlot = timeSlot != null
                && Set.of("early", "middle", "late", "morning", "afternoon",
                "evening", "weekday", "weekend").contains(timeSlot)
                ? timeSlot : null;
        String order = "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";

        List<Course> list = courseMapper.selectList(categoryId, courseType, creditMin, creditMax,
                teacherName, keyword, normalizedTimeSlot, normalizedSortBy, order, offset, pageSize);
        long total = courseMapper.countList(categoryId, courseType, creditMin, creditMax,
                teacherName, keyword, normalizedTimeSlot);

        PageResult<Course> pageResult = new PageResult<>(list, total);
        return Result.ok(pageResult);
    }

    @Override
    public Result getCourseDetail(Long courseId, Long userId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("course", course);

        List<CourseReviewWithUser> reviews = courseReviewMapper.selectWithUserByCourseId(courseId, 0, 100);
        data.put("reviews", reviews);

        List<Course> related = courseMapper.selectRelatedCourses(courseId, 5);
        data.put("relatedCourses", related);

        if (userId != null) {
            Favorite fav = favoriteMapper.selectByUserIdAndTarget(userId, 1, courseId);
            data.put("is_favorited", fav != null);
            CourseSelection selection = courseSelectionMapper.selectByUserIdAndCourseId(userId, courseId);
            data.put("is_selected", selection != null && selection.getStatus() == 1);
        } else {
            data.put("is_favorited", false);
            data.put("is_selected", false);
        }

        return Result.ok(data);
    }

    @Override
    public Result searchCourse(String keyword, Integer page, Integer pageSize) {
        return getCourseList(null, null, null, null, null, keyword,
                null, null, null, page, pageSize);
    }

    @Override
    @Transactional
    public Result submitReview(Long courseId, Long userId, CourseReviewDTO dto) {
        // Establish the REPEATABLE READ snapshot only after earlier rating mutations commit.
        courseMapper.lockForRatingUpdate(courseId);
        Course course = courseMapper.selectById(courseId);
        if (course == null || !Integer.valueOf(1).equals(course.getStatus())
                || Integer.valueOf(1).equals(course.getIsDeleted())) {
            throw new BusinessException("课程不存在或已下架");
        }
        CourseSelection selection = courseSelectionMapper.selectByUserIdAndCourseId(userId, courseId);
        if (selection == null || !Integer.valueOf(1).equals(selection.getStatus())) {
            throw new BusinessException("选课后才能评价该课程");
        }
        if (dto == null || dto.getRating() == null || dto.getRating() < 1 || dto.getRating() > 5) {
            throw new BusinessException("评分必须在 1 到 5 之间");
        }
        if (dto.getContent() == null || dto.getContent().isBlank()) {
            throw new BusinessException("评价内容不能为空");
        }
        String content = dto.getContent().trim();
        if (content.length() > 500) {
            throw new BusinessException("评价内容不能超过 500 字");
        }
        if (courseReviewMapper.existsByUserIdAndCourseId(userId, courseId)) {
            throw new BusinessException("您已评价过该课程");
        }

        // 检查是否有已删除的旧评价，有则激活更新而非新增（避免唯一约束冲突）
        Long reviewId;
        CourseReview existing = courseReviewMapper.selectByUserIdAndCourseId(userId, courseId);
        if (existing != null && existing.getIsDeleted() == 1) {
            courseReviewMapper.reactivateReview(existing.getReviewId(),
                    dto.getRating(), content,
                    dto.getIsAnonymous() != null ? dto.getIsAnonymous() : 0,
                    LocalDateTime.now());
            reviewId = existing.getReviewId();
        } else {
            CourseReview review = new CourseReview();
            review.setCourseId(courseId);
            review.setUserId(userId);
            review.setRating(dto.getRating());
            review.setContent(content);
            review.setIsAnonymous(dto.getIsAnonymous() != null ? dto.getIsAnonymous() : 0);
            review.setRewardGranted(0);
            review.setStatus(1);
            review.setIsDeleted(0);
            review.setCreateTime(LocalDateTime.now());
            review.setUpdateTime(LocalDateTime.now());
            courseReviewMapper.insert(review);
            reviewId = review.getReviewId();
        }

        recalcRating(courseId);

        Map<String, Object> data = new HashMap<>();
        data.put("reviewId", reviewId);
        if (courseReviewMapper.claimReviewReward(reviewId) == 1) {
            if (userMapper.earnScore(userId, 3) != 1) {
                throw new BusinessException("评价奖励发放失败");
            }
            User user = userMapper.selectById(userId);
            int newBalance = user.getScoreBalance();
            ScoreRecord record = new ScoreRecord();
            record.setUserId(userId);
            record.setChangeType(1);
            record.setChangeAmount(3);
            record.setBalanceAfter(newBalance);
            record.setSourceDesc("课程评价奖励");
            record.setRelatedId(courseId);
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            scoreRecordMapper.insert(record);
            data.put("score_balance", newBalance);
            data.put("reward_granted", true);
        } else {
            data.put("reward_granted", false);
        }
        return Result.ok(data);
    }

    private void recalcRating(Long courseId) {
        Map<String, Object> stats = courseReviewMapper.calcAvgAndCount(courseId);
        BigDecimal avgRating = new BigDecimal(stats.get("avg_rating").toString());
        int ratingCount = Integer.parseInt(stats.get("rating_count").toString());
        courseMapper.updateRating(courseId, avgRating, ratingCount);
    }

    @Override
    @Transactional
    public Result deleteReview(Long courseId, Long userId) {
        courseMapper.lockForRatingUpdate(courseId);
        if (!courseReviewMapper.existsByUserIdAndCourseId(userId, courseId)) {
            throw new BusinessException("该课程暂无您的评价");
        }
        courseReviewMapper.deleteByUserIdAndCourseId(userId, courseId);
        recalcRating(courseId);
        return Result.ok();
    }

    @Override
    @Transactional
    public Result deleteReviewForAdmin(Long courseId, Long reviewId) {
        courseMapper.lockForRatingUpdate(courseId);
        CourseReview review = courseReviewMapper.selectById(reviewId);
        if (review == null || !courseId.equals(review.getCourseId())
                || Integer.valueOf(1).equals(review.getIsDeleted())) {
            throw new BusinessException("评价不存在");
        }
        if (courseReviewMapper.deleteById(reviewId) != 1) {
            throw new BusinessException("评价状态已变化，请刷新后重试");
        }
        recalcRating(courseId);
        return Result.ok();
    }

    @Override
    @Transactional
    public Result selectCourse(Long courseId, Long userId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null || course.getStatus() != 1) {
            throw new BusinessException("课程不存在或不可选");
        }
        CourseSelection existing = courseSelectionMapper.selectByUserIdAndCourseId(userId, courseId);
        if (existing != null && existing.getStatus() == 1) {
            throw new BusinessException("您已选择该课程");
        }
        int changed;
        if (existing == null) {
            CourseSelection selection = new CourseSelection();
            selection.setCourseId(courseId);
            selection.setUserId(userId);
            selection.setStatus(1);
            selection.setSelectTime(LocalDateTime.now());
            selection.setCreateTime(LocalDateTime.now());
            selection.setUpdateTime(LocalDateTime.now());
            selection.setIsDeleted(0);
            changed = courseSelectionMapper.insertSelection(selection);
        } else {
            changed = courseSelectionMapper.reactivateSelection(userId, courseId);
        }
        if (changed != 1) {
            throw new BusinessException("选课状态已变化，请刷新后重试");
        }
        courseMapper.incrementSelectionCount(courseId);
        return Result.ok();
    }

    @Override
    @Transactional
    public Result dropCourse(Long courseId, Long userId) {
        CourseSelection selection = courseSelectionMapper.selectByUserIdAndCourseId(userId, courseId);
        if (selection == null || selection.getStatus() != 1) {
            throw new BusinessException("您尚未选择该课程");
        }
        if (courseSelectionMapper.dropSelected(userId, courseId) != 1) {
            throw new BusinessException("选课状态已变化，请刷新后重试");
        }
        courseMapper.decrementSelectionCountSafely(courseId);
        return Result.ok();
    }

    @Override
    public Result getSelectedCourses(Long userId) {
        return Result.ok(courseSelectionMapper.selectSelectedCourses(userId));
    }

    @Override
    public Result getMyReviews(Long userId) {
        return Result.ok(courseReviewMapper.selectMyReviews(userId));
    }
}
