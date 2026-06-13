# Bugaoshan Production Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the confirmed security and data-integrity defects, replace Base64 persistence with local file storage, and leave a cloud-ready production baseline.

**Architecture:** Keep the existing Spring Boot, MyBatis, Vue, and Axios structure. Add narrow security and storage services, move critical balance and status transitions into conditional SQL updates inside transactions, and keep the API contract snake_case. Existing data may be rebuilt from the canonical schema.

**Tech Stack:** Java 21, Spring Boot 3.3, Spring Security, MyBatis/MyBatis-Plus, JUnit 5, Mockito, MySQL 8, Vue 3, Pinia, Axios, Vite.

**Execution constraint:** Work remains local. Do not commit or push. Each task ends with a local verification checkpoint.

---

### Task 1: Security Regression Test Baseline

**Files:**
- Create: `backend/src/test/java/com/bugaoshan/security/JwtUtilTest.java`
- Create: `backend/src/test/java/com/bugaoshan/service/impl/AuthServiceImplTest.java`
- Modify: `backend/pom.xml`

- [ ] Write a failing test asserting access and refresh tokens have distinct accepted types.
- [ ] Write a failing test asserting an access token cannot be used at the refresh endpoint.
- [ ] Write a failing test asserting mock SMS rejects every code except the configured development code.
- [ ] Run `mvn -Dtest=JwtUtilTest,AuthServiceImplTest test` with Java 21 and confirm failures are caused by missing token-type and mock-code enforcement.
- [ ] Implement explicit token type validation, persisted-user status checks during refresh, and configured mock SMS code validation.
- [ ] Re-run the focused tests and then `mvn test`.

### Task 2: Request Authentication And Secret Configuration

**Files:**
- Create: `backend/src/test/java/com/bugaoshan/security/JwtAuthenticationFilterTest.java`
- Modify: `backend/src/main/java/com/bugaoshan/security/JwtAuthenticationFilter.java`
- Modify: `backend/src/main/java/com/bugaoshan/security/SecurityConfig.java`
- Modify: `backend/src/main/resources/application.yml`
- Create: `.env.example`

- [ ] Write failing filter tests for refresh-token rejection and disabled-user rejection.
- [ ] Run the focused filter test and confirm both cases currently authenticate incorrectly.
- [ ] Load the current user from `UserMapper`, require status `1`, and derive authorities from persisted `user_type`.
- [ ] Replace tracked credentials and JWT values with environment references and safe local placeholders; default SMS mock mode to false.
- [ ] Add a non-secret environment example documenting required deployment variables.
- [ ] Re-run security tests and scan tracked configuration for exposed values.

### Task 3: Object Authorization And Privacy

**Files:**
- Create: `backend/src/test/java/com/bugaoshan/controller/ContactControllerTest.java`
- Create: `backend/src/test/java/com/bugaoshan/service/impl/MaterialServiceImplTest.java`
- Modify: `backend/src/main/java/com/bugaoshan/controller/ContactController.java`
- Modify: `backend/src/main/java/com/bugaoshan/mapper/FriendMapper.java`
- Modify: `backend/src/main/resources/mapper/FriendMapper.xml`
- Modify: `backend/src/main/resources/mapper/CourseReviewMapper.xml`
- Modify: `backend/src/main/java/com/bugaoshan/service/impl/MaterialServiceImpl.java`

- [ ] Write failing tests proving a non-participant cannot remove a friendship and an unapproved material cannot be downloaded by another user.
- [ ] Run focused tests and confirm the current ownership/status gaps.
- [ ] Add participant-constrained friend deletion and check affected rows.
- [ ] Remove phone and email from friend-list SQL; expose them only through visibility-filtered detail responses.
- [ ] Null anonymous review identity columns in public review SQL.
- [ ] Apply the same material visibility rule to detail and download operations.
- [ ] Re-run focused and full backend tests.

### Task 4: Canonical Schema And Atomic Value Transfers

**Files:**
- Create: `backend/src/test/java/com/bugaoshan/service/impl/OrderServiceImplTest.java`
- Create: `backend/src/test/java/com/bugaoshan/service/impl/MallServiceImplTest.java`
- Create: `backend/src/main/java/com/bugaoshan/service/AdminMaterialService.java`
- Create: `backend/src/main/java/com/bugaoshan/service/impl/AdminMaterialServiceImpl.java`
- Modify: `backend/src/main/java/com/bugaoshan/controller/AdminController.java`
- Modify: `backend/src/main/java/com/bugaoshan/mapper/UserMapper.java`
- Modify: `backend/src/main/java/com/bugaoshan/mapper/GoodsMapper.java`
- Modify: `backend/src/main/java/com/bugaoshan/mapper/OrderMapper.java`
- Modify: `backend/src/main/java/com/bugaoshan/mapper/MaterialMapper.java`
- Modify: corresponding mapper XML files
- Replace: `backend/src/main/resources/db/init.sql`

- [ ] Write failing tests for duplicate cancellation, insufficient-balance exchange, per-goods exchange limits, and duplicate material approval rewards.
- [ ] Confirm failures before implementation.
- [ ] Add conditional balance deduction/credit, stock decrement/increment, and status-transition mapper methods.
- [ ] Make cancellation, exchange, and approval transactional and require exactly one successful state transition.
- [ ] Count exchange limits by `user_id` and `goods_id` across non-cancelled orders.
- [ ] Rebuild `init.sql` with every mapper-required table and column, including `is_anonymous`, friend, feedback, square tables, and material download records.
- [ ] Add schema constraints and indexes supporting idempotency and ownership queries.
- [ ] Run focused service tests, full backend tests, and a schema table/column contract script.

### Task 5: Local File Storage And Multipart APIs

**Files:**
- Create: `backend/src/main/java/com/bugaoshan/storage/FileStorageService.java`
- Create: `backend/src/main/java/com/bugaoshan/storage/LocalFileStorageService.java`
- Create: `backend/src/main/java/com/bugaoshan/storage/StoredFile.java`
- Create: `backend/src/test/java/com/bugaoshan/storage/LocalFileStorageServiceTest.java`
- Modify: `backend/src/main/java/com/bugaoshan/controller/MaterialController.java`
- Modify: `backend/src/main/java/com/bugaoshan/controller/UserController.java`
- Modify: `backend/src/main/java/com/bugaoshan/service/MaterialService.java`
- Modify: `backend/src/main/java/com/bugaoshan/service/impl/MaterialServiceImpl.java`
- Modify: material/user entities and mapper XML files

- [ ] Write failing tests for traversal-safe storage, generated storage keys, multipart metadata persistence, and authorized streaming.
- [ ] Run tests and confirm storage APIs do not yet exist.
- [ ] Implement local storage rooted at `FILE_UPLOAD_PATH`, with normalized paths and generated names.
- [ ] Store storage key, original filename, content type, and size instead of Base64 content.
- [ ] Change material and avatar uploads to multipart endpoints with explicit size and media-type validation.
- [ ] Stream authorized downloads with safe `Content-Disposition` and content type headers.
- [ ] Add one-charge-per-user material download records and conditional score deduction.
- [ ] Re-run storage, material, and full backend tests.

### Task 6: Frontend Contract, Persistence, And Operations

**Files:**
- Modify: `frontend/src/api/request.js`
- Modify: `frontend/src/api/material.js`
- Modify: `frontend/src/api/auth.js`
- Modify: `frontend/src/views/material/MaterialListView.vue`
- Modify: `frontend/src/views/material/MaterialDetailView.vue`
- Modify: profile avatar views
- Modify: `frontend/src/views/home/HomeView.vue`
- Modify: `frontend/src/stores/selection.js`
- Modify: `frontend/src/stores/user.js`
- Create: `frontend/scripts/production-hardening.test.mjs`
- Modify: `frontend/package.json`
- Modify: `start.bat`
- Modify: `stop.bat`
- Create: `stop.ps1`

- [ ] Write a failing contract script checking multipart upload construction, blob download usage, no direct relative API fetches, user-scoped selection keys, and presence of the stop script.
- [ ] Run the script and confirm current contract failures.
- [ ] Use `FormData` for material/avatar uploads and request authorized blobs for downloads.
- [ ] Route all API traffic through the configured Axios instance and coordinate refresh with one shared promise.
- [ ] Namespace course selections by current user and clear/switch state during authentication changes.
- [ ] Set Java 21 for Maven in `start.bat`, abort on build failure, record local process IDs, and implement targeted shutdown in `stop.ps1`.
- [ ] Run the contract script, `npm run build`, and `npm audit --omit=dev`.

### Task 7: Final Verification

**Files:**
- Modify only files required by failures discovered during verification.

- [ ] Run Java 21 `mvn clean test` and confirm non-zero test execution with zero failures.
- [ ] Run `npm run test:contracts` and `npm run build`.
- [ ] Scan for tracked hard-coded credentials, JWT secrets, Base64 upload persistence, and direct `/api` fetches.
- [ ] Validate `init.sql` contains every table and column used by mapper XML files.
- [ ] Review `git diff --check` and `git status` without reverting unrelated user changes.
- [ ] Report completed fixes, commands run, residual deployment actions, and any unverified environment-dependent behavior.

