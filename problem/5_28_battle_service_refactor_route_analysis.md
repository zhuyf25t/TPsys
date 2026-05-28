# services/battle 重构路线合理性分析

## 结论

这条路线的大方向是合理的：`battle` 应该收敛成清晰的四个边界，分别是 `api`、`objects`、`routes`、`database`。其中 `routes` 只注册 APIMessage，`api` 承担 use case 编排，`objects` 承担 ADT/value object/DTO/codec，`database` 承担 PostgreSQL table 和 initializer。这个方向比现在的 `routes 注入 service context + database 目录承载大量 runtime rules + microservices 过渡目录` 更清晰，也更符合类型安全和 contract-level consistency。

但是，这条路线不能被机械理解成“所有业务逻辑都塞进 APIMessage 的 private def”。`battle` 不是普通 CRUD，它包含 queue、room、session、runtime tick、world collision、combat、abilities、actors、results、replay projection。若 `database` 严格只允许 `Table` / `TableInitializer`，`objects` 严格只允许 case class / enum / codec，而又不允许纯规则函数有独立位置，那么大规模 runtime/game rules 会被迫进入 APIMessage，最终 APIMessage 会变成新的 god service。

因此建议采用这个可执行版本：

- `services/battle/routes`：只保留 `BattleRoutes.scala`，暴露 `val apiMessages: List[RegisteredAPIMessage]`。
- `services/battle/api/<domain>`：只放 `XXXAPIMessage.scala`，负责 `plan(connection): IO[XXXResponse]` 和少量 private pure helper。
- `services/battle/objects/<domain>`：放 ADT、enum、value object、immutable state、request/response DTO、Circe codec。
- `services/battle/objects/apiTypes/<domain>`：集中 wire contract codec，不让 APIMessage 手写长 decoder。
- `services/battle/database/<domain>`：只放 `Table`、`TableInitializer`，以及必要的 row/domain mapping。
- 纯战斗规则需要明确归属。推荐短期允许保留在现有 `database/<domain>/*Rules.scala`，后续再迁到 `objects/rules` 或单独 `rules`；不建议塞进 APIMessage。

同时，目标中的 `battle/object` 建议落地为 `battle/objects`。原因是 `object` 是 Scala 关键字，当前仓库也已经使用 `services.battle.objects`，继续用复数包名更稳定。

## 当前真实结构

当前 `backend/src/main/scala/services/battle` 下存在：

- `api/`
- `database/`
- `microservices/`
- `objects/`
- `routes/`

这说明仓库已经部分靠近目标结构，但还存在两套结构并行的问题：旧的 `database` 下放了大量非数据库逻辑，新加的 `microservices` 又在 battle 下直接拆业务域，和“只能在 api/objects/routes/database 下细分”的目标冲突。

## 当前模块实现逻辑

### Route 层

核心文件：

- `route/battle/BattleHttp4sRoutes.scala`
- `services/battle/routes/BattleRoutes.scala`
- `services/battle/routes/BattleAPIRuntimeContext.scala`

当前 HTTP 层已经接入 `system.api.APIMessageRouter`，请求路径是：

```text
POST /api/{apiName}
```

`apiName` 由 `apiNameFromClassName` 推导，例如：

```text
BattleQueueJoinAPIMessage -> /api/battlequeuejoin
BattleCommandAPIMessage -> /api/battlecommand
```

这部分和目标一致，不需要 rewrite。

当前问题是 `BattleRoutes.scala` 仍然不是薄 route。它现在做了这些事：

- 注册 APIMessage。
- 给 queue/room/state/command 注入 `BattleQueueService`、`BattleStateService`。
- 给 result API 兼容 `ConnectionBacked` 和 `RepositoryBacked`。
- 使用 `apiWithTokenAndContext`，而不是目标中的 `apiWithToken`。

这意味着 route 层仍然知道 battle runtime service 组合，和目标的“route 只记录支持哪些 APIMessage”不一致。

目标形态应该类似：

```scala
object BattleRoutes:
  val apiMessages: List[RegisteredAPIMessage] =
    List(
      apiWithToken[BattleQueueJoinAPIMessage, BattleQueueJoinResponse],
      apiWithToken[BattleQueueStatusAPIMessage, BattleQueueStatusResponse],
      apiWithToken[BattleQueueLeaveAPIMessage, BattleQueueLeaveResponse],
      apiWithToken[BattleRoomSnapshotAPIMessage, BattleRoomSnapshotResponse],
      apiWithToken[BattleRoomHeartbeatAPIMessage, BattleRoomHeartbeatResponse],
      apiWithToken[BattleStateReadAPIMessage, BattleStateReadResponse],
      apiWithToken[BattleCommandAPIMessage, BattleCommandResponse],
      apiWithToken[BattleResultListAPIMessage, BattleResultListResponse],
      apiWithToken[BattleResultRecordAPIMessage, BattleResultRecordResponse]
    )
```

### API 层

当前 API 文件已经按业务拆分：

- `api/queue/BattleQueueJoinAPIMessage.scala`
- `api/queue/BattleQueueStatusAPIMessage.scala`
- `api/queue/BattleQueueLeaveAPIMessage.scala`
- `api/room/BattleRoomSnapshotAPIMessage.scala`
- `api/room/BattleRoomHeartbeatAPIMessage.scala`
- `api/state/BattleStateReadAPIMessage.scala`
- `api/command/BattleCommandAPIMessage.scala`
- `api/results/BattleResultListAPIMessage.scala`
- `api/results/BattleResultRecordAPIMessage.scala`

方向是对的，但内部形态不一致。

当前主要问题：

- queue/room/state/command 使用 `APIWithTokenContextMessage[Context, Response]`。
- `BattleQueueJoinAPIMessage` 依赖 `BattleQueueJoinAPIContext`，由 route 注入 queue service 和 authorization service。
- `BattleCommandAPIMessage` 依赖 `BattleStateService`，由 route 注入。
- `BattleCommandAPIMessage` 内部包含大量 Circe cursor decoder helper，这些应该下沉到 `objects/apiTypes/command`。
- result API 相对接近目标，因为它已经支持 `plan(connection)`，但还兼容 repository-backed context，需要后续删除兼容分支。

目标 APIMessage 形态应该是：

```scala
final case class BattleCommandAPIMessage(
  userId: UserId,
  request: BattleCommandRequest
) extends APIWithTokenMessage[BattleCommandResponse]:
  override def plan(connection: Connection): IO[BattleCommandResponse] =
    for
      state <- BattleStateTable.loadForCommand(connection, request)
      result <- applyCommand(state, request)
      _ <- BattleStateTable.save(connection, result.nextState)
    yield result.response
```

APIMessage 可以有 private helper，但 helper 应该是纯转换、错误映射或小型编排，不应该承载长 JSON decoder，也不应该承载全部 combat/world/runtime 规则。

### Objects 层

当前 `objects` 是 battle 类型安全基础，应该优先保留。

已存在较好的 ADT/value object：

- `core/`：`BattleId`、`RoomId`、`TicketId`、`PlayerId`、`DurationMillis`、`HitPoints`、`Radius`、`Damage`、`BattleVector2` 等。
- `player/`：玩家状态、生命状态、参与者类型、结算结果。
- `weapon/`：武器状态、热量状态、切枪方向和索引。
- `projectile/`：投射物状态。
- `pickup/`：拾取物定义、availability、runtime state。
- `queue/`：排队、房间、snapshot。
- `result/`：战斗结果、结算投影。
- `replay/`：回放帧。
- `BattleEnums.scala`：集中 enum 和 wireValue/fromWire。

优点：

- 大量业务概念不是裸 `String` / `Long`，而是 value object。
- 有限状态多用 enum/ADT 表达。
- 状态模型大多是 immutable case class。
- wire enum 大多有 `wireValue` / `fromWire`，具备序列化和反序列化基础。

当前问题：

- `BattleEnums.scala` 过大，但统一 enum 的方向是对的。
- `objects/apiTypes` 内有些文件仍承担较复杂的 decoder helper；这是可以接受的边界代码，但不能继续散落到 APIMessage。
- 不应该在 `apiTypes` 里重复声明已经存在于 `objects/core` 或 `objects/player` 的业务类型。
- `apiTypes` 应该表达 wire contract，不应该重新创造一套 parallel domain model。

推荐规则：

- 核心业务概念只在 `objects/<domain>` 声明一次。
- `objects/apiTypes` 只引用这些类型，负责 Encoder/Decoder/DTO。
- 如果前端需要 wire DTO，就让 `apiTypes` 成为 contract source，不要让 APIMessage 内部隐式拼 JSON。

### Database 层

当前 `database` 目录不是纯 database。它混合了：

- queue in-memory service。
- session in-memory state service。
- runtime tick 推进。
- world geometry / collision / movement。
- combat weapon/projectile/fire rules。
- abilities / bot / pickup rules。
- results repository / file JSON / PostgreSQL table。
- replay projection。

这和目录名不一致。真正符合 database 边界的文件应该是：

- `XXXTable.scala`
- `XXXTableInitializer.scala`
- row/domain mapping。
- JDBC/Cats Effect IO 边界。

当前已经有一些 PostgreSQL rule table：

- `microservices/world/database/BattleWorldRuleTable.scala`
- `microservices/runtime/database/BattleRuntimeRuleTable.scala`
- `microservices/combat/database/BattleCombatRuleTable.scala`
- `microservices/abilities/database/BattleAbilityRuleTable.scala`
- `microservices/actors/database/BattleActorRuleTable.scala`
- `database/results/BattleResultTable.scala`

这些说明“配置型规则迁 PostgreSQL”的方向已经启动。但 `queue` / `session` / `state` 仍然是 in-memory service，尚未满足 `APIMessage.plan(connection)` 的最终目标。

### Microservices 过渡层

当前 `services/battle/microservices` 下有：

- `abilities/api|objects|database`
- `actors/api|objects|database`
- `combat/api|objects|database|routes`
- `runtime/api|objects|database`
- `world/api|objects|database`

这个目录是当前最大结构问题之一：

- 它在 `battle` 下直接拆业务域，违反目标中的“业务域应在 api/objects/routes/database 下拆”。
- `combat/routes` 实际不是 route，而是 runtime combat rules。
- `api/*RuleBook` 不是 APIMessage，而是全局规则缓存。
- `objects` 和 `database` 里的内容应该分别并回 `services/battle/objects/<domain>` 与 `services/battle/database/<domain>`。

结论：`microservices` 应被视为过渡产物，最终应删除或迁移，不应成为长期结构。

## 类型安全结构分析

### ADT / enum

当前 battle 类型安全基础较好：

- `BattlePhase`、`BattleMode`、`WeaponKind`、`ProjectileKind`、`PickupKind`、`SkillKind` 等有限状态已用 enum。
- `BattleStateReadError`、`BattleCommandSubmitError`、queue/room/result 相关错误也已用 enum/ADT。
- `BattleWeaponSwitchDirection`、`BattleWeaponSwitchIndex` 对前端 command wire 值做了类型封装。

需要继续修正：

- API error 不能在业务流程内部长期传递裸字符串。
- HTTP 最终可以输出 `"battle_not_found"`，但内部应该先是 `BattleCommandSubmitError.BattleNotFound` 这类 ADT。
- 如果 `apiTypes` 里出现和 `objects/core` 类似的新 `BattleId` / `PlayerId`，应该删除重复定义，复用统一对象。

### Value object

当前已有大量 value object：

- `DurationMillis`
- `ElapsedMillis`
- `EpochMillis`
- `HitPoints`
- `Stamina`
- `Radius`
- `Damage`
- `BattleTick`
- `ClientCommandSeq`
- `BattleVector2`

这符合“避免 primitive obsession”的要求。后续迁移 PostgreSQL 时，应保持 Table 读取后立即转换为这些 value object，而不是在业务层传裸 `Long` / `Double`。

### Immutable state

当前战斗状态大多是 immutable case class，例如：

- `BattleAggregateState`
- `BattlePlayerState`
- `BattleProjectileState`
- `BattlePickupState`
- `BattleReplayFrameState`

运行时推进基本是 old state -> new state 的 copy/update 模式，这是正确方向。

当前主要副作用问题不在 domain state，而在 service：

- `InMemoryBattleQueueService` 使用 `var rooms`、`var tickets`、`lock.synchronized`。
- `InMemoryBattleStateService` 使用 `var battles`、`lock.synchronized`。

这些 mutable state 当前在 application/service 边界，短期可运行，但不符合最终 `plan(connection)` + PostgreSQL authoritative state 的目标。

## Circe 现状

当前技术基础：

- `system.api.RegisteredAPIMessage` 使用 `Decoder[Message]` 和 `Encoder[Response]`。
- `APIMessageRouter` 使用 http4s-circe 读取/返回 JSON。
- `objects/apiTypes/state/*` 已经承担 state response encoding。
- replay frame renderer 使用 Circe `deriveEncoder` 输出 JSON。

当前问题：

- `BattleCommandAPIMessage` 内部手写大量 `HCursor` decode helper。
- 部分 APIMessage companion 仍处理 request decode failure。
- response encoder 有时直接对 domain object 编码，而不是显式 `XXXResponse` DTO。

推荐规则：

- APIMessage 文件不写长 decoder。
- 复杂 decoder 可以留在 `objects/apiTypes/<domain>`，因为这是 wire boundary。
- 每个 API 最终应有明确 `XXXRequest` / `XXXResponse`，且 Response companion 提供 `given Encoder[XXXResponse]`。
- 反序列化必须有 `given Decoder[XXXAPIMessage]` 或 `given Decoder[XXXRequest]`，不能只有 `wireValue` 没有 `fromWire`。

## Cats Effect / IO 现状

当前 `system.api` 已经建立正确效果边界：

- `APIMessage.plan(connection): IO[Response]`
- `APIMessageRouter.routes(...)`
- `Resource[IO, Connection]`
- `IO.blocking` 包裹阻塞 JDBC 或旧 service 调用。

当前问题：

- battle API 大量使用 `APIWithTokenContextMessage`，绕开了 `plan(connection)` 的最终形态。
- queue/session/state 仍依赖内存 service，因此 route 必须注入 context。
- 部分 old service 是同步 mutable service，IO 只是外层包了一层 `IO.blocking`。

目标应该是：

```text
HTTP request
-> APIMessageRouter
-> RegisteredAPIMessage.apiWithToken
-> XXXAPIMessage.plan(connection)
-> Table read/write in IO
-> pure domain transition
-> Table save in IO
-> XXXResponse Encoder
```

这能让副作用边界集中在 APIMessage/database 层，domain state 和 rules 保持可测试。

## Render / wire projection 技术

后端这里的 render 不应该理解为 Phaser 画面渲染。真实画面渲染在前端 Phaser 3，后端只提供权威状态和回放 JSON。

后端 render 主要包括：

- state response：`objects/apiTypes/state/*` 把 `BattleAggregateState` 投影成前端消费的 JSON。
- replay frame：`BattleReplayFramesJsonRenderer` 把战斗状态和事件投影成 replay JSON。
- result response：`objects/apiTypes/results/*` 输出战斗结果列表和记录。

当前问题：

- replay JSON renderer 放在 `database/projections`，命名上不像 wire projection。
- state response 已在 `objects/apiTypes`，方向更正确。
- 后端不应感知 Phaser，只应稳定 DTO 字段名、enum wire value、nullable/optional 语义。

推荐：

- API response render 统一归 `objects/apiTypes`。
- replay payload DTO 可以迁到 `objects/apiTypes/replay` 或 `objects/replay`。
- database 只保存 row，不负责“渲染 JSON 字符串”这种命名混乱的职责。

## 推荐目标目录

建议最终目录：

```text
backend/src/main/scala/services/battle/
  api/
    queue/
      BattleQueueJoinAPIMessage.scala
      BattleQueueStatusAPIMessage.scala
      BattleQueueLeaveAPIMessage.scala
    room/
      BattleRoomSnapshotAPIMessage.scala
      BattleRoomHeartbeatAPIMessage.scala
    session/
      BattleStateReadAPIMessage.scala
    command/
      BattleCommandAPIMessage.scala
    results/
      BattleResultListAPIMessage.scala
      BattleResultRecordAPIMessage.scala

  objects/
    BattleEnums.scala
    core/
    queue/
    room/
    session/
    command/
    runtime/
    world/
    combat/
    abilities/
    actors/
    results/
    replay/
    apiTypes/
      queue/
      room/
      session/
      command/
      state/
      results/
      replay/

  routes/
    BattleRoutes.scala

  database/
    queue/
      BattleQueueTable.scala
      BattleQueueTableInitializer.scala
    room/
      BattleRoomTable.scala
      BattleRoomTableInitializer.scala
    session/
      BattleSessionTable.scala
      BattleSessionTableInitializer.scala
    state/
      BattleStateTable.scala
      BattleStateTableInitializer.scala
    command/
      BattleCommandTable.scala
      BattleCommandTableInitializer.scala
    results/
      BattleResultTable.scala
      BattleResultTableInitializer.scala
    world/
      BattleWorldRuleTable.scala
      BattleWorldRuleTableInitializer.scala
    runtime/
      BattleRuntimeRuleTable.scala
      BattleRuntimeRuleTableInitializer.scala
    combat/
      BattleCombatRuleTable.scala
      BattleCombatRuleTableInitializer.scala
    abilities/
      BattleAbilityRuleTable.scala
      BattleAbilityRuleTableInitializer.scala
    actors/
      BattleActorRuleTable.scala
      BattleActorRuleTableInitializer.scala
```

## 推荐依赖方向

推荐单向依赖：

```text
route/battle adapter
  -> services.battle.routes
    -> services.battle.api
      -> services.battle.database
        -> services.battle.objects
      -> services.battle.objects
```

禁止方向：

- `objects -> api`
- `objects -> database`
- `objects -> routes`
- `database -> api`
- `database -> routes`
- `routes -> database`
- 业务域之间互相调用内部实现。

允许方向：

- `api/queue -> database/queue`
- `api/command -> database/state + database/command`
- `database/session -> objects/session + objects/core`
- 多个领域共享 `objects/core` 的 value object。
- 纯规则函数读取 immutable ADT 并返回新 ADT。

## 分阶段迁移建议

### Phase 0：冻结现状和编译基线

先确认当前 worktree 的 PostgreSQL rule migration 是否保留，然后跑：

```text
sbt compile
sbt Test/compile
```

不要在未稳定状态下继续大迁移。

### Phase 1：清理 microservices 过渡目录

把 `microservices/*/objects` 并入 `objects/<domain>`，把 `microservices/*/database` 并入 `database/<domain>`。

特殊问题：

- `microservices/combat/routes` 不是 route，不能迁到 `routes`。
- 这些文件本质是 runtime combat rules，需要决定放在何处。

### Phase 2：API codec 下沉

先处理最明显的问题：

- `BattleCommandAPIMessage` 的长 decoder 移到 `objects/apiTypes/command`。
- queue/room/state/result request decoder 统一下沉。
- APIMessage companion 只保留必要 given import 或很薄的错误映射。

### Phase 3：results API 先完成 connection-backed

result API 已经最接近目标，适合作为第一条可完成 vertical slice：

- 移除 `BattleResultStorage.Repository` 兼容路径。
- `BattleResultListAPIMessage` / `BattleResultRecordAPIMessage` 只保留 `APIWithTokenMessage`。
- `BattleRoutes` 中 result 注册改为 `apiWithToken`。

### Phase 4：queue/room PostgreSQL 化

迁移：

- join
- status
- leave
- room snapshot
- heartbeat

需要新增：

- `BattleQueueTable`
- `BattleQueueTableInitializer`
- `BattleRoomTable`
- `BattleRoomTableInitializer`

这一步完成后，可以删除 `BattleAPIRuntimeContext` 中的 `BattleQueueService` 依赖。

### Phase 5：session/state/command PostgreSQL 化

迁移：

- `BattleStateReadAPIMessage`
- `BattleCommandAPIMessage`

需要新增或完善：

- `BattleSessionTable`
- `BattleStateTable`
- `BattleCommandTable`

runtime tick 可以继续是纯函数，但 authoritative state 的读写必须来自 PostgreSQL。

### Phase 6：projection/replay 整理

迁移：

- replay frame payload DTO
- result response DTO
- finish projection artifact output

目标：

- database 负责保存 row。
- `objects/apiTypes` 负责 wire projection。
- runtime rules 不直接知道 mail/replay repository 细节。

### Phase 7：删除旧 service/context/fallback

删除或迁出：

- `BattleAPIRuntimeContext`
- production path 的 `InMemoryBattleQueueService`
- production path 的 `InMemoryBattleStateService`
- `apiWithTokenAndContext` 在 battle routes 中的使用。
- `BattleResultAPIRegistration.RepositoryBacked`

测试中如果需要内存实现，应放在 `src/test` fixture，不留在 production path。

## 路线合理性判断

合理：

- 用 APIMessage 替代厚 route。
- 用 `apiNameFromClassName` 替代 rewrite。
- 用 `APIWithTokenMessage.plan(connection)` 替代 service context 注入。
- 用 `objects/apiTypes` 统一 API contract。
- 用 Table/TableInitializer 收敛 PostgreSQL 边界。
- 用 ADT/value object 保持 battle 类型安全。
- 用 Circe/http4s-circe 替代手写 JSON。
- 用 Cats Effect `IO` 表达数据库和阻塞边界。

需要修正：

- 不要把所有 pure runtime/game rules 都塞进 APIMessage。
- 不要把 `microservices` 当成长期目录。
- 不要让 `database` 继续承载所有业务规则。
- 不要在 APIMessage 里继续写长 Circe decoder。
- 不要在 `apiTypes` 里重复声明 `objects` 已经有的 ADT/value object。
- 不要让 route 继续知道 queue/state/result backend 的组合细节。

## 需要你决策的问题

1. 纯战斗规则函数放哪里？

推荐：短期保留在 `database/<domain>/*Rules.scala`，完成 APIMessage/Table 迁移后，再单独做规则层命名清理。更干净但需要你放宽四目录要求的方案是新增 `rules/` 或 `engine/`。

2. 是否允许测试专用 in-memory battle service？

推荐：production path 删除 context 注入和 in-memory battle service；测试如果需要，可以迁到 `src/test` fixture。

3. queue/session 是否立刻 PostgreSQL 化？

如果目标是 `BattleRoutes` 只剩 `apiWithToken[...]` 注册，那么答案是必须。否则 APIMessage 没有地方拿 queue/state service。

4. `objects/apiTypes` 是否允许 private decoder helper？

推荐允许。复杂 command decoder 很难只靠 `deriveDecoder` 表达，但 helper 必须留在 apiTypes，不能在 APIMessage。

5. `BattleEnums.scala` 是否保持单文件？

推荐短期保持统一入口，长期可拆为 `objects/enums/*` 并由 package export 管理。

## 建议下一票

不要直接全量重构 battle。下一票建议做一个最小闭环：

```text
BE-BATTLE-API-RESULTS-01:
把 result list/record 从 dual APIWithTokenMessage + APIWithTokenContextMessage
收敛为纯 APIWithTokenMessage + PostgreSQL connection-backed plan(connection)。
```

原因：

- result API 当前最接近目标，风险最低。
- 能验证 `BattleRoutes` 变薄的方向。
- 能删除一段 repository-backed 兼容逻辑。
- 不会先碰 queue/session 的大型 authoritative state 迁移。

完成后再迁 queue/room，然后迁 session/state/command。

## 决策矩阵

### 决策 A：纯战斗规则函数放哪里

| 方案 | 结构 | 优点 | 风险 | 建议 |
| --- | --- | --- | --- | --- |
| A1 | 短期继续放 `database/<domain>/*Rules.scala` | 改动最小，能先完成 APIMessage/Table 迁移 | `database` 名称仍不精确 | 推荐短期采用 |
| A2 | 新增 `rules/` 或 `engine/` | 语义最清楚，battle runtime 规则有专属位置 | 违反“严格只含 api/objects/routes/database”的字面要求 | 架构上最佳，但需要你确认放宽 |
| A3 | 全塞进 `api/XXXAPIMessage.scala` private def | 表面满足四目录 | APIMessage 变成 god service，combat/world/runtime 难维护 | 不推荐 |
| A4 | 塞进 `objects/` | 保持四目录，规则靠近 ADT | `objects` 不再是 passive model，可能混入复杂逻辑 | 不推荐大规模使用 |

我的建议：先用 A1 完成迁移闭环，之后单独做一次“rules/engine 命名清理”。如果你允许新增 `rules/`，则直接选 A2。

### 决策 B：是否立刻删除 production in-memory battle service

| 方案 | 含义 | 优点 | 风险 | 建议 |
| --- | --- | --- | --- | --- |
| B1 | 立刻删除 `InMemoryBattleQueueService` / `InMemoryBattleStateService` | 最快逼近 PostgreSQL authoritative state | queue/session/state 要一次性补表，改动大 | 不建议第一票做 |
| B2 | 先从 result API 删除 repository fallback，再迁 queue/session | 风险可控，每票可编译验证 | in-memory service 会多保留几轮 | 推荐 |
| B3 | 永久保留 production in-memory service | 开发方便 | 和 `plan(connection)` 目标冲突 | 不推荐 |

我的建议：选 B2。先做 result API，然后 queue/room，再 session/state/command。

### 决策 C：`objects/apiTypes` 能不能有 private decoder helper

| 方案 | 含义 | 优点 | 风险 | 建议 |
| --- | --- | --- | --- | --- |
| C1 | 只允许 `deriveEncoder` / `deriveDecoder` | 文件最短 | command 这种复杂 wire 校验表达不足 | 不建议绝对化 |
| C2 | 允许 apiTypes 内有 private decoder helper | APIMessage 变薄，复杂校验留在 contract 边界 | apiTypes 文件可能变长 | 推荐 |
| C3 | 继续把 decoder 写在 APIMessage | 少搬文件 | APIMessage 继续混杂 JSON 细节 | 不推荐 |

我的建议：选 C2。目标不是“文件极短”，而是“JSON 边界清楚，APIMessage 不手写长 decoder”。

### 决策 D：`BattleEnums.scala` 是否拆分

| 方案 | 含义 | 优点 | 风险 | 建议 |
| --- | --- | --- | --- | --- |
| D1 | 短期保持单文件统一 enum | 避免重复 enum，迁移风险低 | 文件继续偏大 | 推荐短期采用 |
| D2 | 拆到 `objects/enums/*` 并 package export | 长期更可维护 | 需要较多 import/package 修复 | 后续单独做 |
| D3 | 每个 apiTypes 自己声明 enum | 局部看方便 | 破坏单一事实来源，类型漂移 | 禁止 |

我的建议：选 D1，然后后续单独拆 enum，不要和 APIMessage/Table 迁移混做。

## 第一批迁移票据建议

### BE-BATTLE-API-RESULTS-01

目标：把 result list/record 收敛为纯 `APIWithTokenMessage + plan(connection)`。

边界：

- `services/battle/api/results`
- `services/battle/routes/BattleRoutes.scala`
- `services/battle/database/results`
- `services/battle/objects/apiTypes/results`
- `route/battle/BattleHttp4sRoutes.scala` 只允许做必要删除兼容分支

期望变化：

- 删除 battle result API 的 `APIWithTokenContextMessage` 实现。
- 删除 `BattleResultStorage.Repository` 在 battle API 注册路径中的使用。
- `BattleRoutes` 对 result 只用 `apiWithToken[...]`。

验证：

```text
sbt compile
sbt Test/compile
```

### BE-BATTLE-API-CODEC-02

目标：把 `BattleCommandAPIMessage` 内的长 decoder 下沉到 `objects/apiTypes/command`。

边界：

- `services/battle/api/command/BattleCommandAPIMessage.scala`
- `services/battle/objects/apiTypes/command`
- 必要时只读 `objects/command`、`objects/core`、`objects/weapon`、`objects/skill`

期望变化：

- APIMessage 文件只保留 case class、`plan`、错误映射。
- command request decoder 由 apiTypes 提供。
- 不新增重复 `BattleId` / `PlayerId` / `TicketId`。

验证：

```text
sbt compile
sbt Test/compile
```

### BE-BATTLE-STRUCTURE-MICROSERVICES-03

目标：把 `services/battle/microservices` 并回四层结构。

边界：

- `services/battle/microservices`
- `services/battle/objects`
- `services/battle/database`
- 只允许修 import，不改业务行为

期望变化：

- `microservices/*/objects` -> `objects/<domain>`
- `microservices/*/database` -> `database/<domain>`
- `microservices/*/api/*RuleBook` 需要重新命名，不能继续叫 API
- `microservices/combat/routes` 不能进入 `routes`，需要按你的决策放到临时 rules 位置

验证：

```text
sbt compile
sbt Test/compile
```

### BE-BATTLE-QUEUE-POSTGRES-04

目标：把 queue/room 从 service context 迁为 PostgreSQL-backed APIMessage。

边界：

- `api/queue`
- `api/room`
- `database/queue`
- `database/room`
- `objects/queue`
- `objects/apiTypes/queue`
- `objects/apiTypes/room`
- `routes/BattleRoutes.scala`

期望变化：

- join/status/leave/snapshot/heartbeat 全部 `extends APIWithTokenMessage[XXXResponse]`。
- `plan(connection)` 直接读取和写入 queue/room 表。
- `BattleRoutes` 不再为 queue/room 注入 `BattleQueueService`。

验证：

```text
sbt compile
sbt Test/compile
```

### BE-BATTLE-SESSION-POSTGRES-05

目标：把 state read / command accept 从 `BattleStateService` context 迁为 PostgreSQL authoritative state。

边界：

- `api/state`
- `api/command`
- `database/session`
- `database/state`
- `database/command`
- `objects/session`
- `objects/command`
- `objects/apiTypes/state`
- `objects/apiTypes/command`

期望变化：

- `BattleStateReadAPIMessage` 和 `BattleCommandAPIMessage` 都只用 `plan(connection)`。
- authoritative state 从 PostgreSQL 读取、推进、保存。
- runtime 规则保持 pure transition，不直接做数据库 IO。

验证：

```text
sbt compile
sbt Test/compile
```

## 我建议你现在做的选择

如果你要我继续动代码，我建议按下面选择执行：

```text
A1：纯规则短期留在 database/<domain>/*Rules.scala
B2：先 result，再 queue/room，再 session/state/command
C2：apiTypes 允许 private decoder helper
D1：BattleEnums.scala 短期保持统一入口
```

这个组合最稳，能让每一步都有编译反馈，并且不会把大型战斗规则塞进 APIMessage。

## 当前分析阶段验收清单

| 要求 | 当前证据 | 状态 |
| --- | --- | --- |
| 整理 battle 所有主要逻辑 | 已覆盖 route、api、objects、database、microservices、queue/session/runtime/world/combat/results/replay | 已完成 |
| 说明模块实现逻辑 | “当前模块实现逻辑”章节逐层说明了职责和问题 | 已完成 |
| 说明类型安全结构 | “类型安全结构分析”章节覆盖 ADT、enum、value object、immutable state | 已完成 |
| 说明 Circe 技术使用 | “Circe 现状”章节说明 Decoder/Encoder、apiTypes、APIMessage decoder 问题 | 已完成 |
| 说明 Cats Effect 技术使用 | “Cats Effect / IO 现状”章节说明 `plan(connection)`、`IO`、`Resource[IO, Connection]` | 已完成 |
| 说明 render 技术 | “Render / wire projection 技术”章节区分后端 JSON projection 与前端 Phaser 3 渲染 | 已完成 |
| 分析路线合理性 | “结论”和“路线合理性判断”已给出合理部分和需要修正部分 | 已完成 |
| 给出进一步决策点 | “决策矩阵”已列出 A/B/C/D 四组关键选择 | 已完成 |
| 给出可执行迁移票据 | 已列出 `BE-BATTLE-API-RESULTS-01` 到 `BE-BATTLE-SESSION-POSTGRES-05` | 已完成 |
| 开始重构 services/battle | 需要你先确认决策组合，当前尚未开始 | 未开始 |

## 当前不能越过的决策点

在没有你确认前，我不建议直接开始大重构。原因不是技术上不能做，而是下面三项会改变后续目录和代码形态：

- 纯 battle rules 是否允许短期留在 `database/<domain>/*Rules.scala`。
- `objects/apiTypes` 是否允许 private decoder helper。
- production path 的 in-memory battle service 是分阶段删除，还是一次性删除。

如果你同意我推荐的组合，下一步就执行：

```text
BE-BATTLE-API-RESULTS-01
```

如果你不同意推荐组合，请直接指定 A/B/C/D 的选择，例如：

```text
A2 B2 C2 D1
```
