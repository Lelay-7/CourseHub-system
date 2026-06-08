package com.bugaoshan.service.impl;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.common.PageResult;
import com.bugaoshan.common.Result;
import com.bugaoshan.dto.CourseReviewDTO;
import com.bugaoshan.entity.Course;
import com.bugaoshan.entity.CourseReview;
import com.bugaoshan.entity.Favorite;
import com.bugaoshan.entity.ScoreRecord;
import com.bugaoshan.entity.User;
import com.bugaoshan.mapper.CourseMapper;
import com.bugaoshan.mapper.CourseReviewMapper;
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

@Service
public class CourseServiceImpl implements CourseService {

    private final CourseMapper courseMapper;
    private final CourseReviewMapper courseReviewMapper;
    private final FavoriteMapper favoriteMapper;
    private final UserMapper userMapper;
    private final ScoreRecordMapper scoreRecordMapper;

    public CourseServiceImpl(CourseMapper courseMapper, CourseReviewMapper courseReviewMapper,
                             FavoriteMapper favoriteMapper, UserMapper userMapper,
                             ScoreRecordMapper scoreRecordMapper) {
        this.courseMapper = courseMapper;
        this.courseReviewMapper = courseReviewMapper;
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
                                 String teacherName, String keyword, String sortBy, String sortOrder,
                                 Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        String order = (sortOrder == null || sortOrder.isEmpty()) ? "DESC" : sortOrder.toUpperCase();

        List<Course> list = courseMapper.selectList(categoryId, courseType, creditMin, creditMax,
                teacherName, keyword, sortBy, order, offset, pageSize);
        long total = courseMapper.countList(categoryId, courseType, creditMin, creditMax, teacherName, keyword);

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

        List<CourseReview> reviews = courseReviewMapper.selectByCourseId(courseId, 0, 100);
        data.put("reviews", reviews);

        List<Course> related = courseMapper.selectRelatedCourses(courseId, 5);
        data.put("relatedCourses", related);

        if (userId != null) {
            Favorite fav = favoriteMapper.selectByUserIdAndTarget(userId, 1, courseId);
            data.put("is_favorited", fav != null);
        } else {
            data.put("is_favorited", false);
        }

        return Result.ok(data);
    }

    @Override
    public Result searchCourse(String keyword, Integer page, Integer pageSize) {
        return getCourseList(null, null, null, null, null, keyword, null, null, page, pageSize);
    }

    @Override
    @Transactional
    public Result submitReview(Long courseId, Long userId, CourseReviewDTO dto) {
        if (courseReviewMapper.existsByUserIdAndCourseId(userId, courseId)) {
            throw new BusinessException("您已评价过该课程");
        }

        CourseReview review = new CourseReview();
        review.setCourseId(courseId);
        review.setUserId(userId);
        review.setRating(dto.getRating());
        review.setContent(dto.getContent());
        review.setStatus(1);
        review.setIsDeleted(0);
        review.setCreateTime(LocalDateTime.now());
        review.setUpdateTime(LocalDateTime.now());
        courseReviewMapper.insert(review);

        int ratingCount = courseReviewMapper.countByCourseId(courseId);
        List<CourseReview> allReviews = courseReviewMapper.selectByCourseId(courseId, 0, Integer.MAX_VALUE);
        BigDecimal avgRating = BigDecimal.ZERO;
        if (!allReviews.isEmpty()) {
            int sum = 0;
            for (CourseReview r : allReviews) {
                sum += r.getRating();
            }
            avgRating = BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(allReviews.size()), 1, java.math.RoundingMode.HALF_UP);
        }
        courseMapper.updateRating(courseId, avgRating, ratingCount);

        User user = userMapper.selectById(userId);
        int newBalance = user.getScoreBalance() + 3;
        int newTotalScore = user.getTotalScore() + 3;
        userMapper.updateScoreBalance(userId, newBalance, newTotalScore);

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

        Map<String, Object> data = new HashMap<>();
        data.put("reviewId", review.getReviewId());
        data.put("score_balance", newBalance);
        return Result.ok(data);
    }
}
