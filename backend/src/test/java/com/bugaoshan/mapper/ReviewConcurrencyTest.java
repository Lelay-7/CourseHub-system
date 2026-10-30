package com.bugaoshan.mapper;

import com.bugaoshan.controller.AdminController;
import com.bugaoshan.dto.CourseReviewDTO;
import com.bugaoshan.entity.Course;
import com.bugaoshan.entity.CourseReview;
import com.bugaoshan.service.CourseService;
import com.bugaoshan.service.impl.CourseServiceImpl;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ReviewConcurrencyTest {

    @Test
    void simultaneousAdminDeletionsLeaveZeroVisibleReviewsAndZeroRatingCount() throws Exception {
        verifyConcurrentDeletions(false);
    }

    @Test
    void adminAndOwnerDeletionShareTheSameCourseLock() throws Exception {
        verifyConcurrentDeletions(true);
    }

    private void verifyConcurrentDeletions(boolean ownerDeletesSecond) throws Exception {
        Configuration mappings = BusinessSqlContractTest.mapperConfiguration("CourseMapper");
        ReentrantLock courseRow = new ReentrantLock();
        CyclicBarrier unlockedReads = new CyclicBarrier(2);
        Map<Long, CourseReview> visible = new ConcurrentHashMap<>();
        for (long id : new long[]{8L, 9L}) {
            CourseReview review = new CourseReview();
            review.setReviewId(id);
            review.setCourseId(7L);
            review.setUserId(id + 10);
            review.setRating(5);
            review.setIsDeleted(0);
            visible.put(id, review);
        }
        AtomicInteger storedCount = new AtomicInteger(2);
        ThreadLocal<Boolean> inTransaction = ThreadLocal.withInitial(() -> false);
        ThreadLocal<Map<Long, CourseReview>> readView = new ThreadLocal<>();
        ThreadLocal<Set<Long>> ownDeletes = ThreadLocal.withInitial(HashSet::new);
        CourseMapper courses = mock(CourseMapper.class, invocation -> {
            String method = invocation.getMethod().getName();
            String statement = "com.bugaoshan.mapper.CourseMapper." + method;
            if (mappings.hasStatement(statement) && mappings.getMappedStatement(statement)
                    .getBoundSql(Map.of("courseId", 7L)).getSql().toUpperCase().contains("FOR UPDATE")) {
                courseRow.lock();
                return 7L;
            }
            if ("updateRating".equals(method)) {
                storedCount.set(invocation.getArgument(2));
                return 1;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        CourseReviewMapper reviews = mock(CourseReviewMapper.class, invocation -> {
            String method = invocation.getMethod().getName();
            if (method.startsWith("select") || method.startsWith("exists") || "calcAvgAndCount".equals(method)) {
                // MySQL REPEATABLE READ takes its read view at the first consistent read.
                // Reads made by the controller before entering the service are outside this transaction.
                Map<Long, CourseReview> snapshot = visible;
                if (inTransaction.get()) {
                    if (readView.get() == null) {
                        readView.set(new HashMap<>(visible));
                        if (!courseRow.isHeldByCurrentThread()) unlockedReads.await(3, TimeUnit.SECONDS);
                    }
                    snapshot = readView.get();
                }
                if ("selectById".equals(method)) return snapshot.get(invocation.<Long>getArgument(0));
                if ("existsByUserIdAndCourseId".equals(method)) {
                    Long userId = invocation.getArgument(0);
                    return snapshot.values().stream().anyMatch(r -> r.getUserId().equals(userId));
                }
                if ("calcAvgAndCount".equals(method)) {
                    long count = snapshot.keySet().stream().filter(id -> !ownDeletes.get().contains(id)).count();
                    return Map.of("avg_rating", count == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(5),
                            "rating_count", count);
                }
            }
            Long deletedId = null;
            if ("deleteById".equals(method)) deletedId = invocation.getArgument(0);
            if ("deleteByUserIdAndCourseId".equals(method)) deletedId = invocation.<Long>getArgument(0) - 10;
            if (deletedId != null) {
                ownDeletes.get().add(deletedId);
                return visible.remove(deletedId) == null ? 0 : 1;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        UserMapper users = mock(UserMapper.class);
        ScoreRecordMapper scoreRecords = mock(ScoreRecordMapper.class);
        CourseServiceImpl implementation = new CourseServiceImpl(courses, reviews,
                mock(CourseSelectionMapper.class), mock(FavoriteMapper.class), users, scoreRecords);
        // Decorate only the transaction boundary; execute the real controller and service methods.
        CourseService transactionalService = mock(CourseService.class, invocation -> {
            inTransaction.set(true);
            try {
                return invocation.getMethod().invoke(implementation, invocation.getArguments());
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            } finally {
                inTransaction.remove();
                readView.remove();
                ownDeletes.remove();
                if (courseRow.isHeldByCurrentThread()) courseRow.unlock();
            }
        });
        AdminController admin = new AdminController(users, courses, reviews, mock(MaterialMapper.class),
                mock(BannerMapper.class), mock(GoodsMapper.class), mock(FeedbackMapper.class),
                mock(SquarePostMapper.class), mock(SquareCommentMapper.class), scoreRecords,
                mock(OrderMapper.class), mock(MaterialReportMapper.class), transactionalService,
                mock(com.bugaoshan.service.MaterialReviewService.class));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> admin.deleteReview(8L));
            var second = executor.submit(() -> ownerDeletesSecond
                    ? transactionalService.deleteReview(7L, 19L) : admin.deleteReview(9L));
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            assertEquals(0, visible.size());
            assertEquals(0, storedCount.get(), "Course statistics must match both committed deletions");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void submissionLocksCourseBeforeCreatingItsReadSnapshot() throws Exception {
        Configuration mappings = BusinessSqlContractTest.mapperConfiguration("CourseMapper");
        AtomicInteger lockingReads = new AtomicInteger();
        CourseMapper courses = mock(CourseMapper.class, invocation -> {
            String method = invocation.getMethod().getName();
            String statement = "com.bugaoshan.mapper.CourseMapper." + method;
            if (mappings.hasStatement(statement) && mappings.getMappedStatement(statement)
                    .getBoundSql(Map.of("courseId", 7L)).getSql().toUpperCase().contains("FOR UPDATE")) {
                lockingReads.incrementAndGet();
                return 7L;
            }
            if ("selectById".equals(method)) {
                assertTrue(lockingReads.get() > 0, "Lock must precede the first ordinary read");
                Course inactive = new Course();
                inactive.setStatus(0);
                return inactive;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        CourseServiceImpl service = new CourseServiceImpl(courses, mock(CourseReviewMapper.class),
                mock(CourseSelectionMapper.class), mock(FavoriteMapper.class), mock(UserMapper.class),
                mock(ScoreRecordMapper.class));

        assertThrows(com.bugaoshan.common.BusinessException.class,
                () -> service.submitReview(7L, 18L, new CourseReviewDTO()));
    }
}
