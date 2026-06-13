# Third-Round Data Integrity Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent invalid targets, duplicate records, unauthorized deletes, and malformed content in favorites, course reviews, square interactions, material reports, and feedback.

**Architecture:** Keep the existing Vue 3 + Spring Boot + MyBatis structure. Put target and ownership validation in the existing service/controller entry points, use mapper predicates for user-scoped mutation and active-target filtering, retain database unique keys as the final duplicate guard, and add focused Mockito/contract tests before production changes.

**Tech Stack:** Java 21, Spring Boot 3, MyBatis/MyBatis-Plus, JUnit 5, Mockito, Vue 3, Element Plus, Node contract tests.

---

### Task 1: Favorite target validation and filtered listing

**Files:**
- Create: `backend/src/test/java/com/bugaoshan/service/impl/FavoriteServiceImplTest.java`
- Modify: `backend/src/main/java/com/bugaoshan/service/impl/FavoriteServiceImpl.java`
- Modify: `frontend/src/views/profile/FavoriteView.vue`
- Modify: `frontend/scripts/feature-contract.test.mjs`

- [ ] Write tests proving unsupported types, missing/inactive courses, and missing/non-published materials are rejected before insert.
- [ ] Run `mvn.cmd -q -Dtest=FavoriteServiceImplTest test` and confirm the new tests fail for missing validation.
- [ ] Add a strict `course`/`material` type mapper, active-target checks (`status == 1` and not deleted), idempotent duplicate handling, and omission of invalid targets from favorite lists.
- [ ] Add frontend array/null guards and reload the list after successful removal.
- [ ] Run the focused backend test and `npm.cmd run test:contracts` until both pass.

### Task 2: Course review validation

**Files:**
- Modify: `backend/src/test/java/com/bugaoshan/service/impl/CourseServiceImplTest.java`
- Modify: `backend/src/main/java/com/bugaoshan/service/impl/CourseServiceImpl.java`

- [ ] Add tests for missing/inactive courses, absent active selection, invalid rating, blank/oversized content, and duplicate active reviews.
- [ ] Run `mvn.cmd -q -Dtest=CourseServiceImplTest test` and confirm failures occur before review insertion.
- [ ] Validate the course and active selection before duplicate/reactivation logic, normalize review content, and retain user-scoped delete plus rating/count recalculation.
- [ ] Update the existing reactivation test fixture so it represents an active course and active selection.
- [ ] Run the focused test class and confirm all review and selection tests pass.

### Task 3: Square comment integrity and ownership

**Files:**
- Create: `backend/src/test/java/com/bugaoshan/controller/SquareControllerTest.java`
- Modify: `backend/src/main/java/com/bugaoshan/controller/SquareController.java`
- Modify: `backend/src/main/java/com/bugaoshan/mapper/SquareCommentMapper.java`
- Modify: `backend/src/main/resources/mapper/SquareCommentMapper.xml`
- Modify: `backend/src/main/java/com/bugaoshan/controller/AdminController.java`
- Modify: `backend/src/test/java/com/bugaoshan/controller/AdminControllerTest.java`
- Modify: `frontend/src/api/square.js`
- Modify: `frontend/src/views/square/SquareView.vue`

- [ ] Add tests proving comment list/create reject missing, deleted, or inactive posts; blank/over-500 content is rejected; and users can delete only their own comments.
- [ ] Run focused controller tests and confirm the missing guards/endpoints fail.
- [ ] Add active-post validation, content normalization, a user-scoped soft-delete mapper method, and an admin comment-delete endpoint under the existing admin security boundary.
- [ ] Add a small frontend delete action for comments owned by the current user and refresh comments/count after deletion.
- [ ] Run focused tests and frontend contract tests.

### Task 4: Square like integrity

**Files:**
- Modify: `backend/src/test/java/com/bugaoshan/controller/SquareControllerTest.java`
- Modify: `backend/src/main/java/com/bugaoshan/controller/SquareController.java`

- [ ] Add tests proving likes reject invalid posts and toggle only the current user's unique like.
- [ ] Run the focused test and confirm invalid posts currently pass through.
- [ ] Reuse active-post validation before toggling; keep the `(post_id, user_id)` unique key and user-scoped delete as duplicate/ownership protection.
- [ ] Return the database count after every toggle so list and interaction state remain consistent and nonnegative.
- [ ] Run the focused test class.

### Task 5: Material report and feedback validation

**Files:**
- Modify: `backend/src/test/java/com/bugaoshan/service/impl/MaterialServiceImplTest.java`
- Create: `backend/src/test/java/com/bugaoshan/controller/FeedbackControllerTest.java`
- Modify: `backend/src/main/java/com/bugaoshan/service/impl/MaterialServiceImpl.java`
- Modify: `backend/src/main/java/com/bugaoshan/dto/MaterialReportDTO.java`
- Modify: `backend/src/main/java/com/bugaoshan/mapper/MaterialReportMapper.java`
- Modify: `backend/src/main/resources/mapper/MaterialReportMapper.xml`
- Modify: `backend/src/main/java/com/bugaoshan/controller/FeedbackController.java`
- Modify: `backend/src/main/java/com/bugaoshan/mapper/MaterialReportMapper.java`
- Modify: `frontend/src/views/profile/FeedbackView.vue`

- [ ] Add tests for invalid/non-published materials, blank/over-500 report reasons, duplicate pending reports, unsupported feedback types, and blank/over-1000 feedback content.
- [ ] Run focused tests and confirm they fail against the current permissive implementation.
- [ ] Validate published material targets, trim report reasons, block duplicate pending reports, and filter admin report queries through valid material rows.
- [ ] Validate feedback against the four existing UI types, trim content/contact, and enforce the 1000-character backend limit; align the frontend maxlength.
- [ ] Run focused tests.

### Task 6: Full verification and runtime smoke test

**Files:**
- Modify if needed: `scripts/crud-smoke.ps1`

- [ ] Run `mvn.cmd -q test` from `backend` and confirm zero failures/errors.
- [ ] Run `npm.cmd run test:contracts` from `frontend` and confirm the feature contracts pass.
- [ ] Run `npm.cmd run build` from `frontend` and confirm Vite exits successfully.
- [ ] Package/restart the backend if source changes are not reflected in the running process.
- [ ] Exercise authenticated API cases for valid/invalid favorites, reviews, comments, likes, reports, and feedback; clean up temporary records afterward.
- [ ] Re-read the third-round source document and confirm every in-scope acceptance item has either an automated assertion or API smoke result.
