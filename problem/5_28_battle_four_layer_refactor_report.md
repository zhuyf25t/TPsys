# services/battle 四层重构路线可决策报告

更新时间：2026-05-28

## 1. 结论

你提出的方向总体是合理的：`services/battle` 不应该继续以 `microservices` 作为顶层长期结构，而应该收束到清晰的四个边界：

```text
services/battle/
  api/
  objects/
  routes/
  database/
```

但当前 worktree 还没有达到这个结构。当前真实状态是：

| 顶层目录 | Scala 文件数 | 当前语义 |
| --- | ---: | --- |
| `api` | 2 | 只剩 `command` 和 `state` 两个 APIMessage |
| `database` | 0 | 根级 database 已为空，旧文件被删除或迁走 |
| `microservices` | 116 | 当前大部分 battle 业务逻辑实际集中在这里 |
| `objects` | 32 | battle 公共 ADT、value object、state、部分 apiTypes |
| `routes` | 2 | APIMessage 注册表和 runtime context |

`microservices` 内部分布是：

| 子域 | Scala 文件数 | 当前职责 |
| --- | ---: | --- |
| `queue` | 28 | 排队、等待房间、ticket、heartbeat、room snapshot |
| `session` | 11 | authoritative battle state、command accept、stored battle |
| `runtime` | 14 | tick 推进、finalization、retention、event、replay frame |
| `world` | 9 | 地图、碰撞、几何、移动、出生点、world rule |
| `combat` | 13 | 武器、子弹、开火、命中、伤害、终止 |
| `actors` | 8 | player runtime、bot、input、lifecycle |
| `abilities` | 9 | skill、pickup、slow field |
| `results` | 11 | result API、result object、PostgreSQL result table |
| `projections` | 13 | battle finish 后写 result/replay/mail |

所以当前最大问题不是“没有拆业务域”，而是拆分位置不符合目标：业务域被直接放到了 `battle/microservices/<domain>`，而你现在要求的是业务域只能在 `api`、`objects`、`routes`、`database` 这四个边界内部继续细分。

推荐判断：

- 保留 `api / objects / routes / database` 作为最终顶层结构。
- `object` 不建议使用单数包名，因为 `object` 是 Scala 关键字；仓库现有包名是 `objects`，建议继续使用 `objects`。
- `routes` 应该只保留 typed APIMessage registry，不应重新回到手写 HTTP route。
- `database` 应只承载 PostgreSQL/JDBC 的 `Table`、`TableInitializer` 和必要 row mapping，不应再承载 runtime、engine、rules、projection service。
- `api` 应只承载 `XXXAPIMessage.scala`，但不能把所有大型 runtime/game rules 塞进 APIMessage 的 private def，否则 APIMessage 会变成新的 god service。
- `objects/apiTypes` 应承载 request/response DTO 与 Circe codec；核心领域对象和值对象必须复用 `objects` 中已有定义，不允许在 apiTypes 中重复声明类似 `BattleId`、`PlayerId`、`TicketId`。

## 2. 当前执行链路

当前 battle 的真实 HTTP 执行链路是：

```text
HTTP POST /api/{apiName}
  -> route/battle/BattleHttp4sRoutes
  -> system.api.APIMessageRouter
  -> services.battle.routes.BattleRoutes
  -> XXXAPIMessage.plan(...)
  -> queue/session/results/runtime/table
  -> objects/apiTypes Encoder
  -> JSON response
```

这条链路方向是对的。它比旧的 route-heavy 结构清晰，因为 path/method/body/parser 不再散落到每个手写 route。

当前 `route/battle/BattleHttp4sRoutes.scala` 已经很薄：

- 接收 `BattleAPIRuntimeContext`
- 接收 `IdentityService`
- 接收 `Resource[IO, Connection]`
- 调用 `APIMessageRouter.routes(...)`
- 通过 `resolveUserToken` 把 session token 解析成 `UserId`

当前 `services/battle/routes/BattleRoutes.scala` 也已经不是传统 HTTP route，而是 APIMessage 注册表：

- result API 使用 `apiWithToken[...]`
- queue、room、state、command 仍使用 `apiWithTokenAndContext[...]`
- `BattleRoutes.apiMessages(context)` 仍需要 `BattleAPIRuntimeContext`

这说明 route 层已经变薄，但还没有达到你要求的最终形态。最终 `BattleRoutes` 应该更接近：

```scala
object BattleRoutes:
  val apiMessages: List[RegisteredAPIMessage] =
    List(
      apiWithToken[BattleQueueJoinAPIMessage, BattleQueueSnapshotResponse],
      apiWithToken[BattleQueueStatusAPIMessage, BattleQueueSnapshotResponse],
      apiWithToken[BattleQueueLeaveAPIMessage, BattleQueueLeaveResponse],
      apiWithToken[BattleRoomSnapshotAPIMessage, BattleRoomSnapshotResponse],
      apiWithToken[BattleRoomHeartbeatAPIMessage, BattleRoomSnapshotResponse],
      apiWithToken[BattleStateReadAPIMessage, BattleStateReadResponse],
      apiWithToken[BattleCommandAPIMessage, BattleCommandAcceptedResponse],
      apiWithToken[BattleResultListAPIMessage, BattleResultListResponse],
      apiWithToken[BattleResultRecordAPIMessage, BattleResultRecordResponse]
    )
```

也就是说，最终 route 不应该注入 queue service 或 state service。

## 3. 当前模块实现逻辑

### queue

当前位置：

```text
services/battle/microservices/queue/
  api/
  objects/
  services/
```

核心职责：

- `join`：玩家进入等待队列，创建或复用等待房间。
- `status`：通过 `ticketId` 查询排队状态。
- `leave`：通过 `ticketId` 离开等待队列。
- `roomSnapshot`：通过 `roomId` 查询等待房间快照。
- `heartbeat`：刷新等待区玩家在线状态。
- `activeBattleSession`：让 battle session 通过 `battleId` 查找已启动的 session seed。

类型安全结构：

- `QueueRoomLifecycle = Waiting | Active(session) | Finished(completedAt, session)` 已经是正确 ADT。
- `QueueRoomStartDecision = Start | Keep` 已经比 Boolean 更清楚。
- `BattleQueueSnapshot`、`RealtimeRoomSnapshot`、`BattleSessionDescriptor`、`BattleSessionBootstrap` 等是不可变 case class。
- `TicketId`、`RoomId`、`PlayerId`、`QueueRequestId`、`BattleCapacity`、`EpochMillis`、`DurationMillis` 都复用 value object。

主要问题：

- `InMemoryBattleQueueService` 使用 `AtomicReference[QueueRuntimeState]` 和 `lock.synchronized`，生产路径仍是内存状态机。
- queue API 仍是 `APIWithTokenContextMessage[BattleQueueService, Response]`，不是最终的 `APIWithTokenMessage[Response] + plan(connection)`。
- queue 的 APIMessage 位于 `microservices/queue/api`，不在目标路径 `battle/api/queue`。
- queue 的 apiTypes 位于 `microservices/queue/objects/apiTypes`，不在目标路径 `battle/objects/apiTypes/queue`。

判断：

- queue 的 ADT 建模方向是对的。
- queue 的副作用边界还不符合最终目标。
- 如果坚持所有 battle API 都只能 `plan(connection)`，queue/room ticket state 必须 PostgreSQL 化。

### session

当前位置：

```text
services/battle/microservices/session/services/
```

核心职责：

- 通过 `BattleSessionLookup` 找到 active battle session。
- `currentState(battleId)` 读取并推进 authoritative state。
- `acceptCommand(request)` 校验玩家命令并调用 runtime engine apply command。
- 管理 `StoredBattle`、command ownership、finish projection status。
- battle 结束时通知 queue room lifecycle sink。

类型安全结构：

- `BattleStateReadError = BattleNotFound`
- `BattleCommandSubmitError = BattleNotFound | PlayerNotFound | BotCommandsNotSupported | CommandNotAuthorized`
- `StoredBattle` 是不可变 case class，保存 state、ownership、projection status、lastUpdatedAt、pendingStepMs。
- command ownership 用 `PlayerId -> TicketId`，没有裸 String。

主要问题：

- `InMemoryBattleStateService` 使用 `private var battles: Map[BattleId, StoredBattle]`。
- 状态推进和存储仍在内存 service 中，而不是 database table。
- `BattleStateReadAPIMessage` 和 `BattleCommandAPIMessage` 都通过 `APIWithTokenContextMessage[BattleStateService, Response]` 调用 service。
- `BattleCommandAPIMessage` 里包含很长的手写 Circe decoder，这是 APIMessage 过厚。

判断：

- session 的业务结果 ADT 是好的。
- service 层 mutable state 不符合最终的 PostgreSQL authoritative state 目标。
- session 是后续迁移风险最高的部分，应该排在 queue/results 之后。

### runtime

当前位置：

```text
services/battle/microservices/runtime/
  api/BattleRuntimeRuleBook.scala
  objects/BattleRuntimeRuleDefinitions.scala
  database/BattleRuntimeRuleTable.scala
  services/BattleEngine.scala
  services/BattleRuntimeStepRules.scala
  ...
```

核心职责：

- 固定 tick 推进。
- 根据 server time 计算 elapsed/tick。
- 推进 slow fields、players、pickups、reload、held fire、projectiles、pickup collection。
- finalization，处理 finished phase、winner、result/replay ready。
- replay frame retention 和 event retention。

类型安全结构：

- `BattleRuntimeRuleSet`、`BattleRuntimeRuleConfig`、`BattleHistoryRuleConfig`、`BattleSessionPlayerRuleConfig` 表达 runtime 配置。
- `BattleTick`、`ElapsedMillis`、`DurationMillis` 封装时间和 tick。
- `BattlePhase` 是 enum。

主要问题：

- `BattleRuntimeRuleBook` 使用 `AtomicReference[Option[BattleRuntimeRuleSet]]` 缓存 PostgreSQL 规则。
- `BattleEngine` 是当前 runtime 总入口，调用 actors、combat、abilities、world 多个域。
- runtime 作为 orchestrator 必然调用其他规则域，所以“不允许不同业务逻辑之间方法调用”不能机械执行；合理目标应该是单向 pipeline，不是完全零调用。

判断：

- runtime rules 不应塞进 APIMessage。
- 如果严格只有四层目录，runtime pure rules 应放在 `objects/runtime/rules` 或 `objects/runtime`，但这会让 `objects` 变厚。
- 更干净的架构其实需要额外 `rules` 或 `engine` 层；如果你坚持四层，必须接受 `objects` 承载 pure rule functions。

### world

当前位置：

```text
services/battle/microservices/world/
  api/BattleWorldRuleBook.scala
  objects/BattleWorldRuleDefinitions.scala
  database/BattleWorldRuleTable.scala
  services/BattleArenaCatalog.scala
  services/BattleMotionRules.scala
  ...
```

核心职责：

- 读取 PostgreSQL 中的 world/movement/map 规则。
- 加载 map spec JSON。
- 生成 collision obstacles、spawn points、pickup definitions。
- 提供地图尺寸、tile size、player collision radius、projectile clearance。
- 提供移动和碰撞 resolution。

类型安全结构：

- `ArenaObstacleKind`、`ArenaObstacleShape` 是 ADT。
- `BattleLoadedMapSpec` 把 map id、theme、world size、spawn points、collision obstacles、pickup definitions 绑定到一起。
- `BattleMapId`、`BattleVector2`、`Radius`、`PickupId` 等来自共享 battle objects。

主要问题：

- `BattleWorldRuleBook` 是全局 `AtomicReference`。
- `BattleWorldRuleTable.readMap` 里承担了 map spec JSON decode、collision object construction、pickup definition construction，这已经超出最纯粹的 Table 语义。
- 如果 database 只允许 Table/Initializer，map JSON 到 domain 的解析应被视为 row/domain mapping 边界，不能继续扩展成 game rule service。

判断：

- world 规则入 PostgreSQL 的方向正确。
- 需要把 `RuleBook` 从 `api` 移走；它不是 APIMessage。
- `BattleWorldRuleTable` 可以保留加载 row 并转为 domain rule set，但不应继续变成地图业务 service。

### combat

当前位置：

```text
services/battle/microservices/combat/
  api/BattleCombatRuleBook.scala
  objects/BattleCombatRuleDefinitions.scala
  database/BattleCombatRuleTable.scala
  services/BattleWeaponFireRules.scala
  services/BattleProjectileRuntimeRules.scala
  ...
```

核心职责：

- 武器库存规则、开火规则、热量规则。
- projectile factory、motion、impact、targeting、terminal。
- weapon reload、fire cooldown、heat overheat。
- damage 和 splash damage。

类型安全结构：

- `BattleWeaponFiringResource = Magazine | Heat`
- `BattleWeaponRuleDefinition` 组合 `inventory` 和 `fire`。
- `BattleWeaponProjectileDefinition` 封装 projectile kind、speed、damage、radius、lifetime、splash、count、spread。
- `WeaponKind`、`ProjectileKind`、`Damage`、`Radius`、`CooldownMillis`、`DurationMillis` 来自共享 objects。

主要问题：

- `BattleCombatRuleBook` 使用 `AtomicReference[Map[WeaponKind, BattleWeaponRuleDefinition]]`。
- `BattleWeaponFireRules.applyPrimaryFire` 仍按 `WeaponKind` 分支处理 Pistol/RocketLauncher/Gatling/Shotgun，这个分支是业务规则，可以保留，但不应出现在 database 或 APIMessage。
- `BattleCombatRuleBook` 位于 `api`，但它不是 APIMessage，这是命名和层级污染。

判断：

- combat 的动态配置入 PostgreSQL 方向正确。
- combat 的 pure rules 应迁出 database/api 语义，放入用户确认后的规则归属位置。

### actors

当前位置：

```text
services/battle/microservices/actors/
  api/BattleBotRuleBook.scala
  objects/BattleActorRuleDefinitions.scala
  database/BattleActorRuleTable.scala
  services/BattleBotRules.scala
  services/BattlePlayerRuntimeRules.scala
  ...
```

核心职责：

- player movement runtime。
- bot movement、aim、reload、pickup seeking、opening fire delay、fire pulse。
- input command application helper。
- player lifecycle/respawn。

类型安全结构：

- `BattleBotRuleConfig` 封装 bot 所有动态参数。
- `BattleBotMoveSpeed`、`Radius`、`DurationMillis` 等避免裸 primitive 传递。
- player state 使用 `BattlePlayerState`，life state 使用 `BattlePlayerLifeState`。

主要问题：

- `BattleBotRuleBook` 使用全局 `AtomicReference`。
- bot/player runtime rules 位于 `services`，但最终四层结构中没有 `services` 位置。
- `lowHealthRatio`、`pickupHealthRatio`、`tacticalReloadRatio` 仍是 `Double`，可接受但最好后续建 value object。

判断：

- bot 参数入 PostgreSQL 已经符合“动态配置”目标。
- 但 rule book 全局缓存和目录命名仍不符合最终结构。

### abilities

当前位置：

```text
services/battle/microservices/abilities/
  api/BattleSkillRuleBook.scala
  api/BattlePickupRuleBook.scala
  objects/BattleAbilityRuleDefinitions.scala
  database/BattleAbilityRuleTable.scala
  services/BattleSkillCommandRules.scala
  services/BattlePickupRules.scala
  services/BattleSlowFieldRuntimeRules.scala
```

核心职责：

- Blink/Dash/Freeze 技能规则。
- 技能 cooldown/active duration。
- pickup contact radius、medkit heal、respawn duration。
- slow field runtime。

类型安全结构：

- `BlinkConfig`、`DashConfig`、`FreezeConfig` 是明确配置对象。
- `BattleSkillRuntime` 包含 cooldown 和 active duration。
- `SkillDistance`、`DurationMillis`、`Radius` 有值对象。

主要问题：

- `BattleSkillRuleBook` 和 `BattlePickupRuleBook` 是全局缓存。
- skill/pickup runtime rules 不应位于 `api` 或 `database`。
- 如果最终只有四层，需要决定这些 pure rules 进入 `objects/abilities` 还是新增规则层。

判断：

- abilities 配置入 PostgreSQL 正确。
- 需要把 rule book 从 `api` 命名中剥离。

### results

当前位置：

```text
services/battle/microservices/results/
  api/
  objects/
  database/
```

核心职责：

- battle result list。
- battle result record save。
- PostgreSQL `battle_results` table。
- result response encoder。

类型安全结构：

- `BattleResultList`
- `BattleResultRecord`
- `BattleFinishProjectionStatus`
- `BattleFinishProjectionOutcome`
- `BattleResultId`、`BattlePlacement`、`Score`、`RatingDelta` 等 value object。

当前优点：

- result API 已经接近目标：`BattleResultListAPIMessage` 使用 `APIWithTokenMessage[BattleResultList]` 和 `plan(connection)`。
- result database 已经是 `BattleResultTable` / `BattleResultTableInitializer`。

主要问题：

- 目录仍在 `microservices/results`。
- `BattleResultListAPIMessage.decodeRequest` 用 `payload.as[BattleResultListQuery].getOrElse(...)`，失败默认静默 fallback，类型安全和契约严格性不足。
- `BattleResultTable` 有 `save/list` 动态方法，这在当前仓库风格里是 Table 的正常边界；如果你要求 Table 只有 SQL，不允许 repository-like 方法，需要另行明确。

判断：

- results 是第一批迁回四层结构的最佳目标。
- 它依赖较少，已经 connection-backed，迁移风险最低。

### projections

当前位置：

```text
services/battle/microservices/projections/services/
```

核心职责：

- battle finish 后创建 result/replay/mail artifact。
- 写 replay。
- 写 mail。
- 写 result。
- 生成 settlement scoring、labels、timeline、replay frames JSON。

类型安全结构：

- `BattleFinishProjectionOutcome`
- `BattleFinishProjectionStatus`
- replay/result/mail 通过 port 连接外部服务。

主要问题：

- projection 不应该归在 database。
- replay JSON renderer 不是 database 职责。
- projection 涉及 battle -> replay/mail/results 的跨域副作用，应通过 port 或 APIMessagePlanner，而不是让 battle 直接知道外部 repository 细节。

判断：

- projections 应最后迁，因为它跨边界最多。
- 短期可保留，只要不继续扩张。

## 4. ADT 和类型安全结构

当前 battle 已有较好的类型安全基础，应保留并继续作为唯一事实来源。

核心 value object：

- `BattleId`
- `RoomId`
- `TicketId`
- `PlayerId`
- `HeroId`
- `ProjectileId`
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
- `Radius`
- `Damage`
- `Score`
- `Rating`
- `RatingDelta`

核心 enum / ADT：

- `BattleMode`
- `MatchmakingRoomPhase`
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
- `QueueRoomLifecycle`
- `BattleStateReadError`
- `BattleCommandSubmitError`

当前正确点：

- 大多数有限状态不是 String，而是 enum 或 sealed-style ADT。
- 大多数领域 id、时间、数值不是裸 primitive，而是 value object。
- authoritative state 大多是 immutable case class，通过 copy/update 推进。

当前不足：

- API error 最终仍以字符串 wire code 输出是可以的，但内部不应长期传字符串。
- `BattleCommandAPIMessage` 中 decoder 仍把 field-level validation 写在 APIMessage 内。
- production queue/session 使用 mutable service 模拟状态机，不符合最终 `plan(connection)` 目标。
- `objects/apiTypes` 必须检查是否重复声明核心对象；如果重复，应删掉并复用 `objects/core`、`objects/player`、`objects/weapon` 等已有类型。

## 5. Circe 边界

当前已使用：

- `io.circe.Decoder`
- `io.circe.Encoder`
- `io.circe.generic.semiauto.deriveEncoder`
- `io.circe.generic.semiauto.deriveDecoder`
- `io.circe.parser.decode`
- http4s-circe 通过 `APIMessageRouter` 做 JSON request/response。

合理目标：

```text
objects/<domain>/
  ADT / value object / immutable state / pure companion

objects/apiTypes/<domain>/
  final case class XXXRequest
  final case class XXXResponse
  object XXXRequest { given Decoder[XXXRequest] = ... }
  object XXXResponse { given Encoder[XXXResponse] = ... }

api/<domain>/
  final case class XXXAPIMessage(...) extends APIWithTokenMessage[XXXResponse]
```

需要特别明确：

- `apiTypes` 可以有 private decoder helper，因为 command/request contract 有复杂校验。
- APIMessage 文件不应该有大型 HCursor decoder。
- domain object companion 不应该普遍挂 Circe encoder/decoder，否则 domain 会依赖 wire transport。
- `wireValue` 和 `fromWire` 应保留在 enum companion，作为 wire codec 的底层映射。
- 最终 HTTP JSON 可以是 String code，但业务流内部必须先是 ADT，再在边界映射成 String。

当前最高置信度问题：

- `BattleCommandAPIMessage.scala` 内部有很长 decoder，应迁到 `objects/apiTypes/command`。
- queue/room APIMessage companion 也有 decode failure mapping，可以保留少量 error mapping，但 request decoder 应主要下沉到 apiTypes。
- result list request 的 decode fallback 应变成明确 decoder result，避免坏请求静默变默认查询。

## 6. Cats Effect / IO 边界

当前已具备的基础：

- `APIMessage.plan(connection): IO[Response]`
- `APIWithTokenMessage[Response]`
- `APIWithTokenContextMessage[Context, Response]`
- `Resource[IO, Connection]`
- `IO.blocking` 包裹 JDBC 或旧同步 service 调用

正确方向：

```text
HTTP request
  -> APIMessageRouter
  -> XXXAPIMessage.plan(connection)
  -> Table read/write in IO
  -> pure domain transition
  -> Table save in IO
  -> XXXResponse Encoder
```

当前问题：

- queue/state/command 仍是 `APIWithTokenContextMessage`。
- route 仍需注入 `BattleAPIRuntimeContext`。
- production path 中 queue/session 的副作用边界在 mutable service，不在 database table。
- `IO` 目前很多只是包了一层 `IO.blocking(service.call)`，还不是完整 effect-safe application flow。

最终要求如果是“所有 battle API 都强制 `plan(connection)`”，那么必须迁移：

- queue tickets
- queue rooms
- room heartbeat
- active battle session seed
- stored battle state
- accepted command log 或 command ownership

否则 APIMessage 没有地方拿到 queue/state service。

## 7. Render / 前后端契约技术

后端这里的 render 不等于 Phaser 画面渲染。后端真正负责的是 wire projection：

- `objects/apiTypes/state/*` 把 `BattleAggregateState` 编码成前端 authoritative state JSON。
- result apiTypes 把 `BattleResultRecord` / `BattleResultList` 编码成结果 JSON。
- projection/replay 代码把 battle finish state 转成 replay/result/mail artifact。

前端实际渲染在 Phaser 3：

- `frontend/src/runtime/battle/game/renderer/createBattleRuntime.ts` 创建 Phaser runtime。
- `frontend/src/runtime/battle/authoritative/authoritativeBattleClient.ts` 调用 battle APIMessage 并 normalize authoritative response。
- `frontend/src/runtime/battle/authoritative/battleContractAdapter.ts` 做 local DTO 和 battle runtime DTO 互转。
- `frontend/src/objects/battle/contracts/apiMessages.ts` 当前手写前端 battle wire DTO。
- `frontend/src/objects/battle/contracts/snapshots.ts` 把后端 state response alias 到前端 battle snapshot DTO。

对后端重构的约束：

- 后端包路径可以变，但 JSON field name 不能漂移。
- enum wire value 不能漂移，例如 `WeaponKind`、`ProjectileKind`、`SkillKind`、`BattlePhase`。
- optional / nullable 语义不能漂移，例如 `reserveAmmo: number | null`、`winnerPlayerId?: string`、`weaponKind?: BattleWeaponKindDto`。
- `BattleStateReadAPIMessage` response shape 是前端 Phaser authoritative runtime 的关键输入，迁移时必须用 contract test 或 TS 类型核对。

当前前端仍有手写 normalize，这是前端 runtime safety 的兼容层，不是后端重构第一优先级。后端当前更重要的是保持 API contract 不变。

## 8. 路线合理性判断

合理部分：

- 用 `APIMessageRouter` 和 `apiNameFromClassName` 替代 rewrite。
- `BattleRoutes` 只做 typed APIMessage registry。
- `api` 放 `XXXAPIMessage.scala`。
- `objects` 作为 ADT/value object/domain state 单一事实来源。
- `objects/apiTypes` 作为 request/response DTO 与 Circe codec 边界。
- `database` 收缩为 PostgreSQL Table/TableInitializer。
- 业务域仍按 queue/session/runtime/world/combat/actors/abilities/results/projections 分类，但分类要下沉到四层目录内部。

需要修正的部分：

- 不建议把所有 pure game rules 都塞进 APIMessage private def。
- 不建议把所有 Circe codec 都直接放到 domain object companion。
- 不建议长期保留 `battle/microservices`。
- 不建议把 `RuleBook` 放在 `api` 下，因为它不是 APIMessage。
- 不建议 `database` 再承载 `BattleEngine`、`BattleWeaponFireRules`、`BattleBotRules` 这类业务规则。
- 不建议强行禁止 runtime 调 actors/combat/world/abilities；合理目标是单向调用 pipeline，不是完全隔离。

## 9. 推荐目标目录

建议目标形态：

```text
services/battle/
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
    actors/
    abilities/
    results/
    projections/
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

如果你严格不允许第五层 `rules` 或 `engine`，那么 pure runtime/game rules 只能进入 `objects/<domain>`。这能满足四层目录，但会让 `objects` 不再是纯 passive data。架构上更干净的方案是允许 `rules/` 或 `engine/`，但这超出你当前四层约束，需要你明确放宽。

## 10. 推荐依赖方向

推荐：

```text
route/battle adapter
  -> services.battle.routes
    -> services.battle.api
      -> services.battle.database
        -> services.battle.objects
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
- `runtime pipeline -> actors/combat/abilities/world pure rules`

禁止：

- `objects -> api`
- `objects -> routes`
- `objects -> database`
- `database -> api`
- `database -> routes`
- `routes -> database`
- `apiTypes` 重复声明核心 domain ADT/value object
- `RuleBook` 伪装成 APIMessage
- route 注入 production mutable service context
- queue/session/runtime 互相双向调用内部实现

## 11. 迁移顺序建议

### Phase 0：冻结当前基线

目标：

- 确认当前 dirty worktree 是否全部是预期改动。
- 跑 `sbt compile` 和 contract runner。
- 不在未稳定状态下开始大迁移。

### Phase 1：results 先迁

原因：

- results 已经最接近目标。
- 已经有 `APIWithTokenMessage + plan(connection)`。
- 已经有 `BattleResultTable` 和 `BattleResultTableInitializer`。
- 不依赖 queue/session mutable runtime。

目标：

- `microservices/results/api` -> `api/results`
- `microservices/results/objects/result` -> `objects/results`
- `microservices/results/objects/apiTypes/results` -> `objects/apiTypes/results`
- `microservices/results/database` -> `database/results`
- `BattleRoutes` 对 result 只保留 `apiWithToken[...]`

### Phase 2：command codec 下沉

目标：

- 把 `BattleCommandAPIMessage` 中的大型 decoder 移到 `objects/apiTypes/command`。
- APIMessage 只保留 case class、plan、少量 error mapping。
- 不改变 JSON shape。

### Phase 3：queue/room PostgreSQL 化

目标：

- 新增或迁移 `database/queue`、`database/room` 的 Table/Initializer。
- `BattleQueueJoinAPIMessage`、`BattleQueueStatusAPIMessage`、`BattleQueueLeaveAPIMessage`、`BattleRoomSnapshotAPIMessage`、`BattleRoomHeartbeatAPIMessage` 全部变成 `APIWithTokenMessage`。
- 删除 production path 对 `BattleQueueService` context 注入的依赖。

### Phase 4：session/state/command PostgreSQL 化

目标：

- authoritative state 从 table 加载、推进、保存。
- `BattleStateReadAPIMessage` 和 `BattleCommandAPIMessage` 不再依赖 `BattleStateService` context。
- runtime engine 继续保持 pure old state -> new state transition。

### Phase 5：runtime/world/combat/actors/abilities 归位

目标：

- Table/Initializer 进入 `database/<domain>`。
- rule config ADT 进入 `objects/<domain>`。
- pure rules 根据你的决策放入 `objects/<domain>` 或允许的 `rules/engine` 层。
- `Battle*RuleBook` 不再位于 `api`。

### Phase 6：projection/replay 边界收束

目标：

- result/replay/mail artifact 通过明确 port 或 API plan 输出。
- replay JSON projection 不放在 database。
- battle runtime rules 不直接拥有 mail/replay repository 细节。

### Phase 7：删除过渡层

目标：

- 删除或迁出 `battle/microservices`。
- 删除 production path 的 `BattleAPIRuntimeContext`。
- 删除 production path 的 `InMemoryBattleQueueService` 和 `InMemoryBattleStateService`。
- `BattleRoutes` 只暴露 typed `val apiMessages: List[RegisteredAPIMessage]`。

## 12. 决策点

继续动源码前，需要你确认下面几个问题。

### 决策 A：pure runtime/game rules 放哪里

选项 A1：严格四层，pure rules 放 `objects/<domain>`。

影响：满足目录要求，但 `objects` 会变厚，不再只是 passive data。

选项 A2：允许额外 `rules/` 或 `engine/`。

影响：架构更干净，但超出“至少严格包含四层”之外，需要你明确允许。

我的建议：如果你坚持四层，选 A1；如果你允许更长期可维护，选 A2。

### 决策 B：queue/session 是否必须立刻 PostgreSQL 化

选项 B1：立刻 PostgreSQL 化。

影响：可以真正删除 `APIWithTokenContextMessage` 和 `BattleAPIRuntimeContext`，但改动大。

选项 B2：先迁 results/codec/package，再分阶段 PostgreSQL 化 queue/session。

影响：风险低，但短期还会保留 context 注入。

我的建议：选 B2，先拿 results 做第一个可验证闭环。

### 决策 C：apiTypes 是否允许 private decoder helper

选项 C1：只允许 `deriveEncoder/deriveDecoder`。

影响：简单 DTO 可以，但 command 复杂校验不够表达。

选项 C2：允许 apiTypes 内有 private decoder helper。

影响：APIMessage 会变薄，JSON 边界集中在 apiTypes。

我的建议：选 C2。

### 决策 D：BattleEnums 是否现在拆分

选项 D1：短期保留 `BattleEnums.scala` 统一 enum。

影响：避免迁移时重复 enum，风险低。

选项 D2：现在拆成 `objects/enums/*`。

影响：更整洁，但 import/package 改动大，不适合和 API/database 迁移混做。

我的建议：选 D1。

## 13. 下一票建议

如果你接受我的建议组合：

```text
A1 或 A2
B2
C2
D1
```

下一票应该做：

```text
BE-BATTLE-RESULTS-FOUR-LAYER-01

目标：
把 battle results 从 microservices/results 迁回四层结构。

边界：
services/battle/microservices/results
services/battle/api/results
services/battle/objects/results
services/battle/objects/apiTypes/results
services/battle/database/results
services/battle/routes/BattleRoutes.scala

不做：
不动 queue/session/runtime。
不改前端 JSON contract。
不改 PostgreSQL schema 语义。
不重写 result 业务。

验证：
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
```

这个票据是最安全的，因为 results 已经 connection-backed，能先验证四层迁移方式，而不会立刻碰 queue/session 的 authoritative state 大迁移。

## 14. 当前报告阶段验收

| 要求 | 当前结论 |
| --- | --- |
| 整理 battle 模块实现逻辑 | 已覆盖 queue/session/runtime/world/combat/actors/abilities/results/projections |
| 说明类型安全结构 | 已覆盖 ADT、enum、value object、immutable state |
| 说明 Circe | 已说明 apiTypes/Decoder/Encoder/derive/manual decoder 边界 |
| 说明 Cats Effect | 已说明 `IO`、`plan(connection)`、`Resource[IO, Connection]`、`IO.blocking` |
| 说明 render 技术 | 已区分后端 wire projection 与前端 Phaser 3 渲染消费 |
| 分析四层路线合理性 | 已给出合理部分、需要修正部分和目标目录 |
| 让你进一步决策 | 已列出 A/B/C/D 决策点 |

本报告没有开始源码迁移。原因是 route/context、queue/session PostgreSQL 化、pure rules 归属这三个问题会决定后续目录和代码形态，先确认再迁移更安全。
