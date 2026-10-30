package com.bugaoshan.controller;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.entity.Material;
import com.bugaoshan.entity.Course;
import com.bugaoshan.entity.CourseReview;
import com.bugaoshan.entity.Goods;
import com.bugaoshan.entity.Order;
import com.bugaoshan.entity.SquareComment;
import com.bugaoshan.entity.SquarePost;
import com.bugaoshan.entity.User;
import com.bugaoshan.mapper.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock UserMapper userMapper;
    @Mock CourseMapper courseMapper;
    @Mock CourseReviewMapper courseReviewMapper;
    @Mock MaterialMapper materialMapper;
    @Mock BannerMapper bannerMapper;
    @Mock GoodsMapper goodsMapper;
    @Mock FeedbackMapper feedbackMapper;
    @Mock SquarePostMapper squarePostMapper;
    @Mock SquareCommentMapper squareCommentMapper;
    @Mock ScoreRecordMapper scoreRecordMapper;
    @Mock OrderMapper orderMapper;
    @Mock MaterialReportMapper materialReportMapper;
    @Mock com.bugaoshan.service.MaterialReviewService materialReviewService;

    private AdminController controller() {
        return new AdminController(userMapper, courseMapper, courseReviewMapper,
                materialMapper, bannerMapper, goodsMapper, feedbackMapper, squarePostMapper,
                squareCommentMapper,
                scoreRecordMapper, orderMapper, materialReportMapper,
                new com.bugaoshan.service.impl.CourseServiceImpl(courseMapper, courseReviewMapper,
                        org.mockito.Mockito.mock(CourseSelectionMapper.class),
                        org.mockito.Mockito.mock(FavoriteMapper.class), userMapper, scoreRecordMapper), materialReviewService);
    }

    @Test
    void deletingLastReviewClearsCourseRatingAndCount() {
        CourseReview review = new CourseReview();
        review.setReviewId(8L);
        review.setCourseId(7L);
        review.setUserId(9L);
        review.setIsDeleted(0);
        org.mockito.Mockito.lenient().when(courseReviewMapper.selectById(8L)).thenReturn(review);
        org.mockito.Mockito.lenient().when(courseReviewMapper.calcAvgAndCount(7L))
                .thenReturn(java.util.Map.of("avg_rating", java.math.BigDecimal.ZERO, "rating_count", 0));
        org.mockito.Mockito.lenient().when(courseReviewMapper.deleteById(8L)).thenReturn(1);

        controller().deleteReview(8L);

        verify(courseMapper).updateRating(7L, java.math.BigDecimal.ZERO, 0);
    }

    @Test
    void deletingMissingReviewIsRejected() {
        assertThrows(BusinessException.class, () -> controller().deleteReview(999L));

        verify(courseReviewMapper, never()).deleteById(999L);
    }

    @Test
    void deletingReviewLostRaceDoesNotChangeRating() {
        CourseReview review = new CourseReview();
        review.setReviewId(8L);
        review.setCourseId(7L);
        review.setIsDeleted(0);
        org.mockito.Mockito.lenient().when(courseReviewMapper.selectById(8L)).thenReturn(review);

        assertThrows(BusinessException.class, () -> controller().deleteReview(8L));

        verify(courseMapper, never()).updateRating(anyLong(), org.mockito.ArgumentMatchers.any(), anyInt());
    }

    @Test
    void reviewListWithoutCourseFilterReturnsAllReviews() {
        CourseReview review = new CourseReview();
        review.setReviewId(8L);
        when(courseReviewMapper.selectAllForAdmin(0, 20)).thenReturn(List.of(review));
        AdminController controller = controller();

        var result = controller.listReviews(null, 1, 20);

        org.junit.jupiter.api.Assertions.assertInstanceOf(Map.class, result.getData());
        assertEquals(List.of(review), ((Map<?, ?>) result.getData()).get("list"));
        org.junit.jupiter.api.Assertions.assertTrue(((Map<?, ?>) result.getData()).containsKey("total"));
        verify(courseReviewMapper, never()).selectByCourseId(anyLong(), anyInt(), anyInt());
    }

    @Test
    void createCourseRejectsBlankRequiredFields() {
        Course course = new Course();
        course.setCourseName(" ");
        course.setCourseCode("CS999");

        assertThrows(BusinessException.class, () -> controller().createCourse(course));
        verify(courseMapper, never()).insert(course);
    }

    @Test
    void createCourseRejectsDuplicateCourseCode() {
        Course course = new Course();
        course.setCourseName("Test course");
        course.setCourseCode(" CS001 ");
        course.setCourseType(2);
        course.setCredit(new java.math.BigDecimal("2.0"));
        when(courseMapper.selectByCourseCode("CS001")).thenReturn(new Course());

        assertThrows(BusinessException.class, () -> controller().createCourse(course));
        verify(courseMapper, never()).insert(course);
    }

    @Test
    void deactivatingPublishedMaterialUsesSeparateStatus() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, List.of()));
        try {
            controller().deactivateMaterial(3L, Map.of("reason", "版权核实后下架"));
            verify(materialReviewService).deactivate(3L, 1L, "版权核实后下架");
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void userManagementUsesRealFilteredPagination() {
        User user = new User();
        user.setUserId(8L);
        user.setUsername("student8");
        user.setPasswordHash("secret");
        when(userMapper.selectAdminPage("student", 1, 1, 20, 20)).thenReturn(List.of(user));
        when(userMapper.countAdminPage("student", 1, 1)).thenReturn(21L);

        var result = controller().listUsers(1, 1, "student", 2, null, 20);
        Map<?, ?> data = (Map<?, ?>) result.getData();
        User returned = (User) ((List<?>) data.get("list")).get(0);

        assertEquals(21L, data.get("total"));
        assertNull(returned.getPasswordHash());
        verify(userMapper).selectAdminPage("student", 1, 1, 20, 20);
        verify(userMapper, never()).selectTopScoreRank(anyInt());
    }

    @Test
    void completeOrderRejectsCancelledOrder() {
        Order order = new Order();
        order.setOrderId(4L);
        order.setStatus(4);
        when(orderMapper.selectById(4L)).thenReturn(order);

        assertThrows(BusinessException.class, () -> controller().completeOrder(4L, Map.of("fulfillment_note", "请到服务台领取")));

        verify(orderMapper, never()).completeWithNote(anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void completeOrderTransitionsPendingOrder() {
        Order order = new Order();
        order.setOrderId(4L);
        order.setStatus(1);
        when(orderMapper.selectById(4L)).thenReturn(order);
        when(orderMapper.completeWithNote(4L, "请到服务台领取")).thenReturn(1);

        controller().completeOrder(4L, Map.of("fulfillment_note", "请到服务台领取"));

        verify(orderMapper).completeWithNote(4L, "请到服务台领取");
    }

    @Test
    void adminCourseListIncludesInactiveCourses() {
        when(courseMapper.selectAllForAdmin("course", 0, 10)).thenReturn(List.of(new Course()));
        when(courseMapper.countAllForAdmin("course")).thenReturn(1L);

        controller().listCourses("course", 1, 10);

        verify(courseMapper).selectAllForAdmin("course", 0, 10);
        verify(courseMapper, never()).selectList(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateCourseRejectsCodeUsedByAnotherCourse() {
        Course current = validCourse();
        current.setCourseId(2L);
        Course duplicate = validCourse();
        duplicate.setCourseId(3L);
        when(courseMapper.selectById(2L)).thenReturn(current);
        when(courseMapper.selectByCourseCode("CS200")).thenReturn(duplicate);

        assertThrows(BusinessException.class, () -> controller().updateCourse(2L, current));

        verify(courseMapper, never()).updateById(current);
    }

    @Test
    void createCourseRejectsNonTeacherUser() {
        Course course = validCourse();
        User student = new User();
        student.setUserId(5L);
        student.setUserType(1);
        course.setTeacherId(5L);
        when(userMapper.selectById(5L)).thenReturn(student);

        assertThrows(BusinessException.class, () -> controller().createCourse(course));

        verify(courseMapper, never()).insert(course);
    }

    @Test
    void createGoodsRejectsNegativeExchangeLimit() {
        Goods goods = validGoods();
        goods.setExchangeLimit(-1);

        assertThrows(BusinessException.class, () -> controller().createGoods(goods));

        verify(goodsMapper, never()).insert(goods);
    }

    @Test
    void adminGoodsListIncludesInactiveGoods() {
        when(goodsMapper.selectAllForAdmin(null, null, 0, 20)).thenReturn(List.of(new Goods()));
        when(goodsMapper.countAllForAdmin(null, null)).thenReturn(1L);

        controller().listGoods(null, null, 1, 20);

        verify(goodsMapper).selectAllForAdmin(null, null, 0, 20);
    }

    @Test
    void administratorCannotDisableOwnAccount() {
        User admin = new User();
        admin.setUserId(1L);
        admin.setStatus(1);
        when(userMapper.selectById(1L)).thenReturn(admin);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(1L, null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            assertThrows(BusinessException.class,
                    () -> controller().toggleUserStatus(1L, Map.of("status", 0)));
            verify(userMapper, never()).updateStatus(anyLong(), anyInt());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void createCourseRejectsNegativeSelectionCount() {
        Course course = validCourse();
        course.setSelectionCount(-1);

        assertThrows(BusinessException.class, () -> controller().createCourse(course));

        verify(courseMapper, never()).insert(course);
    }

    @Test
    void createCourseRejectsMissingCategory() {
        Course course = validCourse();
        course.setCategoryId(99L);
        when(courseMapper.categoryExists(99L)).thenReturn(false);

        assertThrows(BusinessException.class, () -> controller().createCourse(course));

        verify(courseMapper, never()).insert(course);
    }

    @Test
    void createGoodsRejectsNegativeStock() {
        Goods goods = validGoods();
        goods.setStock(-1);

        assertThrows(BusinessException.class, () -> controller().createGoods(goods));

        verify(goodsMapper, never()).insert(goods);
    }

    @Test
    void administratorCanSoftDeleteAnyExistingComment() {
        SquareComment comment = new SquareComment();
        comment.setCommentId(5L);
        comment.setUserId(9L);
        comment.setIsDeleted(0);
        when(squareCommentMapper.selectById(5L)).thenReturn(comment);
        when(squareCommentMapper.softDeleteById(5L)).thenReturn(1);

        controller().deleteSquareComment(5L);

        verify(squareCommentMapper).softDeleteById(5L);
    }

    @Test
    void administratorCanListCommentsForInactiveButExistingPost() {
        SquarePost post = new SquarePost();
        post.setPostId(3L);
        post.setStatus(0);
        post.setIsDeleted(0);
        when(squarePostMapper.selectById(3L)).thenReturn(post);
        when(squareCommentMapper.selectByPostId(3L)).thenReturn(List.of(new SquareComment()));

        controller().listSquareComments(3L);

        verify(squareCommentMapper).selectByPostId(3L);
    }

    @Test
    void resolvingAnotherReportOnAlreadyDeactivatedMaterialDoesNotFailOrDeactivateTwice() {
        var report = new com.bugaoshan.entity.MaterialReport();
        report.setReportId(4L); report.setMaterialId(3L); report.setStatus(0);
        var material = new Material(); material.setMaterialId(3L); material.setStatus(3);
        when(materialReportMapper.selectForUpdate(4L)).thenReturn(report);
        when(materialMapper.selectForUpdate(3L)).thenReturn(material);
        when(materialReportMapper.resolvePending(4L, 1, "deactivate", "重复举报核实成立", 1L)).thenReturn(1);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(1L, null));
        try {
            controller().handleReport(4L, Map.of("action", "deactivate", "reason", "重复举报核实成立"));
            verify(materialReviewService, never()).deactivate(anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyString());
            verify(materialReportMapper).resolvePending(4L, 1, "deactivate", "重复举报核实成立", 1L);
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void repeatReportHandlingCannotOverwriteTheDecision() {
        var report = new com.bugaoshan.entity.MaterialReport(); report.setStatus(2);
        when(materialReportMapper.selectForUpdate(4L)).thenReturn(report);
        assertThrows(BusinessException.class, () -> controller().handleReport(4L,
                Map.of("action", "deactivate", "reason", "second decision")));
        verify(materialReviewService, never()).deactivate(anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void completedOrderRequiresDeliveryExplanation() {
        Order order = new Order(); order.setStatus(1);
        when(orderMapper.selectById(4L)).thenReturn(order);
        assertThrows(BusinessException.class, () -> controller().completeOrder(4L, Map.of("fulfillment_note", " ")));
        verify(orderMapper, never()).completeWithNote(anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    private Course validCourse() {
        Course course = new Course();
        course.setCourseName("Test Course");
        course.setCourseCode("CS200");
        course.setCourseType(2);
        course.setCredit(new java.math.BigDecimal("2.0"));
        course.setSelectionCount(0);
        course.setStatus(1);
        return course;
    }

    private Goods validGoods() {
        Goods goods = new Goods();
        goods.setGoodsName("Notebook");
        goods.setCategory(1);
        goods.setPriceScore(0);
        goods.setStock(0);
        goods.setExchangeLimit(0);
        goods.setStatus(1);
        return goods;
    }
}
