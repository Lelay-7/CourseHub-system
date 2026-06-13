package com.bugaoshan.service.impl;

import com.bugaoshan.dto.CourseReviewDTO;
import com.bugaoshan.entity.CourseReview;
import com.bugaoshan.entity.Course;
import com.bugaoshan.entity.CourseSelection;
import com.bugaoshan.mapper.CourseMapper;
import com.bugaoshan.mapper.CourseReviewMapper;
import com.bugaoshan.mapper.CourseSelectionMapper;
import com.bugaoshan.mapper.FavoriteMapper;
import com.bugaoshan.mapper.ScoreRecordMapper;
import com.bugaoshan.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceImplTest {

    @Mock private CourseMapper courseMapper;
    @Mock private CourseReviewMapper reviewMapper;
    @Mock private CourseSelectionMapper selectionMapper;
    @Mock private FavoriteMapper favoriteMapper;
    @Mock private UserMapper userMapper;
    @Mock private ScoreRecordMapper scoreRecordMapper;

    @Test
    void reactivatedReviewDoesNotReceiveRewardTwice() {
        Course course = new Course();
        course.setStatus(1);
        course.setIsDeleted(0);
        CourseSelection selection = new CourseSelection();
        selection.setStatus(1);
        CourseReview existing = new CourseReview();
        existing.setReviewId(4L);
        existing.setCourseId(2L);
        existing.setUserId(8L);
        existing.setIsDeleted(1);
        when(courseMapper.selectById(2L)).thenReturn(course);
        when(selectionMapper.selectByUserIdAndCourseId(8L, 2L)).thenReturn(selection);
        when(reviewMapper.existsByUserIdAndCourseId(8L, 2L)).thenReturn(false);
        when(reviewMapper.selectByUserIdAndCourseId(8L, 2L)).thenReturn(existing);
        when(reviewMapper.claimReviewReward(4L)).thenReturn(0);
        when(reviewMapper.calcAvgAndCount(2L)).thenReturn(java.util.Map.of(
                "avg_rating", java.math.BigDecimal.valueOf(5),
                "rating_count", 1));
        CourseServiceImpl service = new CourseServiceImpl(
                courseMapper, reviewMapper, selectionMapper, favoriteMapper, userMapper, scoreRecordMapper);
        CourseReviewDTO dto = new CourseReviewDTO();
        dto.setRating(5);
        dto.setContent("good");

        service.submitReview(2L, 8L, dto);

        verify(userMapper, never()).earnScore(anyLong(), anyInt());
    }

    @Test
    void reviewRejectsInactiveCourse() {
        Course course = new Course();
        course.setStatus(0);
        course.setIsDeleted(0);
        when(courseMapper.selectById(2L)).thenReturn(course);

        assertThrows(com.bugaoshan.common.BusinessException.class,
                () -> service().submitReview(2L, 8L, review(5, "good")));

        verify(reviewMapper, never()).insert(org.mockito.ArgumentMatchers.any(CourseReview.class));
    }

    @Test
    void reviewRequiresActiveSelection() {
        Course course = new Course();
        course.setStatus(1);
        course.setIsDeleted(0);
        when(courseMapper.selectById(2L)).thenReturn(course);
        when(selectionMapper.selectByUserIdAndCourseId(8L, 2L)).thenReturn(null);

        assertThrows(com.bugaoshan.common.BusinessException.class,
                () -> service().submitReview(2L, 8L, review(5, "good")));

        verify(reviewMapper, never()).insert(org.mockito.ArgumentMatchers.any(CourseReview.class));
    }

    @Test
    void reviewRejectsRatingOutsideOneToFive() {
        stubActiveCourseAndSelection();

        assertThrows(com.bugaoshan.common.BusinessException.class,
                () -> service().submitReview(2L, 8L, review(6, "good")));

        verify(reviewMapper, never()).insert(org.mockito.ArgumentMatchers.any(CourseReview.class));
    }

    @Test
    void reviewRejectsBlankContent() {
        stubActiveCourseAndSelection();

        assertThrows(com.bugaoshan.common.BusinessException.class,
                () -> service().submitReview(2L, 8L, review(5, "   ")));

        verify(reviewMapper, never()).insert(org.mockito.ArgumentMatchers.any(CourseReview.class));
    }

    @Test
    void reviewRejectsContentOverFiveHundredCharacters() {
        stubActiveCourseAndSelection();

        assertThrows(com.bugaoshan.common.BusinessException.class,
                () -> service().submitReview(2L, 8L, review(5, "x".repeat(501))));

        verify(reviewMapper, never()).insert(org.mockito.ArgumentMatchers.any(CourseReview.class));
    }

    @Test
    void duplicateReviewIsRejectedAfterTargetAndSelectionValidation() {
        stubActiveCourseAndSelection();
        when(reviewMapper.existsByUserIdAndCourseId(8L, 2L)).thenReturn(true);

        assertThrows(com.bugaoshan.common.BusinessException.class,
                () -> service().submitReview(2L, 8L, review(5, "good")));

        verify(reviewMapper, never()).insert(org.mockito.ArgumentMatchers.any(CourseReview.class));
    }

    @Test
    void duplicateSelectionDoesNotIncreaseCourseCount() {
        Course course = new Course();
        course.setCourseId(2L);
        course.setStatus(1);
        CourseSelection selection = new CourseSelection();
        selection.setStatus(1);
        when(courseMapper.selectById(2L)).thenReturn(course);
        when(selectionMapper.selectByUserIdAndCourseId(8L, 2L)).thenReturn(selection);
        CourseServiceImpl service = new CourseServiceImpl(
                courseMapper, reviewMapper, selectionMapper, favoriteMapper, userMapper, scoreRecordMapper);

        assertThrows(com.bugaoshan.common.BusinessException.class, () -> service.selectCourse(2L, 8L));

        verify(courseMapper, never()).incrementSelectionCount(anyLong());
    }

    @Test
    void droppingSelectedCourseUsesSafeDecrement() {
        CourseSelection selection = new CourseSelection();
        selection.setStatus(1);
        when(selectionMapper.selectByUserIdAndCourseId(8L, 2L)).thenReturn(selection);
        when(selectionMapper.dropSelected(8L, 2L)).thenReturn(1);
        CourseServiceImpl service = new CourseServiceImpl(
                courseMapper, reviewMapper, selectionMapper, favoriteMapper, userMapper, scoreRecordMapper);

        service.dropCourse(2L, 8L);

        verify(courseMapper).decrementSelectionCountSafely(eq(2L));
    }

    @Test
    void invalidSortFieldFallsBackToCreateTimeDescending() {
        CourseServiceImpl service = new CourseServiceImpl(
                courseMapper, reviewMapper, selectionMapper, favoriteMapper, userMapper, scoreRecordMapper);

        service.getCourseList(null, null, null, null, null, null,
                "unknown", "student_count", "sideways", 1, 10);

        verify(courseMapper).selectList(null, null, null, null, null, null,
                null, "create_time", "desc", 0, 10);
        verify(courseMapper).countList(null, null, null, null, null, null, null);
    }

    @Test
    void validTimeSlotIsAppliedToListAndCount() {
        service().getCourseList(null, null, null, null, null, "data",
                "early", "avg_rating", "asc", 2, 5);

        verify(courseMapper).selectList(null, null, null, null, null, "data",
                "early", "avg_rating", "asc", 5, 5);
        verify(courseMapper).countList(null, null, null, null, null, "data", "early");
    }

    @Test
    void myReviewsComeFromCourseReviews() {
        CourseServiceImpl service = new CourseServiceImpl(
                courseMapper, reviewMapper, selectionMapper, favoriteMapper, userMapper, scoreRecordMapper);

        service.getMyReviews(8L);

        verify(reviewMapper).selectMyReviews(8L);
        verify(scoreRecordMapper, never()).selectByUserId(anyLong(), anyInt(), anyInt(), org.mockito.ArgumentMatchers.any());
    }

    private CourseServiceImpl service() {
        return new CourseServiceImpl(
                courseMapper, reviewMapper, selectionMapper, favoriteMapper, userMapper, scoreRecordMapper);
    }

    private CourseReviewDTO review(int rating, String content) {
        CourseReviewDTO dto = new CourseReviewDTO();
        dto.setRating(rating);
        dto.setContent(content);
        return dto;
    }

    private void stubActiveCourseAndSelection() {
        Course course = new Course();
        course.setStatus(1);
        course.setIsDeleted(0);
        CourseSelection selection = new CourseSelection();
        selection.setStatus(1);
        when(courseMapper.selectById(2L)).thenReturn(course);
        when(selectionMapper.selectByUserIdAndCourseId(8L, 2L)).thenReturn(selection);
    }
}
