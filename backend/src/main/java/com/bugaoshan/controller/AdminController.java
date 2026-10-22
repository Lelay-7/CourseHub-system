package com.bugaoshan.controller;

import com.bugaoshan.common.BusinessException;
import com.bugaoshan.common.Result;
import com.bugaoshan.common.SecurityUtils;
import com.bugaoshan.entity.*;
import com.bugaoshan.mapper.*;
import com.bugaoshan.service.CourseService;
import com.bugaoshan.service.MaterialReviewService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 管理员后台 API
 * 路径: /api/admin/**
 * 仅 ROLE_ADMIN 可访问（SecurityConfig 控制）
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserMapper userMapper;
    private final CourseMapper courseMapper;
    private final CourseReviewMapper courseReviewMapper;
    private final MaterialMapper materialMapper;
    private final BannerMapper bannerMapper;
    private final GoodsMapper goodsMapper;
    private final FeedbackMapper feedbackMapper;
    private final SquarePostMapper squarePostMapper;
    private final SquareCommentMapper squareCommentMapper;
    private final ScoreRecordMapper scoreRecordMapper;
    private final OrderMapper orderMapper;
    private final MaterialReportMapper materialReportMapper;
    private final CourseService courseService;
    private final MaterialReviewService materialReviewService;

    public AdminController(UserMapper userMapper, CourseMapper courseMapper,
                           CourseReviewMapper courseReviewMapper, MaterialMapper materialMapper,
                           BannerMapper bannerMapper, GoodsMapper goodsMapper,
                           FeedbackMapper feedbackMapper, SquarePostMapper squarePostMapper,
                           SquareCommentMapper squareCommentMapper,
                           ScoreRecordMapper scoreRecordMapper, OrderMapper orderMapper,
                           MaterialReportMapper materialReportMapper,
                           CourseService courseService, MaterialReviewService materialReviewService) {
        this.userMapper = userMapper;
        this.courseMapper = courseMapper;
        this.courseReviewMapper = courseReviewMapper;
        this.materialMapper = materialMapper;
        this.bannerMapper = bannerMapper;
        this.goodsMapper = goodsMapper;
        this.feedbackMapper = feedbackMapper;
        this.squarePostMapper = squarePostMapper;
        this.squareCommentMapper = squareCommentMapper;
        this.scoreRecordMapper = scoreRecordMapper;
        this.orderMapper = orderMapper;
        this.materialReportMapper = materialReportMapper;
        this.courseService = courseService;
        this.materialReviewService = materialReviewService;
    }

    // ==================== 仪表盘 ====================

    @GetMapping("/dashboard")
    public Result<?> dashboard() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("bannerCount", bannerMapper.selectAll().size());
        stats.put("userCount", userMapper.selectByUserType(1).size()
                + userMapper.selectByUserType(2).size()
                + userMapper.selectByUserType(3).size());
        stats.put("courseCount", courseMapper.countList(null, null, null, null, null, null, null));
        stats.put("pendingMaterialCount", materialMapper.countAllForAdmin(0));
        return Result.ok(stats);
    }

    // ==================== 课程管理 ====================

    @GetMapping("/courses")
    public Result<?> listCourses(@RequestParam(required = false) String keyword,
                                  @RequestParam(defaultValue = "1") Integer page,
                                  @RequestParam(defaultValue = "10") Integer pageSize) {
        String normalizedKeyword = normalizeKeyword(keyword);
        long total = courseMapper.countAllForAdmin(normalizedKeyword);
        page = Math.max(page, 1);
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (page - 1) * pageSize;
        List<Course> list = courseMapper.selectAllForAdmin(normalizedKeyword, offset, pageSize);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total);
        return Result.ok(result);
    }

    @PostMapping("/courses")
    public Result<?> createCourse(@RequestBody Course course) {
        validateCourse(course);
        normalizeCourse(course);
        if (courseMapper.selectByCourseCode(course.getCourseCode()) != null) {
            throw new BusinessException("课程代码已存在");
        }
        validateCourseRelations(course);
        course.setCreateTime(LocalDateTime.now());
        course.setUpdateTime(LocalDateTime.now());
        course.setIsDeleted(0);
        if (course.getStatus() == null) course.setStatus(1);
        if (course.getSelectionCount() == null) course.setSelectionCount(0);
        if (course.getRatingCount() == null) course.setRatingCount(0);
        try {
            courseMapper.insert(course);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("课程代码已存在，包括已删除课程使用的代码");
        }
        return Result.ok(course);
    }

    private void validateCourse(Course course) {
        if (course.getCourseName() == null || course.getCourseName().isBlank()) {
            throw new BusinessException("课程名称不能为空");
        }
        if (course.getCourseCode() == null || course.getCourseCode().isBlank()) {
            throw new BusinessException("课程代码不能为空");
        }
        if (course.getCourseType() == null || course.getCourseType() < 1 || course.getCourseType() > 3) {
            throw new BusinessException("课程类型无效");
        }
        if (course.getCredit() == null || course.getCredit().signum() <= 0) {
            throw new BusinessException("课程学分必须大于 0");
        }
        if (course.getCourseName().trim().length() < 2 || course.getCourseName().trim().length() > 100) {
            throw new BusinessException("课程名称长度应为 2-100 个字符");
        }
        if (course.getCourseCode().trim().length() < 2 || course.getCourseCode().trim().length() > 20) {
            throw new BusinessException("课程代码长度应为 2-20 个字符");
        }
        if (course.getSelectionCount() != null && course.getSelectionCount() < 0) {
            throw new BusinessException("选课人数不能小于 0");
        }
        if (course.getStatus() != null && course.getStatus() != 0 && course.getStatus() != 1) {
            throw new BusinessException("课程状态无效");
        }
    }

    @PutMapping("/courses/{id}")
    public Result<?> updateCourse(@PathVariable Long id, @RequestBody Course course) {
        Course current = courseMapper.selectById(id);
        if (current == null) throw new BusinessException("课程不存在");
        validateCourse(course);
        normalizeCourse(course);
        Course duplicate = courseMapper.selectByCourseCode(course.getCourseCode());
        if (duplicate != null && !duplicate.getCourseId().equals(id)) {
            throw new BusinessException("课程代码已存在");
        }
        validateCourseRelations(course);
        course.setCourseId(id);
        course.setUpdateTime(LocalDateTime.now());
        try {
            courseMapper.updateById(course);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("课程代码已存在，包括已删除课程使用的代码");
        }
        return Result.ok();
    }

    @PutMapping("/courses/{id}/status")
    public Result<?> updateCourseStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Course course = courseMapper.selectById(id);
        if (course == null) throw new BusinessException("课程不存在");
        Integer status = requireBinaryStatus(body.get("status"), "课程状态无效");
        if (courseMapper.updateStatus(id, status) != 1) throw new BusinessException("课程状态更新失败");
        return Result.ok(Map.of("courseId", id, "status", status));
    }

    @DeleteMapping("/courses/{id}")
    public Result<?> deleteCourse(@PathVariable Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) throw new BusinessException("课程不存在");
        courseMapper.deleteById(id);
        return Result.ok();
    }

    // ==================== 评价管理 ====================

    @GetMapping("/reviews")
    public Result<?> listReviews(@RequestParam(required = false) Long courseId,
                                  @RequestParam(defaultValue = "1") Integer page,
                                  @RequestParam(defaultValue = "20") Integer pageSize) {
        page = Math.max(page, 1);
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (page - 1) * pageSize;
        List<CourseReview> reviews = courseId == null
                ? courseReviewMapper.selectAllForAdmin(offset, pageSize)
                : courseReviewMapper.selectByCourseId(courseId, offset, pageSize);
        long total = courseId == null ? courseReviewMapper.countAllForAdmin()
                : courseReviewMapper.countByCourseId(courseId);
        return Result.ok(Map.of("list", reviews, "total", total));
    }

    @DeleteMapping("/reviews/{id}")
    public Result<?> deleteReview(@PathVariable Long id) {
        // Resolve the course outside the mutation transaction; its first read must be the course lock.
        CourseReview review = courseReviewMapper.selectById(id);
        if (review == null) throw new BusinessException("评价不存在");
        return courseService.deleteReviewForAdmin(review.getCourseId(), id);
    }

    // ==================== 资料审核 ====================

    @GetMapping("/materials")
    public Result<?> listMaterials(@RequestParam(required = false) Integer status,
                                    @RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "20") Integer pageSize) {
        page = Math.max(page, 1);
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (page - 1) * pageSize;
        List<Material> list = materialMapper.selectAllForAdmin(status, offset, pageSize);
        long total = materialMapper.countAllForAdmin(status);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total);
        return Result.ok(result);
    }

    @PutMapping("/materials/{id}/deactivate")
    public Result<?> deactivateMaterial(@PathVariable Long id, @RequestBody Map<String, String> body) {
        materialReviewService.deactivate(id, SecurityUtils.getCurrentUserId(),
                requireText(body.get("reason"), "请填写下架说明", 500));
        return Result.ok();
    }

    // ==================== 用户管理 ====================

    @GetMapping("/users")
    public Result<?> listUsers(@RequestParam(required = false) Integer userType,
                                @RequestParam(required = false) Integer status,
                                @RequestParam(required = false) String keyword,
                                @RequestParam(defaultValue = "1") Integer page,
                                @RequestParam(required = false) Integer size,
                                @RequestParam(required = false) Integer pageSize) {
        int resolvedPage = Math.max(page, 1);
        int resolvedSize = Math.min(Math.max(size != null ? size : pageSize != null ? pageSize : 10, 1), 100);
        int offset = (resolvedPage - 1) * resolvedSize;
        String normalizedKeyword = normalizeKeyword(keyword);
        List<User> users = userMapper.selectAdminPage(
                normalizedKeyword, userType, status, offset, resolvedSize);
        long total = userMapper.countAdminPage(normalizedKeyword, userType, status);
        users.forEach(u -> u.setPasswordHash(null));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", users);
        result.put("total", total);
        return Result.ok(result);
    }

    @PutMapping("/users/{id}/status")
    public Result<?> toggleUserStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException("用户不存在");
        Integer newStatus = requireBinaryStatus(body.get("status"), "用户状态无效");
        if (newStatus == 0 && id.equals(SecurityUtils.getCurrentUserId())) {
            throw new BusinessException("不能禁用当前登录管理员");
        }
        if (userMapper.updateStatus(id, newStatus) != 1) throw new BusinessException("用户状态更新失败");
        return Result.ok(Map.of("userId", id, "status", newStatus));
    }

    // ==================== Banner 管理 ====================

    @GetMapping("/banners")
    public Result<?> listBanners() {
        return Result.ok(bannerMapper.selectAllForAdmin());
    }

    @PostMapping("/banners")
    public Result<?> createBanner(@RequestBody Banner banner) {
        banner.setCreateTime(LocalDateTime.now());
        banner.setUpdateTime(LocalDateTime.now());
        banner.setIsDeleted(0);
        if (banner.getStatus() == null) banner.setStatus(1);
        bannerMapper.insert(banner);
        return Result.ok(banner);
    }

    @PutMapping("/banners/{id}")
    public Result<?> updateBanner(@PathVariable Long id, @RequestBody Banner banner) {
        banner.setBannerId(id);
        banner.setUpdateTime(LocalDateTime.now());
        bannerMapper.updateById(banner);
        return Result.ok();
    }

    @DeleteMapping("/banners/{id}")
    public Result<?> deleteBanner(@PathVariable Long id) {
        bannerMapper.deleteById(id);
        return Result.ok();
    }

    // ==================== 商品管理 ====================

    @GetMapping("/goods")
    public Result<?> listGoods(@RequestParam(required = false) String keyword,
                               @RequestParam(required = false) Integer status,
                               @RequestParam(defaultValue = "1") Integer page,
                               @RequestParam(defaultValue = "20") Integer pageSize) {
        int resolvedPage = Math.max(page, 1);
        int resolvedSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (resolvedPage - 1) * resolvedSize;
        String normalizedKeyword = normalizeKeyword(keyword);
        List<Goods> list = goodsMapper.selectAllForAdmin(normalizedKeyword, status, offset, resolvedSize);
        long total = goodsMapper.countAllForAdmin(normalizedKeyword, status);
        return Result.ok(Map.of("list", list, "total", total));
    }

    @PostMapping("/goods")
    public Result<?> createGoods(@RequestBody Goods goods) {
        validateGoods(goods);
        goods.setGoodsName(goods.getGoodsName().trim());
        goods.setCreateTime(LocalDateTime.now());
        goods.setUpdateTime(LocalDateTime.now());
        goods.setIsDeleted(0);
        if (goods.getStatus() == null) goods.setStatus(1);
        goodsMapper.insert(goods);
        return Result.ok(goods);
    }

    @PutMapping("/goods/{id}")
    public Result<?> updateGoods(@PathVariable Long id, @RequestBody Goods goods) {
        if (goodsMapper.selectById(id) == null) throw new BusinessException("商品不存在");
        validateGoods(goods);
        goods.setGoodsName(goods.getGoodsName().trim());
        goods.setGoodsId(id);
        if (goodsMapper.updateGoods(goods) != 1) throw new BusinessException("商品更新失败");
        return Result.ok();
    }

    @PutMapping("/goods/{id}/status")
    public Result<?> updateGoodsStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        if (goodsMapper.selectById(id) == null) throw new BusinessException("商品不存在");
        Integer status = requireBinaryStatus(body.get("status"), "商品状态无效");
        if (goodsMapper.updateStatus(id, status) != 1) throw new BusinessException("商品状态更新失败");
        return Result.ok(Map.of("goodsId", id, "status", status));
    }

    @DeleteMapping("/goods/{id}")
    public Result<?> deleteGoods(@PathVariable Long id) {
        goodsMapper.deleteById(id);
        return Result.ok();
    }

    // ==================== 反馈管理 ====================

    @GetMapping("/feedback")
    public Result<?> listFeedback(@RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "20") Integer pageSize) {
        page = Math.max(page, 1); pageSize = Math.min(Math.max(pageSize, 1), 100);
        return Result.ok(Map.of("list", feedbackMapper.selectAllWithUser(status, (page-1)*pageSize, pageSize),
                "total", feedbackMapper.countAll(status)));
    }

    @PutMapping("/feedback/{id}/reply")
    public Result<?> replyFeedback(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reply = requireText(body.get("reply"), "回复内容不能为空", 1000);
        if (feedbackMapper.updateReply(id, reply, LocalDateTime.now()) != 1) throw new BusinessException("反馈不存在或状态已变化");
        return Result.ok();
    }

    // ==================== 订单管理 ====================

    @GetMapping("/orders")
    public Result<?> listOrders(@RequestParam(required = false) Integer status,
                                 @RequestParam(defaultValue = "1") Integer page,
                                 @RequestParam(defaultValue = "20") Integer pageSize) {
        page = Math.max(page, 1);
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (page - 1) * pageSize;
        List<Order> list = orderMapper.selectAll(status != null ? status : null, offset, pageSize);
        long total = orderMapper.countAll(status != null ? status : null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total);
        return Result.ok(result);
    }

    @PutMapping({"/orders/{id}/complete", "/orders/{id}/ship"})
    public Result<?> completeOrder(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Order order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("订单不存在");
        if (order.getStatus() == 4) throw new BusinessException("已取消订单不能完成");
        if (order.getStatus() == 3) throw new BusinessException("订单已完成，请勿重复操作");
        if (order.getStatus() != 1 && order.getStatus() != 2) {
            throw new BusinessException("当前订单状态不能完成");
        }
        String note = requireText(body.get("fulfillment_note"), "请填写订单交付或处理说明", 1000);
        if (orderMapper.completeWithNote(id, note) != 1) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }
        return Result.ok();
    }

    @GetMapping("/orders/{id}")
    public Result<?> orderDetail(@PathVariable Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) throw new BusinessException("订单不存在");
        return Result.ok(order);
    }

    private String requireText(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) throw new BusinessException(message);
        String text = value.trim();
        if (text.length() > maxLength) throw new BusinessException("内容不能超过 " + maxLength + " 个字符");
        return text;
    }

    private void normalizeCourse(Course course) {
        course.setCourseName(course.getCourseName().trim());
        course.setCourseCode(course.getCourseCode().trim());
    }

    private void validateCourseRelations(Course course) {
        if (course.getTeacherId() != null) {
            User teacher = userMapper.selectById(course.getTeacherId());
            if (teacher == null || teacher.getUserType() != 2) {
                throw new BusinessException("授课教师不存在或用户类型不是教师");
            }
        }
        if (course.getCategoryId() != null && !courseMapper.categoryExists(course.getCategoryId())) {
            throw new BusinessException("课程分类不存在");
        }
    }

    private void validateGoods(Goods goods) {
        if (goods.getGoodsName() == null || goods.getGoodsName().isBlank()) {
            throw new BusinessException("商品名称不能为空");
        }
        if (goods.getPriceScore() == null || goods.getPriceScore() < 0) {
            throw new BusinessException("商品积分不能小于 0");
        }
        if (goods.getStock() == null || goods.getStock() < 0) {
            throw new BusinessException("商品库存不能小于 0");
        }
        if (goods.getExchangeLimit() != null && goods.getExchangeLimit() < 0) {
            throw new BusinessException("限兑次数不能小于 0");
        }
        if (goods.getCategory() == null || goods.getCategory() < 1 || goods.getCategory() > 3) {
            throw new BusinessException("商品分类无效");
        }
        if (goods.getStatus() != null && goods.getStatus() != 0 && goods.getStatus() != 1) {
            throw new BusinessException("商品状态无效");
        }
    }

    private Integer requireBinaryStatus(Integer status, String message) {
        if (status == null || (status != 0 && status != 1)) throw new BusinessException(message);
        return status;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        return keyword.trim();
    }

    // ==================== 广场管理 ====================

    @GetMapping("/square")
    public Result<?> listSquarePosts(@RequestParam(required = false) Integer status,
                                      @RequestParam(defaultValue = "1") Integer page,
                                      @RequestParam(defaultValue = "20") Integer pageSize) {
        page = Math.max(page, 1);
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (page - 1) * pageSize;
        List<SquarePost> list = squarePostMapper.selectAllWithUser(status, null, null, offset, pageSize);
        long total = squarePostMapper.countAllWithUser(status, null, null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total);
        return Result.ok(result);
    }

    @DeleteMapping("/square/{id}")
    public Result<?> deleteSquarePost(@PathVariable Long id) {
        squarePostMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/square/{id}/comments")
    public Result<?> listSquareComments(@PathVariable Long id) {
        SquarePost post = squarePostMapper.selectById(id);
        if (post == null || Integer.valueOf(1).equals(post.getIsDeleted())) {
            throw new BusinessException("帖子不存在");
        }
        return Result.ok(squareCommentMapper.selectByPostId(id));
    }

    @DeleteMapping("/square/comments/{id}")
    public Result<?> deleteSquareComment(@PathVariable Long id) {
        SquareComment comment = squareCommentMapper.selectById(id);
        if (comment == null || Integer.valueOf(1).equals(comment.getIsDeleted())) {
            throw new BusinessException("评论不存在");
        }
        if (squareCommentMapper.softDeleteById(id) != 1) {
            throw new BusinessException("评论状态已变化，请刷新后重试");
        }
        return Result.ok();
    }

    // ==================== 举报管理 ====================

    @GetMapping("/reports")
    public Result<?> listReports(@RequestParam(defaultValue = "1") Integer page,
                                  @RequestParam(defaultValue = "20") Integer pageSize,
                                  @RequestParam(required = false) Integer status) {
        if (status != null && (status < 0 || status > 2)) throw new BusinessException("举报状态无效");
        page = Math.max(page, 1);
        pageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (page - 1) * pageSize;
        List<MaterialReport> list = materialReportMapper.selectAll(status, offset, pageSize);
        long total = materialReportMapper.countAll(status);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total);
        return Result.ok(result);
    }

    @PutMapping("/reports/{id}/handle")
    @Transactional
    public Result<?> handleReport(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String action = body.get("action");
        if (!"deactivate".equals(action) && !"dismiss".equals(action)) {
            throw new BusinessException("请选择下架资料或不予下架");
        }
        String reason = requireText(body.get("reason"), "请填写举报处理说明", 500);
        MaterialReport report = materialReportMapper.selectForUpdate(id);
        if (report == null) throw new BusinessException("举报不存在");
        if (!Integer.valueOf(0).equals(report.getStatus())) throw new BusinessException("举报已处理，请刷新后查看");
        Long actorId = SecurityUtils.getCurrentUserId();
        if ("deactivate".equals(action)) {
            Material material = materialMapper.selectForUpdate(report.getMaterialId());
            if (material == null) throw new BusinessException("资料已不存在，请选择不予下架并说明原因");
            if (!Integer.valueOf(3).equals(material.getStatus())) {
                materialReviewService.deactivate(report.getMaterialId(), actorId, reason);
            }
        }
        if (materialReportMapper.resolvePending(id, "deactivate".equals(action) ? 1 : 2,
                action, reason, actorId) != 1) throw new BusinessException("举报状态已变化，请刷新后重试");
        return Result.ok();
    }
}
