# Slay Demo 总指挥文档 v1

## 0. 项目重新定义

本项目不再以旧 `slaylab` 为基础修补，而是以 `slay-demo` 现有 battle 前端体验为唯一展示核心，从零搭建一个新的、满足课程要求的系统：

* **类型安全**
* **声明式**
* **微服务导向**
* **前后端强契约**
* **对象关系丰富**
* **能够支撑 4 分钟 demo 和同学互评**

本项目的本质不是“一个 battle 页面”，而是：

**一个以 battle 为核心，向外扩展 replay、forum、mails、rating、contribution、profile、admin moderation 的竞技游戏社区系统 demo。**

---

## 1. 硬约束与总原则

### 1.1 最高原则

1. **Battle 前端体验优先保留。**
   现有 `slay-demo` 的 battle 渲染、HUD、地图、即时战斗感，是本项目最值钱的资产，不允许先为了“架构纯洁”而重写。

2. **类型安全必须成为骨架，而不是口号。**
   所有对象、API、状态、身份、事件、数据库记录，都必须有明确且可组合的类型表达。

3. **声明式优先。**
   对象只描述“是什么”，不在对象层写主观行为。
   行为放在 planner / service / runtime / policy 层。

4. **微服务按边界先成立，再决定是否物理拆分。**
   当前代码仓库允许采用单仓多服务（microservice-oriented monorepo），但服务边界、数据边界、API 边界必须清晰，禁止跨服务直接乱读乱写。

5. **前后端必须围绕合同（contracts）设计。**
   前端不能自己发明后端状态；后端不能随意返回临时 JSON。

---

### 1.2 对象层原则

对象层（`objects`）只允许定义：

* `case class`
* `enum`
* `opaque type` 或 ID wrapper
* 无副作用数据结构

对象层禁止：

* 业务逻辑
* 状态修改函数
* 隐式副作用
* “方便临时写进去”的 def 行为方法

对象就是“鱼肉”，不是“主动者”。

---

### 1.3 API 层原则

API 层（`api`）只定义：

* request
* response
* summary
* view model
* event payload
* 外部合同

API 层禁止：

* 数据库存取
* 运行时副作用
* 业务决策
* 复杂转换逻辑

---

### 1.4 Route 层原则

Route 层（`routes`）只负责：

* 参数接收
* 调用 planner / service
* 返回类型安全响应
* HTTP 错误映射

Route 层必须薄，不能成为逻辑黑洞。

---

### 1.5 Database 层原则

每个服务只拥有自己负责的数据表。

禁止：

* battle 服务直接改 forum 表
* governance 服务直接改 battle 表
* forum 服务直接读 replay 私有数据表

跨服务协作只能通过：

* typed API
* typed event
* typed port / interface

---

## 2. 项目目标

## 2.1 演示目标

在 4 分钟 demo 中，系统必须让观众清晰看到：

1. 这是一个**真的能玩的 battle 系统**
2. 这不是孤立 battle，而是一个**有 replay / rating / mails / profile / forum / admin 的完整壳层**
3. 系统内部有**丰富对象关系与身份差异**
4. 设计满足课程强调的**类型安全、声明式、微服务边界**

---

## 2.2 产品目标

普通用户流程：

1. 注册 / 登录
2. 选择角色与技能
3. 进入匹配弹窗，10 秒倒计时
4. 进入 6 人 battle（含 bot）
5. 5 分钟限时或直到只剩最后一人
6. 返回主页
7. mails 红点提示 rating 更新
8. 用户可查看 replay、forum、profile、rating、contribution

管理员流程：

1. 管理员登录
2. mails 中看到举报 / 建议处理提醒
3. 点击链接跳到对应 replay / thread / comment / user
4. 进行处理
5. 可对用户进行 contribution 增减
6. 可用模板理由快速发站内邮件
7. 可通过“用户旁小黄点”触发快速管理动作

---

## 3. 前端页面树

前端路由最小集合如下：

```text
/
  首页，视频背景，主入口

/register
/login

/loadout
  角色与技能配置页面

/battle
  battle 主页面
  包括 queue modal, session ui, result return

/replay
  replay 图书馆列表页

/replay/:replayId
  replay 观看页

/discussion
  帖子列表页

/discussion/:threadId
  帖子详情页 + 评论区

/rating
  rating 榜单页

/contribution
  contribution 榜单页

/profile/:handle
  个人主页

/mails
  邮件总列表页
```

### 3.1 页面优先级

第一批必须完成：

* `/`
* `/loadout`
* `/battle`
* `/replay`
* `/replay/:replayId`
* `/rating`
* `/contribution`
* `/profile/:handle`
* `/mails`

第二批完成：

* `/discussion`
* `/discussion/:threadId`

管理员不需要独立 `/admin` 大后台页面，管理员能力优先嵌入：

* `/mails`
* `/replay/:replayId`
* `/discussion/:threadId`
* `/profile/:handle`

---

## 4. 后端总体架构

后端采用：

**单仓多服务 + 单 HTTP 入口 + 服务边界清晰 + 未来可物理拆分**

五个核心微服务如下：

1. `identity`
2. `battle`
3. `replay`
4. `forum`
5. `governance`

此外允许一个非业务顶层目录：

* `shared`

`shared` 只存放：

* 基础 ID 类型
* 通用错误类型
* 公共时间与分页结构
* 公共事件总线接口
* 公共基础枚举
* 通用 infra，不承载业务

---

## 5. backend 目录硬规范

后端目录从这里开始：

```text
backend/src/main/scala
```

然后必须是如下结构：

```text
backend/src/main/scala/
  shared/
    ids/
    primitives/
    errors/
    events/
    infra/
    utils/

  identity/
    api/
    objects/
    routes/
    database/
    planners/
    services/
    ports/
    policies/

  battle/
    api/
    objects/
    routes/
    database/
    planners/
    services/
    ports/
    runtime/
    policies/

  replay/
    api/
    objects/
    routes/
    database/
    planners/
    services/
    ports/
    policies/

  forum/
    api/
    objects/
    routes/
    database/
    planners/
    services/
    ports/
    policies/

  governance/
    api/
    objects/
    routes/
    database/
    planners/
    services/
    ports/
    policies/
```

### 5.1 各目录职责

#### `objects/`

纯对象定义：

* case class
* enum
* ID wrapper
* 领域对象
* 数据记录对象

#### `api/`

外部合同：

* request
* response
* summary
* view
* event payload DTO

#### `routes/`

HTTP 路由入口：

* route registration
* parameter parsing
* planner invocation
* response encoding

#### `database/`

数据库层：

* table row model
* repository interface implementation
* SQL mapper
* migration hook
* persistence adapter

#### `planners/`

用例编排层：

* 一个请求如何协调多个 service / repository / policy
* 纯业务流转决策

#### `services/`

更小粒度领域服务：

* rating calculator
* mail composer
* replay summarizer
* performance evaluator
* vote validator

#### `ports/`

跨服务接口：

* `BattleToGovernancePort`
* `ForumToGovernancePort`
* `ReplayToGovernancePort`
* `IdentityLookupPort`

#### `policies/`

规则与权限：

* admin 权限
* report 提交限制
* vote 限制
* contribution 调整规则

#### `runtime/`

仅 `battle` 服务专有：

* tick engine
* bot engine
* session state machine
* simulation driver

---

## 6. 五个微服务职责

## 6.1 identity 服务

负责：

* 用户注册
* 用户登录
* 用户基本身份
* 角色与技能配置（loadout）
* 头像、handle、role
* 基本 profile 信息
* admin / player / bot 身份区分

不负责：

* rating 计算
* contribution 计算
* replay 存储
* forum 内容
* moderation 决策

### identity 核心对象

* `UserId`
* `Handle`
* `UserRole`
* `AvatarRef`
* `PlayerProfile`
* `AccountCredential`
* `LoadoutConfig`
* `SelectedHero`
* `SelectedSkillSet`
* `IdentitySession`

### identity 核心 API

* register
* login
* get profile
* update loadout
* list user basic public cards
* resolve handle to user

---

## 6.2 battle 服务

负责：

* 匹配倒计时
* 填充 bot
* 生成 battle session
* 战斗 runtime
* 接收输入
* 推进 tick
* 计算死亡顺序
* 计算胜者
* 计算 performance
* 产出 battle result
* 触发 replay / rating / mails 事件

不负责：

* replay 图书馆 UI
* forum 讨论
* 管理员处理
* mails 存储
* contribution 调整

### battle 核心对象

* `QueueTicketId`
* `BattleSessionId`
* `BattlePhase`
* `BattleMode`
* `BattleParticipant`
* `HumanParticipant`
* `BotParticipant`
* `PlayerCommand`
* `BattleSnapshot`
* `PlayerView`
* `ProjectileView`
* `PickupView`
* `HudStateView`
* `BattleOutcome`
* `DeathOrderEntry`
* `PerformanceSummary`

### battle 核心规则

* 匹配倒计时 10 秒
* session 总人数 6
* 人数不足时自动补 bot
* session 限时 5 分钟
* 胜利条件：只剩一人或时间结束后根据存活/分数规则裁定
* session 结束后生成 typed result event

---

## 6.3 replay 服务

负责：

* replay metadata
* replay list
* replay detail
* replay timeline
* replay screenshot
* replay 的 suggestion / report 提交入口
* replay 内容的可浏览化

不负责：

* 真的运行 battle
* 计算 rating
* 论坛帖子
* admin 处罚决策

### replay 核心对象

* `ReplayId`
* `ReplaySummary`
* `ReplayDetail`
* `ReplayThumbnailRef`
* `ReplayTimeline`
* `ReplayOwnerRef`
* `ReplayParticipantRef`
* `ReplayReportSubmission`
* `ReplaySuggestionSubmission`

---

## 6.4 forum 服务

负责：

* 帖子列表
* 帖子详情
* 发帖
* 评论
* 帖子/评论 up/down
* 帖子/评论 report 提交
* 作者信息引用

不负责：

* admin 最终审核
* contribution 最终增减
* battle 结算
* replay 真数据生成

### forum 核心对象

* `ThreadId`
* `ThreadTitle`
* `ThreadBody`
* `ForumThread`
* `CommentId`
* `ForumComment`
* `VoteType`
* `ThreadVote`
* `CommentVote`
* `ThreadReportSubmission`
* `CommentReportSubmission`

---

## 6.5 governance 服务

负责：

* rating 榜单
* contribution 榜单
* mails
* moderation
* admin 决策
* notification
* report / suggestion 的处理状态
* contribution 增减记录
* rating 更新记录
* 模板理由
* profile 页面中的 progression 数据

不负责：

* 用户注册
* battle runtime
* forum 内容实体本身
* replay 实际文件与时间轴

### governance 核心对象

* `MailId`
* `MailType`
* `MailStatus`
* `UserMail`
* `RatingValue`
* `RatingDelta`
* `RatingHistoryPoint`
* `ContributionValue`
* `ContributionDelta`
* `ContributionAdjustment`
* `ModerationCaseId`
* `ModerationTarget`
* `ModerationDecision`
* `ReasonTemplate`
* `AdminAction`
* `NotificationLink`

---

## 7. shared 顶层基础类型

`shared` 层定义全局基础类型，但不放业务对象。

必须包括：

* `UserId`
* `ReplayId`
* `BattleSessionId`
* `ThreadId`
* `CommentId`
* `MailId`
* `ModerationCaseId`

通用基础对象：

* `Timestamp`
* `Pagination`
* `PageRequest`
* `PageResponse[T]`
* `ServiceError`
* `DomainError`
* `PermissionError`
* `NotFoundError`
* `ValidationError`

通用事件：

* `BattleFinishedEvent`
* `ReplayStoredEvent`
* `ReportSubmittedEvent`
* `SuggestionSubmittedEvent`
* `RatingUpdatedEvent`
* `ContributionAdjustedEvent`
* `MailCreatedEvent`
* `ModerationResolvedEvent`

---

## 8. 前后端契约设计原则

### 8.1 合同优先

所有前端页面都必须围绕 typed contracts 设计，而不是围绕“页面想显示什么就临时拼什么”。

### 8.2 Battle 合同

battle 页面只允许消费：

* `JoinQueueRequest`
* `JoinQueueResponse`
* `QueueStatusResponse`
* `StartBattleResponse`
* `SubmitCommandRequest`
* `BattleSnapshotResponse`
* `BattleResultResponse`

battle 页面内部必须通过 adapter 层把 API DTO 转成 battle renderer 使用的本地 view model。

不允许 battle renderer 直接依赖后端乱 JSON。

### 8.3 Replay 合同

* replay list 页面只消费 `ReplaySummary`
* replay detail 页面消费 `ReplayDetail`
* report / suggestion 走明确 request

### 8.4 Governance 合同

* mails、rating、contribution、moderation 全部使用 governance service 提供的统一 DTO
* profile 页面中的 rating history / contribution summary / recent match history 由前端组合多个服务响应，不允许由 battle 服务硬塞 profile 全量数据

---

## 9. 数据边界与数据库建议

建议一个 Postgres，五个 schema：

```text
identity
battle
replay
forum
governance
```

这样既保留微服务边界，又避免本地开发复杂度爆炸。

### 9.1 identity schema

表建议：

* `users`
* `credentials`
* `profiles`
* `loadouts`
* `identity_sessions`

### 9.2 battle schema

表建议：

* `queue_tickets`
* `battle_sessions`
* `battle_participants`
* `battle_results`
* `performance_records`

### 9.3 replay schema

表建议：

* `replays`
* `replay_participants`
* `replay_timeline_chunks`
* `replay_reports`
* `replay_suggestions`

### 9.4 forum schema

表建议：

* `threads`
* `comments`
* `thread_votes`
* `comment_votes`
* `thread_reports`
* `comment_reports`

### 9.5 governance schema

表建议：

* `mails`
* `rating_history`
* `contribution_history`
* `moderation_cases`
* `moderation_decisions`
* `reason_templates`
* `admin_actions`

---

## 10. 关键用户流

## 10.1 用户注册与进入 battle

1. 用户注册
2. 用户登录
3. 进入 `/loadout`
4. 选择 hero 与 skill set
5. 提交 loadout
6. 进入 `/battle`
7. 点击开始匹配
8. battle 服务创建 `QueueTicket`
9. 前端显示 10 秒倒计时 modal
10. 倒计时结束或人数满足时生成 session
11. battle 进入运行态
12. 前端轮询或 websocket 获取 snapshot
13. battle 结束
14. battle 服务生成 result
15. 触发 `BattleFinishedEvent`

---

## 10.2 battle 结束后的后续流

1. battle 服务发出 `BattleFinishedEvent`
2. replay 服务生成 replay metadata
3. governance 服务更新 rating history
4. governance 服务生成用户 mails
5. 用户返回主页
6. mails 红点亮起
7. 用户进入 mails 查看 rating 更新通知

---

## 10.3 replay 举报 / 建议流

1. 用户进入 `/replay`
2. 点击某条 replay 的举报或建议
3. 填文字，send
4. replay 服务创建 submission
5. governance 服务接收事件，创建 moderation case
6. 管理员 mails 出现提醒
7. 管理员点击进入目标 replay
8. 管理员做处理
9. governance 服务写入决策
10. 如有需要，修改 contribution
11. 给用户发处理结果邮件

---

## 10.4 forum 举报流

1. 用户进入 thread 或 comment
2. 点击 report
3. forum 服务记录 report
4. governance 创建 moderation case
5. 管理员 mails 收到提醒
6. 管理员点链接直达对应 thread/comment
7. 管理员处理并给出处置结果

---

## 10.5 管理员小黄点快速处理流

1. 管理员在 replay / discussion / profile 中看到用户旁小黄点
2. 点击小黄点
3. 弹窗默认带当前页面链接
4. 选择 contribution 调整按钮
5. 选择模板理由或补充文字
6. 提交后 governance 写入：

   * admin action
   * contribution history
   * mail to user

---

## 11. Demo 强制讲述顺序

推荐 4 分钟演示顺序：

### 第一段：主页

* 视频背景
* 统一风格
* 展示 Battle / Replay / Rating / Discussion / Profile / Mails 入口

### 第二段：Loadout + Battle

* 角色选择
* 技能选择
* 10 秒匹配弹窗
* 进入 battle
* 展示 HUD、移动、战斗、击杀、计时、结束

### 第三段：Mails + Rating

* 返回主页
* 右下角 mails 红点
* 点开收到本局 rating 更新
* 展示榜单

### 第四段：Replay

* 打开 replay library
* 看到截图、6 名玩家、颜色与死亡顺序
* 点开单局 replay 观看页

### 第五段：Forum / Admin

* 展示 forum 列表或帖子详情
* 展示用户 report/suggestion
* 切管理员身份
* mails 收到处理提醒
* 点击进入目标内容
* 使用小黄点或处理面板进行 contribution 调整
* 用户收到处理结果邮件

### 第六段：Profile

* 展示 `/profile/:handle`
* rating / contribution / performance / history / 曲线

---

## 12. Battle 与现有前端的关系

现有 `slay-demo` battle 前端处理原则：

1. **视觉不先动**
2. **battle renderer 是展示资产**
3. **要在其外部包 typed adapter**
4. **先兼容 battle 当前前端的数据结构，再逐步替换成真实 typed snapshot**
5. **禁止把 battle 现有逻辑直接复制成后端对象层逻辑**

battle 前端保留：

* canvas / scene / HUD / minimap / skill slots / weapon panel / match timer 等表现

battle 前端需要新增：

* API adapter
* queue modal state
* session lifecycle
* result return hook
* typed DTO mapping

---

## 13. Scala 命名与实现规范

### 13.1 命名

* case class / enum：`PascalCase`
* 字段：`camelCase`
* DTO 后缀：

  * `Request`
  * `Response`
  * `Summary`
  * `View`
  * `Event`
* planner 后缀：`Planner`
* repository 后缀：`Repository`
* service 后缀：`Service`
* route 后缀：`Routes`
* policy 后缀：`Policy`

### 13.2 错误处理

优先：

* `Either[DomainError, A]`
* `IO[Either[DomainError, A]]` 或等价 effect 风格

避免：

* 随意抛异常
* route 内 try-catch 大杂烩

### 13.3 状态表达

使用：

* Scala 3 enum
* sealed trait ADT

禁止：

* magic string
* `"admin"` / `"user"` / `"bot"` 到处手写

---

## 14. 第一阶段开发目标

第一阶段只做框架，不做大而全实现。

必须完成：

1. 五个微服务目录搭好
2. shared 基础类型搭好
3. 每个服务至少有：

   * health route
   * 1~3 个核心 request/response DTO
   * planner skeleton
   * repository interface
   * stub database adapter
4. battle 服务先跑通：

   * join queue
   * start session
   * submit command
   * get snapshot
   * finish session
5. governance 服务先跑通：

   * get mails
   * get rating leaderboard
   * get contribution leaderboard
6. identity 服务先跑通：

   * register
   * login
   * get profile
   * save loadout
7. replay 服务先跑通：

   * replay list
   * replay detail
   * submit replay report/suggestion
8. forum 服务先跑通：

   * thread list
   * thread detail
   * create thread
   * create comment

---

## 15. 第二阶段开发目标

1. battle 与前端接通
2. replay 列表接通
3. mails 红点接通
4. rating / contribution 榜单接通
5. profile 页组合 identity + governance + recent match history
6. admin moderation 基础链路接通

---

## 16. 明确不做的事情

当前 demo 阶段不追求：

* 完整真实分布式部署
* 真正复杂权限系统
* 真正复杂分页与搜索
* 复杂 replay 压缩格式
* 高智能 bot AI
* 复杂推荐排序
* 完整外部邮件系统
* 大型后台管理面板

只追求：

* 可展示
* 类型正确
* 边界清楚
* 交互闭环存在
* battle 足够像样

---

## 17. 给 Codex 的最高命令

Codex 必须始终遵守：

1. 不得推翻 battle 当前视觉资产
2. 不得把对象层写成充满逻辑的方法容器
3. 不得绕过微服务边界跨库乱读
4. 不得先做无关页面而拖慢 battle 对接
5. 必须优先搭框架、合同、边界，再补实现
6. 每次任务必须输出：

   * 改动文件
   * 新增对象
   * 新增接口
   * 是否 compile 通过
   * 下一步建议

---
