# 账号安全与密码找回验证记录

日期：2026-10-10（实现与验收），2026-10-11（提交前复核）。依据 `docs/superpowers/specs/2026-10-09-account-recovery-design.md`，在现有 `dev1.1` 工作目录实现，未推送或部署。

## 已实现

- 六个账号安全/找回接口、明确 snake_case DTO；独立的找回邮箱和验证时间，仅安全接口返回脱敏状态。
- 6 位安全随机邮件验证码、专用 HMAC 密钥、10 分钟有效期、5 次错误、一次性消费；账号/目的 60 秒和滚动一小时 5 次、IP 一小时 20 次、绑定目标邮箱额外限流。
- 数据库共享范围锁和用户/验证码行锁；错误次数在失败返回前提交，正确消费与绑定/改密同一事务；重发、换邮箱、改密、发送失败及迟到发送结果不会复活旧码。
- 找回统一反馈和有界异步队列；受理时捕获凭据版本，阻止改密前排队任务创建新版本码。SMTP 在数据库事务外调用，诊断日志仅包含随机请求 ID 和异常类型。
- 访问/刷新 JWT 携带凭据版本，过滤链和刷新服务拒绝旧版本及无版本令牌。登录只更新登录时间，不用旧用户快照覆盖其他资料。
- “我的 → 账号安全”和登录页“忘记密码”；确认密码校验、重复提交保护、失败保留表单、验证码倒计时、改密成功清理当前会话并回登录；账号切换后旧响应不会退出新账号。
- 一次性增量迁移、禁用且留空的邮件环境变量示例及 `deploy/account-recovery.md` 运行说明。

## 已执行的验证

| 检查 | 结果 |
| --- | --- |
| Java 21 `mvn.cmd test` | **153 测试，0 失败、0 错误、0 跳过** |
| `AccountRecoveryTest` | 18 测试；真实服务、MyBatis SQL、Spring 事务和独立 H2 MySQL 模式库，执行实际迁移 |
| `AccountRecoveryMySqlIT` | **26 测试全部通过**；独立 MySQL 8.0.43、原始 utf8mb4_unicode_ci 用户表及实际增量迁移，18 个共用边界用例加 8 个 MySQL 专项用例 |
| `verify-account-recovery-mysql.ps1` | Windows PowerShell **5.1.26100.9444** 完整执行成功，归档本次 JUnit 报告，临时库/父子进程/数据目录全部清理 |
| `verify-schema.ps1` | 初始化加增量迁移共 2 个 SQL 文件、24 个 mapper 表检查通过 |
| `AccountSecurityApiTest` | 3 测试；真实 MVC/安全过滤链、公开/保护路由、snake_case 与 DTO 校验 |
| JWT、刷新与验证码边界 | 旧/无版本拒绝、当前版本签发、HMAC 请求绑定、普通用户 JSON 不泄漏安全字段 |
| `node --test scripts/account-recovery.test.mjs scripts/auth-session.test.mjs` | **18 测试全部通过** |
| `production-hardening.test.mjs` / `feature-contract.test.mjs` / `contact-ui.test.mjs` | 全部通过 |
| `vite build` | 生产构建通过；保留现有 Element Plus 大分块警告 |
| `account-recovery-browser.mjs` | 生产产物的两个页面 × 三主题 × 桌面/手机，共 **12 组合**；无横向溢出、无页面脚本错误；确认密码、找回提交及改密后 token 清理通过 |
| `verify-account-recovery-mysql.ps1 -BrowserE2E` | **16 检查全部通过**；真实前端生产产物、Spring Boot、MySQL 8.0.43、JavaMail SMTP 和独立浏览器会话，未使用 API 夹具 |
| `account-recovery-smtp.test.mjs` | **3 测试全部通过**；UTF-8 MIME、分片 SMTP DATA 和拒绝非测试收件人 |
| 聚焦只读审查 | 发现待处理找回任务的改密竞态，测试复现后修复；最终完整回归通过 |
| `git diff --check` | 无空白错误 |

12 组合的布局验证拦截后端请求为测试夹具，截图在 `frontend/output/playwright/account-recovery/`；新增完整链路验收使用真实 HTTP、SQL 和本地 SMTP，未拦截请求。日志在 backend 的 `account-recovery-*.log`、`target/` 和 frontend 的 `output/`，由 Git 忽略。Windows 沙箱对 Maven 新缓存目录、Vite realpath 和回环连接的限制通过经自动审核的测试命令解除；未更改执行策略、系统 PATH、运行时或真实配置。

## 验证界限与下一步

未配置真实 SMTP 凭据、未向外部邮箱发送邮件、未连接或迁移现有业务 MySQL 数据库、未部署。真实 MySQL 并发用例使用内存邮件替身；完整浏览器验收使用生产 JavaMail 发送器和本地 SMTP 接收器。两个实际浏览器会话跨服务端改密失效与新密码登录已验证。外部邮件服务的认证/TLS、真实邮箱收件和服务商投递行为仍须按 `deploy/account-recovery.md` 在后续授权配置后验收。

## MySQL 验收补充

2026-10-10 推进独立 MySQL 验收，完整初始化后执行一次性增量迁移，确认 **7 个原有用户、23 个原字段逐列保留**，新增找回邮箱/验证时间为空、凭据版本为 0。用例确认表引擎、真实唯一邮箱约束及验证码外键，不用 H2 的兼容模式代替 MySQL 锁语义。

真实库先复现三个并发问题，再修复并回归：`INSERT IGNORE` 后的共享锁升级；空事件范围的间隙锁导致独立账号插入死锁；发送后激活先锁验证码、改密先锁用户的反序死锁。最后一例还核对实际锁等待和 InnoDB `lock_deadlocks` 计数，避免被发送异常处理吞掉而误判通过。修复为排他 upsert、仅账号安全事务 READ COMMITTED、激活先用户再验证码的统一锁序；保留显式锁、条件更新及同事务消费。

专项用例使用独立数据源、MyBatis session factory 和账号安全服务实例共享同一测试库，覆盖既有 IP 范围竞争、两个不同账号首次发送、并发 IP 恰好 20 次预算且拒绝不留下半笔事件、大小写/重音账号别名、竞争绑定同一邮箱后的消费回滚，以及激活/改密竞争。其余 18 个共用用例在真实 MySQL 上复验过期、错误次数、并发一次消费、原密码、旧版本及队列延迟等边界。数据库默认隔离级别保持 REPEATABLE READ。

最终脚本验收于 15:02（Asia/Shanghai）通过，证据在 `backend/target/coursehub-recovery-mysql-f8e702c0d3144924a931459bd68c6858/`，包含逐字段迁移比较、JUnit XML 和 MySQL 启停日志。后端全量回归于 14:48 通过，153 测试、0 失败/错误/跳过。本轮修复了验收脚本的报告路径、日志共享读取和 Windows 进程退出码兼容问题；没有修改执行策略、系统 PATH、全局运行时或 ACL。PowerShell 5.1 验收通过读取 UTF-8 脚本到内存执行，以沿用机器已有的文件执行策略。所有本轮创建的临时数据目录均已删除，日志保留。

## 复验命令

后端目录运行 `mvn.cmd test`（项目要求 JDK 21）。前端依赖就绪后运行 `npm.cmd run test:auth`、`npm.cmd run test:recovery`、`npm.cmd run test:contracts`、`npm.cmd run test:contact`、`npm.cmd run build`、`npm.cmd run test:recovery:browser`。浏览器测试使用已有 Playwright Chromium，临时本机预览和浏览器会在完成后关闭。

项目根目录运行 `scripts/verify-account-recovery-mysql.ps1`，自动创建、迁移、测试及清理。MySQL 专项类名以 `IT` 结尾，默认 `mvn.cmd test` 不连接真实库；直接指定 `-Dtest=AccountRecoveryMySqlIT` 缺少受保护的临时库参数时明确失败，不静默跳过。`scripts/verify-schema.ps1` 同时检查初始化与增量迁移链。

完整浏览器验收运行 `scripts/verify-account-recovery-mysql.ps1 -BrowserE2E`；默认并发 IT 与浏览器分支分别运行，避免无必要重复。SMTP 接收器聚焦测试在前端目录运行 `node --test scripts/account-recovery-smtp.test.mjs`。PowerShell 文件执行受本机策略限制时，可像本次一样读取 UTF-8 源码到内存执行，不改变执行策略；父脚本通过 `$PSScriptRoot` 定位项目，内存调用须提供等效项目根目录。

## 本地完整链路验收补充

2026-10-10 **18:55（Asia/Shanghai）**，Windows PowerShell 5.1.26100.9444 完整运行浏览器分支成功。证据为 `backend/target/coursehub-recovery-mysql-31fd8b28dda44ca99c7965f19a8745e5/`，包括 `e2e-summary.json`、`e2e-processes.log`、构建/应用日志和逐字段迁移比较；浏览器原报告为 `frontend/output/playwright/account-recovery-e2e/3e77d57186ea4e5f920d485fa4e3f53b/summary.json`。16 项检查全部通过，前端生产构建成功，保留现有打包警告；SMTP 接收器 3 项测试通过。

本次运行实际邮件发送器、MIME 编码、SMTP 协议、本机反向代理、完整 Spring Boot 安全过滤链、MyBatis/事务以及临时 MySQL。测试启动器先验证严格本机随机库 URL 和 nonce，再插入独立测试账号；运行 classpath 仅含无注解启动器与生产类，避免其他测试配置污染组件扫描。JWT/HMAC 密钥在内存生成，邮件地址强制为本机接收器，只允许 `example.test` 收件人；验证码与令牌未写入验收报告。

检查包括两个独立浏览器账号密码登录、找回邮箱绑定（错误验证码后表单保留、正确验证码后状态回读）、公开申请和密码重置、旧密码拒绝、新密码登录、已登录改密后当前会话退出。密码重置和已登录改密两种触发都逐一确认两会话的旧 access 返回 HTTP 401、旧 refresh 返回 HTTP 200/业务码 401 且不签发新令牌；另一浏览器受保护请求失败后，真实刷新同样被拒绝，清理所有本地登录态并回登录页。最后使用最新密码登录成功，页面脚本错误和远端请求均为 0，本地接收器恰好收到两封验证邮件。

联调修正了测试登录按钮定位、待响应 promise 的错误收尾及代理 Host/Origin 不一致的问题。代理保留浏览器 Host 以模拟同源反代，没有放宽生产 CORS 或改动认证业务代码。每次运行都创建全新数据库，最终脚本和额外只读检查确认捕获的 Java/Node/MySQL 进程已结束、临时 MySQL 数据目录已删除；测试日志保留，未配置真实 SMTP 或部署。

## 提交前复核

2026-10-11 按用户授权整理本地 Git 提交。核对 54 个代码、迁移、测试、脚本及说明文件；SMTP/HMAC 凭据示例为空、邮件验证默认关闭，常见凭据签名检查未发现问题。确认 `target/`、`dist/`、浏览器证据和所有测试日志均被 Git 忽略。

本次重新运行后端全量测试 **153 项通过，0 失败/错误/跳过**，前端账号安全/会话 **18 项通过**，生产约束、功能契约和联系隐私回归通过；生产构建及 24 个 mapper 表/2 个 SQL 文件的 schema 检查通过。后端首次运行被沙箱阻断 JDK 测试代理管道，停止后通过自动权限审核重跑成功，未修改系统设置或测试代码。日志为 `backend/account-recovery-precommit-tests-verified.log` 和 `frontend/output/account-recovery-precommit-build.log`，均不进入提交；未重复运行已通过的临时 MySQL/浏览器验收，未配置真实 SMTP、迁移业务库或部署。
