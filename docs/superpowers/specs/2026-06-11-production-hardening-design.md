# Bugaoshan System Production Hardening Design

## Goal

Convert the current local course-system prototype into a production-ready baseline that remains easy to run on a developer workstation and can later move to a cloud server without changing business APIs.

## Scope

The repair covers the confirmed review findings:

- secrets and environment configuration;
- access and refresh token separation;
- disabled-user enforcement and SMS verification safety;
- object-level authorization and privacy filtering;
- database schema consistency and rebuild scripts;
- transactional score, stock, refund, and review-reward operations;
- local file storage behind a cloud-ready storage interface;
- frontend API consistency and user-scoped persisted state;
- reliable Windows startup and shutdown scripts;
- automated regression tests for security and critical business rules.

Unrelated visual redesign and broad framework replacement are out of scope.

## Delivery Strategy

Work is split into independently verifiable stages:

1. Establish test infrastructure and configuration boundaries.
2. Close authentication, authorization, and privacy vulnerabilities.
3. Rebuild the database schema and make score-related operations atomic and idempotent.
4. Replace Base64 database storage with multipart uploads and controlled downloads.
5. Align frontend contracts, persisted state, and local operations scripts.
6. Run clean builds, tests, schema checks, and focused end-to-end smoke tests.

Each behavior change starts with a failing regression test. Existing uncommitted work is preserved and changes remain scoped to the affected modules.

## Configuration And Secrets

`application.yml` contains only environment-variable references and safe development defaults. Database passwords, Redis passwords, JWT secrets, SMS mode, storage paths, and allowed origins are supplied externally.

The application fails fast when a production profile uses missing or insecure JWT configuration. A checked-in `.env.example` documents required names but contains no usable credentials. Existing exposed credentials and JWT secrets are treated as compromised and must be rotated before cloud deployment.

## Authentication Design

Access and refresh tokens contain an explicit token type claim. The authentication filter accepts only access tokens. The refresh endpoint accepts only refresh tokens and reloads the user before issuing new tokens.

Every authenticated request reloads the user security state through a dedicated service so deleted or disabled users cannot continue using previously issued tokens. Authorities come from the persisted user type, not solely from a token claim.

Mock SMS mode is restricted to a non-production profile and uses one explicit configured test code for all permitted development numbers. Production mode never logs verification codes and requires the real SMS provider path. Sending and verification receive basic expiry, single-use, and request-throttling protections.

## Authorization And Privacy

Object-level authorization is enforced in mapper update/delete conditions, not only in controller checks.

- A friend request may only be accepted by its target user.
- A friend relationship may only be removed by one of its participants.
- Contact list and detail responses apply visibility rules before returning phone or email fields.
- Anonymous course reviews never expose author identifiers, names, or avatars in public responses.
- Material downloads require an approved material, except that the uploader may access their own pending or rejected upload.
- Administrative actions remain protected by the persisted administrator role.

## Database Rebuild

Because existing data may be discarded, `init.sql` becomes the canonical complete schema. It creates every table referenced by current mappers, including friend, feedback, square post, square comment, and square like tables. The course review table includes `is_anonymous`.

The schema adds constraints and indexes for:

- unique user-material download charging records;
- unique course review per user;
- unique square like per user and post;
- unique directed friend request;
- unique order number;
- valid non-negative balances and stock where supported;
- query paths used by list and ownership checks.

Seed credentials are development-only and clearly separated from production configuration.

## Transaction And Concurrency Design

Score and stock mutations use conditional SQL updates rather than read-modify-write assignments.

- Exchange atomically decrements stock and user balance only when both remain sufficient.
- Exchange limits count orders for the requested goods, not all pending orders.
- Cancellation changes `status=1` to `status=4` conditionally; only the request that changes one row restores stock and balance.
- Material approval changes only pending or rejected material to approved and awards points once.
- Course review rewards are issued only for the first successful review, not after delete/reactivate cycles.
- Paid material downloads charge once per user and material; subsequent downloads return the file without another deduction.

All multi-row mutations run in Spring transactions, and a zero-row conditional update becomes a domain error rather than a silent success.

## File Storage Design

A `FileStorageService` owns storage and retrieval through storage keys. Its local implementation writes beneath a configured upload root using generated names and normalized paths. Business records store the storage key, original filename, media type, and file size, never Base64 content.

Upload endpoints accept `multipart/form-data`, validate size and allow-listed content types, and reject empty or malformed files. Download endpoints authorize the caller before resolving a storage key and stream the file with safe response headers.

The interface is intentionally compatible with a later S3 or OSS implementation. Moving to cloud storage requires a new implementation and configuration, not controller or frontend changes.

## Frontend Changes

All network traffic uses the configured Axios instance. Direct relative `fetch('/api/...')` calls are removed. File and avatar uploads use `FormData`; downloads request blobs from authorized endpoints instead of navigating to database-provided URLs.

Persisted course selection is namespaced by user ID and cleared or switched on logout/login. Token refresh uses one shared promise so concurrent requests either retry with the new token or all fail and log out consistently.

Frontend field names follow the backend snake-case response contract. Temporary compatibility aliases may remain only where current screens require them during migration.

## Operations

Windows startup sets `JAVA_HOME` and `PATH` before invoking Maven, checks each command result, and does not launch a stale or missing JAR after a build failure. Shutdown is implemented by a checked-in PowerShell script that targets only the application processes or recorded PIDs.

Cloud deployment documentation will identify environment variables, writable upload volume requirements, reverse-proxy limits, CORS origins, and the storage implementation switch point.

## Testing And Acceptance

Backend tests use JUnit 5, Spring Security Test, Mockito, and focused mapper/service integration tests where SQL behavior matters. Tests must demonstrate:

- refresh tokens cannot authenticate API requests;
- access tokens cannot refresh sessions;
- disabled users are rejected;
- mock SMS cannot accept arbitrary codes;
- friend deletion and acceptance enforce ownership;
- anonymous responses contain no identity fields;
- unapproved material downloads are rejected for other users;
- concurrent or repeated cancellation, approval, exchange, review, and download operations do not duplicate value;
- the rebuilt schema contains every mapper-required table and column.

Frontend verification includes production build, contract tests for API URLs and multipart payloads, and focused unit tests for refresh coordination and user-scoped persistence.

Completion requires:

- Java 21 `mvn clean test` passing with real tests executed;
- `npm run build` passing;
- frontend tests passing;
- a fresh database created successfully from `init.sql`;
- local upload and authorized download smoke tests passing;
- no secrets or Base64 file payloads remaining in tracked configuration or business tables.
