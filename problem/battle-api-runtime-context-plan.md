# Battle API Runtime Context Plan

更新日期：2026-05-27

## 1. 票据

```text
ID: BE-BATTLE-API-CONTEXT-03
Goal: 分析 queue/room/state/command API 从 service-injected registered factory 迁到 sample 风格 typed registration 所需的最小上下文模型。
Boundary:
  - services/battle/routes
  - services/battle/api/{queue,room,state,command}
  - system/api 仅作为设计参考，本票不改
Forbidden:
  - 不引入全局 service locator
  - 不把 runtime service 放进 objects/apiTypes
  - 不 table 化 queue/session
  - 不改 JSON 字段
  - 不改前端
```

## 2. 当前 system/api 能力

当前 `system/api` 的核心模型是：

```scala
trait APIMessage[Response]:
  def plan(connection: Connection): IO[Response]

trait APIWithTokenMessage[Response] extends APIMessage[Response]
```

当前 sample 风格注册是：

```scala
apiWithToken[Message, Response]
```

它要求：

- `Message` 可以从 JSON 直接 decode。
- `Message.plan(connection)` 只依赖 `Connection` 和 message 自己携带的 immutable request data。
- `Message` 构造函数不能要求 runtime service 实例。

当前 `RegisteredAPIMessage.apiWithToken` 做的事情是：

```text
Json -> Decoder[Message] -> message.plan(connection) -> Encoder[Response]
```

所以它不适合直接 decode 出带有 `BattleQueueService` 或 `BattleStateService` 字段的 message。

## 3. 当前 battle API runtime dependency matrix

| APIMessage | 当前 runtime service | plan 内效果 | 为什么不能直接 apiWithToken |
|---|---|---|---|
| `BattleQueueJoinAPIMessage` | `BattleQueueService`, `BattleQueueJoinAuthorizationService` | authorize session/handle，再 join queue | service 不能从 JSON decode，也不应该成为 DTO 字段 |
| `BattleQueueStatusAPIMessage` | `BattleQueueService` | 查询 ticket 对应排队快照 | queue 是内存 runtime store，不是 connection table |
| `BattleQueueLeaveAPIMessage` | `BattleQueueService` | 离开排队 | queue 状态在 runtime service 内 |
| `BattleRoomSnapshotAPIMessage` | `BattleQueueService` | 读取等待房间快照 | room snapshot 由 queue runtime 管理 |
| `BattleRoomHeartbeatAPIMessage` | `BattleQueueService` | 更新房间参与者 heartbeat | heartbeat 是 runtime side effect |
| `BattleStateReadAPIMessage` | `BattleStateService` | 读取权威 battle aggregate state | battle state 是 in-memory authoritative runtime |
| `BattleCommandAPIMessage` | `BattleStateService` | 接受并应用玩家 command | command 会进入 authoritative runtime |
| `BattleResultListAPIMessage` | connection 或 repository fallback | 查询战报 | connection-backed 分支已经能 `apiWithToken` |
| `BattleResultRecordAPIMessage` | connection 或 repository fallback | 保存战报 | connection-backed 分支已经能 `apiWithToken` |

结论：

- `results` 已经具备 sample 风格注册的前提，因为它有 `BattleResultTable` 和 `BattleResultTableInitializer`。
- queue/room/state/command 不具备这个前提，因为它们依赖实时内存运行时，而不是单纯 `Connection`。

## 4. 不应该做的方案

### 方案 A：把 service 字段留在 APIMessage，然后强行 derive Decoder

不可接受。

原因：

- Circe 不能安全 decode `BattleQueueService` / `BattleStateService`。
- 如果给 service 写 fake decoder，就是隐藏全局状态。
- 这会把 runtime dependency 伪装成 request data，破坏 contract-level consistency。

### 方案 B：在 companion object 里读全局 singleton service

不可接受。

原因：

- `plan(connection)` 看起来只依赖 connection，实际依赖全局可变运行时。
- 测试会变脆弱，多实例 server 会出现上下文污染。
- 违反 side-effect boundary 和显式依赖原则。

### 方案 C：把 runtime service 塞进 `objects/apiTypes`

不可接受。

原因：

- `objects/apiTypes` 是 wire DTO/codec 边界。
- DTO 只应该表达 request/response shape，不应该持有运行时 service。
- 这会让前后端 contract 类型和后端运行时实现混在一起。

### 方案 D：立即 table 化 queue/session

当前不建议。

原因：

- queue/session 是实时状态，涉及匹配、heartbeat、room lifecycle、tick、command accept/apply。
- table 化会引入事务、锁、tick latency、并发一致性和恢复策略。
- 这应该是单独的大票，不能混在 route/APIMessage 注册清理里。

## 5. 可接受的渐进方案

### 阶段 1：保持显式 service-injected factory

当前做法是：

```scala
BattleQueueJoinAPIMessage.registered(queueService, authorizationService)
```

这个 registered 内部使用：

```scala
RegisteredAPIMessage.apiWithTokenFromJson[BattleQueueJoinAPIMessage, BattleQueueSnapshotResponse] { payload =>
  ...
}
```

它的优点：

- API name 仍由 `BattleQueueJoinAPIMessage` class 推导。
- route 不手写 API path。
- runtime service 是显式参数，不是隐藏全局状态。
- JSON decode 和 response encode 仍在 API boundary。

这是当前最安全的迁移状态。

### 阶段 2：引入 battle-specific typed context，但不伪装成无参 val

如果后续要减少 `BattleRoutes` 里的 service 参数传递，可以引入显式 context：

```scala
final case class BattleAPIRuntimeContext(
  queueService: BattleQueueService,
  joinAuthorizationService: BattleQueueJoinAuthorizationService,
  stateService: BattleStateService
)
```

使用方式应保持显式：

```scala
def apiMessages(context: BattleAPIRuntimeContext): List[RegisteredAPIMessage]
```

这不是最终 sample 无参 val，但比散落参数更清晰。它的边界应在 `services/battle/routes` 或 `services/battle/api`，不能放入 `objects/apiTypes`。

### 阶段 3：如果要系统级支持 context，应改 system/api，而不是使用全局状态

可选设计：

```scala
trait APIMessageWithContext[Context, Response]:
  def plan(context: Context, connection: Connection): IO[Response]
```

对应注册：

```scala
apiWithTokenAndContext[BattleRuntimeContext, BattleQueueJoinAPIMessage, BattleQueueSnapshotResponse](context)
```

这个方向比 service locator 类型安全，但会影响 `system/api`，需要单独票据，因为它改变的是全后端 APIMessage 抽象。

### 阶段 4：connection-backed queue/session 才能真正无参 sample 化

只有当 queue/session/command/state 的运行时依赖能通过 `Connection` 或明确的 typed context 提供时，才可以考虑：

```scala
val apiMessages: List[RegisteredAPIMessage] = List(
  apiWithToken[BattleQueueJoinAPIMessage, BattleQueueSnapshotResponse],
  apiWithToken[BattleStateReadAPIMessage, BattleStateResponse],
  apiWithToken[BattleCommandAPIMessage, BattleCommandAcceptedResponse]
)
```

当前还没有这个条件。

## 6. 当前推荐决策

推荐保持下面的分层：

```text
BattleRoutes.connectionBackedResultApiMessages
  无参 typed val
  只放 results 这类 connection-backed API

BattleRoutes.serviceInjectedRuntimeApiMessages(services)
  显式 runtime service 注入
  放 queue/room/state/command
```

这样做的好处：

- 已经完成的 results sample 化不会倒退。
- 仍需 runtime service 的 API 不会伪装成无参注册。
- route catalog 的差异非常清楚：哪些 API 是 connection-backed，哪些 API 仍是 runtime-backed。
- 不引入 String API name。
- 不引入隐藏全局状态。

## 7. 下一步实现建议

```text
ID: BE-BATTLE-RUNTIME-CONTEXT-04
Result:
  BattleAPIMessageServices 已替换为 BattleAPIRuntimeContext。
  HttpApiServices.battleServices 已重命名为 battleRuntimeContext。
  route catalog 仍不维护 String API name。
  queue/room/state/command 仍显式 runtime-backed。
Verification:
  - npm run backend:compile passed
  - npm run backend:test-contracts passed
```

```text
ID: BE-BATTLE-RUNTIME-CONTEXT-05
Result:
  resultRepository 已从 BattleAPIRuntimeContext 移出。
  BattleResultAPIRegistration 改为带数据的 ADT。
  RepositoryBacked(resultRepository) 只在 repository-backed compatibility path 携带 result repository。
  ConnectionBacked results 继续使用无参 typed val。
Verification:
  - npm run backend:compile passed
  - npm run backend:test-contracts passed
```

```text
ID: BE-BATTLE-RESULT-REGISTRATION-06
Result:
  新增 BattleHttp4sResultBackend HTTP 层 ADT。
  ConnectionBacked(connectionResource) 强制携带 JDBC connection resource。
  RepositoryBacked(resultRepository) 强制携带 result repository。
  HttpApiServices 不再暴露可错配的 battleResultRegistration / battleConnectionResource 双字段。
Verification:
  - npm run backend:compile passed
  - npm run backend:test-contracts passed
```

## 8. 下一步实现建议

```text
ID: BE-BATTLE-PLAYER-DTO-FLAT-08
Verification:
  - npm run backend:compile passed
  - npm run backend:test-contracts passed
Result:
  BattleStatePlayerResponse 已改为显式扁平 DTO。
  identity/control/weapon/vitals 四个中间分组 DTO 已删除。
  mergeEncodedObjects 已删除。
```
