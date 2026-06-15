# 不高山选课通 — 云端部署清单

## 1. 部署目标

| 项目 | 版本/说明 |
|------|-----------|
| OS | Ubuntu 22.04 LTS |
| Web Server | Nginx |
| Java | JDK 21 |
| Build | Maven 3.9+ |
| Frontend | Node.js 18+, npm 9+ |
| Database | MySQL 8.0 |
| Cache | Redis 7 |

## 2. 服务器目录结构

```
/opt/bugaoshan/
├── backend/
│   ├── bugaoshan-backend.jar
│   ├── app.pid
│   └── uploads/
│       └── seed/
├── frontend/
│   └── dist/
├── logs/
│   └── app.log
└── deploy/
    ├── env-template
    ├── start-backend.sh
    ├── stop-backend.sh
    ├── restart-backend.sh
    └── nginx-bugaoshan.conf
```

## 3. 安装系统依赖

```bash
# 更新系统
sudo apt update && sudo apt upgrade -y

# JDK 21
sudo apt install openjdk-21-jdk -y

# Maven
sudo apt install maven -y

# Node.js 18+
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt install nodejs -y

# MySQL 8.0
sudo apt install mysql-server-8.0 -y
sudo systemctl enable mysql
sudo systemctl start mysql

# Redis
sudo apt install redis-server -y
sudo systemctl enable redis-server
sudo systemctl start redis-server

# Nginx
sudo apt install nginx -y
sudo systemctl enable nginx
sudo systemctl start nginx
```

## 4. 拉取代码

```bash
cd /opt
sudo mkdir bugaoshan
sudo chown $USER:$USER bugaoshan
cd bugaoshan

git clone https://gitee.com/wen-zheng-nnn/cuit-help.git repo
cd repo
git checkout dev1.0
```

## 5. 创建数据库用户并初始化

```sql
-- 以 root 登录 MySQL
sudo mysql

-- 创建 bgss_user（替换密码）
CREATE USER 'bgss_user'@'localhost' IDENTIFIED BY 'CHANGE_ME';
GRANT ALL PRIVILEGES ON bgss_xtt.* TO 'bgss_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

```bash
# 执行初始化脚本
mysql -u root -p < backend/src/main/resources/db/init.sql
```

> 初始化后验证：
> ```sql
> USE bgss_xtt;
> SELECT COUNT(*) FROM user;   -- 应返回 7
> SELECT COUNT(*) FROM course;   -- 应返回 7
> SELECT COUNT(*) FROM material; -- 应返回 6
> ```

## 6. 构建后端

```bash
cd /opt/bugaoshan/repo/backend
mvn clean package -DskipTests
```

JAR 输出路径：`backend/target/bugaoshan-backend-0.0.1-SNAPSHOT.jar`

## 7. 构建前端

```bash
cd /opt/bugaoshan/repo/frontend
npm ci
npm run build
```

输出目录：`frontend/dist/`

## 8. 复制部署产物

```bash
BASE=/opt/bugaoshan
REPO=/opt/bugaoshan/repo

# 后端 JAR
cp $REPO/backend/target/bugaoshan-backend-*.jar $BASE/backend/bugaoshan-backend.jar

# 前端 dist
cp -r $REPO/frontend/dist $BASE/frontend/dist

# 种子文件
mkdir -p $BASE/backend/uploads
cp -r $REPO/uploads/seed $BASE/backend/uploads/seed

# 部署脚本
mkdir -p $BASE/deploy
cp $REPO/deploy/* $BASE/deploy/
chmod +x $BASE/deploy/*.sh
```

## 9. 配置环境变量

```bash
# 复制模板
cp /opt/bugaoshan/deploy/env-template /opt/bugaoshan/deploy/set-env.sh

# 编辑真实值
nano /opt/bugaoshan/deploy/set-env.sh
```

必须替换的占位值：
- `DB_PASSWORD` — MySQL bgss_user 的密码
- `JWT_SECRET` — 生成：`openssl rand -base64 32`

加载环境变量：
```bash
source /opt/bugaoshan/deploy/set-env.sh
```

## 10. 启动后端

```bash
source /opt/bugaoshan/deploy/set-env.sh
bash /opt/bugaoshan/deploy/start-backend.sh

# 检查日志
tail -f /opt/bugaoshan/logs/app.log
```

验证后端：
```bash
curl http://127.0.0.1:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
# 应返回 code=200 和 accessToken
```

## 11. 配置 Nginx

```bash
sudo cp /opt/bugaoshan/deploy/nginx-bugaoshan.conf /etc/nginx/sites-available/bugaoshan
sudo ln -s /etc/nginx/sites-available/bugaoshan /etc/nginx/sites-enabled/bugaoshan

# 移除默认站点（可选）
sudo rm -f /etc/nginx/sites-enabled/default

sudo nginx -t
sudo systemctl reload nginx
```

## 12. 防火墙 / 云安全组

云服务商控制台需放行：

| 端口 | 协议 | 用途 | 建议 |
|:--:|------|------|------|
| 80 | TCP | HTTP | 公网开放 |
| 22 | TCP | SSH | 仅允许你的 IP |
| 8080 | TCP | 后端 | **不建议公网开放** |

```bash
# UFW（如已启用）
sudo ufw allow 80/tcp
sudo ufw allow 22/tcp
```

## 13. 验证部署

浏览器访问：

```
http://<服务器公网IP>/
```

测试账号（密码均为 `123456`）：

| 角色 | 账号 |
|------|------|
| 管理员 | `admin` |
| 教师 | `chenming` |
| 学生 | `2024051224` |
| 备用学生 | `2024051208`（积分 920） |

验证清单：

- [ ] 访问根路径 `/` 进入登录页
- [ ] 学生登录正常
- [ ] 管理员登录正常
- [ ] 课程列表正常展示
- [ ] 课程搜索 / 筛选正常
- [ ] 资料列表正常
- [ ] 资料下载不 404
- [ ] 广场列表正常
- [ ] 商城商品正常
- [ ] 管理员后台正常
- [ ] 页面刷新不 404

## 14. 常见问题

### Nginx 返回 404
```bash
# 检查 dist 目录是否存在
ls /opt/bugaoshan/frontend/dist/index.html

# 检查 Nginx 配置
sudo nginx -t
cat /etc/nginx/sites-enabled/bugaoshan
```

### /api 代理失败（后端连接拒绝）
```bash
# 确认后端进程
cat /opt/bugaoshan/backend/app.pid
ps aux | grep java

# 查看后端日志
tail -50 /opt/bugaoshan/logs/app.log
```

### 后端 8080 未启动
```bash
# 检查 JWT_SECRET 和 DB_PASSWORD 是否已设置
echo $JWT_SECRET
echo $DB_PASSWORD

# 手动启动排查
source /opt/bugaoshan/deploy/set-env.sh
java -jar /opt/bugaoshan/backend/bugaoshan-backend.jar
```

### MySQL 连接失败
```bash
# 测试连接
mysql -u bgss_user -p -h 127.0.0.1 -e "SELECT 1;"

# 检查 MySQL 服务
sudo systemctl status mysql
```

### Redis 未启动
```bash
sudo systemctl status redis-server
sudo systemctl start redis-server
```

### 资料下载 404
```bash
# 确认种子文件存在
ls /opt/bugaoshan/backend/uploads/seed/

# 确认 FILE_UPLOAD_PATH 指向正确
echo $FILE_UPLOAD_PATH
```

### 前端刷新页面 404
确认 Nginx 配置中有 `try_files $uri $uri/ /index.html;`

### 前端 API 请求 401/403
确认账号密码正确，或重新初始化数据库。
