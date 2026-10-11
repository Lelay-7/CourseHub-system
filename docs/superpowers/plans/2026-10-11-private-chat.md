# 好友关系与可扩展私聊 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让已建立好友关系的用户在 CourseHub 内安全发送、读取一对一文字消息，并交付符合现有三主题的桌面/手机 UI。

**Architecture:** 好友关系服务统一规范用户对与加锁；会话、消息及限流分别负责各自事务和 SQL。前端通过 REST、独立会话状态与单一轮询所有者读取消息，成员表、类型化消息和传输边界支持后续扩展。

**Tech Stack:** Java 21 / Spring Boot 3.3.5 / MyBatis / MySQL 8.0 / Vue 3 / Element Plus / Pinia / Node 内置测试 / 已安装 Playwright；不新增产品依赖。

**Spec:** `docs/superpowers/specs/2026-10-11-private-chat-design.md`；UI 依据 `docs/superpowers/prototypes/2026-10-11-private-chat-ui.html`。

**Status:** 设计、交互稿及本计划已确认；七项任务已直接执行（Native），独立聚焦审查及一次重要问题修复已完成，最终验证通过。代码保留在本地 codex/private-chat 分支及隔离工作区，不推送、不部署。

## Global Constraints

- 仅已接受好友可创建私聊或发送新消息；只有成员可读取，管理员不获得额外阅读权限；解除好友保留只读历史，重新加好友复用会话。
- 首版 `PRIVATE` 会话固定两名成员、`TEXT` 消息；无附件/群聊/撤回/编辑/引用/在线状态/外部推送按钮。保留成员、消息类型、传输适配器和提交后事件边界。
- 正文 1–2000 个 Unicode code point，纯空白拒绝，CRLF/CR 规范化为 LF；仅允许换行/制表符这两种控制字符；HTML 字符按纯文本显示。
- 默认每账号每分钟 30 条、每小时 300 条新消息；幂等重试不重复扣额度；窗口过期最多保留 48 小时。配置前缀 `app.chat`。
- 历史默认 30 条、最多 100；`before_sequence` 与 `after_sequence` 互斥；全部 BIGINT ID/序号输出十进制字符串；新表 `DATETIME(3)` 使用服务端 UTC 时间参数。
- 写事务 `READ_COMMITTED`；按用户 ID 升序逐个锁用户，再锁关系、会话、成员、限流；事务内无外部网络调用；锁定当前用户后复核认证时捕获的凭据版本。
- 未读按消息序号和成员读游标查询；读游标只单调前进到本会话真实消息，查询不自动标已读。前端只有可见且滚到最新时上报已显示序号。
- 全局未读/会话首页 15 秒轮询、当前会话 5 秒增量及权限刷新；失败退避 10/20/40/60 秒；请求完成再计时，隐藏/退出取消，返回可见立即补拉。
- 消息/草稿仅在当前认证会话内存中保存；换账号、退出及 401 立即清理；迟到响应不能写入新会话。
- UI 复用现有主题变量、布局和组件：瓷白墨蓝/暖白酒红/曜石黑、1180px 内容宽度、桌面 300px/平板 260px 会话栏、768px 断点、320px 无横向滚动、操作目标至少 44px。
- 仅在独立临时 MySQL 验证迁移；不执行业务库迁移、不配置真实 SMTP、不部署。Windows 命令兼容 PowerShell 5.1、使用 `npm.cmd`、显式 UTF-8；不改全局运行时、PATH 或执行策略。

## Review Focus

1. 接口已提交但响应丢失：重试仍使用原 `client_message_id`，只能存一条、扣一次额度（Task 3、4、6）。
2. emoji、全角/Unicode 空白及控制字符：前后端按相同计数/空白规则处理，2000 个 emoji 可发送，2001 个拒绝（Task 3、4、5）。
3. 读旧历史时新消息到达：不抢滚动、不误清未读、向前分页不丢锚点，增量超过一页连续补拉（Task 4、5、6）。
4. 旧库重复用户对混有禁用用户、孤立关系或非法状态：非法数据在任何修改前停止，合法重复只按既定优先级清理（Task 1、6）。
5. 轮询失败与刷新/换账号同时发生：保留草稿、无重复全局弹窗、无旧响应污染，401 仍清理并跳登录（Task 4、6）。

## 文件边界与公共契约

沿用现有 `controller`、`service`、`mapper`、`dto` 和 `entity` 目录。服务使用构造注入及现有 `TransactionTemplate` 模式，避免测试绕过事务代理。Java 路径下文相对 `backend/src/main/java/com/bugaoshan/`，测试相对 `backend/src/test/java/com/bugaoshan/`。

| 文件/单元 | 唯一职责与输出 |
| --- | --- |
| `service/FriendRelationshipService.java` | 关系状态变更及共享用户对加锁；内部记录 `LockedUsers(long lowerId,long higherId,User lower,User higher)` 提供 `user(long id)` 查询；缺失用户保留 null，由业务策略判断 |
| `security/AuthenticatedSession.java`、`common/SecurityUtils.java` | 已验证角色与凭据版本上下文；`getCurrentUserId()` 保持 Long，角色读取兼容现有 Integer details |
| `dto/ChatDTO.java` | API 请求/响应记录及内部 `Actor(long userId,int credentialVersion)`；下述服务结果使用其嵌套记录 |
| `service/ChatConversationService.java`、`mapper/ChatConversationMapper.java`/XML | 会话创建/复用、成员鉴权、列表、详情、读游标、未读 |
| `service/ChatMessageService.java`、`mapper/ChatMessageMapper.java`/XML | 文字校验、幂等发送、序号、历史/增量分页、提交后事件 |
| `service/ChatSendRateLimiter.java`、`mapper/ChatRateLimitMapper.java`/XML、`config/ChatConfig.java` | UTC 时钟、发送配置、数据库额度及过期清理 |
| `controller/ChatController.java`、`common/ChatException.java`、`common/ChatExceptionHandler.java` | 七个聊天接口、聊天专用 HTTP 错误与 `Result` 包装 |
| `frontend/src/api/chat.js`、`utils/chat-state.js`、`utils/chat-polling.js` | 传输请求、可注入 API 的状态协调、可注入计时器的轮询；独立于页面渲染 |
| `frontend/src/stores/chat.js`、`composables/useChatPolling.js` | reactive 状态接入 Pinia、认证清理、页面可见性与生命周期 |
| `frontend/src/views/chat/MessageCenterView.vue`、`components/chat/*` | 视图及列表、时间线、输入、TEXT 渲染组件，分别负责可理解的一段交互 |

`ChatDTO` 公共响应：`Peer(userId,nickname,avatarUrl,userType,department)`；`TextContent(text)`；`MessageView(messageId,conversationId,sequence,senderId,clientMessageId,messageType,content,createdAt)`；`ConversationView(conversationId,conversationType,peer,lastMessage,lastSequence,lastReadSequence,unreadCount,canSend,sendDisabledReason)`；`ConversationPage(items,nextCursor)`；`MessagePage(items,nextBeforeSequence,nextAfterSequence,hasMore,latestSequence)`；`ReadView(lastReadSequence,unreadCount)`；`UnreadView(unreadCount)`。ID/序号字段为 String，计数为 long，时间为 UTC `Instant`，记录统一输出 snake_case。`lastMessage` 使用 `MessageView` 的最小安全预览内容；空会话为 null。

请求为 `CreatePrivateRequest(Long targetUserId)`、`SendTextRequest(UUID clientMessageId,String messageType,String text)`、`ReadRequest(String lastReadSequence)`。非法字符串序号在业务边界校验为 400，不泄露解析异常。新 peer 字段 `avatar_url` 不改变旧名片的 `avatarUrl` 契约。

实施前使用 `superpowers:using-git-worktrees` 检查现有附件并建立/复用隔离工作区，功能分支使用 `codex/` 前缀；从包含设计和计划的当前 HEAD 开始，保留当前 `dev1.1` 的文档提交。运行前确认工作区干净和 Java/Maven/Node 路径，不安装依赖或升级运行时。

以下 Maven 命令在 `backend` 运行，使用已验证的 `C:/Users/二货道士/.m2/maven-home/bin/mvn.cmd`；前端命令在 `frontend` 运行，Node 使用 `C:/Users/二货道士/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe`。Maven 在沙箱中可能受 JDK attach 管道权限影响；区分环境失败与功能失败，必要时按已有授权申请单次受审执行。

每个新终端会话用以下局部变量调用工具，不修改系统 PATH：

```powershell
$chatMaven = 'C:\Users\二货道士\.m2\maven-home\bin\mvn.cmd'
$chatNode = 'C:\Users\二货道士\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
$env:PLAYWRIGHT_BROWSERS_PATH = 'C:\Users\二货道士\AppData\Local\ms-playwright'
```

---

### Task 1: 一致的好友关系与用户对约束

**Files:** Create `service/FriendRelationshipService.java`；Modify `controller/ContactController.java`、`mapper/FriendMapper.java`、`backend/src/main/resources/mapper/FriendMapper.xml`、`backend/src/main/resources/db/init.sql`；Create `backend/src/main/resources/db/migrations/2026-10-11-private-chat.sql`、`backend/src/test/resources/db/private-chat-h2.sql`；Test `service/FriendRelationshipServiceTest.java`、现有 `controller/ContactControllerTest.java`。

**Interfaces:** Consumes `UserMapper.selectForUpdate(Long)`；Produces `LockedUsers lockUsers(long actorId,long targetId)`（调用者必须已在事务中）、`boolean isAccepted(long actorId,long targetId)`、`AddFriendResult addFriend(long actorId,long targetId)`（含 `acceptedExistingRequest`）、`void accept(long actorId,long friendId)`、`void remove(long actorId,long friendId)`。Mapper 新增 `Friend selectPair(long lowerId,long higherId)`；接受 SQL 保留现有方法但增加 `status=0`；删除返回实际受影响行数。

- [x] Step 1：在真实服务/SQL 的 H2 测试 fixture 中增加 `reverseAcceptedDoesNotCreatePending`、`invalidTargetDoesNotInsert`、`onlyRecipientMayAcceptPending`、`pairLocksAreTakenInAscendingOrder`，补现有控制器委托/参与者测试。断言：
  ```java
  insertFriend(2L, 1L, 1); // helper 在本测试类定义，返回 friend_id
  assertThrows(BusinessException.class, () -> service.addFriend(1L, 2L));
  assertEquals(1, countPair(1L, 2L));
  assertEquals(1, findPair(1L, 2L).getStatus());
  ```
- [x] Step 2：运行 `& $chatMaven '-Dtest=FriendRelationshipServiceTest,ContactControllerTest' test`，确认因缺失关系规则/错误状态而失败；环境错误不记作 RED。
- [x] Step 3：实现上述接口及 `READ_COMMITTED` 事务；先读取申请定位用户对，再按升序锁用户并重新读取关系后变更。校验自身/目标状态，处理正反向申请，条件接受，参与者删除；控制器保持旧接口和名片隐私行为。
- [x] Step 4：迁移先检查自关系、物理不存在的用户及非法状态，发现即 SQL `SIGNAL` 停止；禁用或软删除用户的既有关系保留。合法重复优先已接受，否则最小 friend_id。添加 `user_low_id`/`user_high_id` STORED 生成列和唯一索引；用 information_schema 判断已有列/索引，支持更新后的 init 与迁移链配合。H2 fixture 单独适配语法，不称其验证 MySQL DDL。
- [x] Step 5：运行 Task 1 测试及 `npm.cmd run test:contact`，输出零失败；真实迁移/并发由 Task 6 验收。
- [x] Step 6：只 stage 本任务文件并提交 `feat: make friend relationships consistent for private chat`。

### Task 2: 认证上下文、会话与受限读取

**Files:** Create `security/AuthenticatedSession.java`、`dto/ChatDTO.java`、`entity/ChatConversation.java`、`entity/ChatConversationMember.java`、`entity/ChatMessage.java`、`service/ChatConversationService.java`、`mapper/ChatConversationMapper.java`、`backend/src/main/resources/mapper/ChatConversationMapper.xml`、`controller/ChatController.java`、`common/ChatException.java`、`common/ChatExceptionHandler.java`；Modify `security/JwtAuthenticationFilter.java`、`common/SecurityUtils.java`、Task 1 的 init/迁移/H2 fixture；Test `chat/ChatTestFixture.java`、`chat/ChatConversationTest.java`、`chat/ChatApiTest.java`、现有 `security/JwtAuthenticationFilterTest.java`。

**Interfaces:** Consumes Task 1；Produces `ConversationView openPrivate(Actor actor,long targetId)`、`ConversationPage list(Actor actor,int limit,String cursor)`、`ConversationView detail(Actor actor,long id)`、`ReadView markRead(Actor actor,long id,long sequence)`、`UnreadView unread(Actor actor)`、`LockedUsers lockMembersForWrite(Actor actor,long id)`；最后方法在调用者事务中按成员限定查询获取用户对，再锁用户并复核凭据版本，供 Task 3 复用。`ChatException.status()`/`retryAfterSeconds()` 提供 HTTP 状态及可选重试时间。`SecurityUtils.getCurrentCredentialVersion()` 不存在可信上下文时拒绝，`AuthenticatedSession(int userType,int credentialVersion)` 只由过滤链设置。

- [x] Step 1：创建 `ChatTestFixture`，负责独立 H2、真实 MyBatis 与 TransactionTemplate 服务实例；提供 `actor(id,version)`、`friend(a,b)`、`seedMessage(conversation,sequence,sender,text)`。测试 `privateConversationIsReused`、`outsiderAndAdminCannotRead`、`unfriendingKeepsReadOnlyHistory`、`readCursorCannotRegressOrCrossConversations`、`peerJsonContainsNoPrivateFields`、`largeIdsRemainStrings`：
  ```java
  var chat = conversations.openPrivate(actor(1L, 0), 2L);
  assertEquals(chat.conversationId(), conversations.openPrivate(actor(2L, 0), 1L).conversationId());
  assertEquals(404, assertThrows(ChatException.class,
      () -> conversations.detail(actor(3L, 0), Long.parseLong(chat.conversationId()))).status());
  ```
- [x] Step 2：运行 `& $chatMaven '-Dtest=ChatConversationTest,ChatApiTest,JwtAuthenticationFilterTest' test`，记录上述行为的失败。
- [x] Step 3：为新上下文调整过滤链和 SecurityUtils，保持旧用户 ID/角色调用兼容；添加三张聊天表、索引和显式 UTC 时间参数；会话创建和成员插入同事务，唯一规范用户对；Actor 由控制器读取可信上下文，不从 body 获取。
- [x] Step 4：实现列表/详情/读游标/未读 API 和专用异常处理；非成员与不存在统一 404；成员历史不要求仍是好友；发送资格在详情动态计算。目标不可用/旧凭据转为 ChatException 对应状态，不让通用 BusinessException 导致聊天错误仍返回 HTTP 200。读游标先核对本会话实际消息，单调更新；未读仅统计他人新消息。预览按 code point 截断为最多 80 个，详情最小 peer 投影遵守 ContactCardPolicy。
- [x] Step 5：重复 Step 2 命令，确认 MockMvc 的 HTTP/业务码、五个入口、snake_case、ID 字符串及旧契约测试全部通过。
- [x] Step 6：只 stage 本任务文件并提交 `feat: add authenticated private conversations and read cursors`。

### Task 3: 类型化文字、幂等发送、分页与额度

**Files:** Create `service/ChatMessageService.java`、`service/ChatSendRateLimiter.java`、`mapper/ChatMessageMapper.java`、`mapper/ChatRateLimitMapper.java`、对应 `backend/src/main/resources/mapper/ChatMessageMapper.xml` / `ChatRateLimitMapper.xml`、`entity/ChatSendRateWindow.java`、`config/ChatConfig.java`、`chat/ChatMessageCreated.java`；Modify `ChatController`、Task 2 DTO/异常与 init/迁移/H2 fixture、`backend/src/main/resources/application.yml`、项目根 `.env.example`；Test `chat/ChatMessageTest.java`、`chat/ChatRateLimitTest.java`、`chat/ChatApiTest.java`。

**Interfaces:** Consumes `lockMembersForWrite`、Task 2 DTO；Produces `MessageView send(Actor actor,long conversationId,SendTextRequest request)`、`MessagePage messages(Actor actor,long conversationId,Long before,Long after,int limit)`、`String normalizeText(String text)`；`void reserve(long userId,Instant now)` 在同一事务中原子预留，失败抛 429；`int purgeExpired(Instant cutoff,int batchSize)` 分批清理。配置 `send-per-minute=30`、`send-per-hour=300`、`rate-retention-hours=48`，注入 UTC Clock 便于固定窗口测试。`ChatMessageCreated(long conversationId,long messageId,long sequence,List<Long> participantIds)` 无正文。

- [x] Step 1：增加幂等/冲突、2000/2001 emoji、全角空格/NBSP、控制字符/未配对 surrogate、未知类型、UUID/游标错误、删除好友后的新消息和重试拒绝、序号补拉、回滚额度、旧凭据写入测试：
  ```java
  var request = new SendTextRequest(UUID.randomUUID(), "TEXT", "😀".repeat(2000));
  var first = messages.send(actor(1L, 0), id, request);
  assertEquals(first.messageId(), messages.send(actor(1L, 0), id, request).messageId());
  assertEquals(1, countMessages(id));
  assertEquals(1, usedMinuteQuota(1L));
  ```
- [x] Step 2：运行 `& $chatMaven '-Dtest=ChatMessageTest,ChatRateLimitTest,ChatApiTest' test`，确认缺少发送/校验行为时失败。
- [x] Step 3：实现 TEXT JSON 内容及 DTO 映射。纯空白定义为 Unicode White_Space（包含 NBSP/全角空格）；先拒绝未配对 surrogate，规范换行，再按 code point 校验长度与控制字符；前端用同一定义。事务锁定/校验成员、用户与当前关系，幂等检查后预留额度；会话锁内推进 `last_sequence`、插入消息、更新活动时间，统一提交/回滚。重复键的结果必须核对会话、类型、规范正文，不能直接当成功。JSON 内容只保存类型校验后的对象。
- [x] Step 4：实现历史/增量互斥游标、升序结果与完整下一游标；初次返回最新一页，补拉只推进到实际返回的末条。限流按分钟/小时稳定顺序锁窗口，新消息失败回滚两个窗口。配置有界清理批次、定时清理和 429 重试时间；提交后发布无正文事件，首版不接网络推送器。
- [x] Step 5：重复 Step 2 命令，确认全部七接口、400/401/403/404/409/429、丢失响应后的同 ID 重试测试零失败。
- [x] Step 6：只 stage 本任务文件并提交 `feat: persist idempotent text messages with pagination and limits`。

### Task 4: 前端会话状态、认证清理与轮询

**Files:** Create `frontend/src/api/chat.js`、`frontend/src/utils/chat-state.js`、`frontend/src/utils/chat-polling.js`、`frontend/src/stores/chat.js`、`frontend/src/composables/useChatPolling.js`、`frontend/scripts/private-chat-state.test.mjs`；Modify `frontend/src/utils/auth.js`、`frontend/src/api/request.js`、`frontend/src/App.vue`、`frontend/package.json`、`frontend/scripts/auth-session.test.mjs`。

**Interfaces:** `chatApi` 提供 `openPrivate(targetUserId,config)`、`listConversations(params,config)`、`getConversation(id,config)`、`getMessages(id,params,config)`、`sendMessage(id,data,config)`、`markRead(id,sequence,config)`、`getUnread(config)`，config 支持 `signal`/静默轮询；均复用现有 request 实例。`createChatState()` 返回普通初始对象；`createChatSession({state,api,getSessionId,newClientMessageId})` 提供 `selectConversation`、`loadPrevious`、`pullNew`、`sendText`、`retrySend`、`markVisibleRead`、`refreshConversations`、`refreshUnread`、`reset`；store 将 state 包装为 reactive 再注入。`createChatPoller({task,intervalMs,isVisible,setTimeout,clearTimeout,onError})` 返回 `start/stop/visibilityChanged`。`subscribeAuthSessionChange(listener)` 返回取消订阅函数，clearTokens/新登录身份同步通知，token 刷新保持身份不重置。

状态字段固定为 `conversations`、`messagesByConversation`、`draftsByConversation`、`pendingByClientId`、`selectedId`、`unreadCount`、`paginationByConversation`、`errors`；消息沿用 API snake_case，增加内存 `delivery_state = SENDING|SENT|FAILED`。方法签名为 `selectConversation(id):Promise<void>`、`loadPrevious(config?):Promise<void>`、`pullNew(config?):Promise<void>`、`sendText(text):Promise<LocalMessage|undefined>`、`retrySend(clientMessageId):Promise<LocalMessage|undefined>`、`markVisibleRead({visible,atBottom}):Promise<void>`、`refreshConversations(config?):Promise<void>`、`refreshUnread(config?):Promise<void>`、`reset():void`。发送网络失败转为 FAILED 结果并保存原 UUID，不抛出未处理 Promise rejection。读取 config 可传 AbortSignal；poller 的 `task(signal):Promise<void>` 接收取消信号，三个生命周期方法均返回 void。

- [x] Step 1：用现有 esbuild+Node test 模式加载真实模块，注入 API、UUID 和计时器。测试丢失响应重试、超过一页补拉、分页去重、换账号/快速切会话迟到响应、退出同步清理、Unicode 长度、隐藏取消及退避：
  ```js
  await chat.sendText('hello') // fixture API 本次提交后模拟响应超时
  const originalId = calls[0].data.client_message_id
  await chat.retrySend(originalId)
  assert.equal(calls[1].data.client_message_id, originalId)
  assert.equal(state.messagesByConversation[id].length, 1)
  ```
- [x] Step 2：运行 `& $chatNode --test scripts/private-chat-state.test.mjs scripts/auth-session.test.mjs`，确认缺少状态守卫/轮询行为而失败。
- [x] Step 3：实现请求和状态协调，所有异步响应同时校验认证身份、选中会话和请求代次；失败消息保存原 UUID，草稿按会话保留，仅存内存。序号用 BigInt/规范十进制字符串比较，禁止 Number 转换造成排序精度丢失；加载旧历史与新增合并不直接标已读。
- [x] Step 4：request 增加仅供后台请求使用的静默错误选项，保留现有 401 刷新/清理/跳登录；auth 生命周期同步清理 chat，并处理浏览器 storage 事件引起的跨标签身份改变。App.vue 是全局未读轮询唯一所有者，聊天页只拥有详情计时器；列表首页刷新重置排序游标并保留当前聊天。停止时取消 AbortController，等待请求结束才安排下次，无重叠；隐藏与重新可见按设计处理。
- [x] Step 5：新增 `test:chat` 为 Node 行为测试命令，运行其与 `test:auth`、`test:recovery`、`test:contracts`，确认零失败。
- [x] Step 6：只 stage 本任务文件并提交 `feat: coordinate private chat sessions and polling`。

### Task 5: 与现有风格一致的正式私聊页面

**Files:** Create `frontend/src/views/chat/MessageCenterView.vue`、`frontend/src/components/chat/ConversationList.vue`、`MessageTimeline.vue`、`MessageComposer.vue`、`TextMessage.vue`、`frontend/scripts/private-chat-ui.mjs`；Modify `frontend/src/router/index.js`、`frontend/src/components/AppShell.vue`、`frontend/src/components/AppNavbar.vue`、`frontend/src/views/profile/ProfileView.vue`、`frontend/src/views/contact/ContactListView.vue` / `ContactDetailView.vue`、`frontend/package.json`。

**Interfaces:** Consumes Task 4 store；路由 `/messages` 与 `/messages/:id` 均 requiresAuth、ALL 角色，详情 meta 标记 `hideMobileTabbar`。`ConversationList` props `{items,selectedId,loading,error}`，emit `select/search/load-more/retry`；`MessageTimeline` props `{messages,peer,loading,error,hasPrevious}`，emit `load-previous/at-bottom/retry`，保持锚点并暴露 `scrollToLatest()`；`MessageComposer` props `{modelValue,disabled,sending,maxCodePoints:2000}`，emit `update:modelValue/send`；`TextMessage` 只插值内容、发出 `retry(clientMessageId)`。按 `message_type` 选择 TEXT 组件，未知类型安全降级为“暂不支持的消息类型”。

- [x] Step 1：新增可重复浏览器 UI 探针（此任务用限定的 API fixture，不称真实 E2E）：验证入口、IME Enter 不发送、Shift+Enter 换行、纯文本 XSS 显示、重试复用 UUID、滚动/历史锚点、只读、空/错误/加载、键盘焦点，失败时保存截图/错误。
  ```js
  await page.getByRole('textbox', { name: '消息内容' }).fill('<img src=x onerror=alert(1)>')
  await page.getByRole('textbox', { name: '消息内容' }).press('Shift+Enter')
  assert.equal(capturedWrites.length, 0)
  await page.getByRole('button', { name: '发送', exact: true }).click()
  assert.equal(await page.getByText('<img src=x onerror=alert(1)>', { exact: true }).count(), 1)
  ```
- [x] Step 2：运行 `npm.cmd run build` 后执行 `& $chatNode scripts/private-chat-ui.mjs`，记录页面不存在或行为未满足的失败，排除环境错误。
- [x] Step 3：实现页面与四个组件，复用 AppLayout/PageContainer/PageHeader/EmptyState/ErrorState 和原主题变量，按已批准交互稿落地双栏、聊天头、气泡、固定输入区和手机版列表/详情。维护日期分组、头像失败回退、44px 触达目标、低干扰播报及 reduced-motion；显示“已发送”只代表持久化成功。
- [x] Step 4：接通通讯录“发消息”和个人中心/顶栏入口；好友列表按钮阻止冒泡，现有订单 Bell 含义保持；AppShell 依据路由 meta 隐藏聊天详情 TabBar，并包含 `/messages` 前台路径。窄屏保留消息/个人入口，避免 theme/order 图标挤出页面。
- [x] Step 5：时间线向前加载保存高度/首条锚点；非底部收到消息显示定位按钮且不标已读；当前可见并在底部才上报读游标。绑定 visualViewport resize/scroll 与安全区，卸载清理，模拟软键盘视口缩小检查输入可见。
- [x] Step 6：重复 Step 2 命令及 `npm.cmd run test:contact`；三主题分别检查 1440px、768px、390px、320px、长链接/2000 字正文、初次空聊天，确认无页面 JS 错误/横向溢出。实际手机软键盘如未有设备验证，在证据中明确说明。
- [x] Step 7：只 stage 本任务文件并提交 `feat: add themed responsive private chat UI`。

### Task 6: 独立真实 MySQL 与双账号验收

**Files:** Create `scripts/verify-private-chat-mysql.ps1`、`scripts/run-private-chat-e2e.ps1`、`backend/src/test/java/com/bugaoshan/chat/PrivateChatMySqlIT.java`、`PrivateChatMigrationMySqlIT.java`、`PrivateChatE2eApplication.java`、`backend/src/test/resources/db/private-chat-legacy-mysql.sql`、`frontend/scripts/private-chat-e2e.mjs`；Modify `scripts/verify-account-recovery-mysql.ps1` 的可选套件参数与新套件调度；Create `deploy/private-chat.md`、`docs/private-chat-verification.md`；Modify `scripts/verify-schema.ps1` 以锁定新表/用户对/幂等索引。

**Interfaces:** 既有隔离脚本新增 `-Suite AccountRecovery|PrivateChat`（默认 AccountRecovery），保留原验收入口。新 wrapper 调用 PrivateChat；该分支使用 `coursehub_chat_check_<32hex>`、运行目录及 `private_chat_test_guard` nonce，并设置 `COURSEHUB_CHAT_MYSQL_URL/USER/PASSWORD/NONCE`。`-BrowserE2E` 在 PrivateChat 分支调度新 E2E runner；旧分支仍运行原 SMTP 浏览器验收。所有 SQL/删除/停进程继续核对本次实例、绝对路径、marker、PID 及启动时间；不提供现有服务器连接模式。

- [x] Step 1：添加真实 MySQL 用例：旧库合法重复、非法关系预检查及初始化后迁移可运行；双向申请、并发接受/解除、同一会话并发创建、同 ID 发送、发送/解除交错、发送/改密交错、顺序补拉、读游标竞争、限流并发与回滚。用 latch/独立 datasource 控制关键交错，给等待设置超时，不用偶然 sleep 证明并发。
  命名 `concurrentCreatesReuseOneConversation`、`sendAndUnfriendHaveAValidSerialization`、`passwordChangeRejectsAnOldWaitingSender`、`migrationStopsBeforeChangingInvalidRows`；并发创建结果断言：
  ```java
  assertEquals(first.get(10, TimeUnit.SECONDS).conversationId(),
      second.get(10, TimeUnit.SECONDS).conversationId());
  assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM chat_conversation", Integer.class));
  assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM chat_conversation_member", Integer.class));
  ```
- [x] Step 2：落实新套件分支和 MySQL 8 测试守卫；legacy fixture 从已验证基线 `f6ebb7c` 的 `backend/src/main/resources/db/init.sql` 复制并记录来源，不能使用已加新约束的 init 构造旧库。按 legacy fixture+account recovery migration+私聊 migration 验证增量路径，再按当前 init+全部迁移验证新库路径。非法旧关系必须在删除/DDL 前终止；合法清理结果、禁用用户保留及未受影响用户数据逐字段核对。记录引擎、collation、索引及实际 JUnit 零失败结果。
- [x] Step 3：运行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/verify-private-chat-mysql.ps1`；进程级 Bypass 不改变 Windows 策略。若 MySQL 暴露锁/SQL 错误，先定位并加入确定性失败用例，最小修复后复验受影响场景。
- [x] Step 4：E2E 启动仅绑定 127.0.0.1 的实际 Spring Boot 和生产构建前端，使用 guard 保护的临时账号 A/B/C（C 为非成员管理员）；两个独立浏览器登录、申请/接受、发收/分页/未读、解除后只读、重新加好友复用，直接 HTTP 校验 C 的 404；改密后旧 A 会话的读写/刷新拒绝及页面清理。此验收不启动 SMTP、不替换生产 API 响应、不访问业务库。
- [x] Step 5：运行同一 wrapper 加 `-BrowserE2E`，检查三主题手机/桌面与无外部请求；证据保存到 target/frontend output。失败和成功路径均确认所有自建进程/临时数据清理。因为复用了旧 runner，运行旧账号恢复 MySQL 套件和其浏览器入口确认默认行为仍正确。
- [x] Step 6：记录真实验证结果，只 stage 本任务文件并提交 `test: verify private chat with disposable MySQL and browsers`。

### Task 7: 最终验证、聚焦审查与交付

**Files:** Modify `docs/private-chat-verification.md`、`deploy/private-chat.md`、本计划执行勾选/记录；按审查发现修改对应任务文件和回归测试。

**Interfaces:** Consumes Task 1–6 的代码、JUnit/浏览器报告及迁移说明；Produces 可复验的提交、交付说明和实际通过记录。当前阶段不创建远端 PR、不自动推送功能分支；若用户后续要求同步，再核对远端处理。

- [x] Step 1：运行后端完整 `& $chatMaven test`；前端 `npm.cmd run test:contracts`、`test:auth`、`test:recovery`、`test:contact`、`test:chat`、`npm.cmd run build`；项目根 `scripts/verify-schema.ps1`。检查退出码、测试数与零失败，不引用过期结果。IT 通过 Task 6 的有 guard 命令显式运行，不静默跳过。
- [x] Step 2：对实际 diff 做一次独立聚焦审查，检查成员越权、用户锁顺序、提交后序号、幂等与额度、凭据上下文、JSON 最小投影、旧请求污染和新 UI 状态；发现重要问题先补失败回归再修复，仅复验受影响检查。
- [x] Step 3：核对所有设计要求对应完成任务；说明真实手机软键盘等未实测项。检查增量迁移备份、DDL 隐式提交、回退保留数据说明和环境示例，不含真实秘密或默认外部 SMTP。
- [x] Step 4：运行 `git diff --check` 并精确核对改动与 stage 列表，证据目录保持忽略。
- [x] Step 5：提交最终修复/验证记录，保存分支状态及提交号。
- [x] Step 6：展示正式 UI 和文档，清楚报告功能、测试、实际局限，以及业务迁移/部署仍由后续授权安排。

## 计划审查记录与下一阶段

2026-10-11：本计划对照设计检查了七个 API、四张新表、好友规范用户对、凭据写入复核、TEXT/JSON 扩展边界、未读与分页、轮询生命周期、三主题 UI、真实 MySQL 与双账号验收。Review Focus 五项均归属具体测试任务；公共方法/DTO/前端事件名称集中定义，后续任务引用一致。

2026-10-11：用户确认直接执行后，Task 1–6 已完成并分别提交。Task 7 的独立聚焦审查发现 1 项严重及 5 项重要问题，全部先补失败回归，再于一次修复批次解决；收尾补查的历史取消/重进失败忙标记也已补回归修复。最终后端 175 项、前端 42 项、私聊 MySQL 17 项、真实私聊浏览器 12 项、两个 UI 竞态和三主题 12 组合检查通过；原账号恢复 MySQL 26 项及浏览器 16 项复验通过。保留两项轻微界面问题及真机验证局限，实际结果见 ../../private-chat-verification.md。
