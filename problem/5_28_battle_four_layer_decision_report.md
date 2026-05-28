# services/battle 四层重构决策报告

更新时间：2026-05-28

## 1. 当前结论

这条路线总体合理，但需要修正两个点：

1. `services/battle` 最终可以收敛成 `api / objects / routes / database` 四个顶层边界。
2. 不能把所有游戏规则都塞进 `XXXAPIMessage.plan` 的 private def。APIMessage 应该是用例入口，不应该变成新的 god service。
3. 如果所有 battle API 都必须是 `APIWithTokenMessage[Response]` 并只通过 `plan(connection)` 执行，那么 queue/session 的运行状态必须从内存服务迁到 PostgreSQL，否则 route 仍然必须注入 `BattleQueueService` 和 `BattleStateService`。
4. `objects/apiTypes` 可以放 request/response DTO 和 Circe codec；核心 domain objects 不建议普遍挂 `Encoder/Decoder`，否则 domain 会反向依赖 wire transport。
5. `battle/object` 这个名字不建议使用，因为 `object` 是 Scala 关键字，仓库当前也已经使用 `objects`。建议最终目录仍然叫 `battle/objects`。

当前 worktree 已经部分接近目标，但没有完成：

| 顶层目录 | Scala 文件数 | 当前含义 | 是否符合最终目标 |
| --- | ---: | --- | --- |
| `api` | 9 | battle APIMessage 已集中到 queue/room/state/command/results | 部分符合 |
| `objects` | 50 | ADT、value object、state、apiTypes | 部分符合 |
| `routes` | 2 | APIMessage registry 和 runtime context | 部分符合 |
| `database` | 2 | 只有 results 的 PostgreSQL table/initializer | 不完整 |
| `microservices` | 90 | 大部分 battle 业务逻辑仍在这里 | 不符合最终目标 |

`microservices` 下当前分布：

| 子域 | Scala 文件数 | 当前职责 |
| --- | ---: | --- |
| `abilities` | 9 | skill、pickup、slow field、技能规则配置 |
| `actors` | 8 | player runtime、bot、input、lifecycle |
| `combat` | 13 | weapon、projectile、fire、hit、damage、terminal |
| `projections` | 13 | battle finish 后写 result/replay/mail |
| `queue` | 13 | 排队、房间、ticket、heartbeat、room snapshot |
| `results` | 0 | 已迁空，results 已回到四层结构 |
| `runtime` | 14 | tick 推进、finalization、event、retention、engine |
| `session` | 11 | battle session、state service、command accept/apply |
| `world` | 9 | 地图、碰撞、几何、移动、出生点、world rule |

## 2. 当前执行链路

当前 battle HTTP 链路已经比旧 route-heavy 结构更接近目标：

```text
HTTP POST /api/{apiName}
  -> route/battle/BattleHttp4sRoutes
  -> system.api.APIMessageRouter
  -> services.battle.routes.BattleRoutes
  -> XXXAPIMessage.plan(...)
  -> service/table/runtime
  -> objects/apiTypes Encoder
  -> JSON response
```

当前正确的地方：

- `route/battle/BattleHttp4sRoutes` 已经很薄，主要负责挂载 `APIMessageRouter`。
- `services/battle/routes/BattleRoutes.scala` 已经不再是传统 HTTP route，而是 typed APIMessage registry。
- `api/results` 已经是 connection-backed：`BattleResultListAPIMessage` 和 `BattleResultRecordAPIMessage` 使用 `APIWithTokenMessage` + `plan(connection)`。
- queue/room/state/command 的 APIMessage 已经集中在 `services/battle/api/...`，没有继续散落在 HTTP route。

当前未完成的地方：

- `BattleRoutes.apiMessages(context)` 仍然需要 `BattleAPIRuntimeContext`。
- queue/room/state/command 仍然使用 `APIWithTokenContextMessage[Service, Response]`。
- route 层仍然需要注入 `BattleQueueService`、`BattleStateService`、`BattleQueueJoinAuthorizationService`。
- 只要这些 service 是运行时状态入口，`BattleRoutes` 就无法简化为纯 `val apiMessages: List[RegisteredAPIMessage]`。

因此，路线本身合理，但前提是下一阶段必须决定：是否把 queue/session 状态持久化到 PostgreSQL。

## 3. 类型安全结构现状

### 3.1 已经做得好的部分

battle 已有比较强的 ADT/value object 基础：

核心 value object：

- `BattleId`
- `TicketId`
- `QueueRequestId`
- `RoomId`
- `PlayerId`
- `HeroId`
- `ProjectileId`
- `SlowFieldId`
- `PickupId`
- `BattleEventId`
- `BattleResultId`
- `EpochMillis`
- `DurationMillis`
- `ElapsedMillis`
- `BattleTick`
- `ClientCommandSeq`
- `BattleVector2`
- `HitPoints`
- `Stamina`
- `AmmoCount`
- `CooldownMillis`
- `FacingRadians`
- `Radius`
- `Damage`
- `Score`
- `Rating`
- `RatingDelta`

核心 enum / ADT：

- `MatchmakingRoomPhase`
- `BattleMode`
- `BattlePhase`
- `BattleArtifactStatus`
- `WeaponKind`
- `ProjectileKind`
- `SkillKind`
- `PickupKind`
- `BattleCommandStatus`
- `BattleCommandReason`
- `SkillOutcomeStatus`
- `SkillOutcomeReason`
- `ProjectileTerminalReason`
- `BattleEventKind`
- `BattlePlayerLifeState`
- `BattleParticipantKind`
- `BattleWeaponThermalState`
- `BattleWeaponSwitchDirection`
- `BattleSurvivalOutcome`
- `BattleStateReadError`
- `BattleCommandSubmitError`
- `BattleQueueStatusError`
- `BattleRoomError`
- `BattleQueueJoinAuthorizationError`

这些是应该保留的单一事实来源。API 层不应该再重新声明 `BattleId`、`PlayerId`、`TicketId` 这类类型。

### 3.2 需要继续修正的部分

当前仍存在以下结构问题：

- `apiTypes/command/BattleCommandRequestApiTypes.scala` 里有复杂手写 decoder。这个位置比 APIMessage 内部更合理，但它已经超过“只放 final case class + derive codec”的简单边界。
- 如果强制 `apiTypes` 只能有 `final case class + object Response { given Encoder = ... }`，复杂 command request 的字段验证会无处安放。
- `BattleEnums.scala` 当前集中维护统一 enum，这是合理的，短期不建议再拆。之前把 enum 拆散会增加 import 和 contract drift 风险。
- rule config 已经建成 ADT，但仍放在 `microservices/<domain>/objects`，最终应移到 `objects/<domain>`。

我的判断：`apiTypes` 应允许少量 private decoder helper，否则会把复杂 JSON validation 重新推回 APIMessage 或 route，这会倒退。

## 4. Circe 边界现状

当前使用的技术：

- `io.circe.Decoder`
- `io.circe.Encoder`
- `io.circe.generic.semiauto.deriveEncoder`
- `io.circe.generic.semiauto.deriveDecoder`
- `http4s-circe`
- `APIMessageRouter` 统一 request decode / response encode

合理边界应为：

```text
objects/<domain>/
  domain ADT
  value object
  immutable state
  pure companion mapping such as wireValue/fromWire

objects/apiTypes/<domain>/
  request DTO
  response DTO
  Circe Encoder/Decoder
  necessary boundary-only decoder helper

api/<domain>/
  XXXAPIMessage
  plan(connection): IO[Response]
  small private pure helpers or effect orchestration helpers
```

不建议：

- 不建议 domain object 普遍依赖 Circe。
- 不建议 APIMessage 内写大型 `HCursor` decoder。
- 不建议 route 手写 request body parser。
- 不建议 API 内部传递自由字符串状态，应先使用 ADT，再在 codec 边界映射成 string。

## 5. Cats Effect / IO 边界现状

当前已有：

- `APIMessage[Response]`
- `APIWithTokenMessage[Response]`
- `APIMessageWithContext[Context, Response]`
- `APIWithTokenContextMessage[Context, Response]`
- `Resource[IO, Connection]`
- `IO.blocking(...)`

目标链路应是：

```text
XXXAPIMessage.plan(connection)
  -> database Table read/write in IO
  -> pure domain transition
  -> database Table save in IO
  -> response DTO
```

当前不足：

- queue 仍然是 `AtomicReference[QueueRuntimeState]` + `lock.synchronized`。
- session 仍然是 `private var battles: Map[BattleId, StoredBattle]` + `lock.synchronized`。
- rule books 仍然使用 `AtomicReference` 做全局缓存。
- queue/state/command API 仍然通过 context service 执行，不是纯 `plan(connection)`。

这不是简单改包名能解决的问题。要真正达成目标，需要把以下运行时状态表化：

- queue tickets
- queue rooms
- room heartbeat
- active battle session seed
- stored battle state
- command ownership
- command accepted/applied result
- finish projection status

## 6. Render / 前后端契约视角

后端 battle 的“render”不是 Phaser 画面渲染，而是 authoritative state 的 wire projection：

- `objects/apiTypes/state/*` 把 `BattleAggregateState` 编码成前端可消费的 JSON。
- `objects/apiTypes/queue/*` 把 queue/room snapshot 编码给等待区。
- `objects/apiTypes/results/*` 把 battle result 编码给结果页。
- `projections` 把 finished battle 转换成 result/replay/mail artifact。

前端实际渲染链路在 Phaser 3：

- `frontend/src/runtime/battle/authoritative/authoritativeBattleClient.ts`
- `frontend/src/runtime/battle/authoritative/battleContractAdapter.ts`
- `frontend/src/runtime/battle/game/renderer/createBattleRuntime.ts`
- `frontend/src/objects/battle/contracts/*`

后端重构时最重要的约束：

- JSON 字段名不能漂移。
- enum wire value 不能漂移。
- optional/null 语义不能漂移。
- `BattleStateReadAPIMessage` response shape 不能无测试改动。
- queue/room snapshot response shape 不能无测试改动。

因此，后端目录重构不能直接改 response DTO。必须先保持 contract 兼容，再逐步替换内部实现。

## 7. 各业务域当前逻辑

### queue

当前职责：

- 玩家 join queue。
- 查询 ticket status。
- leave queue。
- room snapshot。
- heartbeat。
- 房间从 waiting 进入 active/finished。
- 根据 room 创建 battle session bootstrap。

当前类型安全结构：

- `BattleQueueSnapshot`
- `RealtimeRoomSnapshot`
- `BattleSessionDescriptor`
- `BattleSessionBootstrap`
- `BattleQueueParticipant`
- `QueueRoomLifecycle`
- `QueueRoomStartDecision`
- `TicketId`
- `RoomId`
- `QueueRequestId`
- `BattleCapacity`

当前问题：

- 运行状态仍在 `InMemoryBattleQueueService`。
- service 内部有 `AtomicReference` 和 `synchronized`。
- APIMessage 仍依赖 `BattleQueueService` context。

最终建议：

- `objects/queue` 保存 queue ADT 和 immutable state。
- `database/queue` 保存 `BattleQueueTable` / `BattleQueueTableInitializer`。
- `api/queue` 的 APIMessage 直接通过 `connection` 调 table。
- route 不再注入 queue service。

### room

当前职责：

- 房间 snapshot。
- 房间 heartbeat。
- 等待区玩家在线状态。
- room lifecycle。

当前问题：

- room 没有独立 database 边界，仍混在 queue service runtime state。

最终建议：

- 如果 room 是 queue 的子概念，可放 `database/queue`。
- 如果 room lifecycle 复杂，单独放 `database/room`。
- API 层应保留 `api/room/BattleRoomSnapshotAPIMessage.scala` 和 `api/room/BattleRoomHeartbeatAPIMessage.scala`。

### session

当前职责：

- active battle state lookup。
- read current authoritative state。
- accept player command。
- command ownership。
- stored battle advance。
- finish projection preparation/completion。

当前类型安全结构：

- `StoredBattle`
- `StateRead`
- `CommandSubmission`
- `BattleStateReadError`
- `BattleCommandSubmitError`
- `BattleCommandOwnership`
- `BattleSessionSeed`

当前问题：

- 运行状态在 mutable `InMemoryBattleStateService`。
- APIMessage 依赖 `BattleStateService` context。
- `BattleCommandAPIMessage`/`BattleStateReadAPIMessage` 还不能只靠 `Connection` 工作。

最终建议：

- `database/session` 保存 session metadata。
- `database/state` 保存 authoritative aggregate state 或 serialized state row。
- `database/command` 保存 command ownership / accepted command log。
- `objects/session` 保存 session ADT。
- `api/session` 或 `api/state` 保留 read/command APIMessage。

### runtime

当前职责：

- tick 推进。
- finalization。
- event creation。
- replay frame recording。
- retention。
- aggregate update。

当前类型安全结构：

- `BattleRuntimeRuleSet`
- `BattleRuntimeRuleConfig`
- `BattleHistoryRuleConfig`
- `BattleSessionPlayerRuleConfig`
- `BattleTick`
- `ElapsedMillis`
- `DurationMillis`
- `BattlePhase`

当前问题：

- `BattleRuntimeRuleBook` 放在 `microservices/runtime/api`，但它不是 APIMessage。
- rule book 使用全局 `AtomicReference`。
- `BattleEngine` 必然协调 world/actors/combat/abilities，这不是错误，但需要保持单向 orchestration。

最终建议：

- runtime config ADT 进 `objects/runtime`。
- PostgreSQL config 进 `database/runtime`。
- pure runtime step rules 如果坚持四层，只能进 `objects/runtime/rules` 或 `objects/runtime`。
- 更干净的长期方案是允许第五层 `rules` 或 `engine`，但这需要你明确放宽四层限制。

### world

当前职责：

- map spec。
- movement config。
- collision。
- geometry。
- spawn point。
- pickup definition。

当前问题：

- `BattleWorldRuleBook` 不应在 `api`。
- `BattleWorldRuleTable` 同时做 row load、JSON decode、domain construction，已经偏厚。

最终建议：

- world config ADT 进 `objects/world`。
- PostgreSQL table 进 `database/world`。
- map JSON -> domain 的转换可以作为 database 边界 mapping，但不要扩展成 game service。

### combat

当前职责：

- weapon inventory。
- fire cooldown。
- projectile factory。
- projectile motion。
- hit/impact/damage。
- terminal reason。

当前类型安全结构：

- `BattleWeaponRuleDefinition`
- `BattleWeaponFireDefinition`
- `BattleWeaponProjectileDefinition`
- `BattleWeaponFiringResource`
- `WeaponKind`
- `ProjectileKind`
- `Damage`
- `Radius`
- `CooldownMillis`
- `DurationMillis`

当前问题：

- `BattleCombatRuleBook` 不应在 `api`。
- combat rules 不应该放进 database 或 APIMessage。

最终建议：

- config ADT 进 `objects/combat`。
- table 进 `database/combat`。
- pure weapon/projectile rules 的归属需要你决策：严格四层则放 `objects/combat`；允许第五层则放 `rules/combat`。

### actors

当前职责：

- player input application。
- player lifecycle。
- bot movement/fire/aim/pickup seeking。
- player runtime update。

当前类型安全结构：

- `BattleBotRuleConfig`
- `BattleBotMoveSpeed`
- `BattlePlayerState`
- `BattlePlayerLifeState`
- `BattleParticipantKind`

当前问题：

- `BattleBotRuleBook` 不应在 `api`。
- bot config 已表化，但 rule book 仍全局缓存。

最终建议：

- actor/bot config ADT 进 `objects/actors`。
- table 进 `database/actors`。
- pure bot/player rules 的归属同 runtime/combat，需要决策。

### abilities

当前职责：

- Blink/Dash/Freeze。
- cooldown。
- active duration。
- pickup contact radius。
- medkit heal。
- pickup respawn。
- slow field runtime。

当前类型安全结构：

- `BattleSkillRuleSet`
- `BlinkConfig`
- `DashConfig`
- `FreezeConfig`
- `BattlePickupRuleConfig`
- `SkillDistance`
- `DurationMillis`
- `Radius`

当前问题：

- `BattleSkillRuleBook`、`BattlePickupRuleBook` 不应在 `api`。
- pure skill/pickup rules 不应该进入 APIMessage。

最终建议：

- config ADT 进 `objects/abilities`。
- table 进 `database/abilities`。
- pure rules 归属需决策。

### results

当前职责：

- battle result list。
- battle result record save/read。
- PostgreSQL `battle_results` table。

当前状态：

- 这是最接近目标的域。
- `database/results/BattleResultTable.scala`
- `database/results/BattleResultTableInitializer.scala`
- `api/results/BattleResultListAPIMessage.scala`
- `api/results/BattleResultRecordAPIMessage.scala`
- `objects/result/*`
- `objects/apiTypes/results/*`

当前问题：

- `BattleResultListAPIMessage` 的 request decode 仍需要检查是否存在静默 fallback。
- `BattleResultTable` 有 `save/list` 方法。按当前工程习惯这是可接受的 Table 边界；如果你要求 Table 只能写 SQL 常量，需要单独确认。

### projections

当前职责：

- battle finished 后生成 result。
- 生成 replay frames JSON。
- 生成 mail artifact。
- settlement scoring。
- finish projection status。

当前问题：

- projections 不是 database，也不是 APIMessage。
- 它跨 battle/result/replay/mail 边界，应该通过 port 或 application plan 连接。

最终建议：

- projection ADT 进 `objects/projections`。
- side-effect orchestration 如果严格四层，只能放 `api/projections` 或 `objects/projections`，这都不理想。
- 更合理的是允许 `application` 或 `rules` 层；但这超出你当前要求，需要你决策。

## 8. 目录路线合理性

推荐最终形态：

```text
services/battle/
  api/
    queue/
    room/
    state/
    command/
    results/

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
    actors/
    abilities/
    result/
    projections/
    replay/
    apiTypes/
      queue/
      room/
      state/
      command/
      results/

  routes/
    BattleRoutes.scala

  database/
    queue/
    room/
    session/
    state/
    command/
    results/
    runtime/
    world/
    combat/
    actors/
    abilities/
```

`microservices/` 不建议作为最终目录保留。它现在只是迁移中间态。

合理依赖方向：

```text
route adapter
  -> services.battle.routes
    -> services.battle.api
      -> services.battle.database
      -> services.battle.objects
      -> services.battle.objects.apiTypes
```

允许：

- `routes -> api`
- `routes -> objects/apiTypes given Encoder`
- `api -> database`
- `api -> objects`
- `api -> objects/apiTypes Decoder`
- `database -> objects`
- `objects/apiTypes -> objects`
- runtime orchestrator 单向调用 world/actors/combat/abilities 的 pure rules

禁止：

- `objects -> api`
- `objects -> routes`
- `objects -> database`
- `database -> api`
- `database -> routes`
- `routes -> database`
- domain object 重新声明已有 id/state enum
- APIMessage 变成大型 game rule 容器
- RuleBook 伪装成 APIMessage
- `apiTypes` 重复声明 `BattleId`、`PlayerId`、`TicketId` 等核心类型

## 9. 关于“不同业务逻辑之间不能互相调用”

这个要求需要精确定义，否则会破坏游戏 runtime。

不应该发生：

- queue 直接访问 combat 内部实现。
- combat 直接访问 queue/session repository。
- database 包调用 APIMessage。
- route 直接调用 Table。
- world/combat/actors/abilities 彼此任意双向 import。

可以发生：

- runtime 作为 orchestrator，单向调用 world、actors、combat、abilities 的 pure rule。
- session 调 runtime 推进 battle state。
- projection 通过 port/API plan 写 result/replay/mail。
- APIMessage 调 database table 和 pure transition。

所以正确目标不是“完全零调用”，而是“单向依赖 + 明确 orchestrator + 禁止双向调用”。

## 10. 决策点

### A. 是否严格只允许四层

选择 A1：严格四层。

影响：

- pure runtime/game rules 只能放进 `objects/<domain>`。
- 满足你提出的四层目录要求。
- 缺点是 `objects` 不再只是 passive data，会包含大量 pure rule functions。

选择 A2：允许增加 `rules/` 或 `engine/`。

影响：

- 架构更干净，domain data 和 pure rules 更分明。
- 不完全符合你“至少严格包含四层”中的强约束，除非你允许四层之外再有一层。

我的建议：如果你坚持当前目标，选 A1；如果追求长期可维护性，选 A2。

### B. queue/session 是否立刻 PostgreSQL 化

选择 B1：立刻 PostgreSQL 化。

影响：

- 可以真正删除 `BattleAPIRuntimeContext`。
- `BattleRoutes` 可以变成纯 `val apiMessages`。
- 改动大，风险高，需要 schema 和 contract test 一起推进。

选择 B2：先完成目录和 APIMessage 边界，再分阶段 PostgreSQL 化。

影响：

- 风险较低。
- 短期仍保留 `APIWithTokenContextMessage`。
- 最终目标尚未完成，但更容易保证每一步可编译。

我的建议：选 B2，除非你愿意接受 queue/session 一次性大迁移的风险。

### C. apiTypes 是否允许 private decoder helper

选择 C1：只允许 derive codec。

影响：

- 简洁。
- 复杂 request validation 很难表达。
- 容易把 parser 塞回 APIMessage。

选择 C2：允许 apiTypes 内少量 private decoder helper。

影响：

- JSON 边界集中。
- APIMessage 更薄。
- 更符合复杂 command DTO 的现实。

我的建议：选 C2。

### D. 统一 enum 是否继续保留

选择 D1：短期保留 `BattleEnums.scala`。

影响：

- enum 单一事实来源稳定。
- 避免迁移期间 import 爆炸。

选择 D2：现在按域拆 enum。

影响：

- 长期更局部。
- 当前风险较高，容易重复声明或 wire value 漂移。

我的建议：选 D1。

### E. Table 是否只能包含 SQL，不能有 save/list/load 方法

选择 E1：Table 可以有最小 JDBC 操作方法。

影响：

- 当前 `BattleResultTable.save/list` 风格可保留。
- Table 是 persistence adapter，不是业务 service。

选择 E2：Table 只保留 SQL DDL/DML 常量和 row mapping，不提供动态方法。

影响：

- 更接近你说的“纯净 PostgreSQL”。
- 上层会需要其他地方执行 SQL，否则 IO 逻辑无处安放。

我的建议：选 E1。否则会把数据库 IO 挤到 APIMessage，导致 APIMessage 变厚。

## 11. 推荐下一步

如果你接受我的建议组合：

```text
A1 或 A2
B2
C2
D1
E1
```

下一票建议：

```text
BE-BATTLE-QUEUE-DATABASE-PLAN-01

目标：
先只设计 queue/room PostgreSQL 表和状态转换 API，不马上删除 InMemoryBattleQueueService。

边界：
services/battle/objects/queue
services/battle/database/queue
services/battle/api/queue
services/battle/api/room
services/battle/routes/BattleRoutes.scala

不做：
不动 session/runtime/combat/world/actors/abilities。
不改前端 JSON contract。
不删除旧 queue service，直到新 table-backed API 通过测试。

验收：
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
新增 queue table contract test
```

如果你更想先做目录清理而不碰数据库：

```text
BE-BATTLE-MICROSERVICES-FLAT-MOVE-01

目标：
把 microservices 下的 objects/database/api 按域搬到四层结构，services 暂时不动。

风险：
只能改善目录，不会完成 plan(connection) 目标。
```

## 12. 我的最终判断

这条路线合理，但不是单纯移动文件能完成。

真正的核心工作是：

1. 保持 `objects` 是 ADT/value object 的单一事实来源。
2. 保持 `objects/apiTypes` 是 Circe/request/response 边界。
3. 把 `api` 收缩成 typed APIMessage。
4. 把 production state 从 queue/session 内存服务迁到 PostgreSQL。
5. 把 `routes` 收缩成纯 registry。
6. 删除 `microservices` 作为顶层业务结构。

我建议你先决策 A/B/C/D/E，尤其是：

- 是否允许 `rules/engine` 第五层。
- queue/session 是否现在就 PostgreSQL 化。
- apiTypes 是否允许 private decoder helper。
- Table 是否允许 `save/list/load` 这类最小 JDBC 方法。

这几个决定会直接影响后续代码形态。
