# 后端运行结构与 http4s 清理审计

本文只描述当前 `multimodule` 分支、当前 worktree 下的后端事实。结论来自以下证据：

- `package.json`
- `backend/build.sbt`
- `backend/src/main/scala`
- `backend/src/test/scala`
- `git branch --show-current`
- `git log --left-right main...HEAD`
- `git diff --name-status main...HEAD -- backend/src/main/scala backend/src/test/scala backend/build.sbt`
- `rg` 对 `BackendHttp4sApp`、`BackendHttp4sRoutes`、`routes`、`HttpExchange` 的搜索结果

## 1. 当前结论

当前真正运行的后端是 `backend/src/main` 里的 http4s 后端，不是旧 Java `HttpServer`，也不是 `src/test`。

```text
npm run backend:dev
  -> cd backend && sbt "runMain slaydemo.backend.http4s.BackendHttp4sApp"
  -> backend/src/main/scala/slaydemo/backend/http4s/BackendHttp4sApp.scala
```

`backend/build.sbt` 也明确设置：

```scala
Compile / mainClass := Some("slaydemo.backend.http4s.BackendHttp4sApp")
```

运行链路是：

```text
BackendHttp4sApp.run
  -> BackendEnvironment.load()
  -> BackendRuntime.fromEnvironment(env)
  -> BackendHttp4sRoutes.backendRoutes(...)
  -> .orNotFound
  -> EmberServerBuilder.withHttpApp(httpApp)
```

| 问题 | 当前结论 |
| --- | --- |
| 当前真正跑的是不是 `src/main` | 是。入口是 `BackendHttp4sApp`。 |
| 当前真正跑的是不是旧 `BackendApp` | 不是。当前 `backend/src/main` 下已经没有旧 `BackendApp.scala`。 |
| route 包还能不能继续“清空” | 旧 `*/routes` 包已经从 main 源码中清掉；现在不能把 `http4s/` 当成可删除旧 route，因为它就是当前生产 HTTP adapter。 |
| test 是否仍依赖旧 route | 未发现 `src/test` 依赖旧 `*/routes` 包；当前 route contract test 直接测 http4s adapter。 |
| 是否应该删除全部 test | 不应该。应该保留能验证当前 main 入口、http4s contract、service state transition 和边界规则的测试。 |
| 当前分支是否等于 Git `main` | 不是。当前分支是 `multimodule`，`main` 是迁移前基线。 |

## 2. 真实运行入口

| 文件 | 当前作用 |
| --- | --- |
| `backend/src/main/scala/slaydemo/backend/http4s/BackendHttp4sApp.scala` | 进程入口：加载环境、创建 runtime、启动 Ember HTTP server。 |
| `backend/src/main/scala/slaydemo/backend/http4s/BackendHttp4sRoutes.scala` | 当前 HTTP route 总组合入口。 |
| `backend/src/main/scala/slaydemo/backend/BackendRuntime.scala` | 组装当前运行所需 service。 |
| `backend/src/main/scala/slaydemo/backend/BackendRepositories.scala` | 按配置选择 repository 实现。 |
| `backend/src/main/scala/slaydemo/backend/BackendLiveRepositoryFactories.scala` | 创建 live repository。 |
| `backend/src/main/scala/slaydemo/backend/BackendEnvironment.scala` | 从环境变量和本地 env 文件读取配置。 |
| `backend/src/main/scala/slaydemo/backend/BackendConfig.scala` | 后端配置值对象。 |

核心运行事实：

- `BackendHttp4sApp` 通过 `Resource.make(IO.blocking(BackendRuntime.fromEnvironment(env)))` 创建 runtime。
- `BackendRuntime` 创建当前真实 service，包括 `InMemoryBattleQueueService`、`InMemoryBattleStateService`、`DefaultIdentityService`、`DefaultReplayService`、`DefaultMailService` 等。
- `BackendHttp4sRoutes.backendRoutes(...)` 把这些 service 注入 http4s routes。
- `EmberServerBuilder` 才是真正监听端口并处理请求的 HTTP server。

## 3. 当前 http4s 层问题

当前 `http4s/` 不是旧代码，但结构确实已经中期失控。

### 3.1 `BackendHttp4sRoutes` 变成全后端总线

`BackendHttp4sRoutes.scala` 一次性接收十几个 service，然后用 `<+>` 平铺组合所有 route：

```text
HealthHttp4sRoutes
IdentityHttp4sRoutes
MailHttp4sRoutes
SocialHttp4sRoutes
ForumHttp4sRoutes
GovernanceHttp4sRoutes
ReplayHttp4sRoutes
BotProfileHttp4sRoutes
BattleQueueHttp4sRoutes
BattleRoomHttp4sRoutes
BattleStateHttp4sRoutes
BattleCommandHttp4sRoutes
BattleResultHttp4sRoutes
```

问题不是功能错误，而是它已经承担了“全后端功能总线”的职责。battle、identity、mail、social、forum、governance 被平铺在同一层，读代码时很难一眼看出某个业务域的完整 HTTP 边界。

### 3.2 每个 route 文件都像手写 HTTP 状态机

各 route 文件重复处理：

- path 判断
- method switch
- `OPTIONS` / `HEAD` / `GET` / `POST`
- request body decode
- API error 映射
- `IO.blocking` 包同步 service 调用
- response status/body/CORS

这使 route 不像声明式路由，更像每个文件手写一套小型 HTTP 状态机。

### 3.3 `Http4sRouteSupport` 是万能工具箱

`Http4sRouteSupport.scala` 同时管理：

- request path
- entity decode
- JSON object decode
- CORS response
- success response
- error response
- blocking boundary

短期能减少重复，长期会把所有 route 都绑到同一个通用工具箱，导致公共 helper 越来越厚，模块边界越来越弱。

### 3.4 HTTP 层知道过多业务错误细节

例如 battle command route 会把 battle service 的错误枚举翻译成 HTTP error。这个职责可以存在于 HTTP adapter，但最好下沉到 battle 模块内部的 error mapper，而不是留在全局平铺的 route 文件中。

## 4. 当前真实源码层级

### 4.1 领域模块

| 目录 | 当前作用 |
| --- | --- |
| `battle` | 战斗队列、房间、状态、命令、结算、地图、武器、技能、bot、碰撞等。 |
| `identity` | 账号、登录、session token、密码哈希。 |
| `mail` | 邮件列表、已读状态。 |
| `social` | 好友请求。 |
| `forum` | 论坛 topic/reply/vote。 |
| `governance` | 贡献调整和治理通知。 |
| `replay` | 回放记录、目录、评论、结算展示。 |
| `bots` | bot profile。 |
| `shared` | 共享 id、配置、storage、database support、health。 |
| `http4s` | 当前所有 HTTP adapter 和全局 route support。 |

### 4.2 battle 内部

| 目录 | 当前作用 |
| --- | --- |
| `battle/objects` | 战斗领域对象和值对象。 |
| `battle/objects/apiTypes` | battle HTTP request/response DTO、codec、parser。 |
| `battle/services/queue` | 排队、join/leave、room snapshot、heartbeat。 |
| `battle/services/session` | battle state service、command acceptance/application、stored battle。 |
| `battle/services/runtime` | tick 推进、finish/finalization、event、retention、aggregate update。 |
| `battle/services/world` | 地图、碰撞、几何、移动、出生点。 |
| `battle/services/combat` | 武器、开火、弹体、命中、伤害、terminal。 |
| `battle/services/actors` | player、bot、input、生命周期、运行时 actor 更新。 |
| `battle/services/abilities` | skill、pickup、slow field。 |
| `battle/services/results` | result、settlement、finish projection、replay、history。 |
| `battle/database` | 战斗结果持久化，包含 file/in-memory/postgres 实现。 |

这说明 battle service 已经按业务域拆开了，但 HTTP adapter 仍然没有按 battle 模块聚合。

## 5. 已清理的旧 route/test

当前 `backend/src/main` 没有旧 `*/routes` 包，也没有旧 `HttpExchange` main source。

审计结果：

```text
rg --files backend/src/main/scala | rg "[\\/]routes[\\/]" -> 0
rg --files backend/src/test/scala | rg "[\\/]routes[\\/]" -> 0
rg -n "com.sun.net.httpserver|HttpExchange" backend/src/main/scala backend/src/test/scala
  -> 只在 BackendApiBoundaryContractTest 的禁止规则中出现
```

已从 main 源码中删除或迁移的旧结构包括：

| 旧结构 | 当前状态 |
| --- | --- |
| `BackendApp.scala` | 已删除。 |
| `BackendRouteCatalog.scala` | 已删除。 |
| `BackendRouteRegistry.scala` | 已删除。 |
| `battle/routes` | 已删除或迁移到 `battle/objects/apiTypes`、`http4s`。 |
| `bots/routes` | 已删除或迁移到 `bots/objects/apiTypes`、`http4s`。 |
| `forum/routes` | 已删除或迁移到 `forum/objects/apiTypes`、`http4s`。 |
| `governance/routes` | 已删除或迁移到 `governance/objects/apiTypes`、`http4s`。 |
| `identity/routes` | 已删除或迁移到 `identity/api`、`http4s`。 |
| `mail/routes` | 已删除或迁移到 `mail/objects/apiTypes`、`http4s`。 |
| `replay/routes` | 已删除或迁移到 `replay/objects/apiTypes`、`http4s`。 |
| `shared/routes` | 已删除或迁移到 `shared/api`、`http4s`。 |
| `social/routes` | 已删除或迁移到 `social/objects/apiTypes`、`http4s`。 |

结论：现在不应该再说“清空 route 包”，因为旧 route 包已经没了。真正该整理的是当前 `http4s/` 的模块化边界。

## 6. 当前 test 的实际作用

当前测试不是旧 main 的替身，而是分成三类：

| test 类型 | 当前作用 | 是否保留 |
| --- | --- | --- |
| `http4s/*ContractTest.scala` | 验证当前 http4s adapter 的 method/path/body/status/error shape。 | 保留。 |
| `BackendHttp4sRoutesCompositionContractTest` | 验证总 route composition 能接住各业务域路径。 | 保留，但 fixture 可瘦身。 |
| service contract test | 验证业务状态转换和 service 规则，不依赖 HTTP adapter。 | 保留。 |
| repository/storage/boundary contract test | 验证 repository、storage、旧结构禁入规则。 | 保留。 |
| 旧 `routes/*RouteContractTest.scala` | 当前不存在。 | 不需要恢复。 |

需要注意：

- 多数 http4s route test 使用 recording/stub service，不是完整 `BackendRuntime.fromEnvironment(...)`。
- 因此 route test 能证明 HTTP contract 和 route 调度，不等同于完整端到端 smoke。
- 如果要证明“实际能跑通”，还需要启动 `BackendHttp4sApp` 后做 smoke，例如 `npm run demo:api-contract` 或更小的 health/battle queue smoke。

## 7. Git 分支差异

当前分支：

```text
multimodule
```

当前 HEAD：

```text
d5fb942a566840343d981be3758013bff0ee29dd
```

当前 `main`：

```text
483d4515a776e33aac98e43963c81617400e24dd
```

相对 `main` 的后端核心差异：

- `backend/build.sbt` 新增 http4s、cats-effect、circe、log4cats 依赖，并把 mainClass 指向 `BackendHttp4sApp`。
- 删除旧 Java `HttpServer` 入口：`BackendApp.scala`、`BackendRouteCatalog.scala`、`BackendRouteRegistry.scala`。
- 删除旧 `*/routes` 包。
- 新增 `backend/http4s` 作为当前 HTTP adapter。
- 新增 `BackendRuntime.scala` 作为 service/runtime 组装点。
- battle objects 从单层文件拆到 `core/player/weapon/projectile/pickup/...`。
- battle services 从单层文件拆到 `queue/session/runtime/world/combat/actors/abilities/results`。
- parser/renderer/request/response 等旧 route 内容迁移到各 domain 的 `apiTypes` 或 `api`。
- 新增当前 http4s contract tests 和边界测试，禁止旧 `HttpExchange` 和旧 route adapter 回流。

所以：“当前跑的不是 Git main 分支”是正确的；但“当前跑的是 test 逻辑”不正确。当前运行的是 `multimodule` 分支的 `backend/src/main`，测试只是验证这套 main 入口和业务层。

## 8. 推荐目标结构

当前 `http4s/` 不应继续平铺扩大。建议改为按业务域聚合模块，对外只暴露 domain module。

```text
backend/src/main/scala/slaydemo/backend/http4s/
  BackendHttp4sApp.scala
  HttpApiModules.scala
  shared/
    Http4sRouteSupport.scala
    Http4sResponses.scala
    Http4sCors.scala
  battle/
    BattleHttpModule.scala
    BattleQueueRoutes.scala
    BattleRoomRoutes.scala
    BattleStateRoutes.scala
    BattleCommandRoutes.scala
    BattleResultRoutes.scala
    BattleHttpCodecs.scala
    BattleHttpErrors.scala
  identity/
    IdentityHttpModule.scala
    IdentityRoutes.scala
    IdentityHttpErrors.scala
  social/
    SocialHttpModule.scala
    SocialRoutes.scala
  forum/
    ForumHttpModule.scala
    ForumRoutes.scala
  governance/
    GovernanceHttpModule.scala
    GovernanceRoutes.scala
  replay/
    ReplayHttpModule.scala
    ReplayRoutes.scala
  bots/
    BotProfileHttpModule.scala
    BotProfileRoutes.scala
  health/
    HealthHttpModule.scala
    HealthRoutes.scala
```

目标依赖方向：

```text
BackendHttp4sApp
  -> HttpApiModules
  -> battle.BattleHttpModule
  -> battle.*Routes
  -> battle services + battle apiTypes

BackendHttp4sApp
  -> HttpApiModules
  -> identity.IdentityHttpModule
  -> identity service + identity api
```

约束：

- 模块之间不要直接互相调用 route。
- 全局 shared helper 只能保留极薄的 HTTP 基础设施。
- 业务错误映射留在 domain HTTP module 内部。
- route 文件只做 method/path/body -> service -> response。
- DTO、enum wire value、parser/encoder 的权威定义仍留在各 domain 的 `objects/apiTypes` 或 `api`。

## 9. 后续小票建议

### BE-HTTP4S-MODULE-94

目标：先迁移 battle HTTP adapter 到 `http4s/battle` 子目录，并新增 `BattleHttpModule.routes(...)`。

允许范围：

- `backend/src/main/scala/slaydemo/backend/http4s/Battle*Http4sRoutes.scala`
- `backend/src/main/scala/slaydemo/backend/http4s/BackendHttp4sRoutes.scala`
- 新增 `backend/src/main/scala/slaydemo/backend/http4s/battle/*`
- 必要的 package/import 更新
- 对应 `backend/src/test/scala/slaydemo/backend/http4s` 中 battle route test 的 import 更新

验收：

- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。
- `git diff --check` 通过。
- `BackendHttp4sRoutes.scala` 不再直接拼每个 battle 子 route，只拼 `BattleHttpModule.routes(...)`。

### BE-HTTP4S-SHARED-95

目标：把 `Http4sRouteSupport` 拆成更薄的 shared 基础设施，避免继续变成万能工具箱。

建议拆分：

- `Http4sResponses`
- `Http4sCors`
- `Http4sBodyDecoders`
- `Http4sBlocking`

验收：

- domain route 只 import 自己需要的 helper。
- 不改变 API contract。
- `npm run backend:compile` 和 `npm run backend:test-contracts` 通过。

### BE-HTTP4S-TEST-FIXTURE-96

目标：整理 http4s route contract test fixture，减少 `Unused*Service` 和重复 `runRoute`/`RouteResponse` 样板。

验收：

- 测试覆盖不减少。
- `npm run backend:test-contracts` 通过。

## 10. 当前可执行结论

不建议“清空 `http4s` route”。旧 route 已经清空；当前要做的是模块化当前正在运行的 http4s adapter。

最安全的下一步是先做 `BE-HTTP4S-MODULE-94`：只把 battle HTTP adapter 下沉到 `http4s/battle`，让顶层 route composition 从“全功能总线”变成“按领域拼 module”。这会直接改善你指出的结构性问题，同时不改变 contract、不碰 service 业务逻辑、不删除测试反馈。
