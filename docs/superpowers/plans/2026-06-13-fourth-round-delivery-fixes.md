# Fourth-Round Filtering and Delivery Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move paginated course/material filters to SQL, make seed data internally consistent, and leave a clean, documented local-delivery workspace.

**Architecture:** Extend the existing controller-service-mapper call chains with normalized filter parameters and identical list/count SQL predicates. Keep the existing Vue controls, add real page state, remove current-page post-filtering, validate `init.sql` in an isolated database, and document environment-driven startup without exposing local secrets.

**Tech Stack:** Java 21, Spring Boot 3, MyBatis/MyBatis-Plus, JUnit 5, Mockito, Vue 3, Vite, MySQL 8, PowerShell.

---

### Task 1: Course time filtering

**Files:**
- Modify: `backend/src/test/java/com/bugaoshan/service/impl/CourseServiceImplTest.java`
- Modify: `backend/src/main/java/com/bugaoshan/controller/CourseController.java`
- Modify: `backend/src/main/java/com/bugaoshan/service/CourseService.java`
- Modify: `backend/src/main/java/com/bugaoshan/service/impl/CourseServiceImpl.java`
- Modify: `backend/src/main/java/com/bugaoshan/mapper/CourseMapper.java`
- Modify: `backend/src/main/resources/mapper/CourseMapper.xml`
- Modify: `frontend/src/views/course/CourseListView.vue`
- Modify: `frontend/scripts/feature-contract.test.mjs`

- [ ] Add a failing service test proving valid `timeSlot` values reach both list and count mapper calls and unknown values normalize to no filter.
- [ ] Add frontend contracts requiring `timeSlot` in the request and forbidding current-page schedule filtering.
- [ ] Run focused backend and frontend contract tests and confirm the new assertions fail.
- [ ] Thread `timeSlot` through controller/service/mapper signatures.
- [ ] Add the same `early`, `middle`, and `late` schedule predicate to list and count SQL.
- [ ] Replace `filteredCourses` with the server result, add page state and pagination, reset to page 1 on filter/search changes, and preserve filters during page changes.
- [ ] Run focused tests until green.

### Task 2: Material price filtering

**Files:**
- Modify: `backend/src/test/java/com/bugaoshan/service/impl/MaterialServiceImplTest.java`
- Modify: `backend/src/main/java/com/bugaoshan/controller/MaterialController.java`
- Modify: `backend/src/main/java/com/bugaoshan/service/MaterialService.java`
- Modify: `backend/src/main/java/com/bugaoshan/service/impl/MaterialServiceImpl.java`
- Modify: `backend/src/main/java/com/bugaoshan/mapper/MaterialMapper.java`
- Modify: `backend/src/main/resources/mapper/MaterialMapper.xml`
- Modify: `frontend/src/views/material/MaterialListView.vue`
- Modify: `frontend/scripts/feature-contract.test.mjs`

- [ ] Add failing tests proving `free`/`score` reach list and count while unsupported values normalize to `all`.
- [ ] Add frontend contracts requiring `priceFilter` in the request and forbidding current-page price filtering.
- [ ] Thread `priceFilter` through the backend and add identical free/score predicates to list and count SQL.
- [ ] Replace `visibleMaterials` with server results, add page state and pagination, and reset page 1 on filter/search changes.
- [ ] Run focused tests until green.

### Task 3: Consistent initialization data

**Files:**
- Modify: `backend/src/main/resources/db/init.sql`
- Create: `scripts/validate-init.ps1`

- [ ] Add the missing `material_report` table.
- [ ] Add categories referenced by all courses and retain active category metadata.
- [ ] Seed active/inactive courses across all current time-slot groups.
- [ ] Seed free/paid and pending/published/rejected/deactivated materials with valid users/courses.
- [ ] Seed course selections/reviews, downloads, favorites, square posts/comments/likes, feedback, reports, goods, orders, score records, and news using valid IDs.
- [ ] Add validation queries for role accounts, status coverage, orphan references, and display-data counts.
- [ ] Execute a transformed copy of `init.sql` against an isolated `bgss_xtt_fourth_check` database and drop it afterward.

### Task 4: Delivery ignore rules and examples

**Files:**
- Modify: `.gitignore`
- Modify: `frontend/.gitignore`
- Modify: `backend/.gitignore`
- Modify: `.env.example`
- Create: `frontend/.env.example`
- Create: `backend/src/main/resources/application-example.yml`
- Create: `README.md`

- [ ] Expand ignore rules for dependencies, builds, runtime state, secrets, uploads, logs, coverage, and private IDE files.
- [ ] Keep only actual environment variable names used by the application.
- [ ] Document project modules, Java/Node/MySQL/Redis requirements, database initialization, environment setup, frontend/backend commands, seeded accounts, tests/builds, and troubleshooting.
- [ ] Ensure examples contain placeholders only and no machine-specific absolute paths.

### Task 5: Clean install and full verification

**Files:**
- Remove generated local paths only after resolving and verifying they are inside the workspace:
  `frontend/node_modules`, `frontend/dist`, `backend/target`, `.run`, `.env.local`, `backend/dump.rdb`, `uploads`

- [ ] Stop recorded frontend/backend processes before deleting runtime paths.
- [ ] Verify every resolved cleanup target is inside the workspace, then remove only the listed generated paths.
- [ ] Run `npm.cmd ci`, `npm.cmd run test:contracts`, and `npm.cmd run build`.
- [ ] Run `mvn.cmd -q test` and `mvn.cmd -q clean package`.
- [ ] Re-run isolated database initialization validation.
- [ ] Run `git status --ignored --short` and `git diff --check` to confirm artifacts are ignored and source changes are clean.
