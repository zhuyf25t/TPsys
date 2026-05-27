# Battle Service 重构路线合理性报告

日期：2026-05-26

## 本轮结论

你提出的方向有价值，但不能直接照搬到当前代码上执行。当前 battle 的真实问题是：API 边界、游戏规则、运行时状态、持久化和 HTTP 路由混在不同历史结构里，导致 route 层偏厚、API contract 没有完全使用已有 ADT/value object、`apiTypes` 变成 DTO/codec/render 混合层。

用户最新补充的 `api/BattleQueueAPIMessagePlanner` 应理解为代码侧 API 文件/类路径，不能直接等同于 HTTP URL。当前 HTTP 入口是 `POST /api/{apiName}`，而 `apiNameFromClassName` 会把类名转成小写并去掉 `APIMessage` 后缀；因此如果类名叫 `BattleQueueAPIMessagePlanner`，默认推导出的 URL 段会是 `battlequeueapimessageplanner`，不是 `/api/BattleQueueAPIMessagePlanner`。后续需要单独确认“代码路径”与“前端请求 URL”是否绑定。

目标结构可以向 `battle/api`、`battle/objects`、`battle/routes`、`battle/database` 收敛，但必须先做三个决策：

1. 是否把全局 `system/api` 改成 sample 风格的 `plan(connection: Connection): IO[Response]`。
2. 是否让 HTTP URL 继续保持当前 lowercase contract，还是跟随 `BattleQueueAPIMessagePlanner` 这类代码名。
3. 是否允许保留纯游戏规则层，例如 `battle/rules` 或继续保留 `battle/engine`，而不是把碰撞、子弹、bot、tick 推进全部塞进 `objects` 或 `api`。

如果不先确认这三点，直接删除 `application/engine/persistence/ports` 会把可运行后端变成大面积不可编译状态。

## 当前 worktree 状态

当前工作区不是干净状态，已有未提交的 APIPlanner 迁移：

```text
M  backend/src/main/scala/route/battle/BattleHttp4sRoutes.scala
D  backend/src/main/scala/services/battle/api/BattleQueueApiTypes.scala
D  backend/src/main/scala/services/battle/routes/BattleAPIMessageRegistry.scala
D  backend/src/main/scala/services/battle/routes/BattleQueueAPIMessage.scala
D  backend/src/main/scala/services/battle/routes/BattleRoomAPIMessage.scala
?? backend/src/main/scala/services/battle/api/queue/
?? backend/src/main/scala/services/battle/api/room/
?? backend/src/main/scala/services/battle/objects/apiTypes/
?? backend/src/main/scala/services/battle/routes/BattleRoutes.scala
?? problem/
```

这批改动已经通过：

```powershell
npm run backend:compile
npm run backend:test-contracts
```

但这批改动是 `APIPlanner` 路线，不是你现在重新明确的 `final case class XXXAPIMessage extends APIWithTokenMessage[XXXResponse]` 路线。因此后续如果选择 APIMessage 路线，需要把这批 planner 结构再调整，不能继续在 planner 方向上扩大迁移。

## 当前系统 API 机制

TPsys 当前 `system/api/APIMessage.scala` 是：

```scala
trait APIMessage[Response]:
  def plan: IO[Response]

trait APIWithTokenMessage[Response] extends APIMessage[Response]

final case class RegisteredAPIMessage(
  apiName: String,
  requiresUserToken: Boolean,
  planJson: Json => IO[Json]
)
```

sample 的机制是：

```scala
trait APIMessage[Response]:
  def plan(connection: Connection): IO[Response]

final case class RegisteredAPIMessage(
  apiName: String,
  requiresUserToken: Boolean,
  planJson: (Json, Connection) => IO[Json]
)
```

这说明：你要求的 `override def plan(connection: Connection): IO[Response]` 不是 battle 局部问题，而是全后端 API 基础设施问题。必须先改 `system/api`、`APIMessageRouter`、connection 获取方式、所有已迁移 APIMessage 的注册方式，否则 battle 单独写 `plan(connection)` 无法接入现有 router。

## 当前 battle 模块职责

### `services/battle/api`

当前职责：

- 存放 `BattleCommandApiTypes.scala`、`BattleStateApiTypes.scala`、`BattleResultApiTypes.scala` 等 API DTO/codec/render 文件。
- 新增未提交的 `api/queue/*APIPlanner.scala` 和 `api/room/*APIPlanner.scala`。
- 使用 Circe 做 request decode 和 response encode。

问题：

- `api` 目录没有统一使用 `XXXAPIMessage.scala`。
- 一部分 API 仍在 `routes` 下，一部分迁到了 `api/queue`、`api/room`，结构处于中间态。
- 老 `apiTypes` 文件里仍有大量 wire-level `String`，没有始终复用 `objects` 里的 `BattleId`、`PlayerId`、`TicketId`、`RoomId`、`WeaponKind` 等类型。

### `services/battle/application`

当前职责：

- `queue`：排队、房间等待、join/leave、heartbeat、room snapshot、queue ticket。
- `session`：battle state 读写、command accept、stored battle 初始化和推进。
- `results`：战斗结算、replay、result record、mail/replay projection。

优点：

- 有明确 use-case service，例如 `BattleQueueService`、`BattleStateService`、`BattleResultService`。
- 已有业务错误 ADT，例如 `BattleStateReadError`、`BattleCommandSubmitError`、`BattleQueueStatusError`。

问题：

- in-memory service 使用 `var` 和 `synchronized` 管理运行时状态。这不是纯 domain，但作为 runtime adapter 可以接受，需要明确归属。
- 如果直接删 `application`，这些 orchestration 会被挤进 APIMessage，导致 API 文件变成 god object。

结论：

- 不建议“删除 application 逻辑后直接塞进 api”。
- 如果坚持目标目录只有 `api/objects/routes/database`，应把 application 的 use-case orchestration 分散到各业务 APIMessage 的 private helper 和必要的 `database`/runtime adapter 中，但要小心 APIMessage 变厚。

### `services/battle/engine`

当前职责：

- `world`：地图、碰撞、出生点、移动空间。
- `actors`：玩家、bot、输入、生命周期。
- `combat`：武器、子弹、命中、伤害、终止事件。
- `abilities`：技能、拾取物、slow field。
- `runtime`：tick 推进、事件、finalization、retention。

优点：

- 大部分是纯游戏规则，接近 `old state + input -> new state/result`。
- `BattleEngine` 作为 facade 被 session 调用，不直接依赖 HTTP 或 DB。

风险：

- 这是 battle 最应该保留的纯规则层。直接删除 `engine` 会导致 tick、碰撞、bot、子弹等规则无处安放。

建议：

- 最合理是保留 `battle/engine` 或改名为 `battle/rules`。
- 如果你坚持根目录只保留四类目录，建议放入 `battle/objects/rules`，但要明确它们是纯函数规则，不是普通数据对象。

### `services/battle/objects`

当前职责：

- value object：`BattleId`、`PlayerId`、`TicketId`、`RoomId`、`DurationMillis`、`BattleTick` 等。
- enum/ADT：`BattleMode`、`BattlePhase`、`WeaponKind`、`ProjectileKind`、`SkillKind`、`PickupKind` 等。
- aggregate state：player、projectile、pickup、queue、result、replay state。
- 新增未提交的 `objects/apiTypes` 存放 queue/room request/response codec。

优点：

- 已经具备较好的类型安全基础。
- 应成为 API contract 的单一事实来源。

问题：

- `apiTypes` 中仍有 response 字段过早转成 `String` 的情况。
- 当前目标说 `battle/object`，但 Scala 里 `object` 是关键字，不建议作为 package 名。应使用现有 `objects`。

### `services/battle/persistence`

当前职责：

- battle result repository。
- file/in-memory/postgres repository。
- postgres schema 初始化。
- battle result file JSON parser/renderer。

问题：

- 命名不符合你提出的 `database`。
- sample 使用 `tables/{entity}/Table.scala` 和 `TableInitializer.scala`，当前是 repository/schema 风格。
- queue/session runtime 当前不是 table-backed，而是 in-memory。

建议：

- 可以逐步迁移为 `services/battle/database/result/BattleResultTable.scala`、`BattleResultTableInitializer.scala`。
- 不要把 game rule 放进 table。
- legacy file JSON parser/renderer 应作为 database compatibility boundary 保留，不应污染 APIMessage。

### `services/battle/ports`

当前职责：

- `BattleMailPublisherPort`
- `BattleReplayWriterPort`

它们用于 battle finish projection 向 mail/replay 模块输出 artifact。

建议：

- 不建议简单删除。可以迁入 `database/projection` 或 `api/result` 的明确 adapter 边界。
- battle simulation rule 不应直接 import mail/replay repository。

### `services/battle/routes`

当前职责：

- 当前已有未提交的 `BattleRoutes.scala`，用于列出 `apiMessages`。
- 老的 `BattleStateAPIMessage.scala`、`BattleCommandAPIMessage.scala`、`BattleResultAPIMessage.scala` 仍在 routes 下。
- `BattleAPIMessageSupport.scala` 仍提供手写 `Json => IO[Json]` support。

问题：

- APIMessage 放在 routes 下是不合理的。
- routes 应只列注册表，不应该 decode battle request、调用 service、映射业务错误。

目标：

```scala
object BattleRoutes:
  val apiMessages: List[RegisteredAPIMessage] = List(
    RegisteredAPIMessage.apiWithToken[BattleQueueJoinAPIMessage, BattleQueueJoinResponse],
    ...
  )
```

如果某些 API 不需要 token，应使用 `RegisteredAPIMessage.api[...]`，而不是强行 `APIWithTokenMessage`。

## 类型安全结构分析

### 已有 ADT/value object 基础

当前 battle 已有可复用类型：

- ID：`BattleId`、`PlayerId`、`TicketId`、`RoomId`、`ProjectileId`、`PickupId`、`BattleResultId`。
- 时间和值：`EpochMillis`、`DurationMillis`、`ElapsedMillis`、`BattleTick`、`ClientCommandSeq`、`HitPoints`、`AmmoCount`。
- finite state：`BattleMode`、`BattlePhase`、`WeaponKind`、`ProjectileKind`、`SkillKind`、`PickupKind`、`BattleCommandStatus`、`SkillOutcomeStatus`。
- state：`BattleAggregateState`、`BattlePlayerState`、`BattleProjectileState`、`BattlePickupState`、`BattleQueueSnapshot`、`RealtimeRoomSnapshot`。

这些应该作为 APIMessage 和 response 的内部字段类型，而不是重新声明 `String`。

### 当前类型安全缺口

典型问题：

```scala
battleId: String
playerId: String
ticketId: Option[String]
weaponKind: String
status: String
phase: String
message: String
```

更合理：

```scala
battleId: BattleId
playerId: PlayerId
ticketId: Option[TicketId]
weaponKind: WeaponKind
status: BattleCommandStatus
phase: BattlePhase
```

`String` 应只出现在：

- Circe decoder 读 JSON 的瞬间。
- Circe encoder 写 JSON 的瞬间。
- UI 展示文案。
- legacy file format compatibility。

## Circe 使用分析

当前使用：

- `system/api/RegisteredAPIMessage` 已经能基于 `Decoder[Message]` 和 `Encoder[Response]` 自动完成 JSON contract。
- `BattleStateApiTypes.scala` 有大量 `Encoder.forProductN` 和手写 JSON object。
- `BattleCommandApiTypes.scala` 有较复杂 decoder。
- `BattleResultFileJsonParser.scala` 用 Circe parser/decoder 处理文件 JSON。

建议：

- `objects/apiTypes` 可以存放 response case class 和 companion codec。
- 对 value object/enum 提供集中或就近的 Circe `Encoder`/`Decoder`。
- APIMessage case class 本身应被 Circe decode 成 typed message。
- 不再在 route/API support 层手写 `Json => IO[Json]`。

## Cats Effect 与副作用边界

当前 TPsys：

- APIMessage `plan` 无 `Connection`。
- 通过注入 service，然后 `IO.blocking(service.method(...))` 包住同步服务调用。

sample：

- APIMessage `plan(connection: Connection)`。
- Table 方法显式接收 `Connection`。
- Router 在请求边界传入 connection。

如果采用 sample 方式，迁移顺序必须是：

1. 先改 `system/api/APIMessage.scala`。
2. 再改 `APIMessageRouter`，让它拿到 `Connection`。
3. 再改全局 route module，传入 connection provider。
4. 再迁移 battle APIMessage。

不能只在 battle 里写 `plan(connection)`，否则无法接入现有 router。

## Render 技术边界

这里的 render 不是前端 Phaser 渲染，而是后端数据渲染：

- API state render：`BattleStateApiTypes.scala` 把 `BattleAggregateState` 转成前端 state JSON。
- replay render：`BattleReplayFramesJsonRenderer.scala` 把 battle replay frames 转成 replay JSON。
- file render：`BattleResultFileJsonRenderer.scala` 把 result records 转成文件 JSON。
- API result render：`BattleResultApiTypes.scala` 把 result record 转成 API response。

建议：

- API render 放在 `objects/apiTypes` 的 response companion/codec 中。
- replay render 独立归入 `objects/replay` 或 `database/replay` compatibility boundary。
- file render 归入 `database/result` compatibility boundary。
- 不要把 API response render、replay render、file persistence render 混在一个文件里。

## 对你提出路线的逐项评估

### 1. `battle/api` 放 `XXXAPIMessage.scala`

合理。

目标形态可以是：

```scala
final case class BattleStateReadAPIMessage(
  battleId: BattleId
) extends APIMessage[BattleStateResponse]:

  override def plan(connection: Connection): IO[BattleStateResponse] =
    for
      state <- readState(connection, battleId)
      response <- buildResponse(state)
    yield response
```

但前提是 `system/api` 已支持 `plan(connection)`。

如果暂时不迁移 system/api，则目标形态只能是：

```scala
final case class BattleStateReadAPIMessage(
  battleId: BattleId,
  service: BattleStateService
) extends APIMessage[BattleStateResponse]:

  override def plan: IO[BattleStateResponse] = ...
```

这个形态不如 sample 干净，因为 service 不能直接由 Circe decode，仍需要注册时注入。

### 2. 所有 API 都 extends `APIWithTokenMessage`

不完全合理，需要按 API 判断。

应该需要 token 的 API：

- command submit
- result record/list 如果绑定登录身份
- 未来 profile/replay 私有能力

不一定需要 token的 API：

- queue join 当前使用 `sessionToken` + `handle`。
- queue status 当前使用 `ticketId`。
- room heartbeat 当前使用 `roomId/ticketId/handle`。
- battle state read 当前可能只按 `battleId` 读取。

如果强制所有 battle API 都使用 `APIWithTokenMessage`，会改变前端契约，需要前端统一传 `userToken`，并由 router 注入 `userId`。这是 contract change，不能作为无风险内部重构。

### 3. `objects/apiTypes` 只放 final case class + companion encoder/decoder

方向合理，但要补充一条：API error ADT 也需要归属。

建议：

- request/response case class 放 `objects/apiTypes/{domain}`。
- companion object 放 Circe encoder/decoder 和 `fromDomain` / `toCommand` 这种轻量转换。
- error ADT 可以放同文件或 `objects/apiTypes/{domain}/BattleXApiError.scala`。
- 不要把 service call、database call、HTTP status 逻辑放进 apiTypes。

### 4. `objects` 里只有 case class、companion codec、统一 enum

部分合理。

合理部分：

- domain object 应该尽量是 immutable `final case class`、`enum`、pure companion。
- finite state 应该集中建模，不能散落字符串。

需要修正：

- “统一 enum”不应变成一个巨大 `BattleEnums.scala` 永久承载所有枚举。当前保留统一 enum 是为了映射一致，但长期应允许按业务归属拆分，例如 weapon enum 放 weapon，pickup enum 放 pickup。
- `objects` 还需要 pure construction/validation，例如 `BattlePlacement.fromInt`，这类可以保留。
- 纯规则如果必须放入 `objects/rules`，也必须明确无副作用。

### 5. `battle/routes/BattleRoutes.scala` 只列 apiMessages

合理。

目标：

```scala
object BattleRoutes:
  val apiMessages: List[RegisteredAPIMessage] = List(
    RegisteredAPIMessage.api[BattleQueueStatusAPIMessage, BattleQueueSnapshotResponse],
    RegisteredAPIMessage.apiWithToken[BattleCommandAPIMessage, BattleCommandAcceptedResponse]
  )
```

不应再有：

- JSON decode helper。
- service error match。
- business if/else。
- `BattleAPIMessageSupport.registered(className)(Json => IO[Json])`。

### 6. `battle/database` 放 Table 和 TableInitializer

合理，但需要分阶段。

第一阶段可迁移：

- battle result repository。
- postgres schema。
- file JSON compatibility。

不建议第一阶段迁移：

- queue runtime state。
- active battle session runtime state。
- tick 推进状态。

这些目前是 authoritative runtime memory state，不是普通 CRUD table。除非你明确要把战斗状态持久化到数据库，否则不要为了目录统一强行 table 化。

## 推荐目标结构

建议结构：

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
      BattleCommandAPIMessage.scala
    results/
      BattleResultListAPIMessage.scala
      BattleResultRecordAPIMessage.scala

  objects/
    core/
    queue/
    room/
    session/
    player/
    weapon/
    projectile/
    pickup/
    skill/
    result/
    replay/
    apiTypes/
      queue/
      room/
      session/
      results/

  routes/
    BattleRoutes.scala

  database/
    result/
      BattleResultTable.scala
      BattleResultTableInitializer.scala
      BattleResultRowCodec.scala
    replay/
      BattleReplayTable.scala
      BattleReplayTableInitializer.scala

  rules/         # 推荐保留，或暂时继续叫 engine
    world/
    actors/
    combat/
    abilities/
    runtime/
```

如果你强制不允许 `rules/engine`，则把 `rules` 放到：

```text
services/battle/objects/rules/
```

但这会让 `objects` 同时承载“数据对象”和“纯规则函数”，可读性比单独 `rules` 差。

## 单向依赖规则

推荐依赖方向：

```text
route/battle
  -> services.battle.routes
    -> services.battle.api
      -> services.battle.database
      -> services.battle.rules
      -> services.battle.objects

services.battle.database -> services.battle.objects
services.battle.rules    -> services.battle.objects
services.battle.objects  -> no api/routes/database/framework effects
```

禁止：

- `objects` import `api/routes/database`。
- `database` import `api/routes`。
- `rules` import `api/routes/database`。
- 一个 APIMessage 调另一个 APIMessage 的 private 方法。
- 跨业务域互相直接调用内部实现。

允许：

- APIMessage 调用同域 table。
- APIMessage 调用纯 rules。
- 跨域通过明确 command/result ADT 或公开端口协作。
- auth 这类通用能力通过独立 APIMessage/服务处理，但要避免形成循环。

## 建议实施顺序

### Phase 0：冻结当前中间态

先决定保留还是回退当前未提交的 APIPlanner 改动。它与新的 APIMessage 目标不完全一致。

### Phase 1：系统 APIMessage 对齐 sample

目标：

- `APIMessage.plan(connection: Connection)`。
- `RegisteredAPIMessage.planJson(payload, connection)`。
- `APIMessageRouter` 获取 connection 后执行 message。
- 保持现有 http4s route 入口不变。

验收：

```powershell
npm run backend:compile
npm run backend:test-contracts
```

### Phase 2：迁移一个最小 battle vertical slice

推荐先迁移 `BattleStateReadAPIMessage`：

- 输入少，只需要 `BattleId`。
- response 已有 state render。
- 可验证 `/api/battlestateread` 或新路径是否保持一致。

### Phase 3：迁移 command API

重点把：

- `battleId`
- `playerId`
- `ticketId`
- `clientTick`
- `clientCommandSeq`
- `weaponKind`
- `commandStatus`
- `skill outcome`

从 string-heavy DTO 改为 typed APIMessage/response。

### Phase 4：迁移 queue/room API

把当前 APIPlanner 改回 `XXXAPIMessage` 形态，或如果你决定保留 planner，则需要放弃“每个 API 是 XXXAPIMessage”的目标。

### Phase 5：迁移 result/replay database

把 `persistence` 收敛到 `database/result`、`database/replay`，用 Table/TableInitializer 表达 PostgreSQL 边界。

### Phase 6：删除旧结构

只有在引用归零后才删除：

- `services/battle/application`
- `services/battle/persistence`
- `services/battle/ports`
- 旧 `services/battle/routes/*APIMessage.scala`
- 旧 `BattleAPIMessageSupport.scala`

## 需要你决策的问题

1. 路径命名：是否接受 sample 的 `/api/battlequeuejoinapi`，还是保持当前 `/api/battlequeuejoin`？
2. system/api：是否现在就迁移到 `plan(connection: Connection)`？
3. token：是否真的要求所有 battle API 都是 `APIWithTokenMessage`，还是按 API 业务选择 token/no-token？
4. 规则层：是否允许保留 `engine` 或改名 `rules`？
5. 目录名：是否接受 Scala 更自然的 `objects/routes`，而不是强制 `object/route`？
6. database：是否要求 queue/session 状态也持久化成 table，还是第一阶段只迁移 result/replay persistence？

## 我的建议

建议采用这条路线，但做三点修正：

- 使用 `objects`，不要使用 `object` 作为 package 名。
- 保留纯规则层，命名为 `rules` 或暂时保留 `engine`。
- 先单独迁移 `system/api` 到 sample 的 connection-aware APIMessage，再做 battle APIMessage。

如果你确认这些决策，我建议下一票从 `system/api` 开始，而不是继续扩大 battle 内部迁移。因为没有 `plan(connection)`，你要求的 `XXXAPIMessage extends APIWithTokenMessage[XXXResponse]` 在当前系统里无法完整落地。
