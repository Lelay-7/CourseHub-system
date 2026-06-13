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

    private AdminController controller() {
        return new AdminController(userMapper, courseMapper, courseReviewMapper,
                materialMapper, bannerMapper, goodsMapper, feedbackMapper, squarePostMapper,
                squareCommentMapper,
                scoreRecordMapper, orderMapper, materialReportMapper);
    }

    @Test
    void approvalDoesNotRewardWhenPendingTransitionAffectsNoRows() {
        Material material = new Material();
        material.setMaterialId(3L);
        material.setUploaderId(7L);
        material.setStatus(0);
        when(materialMapper.selectById(3L)).thenReturn(material);
        when(materialMapper.transitionStatus(3L, 0, 1)).thenReturn(0);
        AdminController controller = controller();

        assertThrows(BusinessException.class, () -> controller.approveMaterial(3L));
        verify(userMapper, never()).earnScore(anyLong(), anyInt());
    }

    @Test
    void reviewListWithoutCourseFilterReturnsAllReviews() {
        CourseReview review = new CourseReview();
        review.setReviewId(8L);
        when(courseReviewMapper.selectAllForAdmin(0, 20)).thenReturn(List.of(review));
        AdminController controller = controller();

        var result = controller.listReviews(null, 1, 20);

        assertEquals(List.of(review), result.getData());
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
    void rejectingMaterialPersistsReason() {
        Material material = new Material();
        material.setMaterialId(3L);
        material.setStatus(0);
        when(materialMapper.selectById(3L)).thenReturn(material);
        when(materialMapper.rejectPending(3L, "缺少课程说明")).thenReturn(1);

        controller().rejectMaterial(3L, java.util.Map.of("reason", "缺少课程说明"));

        verify(materialMapper).rejectPending(3L, "缺少课程说明");
    }

    @Test
    void deactivatingPublishedMaterialUsesSeparateStatus() {
        Material material = new Material();
        material.setMaterialId(3L);
        material.setStatus(1);
        when(materialMapper.selectById(3L)).thenReturn(material);
        when(materialMapper.transitionStatus(3L, 1, 3)).thenReturn(1);

        controller().deactivateMaterial(3L);

        verify(materialMapper).transitionStatus(eq(3L), eq(1), eq(3));
        verify(materialMapper, never()).updateStatus(3L, 2);
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

        assertThrows(BusinessException.class, () -> controller().completeOrder(4L));

        verify(orderMapper, never()).completeIfProcessable(anyLong());
    }

    @Test
    void completeOrderTransitionsPendingOrder() {
        Order order = new Order();
        order.setOrderId(4L);
        order.setStatus(1);
        when(orderMapper.selectById(4L)).thenReturn(order);
        when(orderMapper.completeIfProcessable(4L)).thenReturn(1);

        controller().completeOrder(4L);

        verify(orderMapper).completeIfProcessable(4L);
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
