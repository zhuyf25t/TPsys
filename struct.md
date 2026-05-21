# 后端运行结构审计

本文只分析当前后端运行时真正起作用的代码、当前 8080 请求链完全用不到的代码、以及重复逻辑。判断依据以当前机器上的运行进程、启动脚本和 `backend/src/main` 源码为准。

## 1. 结论

当前 8080 上运行的后端不是旧的 `BackendApp`，而是 http4s 入口：

```text
npm run backend:dev
  -> cd backend && sbt "runMain slaydemo.backend.http4s.BackendHttp4sApp"
  -> backend/src/main/scala/slaydemo/backend/http4s/BackendHttp4sApp.scala
```

关键结论：

| 问题 | 结论 |
| --- | --- |
| 真正在跑的是不是 `src/main` 代码 | 是。入口类在 `backend/src/main/scala/slaydemo/backend/http4s/BackendHttp4sApp.scala`，不是 `src/test`。 |
| 真正在跑的是不是旧 `BackendApp` | 不是。旧入口 `slaydemo.backend.BackendApp` 仍存在，但当前 8080 进程没有使用它。 |
| 真正在跑的是不是 Git 的 `main` 分支 | 不是。当前工作分支是 `multimodule`；具体 HEAD 会随提交变化，用 `git log -1 --oneline` 查看。 |
| 当前服务入口 | `slaydemo.backend.http4s.BackendHttp4sApp`。 |
| 当前 HTTP 框架 | http4s + Ember server + cats-effect `IO`。 |
| 当前存储模式 | `/api/health` 返回 `storageMode = postgres`，所以当前实际运行使用 Postgres 仓库实现。 |

所以需要把三个概念分开：

1. 运行入口：当前 8080 入口是 `BackendHttp4sApp`，不是旧 `BackendApp`。
2. 源码层级：当前运行的是 `backend/src/main` 下的正式后端源码，不是 `backend/src/test`。
3. Git 分支差异：当前工作区在 `multimodule` 分支上，不在 Git 的 `main` 分支上；这只影响版本来源，不代表运行了测试代码。

## 2. 当前真正起作用的运行链

当前 8080 进程命令行显示：

```text
runMain slaydemo.backend.http4s.BackendHttp4sApp
```

运行链如下：

```text
BackendHttp4sApp.run
  -> BackendEnvironment.load()
  -> BackendRuntime.fromEnvironment(env)
  -> BackendRepositories.fromStorage(config.storage)
  -> BackendHttp4sRoutes.backendRoutes(...)
  -> EmberServerBuilder.default[IO]
  -> .withHost(0.0.0.0)
  -> .withPort(config.port)
  -> .withHttpApp(httpApp)
```

真正接收 HTTP 请求的是：

| 层级 | 起作用的代码 |
| --- | --- |
| 进程入口 | `backend/src/main/scala/slaydemo/backend/http4s/BackendHttp4sApp.scala` |
| 运行时装配 | `backend/src/main/scala/slaydemo/backend/BackendRuntime.scala` |
| 仓库装配 | `backend/src/main/scala/slaydemo/backend/BackendRepositories.scala`、`BackendLiveRepositoryFactories.scala` |
| http4s 总路由 | `backend/src/main/scala/slaydemo/backend/http4s/BackendHttp4sRoutes.scala` |
| http4s 通用支持 | `backend/src/main/scala/slaydemo/backend/http4s/Http4sRouteSupport.scala` |

## 3. 当前真正处理请求的 http4s route

这些文件是当前 8080 请求链上的 route adapter：

| 文件 | 当前作用 |
| --- | --- |
| `http4s/HealthHttp4sRoutes.scala` | 健康检查。 |
| `http4s/IdentityHttp4sRoutes.scala` | 注册、登录、当前用户、账号列表。 |
| `http4s/MailHttp4sRoutes.scala` | 邮件列表和已读操作。 |
| `http4s/SocialHttp4sRoutes.scala` | 好友请求创建、查询、响应。 |
| `http4s/ForumHttp4sRoutes.scala` | 讨论区 topic、reply、vote。 |
| `http4s/GovernanceHttp4sRoutes.scala` | 贡献调整和治理通知。 |
| `http4s/ReplayHttp4sRoutes.scala` | replay catalog、详情、评论。 |
| `http4s/BotProfileHttp4sRoutes.scala` | bot profile 列表。 |
| `http4s/BattleQueueHttp4sRoutes.scala` | battle 排队 join/status/leave。 |
| `http4s/BattleRoomHttp4sRoutes.scala` | 房间 snapshot/heartbeat。 |
| `http4s/BattleStateHttp4sRoutes.scala` | battle state 读取和 SSE stream。 |
| `http4s/BattleCommandHttp4sRoutes.scala` | battle command 提交。 |
| `http4s/BattleResultHttp4sRoutes.scala` | battle result 查询和写入。 |

这些 route 共同由 `BackendHttp4sRoutes.backendRoutes(...)` 组合成一个 `HttpRoutes[IO]`，然后 `.orNotFound` 交给 Ember server。

## 4. 当前真正起作用的 service/domain 代码

`BackendRuntime.fromEnvironment` 当前会实例化这些业务服务：

| 服务 | 当前作用 |
| --- | --- |
| `StaticHealthService` | 生成健康检查响应。 |
| `DefaultIdentityService` | 注册、登录、session、账号列表。 |
| `InMemoryBattleQueueService` | 当前 battle 排队、房间等待、ticket、room snapshot。 |
| `DefaultBattleQueueJoinAuthorizationService` | 检查 battle join 是否拥有合法 identity/session。 |
| `InMemoryBattleStateService` | 当前 battle authoritative state、tick 推进、command 接收和应用。 |
| `DefaultBattleResultService` | battle result 查询和写入。 |
| `DefaultBattleFinishProjector` | battle 结束后投影 result、replay、mail。 |
| `DefaultReplayService` | replay catalog、详情、评论。 |
| `DefaultMailService` | 邮件查询和已读。 |
| `DefaultBotProfileService` | bot profile 查询。 |
| `DefaultFriendRequestService` | 好友请求和通知邮件。 |
| `DefaultForumService` | forum topic/reply/vote。 |
| `DefaultGovernanceService` | 贡献调整和治理通知。 |

Battle 下的这些 service 子域是真正参与当前游戏逻辑的：

| battle service 子域 | 当前作用 |
| --- | --- |
| `services/queue` | 排队、房间、ticket、heartbeat、room lifecycle。 |
| `services/session` | battle session、命令接收、当前状态读取、stored battle。 |
| `services/runtime` | tick 推进、结束判定、event、聚合状态更新。 |
| `services/world` | 地图尺寸、出生点、碰撞、移动规则。 |
| `services/combat` | 武器、射击、projectile、命中、伤害、terminal。 |
| `services/actors` | 玩家输入、bot、玩家生命周期和运行时更新。 |
| `services/abilities` | 技能、pickup、slow field。 |
| `services/results` | result、settlement、replay、history、结束投影。 |

这些 service 虽然很多，但不是“微服务”。它们是同一个 JVM 进程内的业务模块。它们通过 Scala package 和类型组织，没有进程隔离、网络边界或独立部署边界。

## 5. 当前真正起作用的存储代码

当前 `/api/health` 返回：

```json
{
  "status": "ok",
  "service": "slay-demo-backend",
  "port": 8080,
  "storageMode": "postgres"
}
```

所以当前运行时通过 `BackendRepositories.fromStorage(StorageConfig.Postgres(...))` 使用 Postgres 实现：

| Repository 接口 | 当前实际实现 |
| --- | --- |
| `IdentityAccountRepository` | `PostgresIdentityAccountRepository` |
| `BattleResultRepository` | `PostgresBattleResultRepository` |
| `MailRepository` | `PostgresMailRepository` |
| `BotProfileRepository` | `PostgresBotProfileRepository` |
| `ReplayRepository` | `PostgresReplayRepository` |
| `FriendRequestRepository` | `PostgresFriendRequestRepository` |
| `ForumRepository` | `PostgresForumRepository` |
| `GovernanceRepository` | `PostgresGovernanceRepository` |

`InMemory*Repository` 和 `File*Repository` 当前这次运行没有被使用，但不能简单定义为永久废代码。它们是配置模式分支：

| 代码 | 当前运行是否使用 | 是否可以直接删 |
| --- | --- | --- |
| `Postgres*Repository` | 使用中 | 不可以。 |
| `InMemory*Repository` | 当前未使用 | 不建议直接删，测试和内存模式可能依赖。 |
| `File*Repository` | 当前未使用 | 不建议直接删，file 模式和文件迁移测试可能依赖。 |
| `*FileJsonParser` / `*FileJsonRenderer` | 当前 postgres 模式未使用 | 只有确认不再支持 file 模式后才可删。 |

## 6. 当前 8080 请求链完全用不到的代码

以下代码不在当前 8080 的 http4s 请求链上。它们仍可能被编译、被测试引用，或被旧入口引用；因此“当前运行不用”不等于“可以不验证直接删除”。

### 6.1 旧 Java HttpServer 入口

| 文件 | 当前状态 |
| --- | --- |
| `BackendApp.scala` | 旧 `com.sun.net.httpserver.HttpServer` 入口；当前 8080 没有运行它。 |
| `BackendRouteRegistry.scala` | 旧入口注册 `HttpExchange => Unit` handler 用；当前 http4s 入口不用。 |
| `BackendRouteCatalog.scala` | 旧入口 route context 清单；当前 http4s 入口不用。 |

当前 `package.json` 里仍保留：

```json
"backend:dev:legacy": "cd backend && sbt \"runMain slaydemo.backend.BackendApp\""
```

所以旧入口还不是“代码层面完全不可达”，但它不是当前实际服务入口。

### 6.2 旧 `HttpExchange` route wrapper

这些文件代表旧 Java HttpServer route 层，不是当前 http4s 运行时接收请求的代码：

| 旧 route 文件 | 当前 8080 是否处理请求 |
| --- | --- |
| `shared/routes/HealthRoutes.scala` | 否。当前使用 `HealthHttp4sRoutes`。 |
| `identity/routes/IdentityRoutes.scala` | 否。当前使用 `IdentityHttp4sRoutes`。 |
| `mail/routes/MailRoutes.scala` | 否。当前使用 `MailHttp4sRoutes`。 |
| `social/routes/SocialRoutes.scala` | 否。当前使用 `SocialHttp4sRoutes`。 |
| `forum/routes/ForumRoutes.scala` | 否。当前使用 `ForumHttp4sRoutes`。 |
| `governance/routes/GovernanceRoutes.scala` | 否。当前使用 `GovernanceHttp4sRoutes`。 |
| `replay/routes/ReplayRoutes.scala` | 否。当前使用 `ReplayHttp4sRoutes`。 |
| `bots/routes/BotProfileRoutes.scala` | 否。当前使用 `BotProfileHttp4sRoutes`。 |
| `battle/routes/BattleRoutes.scala` | 否。当前使用 `BattleQueue/Room/State/Command/ResultHttp4sRoutes`。 |
| `battle/routes/BattleCommandRouteHandler.scala` | 否。当前使用 `BattleCommandHttp4sRoutes`。 |
| `battle/routes/BattleQueueRouteHandler.scala` | 否。当前使用 `BattleQueueHttp4sRoutes`。 |
| `battle/routes/BattleRoomRouteHandler.scala` | 否。当前使用 `BattleRoomHttp4sRoutes`。 |
| `battle/routes/BattleStateRouteHandler.scala` | 否。当前使用 `BattleStateHttp4sRoutes`。 |
| `battle/routes/BattleStateStreamWriter.scala` | 否。当前 http4s SSE 在 `BattleStateHttp4sRoutes` 内实现。 |

### 6.3 不能直接整包删除的 `routes` 目录

注意：`routes` 目录里不是所有文件都能删。当前 http4s route 仍复用了部分 parser、target parser、error mapper：

| 仍被 http4s 使用的旧 routes 支撑文件 | 原因 |
| --- | --- |
| `IdentityCommandParsers.scala` | `IdentityHttp4sRoutes` 仍用它解析注册/登录命令。 |
| `IdentitySessionTokenParser.scala` | `IdentityHttp4sRoutes` 仍用它解析 session token。 |
| `MailCommandParsers.scala` | `MailHttp4sRoutes` 仍用它解析 owner/read command。 |
| `SocialCommandParsers.scala` | `SocialHttp4sRoutes` 仍用它解析好友请求命令。 |
| `ForumCommandParsers.scala` | `ForumHttp4sRoutes` 仍用它解析 topic/reply/vote 命令。 |
| `ForumRouteTargetParsers.scala` | `ForumHttp4sRoutes` 仍用它识别 topic/reply/vote path。 |
| `ForumRouteErrorMapper.scala` | `ForumHttp4sRoutes` 仍用它映射 service/parse error。 |
| `GovernanceCommandParsers.scala` | 已迁到 `governance/objects/apiTypes`，`GovernanceHttp4sRoutes` 仍用它解析治理命令。 |
| `ReplayCommandParsers.scala` | 已迁到 `replay/objects/apiTypes`，`ReplayHttp4sRoutes` 仍用它解析 replay id、record、comment。 |
| `ReplayJsonObjectParser.scala` | 已迁到 `replay/objects/apiTypes`，`ReplayHttp4sRoutes` 仍用它解析 replay JSON body。 |
| `BattleResultApiCodec.scala` | `BattleResultHttp4sRoutes` 仍用它解析 result query/body。 |

结论：可以清理旧 route wrapper，但不能粗暴删除整个 `routes` 目录。正确方向是先把仍被 http4s 复用的 parser/codec 从 `routes` 下沉或迁移到 `objects/apiTypes`、`api`、`support` 等非 route 包，再删除真正的 `HttpExchange` route wrapper。

## 7. 当前逻辑重复的位置

### 7.1 HTTP route 重复

同一个业务接口目前存在两套路由实现：

| 业务 | 当前实际运行 | 旧重复实现 |
| --- | --- | --- |
| health | `HealthHttp4sRoutes` | `HealthRoutes` |
| identity | `IdentityHttp4sRoutes` | `IdentityRoutes` |
| mail | `MailHttp4sRoutes` | `MailRoutes` |
| social | `SocialHttp4sRoutes` | `SocialRoutes` |
| forum | `ForumHttp4sRoutes` | `ForumRoutes`、`ForumMutationRouteHandler` |
| governance | `GovernanceHttp4sRoutes` | `GovernanceRoutes` |
| replay | `ReplayHttp4sRoutes` | `ReplayRoutes` |
| bot profile | `BotProfileHttp4sRoutes` | `BotProfileRoutes` |
| battle queue | `BattleQueueHttp4sRoutes` | `BattleQueueRouteHandler` |
| battle room | `BattleRoomHttp4sRoutes` | `BattleRoomRouteHandler` |
| battle state | `BattleStateHttp4sRoutes` | `BattleStateRouteHandler`、`BattleStateStreamWriter` |
| battle command | `BattleCommandHttp4sRoutes` | `BattleCommandRouteHandler` |
| battle result | `BattleResultHttp4sRoutes` | `BattleResultRoutes` |

风险：同一个 API contract 有两处行为来源，后续改字段、状态码、错误码时容易只改一边。

### 7.2 JSON 解析和渲染重复

当前存在两套 JSON 风格：

| 风格 | 文件 | 当前问题 |
| --- | --- | --- |
| 旧手写字符串 JSON | `shared/routes/HttpRouteSupport.scala`、`battle/routes/BattleStateJson.scala`、多个 `*JsonRenderer.scala` | 容易漏字段、转义错误、和 Circe DTO drift。 |
| typed DTO + Circe | `objects/apiTypes/*ApiTypes.scala`、`http4s/*Routes.scala` | 当前运行路径正在使用，但还没有完全替代旧手写 route JSON。 |

最明显的重复：

| 旧实现 | 新实现 |
| --- | --- |
| `battle/routes/BattleStateJson.scala` | `battle/objects/apiTypes/BattleStateApiTypes.scala` |
| `battle/routes/BattleCommandRequestParser.scala` | `battle/objects/apiTypes/BattleCommandApiTypes.scala` |
| `battle/routes/BattleQueueRoomJsonRenderer.scala` | `battle/objects/apiTypes/BattleQueueApiTypes.scala` |
| `battle/routes/BattleResultCommandParsers.scala` + manual route response | `battle/objects/apiTypes/BattleResultApiTypes.scala` + `BattleResultHttp4sRoutes` |
| `shared/routes/HttpRouteSupport.sendJsonError` | `http4s/Http4sRouteSupport.apiError` |

风险：测试通过某一路径不等于另一条路径正确。比如旧 `BattleCommandRequestParser` 正确，不能证明当前运行的 `BattleCommandAPIRequest.decode` 正确；反过来也一样。

### 7.3 路径兼容表重复

旧入口把兼容路径集中在：

```text
BackendRouteRegistry.scala
BackendRouteCatalog.scala
```

http4s 入口把兼容路径散在每个 route 内：

```text
BattleQueueHttp4sRoutes.AllowedStatusPaths
BattleCommandHttp4sRoutes.AllowedPaths
BattleStateHttp4sRoutes.AllowedReadPaths
...
```

风险：Vite 代理会把 `/api/...` rewrite 成无 `/api` 的路径；如果只在一套路由表里补路径，另一套就可能 drift。当前 battle proxy-stripped path 已有 contract 测试覆盖，但结构上仍重复。

### 7.4 `BackendRuntime` 和旧 route 的装配边界

已修正：`BackendRuntime.fromEnvironment` 当前只装配 config、service、repository 相关运行时对象，不再实例化旧 `HttpExchange` route 对象。

旧 Java HttpServer 入口需要这些 route 时，会在 `BackendApp.legacyRouteHandlers(runtime)` 内本地构造：

```text
BackendApp.legacyRouteHandlers(runtime)
  -> HealthRoutes(runtime.healthService)
  -> IdentityRoutes(runtime.identityService)
  -> BattleRoutes(runtime.battleQueueService, runtime.battleStateService, ...)
...
```

`BackendHttp4sApp` 仍只把 service 传给 `BackendHttp4sRoutes.backendRoutes(...)`。这意味着当前 http4s 运行对象图已经不再间接初始化旧 route wrapper。

剩余风险：旧 route wrapper 仍存在，并且 legacy tests 仍覆盖它们；只是它们已经从当前 http4s runtime 对象图中移出。

## 8. 可以清理但需要分阶段做的代码

不要一次性删除。建议按这个顺序：

### Phase A：拆分 runtime 装配，先消除误导

状态：已完成。

```text
BackendRuntime 只保留 service/repository/runtime state
BackendApp.legacyRouteHandlers(runtime) 内部再创建旧 HttpExchange routes
BackendHttp4sApp 不再间接构造旧 route object
```

当前代码已经可以证明：当前 http4s 运行对象图里没有旧 route。

### Phase B：把仍被 http4s 复用的 parser 从 `routes` 迁出去

状态：进行中。已完成 battle result 这一组 API codec/parser 迁移。

迁移方向：

| 状态 | 当前位置 | 建议位置 |
| --- | --- | --- |
| 已完成 | `identity/routes/IdentityCommandParsers.scala` | `identity/api/IdentityCommandParsers.scala` |
| 已完成 | `mail/routes/MailCommandParsers.scala` | `mail/objects/apiTypes/MailCommandParsers.scala` |
| 已完成 | `social/routes/SocialCommandParsers.scala` | `social/objects/apiTypes/SocialCommandParsers.scala` |
| 已完成 | `governance/routes/GovernanceCommandParsers.scala` | `governance/objects/apiTypes/GovernanceCommandParsers.scala` |
| 待迁移 | `forum/routes/ForumCommandParsers.scala` | `forum/objects/apiTypes` |
| 已完成 | `replay/routes/ReplayCommandParsers.scala` | `replay/objects/apiTypes/ReplayCommandParsers.scala` |
| 已完成 | `replay/routes/ReplayJsonObjectParser.scala` | `replay/objects/apiTypes/ReplayJsonObjectParser.scala` |
| 已完成 | `battle/routes/BattleResultApiCodec.scala` | `battle/objects/apiTypes/BattleResultApiCodec.scala` |
| 已完成 | `battle/routes/BattleResultCommandParsers.scala` | `battle/objects/apiTypes/BattleResultCommandParsers.scala` |
| 已完成 | `battle/routes/BattleResultJsonObjectParser.scala` | `battle/objects/apiTypes/BattleResultJsonObjectParser.scala` |

迁完后，`routes` 包才可以更明确地表示“旧 HttpExchange adapter”。

### Phase C：删除旧 `HttpExchange` route wrapper

前提：

1. `backend:dev:legacy` 已确认不再需要。
2. contract tests 已迁到 http4s 路径。
3. `BackendRouteCatalog/Registry` 的覆盖测试不再作为主路径测试。
4. 所有当前前端请求路径都有 http4s composition test 覆盖。

可删候选：

```text
BackendApp.scala
BackendRouteRegistry.scala
BackendRouteCatalog.scala
shared/routes/HealthRoutes.scala
shared/routes/HttpRouteSupport.scala
各 domain/routes/*Routes.scala
各 domain/routes/*RouteHandler.scala
battle/routes/BattleStateStreamWriter.scala
```

### Phase D：删除旧手写 JSON renderer

前提是对应 typed DTO + Circe encoder 已完全覆盖字段和 contract test：

```text
battle/routes/BattleStateJson.scala
battle/routes/BattlePlayerStateJsonRenderer.scala
battle/routes/BattleEntityStateJsonRenderer.scala
battle/routes/BattleQueueRoomJsonRenderer.scala
其他只服务旧 route 的 *JsonRenderer
```

## 9. 当前不能简单删除的代码

| 代码 | 原因 |
| --- | --- |
| `backend/src/main/scala/slaydemo/backend/battle/services/**` | 这是当前 battle authoritative runtime 的核心逻辑，不是微服务垃圾代码。 |
| `backend/src/main/scala/slaydemo/backend/battle/objects/**` | 当前 API DTO、状态、枚举、值对象都依赖这里。 |
| `objects/apiTypes/**` | 当前 http4s route 的 typed contract 层正在使用。 |
| `Postgres*Repository` | 当前 postgres 运行模式正在使用。 |
| `InMemoryBattleQueueService`、`InMemoryBattleStateService` | 名字带 InMemory，但当前 battle 排队和 battle state runtime 实际就是它们。 |
| `*CommandParsers`、`*RouteTargetParsers`、`*RouteErrorMapper` 中被 http4s import 的部分 | 还在当前 http4s 路径被调用；需要先迁包再删旧 route。 |

## 10. 下一步建议

最高优先级不是继续大改 service，而是把当前 http4s 仍复用的 parser/codec 从 `routes` 包迁出去。

推荐下一个小票：

```text
BE-API-PARSER-MOVE-01
目标：把当前 http4s 仍复用的 parser/codec 从 routes 包迁移到 apiTypes 或 api 包。
收益：routes 目录只剩真正 route adapter，便于删除 legacy route。
```

最后再做：

```text
BE-LEGACY-ROUTES-REMOVE-01
目标：删除旧 Java HttpServer 入口和旧 HttpExchange route wrappers。
前提：所有 contract tests 已经覆盖 http4s 路径。
```
