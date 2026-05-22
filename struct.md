# 后端运行结构与 route/test 清理审计

本文只描述当前 `multimodule` 分支、当前 worktree 下的后端事实。判断依据来自：

- `package.json`
- `backend/build.sbt`
- `backend/src/main/scala`
- `backend/src/test/scala`
- `rg` 对 `routes`、`HttpExchange`、`APIMessage`、`BackendHttp4sRoutes` 的搜索结果

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
| 现在真正跑的是不是 `src/main` | 是。入口是 `BackendHttp4sApp`。 |
| 现在真正跑的是不是旧 `BackendApp` | 不是。当前 `backend/src/main` 下已经没有旧 `BackendApp.scala`。 |
| route 包还能不能继续“清空” | `backend/src/main` 下已经没有任何 `*/routes` 包；旧 `HttpExchange` route adapter 已经清掉。 |
| test 是否仍依赖旧 route | 没有发现依赖旧 `routes` 包的 test；当前 route contract test 直接测 http4s adapter。 |
| 是否应该删除所有 test | 不应该。应保留当前 main 真实路径的 http4s contract 和 service contract。 |
| 当前分支是否等于 Git `main` | 不是。当前分支是 `multimodule`，`main` 仍是旧架构基线。 |

## 2. 当前真正起作用的代码

### 2.1 运行入口

| 文件 | 作用 |
| --- | --- |
| `backend/src/main/scala/slaydemo/backend/http4s/BackendHttp4sApp.scala` | 进程入口：加载环境、创建 runtime、启动 Ember HTTP server。 |
| `backend/src/main/scala/slaydemo/backend/http4s/BackendHttp4sRoutes.scala` | 当前 HTTP route 总组合入口。 |
| `backend/src/main/scala/slaydemo/backend/BackendRuntime.scala` | 组装当前运行所需 service。 |
| `backend/src/main/scala/slaydemo/backend/BackendRepositories.scala` | 按配置选择 repository 实现。 |
| `backend/src/main/scala/slaydemo/backend/BackendLiveRepositoryFactories.scala` | 创建 live repository。 |
| `backend/src/main/scala/slaydemo/backend/BackendEnvironment.scala` | 从环境变量和本地 env 文件读取配置。 |
| `backend/src/main/scala/slaydemo/backend/BackendConfig.scala` | 后端配置值对象。 |

### 2.2 当前 HTTP adapter

当前所有 HTTP 请求通过 `BackendHttp4sRoutes.backendRoutes(...)` 组合。`backend/src/main/scala/slaydemo/backend/http4s` 下的正式运行文件是：

| 文件 | 当前作用 |
| --- | --- |
| `BackendHttp4sRoutes.scala` | 总 route composition，把各 domain route 组合成一个 `HttpRoutes[IO]`。 |
| `Http4sRouteSupport.scala` | CORS、错误响应、请求 path、阻塞 service 调用边界。 |
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

这些文件是当前 main 后端的 HTTP 边界，不是可以清空的旧 route。

### 2.3 当前业务层

`BackendRuntime` 实例化的 service 是当前 main 的真实业务入口。主要分层如下：

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

这些 service 不是独立微服务进程，而是同一个 JVM 后端里的业务模块。

## 3. 已经清掉的旧代码

### 3.1 旧 main 入口和旧 route

当前分支已经删除旧 Java `HttpServer` main 入口：

| 已删除文件 | 原作用 |
| --- | --- |
| `backend/src/main/scala/slaydemo/backend/BackendApp.scala` | 旧 Java HttpServer 入口。 |
| `backend/src/main/scala/slaydemo/backend/BackendRouteCatalog.scala` | 旧 route catalog。 |
| `backend/src/main/scala/slaydemo/backend/BackendRouteRegistry.scala` | 旧 route registry。 |

当前分支已经删除这些旧 `routes` 包：

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

审计搜索结果：

```text
rg --files backend/src/main/scala | rg "[\\/]routes[\\/]" -> 0
rg --files backend/src/test/scala | rg "[\\/]routes[\\/]" -> 0
```

当前 `backend/src/main/scala` 没有 `routes` 路径，也没有旧 `HttpExchange` main source。

### 3.2 旧 route test

当前分支已经删除旧 route contract test，并用 http4s contract test 替代：

| 已删除旧 test | 替代方向 |
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

`backend-legacy` 目录仍然是 tracked 历史代码，但它不在 `backend/build.sbt` 的 project root 内，也不被下面命令调用：

- `npm run backend:dev`
- `npm run backend:compile`
- `npm run backend:test-contracts`

所以对当前后端运行链路来说，`backend-legacy` 是运行时完全不用的历史代码。是否删除它是仓库管理决策，不影响当前 main 后端是否能跑。

## 4. 当前 test 到底在测什么

| test 类型 | 是否保留 | 原因 |
| --- | --- | --- |
| `http4s/*ContractTest.scala` | 保留 | 直接验证当前 `BackendHttp4sRoutes` 和各 domain http4s adapter。 |
| service contract test | 保留 | 验证业务状态转换，不依赖 HTTP adapter。 |
| repository/storage contract test | 保留 | 验证 memory/file/postgres repository 边界。 |
| `BackendApiBoundaryContractTest` | 保留 | 防止旧 `routes`、`HttpExchange`、APIMessage boundary 重新出现。 |
| 旧 `routes/*RouteContractTest.scala` | 已删除 | 旧 HttpExchange adapter 不再是 main。 |

因此，“test 和 main 根本内部逻辑不一样”的问题已经被部分修正：现在 route contract test 测的是当前 http4s adapter，composition contract test 测的是 `BackendHttp4sRoutes.backendRoutes(...)`，service contract test 测的是业务层。

仍然需要注意：大多数 route test 用的是 recording/stub service，而不是真实 `BackendRuntime.fromEnvironment(...)`。这类测试只能证明 HTTP contract 和 route 调度，不等同于完整端到端运行。完整端到端仍需要 smoke 脚本或实际启动后端。

## 5. 当前还重复或不够简洁的地方

### 5.1 http4s route adapter 仍有重复结构

当前 route 已经比旧 `HttpExchange` 简洁，但各 route 文件仍重复这些模式：

- method match：`OPTIONS`、`HEAD`、`GET`、`POST`。
- request body decode 后映射到 domain API error。
- domain `ApiErrorCode` 转 `HttpApiError`。
- `blocking(service.xxx)` 包住同步 service 调用。
- `Response[IO](Status.X).withEntity(...)` 手写成功响应。

这不是旧 route 残留，但说明 route adapter 还可以继续瘦身。下一步应该抽薄的 http4s support/test fixture，而不是重新创建 `routes` 包。

### 5.2 http4s tests 有大量 stub service

`BackendHttp4sRoutesCompositionContractTest` 为了验证 route composition，定义了很多 `Unused*Service` stub。这些只存在于 test，不影响 main 运行。

问题是测试代码偏长，不是业务重复。后续可以抽 `test/http4s/fixtures`，但不应该因为“看起来多”就删除测试覆盖。

### 5.3 文档曾经滞后

迁移过程中旧文档曾经保留 `legacy route`、`BackendApp`、`backend:dev:legacy` 等描述。当前事实是：

- `package.json` 没有 `backend:dev:legacy`。
- `backend/src/main` 没有旧 `BackendApp.scala`。
- `backend/src/main` 没有 `routes` 包。
- 默认运行入口只有 `BackendHttp4sApp`。

## 6. 对“清空 route 和 test”的判断

### 6.1 route

可以清的旧 route 已经清掉了。当前 `backend/src/main` 没有 `*/routes` 包，也没有 `HttpExchange` route adapter。

不应该清空 `backend/src/main/scala/slaydemo/backend/http4s`，因为这里就是当前 main 真正运行的 HTTP adapter。

正确方向是：

1. 保留 `http4s` adapter。
2. 继续把 path、request DTO、response DTO、error enum 下沉到各 domain 的 `apiTypes` 或 `api` contract 文件。
3. 把 route 文件瘦成“method/path/body -> service -> response”的薄边界。
4. 抽公共 helper 减少重复，但不要牺牲 contract 清晰度。

### 6.2 test

不应该清空所有 test。当前 test 已经按当前 main 路径重建，主要分为：

| test 类型 | 当前价值 |
| --- | --- |
| http4s route contract | 验证 HTTP method/path/body/status/error shape。 |
| BackendHttp4sRoutes composition contract | 验证总 route composition 能接住各 domain 的路径。 |
| service contract | 验证业务状态转换，不受 HTTP adapter 影响。 |
| boundary contract | 防止旧 route/APIMessage/HttpExchange 回流。 |

如果继续重构 test，应该做的是抽 fixture、减少重复 stub，而不是删除验证面。

## 7. Git 分支差异

当前分支：

```text
multimodule
```

当前 `main` 指向：

```text
483d4515a776e33aac98e43963c81617400e24dd
```

当前 `multimodule` 相对 `main` 的后端核心变化是：

- 删除旧 `BackendApp`、`BackendRouteCatalog`、`BackendRouteRegistry`。
- 删除旧 Java `HttpExchange` route adapter。
- 新增 `backend/http4s` 入口和 route adapter。
- 引入 `cats-effect`、`http4s`、`circe`、`log4cats`。
- 把旧 route parser/renderer 大量迁到各 domain 的 `apiTypes` 或 `api` contract 文件。
- 删除旧 route contract tests。
- 新增当前 http4s contract tests。
- 新增 boundary contract，阻止旧 route/APIMessage 结构回流。

所以，“当前跑的不是 Git main 分支”是正确的；但“当前跑的是 test 逻辑”是不正确的。当前跑的是 `multimodule` 分支的 `backend/src/main`，test 只是验证这套 main 入口和业务层。

## 8. 后续建议 ticket

### BE-HTTP4S-TEST-FIXTURE-88

目标：抽出 http4s contract tests 里重复的 `RouteResponse`、`run(...)`、recording service fixture，降低测试噪声，不降低覆盖面。

范围：只改 `backend/src/test/scala/slaydemo/backend/http4s` 下的测试 fixture 和 imports。

验收：`npm run backend:test-contracts` 通过。

### BE-HTTP4S-ROUTE-SLIM-89

目标：继续瘦身 http4s route adapter 中重复的 method/body/error 映射 helper。

范围：只改 `backend/src/main/scala/slaydemo/backend/http4s` 和必要的 domain `apiTypes` 调用点。

验收：`npm run backend:compile` 和 `npm run backend:test-contracts` 通过。
