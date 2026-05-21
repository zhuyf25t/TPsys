# 后端运行结构与 route/test 清理审计

本文件只描述当前 `multimodule` 分支、当前 worktree 下的后端事实。判断依据来自：

- `backend/build.sbt`
- `package.json`
- `backend/src/main/scala`
- `backend/src/test/scala`
- `git diff main..HEAD -- backend`
- `rg` 对 `routes`、`HttpExchange`、`APIMessage`、`BackendHttp4sRoutes` 的搜索结果

## 1. 总结论

当前真正运行的后端是 `src/main` 里的 http4s 后端，不是旧 `BackendApp`，也不是 `src/test`。

```text
npm run backend:dev
  -> cd backend && sbt "runMain slaydemo.backend.http4s.BackendHttp4sApp"
  -> backend/src/main/scala/slaydemo/backend/http4s/BackendHttp4sApp.scala
```

`backend/build.sbt` 也明确设置：

```scala
Compile / mainClass := Some("slaydemo.backend.http4s.BackendHttp4sApp")
```

所以运行链路是：

```text
BackendHttp4sApp.run
  -> BackendEnvironment.load()
  -> BackendRuntime.fromEnvironment(env)
  -> BackendHttp4sRoutes.backendRoutes(...)
  -> .orNotFound
  -> EmberServerBuilder.withHttpApp(httpApp)
```

结论表：

| 问题 | 当前结论 |
| --- | --- |
| 现在真正跑的是不是 `src/main` | 是。入口是 `BackendHttp4sApp`。 |
| 现在真正跑的是不是旧 `BackendApp` | 不是。旧 `BackendApp.scala` 在当前分支已经删除。 |
| 现在 route 包还能不能“清空” | `backend/src/main` 下已经没有 `*/routes` 包；旧 `HttpExchange` route adapter 已经清掉。 |
| 现在 test 是否仍依赖旧 route | 没有发现依赖旧 `routes` 包的 test；当前 route contract test 走 `BackendHttp4sRoutes`。 |
| 是否应该删除所有 test | 不应该。应保留当前 main 真实路径的 http4s contract 和 service contract。 |
| 当前分支是否等于 Git `main` | 不是。当前分支是 `multimodule`，`main` 仍是旧架构基线。 |

## 2. 当前真正起作用的代码

### 2.1 运行入口

| 文件 | 作用 |
| --- | --- |
| `backend/src/main/scala/slaydemo/backend/http4s/BackendHttp4sApp.scala` | 进程入口；加载环境、创建 runtime、启动 Ember HTTP server。 |
| `backend/src/main/scala/slaydemo/backend/BackendRuntime.scala` | 组装当前运行所需 service。 |
| `backend/src/main/scala/slaydemo/backend/BackendRepositories.scala` | 选择当前 repository 实现。 |
| `backend/src/main/scala/slaydemo/backend/BackendLiveRepositoryFactories.scala` | 创建 live repository。 |
| `backend/src/main/scala/slaydemo/backend/BackendEnvironment.scala` | 从环境变量读取配置。 |
| `backend/src/main/scala/slaydemo/backend/BackendConfig.scala` | 后端配置值对象。 |

### 2.2 当前 HTTP adapter

当前所有 HTTP 请求通过 `BackendHttp4sRoutes.backendRoutes(...)` 组合。`backend/src/main/scala/slaydemo/backend/http4s` 下共有这些正式运行文件：

| 文件 | 当前作用 |
| --- | --- |
| `BackendHttp4sRoutes.scala` | 总 route composition，把各 domain route 组合成一个 `HttpRoutes[IO]`。 |
| `Http4sRouteSupport.scala` | CORS、错误响应、阻塞 service 调用边界。 |
| `HealthHttp4sRoutes.scala` | health API。 |
| `IdentityHttp4sRoutes.scala` | 注册、登录、当前用户、账号列表。 |
| `MailHttp4sRoutes.scala` | 邮件列表、邮件已读。 |
| `SocialHttp4sRoutes.scala` | 好友请求查询、创建、响应。 |
| `ForumHttp4sRoutes.scala` | forum topic、reply、vote。 |
| `GovernanceHttp4sRoutes.scala` | 贡献调整、治理通知。 |
| `ReplayHttp4sRoutes.scala` | replay catalog、detail、comments。 |
| `BotProfileHttp4sRoutes.scala` | bot profile 列表。 |
| `BattleQueueHttp4sRoutes.scala` | battle queue status/join/leave。 |
| `BattleRoomHttp4sRoutes.scala` | room snapshot/heartbeat。 |
| `BattleStateHttp4sRoutes.scala` | battle state read/SSE stream。 |
| `BattleCommandHttp4sRoutes.scala` | battle command submit。 |
| `BattleResultHttp4sRoutes.scala` | battle result list/record。 |

### 2.3 当前业务层

`BackendRuntime` 实例化的 service 才是当前 main 真实业务入口。主要分层如下：

| 目录 | 当前作用 |
| --- | --- |
| `battle/services` | battle 队列、session、runtime、world、combat、actors、abilities、results。 |
| `identity/services` | 账号注册、登录、session 当前用户。 |
| `mail/services` | 邮件列表和已读状态。 |
| `social/services` | 好友请求。 |
| `forum/services` | 论坛 topic/reply/vote。 |
| `governance/services` | 治理与通知。 |
| `replay/services` | replay 记录、列表、评论。 |
| `bots/services` | bot profile。 |
| `shared/services` | health。 |

这些 service 被 http4s route 调用；它们不是微服务进程，只是同一个 JVM 后端中的业务模块。

## 3. 当前完全用不到或已删除的代码

### 3.1 当前分支已经删除的旧 main 入口和旧 route

和 Git `main` 相比，当前分支已经删除了旧入口：

| 已删除文件 | 原作用 |
| --- | --- |
| `backend/src/main/scala/slaydemo/backend/BackendApp.scala` | 旧 Java HttpServer 入口。 |
| `backend/src/main/scala/slaydemo/backend/BackendRouteCatalog.scala` | 旧 route catalog。 |
| `backend/src/main/scala/slaydemo/backend/BackendRouteRegistry.scala` | 旧 route registry。 |

当前分支也已经删除了这些旧 `routes` 包：

| 已删除目录 | 原作用 |
| --- | --- |
| `battle/routes` | battle 旧 HttpExchange parser/handler/renderer。 |
| `bots/routes` | bot profile 旧 route。 |
| `forum/routes` | forum 旧 parser/handler/renderer。 |
| `governance/routes` | governance 旧 parser/renderer。 |
| `identity/routes` | identity 旧 route。 |
| `mail/routes` | mail 旧 route。 |
| `replay/routes` | replay 旧 route/parser/renderer。 |
| `shared/routes` | 旧 shared HTTP support/health route。 |
| `social/routes` | social 旧 route。 |

审计搜索结果说明：当前 `backend/src/main/scala` 没有 `routes` 路径，也没有 `HttpExchange` main source。

### 3.2 当前分支已经删除或替换的旧 test

和 Git `main` 相比，当前分支已经删除旧 route contract test，例如：

| 已删除 test | 替代方向 |
| --- | --- |
| `battle/routes/*RouteContractTest.scala` | `http4s/Battle*Http4sContractTest.scala` |
| `bots/routes/BotProfileRouteContractTest.scala` | `http4s/BotProfileHttp4sContractTest.scala` |
| `forum/routes/ForumRouteContractTest.scala` | `http4s/ForumHttp4sContractTest.scala` |
| `governance/routes/GovernanceRouteContractTest.scala` | `http4s/GovernanceHttp4sContractTest.scala` |
| `identity/routes/IdentityRouteContractTest.scala` | `http4s/IdentityHttp4sContractTest.scala` |
| `mail/routes/MailRouteContractTest.scala` | `http4s/MailHttp4sContractTest.scala` |
| `replay/routes/ReplayRouteContractTest.scala` | `http4s/ReplayHttp4sCatalogContractTest.scala` |
| `shared/routes/HealthRouteContractTest.scala` | `http4s/HealthHttp4sRouteContractTest.scala` |
| `social/routes/SocialRouteContractTest.scala` | `http4s/SocialHttp4sContractTest.scala` |

当前 `BackendContractTestRunner` 调用的是新的 http4s contract 和 service contract，不是旧 route 包。

### 3.3 `backend-legacy`

`backend-legacy` 目录当前有 123 个 tracked 文件，但它不在 `backend/build.sbt` 的 project root 内，也不被 `npm run backend:dev`、`npm run backend:compile`、`npm run backend:test-contracts` 调用。

所以对当前后端运行链路来说，`backend-legacy` 是运行时完全不用的历史代码。是否删除它是仓库管理决策，不影响当前 main 后端是否能跑。

## 4. 当前仍然重复或不够干净的地方

### 4.1 http4s route adapter 仍有重复结构

当前 route 已经比旧 `HttpExchange` 简洁，但各 route 文件仍重复这些模式：

- `OPTIONS`/`HEAD`/`GET`/`POST` 的 method match。
- `statusFrom(Int): Status`。
- domain `ApiErrorCode` 到 `HttpApiError` 的转换。
- `request.as[...]` 失败后映射到 `InvalidJsonObject`。
- `blocking(service.xxx).flatMap(...)` 的样板。

这不是旧 route 残留，但说明 route adapter 还可以继续瘦身。下一步应该抽一个小的 http4s support 层，而不是重新创建 `routes` 包。

### 4.2 battle API 类型位置仍然不统一

当前 battle 有两个 API 相关位置：

| 位置 | 当前内容 | 问题 |
| --- | --- | --- |
| `battle/api` | `BattleCommandRequest`、`BattleCommandAccepted`、`BattleCommandVector`。 | 被 service 和 tests 使用，不是废代码；但名字像全局 API 层。 |
| `battle/objects/apiTypes` | request parser、response DTO、route target、error enum。 | 更符合当前“domain 内 apiTypes colocation”的方向。 |

这两个位置不是完全重复，但边界不够清晰。后续应选择一种策略：

- 把 `battle/api` 明确改名为 battle command contract 层。
- 或把其中稳定 DTO 下沉/合并到 `battle/objects/apiTypes`。

这一步不能直接删除，因为 `BattleStateService`、battle runtime test、http4s command route 都在使用 `BattleCommandRequest`。

已清理：`BattleQueueApi.scala` 和 `BattleStateApi.scala` 只包含无引用旧 request/view case class，当前分支已经删除。queue、room、state 的当前 HTTP contract 都由 `battle/objects/apiTypes` 承接。

### 4.3 兼容旧前端的 path alias 增加了复杂度

当前 battle route 同时支持 REST 风格路径和旧 APIMessage 风格路径，例如：

```text
/api/battle/queue/join
/api/battlequeuejoinapi
/battlequeuejoinapi
```

这些 alias 是当前前端兼容的一部分，不是死代码。但它们会让 target parser 和 contract test 变复杂。只有在确认前端不再调用旧 alias 后，才能删除。

### 4.4 test 中有大量 stub service

`BackendHttp4sRoutesCompositionContractTest` 为了验证 route composition，定义了很多 `Unused*Service` stub。这些只存在于 test，不影响 main 运行。

它们的问题是测试代码偏长，不是业务重复。后续可以抽成 test fixture，但不应该因为“看起来多”就删掉，因为它证明了组合 route 能走到真实 http4s adapter。

## 5. 对“清空 route 和 test”的判断

### 5.1 route

可以清的旧 route 已经清掉了。当前 `backend/src/main` 没有 `*/routes` 包，也没有 `HttpExchange` route adapter。

不应该清空 `backend/src/main/scala/slaydemo/backend/http4s`，因为这里就是当前 main 真正运行的 HTTP adapter。

正确方向是：

1. 保留 `http4s` adapter。
2. 继续把 path、request DTO、response DTO、error enum 下沉到 domain 的 `apiTypes` 或 `api` contract 文件。
3. 把 route 文件瘦成“method/path/body -> service -> response”的薄层。
4. 删除确认无前端调用的旧 path alias。

### 5.2 test

不应该清空所有 test。当前 test 已经按当前 main 路径重建，主要分两类：

| test 类型 | 是否保留 | 原因 |
| --- | --- | --- |
| `http4s/*ContractTest.scala` | 保留 | 直接验证当前 `BackendHttp4sRoutes`，覆盖 main 真实 HTTP adapter。 |
| service contract test | 保留 | 验证业务状态转换，不依赖 HTTP adapter。 |
| `BackendApiBoundaryContractTest` | 保留 | 防止旧 `routes`、`HttpExchange`、`APIMessage` 重新出现。 |
| 旧 `routes/*RouteContractTest.scala` | 已删除 | 旧 HttpExchange adapter 不再是 main。 |

如果继续重构 test，应该做的是抽 fixture、减少重复 stub，而不是删除验证面。

## 6. Git 分支差异

当前分支：

```text
multimodule
```

当前 `main` 指向：

```text
483d4515a776e33aac98e43963c81617400e24dd
```

当前 `multimodule` 相对 `main` 的后端差异很大，核心变化是：

- 删除旧 `BackendApp`、`BackendRouteCatalog`、`BackendRouteRegistry`。
- 删除旧 Java `HttpExchange` route adapter。
- 新增 `backend/http4s` 入口和 route adapter。
- 引入 `cats-effect`、`http4s`、`circe`、`log4cats`。
- 把旧 route parser/renderer 大量迁到各 domain 的 `apiTypes` 或 `api` contract 文件。
- 删除旧 route contract tests。
- 新增当前 http4s contract tests。
- 新增 boundary contract，阻止旧 route/APIMessage 结构回流。

所以“当前跑的不是 Git main 分支”是正确的；但“当前跑的是 test 逻辑”是不正确的。当前跑的是 `multimodule` 分支的 `backend/src/main`。

## 7. 后续建议 ticket

### BE-HTTP-ROUTE-SUPPORT-59

目标：把 route adapter 中重复的 `statusFrom`、typed API error 到 `HttpApiError` 的转换、method-not-allowed 响应提取到 `Http4sRouteSupport` 或一个很薄的 `HttpApiErrorRenderer`。

范围：只改 `backend/http4s` 和必要的 domain error enum 调用点。

验收：`npm run backend:compile` 和 `npm run backend:test-contracts` 通过。

### BE-BATTLE-API-LAYER-60

目标：整理 `battle/api` 与 `battle/objects/apiTypes` 的职责边界，避免同一个 API contract 概念散在两个位置。

范围：只处理 battle command/queue/state API 类型的包位置和 imports，不改字段、不改 JSON。

验收：battle http4s contract、battle runtime contract、全量 backend contract 通过。

### BE-LEGACY-PATH-ALIAS-AUDIT-61

目标：审计前端是否仍调用 `/api/battlequeuejoinapi` 这类旧 alias。确认没有调用后，分批删除 alias。

范围：先审计，不直接删。

验收：列出每个 alias 的前端调用证据和保留/删除建议。
