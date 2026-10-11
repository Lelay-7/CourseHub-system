# 不高山选课通

一个面向学生、教师和管理员的校园课程与学习资源平台。前端使用 Vue 3、Vite、Pinia 和 Element Plus，后端使用 Spring Boot 3、MyBatis-Plus、MySQL 与 Redis。

## 技术栈

- 前端：Vue 3、Vite、Element Plus、Pinia、Axios
- 后端：Spring Boot 3、Spring Security、MyBatis-Plus、Maven
- 数据：MySQL、Redis

## 功能模块

- 学生：课程检索与选课、课程评价、资料上传下载、收藏、积分商城、广场互动、意见反馈
- 教师：课程与联系人信息查看、学习资源共享
- 管理员：用户、课程、资料审核、商品、订单、广场内容、反馈与举报管理

## 环境要求

- JDK 21
- Maven 3.9+
- Node.js 18+
- npm 9+
- MySQL 8.0+
- Redis 5+

## 初始化数据库

初始化脚本位于 `backend/src/main/resources/db/init.sql`，会重建 `bgss_xtt` 数据库中的业务表并写入演示数据。

在 CMD 或 Git Bash 中执行：

```bash
mysql -uroot -p --default-character-set=utf8mb4 < backend/src/main/resources/db/init.sql
```

也可以先在临时数据库中验证脚本，不影响正式数据库：

```powershell
$env:MYSQL_PWD = 'your-mysql-root-password'
powershell -ExecutionPolicy Bypass -File .\scripts\validate-init.ps1
```

## 本地配置

复制根目录配置模板，填写本机数据库密码和至少 32 字节的随机 JWT 密钥：

```powershell
Copy-Item .env.example .env.local
```

前端配置模板在 `frontend/.env.example`。默认开发配置通过 Vite 代理访问 `http://localhost:8080`，通常无需修改。

## 启动

确保 MySQL 与 Redis 已启动，并设置好 `JAVA_HOME`。Windows 下可从仓库根目录启动：

```bat
start.bat
```

脚本会读取 `.env.local`、构建后端、安装缺失的前端依赖，然后启动：

- 前端：http://localhost:3000
- 后端：http://localhost:8080

停止服务：

```bat
stop.bat
```

也可以分别启动：

```powershell
cd backend
$env:DB_USERNAME = 'your-mysql-user'
$env:DB_PASSWORD = 'your-mysql-password'
$env:JWT_SECRET = 'replace-with-at-least-32-random-bytes'
mvn spring-boot:run
```

```powershell
cd frontend
npm install
npm run dev
```

首次安装可使用 `npm install`；需要严格按锁文件做干净安装和交付验证时使用 `npm ci`。

## 测试账号

初始化脚本中的账号密码均为 `123456`：

| 角色 | 用户名 |
| --- | --- |
| 管理员 | `admin` |
| 教师 | `chenming` |
| 学生 | `2024051224` |

## 测试与构建

账号安全与密码找回已实现；运行前须完成独立增量迁移。邮件验证默认关闭，配置与后续真实收件验收见 [账号找回运行说明](deploy/account-recovery.md)，本次自动验证见 [验证记录](docs/account-recovery-verification.md)。

账号找回的真实 MySQL 迁移和并发验收可在项目根目录运行 `scripts/verify-account-recovery-mysql.ps1`；脚本使用全新临时实例与随机测试库，完成后清理，不配置 SMTP 或连接业务库。

加上 `-BrowserE2E` 可运行前端生产产物、真实后端、临时 MySQL 和本地 SMTP 接收器的完整浏览器验收，检查绑定、找回、改密和两个旧会话的凭据失效；需要已有前端依赖与 Playwright Chromium，不使用真实邮箱凭据。

后端：

```powershell
cd backend
mvn test
mvn clean package
```

前端：

```powershell
cd frontend
npm ci
npm run test:contracts
npm.cmd run test:auth
npm.cmd run test:recovery
npm.cmd run test:contact
npm run build
npm.cmd run test:recovery:browser
```

## 常见问题

- 登录失败：确认 `.env.local` 的数据库账号密码正确，并已执行初始化脚本。
- 后端启动失败：确认 `JAVA_HOME` 指向 JDK 21，而不是 JRE。
- Redis 连接失败：先启动 Redis，或在 `.env.local` 中设置 `REDIS_HOST`、`REDIS_PORT` 和 `REDIS_PASSWORD`。
- 上传或下载失败：确认 `FILE_UPLOAD_PATH` 可写。上传目录是运行时数据，不应提交到 Git。
- 前端请求失败：确认后端监听 `8080` 端口，且 `VITE_API_BASE_URL` 未指向错误地址。

## 已知非阻塞问题

### npm audit 依赖警告

`npm audit` 报告部分中危/高危依赖，主要集中在开发依赖（Vite、Playwright 等）。这些漏洞不会影响生产构建产物的安全性，暂不执行破坏性升级（`npm audit fix --force` 可能导致兼容性问题）。在后续大版本升级时统一处理。

### 后端测试与真实 MySQL 验收

默认 `mvn.cmd test` 使用单元测试及独立 H2 MySQL 模式夹具，不依赖运行中的业务数据库。账号安全的 `AccountRecoveryMySqlIT` 通过上面的隔离验收脚本显式运行，要求现有 MySQL 8.0 安装和 JDK 21/Maven；脚本验证实际 MySQL 的迁移、锁、限流和并发回滚。

### 暂不包含云端部署配置

当前项目定位为本地开发与演示环境，尚未包含 Docker 容器化、Kubernetes、CI/CD 流水线等云端部署相关配置。`frontend/.env.production` 中的 `VITE_API_BASE_URL` 默认使用 `/api` 相对路径，适合通过反向代理部署；如需直接部署到独立域名，请按需修改。
