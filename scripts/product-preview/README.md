# 本地产品体验环境

从工作区根目录运行 `scripts/start-product-preview.ps1`，使用 `-Status` 查看状态；`scripts/stop-product-preview.ps1` 停止本环境并保留全部数据。再次启动会沿用同一数据库、上传文件、账号和收件箱，不重复灌入种子。

依赖已安装的 MySQL 8.0、Java 21 和 Codex 自带 Python、Node。先构建 `backend/target/bugaoshan-backend-0.0.1-SNAPSHOT.jar` 与 `frontend/dist`。前端构建必须使用同源 API（`VITE_API_BASE_URL` 为空或 `/api`），此环境自行转发 `/api`。

默认入口：

- 网页：<http://localhost:3001>
- 测试收件箱：<http://localhost:3002>
- API：`127.0.0.1:8081`；MySQL：`127.0.0.1:33363`；SMTP：`127.0.0.1:2526`

所有服务只监听本机。SMTP 只接收 `example.test` 地址，不连接外部邮件服务。收件箱可查看绑定或找回密码验证码；绑定新邮箱时可输入任意未占用的 `@example.test` 测试地址。

四个体验账号为 `product_student_a`、`product_student_b`、`product_teacher`、`product_admin`，初始密码均为 `Product-local-2026!`。均已绑定各自的 `用户名@example.test` 找回邮箱。改密码、改邮箱和其他操作会在重启后保留。

种子包括 61 门课程、33 个商品、42 份可打开的真实 PDF 资料及其审核历史、41 个用户、300 条积分记录、待处理与已处理的举报/反馈/订单、好友申请和 36 条私聊记录。学生甲拥有驳回资料 `1000`、重提待审的第 2 版资料 `1001`；学生乙拥有已通过的收费资料 `1002`。兑换品及交付说明均为本地演示，不实际发货或发券。

配置、凭据、日志、上传文件和状态在 Git 忽略目录 `backend/target/product-preview/`。MySQL 数据保存在带随机运行标识和所有权标记的 `C:\Windows\Temp\coursehub-product-preview-<run_id>`。脚本会校验数据目录、端口、PID 文件和数据库 nonce；只停止捕获了进程句柄及启动时间的本次服务，不停止系统 MySQL 或旧体验服务，也不执行数据清理。

后端启动时复制构建产物到该环境的 `backend-runtime.jar`，避免 Windows 文件锁或后续构建改写运行中的 JAR。后端代码更新后先停止本环境、构建、再启动；前端静态页面来自 `frontend/dist`，构建完成后刷新网页即可。

已有数据库迁移依次使用 `2026-10-15-material-review-closure.sql` 和 `2026-10-15-interaction-closure.sql`，不要对已有数据重新运行 `init.sql`。迁移只为旧资料建立初始版本和已有奖励标记，保留状态、积分及驳回原因；旧举报/订单没有可靠的处理人、时间或交付说明时保持空值。当前与历史版本文件继续保留，数据库失败只清理本次未引用的新文件。回退需同时恢复迁移前数据库和上传文件备份，不可只删除新表或重置积分。本轮迁移仅应用到专属测试数据库。

`GET /__preview` 返回本环境的 `run_id/database/nonce`，供自动体验脚本在写入前校验归属。`manage.py prepare-material-check` 可在同一独立 MySQL 实例生成另外的受保护资料事务测试库，连接参数仅写入忽略目录的 `material-check-env.json`。该库和产品体验库互不相同。

若某默认端口被占用，启动会失败且不停止占用者。可在尚未启动的忽略目录配置中调整 `web_port/api_port/mysql_port/mailbox_port/smtp_port`，再启动。停止不会删除任何目录；如需要清理，先确认状态为 stopped，再单独核对带随机运行标识的数据目录。
