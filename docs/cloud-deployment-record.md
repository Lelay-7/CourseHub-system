# 不高山选课通 — 云端部署完成记录

## 一、部署基本信息

| 项目 | 说明 |
|------|------|
| 项目名称 | 不高山选课通 |
| 云端访问地址 | `http://8.137.197.5/` |
| 部署分支 | `dev1.0` |
| 当前 commit | `cd5cd4d fix: normalize api prefix and mall category filter` |
| 部署服务器 | 阿里云轻量应用服务器 |
| 系统版本 | Ubuntu 22.04 |
| 服务器规格 | 2 vCPU / 4 GiB / 50 GiB |
| 公网 IP | 8.137.197.5 |

## 二、服务器目录结构

| 路径 | 用途 |
|------|------|
| `/opt/bugaoshan-src/cuit-help` | 源码目录（Git 仓库） |
| `/opt/bugaoshan/frontend/dist` | 前端部署目录（Nginx 静态托管） |
| `/opt/bugaoshan/backend/bugaoshan-backend.jar` | 后端 Spring Boot JAR |
| `/opt/bugaoshan/backend/uploads` | 上传文件目录（含 seed/ 演示文件） |
| `/opt/bugaoshan/deploy` | 部署脚本目录 |
| `/opt/bugaoshan/logs` | 日志目录 |
| `/opt/bugaoshan/logs/app.log` | 后端运行日志 |

## 三、服务配置

| 服务 | 配置项 | 说明 |
|------|--------|------|
| 后端 systemd | `bugaoshan-backend.service` | 自启服务，JAR 启动 |
| Nginx 配置 | `/etc/nginx/sites-available/bugaoshan` | 反向代理 + 静态托管 |
| Nginx 启用 | `/etc/nginx/sites-enabled/bugaoshan` | 软链接到 sites-available |
| MySQL 数据库 | `bgss_xtt` | UTF-8mb4，种子数据已初始化 |
| MySQL 用户 | `bgss_user` | 仅授权 bgss_xtt 数据库 |
| Redis | `127.0.0.1:6379` | JWT Token 缓存 |
| 后端端口 | `8080` | 仅本机监听 |
| 前端端口 | `80` | Nginx 公网暴露 |

## 四、已完成的部署事项

- [x] JDK 21 安装完成
- [x] Maven 安装完成
- [x] Node.js 20 / npm 安装完成
- [x] MySQL 8.0 安装并初始化数据库完成
- [x] Redis 安装并启动完成
- [x] Nginx 安装并配置完成
- [x] 前端 `npm run build` 构建并部署到 `/opt/bugaoshan/frontend/dist`
- [x] 后端 `mvn clean package` 构建并部署 JAR
- [x] 后端 systemd 自启配置完成
- [x] 服务器重启后服务自动恢复验证通过
- [x] Nginx gzip 和静态资源缓存已配置
- [x] `/api/auth/login` 接口正常
- [x] 积分商城分类筛选修复并验证正常
- [x] `/api/api` 重复前缀问题已修复

## 五、常用运维命令

### 后端服务

```bash
# 查看状态
systemctl is-active bugaoshan-backend
sudo systemctl status bugaoshan-backend --no-pager

# 重启
sudo systemctl restart bugaoshan-backend

# 查看 8080 端口
ss -lntp | grep ':8080'
```

### 日志

```bash
# 实时日志
tail -f /opt/bugaoshan/logs/app.log

# 最近 100 行
tail -n 100 /opt/bugaoshan/logs/app.log
```

### Nginx

```bash
# 检查配置
sudo nginx -t

# 重载配置
sudo systemctl reload nginx

# 查看状态
systemctl is-active nginx
```

### MySQL / Redis

```bash
# MySQL
sudo systemctl status mysql

# Redis
sudo systemctl status redis-server
```

### 更新前端

```bash
cd /opt/bugaoshan-src/cuit-help
git checkout dev1.0
git pull origin dev1.0
cd frontend
npm install
npm run build
rm -rf /opt/bugaoshan/frontend/dist
cp -r dist /opt/bugaoshan/frontend/dist
sudo nginx -t
sudo systemctl reload nginx
```

### 更新后端

```bash
cd /opt/bugaoshan-src/cuit-help
git checkout dev1.0
git pull origin dev1.0
cd backend
mvn clean package -DskipTests
cp target/bugaoshan-backend-*.jar /opt/bugaoshan/backend/bugaoshan-backend.jar
sudo systemctl restart bugaoshan-backend
```

## 六、线上验证结果

### 登录

| 角色 | 账号 | 结果 |
|------|------|:--:|
| 管理员 | `admin / 123456` | ✅ |
| 教师 | `chenming / 123456` | ✅ |
| 学生 | `2024051224 / 123456` | ✅ |

### 前台页面

| 页面 | 结果 |
|------|:--:|
| 首页 | ✅ 可访问 |
| 课程列表 | ✅ |
| 课程详情 | ✅ |
| 我的课程 | ✅ |
| 学习资料 | ✅ |
| 资料下载 | ✅ |
| 广场列表 | ✅ |
| 积分商城 | ✅ |
| 个人中心 | ✅ |

### 积分商城分类筛选

| 分类 | 预期显示 | 结果 |
|------|------|:--:|
| 全部 | 4 件商品 | ✅ |
| 实物 | 校园咖啡券、活动限定笔记本 | ✅ |
| 虚拟 | 云盘扩容5GB | ✅ |
| 服务 | 图书馆研修间预约券 | ✅ |

### 管理员后台

| 页面 | 结果 |
|------|:--:|
| Dashboard | ✅ |
| 用户管理 | ✅ |
| 资料审核 | ✅ |
| 课程管理 | ✅ |
| 商品管理 | ✅ |

## 七、已知说明

- 当前使用 **HTTP** 访问，暂未配置 HTTPS。
- 当前使用 **公网 IP** 访问，暂未绑定域名。
- Nginx 中保留了 `/api/api/` 兼容规则，但前端代码层已修复 `/api/api` 重复前缀问题。
- Element Plus 控制台存在 `label/value` 弃用警告，不影响当前功能。
- Vite 构建存在 chunk size 警告（Element Plus 约 880 KB），不影响当前运行，可后续按需加载优化。
- 不建议执行 `npm audit fix --force`，避免依赖版本破坏性升级。
