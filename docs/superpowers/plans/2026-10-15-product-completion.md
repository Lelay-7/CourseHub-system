# 功能闭环、设置页与全站体验整改 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. 默认建议主代理在本会话连续分批执行；若用户另选并行执行，使用 superpowers:subagent-driven-development。Steps use checkbox (`- [x]`) syntax for tracking.

**状态：2026-10-22 第一轮 Task 1–7 开发与验证完成，交付网页体验。** 当前停在 Task 8 用户实际体验节点，Task 9–10 待用户反馈及网页确认。详见[进度记录](../../product-completion-progress-2026-10-16.md)。

| 交付轮次 | 内部任务 | 用户参与节点 |
| --- | --- | --- |
| 第一轮：完整开发与统一验证 | 执行前准备、Task 1–7，一并完成修复、闭环和页面调整 | 完整网页体验版本准备好时交付 |
| 第二轮：网页体验与集中调整 | Task 8–9 | 用户一次体验全站并决定调整/新增，集中处理后复看受影响部分 |
| 第三轮：手机核验与收尾 | Task 10 | 网页确认后由 Codex 执行，模拟与真机分别记录 |

内部任务保留为进度记录，不表示独立交付或需要逐项审批。

**Goal:** 补齐当前功能闭环，重组设置与重点页面，交付可完整体验的网页版本；用户体验并决定后续调整后，由 Codex 完成最终手机端核验。

**Architecture:** 保留 Vue 3 / Element Plus / Pinia 与 Spring Boot / MyBatis / MySQL 架构及三主题。按“正确性→资料与运营闭环→设置→其他页面→网页体验→反馈调整→手机核验”交付独立批次，复用现有隔离测试和身份守卫。各批有聚焦验证，最终提供真实后端支持的完整体验环境。

**Tech Stack:** Vue 3、Vue Router、Pinia、Element Plus、Vite、Node 原生测试、Playwright；Java 21、Spring Boot、MyBatis、JUnit、MySQL 8。

**Spec:** [功能闭环与设置设计](../specs/2026-10-15-product-completion-design.md)；[审视报告 F01–F14](../../product-integrity-and-ui-review-2026-10-15.md)。用户应一并审阅，确认后再执行。

## Global Constraints

- 用户先确认本设计及实施计划，再开始业务修改。
- 保留三主题与现有技术栈。
- 不部署、不改业务数据库、不配置真实 SMTP。
- 测试和体验均使用有归属校验的独立数据库。
- 密码恢复体验使用本地测试收件箱，真实邮件到达另列部署前事项。
- 隐私只有一个编辑入口：设置页。
- 主题切换不会保存隐私草稿；隐私保存不会重置主题。
- 审核预览不扣积分，不增加普通下载统计，不公开待审文件。
- 上传奖励仍在首次有效审核通过时发放。
- 用户确认网页端体验后，由 Codex 进行最终手机端核验。
- Chromium/WebKit 的设备模拟、浏览器兼容和实际真机分别记账。
- Windows PowerShell 5.1 兼容，使用 `npm.cmd`、已验证的捆绑运行时与显式 UTF-8；不改全局执行策略、PATH、语言或运行时。
- 每批完成受影响检查后继续推进，不重复进行无新依据的整套测试或逐页审批。用户要求增加功能、改变设计方向或触及不可逆操作时再明确范围。

## Review Focus

1. **换号/身份失效时的迟到响应**：列表、隐私保存和审核操作不能写入新账号状态。归属任务 1、3、5。
2. **大于一页与筛选交错**：21/40/51 条记录、第二页、快速变更条件、旧请求迟到仍显示正确集合。归属任务 1、2。
3. **资料重提与审核并发**：旧版本不能误审、失败换文件不能丢旧文件、重复通过不能重复积分。归属任务 3。
4. **设置页生效方式与数据保留**：主题立即生效、隐私显式保存、失败留草稿、重新加载不清令牌/主题、退出不被未保存提示阻挡。归属任务 5。
5. **极小移动视口与未读**：47px 可见高度仍能编辑发送、隐藏消息不误标已读，旋转关闭键盘后恢复；用户网页确认后才做最终移动验收。归属任务 6、10。

---

## 文件与职责划分

| 范围 | 主要修改/新增位置 | 职责 |
| --- | --- | --- |
| 列表、返回与表单 | `frontend/src/views/admin/*View.vue`，`views/profile/ScoreView.vue`、`OrderListView.vue`，`views/course/CourseListView.vue`，`views/material/MaterialListView.vue`，`views/square/SquareView.vue`，`router/index.js`，相关 `stores/` 与 `api/` | 分页、请求时序、表单初始化、错误反馈和返回状态 |
| 业务查询 | `backend/src/main/java/com/bugaoshan/controller/AdminController.java`、`service/impl/{Material,Mall,Score}ServiceImpl.java`、对应 mapper 与 XML | 分页总数、权限化 DTO、全量排序、月度汇总 |
| 资料生命周期 | `controller/MaterialController.java`，新增 `controller/AdminMaterialController.java`、资料版本/审核 DTO 与服务、mapper、迁移；前端新增资料编辑和审核详情组件 | 受控文件检查、版本、重提、审核记录与事务 |
| 举报与订单闭环 | `FeedbackView.vue`、`ReportsManageView.vue`、`OrderManageView.vue`、`OrderDetailView.vue`，对应控制器、服务、实体、mapper 与迁移 | 处理结论、本人回看、交付说明 |
| 设置 | `SettingsView.vue`、`EditProfileView.vue`、`ThemeOptions.vue`、`utils/storage.js`、`api/contact.js`、`api/security.js`；必要时新增 `composables/usePrivacySettings.js` | 单一隐私入口、生效语义、状态摘要和维护操作 |
| 页面结构 | `LoginView.vue`、`ProfileView.vue`、`SquareView.vue`、`HomeView.vue`、课程/资料页、后台概览/导航、共享样式 | 主次信息、入口和三主题响应式 |
| 回归与体验 | `frontend/scripts/product-integrity-browser.mjs`、`settings-ui.mjs`、现有前后端测试；`scripts/start-product-preview.ps1`、`stop-product-preview.ps1`；`docs/product-web-experience-checklist.md` | 可复查的回归、持续体验环境、逐页记录 |

新增文件应以单一职责命名，不把全部新业务继续塞入 `AdminController` 或单个大页面。上表中的 `*` 是影响范围，实施时只改实际需要的页面。

## 执行前准备（用户确认后）

- [x] 重新确认当前 Git 状态和 `dev1.1` 基线，保留用户未提交内容及既有验收证据。
- [x] 使用适合嵌套 CourseHub 仓库的隔离工作区和 `codex/` 分支；已有适用工作区可复用，不复位或删除旧私聊工作区。
- [x] 将本设计、计划和审视报告纳入同一整改版本，建立 F01–F14、设置专项和体验节点的进度表。
- [x] 只启动必要的隔离测试服务，确认数据库/文件目录/进程归属；运行每批所需的针对性基线检查，不再要求用户配合手机测试。

## Task 1: 修正误编辑、翻页、请求与返回状态

**Files:**
- Modify: `frontend/src/views/admin/CourseManageView.vue`、`BannerManageView.vue`。
- Modify: `frontend/src/views/profile/ScoreView.vue`、`OrderListView.vue`、`OrderDetailView.vue`。
- Modify: `frontend/src/views/course/CourseListView.vue`、`views/material/MaterialListView.vue`、`views/square/SquareView.vue`、对应 API、store 和 `router/index.js`。
- Create/Test: `frontend/scripts/product-integrity-browser.mjs`。

**Interfaces:** 保持现有列表查询字段与 `list/total`；返回状态按完整路由/账号归属保存；退款继续消费已有 `score_balance`。

- [x] 从审视证据转为聚焦回归：取消编辑后新增 `editingId=null`、空表单；40 条记录点击 2 后请求页码为 2；算法旧响应迟到时关键词 Web 与 Web 结果一致；详情返回保留关键词、页码和滚动。
- [x] 先运行对应用例，确认当前代码分别暴露 F01/F02/F06/F07；为不同账号的迟到查询补隔离断言。
- [x] 分开重置条件与翻页；使用现有请求代数/取消模式；实现可恢复列表状态；新增与编辑统一初始化；退款后同步余额。
- [x] 运行 `node scripts/product-integrity-browser.mjs --group=list-state`，预期全部对应断言通过且无非注入页面错误；不提前重跑真机。
- [x] 保存聚焦证据并提交该批变更，记录 F01/F02/F06/F07 处理状态。

## Task 2: 完整查询契约与管理端分页

**Files:**
- Modify: `frontend/src/views/admin/MaterialAuditView.vue`、`OrderManageView.vue`、`ReportsManageView.vue`、`SquareManageView.vue`、`ReviewManageView.vue`、`CourseManageView.vue`、`GoodsManageView.vue`。
- Modify: `frontend/src/api/admin.js`、`api/material.js`、`api/mall.js`、`components/MaterialCard.vue`、`views/mall/MallListView.vue`、`views/profile/ProfileView.vue`。
- Modify: `backend/src/main/java/com/bugaoshan/controller/AdminController.java`、`ScoreController.java`、相关服务接口/实现、mapper/DTO，`backend/src/main/resources/mapper/{Material,Goods,Course,ScoreRecord}Mapper.xml`。
- Test: `backend/src/test/java/com/bugaoshan/controller/AdminControllerTest.java`、`mapper/BusinessSqlContractTest.java`、相关服务测试及任务 1 浏览器脚本。

**Interfaces:** 所有管理列表消费 `{list,total}`；评价列表同步升级该形状。资料列表提供当前用户的 `is_favorited` 及公开 `uploader_name`。商城接收白名单 `sortBy=create_time|price_score`。新增 `GET /api/score/summary?month=YYYY-MM` 返回 `{earned,spent,refunded}`，只统计当前用户。

- [x] 加入 21 条管理记录、51 条课程/商品、末页删除、筛选改动、失败重试测试；修复前确认固定首批记录不能完整到达。
- [x] 后端排序先于分页；统一列表和总数筛选条件；公开上传者不绕过通讯录隐私；收藏按登录身份计算；移除无实现的评分/热度选项。
- [x] 接通页码、总数和筛选复位；教师筛选候选不依赖当前结果页；服务失败不能落到“暂无数据”。
- [x] 课程代码查重包含已删除记录，唯一键竞争返回业务提示；月度奖励、消耗、退款用大于 100 条流水验证，并覆盖退款跨月。
- [x] 执行相关 JUnit 和 `node scripts/product-integrity-browser.mjs --group=list-contracts`，确认末条可到达、排序针对全量、余额/统计正确，再提交本批。

## Task 3: 资料审核、版本与驳回修正闭环

**Files:**
- Modify: `backend/src/main/java/com/bugaoshan/controller/MaterialController.java`、`AdminController.java` 中现有审核动作、`service/impl/MaterialServiceImpl.java`、资料实体/mapper、文件存储接口与实现。
- Create: `backend/src/main/java/com/bugaoshan/controller/AdminMaterialController.java`、资料提交/审核 DTO、版本及审核记录实体/mapper/服务。
- Create: `backend/src/main/resources/db/migrations/2026-10-15-material-review-closure.sql`；同步当前 `db/init.sql`。
- Modify: `frontend/src/views/material/MaterialListView.vue`、`MaterialDetailView.vue`、`views/admin/MaterialAuditView.vue`、`api/material.js`、`api/admin.js`。
- Create: 前端资料修正/审核详情组件；`backend/src/test/java/com/bugaoshan/material/MaterialReviewMySqlIT.java`、`scripts/verify-material-review-mysql.ps1` 和对应浏览器用例。

**Interfaces:** 管理员专用 `GET /api/admin/materials/{id}` 与 `GET /api/admin/materials/{id}/file`；本人 `PUT /api/material/{id}/resubmit` 接收合法元数据、可选替换文件和 `expected_revision`。审核请求同时提交 `expected_revision`，使用单调 `revision` 校验所审文件。HTTP/JSON 仍沿用项目错误封装。

- [x] 为管理员可检查待审文件、普通用户不可检查、原上传者可修正、过期版本拒绝、重复通过只奖励一次写失败测试。
- [x] 增量迁移与初始化同步；旧资料创建初始快照和合法版本，不重置现有状态/积分；记录审核人、动作、原因和版本。
- [x] 实现编辑/换文件/再次提交及审核详情；数据库失败时清理新孤立文件，当前与历史文件引用继续有效；驳回原因必填并限长。
- [x] 用真实 MySQL 和真实本地文件完成“上传→检查→驳回→修改/换文件→重提→通过→下载”及双人并发审核；断言无重复积分、越权或误删旧文件。
- [x] 更新迁移/回退与文件保留说明，保存证据后提交该批。

## Task 4: 跨模块、举报与兑换结果衔接

**Files:**
- Modify: `frontend/src/components/CourseCard.vue`、课程/资料/广场关联入口和对应 API。
- Modify: `frontend/src/views/profile/FeedbackView.vue`、`DownloadView.vue`、`OrderListView.vue`、`OrderDetailView.vue`，`views/admin/ReportsManageView.vue`、`OrderManageView.vue`、`views/mall/MallDetailView.vue`。
- Modify: 举报、反馈、订单、广场控制器/服务/DTO/mapper；需要的新字段通过 `backend/src/main/resources/db/migrations/2026-10-15-interaction-closure.sql` 加入并同步 `init.sql`，不改变既有订单状态编号。
- Test: 对应前后端和浏览器闭环测试。

**Interfaces:** 课程跳资料使用 `courseId`；讨论关联用稳定课程 ID，兼容保留旧名称。新增受认证的本人举报列表；管理员处理请求提交处理动作与原因，用户只看自己的结果。订单处理说明由服务端持久化并返回本人订单详情。

- [x] 写断言：课程跳转后筛选已选当前课程、历史可重开对应对象、举报人能回看结论而其他人不能看、取消订单后直接进入商城显示退款余额。
- [x] 实现举报“成立并下架/不成立”与处理原因；在反馈区域增加“我的举报”子页签；失败时可重试而不伪造已处理状态。
- [x] 管理订单增加内容检查与处理说明，用户回看兑换方式和结果；统一状态名称；去掉无法执行的赚分指引，关联到已有真实入口。
- [x] 真实接口完成一条举报处理回看、一条兑换完成及一条取消退款；非法重复操作不重复扣/退积分。
- [x] 提交本批并补齐体验清单中的前后角色衔接。

## Task 5: 设置页信息架构与交互重组

**Files:**
- Modify: `frontend/src/views/profile/SettingsView.vue`、`EditProfileView.vue`、`AccountSecurityView.vue` 的相关入口、`components/ThemeOptions.vue`、`utils/storage.js`、必要的路由定位。
- Create: `frontend/src/composables/usePrivacySettings.js`（若需要抽离请求/保存/归属状态），`frontend/scripts/settings-ui.mjs`。
- Modify: `views/profile/AboutView.vue`、`SupportView.vue`，统一帮助渠道配置。

**Interfaces:** 继续使用 `/api/contact/auth/my` 与 `/api/contact/auth/update`，字段为 `contact_visible=1|2|3`、`phone_visible/email_visible=1|2`；安全摘要来自现有 `/api/user/security`，不新增第二套邮箱或密码流程。编辑资料通过 `/profile/settings#privacy` 定位。

- [x] 先为同一隐私入口、字段父子关系、独立保存、保存失败保留修改、主题切换不保存隐私、换号迟到结果、重新加载保留登录/主题写行为测试。
- [x] 按设计的账号安全、隐私、界面、帮助维护四分区重组；编辑资料移除重复隐私表单，保留定位链接；移除无效通知开关和全量缓存清理。
- [x] 隐私表单以成功读取/保存的数据为基线计算未保存状态；加载失败禁用、保存失败不清草稿、正常离开提示、认证退出直接清理；有效范围摘要严格匹配后台隐私策略。
- [x] 运行 `node scripts/settings-ui.mjs`，核对三个角色、三主题、桌面/窄屏及断网/切号；确认不意外改动账号安全规则。
- [x] 提交设置批次，更新用户体验项，保留网页端最终统一确认节点。

## Task 6: 重点页面重排与全站一致性

**Files:**
- Modify: `frontend/src/views/login/LoginView.vue`、`profile/ProfileView.vue`、`square/SquareView.vue`、`home/HomeView.vue`、课程/资料页、后台概览和 `components/AppSidebar.vue`、必要的 `AppNavbar.vue`。
- Modify: `frontend/src/styles/common.css`、`variables.css`、`responsive.css` 和确需统一的共享组件。
- Test: 任务 1/5 浏览器脚本、现有私聊/账号回归及页面布局检查。

**Interfaces:** 既有路由和动作继续可到达；角色和数据权限不因布局变化扩大。黑主题选中前景色使用最终计算样式核对。私聊可见视口变量、47px 编辑模式与未读保护保持现有契约。

- [x] 先按设计记录各重点页的桌面/窄屏结构草图和主操作；普通排版细节由主代理实现，不新增逐页审批环节。
- [x] 重排登录、个人中心、广场、首页、课程/资料及后台；个人中心把历史/账户服务分组，后台以待办为主并提供文字导航；关联选择不依赖手填 ID。
- [x] 对其余最终路由逐页检查内容、主要动作、返回、空/错/忙状态；必要时做同体系修正并记入变更清单。
- [x] 验证 1440/1024/768/390/320px 与三主题的代表场景，390×844 登录主动作首屏可见；修正对比度、焦点和触控区域。
- [x] 运行受影响回归及构建；私聊界面有联动时复验既有 47px/偏移/旋转用例，之后提交该批。

## Task 7: 整体验证与稳定网页体验环境

**Files:**
- Create: `scripts/start-product-preview.ps1`、`stop-product-preview.ps1`，必要的本地测试收件箱与体验数据准备助手。
- Create: `docs/product-web-experience-checklist.md`、本地体验操作说明；运行数据、凭据、截图和日志放 Git 忽略目录。
- Modify: 相关验证记录和 README 的当前范围说明。

**Interfaces:** 启动输出明确的本地前端地址、测试邮箱入口及学生 A/B、教师、管理员账号；环境有 run ID/nonce、专属数据库与进程身份。停止只影响所属实例。体验环境在用户体验期间保持运行，不采用执行完立即关闭的 E2E runner 作为长期入口。

- [x] 最终组合树运行后端测试、前端认证/找回/联系人/私聊/本轮行为测试、契约、生产构建；变动数据库事务执行对应真实 MySQL 用例。
- [x] 准备空数据、大于一页、待审/驳回/通过资料、反馈/举报、兑换和双账号私聊等可用测试数据；真实文件与本地收件箱可体验，不能用假成功提示替代。
- [x] 从最终路由、角色、弹窗和动作生成清单，逐项包括入口、体验操作、预期、结果、用户意见。核对每个可访问页面有清单项。
- [x] 验证开始/状态/停止及重新启动不会误连业务库或清掉用户体验数据；提供用户容易使用的账号切换指引。
- [x] 向用户交付稳定地址与清单，**在此停止产品改动，等待用户实际体验和反馈**。

## Task 8: 用户网页端实际体验（用户决策节点）

**Owner:** 用户实际操作；Codex 保持环境可用并协助定位。

- [x] 用户已明确表示“我已经体验完了，没有什么问题”；记录为用户整体确认，不编造逐项操作明细。
- [x] 反馈已整理：登录问题修复后无新的功能问题，本次希望增加配色。
- [x] 用户选择新增配色并要求直接开始第三阶段；其他大功能继续留在候选表。
- [x] Codex 按实际反馈新增三套配色，沿用现有页面和权限。

## Task 9: 网页体验反馈整改与复看

**Files:** 以用户反馈实际涉及文件为准；更新体验清单、变更和回归记录。

- [x] 登录请求失败已复现并修复；用户确认网页正常后，追加范围限定为三套配色。
- [x] 完成新增配色与受影响回归；保留原主题、权限、积分及私聊行为，移动发现的 CSV 与触控问题已定向修复。
- [x] 受影响页面已提供在原体验环境，用户无需重做全站体验；新增配色的主观反馈尚未收集，不预填用户评价。用户已要求直接进入第三阶段。
- [x] 已获用户明确的网页端体验确认及开始第三阶段的指令。

## Task 10: Codex 执行最终手机端核验

**Files:** 移动浏览器验证脚本、必要的页面修复；`docs/private-chat-mobile-acceptance.md` 与全站移动验证记录。

**Owner:** Codex；不再要求用户重复执行长时间手机清单。

- [x] 专用 ADB 查询当前 0 台设备；使用 Chromium 移动模拟，新版真机列为未测，旧 iQOO/vivo 记录不计作新版通过。
- [x] 已检查 320/360/375/390/430/768px 代表页面、模拟触控、横屏与恢复、六主题、弹窗及主要表单；真实浏览器工具栏/硬件安全区未测，边界见核验记录。
- [x] 私聊 24 个主题/尺寸组合与键盘/发送/历史/未读回归通过，含 800×218、47px 和 0/150 偏移；系统输入法和真机明确未测。
- [x] 真实后端完成登录、课程选退、资料版本审核与下载、设置、后台、网络恢复；好友/聊天本轮检查页面与草稿，发送及未读用受控回归；订单检查详情和确认入口，兑换退款事务沿用第一轮证据，不伪造本轮执行。
- [x] 当前未接入可操作真机；Android/vivo、Android/Chrome、iPhone/Safari 均明确未测。
- [x] 受影响网页已补回归并在原环境交付供复看；已测/未测、修复和证据已记录。体验环境保持运行，未部署；用户对新增配色的主观评价仍待其使用反馈。

## 最终完成合同

- [x] F01–F14 均有处理结果、对应验证与范围说明；不把源码推断写成真机实测。
- [x] 设置页逻辑和页面重组完成，重复入口、保存语义、通知项与数据维护行为一致。
- [x] 资料、举报、兑换、返回与失败恢复路径有可操作的结果；大数据分页可以到达末页。
- [x] 用户已整体确认网页体验通过，并决定追加配色后直接进入第三阶段；变更已完成并交付，自动回归已通过，不将其写作用户已评价新增配色。
- [x] Codex 已完成当前可执行的移动核验，自动化与真实后端范围清楚；当前不可接入的真机及相关系统行为均透明记录为未测。
- [x] 最终代码、文档、测试、体验意见和发布边界可追溯；业务库、真实 SMTP 和线上部署没有被计划内操作误改。

**授权记录：** 2026-10-16 用户要求记录并暂停；2026-10-22 用户以“PLEASE IMPLEMENT THIS PLAN”明确重新授权。当前执行第一轮 Task 1–7，完成后停在网页实际体验节点；用户确认网页体验后再进行最终手机核验。

**本轮收口记录：** Task 1–7 合并为一个本地交付提交，保持工作区及体验环境；未合并、推送或部署。[验证证据](../../product-completion-verification-2026-10-22.md)与[网页体验清单](../../product-web-experience-checklist.md)已完成。用户体验、反馈调整与最终手机核验仍待后续节点。

**后续更新（2026-10-22）：** 上述收口描述第一轮结束时状态。用户现已确认网页体验通过，并追加配色后授权第三阶段。扩展至六主题后的移动验证结果见[本轮记录](../../product-mobile-and-themes-2026-10-22.md)；真机未接入，不代表真机验收通过。原有网页无需重新完整体验，新配色交付供用户选择和反馈，不预填其主观评价。
