# 测试增强方案

## 1. 测试目标

保证以下核心链路在每次修改后可快速回归验证：

1. 登录与路由权限不炸
2. 课程功能不炸
3. 资料下载不炸
4. 商城不炸
5. 广场不炸
6. 管理员后台不炸
7. 前后端构建不炸

## 2. 测试范围

| 模块 | 测试方式 | 覆盖内容 |
|------|----------|----------|
| 登录与权限 | API 冒烟 + 手动浏览器 | token、路由守卫、角色拦截 |
| 课程 | API 冒烟 | 列表、搜索、筛选、详情、选课 |
| 资料 | API 冒烟 + 契约测试 | 列表、下载、状态校验 |
| 广场 | API 冒烟 | 列表、发帖、评论、点赞 |
| 商城 | API 冒烟 | 列表、兑换、库存校验 |
| 管理员 | API 冒烟 | Dashboard、用户、资料审核 |
| 构建 | 回归脚本 | npm build + mvn package |

## 3. 测试账号

| 角色 | 账号 | 密码 |
|------|------|------|
| 学生 | `2024051224` | `123456` |
| 教师 | `chenming` | `123456` |
| 管理员 | `admin` | `123456` |
| 备用学生 | `2024051208` | `123456` (积分 920) |

## 4. 测试命令

### 一键回归

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-regression.ps1
```

### API 冒烟测试

```powershell
# 确保后端 8080 已启动
powershell -ExecutionPolicy Bypass -File scripts\api-smoke-test.ps1
```

### 单独执行

```powershell
# 前端
cd frontend
npm run test:contracts
npm run build

# 后端
cd backend
mvn test
mvn clean package
```

## 5. 手动测试清单

### 登录与权限

- [ ] 无 token 访问 `/` 进入 `/login`
- [ ] 无 token 访问 `/home` 进入 `/login`
- [ ] 错误 token 自动清理并回登录页
- [ ] 学生不能访问管理员后台
- [ ] 管理员可以访问后台
- [ ] 退出登录后不能访问受保护页面

### 学生端

- [ ] 登录 — `2024051224 / 123456`
- [ ] 首页正常加载
- [ ] 课程列表正常
- [ ] 课程搜索/筛选/排序正常
- [ ] 课程详情正常
- [ ] 我的课程正常
- [ ] 资料列表正常
- [ ] 资料下载不 404
- [ ] 广场列表正常
- [ ] 商城商品正常
- [ ] 库存不足拦截正常
- [ ] 个人中心正常

### 管理员端

- [ ] 登录 — `admin / 123456`
- [ ] Dashboard 正常
- [ ] 用户管理正常
- [ ] 课程管理正常
- [ ] 资料审核正常
- [ ] 商品管理正常
- [ ] 订单管理正常

## 6. API 冒烟测试清单

| 端点 | 方法 | 预期 |
|------|:--:|:--:|
| `/api/auth/login` | POST | 200 |
| `/api/auth/userinfo` | GET | 200 |
| `/api/course/list` | GET | 200 |
| `/api/course/selected` | GET | 200 |
| `/api/material/list` | GET | 200 |
| `/api/material/1/download` | GET | 200 (file) |
| `/api/material/2/download` | GET | 200 (file) |
| `/api/material/6/download` | GET | 200 (file) |
| `/api/square` | GET | 200 |
| `/api/mall/goods` | GET | 200 |
| `/api/admin/dashboard` | GET | 200 |
| `/api/admin/users` | GET | 200 |
| `/api/admin/materials` | GET | 200 |

## 7. 回归测试流程

```
1. 确认 MySQL + Redis 启动
2. 初始化数据库（如需要）:
   mysql -u root -p < backend/src/main/resources/db/init.sql
3. 启动后端
4. 启动前端
5. 执行 run-regression.ps1
6. 执行 api-smoke-test.ps1
7. 浏览器手动验证关键页面
```

## 8. 测试通过标准

- 前端契约测试全部通过
- 前端生产构建成功
- 后端 80 个单元测试全部通过
- 后端打包成功
- API 冒烟测试全部通过
- 手动测试清单全部打勾

## 9. 已知非阻塞问题

- npm audit 有少量依赖警告，不影响构建和运行
- 后端单元测试需要本地 MySQL + Redis
- API 冒烟测试需要后端已启动

以上问题均不影响当前本地测试、功能演示和答辩展示。
