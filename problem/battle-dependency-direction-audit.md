# Battle Dependency Direction Audit

更新日期：2026-05-27

## 1. 审计目标

本报告审计 `services/battle` 四层依赖方向和 `database` 内部子域依赖方向。

目标方向：

```text
routes -> api -> database -> objects
api -> objects/apiTypes
api -> objects/domain
database -> objects
objects -> 不依赖 api/routes/database
database -> 不依赖 api/routes
```

统计口径：

- 只统计 Scala import 声明。
- 不等同完整 call graph。
- 可以证明文件层依赖方向是否变清晰。

## 2. 四层依赖结论

当前四层方向基本成立：

- 未发现 `objects -> api/routes/database`。
- 未发现 `database -> api/routes`。
- 未发现 `api -> routes`。
- 未发现旧 `services.battle.application` / `engine` / `persistence` / `ports` package 引用。
- `routes -> database` 只来自 runtime context / result backend 注入，不直接执行业务规则。
- `session -> projections` 已消除；session 通过 `objects/result` 中的 `BattleFinishProjector` port 和 `BattleFinishProjectionOutcome` ADT 协作。
- `projections -> session` 也已消除；projection artifact 实现不再读取 session 内部状态。

当前结构：

```text
routes
  -> api
  -> database runtime context
api
  -> database service/table/storage context
  -> objects/apiTypes
  -> objects
database
  -> objects
objects
  -> no upper layer dependency found
```

## 3. API 注册边界

当前 `BattleRoutes` 不维护字符串 API 名称清单：

- 没有 `apiMessageNames: List[String]`。
- 没有 `BattleAPIMessageSpec`。
- 没有 `apiName("...")`。
- `BattleRoutes.apiMessages(...)` 返回 `List[RegisteredAPIMessage]`。
- `BattleRoutes` 直接用 `apiWithTokenAndContext[Context, Message, Response]` 注册每个 API。
- API path 由 `RegisteredAPIMessage` 通过 message class 推导，例如 `BattleQueueJoinAPIMessage -> /api/battlequeuejoin`。
- 各 `XXXAPIMessage` companion 不再保留重复的 `registered(...)` 包装方法。
- `services/battle/api` 下没有 `apiWithTokenFromJson` 残留。
- `BattleResultStorage` 已迁到 `database/results`，不再由 `api/results/BattleResultListAPIMessage.scala` 声明执行后端 ADT。
- `BattleAPIRequestError` 已成为统一 API request error ADT；queue/room/result/command/state 不再各自声明 decode error enum。
- `BattleCommandRequestField` 已从 `objects/apiTypes/command` 迁到 `objects`，避免 command request 字段错误靠裸字符串散落。
- `objects/apiTypes` 下已无 enum，只保留 DTO、codec 和 state response renderer。
- `services/battle/api` 下已无 `private enum ...Error` 残留。
- `BattleStateResponseRenderer` 已迁到 `objects/apiTypes/state`，`api/state/BattleStateReadAPIMessage.scala` 不再承载大型 response render 投影。
- `api` 层已无 `services.battle.objects.*` wildcard import，APIMessage 文件不再依赖 `objects/package.scala` 的宽泛 re-export。
- `projections/BattleReplayFrameTimelineRules.scala` 已移除 `services.battle.objects.*` wildcard import。
- `projections/BattleFinishProjectionReplayRules.scala` 已移除 `services.battle.objects.*` wildcard import。
- `projections/BattleFinishProjectionLabelRules.scala` 已移除 `services.battle.objects.*` wildcard import。
- `projections/BattleFinishProjectionPlanner.scala` 已移除 `services.battle.objects.*` wildcard import。
- `projections/BattleFinishProjectionService.scala` 已移除 `services.battle.objects.*` wildcard import。
- `projections/BattleReplayFramesJsonRenderer.scala` 已移除 `services.battle.objects.*` wildcard import。
- `database/projections` 子域已无 `services.battle.objects.*` wildcard import；database 内该类 wildcard import 当前剩 35 个。
- `queue/BattleQueueIdAllocator.scala` 已移除 `services.battle.objects.*` 和无用的 `services.battle.database.session.*` wildcard import；database 内该类 object wildcard import 当前剩 34 个。
- `queue/BattleQueueServiceContracts.scala` 已移除 `services.battle.objects.*` wildcard import；database 内该类 object wildcard import 当前剩 33 个。
- `queue/BattleQueueRequestReuseRules.scala` 已移除 `services.battle.objects.*` 和无用的 `services.battle.database.session.*` wildcard import；database 内该类 object wildcard import 当前剩 32 个。
- `queue/BattleQueueLeaveRules.scala` 已移除 `services.battle.objects.*` 和无用的 `services.battle.database.session.*` wildcard import；database 内该类 object wildcard import 当前剩 31 个。
- `queue/BattleQueueHeartbeatRules.scala` 已移除 `services.battle.objects.*` 和无用的 `services.battle.database.session.*` wildcard import；database 内该类 object wildcard import 当前剩 30 个。
- `queue/BattleQueueJoinRules.scala` 已移除 `services.battle.objects.*` 和无用的 `services.battle.database.session.*` wildcard import；database 内该类 object wildcard import 当前剩 29 个。
- `queue/BattleQueueTicketSnapshots.scala` 已移除 `services.battle.objects.*` 和无用的 `services.battle.database.session.*` wildcard import；database 内该类 object wildcard import 当前剩 28 个。
- `queue/BattleQueueRuntimeModel.scala` 已移除 `services.battle.objects.*` 和无用的 `services.battle.database.session.*` wildcard import；database 内该类 object wildcard import 当前剩 27 个。
- `queue/BattleQueueRoomLifecycleRules.scala` 已移除 `services.battle.objects.*` 和无用的 `services.battle.database.session.*` wildcard import；database 内该类 object wildcard import 当前剩 26 个。
- `queue/BattleQueueSessionLookupRules.scala` 已移除 `services.battle.objects.*` 和 `services.battle.database.session.*` wildcard import；database 内该类 object wildcard import 当前剩 25 个。
- `queue/BattleRoomBootstrapper.scala` 已移除 `services.battle.objects.*` 和无用的 `services.battle.database.session.*` wildcard import；database 内该类 object wildcard import 当前剩 24 个。
- `queue/BattleQueueService.scala` 已移除 `services.battle.objects.*` 和 `services.battle.database.session.*` wildcard import；database 内该类 object wildcard import 当前剩 23 个。
- `database/queue` 子域已无 `services.battle.objects.*` wildcard import。
- `queue/BattleQueueRoomSelectionRules.scala` 已移除无用 session wildcard；`BattleQueueAuthorizationService.scala` 和 `BattleQueueParticipantRules.scala` 仍有无用 session wildcard，但当前文件包含非 UTF-8 字节，`apply_patch` 无法安全处理。
- `session/BattleFinishProjectionPreparationRules.scala` 已移除 `services.battle.objects.*` wildcard import；database 内该类 object wildcard import 当前剩 22 个。
- `session/BattleStateServiceModels.scala` 已移除 `services.battle.objects.*` 和无用的 `services.battle.database.session.*` wildcard import；database 内该类 object wildcard import 当前剩 21 个。
- `session/BattleStoredBattleInitializationRules.scala` 已移除 `services.battle.objects.*` 和无用的 `services.battle.database.session.*` wildcard import；database 内该类 object wildcard import 当前剩 20 个。
- `session/BattleCommandAcceptanceFactory.scala`、`BattleSessionStateFactory.scala`、`BattleStoredBattleAdvanceRules.scala` 仍有 object wildcard import，但当前文件包含非 UTF-8 字节，`apply_patch` 无法安全处理。
- `actors/BattleBotRules.scala` 已移除 `services.battle.objects.*` 和无用的 database 子包 wildcard import；database 内该类 object wildcard import 当前剩 19 个。
- `session/BattleStateService.scala` 已移除 `services.battle.objects.*` 和无用的 `services.battle.database.session.*` wildcard import；database 内该类 object wildcard import 当前剩 18 个。
- `database` 下 23 个非 UTF-8 Scala 文件已归一化为 UTF-8；后续可以继续用 `apply_patch` 清理剩余 wildcard import。
- `session/BattleCommandAcceptanceFactory.scala`、`session/BattleStoredBattleAdvanceRules.scala`、`session/BattleSessionStateFactory.scala`、`runtime/BattleEventFactory.scala` 已移除 `services.battle.objects.*` wildcard import；database 内该类 object wildcard import 当前剩 14 个。
- `actors/BattleInputRules.scala`、`combat/BattleWeaponCatalog.scala`、`combat/BattleWeaponRules.scala` 已移除 `services.battle.objects.*` wildcard import；database 内该类 object wildcard import 当前剩 11 个。
- `actors/BattlePlayerLifecycleRules.scala`、`combat/BattleProjectileMotionRules.scala`、`combat/BattleProjectileTargetingRules.scala` 已移除 `services.battle.objects.*` wildcard import；database 内该类 object wildcard import 当前剩 8 个。
- `combat/BattleProjectileFactoryRules.scala`、`combat/BattleWeaponFireRules.scala`、`combat/BattleProjectileTerminalRules.scala` 已移除 `services.battle.objects.*` wildcard import；database 内该类 object wildcard import 当前剩 5 个。
- `abilities/BattlePickupRules.scala`、`abilities/BattleSkillCommandRules.scala`、`actors/BattlePlayerRuntimeRules.scala` 已移除 `services.battle.objects.*` wildcard import；database 内该类 object wildcard import 当前剩 2 个。
- `combat/BattleProjectileImpactRules.scala`、`combat/BattleProjectileRuntimeRules.scala` 已移除 `services.battle.objects.*` wildcard import；database 内该类 object wildcard import 当前剩 0 个。
- `database` 下已无 `services.battle.objects.*` wildcard import。
- `actors/BattleBotCatalog.scala`、`abilities/BattleSkillCatalog.scala`、`session/BattleIdGenerator.scala`、`session/BattleFailureMessageFormatter.scala` 已移除 database 子域 wildcard import。
- `abilities/BattleSlowFieldRuntimeRules.scala`、`abilities/BattleSkillRules.scala`、`combat/BattleHeldFireRuntimeRules.scala`、`queue/BattleQueueAuthorizationService.scala`、`queue/BattleQueueParticipantRules.scala` 已移除剩余 database 子域 wildcard import。
- `database` 下已无 `services.battle.database.*` 子域 wildcard import。

当前 battle API 注册形态：

```text
queue/room/state/command -> APIWithTokenContextMessage[RuntimeService, Response]
results                  -> APIWithTokenContextMessage[BattleResultStorage, Response]
```

这比把 service/repository/storage 放进 message 字段更安全，因为 message 只代表 request 解码结果，执行依赖通过 typed context 进入 `plan(context, connection)`。

## 4. database 子域 import matrix

统计范围：

```text
backend/src/main/scala/services/battle/database/**/*.scala
```

统计规则：

- 只统计 `import services.battle.database.<domain>`。
- 不统计 self-import。
- 数字表示 import 声明数量，不表示函数调用次数。

当前单向边：

```text
abilities   -> actors:       5
abilities   -> combat:       6
abilities   -> runtime:     10
abilities   -> world:       10
actors      -> abilities:    5
actors      -> combat:       8
actors      -> runtime:      6
actors      -> world:       12
combat      -> abilities:   10
combat      -> actors:      11
combat      -> runtime:     15
combat      -> world:       21
projections -> results:      2
queue       -> session:     15
runtime     -> abilities:    4
runtime     -> actors:       5
runtime     -> combat:       3
runtime     -> world:        2
session     -> runtime:      4
```

当前 `world` 反向边：

```text
world -> abilities: 0
world -> actors:    0
world -> combat:    0
world -> runtime:   0
```

当前双向边：

```text
abilities <-> actors:      5 / 5
abilities <-> combat:      6 / 10
abilities <-> runtime:    10 / 4
actors    <-> combat:      8 / 11
actors    <-> runtime:     6 / 5
combat    <-> runtime:    15 / 3
```

## 5. 已完成的 world 方向修复

`BE-BATTLE-WORLD-IMPORT-31` 已完成：

- 移除了 `database/world` 下旧 engine 模板式 wildcard imports。
- `world` 不再 import `abilities/actors/combat/runtime`。
- `BattlePickupDefinition` 已从 `database/abilities` 迁到 `objects/pickup`，因为它是地图拾取物定义，不是能力规则。
- `BattleInitialLayout.initialPickups` 直接从 `BattleArenaCatalog.PickupDefinitions.map(_.initialState)` 构造初始拾取物。
- `BattlePickupCatalog` 仍可以依赖 world 的 map pickup definitions，但方向是 `abilities -> world`，不再是双向。

当前 world 职责更清晰：

- map facts
- geometry
- collision
- movement constraints
- spawn points
- map spec loading

world 现在不再知道 weapon、player runtime、skill runtime 或 tick orchestration。

## 6. 主要发现

### 发现 1：queue -> session 是单向

`queue` 依赖 `session`，但当前统计没有发现 `session -> queue`。

这说明之前最小的 queue/session 双向依赖已经消除：

- `BattleRoomLifecycleSink` 位于 session。
- queue 可以通过 session public contract 协作。
- session 不再直接 import queue。

这条方向可以接受：

```text
queue -> session public contracts -> objects
```

### 发现 2：session/projections 双向依赖已消除

已完成的方向修复：

- `BattleFinishProjectionStatus` 已迁到 `objects/result`。
- `BattleFinishProjectionOutcome` / `BattleFinishProjector` / `NoopBattleFinishProjector` 已迁到 `objects/result`。
- `BattleFinishProjectionPreparationRules` / `BattleFinishProjectionCompletionRules` / `BattleFinishProjectionStatusRules` 已迁到 `database/session`，因为它们直接修改 `StoredBattle`。
- `BattleStateService` 不再 import `database/projections`。
- `database/projections` 下旧模板式 `session.*` wildcard imports 已删除。

当前 `session -> projections` 和 `projections -> session` 都为 0。

`BE-BATTLE-PROJECTION-STATUS-32`、`BE-BATTLE-PROJECTION-PORT-35` 与 `BE-BATTLE-PROJECTION-INPUT-36` 已完成：

- `BattleFinishProjectionStatus` 从 projections implementation 迁到 `objects/result`。
- `BattleFinishProjectionOutcome` 和 `BattleFinishProjector` 从 projections implementation 迁到 `objects/result`。
- session 和 projections 都直接依赖 objects 中的 projection contract。
- `session -> projections` import 数从 1 降到 0。
- `projections -> session` import 数从 11 降到 0。

剩余建议方向：

```text
session -> objects/result projection port/outcome ADT
projections -> objects/result + database/results + replay/mail ports
```

可拆解票据：

- 无需继续针对 session/projections 做端口切分；下一步应处理 runtime/actors/combat/abilities 的旧规则网。

### 发现 3：abilities/actors/combat/runtime 仍是旧 engine rules 网

剩余最强规则网：

```text
abilities
actors
combat
runtime
```

这些子域仍存在大量互相 import，根因是旧 engine/rules 迁移后保留了全域 wildcard import 模板。

建议方向：

```text
runtime orchestrates tick
  -> actors update players/bots
  -> combat update weapons/projectiles
  -> abilities update skills/pickups/slow fields
  -> world provides geometry/collision/map facts
```

当前 world 已经更接近底层事实层，下一步不要再从 world 入手。

## 7. 建议的后续票据

### BE-BATTLE-RUNTIME-WILDCARD-36

目标：

- `BattleRuntimeStepRules.scala` 已完成第一轮：移除全域 wildcard imports，改成显式依赖 `BattlePickupRules`、`BattleSlowFieldRuntimeRules`、`BattlePlayerRuntimeRules`、`BattleHeldFireRuntimeRules`、`BattleProjectileRuntimeRules`、`BattleWeaponFireRules`。
- `BattleCommandApplicationRules.scala` 已完成第二轮：移除全域 wildcard imports，显式依赖 `BattleInputRules`、`BattleAggregateUpdateRules`、`BattleSkillCommandRules`。
- `BattleAggregateUpdateRules.scala` 已完成第三轮：只保留 `BattleAggregateState` 和 `BattlePlayerState`，不再依赖任何 database 子域。
- `BattleRuntimeFinalizationRules.scala` 已完成第四轮：显式依赖 `BattleRuntimeFinishRules` 和 `BattleReplayFrameRecorder`，不再依赖 abilities/actors/combat/world。
- `BattleEngine.scala` 已完成第五轮：移除全域 wildcard imports，显式依赖 `BattleSkillCommandRules.CommandApplication`、`BattleInputRules`、`BattleWeaponRules`、`BattleArenaCatalog`、`BattleInitialLayout`。
- `BattleEngine.scala` 是 runtime facade，仍真实依赖 abilities/actors/combat/world，所以 import matrix 数字未下降，但依赖目标已经从整个子域变成具体对象。
- `BattleRuntimeFinishRules.scala` 已完成第六轮：显式依赖 `BattlePlayerLifecycleRules` 和 `BattleReplayFrameRecorder`，不再依赖 abilities/combat/world。
- `BattleReplayFrameRecorder.scala` 已完成第七轮：只依赖 replay/player/projectile/pickup frame objects 和 `BattleHistoryCatalog`，不再依赖任何 database 子域。
- `BattleHistoryCatalog.scala` 已完成第八轮：只依赖 `DurationMillis`，不再依赖任何 database 子域。
- `BattleRetentionRules.scala` 已完成第九轮：只依赖 event/projectile terminal objects 和 `BattleHistoryCatalog`，不再依赖任何其他 database 子域。
- `BattleRuntimeCatalog.scala` 已完成第十轮：只依赖 `DurationMillis`，不再依赖任何 database 子域。
- `BattleTimeRules.scala` 已完成第十一轮：只依赖 `DurationMillis` / `EpochMillis`，不再依赖任何 database 子域。
- 后续继续选一个 runtime 文件，例如 `BattleEventFactory.scala`。
- 移除该文件的全域 wildcard imports。
- 只保留实际需要的显式 imports 或显式对象调用。
- 不改变运行时逻辑。

边界：

```text
services/battle/database/runtime/<one-runtime-file>.scala
```

验收：

- runtime 文件依赖更可审计。
- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。

## 8. 当前结论

当前四层方向已经基本成立；`world` 反向依赖已清理完成，session/projections 双向依赖也已消除。真正未完成的是：

- `abilities/actors/combat/runtime` 的旧规则网。

不建议下一步做大范围文件迁移。更安全的顺序是：

1. 逐个 runtime 文件移除 wildcard imports。
2. 先让 runtime orchestration 的实际依赖显式可审计。
3. 最后才考虑 abilities/actors/combat/runtime 的更大规则边界。
