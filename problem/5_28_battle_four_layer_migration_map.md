# services/battle 四层迁移映射表

更新时间：2026-05-28

这份文档把当前 `services/battle/microservices` 的文件映射到目标四层结构，用于后续分票实施。它不替代架构决策报告，只回答一个更具体的问题：如果继续迁移，每一类文件应该去哪、能不能现在搬、搬之前需要什么前置条件。

## 1. 当前剩余 microservices 文件分布

| domain | api | objects | database | services | 总数 | 迁移难度 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| `abilities` | 2 | 1 | 2 | 4 | 9 | 中 |
| `actors` | 1 | 1 | 2 | 4 | 8 | 中 |
| `combat` | 1 | 1 | 2 | 9 | 13 | 中高 |
| `projections` | 0 | 0 | 0 | 13 | 13 | 高 |
| `queue` | 0 | 0 | 0 | 13 | 13 | 高 |
| `results` | 0 | 0 | 0 | 0 | 0 | 已完成 |
| `runtime` | 1 | 1 | 2 | 10 | 14 | 高 |
| `session` | 0 | 0 | 0 | 11 | 11 | 高 |
| `world` | 1 | 1 | 2 | 5 | 9 | 中高 |

当前最容易迁移的是 `objects` 和 `database` 子目录，因为它们已经比较接近目标四层。最难迁移的是 `services`，因为四层目标没有 `services` 这个顶层归属，必须先决定 pure rules 是否放进 `objects`，或者是否允许新增 `rules/engine`。

## 2. 已经符合或接近目标的部分

### APIMessage

当前已有：

```text
services/battle/api/command/BattleCommandAPIMessage.scala
services/battle/api/queue/BattleQueueJoinAPIMessage.scala
services/battle/api/queue/BattleQueueLeaveAPIMessage.scala
services/battle/api/queue/BattleQueueStatusAPIMessage.scala
services/battle/api/results/BattleResultListAPIMessage.scala
services/battle/api/results/BattleResultRecordAPIMessage.scala
services/battle/api/room/BattleRoomHeartbeatAPIMessage.scala
services/battle/api/room/BattleRoomSnapshotAPIMessage.scala
services/battle/api/state/BattleStateReadAPIMessage.scala
```

判断：

- 文件位置正确。
- 文件命名正确。
- `results` 已经是 `APIWithTokenMessage + plan(connection)`。
- `queue/room/state/command` 仍是 `APIWithTokenContextMessage`，还没达到最终目标。

### apiTypes

当前已有：

```text
services/battle/objects/apiTypes/command/*
services/battle/objects/apiTypes/queue/*
services/battle/objects/apiTypes/results/*
services/battle/objects/apiTypes/room/*
services/battle/objects/apiTypes/shared/*
services/battle/objects/apiTypes/state/*
```

判断：

- 位置基本正确。
- response encoder 和 request decoder 已经集中在 `objects/apiTypes`。
- `BattleCommandRequestApiTypes.scala` 包含较复杂 decoder helper。如果你选择 C1，这个文件需要再拆；如果选择 C2，可以保留。

### results database

当前已有：

```text
services/battle/database/results/BattleResultTable.scala
services/battle/database/results/BattleResultTableInitializer.scala
```

判断：

- 位置正确。
- 是后续 Table/Initializer 模式的参考样板。

## 3. 低风险可直接迁移的映射

这些文件主要是 rule config ADT 或 Table/Initializer，迁移后只需要改 package/import，不改变业务行为。

### abilities

当前：

```text
microservices/abilities/objects/BattleAbilityRuleDefinitions.scala
microservices/abilities/database/BattleAbilityRuleTable.scala
microservices/abilities/database/BattleAbilityRuleTableInitializer.scala
```

目标：

```text
objects/abilities/BattleAbilityRuleDefinitions.scala
database/abilities/BattleAbilityRuleTable.scala
database/abilities/BattleAbilityRuleTableInitializer.scala
```

可以先迁。风险主要来自 import 替换。

暂不建议直接迁：

```text
microservices/abilities/api/BattlePickupRuleBook.scala
microservices/abilities/api/BattleSkillRuleBook.scala
microservices/abilities/services/BattlePickupRules.scala
microservices/abilities/services/BattleSkillCommandRules.scala
microservices/abilities/services/BattleSkillRules.scala
microservices/abilities/services/BattleSlowFieldRuntimeRules.scala
```

原因：`RuleBook` 不是 APIMessage，`services` 是 pure rules/runtime rules，需要先决定 A1/A2。

### actors

当前：

```text
microservices/actors/objects/BattleActorRuleDefinitions.scala
microservices/actors/database/BattleActorRuleTable.scala
microservices/actors/database/BattleActorRuleTableInitializer.scala
```

目标：

```text
objects/actors/BattleActorRuleDefinitions.scala
database/actors/BattleActorRuleTable.scala
database/actors/BattleActorRuleTableInitializer.scala
```

可以先迁。风险主要来自 `BattleBotRuleBook`、`BattleBotRules`、`BattlePlayerRuntimeRules` 等 import。

暂不建议直接迁：

```text
microservices/actors/api/BattleBotRuleBook.scala
microservices/actors/services/*
```

原因：`RuleBook` 是全局缓存，不是 APIMessage；`services` 是 bot/player runtime rules，需要 A1/A2 决策。

### combat

当前：

```text
microservices/combat/objects/BattleCombatRuleDefinitions.scala
microservices/combat/database/BattleCombatRuleTable.scala
microservices/combat/database/BattleCombatRuleTableInitializer.scala
```

目标：

```text
objects/combat/BattleCombatRuleDefinitions.scala
database/combat/BattleCombatRuleTable.scala
database/combat/BattleCombatRuleTableInitializer.scala
```

可以先迁，但风险比 abilities/actors 略高，因为 combat 被 runtime、actors、abilities 高频引用。

暂不建议直接迁：

```text
microservices/combat/api/BattleCombatRuleBook.scala
microservices/combat/services/*
```

原因：weapon/projectile rules 是核心运行时规则，不应在没有 A1/A2 决策时硬塞入 `objects`。

### runtime

当前：

```text
microservices/runtime/objects/BattleRuntimeRuleDefinitions.scala
microservices/runtime/database/BattleRuntimeRuleTable.scala
microservices/runtime/database/BattleRuntimeRuleTableInitializer.scala
```

目标：

```text
objects/runtime/BattleRuntimeRuleDefinitions.scala
database/runtime/BattleRuntimeRuleTable.scala
database/runtime/BattleRuntimeRuleTableInitializer.scala
```

可以先迁，但要注意 runtime rule definitions 被 `BattleRuntimeRuleBook`、`BattleEngine`、`BattleSessionStateFactory` 引用。

暂不建议直接迁：

```text
microservices/runtime/api/BattleRuntimeRuleBook.scala
microservices/runtime/services/*
```

原因：`BattleEngine` 是 orchestrator，迁移它会牵动 world/actors/combat/abilities。

### world

当前：

```text
microservices/world/objects/BattleWorldRuleDefinitions.scala
microservices/world/database/BattleWorldRuleTable.scala
microservices/world/database/BattleWorldRuleTableInitializer.scala
```

目标：

```text
objects/world/BattleWorldRuleDefinitions.scala
database/world/BattleWorldRuleTable.scala
database/world/BattleWorldRuleTableInitializer.scala
```

可以先迁，但 `BattleWorldRuleTable` 较厚，里面承担 map JSON decode 和 domain construction。短期可以作为 database boundary 保留，长期建议瘦身。

暂不建议直接迁：

```text
microservices/world/api/BattleWorldRuleBook.scala
microservices/world/services/*
```

原因：world services 包含 geometry/collision/motion/spawn 等 pure rules，需要 A1/A2 决策。

## 4. 高风险必须等决策的映射

### queue

当前全部剩余文件都在：

```text
microservices/queue/services/*
```

目标不应该是简单搬目录，而是重构为：

```text
objects/queue/*
database/queue/BattleQueueTable.scala
database/queue/BattleQueueTableInitializer.scala
api/queue/BattleQueueJoinAPIMessage.scala
api/queue/BattleQueueStatusAPIMessage.scala
api/queue/BattleQueueLeaveAPIMessage.scala
api/room/BattleRoomSnapshotAPIMessage.scala
api/room/BattleRoomHeartbeatAPIMessage.scala
```

不能直接机械搬的原因：

- 当前 `BattleQueueService` 使用 `AtomicReference[QueueRuntimeState]`。
- 当前 APIMessage 依赖 `BattleQueueService` context。
- 如果要变成 `plan(connection)`，必须先有 queue/room PostgreSQL schema。

建议前置票据：

```text
BE-BATTLE-QUEUE-SCHEMA-01
```

只设计 `queue ticket / queue room / room participant / heartbeat` 表，不先替换运行路径。

### session

当前全部剩余文件都在：

```text
microservices/session/services/*
```

目标不应该是简单搬目录，而是重构为：

```text
objects/session/*
objects/state/*
database/session/*
database/state/*
database/command/*
api/state/BattleStateReadAPIMessage.scala
api/command/BattleCommandAPIMessage.scala
```

不能直接机械搬的原因：

- 当前 `InMemoryBattleStateService` 使用 `private var battles`。
- 当前 state/command APIMessage 依赖 `BattleStateService` context。
- 如果要变成 `plan(connection)`，必须先决定 authoritative state 如何存：normalized tables 还是 serialized aggregate snapshot。

建议前置票据：

```text
BE-BATTLE-SESSION-STORAGE-DESIGN-01
```

先决定 `StoredBattle`、ownership、pending step、finish projection status 的存储模型。

### projections

当前全部剩余文件都在：

```text
microservices/projections/services/*
```

目标归属依赖 A1/A2：

如果选 A1：

```text
objects/projections/*
```

如果选 A2：

```text
rules/projections/*
```

不能直接机械搬的原因：

- projection 会写 result/replay/mail。
- 它是跨域 side-effect orchestration，不是普通 pure object。
- 需要通过 port/API plan 保持 battle 不直接依赖外部 repository 细节。

## 5. RuleBook 的目标处理

当前 RuleBook 文件：

```text
microservices/abilities/api/BattlePickupRuleBook.scala
microservices/abilities/api/BattleSkillRuleBook.scala
microservices/actors/api/BattleBotRuleBook.scala
microservices/combat/api/BattleCombatRuleBook.scala
microservices/runtime/api/BattleRuntimeRuleBook.scala
microservices/world/api/BattleWorldRuleBook.scala
```

问题：

- 它们在 `api` 目录，但不是 APIMessage。
- 它们使用 `AtomicReference` 做全局缓存。
- 它们让 `api` 语义污染：API 目录本应只放 `XXXAPIMessage.scala`。

目标处理：

如果选 A1：

```text
objects/<domain>/<Domain>RuleBook.scala
```

但这会让 `objects` 承担全局缓存，不理想。

如果选 A2：

```text
rules/<domain>/<Domain>RuleBook.scala
```

更合理。

如果同时选 B1：

RuleBook 应尽量被 `database/<domain>/*Table.load(connection)` 替代，API/runtime 每次从 connection 读或通过显式 cache port 读，不保留全局 `AtomicReference`。

## 6. 推荐分批迁移票据

### Ticket 1: rule config objects/database 先回四层

ID：

```text
BE-BATTLE-RULECONFIG-FOUR-LAYER-01
```

范围：

```text
abilities/objects + abilities/database
actors/objects + actors/database
combat/objects + combat/database
runtime/objects + runtime/database
world/objects + world/database
```

不做：

- 不动 `services` pure rules。
- 不动 `RuleBook`。
- 不动 queue/session。
- 不改 API JSON。

价值：

- 立刻减少 `microservices` 中最不该留的 `objects/database` 嵌套。
- 风险可控。

验证：

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
rg "services\.battle\.microservices\.(abilities|actors|combat|runtime|world)\.(objects|database)" backend/src/main/scala -n
```

### Ticket 2: RuleBook 位置决策后处理

ID：

```text
BE-BATTLE-RULEBOOK-PLACEMENT-02
```

前置：

- 必须决定 A1 或 A2。

范围：

```text
BattlePickupRuleBook
BattleSkillRuleBook
BattleBotRuleBook
BattleCombatRuleBook
BattleRuntimeRuleBook
BattleWorldRuleBook
```

目标：

- 从 `microservices/<domain>/api` 移出。
- `api` 目录只剩 `XXXAPIMessage.scala`。

### Ticket 3: queue PostgreSQL schema

ID：

```text
BE-BATTLE-QUEUE-SCHEMA-03
```

前置：

- 必须决定 B1 或 B2。

范围：

```text
database/queue
database/room
objects/queue
objects/apiTypes/queue
objects/apiTypes/room
```

目标：

- 新建 queue/room Table 和 Initializer。
- 暂时不替换所有运行路径，除非选择 B1。

### Ticket 4: queue APIMessage connection-backed

ID：

```text
BE-BATTLE-QUEUE-API-CONNECTION-04
```

前置：

- Ticket 3 完成。

目标：

- `BattleQueueJoinAPIMessage`
- `BattleQueueStatusAPIMessage`
- `BattleQueueLeaveAPIMessage`
- `BattleRoomSnapshotAPIMessage`
- `BattleRoomHeartbeatAPIMessage`

全部从 `APIWithTokenContextMessage` 改为 `APIWithTokenMessage`。

验收：

```text
rg "BattleQueueService|BattleAPIRuntimeContext" backend/src/main/scala/services/battle/api backend/src/main/scala/services/battle/routes -n
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
```

### Ticket 5: session/state storage design and implementation

ID：

```text
BE-BATTLE-SESSION-STATE-STORAGE-05
```

目标：

- 让 `BattleStateReadAPIMessage` 和 `BattleCommandAPIMessage` 不再依赖 `BattleStateService` context。
- 迁移 `StoredBattle` 到 PostgreSQL。

这是高风险票据，不能和 queue 同票做。

### Ticket 6: services pure rules final placement

ID：

```text
BE-BATTLE-PURE-RULES-PLACEMENT-06
```

前置：

- 必须决定 A1/A2。

目标：

- 消灭 `microservices/<domain>/services`。
- 保持 runtime orchestrator 单向调用，不允许双向依赖。

### Ticket 7: delete microservices shell

ID：

```text
BE-BATTLE-MICROSERVICES-DELETE-07
```

前置：

- `microservices` 下无 Scala 文件。
- compile 和 contract tests 通过。

目标：

- 删除空目录。
- `rg "services\.battle\.microservices" backend/src/main/scala backend/src/test/scala -n` 无结果。

## 7. 完成标准

最终完成后必须能证明：

- `services/battle/microservices` 不再有 Scala 文件。
- `services/battle/api` 只包含 `XXXAPIMessage.scala`。
- `services/battle/objects` 是 ADT/value object/state/apiTypes 单一事实来源。
- `services/battle/routes/BattleRoutes.scala` 暴露纯 `val apiMessages: List[RegisteredAPIMessage]`，不依赖 runtime context。
- `services/battle/database` 按 domain 拥有 `Table` 和 `TableInitializer`。
- queue/room/state/command/results 全部是 `APIWithTokenMessage` 或等价的 connection-backed APIMessage。
- `objects/apiTypes` 不重复声明 core domain type。
- `sbt compile` 通过。
- `sbt "Test/runMain route.contract.BackendContractTestRunner"` 通过。

## 8. 当前建议

如果你还没有做 A/B/C/D/E 决策，我建议下一步不要直接改源码。

最安全的默认路线是：

```text
A1 + B2 + C2 + D1 + E1
```

然后先做：

```text
BE-BATTLE-RULECONFIG-FOUR-LAYER-01
```

这一步只迁 `objects/database` 的 rule config 和 table，不碰 queue/session runtime state。

## 9. 2026-05-28 执行状态

已完成：

```text
BE-BATTLE-RULECONFIG-FOUR-LAYER-01
```

本票已把 `abilities / actors / combat / runtime / world` 这五个业务域的 rule config ADT 从
`services/battle/microservices/<domain>/objects` 迁到 `services/battle/objects/<domain>`，
并把对应 `RuleTable` / `RuleTableInitializer` 从
`services/battle/microservices/<domain>/database` 迁到 `services/battle/database/<domain>`。

当前 `microservices` 剩余 Scala 文件数：

| 子域 | 剩余 Scala 文件数 |
| --- | ---: |
| `abilities` | 6 |
| `actors` | 5 |
| `combat` | 10 |
| `projections` | 13 |
| `queue` | 13 |
| `runtime` | 11 |
| `session` | 11 |
| `world` | 6 |

已验证：

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
```

## 23. 2026-05-28 projectile factory pure rule 迁移状态

已完成：

```text
BE-BATTLE-COMBAT-PROJECTILE-FACTORY-PURE-RULE-15
```

本票迁移并纯化：

```text
services/battle/microservices/combat/services/BattleProjectileFactoryRules.scala
  -> services/battle/objects/combat/BattleProjectileFactoryRules.scala
```

关键变化：

- `BattleProjectileFactoryRules` 不再直接依赖 `BattleArenaCatalog`。
- `BattleProjectileFactoryRules` 不再直接依赖 `BattleMotionRules`。
- projectile birth offset 和 movement normalize 函数由 `BattleWeaponFireRules` 显式注入。

当前 `microservices` 剩余 Scala 文件数：

| 子域 | 剩余 Scala 文件数 |
| --- | ---: |
| `abilities` | 0 |
| `actors` | 2 |
| `combat` | 5 |
| `projections` | 13 |
| `queue` | 13 |
| `runtime` | 5 |
| `session` | 11 |
| `world` | 4 |

已验证：

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
```

## 22. 2026-05-28 projectile motion pure rule 迁移状态

已完成：

```text
BE-BATTLE-COMBAT-PROJECTILE-MOTION-PURE-RULE-14
```

本票迁移并纯化：

```text
services/battle/microservices/combat/services/BattleProjectileMotionRules.scala
  -> services/battle/objects/combat/BattleProjectileMotionRules.scala
```

关键变化：

- `BattleProjectileMotionRules` 不再直接依赖 `BattleArenaCatalog`。
- `BattleProjectileMotionRules` 不再直接依赖 `BattleArenaCollision`。
- `BattleProjectileMotionRules` 不再直接依赖 `BattleMotionRules`。
- projectile runtime 显式注入 movement normalize 和 projectile block 查询函数。

当前 `microservices` 剩余 Scala 文件数：

| 子域 | 剩余 Scala 文件数 |
| --- | ---: |
| `abilities` | 0 |
| `actors` | 2 |
| `combat` | 6 |
| `projections` | 13 |
| `queue` | 13 |
| `runtime` | 5 |
| `session` | 11 |
| `world` | 4 |

已验证：

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
```

## 21. 2026-05-28 projectile targeting pure rule 迁移状态

已完成：

```text
BE-BATTLE-COMBAT-PROJECTILE-TARGETING-PURE-RULE-13
```

本票迁移并纯化：

```text
services/battle/microservices/combat/services/BattleProjectileTargetingRules.scala
  -> services/battle/objects/combat/BattleProjectileTargetingRules.scala
```

关键变化：

- `BattleProjectileTargetingRules` 不再直接依赖 `BattleArenaCatalog`。
- `BattleProjectileTargetingRules` 不再直接依赖 `BattleArenaCollision`。
- 命中半径 `hitRadius` 和线段圆碰撞函数 `segmentCircleHitT` 由 projectile runtime 显式注入。

当前 `microservices` 剩余 Scala 文件数：

| 子域 | 剩余 Scala 文件数 |
| --- | ---: |
| `abilities` | 0 |
| `actors` | 2 |
| `combat` | 7 |
| `projections` | 13 |
| `queue` | 13 |
| `runtime` | 5 |
| `session` | 11 |
| `world` | 4 |

已验证：

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
```

## 20. 2026-05-28 projectile terminal pure rule 迁移状态

已完成：

```text
BE-BATTLE-COMBAT-PROJECTILE-TERMINAL-PURE-RULE-12
```

本票迁移：

```text
services/battle/microservices/combat/services/BattleProjectileTerminalRules.scala
  -> services/battle/objects/combat/BattleProjectileTerminalRules.scala
```

理由：

- `BattleProjectileTerminalRules` 只构造 projectile terminal state 和追加终止记录。
- retention 数量已在上一票改为显式参数。
- 文件不再直接依赖 database、RuleBook、IO 或 mutable state。

当前 `microservices` 剩余 Scala 文件数：

| 子域 | 剩余 Scala 文件数 |
| --- | ---: |
| `abilities` | 0 |
| `actors` | 2 |
| `combat` | 8 |
| `projections` | 13 |
| `queue` | 13 |
| `runtime` | 5 |
| `session` | 11 |
| `world` | 4 |

已验证：

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
```

## 19. 2026-05-28 input pure rule 迁移状态

已完成：

```text
BE-BATTLE-ACTOR-INPUT-PURE-RULE-11
```

本票迁移并纯化：

```text
services/battle/microservices/actors/services/BattleInputRules.scala
  -> services/battle/objects/actors/BattleInputRules.scala
```

关键变化：

- `BattleInputRules` 不再直接依赖 `BattleMotionRules`。
- `BattleInputRules` 不再直接依赖 `BattleWeaponRules`。
- 新增 `BattleInputEnvironment`，由 runtime command application 层显式注入 movement normalize 和 weapon switch 函数。
- `normalizeAim` 与 `lastClientCommandSeq` 保持纯对象规则。

当前 `microservices` 剩余 Scala 文件数：

| 子域 | 剩余 Scala 文件数 |
| --- | ---: |
| `abilities` | 0 |
| `actors` | 2 |
| `combat` | 9 |
| `projections` | 13 |
| `queue` | 13 |
| `runtime` | 5 |
| `session` | 11 |
| `world` | 4 |

已验证：

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
```

## 18. 2026-05-28 player lifecycle pure rule 迁移状态

已完成：

```text
BE-BATTLE-ACTOR-LIFECYCLE-PURE-RULE-10
```

本票迁移并纯化：

```text
services/battle/microservices/actors/services/BattlePlayerLifecycleRules.scala
  -> services/battle/objects/actors/BattlePlayerLifecycleRules.scala
```

关键变化：

- `BattlePlayerLifecycleRules` 不再依赖 `BattleArenaCatalog.ZeroVector`。
- 死亡/结束玩家运行态清理直接使用 `BattleVector2(0.0, 0.0)`。
- `winnerFor` 保持纯玩家集合判断。

当前 `microservices` 剩余 Scala 文件数：

| 子域 | 剩余 Scala 文件数 |
| --- | ---: |
| `abilities` | 0 |
| `actors` | 3 |
| `combat` | 9 |
| `projections` | 13 |
| `queue` | 13 |
| `runtime` | 5 |
| `session` | 11 |
| `world` | 4 |

已验证：

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
```

## 17. 2026-05-28 replay frame pure rule 迁移状态

已完成：

```text
BE-BATTLE-RUNTIME-REPLAYFRAME-PURE-RULE-09
```

本票迁移并纯化：

```text
services/battle/microservices/runtime/services/BattleReplayFrameRecorder.scala
  -> services/battle/objects/runtime/BattleReplayFrameRecorder.scala
```

关键变化：

- `BattleReplayFrameRecorder` 不再直接读取 `BattleRuntimeRuleBook`。
- `updateFrames` 显式接收 `replayFrameSampleInterval` 和 `retainedReplayFrameCount`。
- `appendFrame` 显式接收 `retainedReplayFrameCount`。
- `BattleRuntimeFinalizationRules` 和 `BattleRuntimeFinishRules` 负责从 `BattleRuntimeRuleBook.history` 注入配置。

当前 `microservices` 剩余 Scala 文件数：

| 子域 | 剩余 Scala 文件数 |
| --- | ---: |
| `abilities` | 0 |
| `actors` | 4 |
| `combat` | 9 |
| `projections` | 13 |
| `queue` | 13 |
| `runtime` | 5 |
| `session` | 11 |
| `world` | 4 |

已验证：

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
```

## 16. 2026-05-28 retention pure rule 迁移状态

已完成：

```text
BE-BATTLE-RUNTIME-RETENTION-PURE-RULE-08
```

本票迁移并纯化：

```text
services/battle/microservices/runtime/services/BattleRetentionRules.scala
  -> services/battle/objects/runtime/BattleRetentionRules.scala
```

关键变化：

- `BattleRetentionRules` 不再直接读取 `BattleRuntimeRuleBook`。
- `retainRecentProjectileTerminals` 显式接收 `BattleHistoryCount`。
- `retainRecentEvents` 显式接收 `BattleHistoryCount`。
- projectile runtime 负责从 `BattleRuntimeRuleBook.history` 读取保留数量并注入 combat 规则。

这样 retention 规则只保留纯裁剪逻辑，不再把 database rule cache 依赖带入 `objects/runtime`。

当前 `microservices` 剩余 Scala 文件数：

| 子域 | 剩余 Scala 文件数 |
| --- | ---: |
| `abilities` | 0 |
| `actors` | 4 |
| `combat` | 9 |
| `projections` | 13 |
| `queue` | 13 |
| `runtime` | 6 |
| `session` | 11 |
| `world` | 4 |

已验证：

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
```

## 15. 2026-05-28 skill command pure rule 迁移状态

已完成：

```text
BE-BATTLE-ABILITY-SKILLCOMMAND-PURE-RULE-07
```

本票迁移并纯化：

```text
services/battle/microservices/abilities/services/BattleSkillCommandRules.scala
  -> services/battle/objects/abilities/BattleSkillCommandRules.scala
```

关键变化：

- `BattleSkillCommandRules` 不再直接读取 `BattleSkillRuleBook`。
- `BattleSkillCommandRules` 不再直接依赖 `BattleArenaCatalog`、`BattleArenaCollision`、`BattleMotionRules`。
- 新增 `BattleSkillCommandEnvironment`，由 runtime 层显式传入 skill rule set、玩家碰撞半径、世界边界判断、障碍碰撞判断、dash 位移计算函数。
- `BattleCommandApplicationRules` 负责把数据库 rule cache 和 world service 适配成这个纯规则环境。

这样 abilities 子域在 `microservices` 下已经没有 Scala 文件。

当前 `microservices` 剩余 Scala 文件数：

| 子域 | 剩余 Scala 文件数 |
| --- | ---: |
| `abilities` | 0 |
| `actors` | 4 |
| `combat` | 9 |
| `projections` | 13 |
| `queue` | 13 |
| `runtime` | 7 |
| `session` | 11 |
| `world` | 4 |

已验证：

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
```

## 14. 2026-05-28 pickup pure rule 迁移状态

已完成：

```text
BE-BATTLE-ABILITY-PICKUP-PURE-RULE-06
```

本票迁移并纯化：

```text
services/battle/microservices/abilities/services/BattlePickupRules.scala
  -> services/battle/objects/abilities/BattlePickupRules.scala
```

关键变化：

- `BattlePickupRules` 不再直接读取 `BattlePickupRuleBook`。
- `BattlePickupRules` 不再直接调用 `BattleWeaponRules`。
- `BattlePickupRules` 不再直接调用 `BattleRetentionRules`。
- pickup 规则需要的 `BattlePickupRuleConfig`、事件保留数量、武器拾取处理函数由 runtime step 显式注入。

这样 `objects/abilities/BattlePickupRules` 只依赖 battle domain objects 和纯工具函数，不形成
`objects -> database` 或 `objects -> microservices` 的反向依赖。

当前 `microservices` 剩余 Scala 文件数：

| 子域 | 剩余 Scala 文件数 |
| --- | ---: |
| `abilities` | 1 |
| `actors` | 4 |
| `combat` | 9 |
| `projections` | 13 |
| `queue` | 13 |
| `runtime` | 7 |
| `session` | 11 |
| `world` | 4 |

已验证：

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
```

## 12. 2026-05-28 pure leaf rules 迁移状态

已完成：

```text
BE-BATTLE-PURE-LEAF-RULES-04
```

本票迁移以下无数据库、无 IO、无锁、无运行态缓存依赖的纯规则：

```text
services/battle/microservices/abilities/services/BattleSkillRules.scala
  -> services/battle/objects/abilities/BattleSkillRules.scala

services/battle/microservices/runtime/services/BattleTimeRules.scala
  -> services/battle/objects/runtime/BattleTimeRules.scala

services/battle/microservices/runtime/services/BattleAggregateUpdateRules.scala
  -> services/battle/objects/runtime/BattleAggregateUpdateRules.scala

services/battle/microservices/runtime/services/BattleEventFactory.scala
  -> services/battle/objects/runtime/BattleEventFactory.scala
```

当前 `microservices` 剩余 Scala 文件数：

| 子域 | 剩余 Scala 文件数 |
| --- | ---: |
| `abilities` | 3 |
| `actors` | 4 |
| `combat` | 9 |
| `projections` | 13 |
| `queue` | 13 |
| `runtime` | 7 |
| `session` | 11 |
| `world` | 4 |

已验证：

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
```

剩余 `microservices/services` 里仍混有：

- 依赖 `RuleBook` 的配置读取规则。
- queue/session 内存状态和锁。
- projections 的 PostgreSQL 写入和外部 artifact 写入端口。
- runtime orchestrator 对 actors/combat/abilities/world 的横向编排。

## 13. 2026-05-28 slow field pure rule 迁移状态

已完成：

```text
BE-BATTLE-ABILITY-SLOWFIELD-PURE-RULE-05
```

本票迁移：

```text
services/battle/microservices/abilities/services/BattleSlowFieldRuntimeRules.scala
  -> services/battle/objects/abilities/BattleSlowFieldRuntimeRules.scala
```

理由：

- `BattleSlowFieldRuntimeRules` 只根据 `BattleAggregateState` 和 `deltaMs` 递减 slow field TTL。
- 它只依赖 `BattleTimeRules` 和 battle domain objects，不依赖数据库、RuleBook、IO、锁或 mutable state。

当前 `microservices` 剩余 Scala 文件数：

| 子域 | 剩余 Scala 文件数 |
| --- | ---: |
| `abilities` | 2 |
| `actors` | 4 |
| `combat` | 9 |
| `projections` | 13 |
| `queue` | 13 |
| `runtime` | 7 |
| `session` | 11 |
| `world` | 4 |

已验证：

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
```

下一步仍不能直接删除 `microservices`，因为其中还保留 `api` / `services` 层代码。下一票应处理
`RuleBook` 位置或 queue/session 的 PostgreSQL 化，具体取决于 A/B/C/D/E 决策。

## 10. 2026-05-28 RuleBook 迁移状态

已完成：

```text
BE-BATTLE-RULEBOOK-PLACEMENT-02
```

本票已把 `BattlePickupRuleBook`、`BattleSkillRuleBook`、`BattleBotRuleBook`、`BattleCombatRuleBook`、
`BattleRuntimeRuleBook`、`BattleWorldRuleBook` 从 `services/battle/microservices/<domain>/api`
迁到 `services/battle/database/<domain>`。

理由：

- 这些文件不是 `XXXAPIMessage.scala`，继续放在 `api` 会违反 “api 只放 APIMessage” 的目标。
- 这些文件当前是 PostgreSQL 规则加载后的进程内 rule cache，含 `AtomicReference`，不适合放进纯 ADT 的 `objects`。
- 迁到 `database` 是过渡状态：它们表达的是由数据库初始化出来的规则读取边界，不是 HTTP/API 入口。

当前 `microservices` 剩余 Scala 文件数：

| 子域 | 剩余 Scala 文件数 |
| --- | ---: |
| `abilities` | 4 |
| `actors` | 4 |
| `combat` | 9 |
| `projections` | 13 |
| `queue` | 13 |
| `runtime` | 10 |
| `session` | 11 |
| `world` | 5 |

已验证：

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
```

剩余问题：

- `microservices/<domain>/services` 仍存在大量 pure/runtime rules。
- queue/session 仍依赖内存状态和 `BattleAPIRuntimeContext`。
- `database/<domain>/Battle*RuleBook.scala` 仍是过渡性缓存，并不等于最终“只有 Table/Initializer”的最严格数据库形态。

## 11. 2026-05-28 纯 world 几何规则迁移状态

已完成：

```text
BE-BATTLE-WORLD-PURE-GEOMETRY-03
```

本票只迁移 `BattleGeometry`：

```text
services/battle/microservices/world/services/BattleGeometry.scala
  -> services/battle/objects/world/BattleGeometry.scala
```

理由：

- `BattleGeometry` 只包含向量加减、缩放、点积、距离、长度、夹取等纯函数。
- 它不依赖 PostgreSQL、RuleBook、queue/session runtime state，也没有隐藏副作用。
- `BattleArenaCollision`、`BattleInitialLayout`、`BattleMotionRules` 暂时没有迁移，因为它们间接依赖 `BattleArenaCatalog`，而 catalog 当前读取数据库 rule cache；直接迁入 `objects` 会形成 `objects -> database` 的反向依赖。

当前 `microservices` 剩余 Scala 文件数：

| 子域 | 剩余 Scala 文件数 |
| --- | ---: |
| `abilities` | 4 |
| `actors` | 4 |
| `combat` | 9 |
| `projections` | 13 |
| `queue` | 13 |
| `runtime` | 10 |
| `session` | 11 |
| `world` | 4 |

已验证：

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
```
## 23. 2026-05-28 projectile factory pure rule 迁移状态

已完成：

```text
BE-BATTLE-COMBAT-PROJECTILE-FACTORY-PURE-RULE-15
```

本票迁移：

```text
services/battle/microservices/combat/services/BattleProjectileFactoryRules.scala
  -> services/battle/objects/combat/BattleProjectileFactoryRules.scala
```

理由：
- `BattleProjectileFactoryRules` 只负责根据射手、武器 projectile 定义、朝向和 command seq 生成不可变 projectile 状态。
- 原先它隐式依赖 `BattleArenaCatalog` 和 `BattleMotionRules`；现在通过参数注入 `projectileBirthOffset` 与 `normalizeMovement`，避免 `objects/combat` 反向依赖 world/runtime service。
- `BattleWeaponFireRules` 仍留在 `microservices/combat/services`，因为它还承担 weapon rule book、heat、cooldown 等运行期编排。

当前 `microservices` 剩余 Scala 文件数：

| 子域 | 剩余 Scala 文件数 |
| --- | ---: |
| `actors` | 2 |
| `combat` | 5 |
| `projections` | 13 |
| `queue` | 13 |
| `runtime` | 5 |
| `session` | 11 |
| `world` | 4 |

已验证：

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
```
