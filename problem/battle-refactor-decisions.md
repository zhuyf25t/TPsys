# Battle Refactor Decisions

更新日期：2026-05-27

## 1. 当前确认事实

`services/battle` 顶层已经收敛为四类目录：

```text
services/battle/
  api/
  database/
  objects/
  routes/
```

`system/api` 当前支持：

```scala
trait APIMessage[Response]:
  def plan(connection: Connection): IO[Response]

trait APIWithTokenMessage[Response] extends APIMessage[Response]
```

API name 当前由 message class 推导：

```text
BattleQueueJoinAPIMessage -> /api/battlequeuejoin
BattleStateReadAPIMessage -> /api/battlestateread
BattleCommandAPIMessage -> /api/battlecommand
```

因此 battle route 不需要 rewrite，也不需要维护 `List[String]` API 名称目录。

## 2. 已完成事项

### APIMessage

- battle API 已迁到 `services/battle/api/<domain>/XXXAPIMessage.scala`。
- 9 个入口 APIMessage 都是 `final case class ... extends APIWithTokenMessage[Response]`。
- 9 个入口 APIMessage 都有 `plan(connection): IO[Response]`。
- state response render 已拆到 `api/state/BattleStateResponseRenderer.scala`。
- result list/record 的 Postgres 路径已经支持 connection-backed execution。

### routes

- `routes` 不再解析 battle JSON。
- `routes` 不再判断 battle path。
- `routes` 不再映射 battle 业务错误。
- `BattleRoutes` 不再有 `BattleAPIMessageSpec`。
- `BattleRoutes` 不再有 `apiMessageNames: List[String]`。
- `BattleRoutes` 返回 `List[RegisteredAPIMessage]`。
- connection-backed battle results API 已在 `BattleRoutes` 中直接使用 sample 风格 `apiWithToken[Message, Response]` 注册。
- `BattleRoutes.connectionBackedResultApiMessages` 已经是无参 typed `val`。
- `BattleRoutes.serviceInjectedRuntimeApiMessages` 显式隔离仍需 runtime service 的 API。

### objects

- `objects/BattleEnums.scala` 是统一 enum/wire 映射位置。
- 重要业务概念已有 value object，例如 `BattleId`、`PlayerId`、`TicketId`、`DurationMillis`、`BattleTick`。
- `objects/apiTypes` 已按 command/queue/room/state/results/shared 拆分。
- `apiTypes` 主要承载 request/response DTO 和 Circe encoder/decoder。
- `BattleStateReadAPIRequestError` 和 state request `decode(json)` wrapper 已移到 `api/state`。
- 未使用的 `BattleResultListResponse.Empty` 已删除，response companion 不再承载非 codec 便利常量。

### database

- `database/results` 已有 `BattleResultTable.scala` 和 `BattleResultTableInitializer.scala`。
- result repository family 已迁到 `database/results`。
- replay/result file JSON compatibility 使用 Circe。
- 旧 `application/engine/persistence/ports` package 引用已归零。
- 顶层四层依赖方向审计未发现 `objects -> api/routes/database`、`database -> api/routes`、`api -> routes`。
- `queue <-> session` 最小双向依赖已修复，`session` 不再反向 import `queue`。

## 3. 关于 sample 风格 API 注册

sample 写法是：

```scala
val apiMessages: List[RegisteredAPIMessage] = List(
  api[ListBooksAPIMessage, BookListResponse],
  apiWithToken[CreateBookAPIMessage, BookRecord]
)
```

这个形态依赖一个前提：APIMessage 可以直接由 JSON decode 出来，并且 `plan(connection)` 只需要 `connection` 就能完成业务。

当前 battle 只有 `results` 已经部分满足这个前提，并且 connection-backed results 注册已经直接使用 `apiWithToken[Message, Response]`。queue/session/command/state 仍依赖内存权威运行时 service：

- `BattleQueueService`
- `BattleQueueJoinAuthorizationService`
- `BattleStateService`

这些对象不是 request DTO，也不能从 JSON decode。强行把 `BattleRoutes` 改成无参 `val apiMessages` 会导致下面两类坏方案：

- 引入全局 singleton/service locator，让 APIMessage 在 plan 内部偷偷取 service。
- 把运行时 service 塞进 `objects/apiTypes` 或 codec，破坏边界。

因此当前决策是：不维护字符串 API name，但保留 service-injected registered factory。

## 4. 决策问题

### 决策 1：`database` 是否允许暂时承载 runtime/rules

推荐：允许。

原因：

- 当前目标要求顶层只保留 `api/objects/routes/database`。
- battle 的 tick、bot、weapon、projectile、collision、pickup 规则必须有归属。
- 把这些规则塞进 APIMessage 会制造新的 god object。

代价：

- `database` 命名不是纯 persistence。
- 文档必须明确：当前 `database` 表示 battle implementation layer，不只是 table/repository。

### 决策 2：queue/session 是否现在 table 化

推荐：暂不 table 化。

原因：

- queue/session 是实时运行状态，涉及匹配、房间、tick、命令和内存一致性。
- 直接 table 化会引入事务、锁、性能、一致性和回滚问题。
- 当前目标是结构和 contract 收敛，不应混入运行时存储模型重写。

安全替代：

- 保留 `BattleQueueService` 和 `BattleStateService`。
- APIMessage 继续通过 `IO.blocking` 调用它们。
- 后续单独设计 runtime store port 或 table-backed session store。

### 决策 3：BattleRoutes 是否必须现在变成无参 `val apiMessages`

推荐：暂不强行改。

原因：

- battle 多数 API 还需要 runtime service。
- 无参 `val` 只有在 connection-backed 或 typed runtime context 完成后才安全。
- 当前已经消除了 route 层手写 String API name，达到了更关键的类型安全目标。

当前安全形态：

```scala
def apiMessages(
  context: BattleAPIRuntimeContext,
  resultRegistration: BattleResultAPIRegistration
): List[RegisteredAPIMessage]
```

后续达成条件：

- queue/session runtime 被 table/context 化。
- 或 `system/api` 引入类型安全 runtime context，而不是隐藏全局状态。
- 详细上下文分析见 `problem/battle-api-runtime-context-plan.md`。

### 决策 4：objects 是否只能有 case class + companion codec

推荐：目标上同意，实施上渐进。

原因：

- 纯对象层应该尽量是 immutable data、ADT、value object、codec。
- 战斗规则需要归属，不能硬塞进 API。
- 若严格执行，规则应归入 `database/<domain>/rules` 或新增第五层。

当前建议：

- `objects/core/player/weapon/...` 继续收敛为数据和 ADT。
- `objects/apiTypes` 继续只放 DTO/codec。
- 规则保留在 `database/<domain>`，不要新增到 `objects`。

## 5. 已完成的后续票据

```text
ID: BE-BATTLE-RESULTS-REGISTER-02
Verification:
  - npm run backend:compile
  - npm run backend:test-contracts
Result:
  - BattleRoutes 的 connection-backed result list 使用 apiWithToken[BattleResultListAPIMessage, BattleResultListResponse]
  - BattleRoutes 的 connection-backed result record 使用 apiWithToken[BattleResultRecordAPIMessage, BattleResultRecordResponse]
  - 两个 registeredConnectionBacked 中转方法已删除
```

```text
ID: BE-BATTLE-ROUTES-CATALOG-03
Verification:
  - npm run backend:compile
  - npm run backend:test-contracts
Result:
  - connection-backed results 注册被提升为 BattleRoutes.connectionBackedResultApiMessages
  - queue/room/state/command 继续显式保留在 serviceInjectedRuntimeApiMessages
```

```text
ID: BE-BATTLE-RUNTIME-CONTEXT-04
Verification:
  - npm run backend:compile
  - npm run backend:test-contracts
Result:
  - BattleAPIMessageServices 已替换为 BattleAPIRuntimeContext
  - HttpApiServices.battleServices 已重命名为 battleRuntimeContext
  - battle API runtime dependency bundle 的命名更准确
```

```text
ID: BE-BATTLE-RUNTIME-CONTEXT-05
Verification:
  - npm run backend:compile
  - npm run backend:test-contracts
Result:
  - resultRepository 已从 BattleAPIRuntimeContext 移出
  - BattleResultAPIRegistration 改为带数据的 ADT
  - RepositoryBacked(resultRepository) 只在 repository-backed compatibility path 携带 result repository
```

```text
ID: BE-BATTLE-RESULT-REGISTRATION-06
Verification:
  - npm run backend:compile
  - npm run backend:test-contracts
Result:
  - 新增 BattleHttp4sResultBackend
  - ConnectionBacked 必须携带 JDBC connection resource
  - RepositoryBacked 必须携带 result repository
  - 删除 HttpApiServices 中 result registration / connection resource 的可错配双字段
```

```text
ID: BE-BATTLE-APITYPES-BOUNDARY-07
Verification:
  - npm run backend:compile
  - npm run backend:test-contracts
Result:
  - 新增 problem/battle-apitypes-boundary-audit.md
  - objects/apiTypes 当前基本只承载 DTO 与 Circe codec
  - 后续 BE-BATTLE-PLAYER-DTO-FLAT-08 已删除 mergeEncodedObjects
```

```text
ID: BE-BATTLE-PLAYER-DTO-FLAT-08
Verification:
  - npm run backend:compile
  - npm run backend:test-contracts
Result:
  - BattleStatePlayerResponse 已改为显式扁平 DTO
  - identity/control/weapon/vitals 四个中间分组 DTO 已删除
  - mergeEncodedObjects 已删除
```

## 6. 当前下一票建议

```text
ID: BE-BATTLE-COMPLETION-AUDIT-09
Goal: 对照原始目标做一次 services/battle 完成度审计，列出已满足、未满足、无法安全强推的要求。
Boundary:
  - services/battle
  - problem 文档
Forbidden:
  - 不改业务行为
  - 不改 JSON 字段
  - 不改前端
Verification:
  - npm run backend:compile
  - npm run backend:test-contracts
```

不建议现在为了无参 `val apiMessages` 引入全局 runtime registry，也不建议现在 table 化 queue/session。
