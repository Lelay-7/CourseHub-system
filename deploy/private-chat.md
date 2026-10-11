# 私聊运行与迁移说明

首版提供好友间一对一文字消息，使用现有 MySQL 和认证链；邮件配置与私聊无关。此任务只在独立临时数据库验收，没有迁移业务数据库、配置真实 SMTP 或部署。

## 更新顺序

1. 在维护窗口备份完整数据库，至少保留 `user`、`friend` 的结构和数据，以及可验证的恢复记录。暂停好友关系写入，避免旧代码在预检查和添加约束之间产生新重复关系。
2. 在备份副本执行 `backend/src/main/resources/db/migrations/2026-10-10-account-recovery.sql`（如尚未执行），再执行 `2026-10-11-private-chat.sql`。用 MySQL 8 客户端运行完整文件，保留 `DELIMITER` 指令及重复清理计数。
3. 确认自关系、物理不存在的用户或非法状态预检查通过；合法重复优先保留已接受关系，否则保留最小 `friend_id`。禁用或软删除用户的合法关系保留，不自动创建会话。
4. 核对四张 `chat_*` 表使用 InnoDB、utf8mb4，以及好友规范用户对、会话用户对、会话序号和发送人+客户端 UUID 唯一索引。随后再更新后端、前端并执行实际环境验收。

该顺序是后续运维说明，本次没有执行。初始化脚本含删表与示例数据，不能用于更新已有业务库。

## 非法旧数据处理

预检查失败会在修改好友数据或业务表结构前停止。检查下列只读查询结果，再依据实际关系人工修复；不要把未知的非法状态批量变成好友。

```sql
SELECT f.* FROM friend f
LEFT JOIN user a ON a.user_id=f.user_id
LEFT JOIN user b ON b.user_id=f.target_id
WHERE f.user_id=f.target_id OR a.user_id IS NULL OR b.user_id IS NULL
   OR f.status IS NULL OR f.status NOT IN (0,1);
```

## 配置与行为

`app.chat.send-per-minute` 默认 30，`send-per-hour` 默认 300；环境变量分别为 `CHAT_SEND_PER_MINUTE`、`CHAT_SEND_PER_HOUR`。稳定分钟/小时窗口由数据库共享，新消息占额度，幂等重试不重复占用，失败事务回滚。`CHAT_RATE_RETENTION_HOURS` 默认 48、允许 1–48；`CHAT_CLEANUP_BATCH_SIZE` 默认 500、允许 1–1000，后台每十分钟按批次清理。

写事务按用户 ID 升序锁用户，再锁好友关系、会话和限额，锁定当前账号后复核认证捕获的凭据版本。消息、会话和限额时间以 UTC 参数写入 `DATETIME(3)`，无需修改服务器或系统时区。前端按本地时间显示，消息 ID 和序号均为字符串。

解除好友或对方账号不可用时保留成员历史并禁止发送；管理员不具有额外阅读权限。消息和草稿仅保存在当前登录会话内存中，退出、换账号和凭据失效立即清理。全局轮询由 App 唯一拥有；可见页面 15 秒读取列表/未读，详情 5 秒补拉，失败按 10/20/40/60 秒退避。

## 回退与扩展

MySQL DDL 隐式提交，整个迁移不能事务回滚。失败时保持维护窗口，依据备份恢复被清理的好友数据和原结构；先在副本验证恢复结果。已有私聊消息需单独备份，不能通过删除 `chat_*` 表回退而丢弃历史。旧好友代码不符合规范用户对约束，回退应用版本时应恢复相匹配的好友表结构。

成员表、会话/消息类型、前端 API 与渲染组件分别留有扩展边界。`ChatMessageCreated` 只在提交后通知本进程监听者，事件没有正文；当前不接外部发送器。后续群聊、附件、消息操作、SSE/WebSocket 和可靠跨实例推送仍需独立设计权限、迁移与 outbox，不能直接把内存事件视为可靠队列。

## 本地验收

设置进程级 `JAVA_HOME` 为 Java 21，使用已安装 Maven、Node 和 MySQL；不安装或升级依赖。运行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-private-chat-mysql.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-private-chat-mysql.ps1 -BrowserE2E
```

脚本只创建新的 `coursehub_chat_check_<32hex>` 数据库与本地实例。每次 SQL 操作检查实例数据路径、端口、版本、PID 文件和所有权标记；清理核对进程路径、捕获句柄与启动时间。Java fixture 再检查数据库名称和 `private_chat_test_guard` nonce。没有连接已有服务器模式。浏览器验收使用生产 Spring Boot、真实认证和 SQL，全部进程仅绑定 127.0.0.1，不启动 SMTP；证据保留于 `backend/target` 与 `frontend/output/playwright`。
