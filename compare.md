# sample 后端架构与 TPsys 后端对比报告

本文基于以下代码路径整理：

- 标准样例后端：`D:\26Spring\Typesafe\slay\sample\sample\backend-sample`
- 当前项目后端：`D:\26Spring\Typesafe\slay\TPsys\backend`

目标不是简单判断谁“代码更多”或“功能更多”，而是识别 sample 使用的现代 Scala 后端工程方法，并对比当前 TPsys 后端在类型安全、分层、副作用边界、HTTP/JSON/数据库边界上的不足。

## 1. 总体结论

sample 后端是一个功能较小但边界清晰的 Scala 3 后端。它的核心价值在于：

- 用 `cats-effect IO` 表达副作用。
- 用 `Resource` 管理服务和数据库生命周期。
- 用 `http4s` 表达 HTTP route。
- 用 `circe` 管理 JSON encoder/decoder。
- 用 `HikariCP` 管理 PostgreSQL 连接池。
- 用 `APIMessage.plan(connection): IO[Response]` 把 API use-case 写成可组合的 effectful plan。
- 用统一的 `HttpError` 把业务错误映射到 HTTP response。

TPsys 当前后端业务规模更大，battle、identity、mail、forum、social、governance、replay、bots 等业务域比 sample 复杂很多，并且已经有不少有价值的领域建模，例如 battle object 拆分、value object、enum、repository interface、多存储模式等。

但是 TPsys 的主要不足集中在边界层：

- HTTP 层仍然基于 `com.sun.net.httpserver.HttpServer`，route handler 里混入了方法判断、CORS、请求解析、错误映射、JSON 渲染和部分业务调度。
- 自定义 `BackendIO` 只是简单 thunk，不具备 cats-effect 的资源安全、阻塞线程管理、取消语义和错误组合能力。
- JSON 大量手写 parser/renderer，contract 不能由编译期 codec 统一约束。
- APIMessage、Planner、Endpoint、旧 RouteHandler、RouteCatalog 同时存在，导致 API 入口有重复来源。
- PostgreSQL 连接仍然以 `DriverManager` 直接创建为主，没有统一连接池和 `Resource` 生命周期。
- 错误模型分散在各 domain 的 route mapper 中，没有像 sample 一样形成一个统一、可组合、可测试的 HTTP error ADT。

因此，TPsys 后端下一阶段不应该优先继续增加 route/planner 层，而应该优先治理 HTTP/JSON/effect/DB 这些边界，使现有丰富业务模型能够运行在更安全、更现代的 Scala 后端基础上。

## 2. sample 后端架构

### 2.1 技术栈

sample 的 `build.sbt` 使用：

- Scala `3.3.3`
- `cats-effect 3.5.4`
- `http4s-dsl 0.23.27`
- `http4s-ember-server 0.23.27`
- `http4s-circe 0.23.27`
- `circe-generic 0.14.9`
- `circe-parser 0.14.9`
- `HikariCP 5.1.0`
- PostgreSQL JDBC `42.7.4`
- `log4cats-slf4j`
- `slf4j-simple`

这说明 sample 的重点不是自己手写 HTTP server、JSON parser 或 connection lifecycle，而是使用成熟库把副作用边界交给标准抽象。

### 2.2 启动入口

入口文件是：

- `src/main/scala/Main.scala`

核心结构：

- `object Main extends IOApp.Simple`
- `serverResource: Resource[IO, Server]`
- 启动时初始化数据库连接池。
- 在同一个事务边界内初始化表结构和 seed 数据。
- 使用 `EmberServerBuilder.default[IO]` 启动 http4s server。
- 使用 `Logger.httpApp` 做 HTTP 访问日志。
- `run` 通过 `serverResource.useForever` 保持服务生命周期。

这个设计的好处是：

- 启动、关闭、数据库连接池释放都有明确生命周期。
- 服务进程的 effect 类型统一为 `IO`。
- JDBC 这种阻塞操作通过 `IO.blocking` 包住。
- server 本身是 `Resource`，不会散落手写 `while true sleep`。

### 2.3 路由组合

sample 的路由主要在：

- `src/main/scala/routes/ApiRouter.scala`
- `src/main/scala/routes/HealthRouter.scala`
- `src/main/scala/system/api/APIMessageRouter.scala`

`ApiRouter` 组合：

- `HealthRouter.routes`
- `APIMessageRouter.routes(UserRoutes.apiMessages ++ BooksRoutes.apiMessages, UserRoutes.resolveUserToken)`

`HealthRouter` 是直接 http4s DSL：

- `case GET -> Root / "api" / "health" => Ok(...)`

业务 API 统一走：

- `case req @ POST -> Root / "api" / apiName => ...`

这意味着 sample 中 route 的职责很窄：

- 匹配 HTTP method 和 path。
- 找到注册的 API message。
- 解析 JSON。
- 执行 plan。
- 统一错误映射。

route 不直接承载核心业务逻辑。

### 2.4 APIMessage 模式

核心文件：

- `src/main/scala/system/api/APIMessage.scala`

核心抽象：

```scala
trait APIMessage[Response]:
  def plan(connection: Connection): IO[Response]

trait APIWithTokenMessage[Response] extends APIMessage[Response]

trait NoRequestMessage[Response] extends APIMessage[Response]
```

`RegisteredAPIMessage` 保存：

- `apiName`
- `requiresUserToken`
- `planJson: (Json, Connection) => IO[Json]`

注册时依赖：

- `Decoder[Message]`
- `Encoder[Response]`
- `ClassTag[Message]`

API 名称由 class name 推导，例如：

- `BorrowBookAPIMessage`
- 去掉 `APIMessage`
- 拼成 `BorrowBookAPI`
- 转小写为 `borrowbookapi`

最终路由是：

- `POST /api/borrowbookapi`

这个模式的重点是：每个 API message 自己就是 use-case plan，不再额外拆出一层 planner。route 只需要知道有哪些 message，message 内部负责调用表、服务或其他 message。

### 2.5 JSON contract

sample 使用 circe：

- request body 通过 `payload.as[Message]` 反序列化。
- response 通过 `response.asJson` 序列化。
- enum/value object 通过 given `Encoder` / `Decoder` 绑定 wire value。

例如：

- `BookInventoryStatus` 是 enum。
- `toString` 输出 `"available"` / `"borrowed"`。
- `fromString` 做反序列化校验。
- `given Encoder[BookInventoryStatus]`
- `given Decoder[BookInventoryStatus]`

这比手写字符串 JSON 更安全，因为字段、类型、enum wire value 都能被 Scala 类型和 codec 约束。

### 2.6 数据库生命周期与事务

核心文件：

- `src/main/scala/system/DatabaseSession.scala`

设计要点：

- 使用 `HikariDataSource`。
- 使用 `AtomicReference[Option[HikariDataSource]]` 保存当前连接池。
- `initialize: Resource[IO, Unit]` 负责创建和释放连接池。
- `pooledConnectionResource` 用 `Resource.make` 获取和关闭连接。
- `withTransactionConnection[A](operation: Connection => IO[A]): IO[A]` 统一处理：
  - `setAutoCommit(false)`
  - 成功时 `commit`
  - 失败时 `rollback`
  - 连接关闭

这形成了清楚的副作用边界：数据库连接、事务和 JDBC 阻塞都被限制在 `IO` 和 `Resource` 内。

### 2.7 业务模块组织

sample 业务模块主要有：

- `services/user`
- `services/books`

每个 domain 内部结构类似：

- `api/`：每个 API use-case 一个 `XXXAPIMessage`
- `objects/`：领域对象和值对象
- `objects/apiTypes/`：request/response 边界类型
- `routes/`：注册该 domain 支持的 API message
- `tables/`：JDBC 表操作和 schema initializer
- `utils/`：少量辅助 use-case

以 `services/books/api/BorrowBookAPIMessage.scala` 为例，它的 plan 做了：

- 获取当前用户。
- 清理 reader name。
- `BookTable.lockById(connection, bookId)` 锁定图书记录。
- 检查库存。
- 扣减库存。
- 插入借阅记录。

这些步骤都在同一个 `Connection` 和同一个 transaction 中执行，因此库存扣减和借阅记录创建具有一致性。

### 2.8 错误模型

核心文件：

- `src/main/scala/system/HttpError.scala`
- `src/main/scala/system/objects/ErrorResponse.scala`

sample 使用 typed error：

- `HttpError.BadRequest`
- `HttpError.Unauthorized`
- `HttpError.Forbidden`
- `HttpError.NotFound`
- `HttpError.Conflict`

`APIMessageRouter.handleErrors` 统一把这些错误映射为 HTTP status 和 JSON error response。

好处：

- API plan 内只需要表达业务失败。
- HTTP response 格式集中管理。
- route 层不需要到处写重复的 error mapping。

### 2.9 认证与密码安全

sample 的注册逻辑在：

- `src/main/scala/services/user/api/RegisterUserAPIMessage.scala`

它使用：

- `SecureRandom`
- 16 byte salt
- `PBKDF2WithHmacSHA256`
- `120000` iterations
- `256` bit key length
- session token hash 后入库

这比单纯 SHA-256 password hash 更接近现代后端安全实践。

## 3. TPsys 当前后端架构

### 3.1 技术栈

当前 `backend/build.sbt` 只有：

- Scala `3.3.3`
- PostgreSQL JDBC `42.7.4`

没有：

- cats-effect
- http4s
- circe
- HikariCP
- log4cats

这导致 TPsys 很多底层能力都靠项目自己实现，包括：

- HTTP routing
- CORS
- JSON parsing
- JSON rendering
- effect wrapper
- error mapping
- connection lifecycle

这些能力不是业务核心，但当前占用了大量代码复杂度。

### 3.2 启动入口

入口文件：

- `backend/src/main/scala/slaydemo/backend/BackendApp.scala`

当前设计：

- 使用 `com.sun.net.httpserver.HttpServer`。
- 手动创建所有 service、repository、route。
- `Executors.newCachedThreadPool()` 作为 executor。
- `server.start()` 后用 `while true do Thread.sleep(60000L)` 阻塞进程。
- battle live state 明确保存在进程内存中。

优点：

- 依赖少。
- 运行模型直接。
- 对课程 demo 或本地运行友好。

不足：

- 服务生命周期不是 `Resource` 化的。
- shutdown 和资源释放边界不清楚。
- 没有统一 effect runtime。
- 阻塞 IO 和普通计算没有区分。
- executor 策略没有和 request lifecycle、blocking DB 操作做类型层面的隔离。

### 3.3 RouteRegistry 与 RouteCatalog

相关文件：

- `BackendRouteRegistry.scala`
- `BackendRouteCatalog.scala`

当前设计：

- `BackendRouteRegistry` 手动注册所有 `BackendRouteHandler(path, handle)`。
- base route 同时注册裸路径和 `/api` 前缀路径。
- APIMessage endpoints 又注册为 `/api/${endpoint.messageKey}`。
- `BackendRouteCatalog.RouteContexts` 作为另一份 route 元数据。
- 注册时检查 handler table 和 route context metadata 是否一致。

优点：

- 能发现 route table 和 route metadata 的偏差。
- 对前端路径审计有帮助。

不足：

- route path 至少存在两份来源。
- 普通 route 和 APIMessage route 并存，增加理解成本。
- `/api` 前缀复制逻辑使路径行为更难推断。
- 每次增删 API 都容易同时改 handler、catalog、endpoint。

sample 中 route 注册更薄：API message list 是主来源，http4s route 只按 `POST /api/:apiName` 分发。

### 3.4 BackendAPIMessage 当前问题

相关文件：

- `shared/api/BackendAPIMessage.scala`
- `shared/api/BackendIO.scala`
- `battle/routes/api/*APIMessagePlanner.scala`
- `replay/routes/ReplayCatalogAPIMessagePlanner.scala`
- `shared/routes/HealthAPIMessagePlanner.scala`

当前结构：

- `BackendAPIMessage` 是 marker trait。
- `BackendAPIRequest` 保存 method/path/query/body。
- `BackendAPIResponse` 自己写入 `HttpExchange`。
- `BackendAPIMessagePlanner[M]` 执行 `plan(message): BackendIO[BackendAPIResponse]`。
- `BackendAPIEndpoint` 负责 `decode(request)` 再 `planner.plan(...)`。

问题在于它比 sample 多了一层：

- sample：`request JSON -> XXXAPIMessage -> message.plan(connection)`
- TPsys：`request -> BackendAPIRequest -> decode -> XXXAPIMessage enum -> XXXAPIMessagePlanner.plan -> BackendAPIResponse.write`

并且旧 route handler 仍然存在。例如 battle queue join 同时有：

- `BattleQueueRouteHandler.join`
- `BattleQueueJoinAPIMessagePlanner.endpoint`

两者都要解析 method/body、调用 parser、调用 authorization、调用 queue service、渲染 response。即使代码共享了一些 parser/renderer，入口逻辑仍然重复。

这正是当前后端“不干净”的核心来源之一：APIMessage 没有真正简化 route，反而和旧 route handler 并行存在。

### 3.5 BackendIO 的不足

当前 `BackendIO`：

```scala
final class BackendIO[+A] private (private val thunk: () => A) {
  def map[B](transform: A => B): BackendIO[B] =
    BackendIO.delay(transform(unsafeRun()))

  def flatMap[B](transform: A => BackendIO[B]): BackendIO[B] =
    BackendIO.delay(transform(unsafeRun()).unsafeRun())

  def unsafeRun(): A =
    thunk()
}
```

它能表达延迟执行，但不等价于 cats-effect IO。

主要不足：

- 没有 `Resource` / `bracket` 级别的资源安全。
- 没有 `IO.blocking` 区分阻塞 JDBC。
- 没有取消语义。
- 没有 fiber/runtime。
- `flatMap` 内部直接嵌套 `unsafeRun`，不适合复杂组合。
- 错误处理依赖 JVM exception 直接抛出，没有 typed error channel 或统一 error handler。
- 名称上像 effect，但能力远弱于标准 effect system。

因此它适合作为过渡层，不适合作为长期后端 effect 基础。

### 3.6 JSON 处理分散

当前 TPsys 有很多手写 JSON 相关文件，例如：

- `shared/json/JsonObjectParser.scala`
- `shared/routes/HttpRouteSupport.scala`
- `battle/routes/BattleJsonObjectParser.scala`
- `battle/routes/BattleStateJson.scala`
- `identity/routes/IdentityRouteJsonRenderer.scala`
- `mail/database/MailFileJsonParser.scala`
- `forum/routes/ForumRouteJsonRenderer.scala`
- `governance/routes/GovernanceRouteJsonRenderer.scala`
- `replay/support/ReplayFramesJsonCodec.scala`

这带来的问题：

- 字段名是字符串，散落在 parser/renderer 中。
- request 和 response 的结构没有统一 codec 作为 single source of truth。
- enum 的 wire value 可能序列化和反序列化不对称。
- 前端 TypeScript 类型和后端 Scala 类型很难自动对齐。
- object 字段改名时，编译器很难发现所有 JSON 字符串位置。

相比之下，sample 的 circe codec 虽然也需要维护，但它把对象结构、字段、enum wire value 绑定到 Scala 类型上，contract drift 风险更低。

### 3.7 数据库边界

当前公共 PostgreSQL 支持在：

- `shared/database/PostgresSupport.scala`

设计：

- `DriverManager.getConnection`
- `withConnection`
- `withStatement`
- `withResultSet`
- `withTransaction`

优点：

- 简单直接。
- 对 memory/file/postgres 三种存储模式切换友好。

不足：

- 没有连接池。
- 每次 `withConnection` 都可能新建连接。
- 没有 `Resource` 管理应用级 datasource 生命周期。
- JDBC 阻塞没有放入 effect 的 blocking pool。
- transaction 边界分散在 repository 或 query 内部。
- 部分 repository 可能自己再写 transaction helper，导致规则不统一。

sample 的 `DatabaseSession.withTransactionConnection` 更适合作为统一的应用服务事务边界。

### 3.8 Error response 和错误模型

TPsys 当前错误处理分散在：

- `BattleRouteErrorMapper`
- `ForumRouteErrorMapper`
- `ReplayRouteErrorMapper`
- 各 domain route 中的 `jsonError`
- `BackendAPIResponse.jsonError`
- `HttpRouteSupport.sendJsonError`

这种方式可以让每个 domain 输出自己的错误码，但问题是：

- HTTP status 规则不集中。
- APIMessage endpoint 和旧 route handler 可能映射不同。
- error JSON shape 容易出现不一致。
- route 层需要知道太多业务错误细节。

sample 用 `HttpError` 统一表达 HTTP 层错误类别，然后由 router 统一映射。这种模式更容易保证 contract 一致。

### 3.9 认证安全

TPsys 当前：

- `identity/ports/PasswordHasher.scala`
- `Sha256PasswordHasher`

问题：

- 使用单次 SHA-256。
- 没看到 per-user salt。
- 不具备 PBKDF2/bcrypt/argon2 这类 password hashing 的成本因子。

sample 使用 PBKDF2 + salt + iterations，安全性明显更好。

TPsys 已经有 `PasswordHasher` port，这是优点，因为替换实现不需要改大量业务代码。后续可以新增 `Pbkdf2PasswordHasher`，再设计 legacy hash migration。

### 3.10 当前 TPsys 做得更好的地方

不能简单说 sample 全面优于 TPsys。TPsys 在业务建模上有明显更复杂、也更接近游戏项目的部分：

- battle 已经拆分出 `queue/session/runtime/world/combat/actors/abilities/results` 等业务域。
- battle object 已经按 `core/player/weapon/projectile/pickup/skill/result/replay` 等方向拆分。
- 有大量 value object，例如 battle id、ticket id、duration、position、weapon state 等。
- storage 支持 memory/file/postgres 多模式，适合本地 demo、测试和部署之间切换。
- repository interface 较多，domain service 不完全绑定 PostgreSQL。
- 游戏后端有 authoritative state、queue、session、runtime tick、bot、replay、settlement 等 sample 不涉及的复杂领域。

所以正确路线不是“把 TPsys 改成 sample 的小项目结构”，而是把 sample 的边界技术引入 TPsys，让 TPsys 现有领域模型跑在更强的基础设施层上。

## 4. 关键差距表

| 维度 | sample | TPsys 当前 | TPsys 不足 |
| --- | --- | --- | --- |
| HTTP server | http4s + Ember | `com.sun.net.httpserver.HttpServer` | route handler 手写，HTTP 语义不够类型化 |
| Effect | cats-effect `IO` | 自定义 `BackendIO` + 直接副作用 | 资源安全、blocking、错误组合能力不足 |
| 生命周期 | `Resource` | 手动 start + sleep | shutdown/resource release 不清晰 |
| JSON | circe Encoder/Decoder | 手写 parser/renderer | contract drift 风险高 |
| API 模式 | `XXXAPIMessage.plan(connection)` | Message + Planner + Endpoint + RouteHandler 并存 | 层次偏多，入口重复 |
| DB 连接 | HikariCP pool | DriverManager direct connection | 缺连接池和统一 lifecycle |
| 事务 | `withTransactionConnection` | repository/helper 分散处理 | transaction boundary 不统一 |
| 错误处理 | `HttpError` ADT + router 统一映射 | 各 domain mapper + route 手写 | error shape/status 可能漂移 |
| 密码安全 | PBKDF2 + salt + iterations | SHA-256 | password hashing 不够现代 |
| 日志 | log4cats + http4s logger | 零散或较弱 | observability 不足 |
| 业务模型 | 简单图书系统 | 大型游戏后端 | TPsys 业务强，但边界层拖累维护性 |

## 5. TPsys 目前最主要的不足

### 5.1 route 层承担了太多职责

当前 route handler 经常同时负责：

- CORS
- method 判断
- request body 读取
- JSON parse
- command parse
- service 调用
- error mapping
- JSON response rendering
- exchange close

这些逻辑使 route 文件变厚，也让不同 domain 难以保持统一。

更好的目标是：

- route 只匹配 HTTP。
- request decoder 只负责 DTO 反序列化。
- API/use-case 只负责业务 plan。
- response encoder 只负责 DTO 序列化。
- error mapper 统一处理错误到 HTTP response。

### 5.2 APIMessage 抽象方向偏离了 sample

sample 的 APIMessage 是：

- API request object
- use-case plan
- typed response

TPsys 当前 APIMessage 更像：

- request 被 decode 成 enum message
- planner 再解释 enum
- response 是手写 `BackendAPIResponse`

这让 APIMessage 没有成为真正的 use-case 单元，反而成为 route handler 的另一种写法。

如果要借鉴 sample，应优先考虑：

- 删除不必要的 `Planner` 层，或把 planner 内容合并到 `XXXAPIMessage`。
- `XXXAPIMessage` 直接定义 `plan(...)`。
- request/response DTO 放到 domain 的 `objects/apiTypes`。
- route 只保留 endpoint 注册和统一执行。

### 5.3 JSON contract 不是 single source of truth

TPsys 当前前后端契约最大风险来自手写 JSON。

典型问题：

- 后端 renderer 输出字段名。
- 后端 parser 读取字段名。
- 前端 API client/type 也定义字段名。
- 三者没有统一生成或 codec 约束。

这会造成：

- 字段重命名不一定编译失败。
- enum 新增值不一定两端同时更新。
- nullable/optional 规则无法由同一个 schema 表达。
- response shape 改动容易只在运行时暴露。

sample 的 circe codec 至少能让后端 request/response 与 Scala 类型绑定。TPsys 如果要继续追求前后端 contract-level consistency，需要在此基础上进一步考虑：

- 后端 DTO codec 统一。
- 前端类型从同一 contract 生成，或至少由 contract test 对齐。
- enum wire value 统一放在一个可测试位置。

### 5.4 effect 和 resource 边界不足

TPsys 当前很多 effect 是普通函数里直接执行：

- 文件读写。
- JDBC 查询。
- HTTP exchange 写出。
- 当前时间。
- UUID/random。
- 进程内状态修改。

这些并非一定错误，但缺少统一 effect 类型后，不容易区分：

- 纯业务规则。
- 应用服务 orchestration。
- repository IO。
- route IO。
- infrastructure IO。

sample 的 `IO` 不是为了“更高级”，而是为了让副作用出现在类型签名中。

### 5.5 数据库并发和事务一致性不足

游戏后端的并发压力高于图书系统。TPsys 如果使用 PostgreSQL 存储 identity/replay/forum/social/governance 等数据，长期需要：

- 连接池。
- 统一 transaction boundary。
- 避免每次请求反复创建连接。
- 对 blocking JDBC 做隔离。
- 对跨表操作建立明确事务。

sample 在小项目里已经具备这些基础设施，TPsys 反而还没有统一。

### 5.6 route catalog 和 endpoint 注册存在重复 source of truth

`BackendRouteCatalog` 的初衷是好的：让 route 元数据可审计。

但现在：

- route handler table 是一份。
- route catalog 是一份。
- API endpoint key 又是一份。
- 前端 API client 还有一份。

这会导致“为了防 drift 又创造了新的 drift surface”。

更好的方式是让 route metadata 从 endpoint 定义派生，而不是 endpoint 和 catalog 双写。

### 5.7 安全实现需要升级

`PasswordHasher` 作为 port 是好设计，但 `Sha256PasswordHasher` 不适合作为长期 password hashing 实现。

建议：

- 新增 PBKDF2/bcrypt/argon2 实现。
- hash string 中保存 algorithm、salt、iterations、hash。
- `verify` 支持 legacy SHA-256。
- 登录成功后可迁移旧 hash。

这个可以作为独立小票处理，不应混入路由重构。

## 6. 建议的渐进迁移路线

### Phase 1：先治理边界，不重写业务

目标：

- 保留现有 service/repository/domain。
- 引入或准备引入标准 effect/JSON/HTTP 边界。

建议小票：

- 新增统一 `ApiError` / `HttpError` ADT。
- 选择一个最小 endpoint，例如 health 或 replay catalog，做 typed response。
- 给现有 JSON response 增加 contract test。

不要在这个阶段大范围改 battle runtime。

### Phase 2：把 APIMessage 简化为 use-case message

目标：

- 向 sample 靠拢，但结合 TPsys 的 domain 结构。

建议：

- 每个 domain 自己放 API 文件。
- request/response 类型放到 `objects/apiTypes`。
- `XXXAPIMessage` 自己持有 `plan(...)`。
- 删除 message/planner 的人为拆分，除非 planner 真的是可复用的纯业务 planner。
- route 只负责注册支持哪些 message。

示意目标：

```scala
final case class BattleQueueJoinAPIMessage(...) extends BackendAPIMessage[BattleQueueJoinResponse] {
  def plan(context: BattleApiContext): BackendIO[BattleQueueJoinResponse] = ...
}
```

如果迁移到 cats-effect，则目标应变为：

```scala
final case class BattleQueueJoinAPIMessage(...) extends APIMessage[BattleQueueJoinResponse] {
  def plan(context: BattleApiContext): IO[BattleQueueJoinResponse] = ...
}
```

### Phase 3：JSON codec 逐步替换手写 renderer/parser

优先级：

1. API request/response DTO。
2. enum wire value。
3. battle state snapshot。
4. replay frame。
5. 文件持久化 JSON。

不要一次替换所有 JSON。应从 contract 风险最高、前端最依赖的接口开始。

### Phase 4：HTTP 层渐进迁移

可以选择两条路线：

- 保守路线：继续保留 `HttpServer`，但 route handler 变薄，内部复用 typed decoder/encoder。
- 现代路线：引入 http4s，先迁移一个 domain route，再逐步扩大。

如果选择 http4s，不建议一次改全站。应先迁移：

- `HealthRoutes`
- `IdentityRoutes` 中一个只读接口
- `BattleQueueStatus` 或 `ReplayCatalog`

确认 build/test 稳定后，再迁移更复杂的 battle command/state stream。

### Phase 5：数据库连接池与事务边界

建议引入一个类似 sample 的 `DatabaseSession`，但要适配 TPsys 的多存储模式：

- memory/file 模式不需要 PostgreSQL datasource。
- postgres 模式初始化 Hikari pool。
- repository 不直接 `DriverManager.getConnection`。
- 跨 repository 操作通过 application service 的 transaction context 传递。

这一步要非常小心，因为涉及数据一致性和部署配置。

### Phase 6：密码哈希升级

独立处理：

- 新增强 password hasher。
- 保留 legacy verifier。
- 登录成功迁移旧 hash。
- 加测试覆盖。

不要和 HTTP/JSON 迁移混在一个 PR。

## 7. 对当前后端的架构判断

TPsys 当前后端不是“没有架构”，而是业务层和基础设施层发展不均衡：

- 业务层：battle 领域拆分、value object、domain rules 已经有明显结构。
- 基础设施层：HTTP、JSON、effect、DB lifecycle 仍然偏手写、偏原始。
- API 层：正在尝试 APIMessage，但抽象方向目前偏复杂，没有像 sample 一样真正减少 route 负担。
- contract 层：仍然缺少统一 codec/schema 作为前后端契约来源。

因此当前最重要的优化方向是：

1. 减少 route handler 中的手写协议逻辑。
2. 让 API request/response 成为 typed DTO。
3. 用 codec 替代散落 JSON 字符串。
4. 把 APIMessage 简化为 use-case plan，而不是 message + planner + endpoint 三层。
5. 用标准 effect/resource 管理 DB 和 HTTP lifecycle。
6. 保留现有 battle domain 的拆分，不要为了迁移基础设施而打散业务模型。

## 8. 建议优先级

推荐下一步不是直接全量迁移 http4s，而是先做一个可验证的小票：

### BE-API-BOUNDARY-01

目标：

- 选择一个低风险 endpoint，例如 `HealthAPI` 或 `ReplayCatalogAPI`。
- 定义明确 request/response DTO。
- 建立 typed encoder/decoder 或至少集中 renderer/parser。
- 删除该 endpoint 的 planner/route 重复逻辑。
- 保证旧路径兼容。

验收：

- `backend:compile` 通过。
- 该 endpoint 的 contract test 通过。
- route handler 代码变薄。
- 不影响 battle runtime。

### BE-API-BOUNDARY-02

目标：

- 迁移 battle queue 的一个只读接口，例如 status。
- request/query DTO 与 response DTO 放入 `battle/objects/apiTypes`。
- route 只处理 method/path，业务 plan 调 service。

验收：

- 前端等待房间 status 调用不变。
- 后端 response shape 不变。
- parser/renderer 字段集中。

### BE-EFFECT-DB-01

目标：

- 设计 TPsys 版 `DatabaseSession`，先不替换所有 repository。
- postgres storage mode 下使用 Hikari datasource。
- 保留 memory/file mode。

验收：

- 不改变业务 API。
- 不改变 schema。
- 只新增基础设施能力。

## 9. 最终判断

sample 给出的标准设计值得学习的不是“文件名叫 APIMessage”，而是以下原则：

- API 是 typed message。
- message 自己就是 use-case plan。
- route 只是统一分发。
- JSON codec 绑定 request/response 类型。
- effect 用 `IO` 表达。
- 资源用 `Resource` 管理。
- DB transaction 在统一边界内执行。
- error 用 ADT 表达并集中映射。

TPsys 现在最大的问题不是 battle 业务不够拆，而是边界层不够现代、不够统一，导致 route、JSON、APIMessage、DB、error mapper 都有重复代码和 contract drift 风险。

后续重构应保持小步可验证：先挑一个 endpoint，把它做成 sample 风格的 typed API boundary；验证通过后再逐步迁移 battle queue、identity、replay 等更核心接口。

## 10. 迁移状态附录（2026-05-21）

本节记录基于上述路线已经落地的当前状态。前文第 3 到第 9 节保留为迁移前诊断，因此其中“当前缺少 http4s/circe/Hikari/APIMessage 清理”等表述不再全部代表最新代码状态。

### 10.1 已完成的边界迁移

- HTTP 默认入口已经切换为 `slaydemo.backend.http4s.BackendHttp4sApp`，`package.json` 的 `backend:dev` 默认启动 http4s，legacy `com.sun.net.httpserver.HttpServer` 入口保留在 `backend:dev:legacy`。
- `backend/build.sbt` 已引入 `cats-effect`、`http4s-dsl`、`http4s-ember-server`、`http4s-circe`、`circe-core/generic/parser`、`HikariCP`、`log4cats-slf4j` 和 `slf4j-simple`。
- `BackendRuntime` 集中组装 service/repository/runtime，http4s 与 legacy 入口复用同一套运行时 wiring，避免启动入口重复创建业务依赖。
- `PostgresSupport` 已从直接 `DriverManager` 连接改为 Hikari datasource pool，并提供 `closeAll()` 给 http4s/legacy shutdown 边界释放资源。
- `PostgresSupport` 已新增 `connectionResource`、`withConnectionIO` 和 `withTransactionIO`，具备 `Resource` 关闭连接、`IO.blocking` 包裹 JDBC、commit/rollback 和恢复 autoCommit 的基础能力。
- `PostgresSupport` 已新增同步 `withTransactionConnection` 作为 repository 过渡入口，`PostgresForumRepository` 的写事务和 `PostgresReplayRepository.saveReplay` 已迁移到该共享边界，不再私有维护 connect/commit/rollback/close。
- `PostgresBattleResultRepository.save` 已迁移到 `withTransactionConnection`，battle result upsert 写入使用共享事务边界；读取查询继续使用普通 pooled connection。
- `PostgresBotProfileRepository.save` 已迁移到 `withTransactionConnection`，bot profile upsert 和初始化 seed 写入使用共享事务边界；列表查询继续使用普通 pooled connection。
- `PostgresMailRepository.save` 和 `markRead` 已迁移到 `withTransactionConnection`，邮件 upsert 与已读状态更新使用共享事务边界；按 owner 查询继续使用普通 pooled connection。
- `PostgresFriendRequestRepository.createIfAbsent` 的 INSERT 和 `save` upsert 已迁移到 `withTransactionConnection`，好友申请写入使用共享事务边界；查询和幂等预读继续使用普通 pooled connection。
- `PostgresGovernanceRepository.saveAdjustment` 和 `saveReviewNotification` 已迁移到 `withTransactionConnection`，治理贡献调整与审核通知 upsert 使用共享事务边界；列表查询继续使用普通 pooled connection。
- `PostgresReplayRepository.saveComment` 已迁移到 `withTransactionConnection`，replay comment 写入使用共享事务边界；replay/comment 查询继续使用普通 pooled connection。
- `PostgresIdentityAccountRepository.create`、`replacePasswordHash` 和 `updateSession` 已迁移到 `withTransactionConnection`，账号创建、密码 hash 升级和 session 更新使用共享事务边界；认证与列表查询继续使用普通 pooled connection。
- `PasswordHasher` 已新增 PBKDF2 + salt + iterations 实现，并保留 legacy SHA-256 校验兼容；登录成功后可升级旧 hash。
- 旧 `BackendAPIMessage`、`BackendIO`、`BackendAPIMessagePlanner`、battle/replay/health 的 APIMessage planner 层已经删除，`/api/...` 兼容路径直接由 route alias 和 parser 处理。
- 主要后端业务域已经有 http4s route 和 focused contract：health、identity、mail、replay、social、forum、governance、bot profile、battle queue、battle room、battle state、battle command、battle result。
- request/response 边界类型已按 domain 放入 `objects/apiTypes`，例如 battle、bots、forum、governance、mail、replay、social。
- http4s route 的错误响应已收敛到 typed `HttpApiError`；health 的旧 405 契约保持 `{"error":"method_not_allowed"}`，并由 `HealthErrorResponse` DTO 表达，避免裸 JSON 字符串散落。
- health 的 legacy route 与 http4s route 已共用 `HealthJsonCodec`，`HealthResponse` 和 `HealthErrorResponse` 的 JSON 字段由同一组 Circe encoder 约束。
- bot profile 的 legacy route 已删除手写 `BotProfileRouteJsonRenderer`，改为复用 `BotProfilesResponse` shared API DTO/Circe encoder。
- mail 的 legacy route 已删除手写 `MailRouteJsonRenderer`，列表和已读成功响应改为复用 `MailListResponse`、`MailReadResponse` shared API DTO/Circe encoder。
- replay 的 legacy HTTP 响应已改为复用 shared API DTO/Circe encoder：catalog 使用 `ReplayCatalogResponse`，detail/comment 使用 `ReplayDetailResponse`、`ReplayCommentsResponse`、`ReplayCommentWrapperResponse`；文件持久化 JSON renderer 保留在 database 边界。
- social 的 legacy route 已删除手写 `SocialRouteJsonRenderer`，好友申请列表、创建和响应成功响应改为复用 `FriendRequestListResponse`、`FriendRequestCreateResponse`、`FriendRequestRespondResponse` shared API DTO/Circe encoder。
- forum 的 legacy route 已删除手写 `ForumRouteJsonRenderer`，主题列表、详情、创建、回复和投票成功响应改为复用 `ForumTopicListResponse`、`ForumTopicWrapperResponse` shared API DTO/Circe encoder。
- governance 的 legacy route 已删除手写 `GovernanceRouteJsonRenderer`，贡献调整和审核通知的列表/创建成功响应改为复用 shared API DTO/Circe encoder；文件持久化 JSON renderer 保留在 database 边界。
- identity 的 legacy route 已删除手写 `IdentityRouteJsonRenderer`，认证、账户列表和错误响应改为复用 `IdentityApi` 中的 DTO/Circe encoder。
- battle result 的 legacy route 已删除手写 `BattleResultRouteJsonRenderer`，列表和记录创建成功响应改为复用 `BattleResultListResponse`、`BattleResultRecordResponse` shared API DTO/Circe encoder。

### 10.2 当前验证证据

- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过，覆盖 legacy route contracts、http4s route contracts、repository wiring、storage config、password hasher、battle runtime、battle queue、finish projection 等。
- focused contract 已分别验证 social、forum、governance、replay catalog、bot profile、legacy health 和 http4s health。
- legacy health 与 http4s health focused contracts 已验证共享 codec 后的成功响应和 405 错误响应 shape 保持不变。
- legacy bot profile 与 http4s bot profile focused contracts 已验证共享 DTO/Circe 输出后列表响应和 method-not-allowed 错误仍保持兼容。
- legacy mail 与 http4s mail focused contracts 已验证共享 DTO/Circe 输出后邮件列表、metadata 字段、已读响应和错误响应仍保持兼容。
- legacy replay route 与 http4s replay catalog focused contracts 已验证共享 DTO/Circe 输出后 catalog 列表、detail frames JSON、comments、selected settlement 和 alias path 仍保持兼容。
- legacy social 与 http4s social focused contracts 已验证共享 DTO/Circe 输出后好友申请列表、创建、响应和错误映射仍保持兼容。
- legacy forum 与 http4s forum focused contracts 已验证共享 DTO/Circe 输出后主题列表、详情、创建、回复、投票和错误映射仍保持兼容。
- legacy governance 与 http4s governance focused contracts 已验证共享 DTO/Circe 输出后贡献调整、审核通知、空列表和错误映射仍保持兼容。
- legacy identity 与 http4s identity focused contracts 已验证共享 DTO/Circe 输出后注册、登录、当前会话、账户列表和错误映射仍保持兼容。
- legacy battle result 与 http4s battle result focused contracts 已验证共享 DTO/Circe 输出后列表过滤、空列表、记录创建和错误映射仍保持兼容。
- `PostgresSupportContractTest` 已验证 Resource 会关闭连接、事务成功会 commit、事务失败会 rollback，并在两种情况下恢复 autoCommit。
- `PostgresRepositoryBoundaryContractTest` 与 battle result legacy/http4s focused contracts 已验证 battle result Postgres 写入切换共享事务边界后 repository 边界和 API contract 不变。
- `PostgresRepositoryBoundaryContractTest` 与 bot profile legacy/http4s/service focused contracts 已验证 bot profile Postgres 写入切换共享事务边界后 repository 边界和 API contract 不变。
- `PostgresRepositoryBoundaryContractTest` 与 mail legacy/http4s/service focused contracts 已验证 mail Postgres 写入和已读更新切换共享事务边界后 repository 边界和 API contract 不变。
- `PostgresRepositoryBoundaryContractTest` 与 social legacy/http4s/friend request service focused contracts 已验证 social Postgres 写入切换共享事务边界后 repository 边界和 API contract 不变。
- `PostgresRepositoryBoundaryContractTest` 与 governance legacy/http4s/service focused contracts 已验证 governance Postgres 写入切换共享事务边界后 repository 边界和 API contract 不变。
- `PostgresRepositoryBoundaryContractTest` 与 replay legacy/http4s/service focused contracts 已验证 replay comment Postgres 写入切换共享事务边界后 repository 边界和 API contract 不变。
- `PostgresRepositoryBoundaryContractTest` 与 identity legacy/http4s/service/password focused contracts 已验证 identity Postgres 写入切换共享事务边界后 repository 边界、认证 API contract 和 hash 升级行为不变。
- `PostgresRepositoryBoundaryContractTest` 已新增写事务守护：repository 文件中的 `INSERT`、`UPDATE` 和 `executeUpdate` 最近连接边界必须是 `withTransactionConnection`，防止后续写入退回普通 connection。
- 残留扫描显示 `BackendAPIExchangeRouter`、`apiEndpoints`、`ApiMessageRouteContexts`、`BackendAPIEndpoint`、`BackendAPIMessagePlanner`、`BackendIO`、`jsonTextResponse`、`DriverManager` 和裸 `getConnection(` 在 `backend/src/main/scala`、`backend/src/test/scala` 中无命中。
- `BackendApiBoundaryContractTest` 已新增守护，禁止 main source 中重新出现通用 `*RouteJsonRenderer.scala`，要求普通 HTTP API 响应继续复用 shared `objects/apiTypes` codec。
- http4s route 中直接 `apiError(status = ...)` 或 `apiError(Status...)` 的业务调用无残留，只保留 `Http4sRouteSupport` 的兼容 overload。

### 10.3 仍未完成或需要后续小票处理的事项

- JSON contract 仍不是全项目 single source of truth。http4s 新 route 已使用 Circe DTO，但 legacy route、文件持久化 JSON、battle state snapshot、replay frames 仍有手写 parser/renderer。
- Postgres repository 写入边界已从分散 `withConnection` 收敛到共享 `withTransactionConnection`，并由 contract guard 防止 `INSERT`、`UPDATE`、`executeUpdate` 重新落回普通 connection。尚未完成的是更进一步的 use-case 级 `withTransactionIO` 编排；这需要重新设计跨 repository 事务范围，不能和当前 repository 级收口混做。
- http4s 已成为默认入口，但 legacy route 仍保留用于兼容和 contract 对照；后续是否删除 legacy 入口需要单独决策。
- 前后端 contract 仍主要依赖后端 contract tests 与手写 TypeScript 类型，没有 OpenAPI/schema/codegen 级别的统一生成机制。
- `compare.md` 的前半部分是迁移前分析，不应再单独作为当前架构事实；后续 review 应同时参考本附录和实际代码。

### 10.4 Phase 7 完成审计

本轮按“至少达成 phase7”的要求，把前文迁移路线拆成以下可审计清单：

- Phase 1 边界治理：已落地统一 http4s 错误模型、shared API DTO/Circe encoder、focused route contract；证据是 `backend/src/main/scala/slaydemo/backend/http4s/Http4sRouteSupport.scala`、各 domain `objects/apiTypes` 目录和 `BackendApiBoundaryContractTest`。
- Phase 2 APIMessage 简化：旧 `BackendAPIMessage`、`BackendIO`、`BackendAPIMessagePlanner` 层已删除，`/api/...` 兼容路径由真实 route alias 处理；证据是 `BackendApiBoundaryContractTest` 对删除文件、删除目录和旧名称残留的扫描守护。
- Phase 3 JSON codec 收敛：普通 HTTP API 响应已从 route-specific renderer 收敛到 shared DTO/Circe encoder；证据是 main source 中无 `*RouteJsonRenderer.scala`，并由 `BackendApiBoundaryContractTest.legacyRouteJsonRenderersStayDeleted` 持续守护。
- Phase 4 HTTP 迁移：默认后端入口已切到 `slaydemo.backend.http4s.BackendHttp4sApp`，主要业务域都有 http4s route 和 focused contract；证据是 `backend/build.sbt` 的 `Compile / mainClass`、`package.json` 的 `backend:dev` 和 `backend/src/test/scala/slaydemo/backend/http4s`。
- Phase 5 DB 生命周期和事务边界：`PostgresSupport` 已使用 Hikari pool、`Resource`、`IO.blocking`、事务 commit/rollback 和 `closeAll`；repository 写入已迁移到共享事务边界，并由 `PostgresRepositoryBoundaryContractTest` 守护。
- Phase 6 密码哈希升级：`Pbkdf2PasswordHasher` 已支持 PBKDF2 + salt + iterations，兼容 legacy SHA-256，并由 `PasswordHasherContractTest` 与 identity contract 覆盖。
- Phase 7 验证和风险收口：已运行 `npm run backend:compile`、`npm run backend:test-contracts`、`git diff --check`。三者均通过；Java 25/sbt launcher 的 `sun.misc.Unsafe` 是外部 launcher warning，不是项目 unsafe 代码。并行 sbt 曾打印 Windows 文件占用提示，单独重跑 `backend:test-contracts` 已通过。

审计结论：后端迁移路线已达到 phase7 的稳定验证状态。剩余项不是 phase7 阻塞，而是后续独立 ticket：battle state snapshot/replay frames/file persistence JSON 的进一步 codec 收敛、是否移除 legacy route、以及是否把 repository 级事务边界提升到 use-case 级 `withTransactionIO`。
