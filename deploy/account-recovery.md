# 账号安全与密码找回运行说明

本次交付只包含代码、增量迁移与自动验证。未配置真实 SMTP、未变更已有数据库、未部署。

## 数据迁移

备份后，先在测试 MySQL 库对现有 `user` 表运行 `backend/src/main/resources/db/migrations/2026-10-10-account-recovery.sql`。这是一次性增量迁移，不能反复执行，也不能为此重跑 `db/init.sql`。全新库先初始化，再运行增量迁移。迁移不从联系邮箱复制数据，所有原有用户的找回邮箱为空、凭据版本为 0。上线启用版本检查后，无版本旧令牌要求重新登录。

默认自动测试在独立 H2 MySQL 模式内存库运行真实迁移/SQL/事务。真实 MySQL 验收入口为项目根目录的 `scripts/verify-account-recovery-mysql.ps1`，要求已安装 MySQL 8.0 和 JDK 21/Maven；可用 `-MysqlHome`、`-MavenPath` 指定现有安装路径。脚本新建仅监听本机的临时实例，在随机测试库中导入初始化脚本后执行增量迁移，逐列核对原有用户数据，再运行真实服务与 SQL 的并发用例。它不读取应用数据库连接或 SMTP 配置，结束后清理测试库和实例，证据留在 `backend/target/coursehub-recovery-mysql-<随机标识>/`。安装版 MySQL 的中文路径限制通过独立的系统临时目录处理，不改系统服务或 PATH。回滚应用时保留新增字段/表，不删除绑定资料。

## 环境变量（均未填入真实凭据）

| 名称 | 默认值/用途 |
| --- | --- |
| `RECOVERY_ENABLED` | `false`；真实邮件验证开关 |
| `RECOVERY_HMAC_SECRET` | 空；至少 32 字节随机独立密钥，各实例一致，与 `JWT_SECRET` 分开；轮换后旧验证码失效 |
| `SMTP_HOST` / `SMTP_PORT` | 空 / `587` |
| `SMTP_USERNAME` / `SMTP_PASSWORD` | 空；发件账号/服务商授权码，仅放私密环境配置 |
| `SMTP_FROM` | 空；允许发送的发件地址 |
| `SMTP_STARTTLS` / `SMTP_SSL` | `true` / `false`；按服务商配置，465 隐式 TLS 通常设为 `false` / `true` |

未启用或必要发送配置/验证码密钥缺失，绑定与找回申请会明确提示未配置。已登录改密不依赖邮件配置。服务没有固定邮件验证码、浏览器验证码回显或明文验证码日志。不要开启邮件协议调试、请求体日志或在聊天/Git 中提供授权码。

找回申请快速统一反馈，2 个工作线程、64 个排队任务；队列满、匹配失败、账号限额或 SMTP 失败不会声称投递成功。申请受理时对所有输入进行同样的索引查询，只捕获用户 ID 和凭据版本；队列执行时再核对邮箱、账号状态及该版本。改密前排队的旧申请不能在改密后生成新版本验证码。SMTP 调用有 5 秒连接/读/写超时。绑定申请同步发送但在数据库事务之外进行。进程重启时排队发送任务不会重放；用户按频率规则重新申请，遗留待发码会到期。

IP 使用请求的远端地址，不信任客户端自行填入的转发头。反向代理部署时须在网络层限制后端只接受受信代理，再配置容器的受信代理地址解析；否则所有用户可能共享代理 IP 限额。不要简单信任任意 `X-Forwarded-For`。

滚动一小时限额在数据库中串行检查：同账号/目的 60 秒、5 次/小时；绑定待验证邮箱同样限制；所有申请共享 IP 20 次/小时。未知账号仍计入输入账号/IP 限额，实际匹配再按用户 ID 限流，避免数据库账号排序规则的别名绕过。错误次数在失败返回前提交；正确消费与修改密码/绑定同一事务。

账号安全服务的事务单独使用 READ COMMITTED，保留用户、范围及验证码的显式行锁；数据库全局隔离级别不变。范围锁通过无变化的重复键更新直接取得排他锁，发送完成后的激活也先锁用户再更新验证码。这些选择避免已在 MySQL 默认 REPEATABLE READ 下复现的锁升级、空范围间隙锁及激活/改密反序死锁，依据 [MySQL 锁规则](https://dev.mysql.com/doc/refman/8.0/en/innodb-locks-set.html) 和 [事务隔离规则](https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-isolation-levels.html)。

运维可定期分批清理一天前到期的 `recovery_code` 和一小时前的 `recovery_rate_event`；锁表行不要在活跃服务中删除，否则会破坏共享锁语义。锁表只保存范围摘要，可在停机维护时清理未使用行。

## 本地完整浏览器验收

项目根目录运行 `scripts/verify-account-recovery-mysql.ps1 -BrowserE2E`，沿用临时 MySQL 的创建、迁移和清理保护，改为执行真实应用的浏览器流程。前端依赖及 Playwright Chromium 须已就绪，默认使用 Codex 捆绑 Node，可用 `-NodePath` 指定绝对路径。脚本运行生产构建，不安装依赖。

测试启动器仅在受保护的随机库中创建测试账号，通过独立 classpath 启动真实 Spring Boot 应用；不会载入其他测试配置。它在内存生成 JWT/HMAC 密钥，并强制邮件服务器为本机随机端口，不使用认证或 TLS。Node 接收器仅接受 `example.test` 收件人，解析实际 JavaMail MIME；验证码与令牌只用于测试进程，不写入报告。预览代理保留浏览器 Host，模拟同源反向代理，不放宽生产 CORS 配置。浏览器请求通过真实 HTTP/SQL/SMTP 链路，未拦截为 API 夹具。

脚本覆盖错误验证码后继续提交、绑定状态、公开找回及新密码登录、已登录改密，以及两个浏览器会话的旧访问/刷新凭据拒绝和前端会话清理。现有刷新接口以 HTTP 200 / 业务码 401 表示旧版本拒绝，访问过滤链返回 HTTP 401。只有本次新鲜的成功报告才算通过。报告及进程启停证据保存在 `backend/target/coursehub-recovery-mysql-<随机标识>/`，浏览器报告在 `frontend/output/playwright/account-recovery-e2e/<nonce>/`；结束后关闭本次临时服务并删除 MySQL 数据目录。

## 后续真实投递验收

授权配置后使用测试邮箱和两个浏览器会话验证：绑定、更换、忘记密码、新密码登录；改密后两个旧会话的访问与刷新都失效；错误/过期/目的混用/重用/并发验证码被拒绝；未验证联系邮箱不能找回；未配置、发送失败、队列拥塞的提示真实。SMTP 接受邮件不代表邮件已进入收件箱，真实收件验收尚未执行。

实现接口采用 Spring 的 [JavaMailSender](https://docs.spring.io/spring-framework/reference/integration/email.html)，找回响应与一次性验证码边界参考 [OWASP 找回密码指南](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html)。具体阈值遵循项目设计。
