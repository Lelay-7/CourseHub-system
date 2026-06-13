# Second Round Admin Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make administrator user, order, course, and goods management use real data, consistent statuses, and server-side validation.

**Architecture:** Keep the existing Vue 3, Spring Boot, and MyBatis structure. Add focused mapper queries for administrator lists, keep validation in the existing `AdminController`, and use conditional SQL updates for state transitions.

**Tech Stack:** Java 21, Spring Boot 3, MyBatis/MyBatis-Plus, MySQL 8, Vue 3, Element Plus, JUnit 5, Mockito.

---

### Task 1: Real administrator user pagination

**Files:**
- Modify: `backend/src/main/java/com/bugaoshan/mapper/UserMapper.java`
- Modify: `backend/src/main/resources/mapper/UserMapper.xml`
- Modify: `backend/src/main/java/com/bugaoshan/controller/AdminController.java`
- Modify: `frontend/src/views/admin/UserManageView.vue`
- Test: `backend/src/test/java/com/bugaoshan/controller/AdminControllerTest.java`

- [ ] Add failing tests for mapper-backed pagination, status filtering, password removal, invalid status, and self-disable prevention.
- [ ] Add `selectAdminPage`, `countAdminPage`, and conditional `updateStatus` mapper methods.
- [ ] Make `GET /api/admin/users` support `page`, `pageSize`/`size`, `keyword`, `userType`, and `status`.
- [ ] Make `PUT /api/admin/users/{id}/status` accept only 0 or 1 and reject disabling the current administrator.
- [ ] Add keyword, type, status, and pagination controls to the existing page without changing its visual style.

### Task 2: Consistent order completion semantics

**Files:**
- Modify: `backend/src/main/java/com/bugaoshan/controller/AdminController.java`
- Modify: `backend/src/main/java/com/bugaoshan/mapper/OrderMapper.java`
- Modify: `backend/src/main/resources/mapper/OrderMapper.xml`
- Modify: `frontend/src/api/admin.js`
- Modify: `frontend/src/views/admin/OrderManageView.vue`
- Modify: `frontend/src/views/profile/OrderDetailView.vue`
- Test: `backend/src/test/java/com/bugaoshan/controller/AdminControllerTest.java`

- [ ] Add failing tests proving cancelled and completed orders cannot be completed again.
- [ ] Add `PUT /api/admin/orders/{id}/complete` and transition only status 1 or 2 to status 3.
- [ ] Rename frontend actions and messages from shipping to completing an order.
- [ ] Keep user cancellation restricted to status 1 and align user status labels and steps.

### Task 3: Course administrator validation and visibility

**Files:**
- Modify: `backend/src/main/java/com/bugaoshan/mapper/CourseMapper.java`
- Modify: `backend/src/main/resources/mapper/CourseMapper.xml`
- Modify: `backend/src/main/java/com/bugaoshan/controller/AdminController.java`
- Modify: `frontend/src/api/admin.js`
- Modify: `frontend/src/views/admin/CourseManageView.vue`
- Test: `backend/src/test/java/com/bugaoshan/controller/AdminControllerTest.java`

- [ ] Add failing tests for duplicate edit codes, invalid credit/count/status, invalid teacher, and invalid category.
- [ ] Add administrator course list/count queries that include both status 0 and 1.
- [ ] Add category existence lookup and explicit course update SQL.
- [ ] Validate name/code lengths, course type, credit, selection count, teacher role, category, and status for create and update.
- [ ] Add status display and an up/down action while preserving the existing table and dialog style.

### Task 4: Goods administrator validation and visibility

**Files:**
- Modify: `backend/src/main/java/com/bugaoshan/mapper/GoodsMapper.java`
- Modify: `backend/src/main/resources/mapper/GoodsMapper.xml`
- Modify: `backend/src/main/java/com/bugaoshan/controller/AdminController.java`
- Modify: `frontend/src/api/admin.js`
- Modify: `frontend/src/views/admin/GoodsManageView.vue`
- Test: `backend/src/test/java/com/bugaoshan/controller/AdminControllerTest.java`

- [ ] Add failing tests for blank name, negative price/stock/limit, invalid category/status, and missing records.
- [ ] Add administrator goods list/count queries that include both status 0 and 1.
- [ ] Validate create/update payloads and use conditional status updates for up/down actions.
- [ ] Make the admin page use the admin goods endpoint and expose exchange limit and status controls.

### Task 5: Verification

- [ ] Run targeted backend tests and confirm the new tests pass.
- [ ] Run the complete backend test suite with JDK 21.
- [ ] Run frontend contract tests and production build.
- [ ] Restart the local services and verify all four workflows through authenticated HTTP calls.
- [ ] Confirm ordinary course and goods lists still exclude status 0 records.
