# services/battle 重构审计报告

更新日期：2026-05-27

## 1. 总体结论

当前 `backend/src/main/scala/services/battle` 已经收敛为四个顶层目录：

```text
services/battle/
  api/
  database/
  objects/
  routes/
```

这条路线是合理的，因为它把 battle 的入口、协议、领域对象和实现层拆开了：

- `routes`：API catalog 层，只负责把 battle 支持的 APIMessage 注册给通用 `APIMessageRouter`。
- `api`：APIMessage/use-case 层，负责 request decode 后的业务编排、调用实现层、生成 response。
- `objects`：领域事实层，负责 value object、ADT/enum、domain state、API DTO 和 Circe codec。
- `database`：当前 battle 实现层，名字叫 database，但实际包含 queue/session/runtime/world/combat/actors/abilities/results/projections 等实现逻辑。

当前已经达成的点：

- 旧的 `application/engine/persistence/ports` 顶层 battle 包已经从当前源码结构中移除。
- `api` 下只剩 `XXXAPIMessage.scala` 文件，并按 queue、room、state、command、results 分域。
- `routes` 下只剩 `BattleRoutes.scala` 和 `BattleAPIRuntimeContext.scala`。
- `BattleRoutes` 返回 `List[RegisteredAPIMessage]`，不是 `List[String]`。
- `BattleRoutes` 当前使用 `apiWithTokenAndContext[Context, Message, Response]` typed catalog，不直接维护 API name 字符串。
- API path 由 `APIMessage.apiNameFromClass[Message]` 从 message 类型名推导，例如 `BattleQueueJoinAPIMessage` -> `/api/battlequeuejoin`。
- `objects/apiTypes` 当前未发现 enum，主要保存 request/response DTO 与 codec。
- `BattleCommandAcceptedResponse` 已经把 `commandStatus` / `commandReason` 从 `String` 推进为 `BattleCommandStatus` / `Option[BattleCommandReason]`。
- `BattleCommandSkillOutcomeResponse` 已经把 `action` / `status` / `reason` 从 `String` 推进为 `SkillKind` / `SkillOutcomeStatus` / `Option[SkillOutcomeReason]`。
- `BattleCommandAcceptedResponse` 已经把 `battleId` / `acceptedTick` / `acceptedCommandSeq` / `serverTime` 从 `String` / `Long` 推进为 `BattleId` / `BattleTick` / `ClientCommandSeq` / `EpochMillis`。
- `BattleStateRootResponse.phase` 已经从 `String` 推进为 `BattlePhase`，由 encoder 输出原有 wire value。
- state response 中 weapon、skill、projectile、projectile terminal、pickup、event 的 kind/reason 字段已经从 `String` 推进为对应 enum。
- state response 中 battle/player/projectile/pickup/event/slow-field 的 ID 字段已经从 `String` 推进为对应 value object。
- state response 中 tick、epoch/duration/elapsed millis、血量、体力、弹药、半径、伤害、朝向、分数等字段已经按现有 domain value object 收紧，encoder 仍输出原有 primitive JSON。
- `BattleResultRecordResponse` 已经把 result id、battle id、handle、display name、finishedAt、duration、score、placement、rating、result/mode/map/highlight/players/timeline label 等字段推进为对应 value object。
- queue/room response 已经把 ticket id、room id、player id、battle id、mode、map id/label、时间、capacity、duration、phase、rating、seat、spawn point 等字段推进为 value object 或 enum。
- `BattleQueueStatusRequest`、`BattleQueueLeaveRequest`、`BattleRoomSnapshotRequest`、`BattleStateReadAPIRequest` 已经从 wire `String` 字段推进为 `TicketId`、`RoomId`、`BattleId`，decoder 直接完成 value object 构造。
- `BattleQueueJoinRequest` 已经把 handle、session token、mode、queue request id、rating 从 `String` / `Int` 推进为 `PlayerHandle`、`SessionToken`、`BattleMode`、`QueueRequestId`、`Rating`，decoder 保持原 JSON 输入格式。
- `database/results` 已经具备 `BattleResultTable.scala` 和 `BattleResultTableInitializer.scala`。
- `database` 下未发现旧 `services.battle.application`、`engine`、`persistence`、`ports` package 引用。

当前仍未完全达成目标的点：

- 9 个 battle APIMessage 仍然继承 `APIWithTokenContextMessage[Context, Response]`，不是目标形态里的 `APIWithTokenMessage[Response]` + `plan(connection)`。
- `BattleAPIRuntimeContext` 仍然存在，说明 queue/session/state 仍依赖运行时 service 注入。
- `database` 目录语义偏宽，它不只是 Table/Repository，也承载原 engine/application runtime rules。
- queue/session 仍主要是内存 authoritative runtime service，不是 table-backed。
- `BattleStateReadAPIMessage.scala` 内部还有大型 state response renderer，这属于 API 边界投影逻辑，能运行，但后续应拆成 API-local presenter 或 typed response mapper。

## 2. 当前文件结构

### api

```text
api/command/BattleCommandAPIMessage.scala
api/queue/BattleQueueJoinAPIMessage.scala
api/queue/BattleQueueLeaveAPIMessage.scala
api/queue/BattleQueueStatusAPIMessage.scala
api/results/BattleResultListAPIMessage.scala
api/results/BattleResultRecordAPIMessage.scala
api/room/BattleRoomHeartbeatAPIMessage.scala
api/room/BattleRoomSnapshotAPIMessage.scala
api/state/BattleStateReadAPIMessage.scala
```

职责：

- 每个文件对应一个 APIMessage。
- APIMessage 负责把 JSON payload decode 成 typed message。
- APIMessage 的 `plan(...)` 负责调用 queue/state/result 等实现层，并返回 typed response。
- APIMessage 内部允许有 private helper，但不应该把 route/path 判断或 HTTP 细节放进去。

当前执行模型：

```scala
final case class XXXAPIMessage(...)
  extends APIWithTokenContextMessage[Context, XXXResponse]:
  override def plan(context: Context, connection: Connection): IO[XXXResponse] =
    for
      ...
    yield response
```

目标执行模型：

```scala
final case class XXXAPIMessage(...)
  extends APIWithTokenMessage[XXXResponse]:
  override def plan(connection: Connection): IO[XXXResponse] =
    for
      ...
    yield response
```

差距解释：

- 当前模型比旧 route-heavy 模型安全，因为 context 是 typed，不是裸字符串或全局变量。
- 但它还不是最终目标，因为 route catalog 仍然要注入 `BattleAPIRuntimeContext`。
- 要迁移到 `plan(connection)`，必须让 APIMessage 能通过 `Connection` 找到所需的 queue/session/state/result 持久化或运行时边界。

### routes

```text
routes/BattleRoutes.scala
routes/BattleAPIRuntimeContext.scala
```

`BattleRoutes` 的职责：

- 声明 battle 支持哪些 APIMessage。
- 把 message 类型、response 类型和必要 context 注册为 `RegisteredAPIMessage`。
- 不写 path match。
- 不写 JSON parser。
- 不直接处理 HTTP status。
- 不直接调用 battle 业务规则。

当前注册形态：

```scala
apiWithTokenAndContext[
  BattleQueueService,
  BattleQueueStatusAPIMessage,
  BattleQueueSnapshotResponse
](...)
```

这比下面这种字符串 catalog 更安全：

```scala
List("battlequeuejoin", "battlequeuestatus")
```

原因：

- message 类型和 response 类型由编译器检查。
- API name 从 message 类型名推导，不由 route 手写维护。
- 如果 response 类型缺少 Circe encoder，注册处会编译失败。
- 如果 message 类型缺少 decoder，注册处会编译失败。

### objects

职责：

- 保存 battle 的领域值对象，例如 `BattleId`、`PlayerId`、`TicketId`、`RoomId`、`DurationMillis`、`EpochMillis`。
- 保存 battle 的有限状态，例如 `BattlePhase`、`WeaponKind`、`ProjectileKind`、`PickupKind`、`SkillKind`。
- 保存 authoritative battle state，例如 player、projectile、pickup、weapon、skill、event、result、replay。
- 保存 API request/response DTO。
- 保存 Circe encoder/decoder。

类型安全结构：

- ID 使用 value object，不裸用 `String` 表达业务 ID。
- 时间和持续时间使用 value object，不裸用 `Long` 表达业务单位。
- 有限状态使用 enum/ADT，不裸用 `String` 表达业务状态。
- request decode 错误使用 `BattleAPIRequestError` 这样的 ADT，而不是到处散落错误字符串。

`objects/apiTypes` 的目标边界：

```text
final case class Request/Response
object Request/Response:
  given Encoder/Decoder
```

它应该避免：

- 业务 service 调用。
- HTTP route 判断。
- Table/Repository 调用。
- 大型游戏规则。
- 自己重新声明 `BattleId`、`PlayerId` 等已经存在的领域类型。

### database

当前职责：

- `queue`：排队、等待房间、join/leave/status、room snapshot、heartbeat。
- `session`：authoritative battle session、state read、command accept/apply、stored battle。
- `runtime`：tick 推进、时间、事件、finalization、history retention。
- `world`：地图事实、几何、碰撞、移动限制、出生点、map spec loading。
- `combat`：武器、开火、projectile 生成/移动/命中/终止、伤害。
- `actors`：玩家输入、玩家生命周期、玩家 runtime update、bot。
- `abilities`：skill、pickup、slow field。
- `results`：battle result repository、file/in-memory/postgres storage、table、initializer。
- `projections`：结束投影，生成 result、replay、mail/replay artifact plan。

命名风险：

- 当前 `database` 更像 battle implementation layer，不是纯数据库层。
- 如果严格要求 `database` 只包含 Table/Repository/Initializer，那么需要新增第五层，例如 `runtime` 或 `services`。
- 但当前目标明确要求 battle 顶层至少严格包含 `api/object/route/database`，并且不要回到旧顶层拆分，所以短期把 runtime rules 放在 `database/<domain>` 是可接受的迁移折中。

## 3. ADT 与 enum 结构

当前 battle 的类型安全方向是：

- 业务 ID：`BattleId`、`PlayerId`、`TicketId`、`RoomId`。
- 时间单位：`DurationMillis`、`ElapsedMillis`、`EpochMillis`、`BattleTick`。
- 数值单位：`HitPoints`、`AmmoCount`、`Rating`、`Score`。
- 有限状态：`BattlePhase`、`WeaponKind`、`ProjectileKind`、`PickupKind`、`SkillKind`、`BattleArtifactStatus`。
- API request 错误：`BattleAPIRequestError`。
- command 字段名：`BattleCommandRequestField`。

正确原则：

- Scala 内部尽量保留 ADT/enum/value object。
- 只有 Circe codec 边界把 ADT/enum 转成 JSON wire value。
- `wireValue` 是序列化出口。
- `fromWire` 是反序列化入口。
- 不应该在业务逻辑中长期传递 `"active"`、`"rifle"`、`"battle_not_found"` 这种裸字符串。

当前进展：

- command response 已完成第一轮收紧：`commandStatus`、`commandReason`、skill outcome 的 `action/status/reason` 在 Scala DTO 内部已经使用 enum，由 Circe encoder 输出原有 wire string。
- command response 已完成第二轮收紧：`battleId`、`acceptedTick`、`acceptedCommandSeq`、`serverTime` 在 Scala DTO 内部已经使用 value object，由 Circe encoder 输出原有 primitive JSON。
- state response 已完成第一轮收紧：`phase`、weapon kind、skill kind、projectile kind、projectile terminal reason、pickup kind、event kind 在 Scala DTO 内部已经使用 enum，由 Circe encoder 输出原有 wire string。
- state response 已完成第二轮收紧：ID、tick、time、cooldown、duration、hp、stamina、ammo、radius、damage、facing、score 等字段在 Scala DTO 内部已经使用 value object，由 Circe encoder 输出原有 primitive JSON。
- result response 已完成第一轮收紧：result id、battle id、handle、display name、finishedAt、duration、score、placement、rating 和各类 label 字段在 Scala DTO 内部已经使用 value object，由 Circe encoder 输出原有 primitive/string JSON。
- queue/room response 已完成第一轮收紧：snapshot、participant、session descriptor、roster、bootstrap seat 中的 ID、mode、map、time、capacity、duration、phase、rating、seat 和 spawn point 字段在 Scala DTO 内部已经使用 value object/enum，由 Circe encoder 输出原有 primitive/string JSON。
- ID-only request 已完成第一轮收紧：queue status、queue leave、room snapshot、state read 的 request DTO 内部已经使用 `TicketId`、`RoomId`、`BattleId`，由 Circe decoder 从原 JSON string 构造。
- queue join request 已完成第一轮收紧：handle、session token、mode、queue request id、rating 在 request DTO 内部已经使用 value object/enum，APIMessage 不再重复包装这些字段。

当前问题：

- 仍有部分 API DTO response 字段是 `String`，主要是 display text、message、handle 等文本字段；其中一部分是真正 wire 文本，一部分后续可以继续收紧成 value object。
- 这些字段有些是为了稳定前端 JSON contract，但从 Scala 内部 contract 看，它们过早退化成 wire string。
- 后续应按 API vertical slice 逐步把 DTO 字段改为 enum/value object，再由 Circe encoder 统一输出 wire value。

## 4. Circe 使用现状

当前使用方式：

- `io.circe.Decoder` / `Encoder`。
- `deriveDecoder` / `deriveEncoder`。
- `io.circe.syntax.*` 的 `.asJson`。
- APIMessage decoder 中通过 Circe cursor 把 JSON payload 解成 typed message。
- file repository 和 replay/result JSON 输出已使用 Circe，而不是手写字符串拼接。

合理点：

- JSON decode/encode 集中在 API boundary 和 file boundary。
- 编译期能检查 response encoder 是否存在。
- 大部分手写 JSON render 已经被 Circe 取代。

仍需注意：

- 不要新增正则解析 JSON。
- 不要新增手写 JSON escaping。
- 不要让 `objects/apiTypes` 变成业务规则执行层。
- 对 enum 的 `wireValue/fromWire` 应该通过 codec 使用，而不是让业务 plan 中到处手动调用。

## 5. Cats Effect 使用现状

当前边界：

- `APIMessage.plan(...): IO[Response]` 是 API 执行边界。
- 同步 service/table/repository 调用通过 `IO.blocking` 包起来。
- Postgres result 写入通过 `PostgresSupport.withTransactionIO(connection)` 表达事务边界。
- API 错误通过 `IO.raiseError(APIMessageError...)` 进入统一 HTTP error mapper。

合理点：

- 副作用没有放进 domain case class。
- API orchestration 用 `for` comprehension 表达执行顺序。
- 阻塞调用显式进入 `IO.blocking`。

仍需改进：

- 当前很多 queue/session service 是同步内存服务，APIMessage 只能 `IO.blocking` 包裹调用。
- 如果迁移到完全 `plan(connection)`，需要明确 queue/session 的持久化或 runtime access 方式，不能用隐藏全局 singleton 代替 typed context。

## 6. Render / Projection 技术现状

这里的 render 不是前端画面渲染，而是后端把 domain state 投影成 API/file/replay JSON 的过程。

当前 render/projection 点：

- `BattleStateReadAPIMessage.scala` 内部的 `BattleStateResponseRenderer`：把 `BattleAggregateState` 投影成前端需要的 battle state response。
- `BattleResultRecordAPIMessage.renderRecordResponse`：把 `BattleResultRecord` 投影成 result response。
- `database/projections/BattleReplayFramesJsonRenderer.scala`：把 replay frames 投影成 replay JSON。
- `database/results/BattleResultFileJsonRenderer.scala`：把 result repository 文件格式输出为 JSON。

合理点：

- 投影逻辑没有放进 immutable domain model。
- domain state 和 wire response 之间有显式转换。

问题：

- state response renderer 现在贴在 APIMessage 文件内部，能运行但文件偏大。
- 长期更合适的做法是放到 `api/state/BattleStateResponsePresenter.scala` 这类 API-local 文件；但当前目标要求 `api` 只包含 `XXXAPIMessage.scala`，所以暂时只能作为 APIMessage 的 private object。
- 不建议把 renderer 放回 `objects/apiTypes`，因为 `apiTypes` 应只保存 DTO/codec，不应知道完整 domain aggregate 如何投影。

## 7. 当前依赖方向

目标方向：

```text
routes -> api -> database -> objects
api -> objects/apiTypes
objects -> 不依赖 api/routes/database
database -> 不依赖 api/routes
```

当前审计结果：

- 未发现 `objects -> api/routes/database`。
- 未发现 `database -> api/routes`。
- 未发现 `api -> routes`。
- 未发现旧 `services.battle.application`、`engine`、`persistence`、`ports` package 引用。
- `routes -> database` 仍存在，主要来自 `BattleAPIRuntimeContext` 和 result backend 注入。
- `api -> database` 存在，因为 APIMessage 需要调用 service/table/storage。
- `database -> objects` 存在，这是合理方向。

主要结构性风险：

- `abilities`、`actors`、`combat`、`runtime` 之间仍然是原 engine rules 网络，虽然 imports 已经显式化，但概念上还没有完全收敛成单向 pipeline。
- 更理想的方向是：

```text
session -> runtime -> actors/combat/abilities/world -> objects
queue -> session public contracts -> objects
projections -> result/replay/mail ports -> objects
api -> service public surface -> objects/apiTypes
```

## 8. 路线合理性判断

用户提出的路线总体合理：

```text
battle/api
battle/objects
battle/routes
battle/database
```

合理原因：

- `routes` 足够薄，避免 route 文件变成业务状态机。
- `api` 用 APIMessage 表达每个 use case，适合和 sample 的 APIMessageRouter 对齐。
- `objects` 成为单一类型事实来源，避免 API 层重新声明 BattleId、PlayerId、WeaponKind 等概念。
- `database` 承载当前实现层，至少让旧 engine/application 顶层结构不再扩散。

需要明确的取舍：

- 如果坚持 `api` 只允许 `XXXAPIMessage.scala`，那么 API-local renderer/presenter 只能放进对应 APIMessage 文件的 private object，文件会变大。
- 如果坚持 `database` 只允许 Table/Initializer/Repository，那么必须新增 runtime/services 顶层；这与当前“四层顶层”目标冲突。
- 如果要求所有 APIMessage 都改成 `APIWithTokenMessage[Response]` + `plan(connection)`，必须先解决 queue/session/state 的运行时 service 如何从 connection 或明确 port 中获得。

## 9. 推荐下一步

### BE-BATTLE-API-PLAN-68

目标：

- 选择一个最容易迁移的 API vertical slice。
- 把它从 `APIWithTokenContextMessage[Context, Response]` 迁移到 `APIWithTokenMessage[Response]`。
- route catalog 从 `apiWithTokenAndContext[...]` 改成 sample 风格的 `apiWithToken[Message, Response]`。

推荐候选：

- 优先考虑 `BattleResultListAPIMessage` 或 `BattleResultRecordAPIMessage`。

原因：

- result 已经有 `BattleResultTable` 和 `BattleResultTableInitializer`。
- result 比 queue/session 更接近 connection-backed。
- 迁移 result 不会触碰实时房间匹配和 authoritative battle runtime。

风险：

- 当前 contract tests 使用 repository-backed result backend 注入测试仓储行为。
- 如果直接改成 connection-only，需要同步调整 tests 或保留 repository-backed 测试适配层。
- 不建议为了消除 context 使用全局 singleton，因为那会隐藏副作用边界。

### BE-BATTLE-APITYPES-69

目标：

- 选择一个 API response，把其中明显有 enum/value object 的 `String` 字段改成 typed field。
- Circe encoder 负责输出 wire value。

已完成候选：

- `BattleCommandAcceptedResponse.commandStatus`
- `BattleCommandAcceptedResponse.commandReason`
- `BattleCommandSkillOutcomeResponse.action`
- `BattleCommandSkillOutcomeResponse.status`
- `BattleCommandSkillOutcomeResponse.reason`

剩余候选：

- API request DTO 中更复杂的输入字段，例如 battle command 的 battle id/player id/ticket id/vector/tick、result record 的 battle id/duration/rating/score 等。
- 部分展示文本仍是 `String`，例如 avatar、skin、currentLoadout、message 等；这些可能是真正外部文本，不应盲目 value-object 化。

验收：

- Scala 内部 response 字段使用 enum/value object。
- JSON contract 输出不变。
- `backend:compile` 和 `backend:test-contracts` 通过。

## 10. 当前验收状态

已满足：

- 顶层四目录存在。
- APIMessage 文件命名符合 `XXXAPIMessage.scala`。
- route catalog 不再是字符串列表。
- `objects/apiTypes` 未发现 enum。
- old battle 顶层 package 引用未发现。
- backend compile 和 contract tests 在上一次代码变更后通过。

未满足：

- 所有 APIMessage 改为 `APIWithTokenMessage[Response]` + `plan(connection)`。
- queue/session/state 改为 connection/table-backed 或更明确的 typed port。
- 所有 API DTO 字段都使用 domain value object/enum。
- `database` 只包含真正数据库语义。

结论：

当前路线方向正确，但还不是完成态。下一步应该做 result API 的 vertical slice，而不是继续大范围移动文件。

## 11. 本轮补充：API catalog 与 command request 类型收紧

本轮确认并保留的结构：

- `BattleRoutes` 使用 `List[RegisteredAPIMessage]`，每个元素通过 `apiWithTokenAndContext[Context, Message, Response]` 注册。
- battle route catalog 不再维护 `List[String]`，也不直接手写 `apiName(...)`。
- API path 继续由 `APIMessage.apiNameFromClass[Message]` 从 message 类型推导，例如 `BattleCommandAPIMessage` 对应 `/api/battlecommand`。
- 这种结构与 sample 中 `api[ListBooksAPIMessage, BookListResponse]` / `apiWithToken[...]` 的方向一致：注册点表达的是 message type 和 response type，而不是裸字符串。

本轮完成的 command request 收紧：

- `BattleCommandAPIRequest.battleId` 使用 `BattleId`，不再在 planner 中传裸 `String`。
- `BattleCommandAPIRequest.playerId` 使用 `PlayerId`。
- `BattleCommandAPIRequest.ticketId` 使用 `Option[TicketId]`，缺失 ticket 的业务错误仍由 APIMessage 显式处理。
- `BattleCommandAPIRequest.clientTick` 使用 `BattleTick`。
- `BattleCommandAPIRequest.clientCommandSeq` 使用 `Option[ClientCommandSeq]`。
- `BattleCommandAPIRequest.movement`、`aim`、`pointerWorld` 使用 `BattleCommandVector`。
- `BattleCommandAPIRequest.switchWeaponDirection` 使用 `BattleWeaponSwitchDirection`。
- `BattleCommandAPIRequest.switchWeaponIndex` 使用 `Option[BattleWeaponSwitchIndex]`。

边界说明：

- 裸 JSON string/number 只允许出现在 Circe decoder 输入边界。
- decoder 负责把 wire JSON 构造成已有 domain value object。
- `BattleCommandAPIMessage` 不再重复执行 `BattleId.apply`、`PlayerId.apply`、`TicketId.apply` 或 vector 二次转换。
- JSON contract 没有变化，前端仍发送原字段名和原 primitive JSON。

验证：

- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。
- sbt 输出的 `sun.misc.Unsafe` 是当前 JDK/Scala runtime 警告，不是本轮引入的业务类型安全问题。

## 12. 本轮补充：result request DTO 类型收紧

本轮完成的 result API request 收紧：

- `BattleResultListAPIRequest.handle` 使用 `Option[PlayerHandle]`。
- `BattleResultListAPIRequest.battleId` 使用 `Option[BattleId]`。
- `BattleResultListAPIRequest.limit` 使用 `Option[BattleResultListLimit]`，不再裸用 `Int` 表达战绩列表分页数量。
- `BattleResultRecordAPIRequest.battleId` 使用 `Option[BattleId]`。
- `BattleResultRecordAPIRequest.handle` 使用 `Option[PlayerHandle]`。
- `BattleResultRecordAPIRequest.displayName` 使用 `Option[DisplayName]`。
- `BattleResultRecordAPIRequest.finishedAt` 使用 `Option[EpochMillis]`。
- `BattleResultRecordAPIRequest.durationMs` 使用 `Option[DurationMillis]`。
- `BattleResultRecordAPIRequest.score` 使用 `Option[Score]`。
- `BattleResultRecordAPIRequest.placement` 使用 `Option[BattlePlacement]`。
- `BattleResultRecordAPIRequest.ratingBefore` / `ratingDelta` / `ratingAfter` 使用 `Rating` / `RatingDelta` / `Rating`。
- `BattleResultRecordAPIRequest.resultLabel` / `modeLabel` / `mapLabel` / `highlightLine` / `playersLine` / `timelineHint` 使用对应的 battle label value object。

同步完成的 domain command 收紧：

- `BattleResultRecordCommand` 的 result/mode/map/highlight/players/timeline 字段不再是 `String`，改为对应 value object。
- `BattleResultListQuery.limit` 不再是 `Int`，改为 `BattleResultListLimit`。

边界说明：

- JSON wire contract 没有变化，前端仍发送原字段名和原 primitive JSON。
- Circe decoder 位于 `objects/apiTypes/results`，负责把 JSON primitive 反序列化成 domain value object。
- `BattleResultListAPIMessage` 和 `BattleResultRecordAPIMessage` 不再承担这些字段的重复 primitive 包装，只保留 use-case 组装、默认值、校验和存储调用。
- `currentLoadout`、`finishedAtLabel` 仍保留 `String`，因为它们当前是展示文本/装载描述，不是已有有限状态或强业务 ID。

验证：

- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。
- `git diff --check` 通过；输出的 CRLF 提示是 Git 换行符提示，不是空白错误。

## 13. 本轮补充：room heartbeat request 类型收紧

本轮完成的 room heartbeat request 收紧：

- `BattleRoomHeartbeatRequest.roomId` 从 `Option[String]` 改为 `Option[RoomId]`。
- `BattleRoomHeartbeatRequest.ticketId` 从 `Option[String]` 改为 `Option[TicketId]`。
- `BattleRoomHeartbeatRequest.handle` 从 `Option[String]` 改为 `Option[PlayerHandle]`。

同步完成的 APIMessage 简化：

- `BattleRoomHeartbeatAPIMessage.buildCommand` 不再执行 `RoomId.apply`、`TicketId.apply`、`PlayerHandle.forLookup`。
- APIMessage 只把 typed request 组装成 `RealtimeRoomHeartbeatCommand`。
- 字符串 trim、空值过滤、handle lookup 只保留在 Circe decoder 边界。

边界说明：

- JSON wire contract 没有变化，前端仍发送 `roomId`、`ticketId`、`handle` 字符串。
- Scala 内部 request contract 不再传播裸字符串 ID。
- room heartbeat 仍依赖 `BattleQueueService` context；这属于 queue/runtime 迁移问题，不在本轮改动范围内。

验证：

- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。
- `git diff --check` 通过。

## 14. 本轮补充：queue leave response 业务结果 ADT 化

本轮完成的 queue leave response 收紧：

- `BattleQueueLeaveResponse` 不再持有裸 `Boolean left`。
- `BattleQueueLeaveResponse` 改为持有 `BattleQueueLeaveOutcome`。
- `BattleQueueLeaveOutcome` 已经是 domain enum，分支包括 `LeftQueue`、`NotWaiting`、`TicketNotFound`。

同步完成的 APIMessage 简化：

- `BattleQueueLeaveAPIMessage.buildResponse` 不再把业务 outcome 压缩成布尔值。
- `left` 布尔字段只保留在 Circe encoder 的 wire 输出边界。
- JSON contract 没有变化，前端仍收到原来的 `{"left": true/false}`。

类型安全收益：

- Scala 内部不再用 `false` 同时表达“不在等待中”和“ticket 不存在”这类不同业务状态。
- 业务语义保留为 ADT，只有序列化边界才退化成兼容旧前端的布尔 wire 字段。

验证：

- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。
- 残留检索未发现 `left: Boolean`、`leftFlag` 或 `BattleQueueLeaveResponse(left = ...)`。

## 15. 本轮补充：queue 外观字段 value object 化

本轮完成的 battle queue 外观字段收紧：

- 新增 `BattleAvatarKey`，表示 battle 等待区、房间快照和战局 bootstrap 中使用的头像/外观 key。
- 新增 `BattleSkinKey`，表示 battle 渲染层使用的角色皮肤 key。
- `BattleQueueJoinRequest.avatar` / `skin` 从 `Option[String]` 改为 `Option[BattleAvatarKey]` / `Option[BattleSkinKey]`。
- `BattleQueueJoinCommand.avatar` / `skin` 从 `Option[String]` 改为 `Option[BattleAvatarKey]` / `Option[BattleSkinKey]`。
- `BattleQueueParticipant`、`BattleSessionRosterEntry`、`BattleSessionBootstrapSeat` 的 `avatar` / `skin` 字段改为 typed value object。
- lobby shared response DTO 的 `avatar` / `skin` 字段也改为 typed value object，encoder 继续输出原字符串。

边界说明：

- JSON wire contract 没有变化，前端仍发送和接收 `avatar` / `skin` 字符串。
- 字符串 trim、空值过滤和 value object 构造只发生在 `BattleQueueJoinRequest` 的 Circe decoder 边界。
- `BattleQueueJoinAPIMessage` 不再重复清理 `avatar` / `skin` 字符串，只传递 typed request。
- 没有复用 identity 的 `SkinId`，因为 battle 中的 `skin` 是渲染资产 key，bot 资料中存在 `brown` / `woman` 等不属于 identity `SkinId` 的值。

额外发现：

- `BattleRoomBootstrapper` 之前把 bot 的 `skin` 设置为 `avatar` 字符串；现在通过 `BattleSkinKey.fromWire(profile.skin.avatarKey.value)` 显式表达这是 battle 渲染 skin key，而不是普通字符串。
- 这一步没有引入 bot domain 依赖之外的新依赖，仍保持 queue bootstrapper 原有 bot profile 读取方式。

验证：

- `npm run backend:compile` 通过。
- 第一次 `npm run backend:test-contracts` 暴露两个测试 fixture 仍传裸 `"fox"` / `"soldier"` 字符串，已修复。
- 第二次 `npm run backend:test-contracts` 通过。
- 残留检索未发现 battle queue/session 外观字段继续使用 `Option[String]`。

## 16. 本轮补充：result API 向 `plan(connection)` 渐进迁移

本轮完成的 result API 执行模型迁移：

- `BattleResultListAPIMessage` 现在同时实现：
  - `APIWithTokenMessage[BattleResultListResponse]`
  - `APIWithTokenContextMessage[BattleResultStorage, BattleResultListResponse]`
- `BattleResultRecordAPIMessage` 现在同时实现：
  - `APIWithTokenMessage[BattleResultRecordResponse]`
  - `APIWithTokenContextMessage[BattleResultStorage, BattleResultRecordResponse]`
- `BattleRoutes.connectionBackedResultApiMessages` 现在使用 `apiWithToken[Message, Response]` 注册 result APIs。
- Postgres / connection-backed 运行路径现在会调用 message 自己的 `plan(connection)`，不再通过 `BattleResultStorage.ConnectionTable` context 注入。
- Repository-backed 路径仍保留 `apiWithTokenAndContext[BattleResultStorage, Message, Response]`，用于 File/InMemory 存储和现有 contract tests。

为什么这是过渡方案：

- 当前 `BackendRuntime` 仍显式支持 `StorageConfig.InMemory` 和 `StorageConfig.File(_)`。
- 这两种模式没有 JDBC battle result table connection，只能通过 `BattleResultRepository` 工作。
- 因此直接删除 context-backed result API 会破坏非 Postgres 运行模式。
- 本轮先让 connection-backed 路径达到目标形态，同时保留 repository-backed fallback，避免一次性破坏运行入口。

类型安全和 side-effect 边界：

- result API 的 Postgres 路径现在由 `APIWithTokenMessage.plan(connection)` 表达执行边界。
- `BattleResultRecordAPIMessage.plan(connection)` 仍通过 `PostgresSupport.withTransactionIO(connection)` 包裹 table save。
- repository fallback 仍被限定在 result API vertical slice 内，没有扩散到 route path 判断或 JSON parsing。

验证：

- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。
- 检索确认 `connectionBackedResultApiMessages` 使用 `apiWithToken[...]`。
- 检索确认 result APIMessage 已实现 `APIWithTokenMessage[...]`。

剩余差距：

- queue、room、state、command API 仍依赖 runtime service context，因此还不是 `APIWithTokenMessage + plan(connection)`。
- result API 的 repository-backed fallback 仍是 context message；是否完全删除它取决于是否放弃 File/InMemory result API 运行模式，或者先为这些模式设计明确的 connection/resource adapter。

## 17. 本轮补充：weapon heat value object 下沉

本轮完成的 weapon heat 类型收紧：

- `BattleWeaponHeat` 从 combat/database catalog 私有定义下沉到 `objects/core`。
- `BattleWeaponHeatRatePerSecond` 从 combat/database catalog 私有定义下沉到 `objects/core`。
- `BattleWeaponState.heat` 从裸 `Int` 改为 `BattleWeaponHeat`。
- `BattleStateWeaponResponse.heat` 从裸 `Int` 改为 `BattleWeaponHeat`。
- `BattleStatePlayerResponse.heat` 从裸 `Int` 改为 `BattleWeaponHeat`。
- state response encoder 继续把 `BattleWeaponHeat.value` 输出为原来的 JSON number。

同步完成的 runtime/combat 调整：

- `BattleWeaponCatalog` 继续定义 heat weapon 的 max heat、每发热量和冷却速率，但使用 `objects/core` 中的 value object。
- `BattleWeaponRules.createWeaponState` 和 `refillWeaponState` 使用 `BattleWeaponHeat(0)` 初始化。
- `BattleWeaponFireRules.chargeHeatWeapon` 在计算时显式读取 `.value`，写回 `BattleWeaponHeat(heatAfter)`。
- `BattlePlayerRuntimeRules.advanceWeaponHeat` 在冷却时显式读取 `.value`，写回 `BattleWeaponHeat(...)`。

类型安全收益：

- 武器热量不再和普通 `Int` 混用。
- heat 的单位和业务含义进入 battle domain object，而不是只隐藏在 combat catalog 私有类里。
- JSON contract 不变，前端仍收到数字 `heat`。

验证：

- 第一次 `npm run backend:compile` 在 124 秒超时，没有返回编译错误。
- 第二次 `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。
- 残留检索未发现 `heat: Int`、`heat = 0`、`weapon.heat + ...`、`weapon.heat - ...` 等旧裸热量表达。

## 18. 本轮补充：BattleRoutes typed 注册与 kill count value object 化

本轮先复查了 battle API 注册方式：

- `services.battle.routes.BattleRoutes` 当前返回的是 `List[RegisteredAPIMessage]`。
- 每个 battle API 通过 `apiWithToken[...]` 或 `apiWithTokenAndContext[...]` 注册。
- API path 由 `system.api.RegisteredAPIMessage` 内部的 `APIMessage.apiNameFromClass[Message]` 推导。
- 因此 battle route catalog 不再需要维护 `List[String]`、`apiMessageNames` 或手写 `apiName("...")`。
- 这和 sample 中 `api[ListBooksAPIMessage, BookListResponse]` / `apiWithToken[...]` 的方向一致：注册表声明 message type 和 response type，而不是字符串路径。

本轮完成的类型收紧：

- 新增 `KillCount` value object，用来表达战斗击杀数。
- `BattlePlayerState.kills` 从裸 `Int` 改为 `KillCount`。
- `BattleStatePlayerResponse.kills` 从裸 `Int` 改为 `KillCount`。
- state response encoder 继续输出原来的 JSON number，前后端 wire contract 不变。
- 击杀结算处改为 `KillCount(owner.kills.value + 1)`，避免在 domain state 里传播普通整数。
- 初始玩家状态使用 `KillCount(0)`。
- 结算文案读取 `player.kills.value`。
- contract test fixture 的 `kills` 参数也改为 `KillCount`，避免测试层继续裸传击杀数。

类型安全收益：

- `kills` 不再和普通计数、分数、冷却、热量等 `Int` 混用。
- JSON primitive 只保留在 encoder 边界，Scala 内部状态使用明确业务类型。
- 这保持了 sample 式 ADT/value object 建模方向，也不改变现有 API 字段名和响应结构。

验证：

- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。
- sbt/JDK 输出的 `sun.misc.Unsafe` 是当前 Scala runtime 警告，不是本轮引入的类型安全问题。

## 19. 当前完成度审计：目标路线是否合理，以及为什么不能继续盲删 context-backed 代码

### 19.1 路线本身是否合理

用户目标里的大方向是合理的：

- `battle/api` 放 HTTP/API use-case message，也就是 `XXXAPIMessage.scala`。
- `battle/objects` 放被 API、runtime、database 共用的业务对象、ADT、value object 和 API DTO。
- `battle/routes` 只放非常薄的 API 注册表，例如 `BattleRoutes.scala`。
- `battle/database` 放 table、table initializer、repository、runtime rules、queue/session/result 这类有状态或持久化边界。

这个方向比旧的 `application/engine/persistence/ports/routes` 平铺更清晰，因为它把代码按“边界职责”拆开，而不是按历史演进阶段拆开。

但这个路线有一个前提：不能只移动文件名，必须同步解决依赖方向。最终依赖方向应该是：

- `routes` 只依赖 `api` 和注册工具。
- `api` 依赖 `objects/apiTypes`、domain objects、必要的 `database` table 或 narrow adapter。
- `database` 依赖 `objects`，不应该反向依赖 `api` 或 `routes`。
- `objects` 不依赖 `api`、`routes`、`database` 的实现细节。

### 19.2 当前已经满足的部分

当前 `services/battle` 顶层已经基本变成目标目录：

- `services/battle/api`
- `services/battle/objects`
- `services/battle/routes`
- `services/battle/database`

当前 API 文件命名也基本满足目标：

- `BattleCommandAPIMessage.scala`
- `BattleQueueJoinAPIMessage.scala`
- `BattleQueueStatusAPIMessage.scala`
- `BattleQueueLeaveAPIMessage.scala`
- `BattleRoomSnapshotAPIMessage.scala`
- `BattleRoomHeartbeatAPIMessage.scala`
- `BattleStateReadAPIMessage.scala`
- `BattleResultListAPIMessage.scala`
- `BattleResultRecordAPIMessage.scala`

当前 `BattleRoutes` 也已经不是字符串注册表：

- 它返回 `List[RegisteredAPIMessage]`。
- 它使用 `apiWithToken[...]` 和 `apiWithTokenAndContext[...]` 注册 message。
- API path 由 `APIMessage.apiNameFromClass[Message]` 推导。
- 已经没有 `apiMessageNames: List[String]`、`List[String]` 或手写 `apiName("...")`。

这点和 sample 的注册风格一致：注册的是 message type 和 response type，不是字符串路径。

### 19.3 当前没有满足的部分

目标要求每个 APIMessage 最终类似：

```scala
final case class XxxAPIMessage(...) extends APIWithTokenMessage[XxxResponse]:
  override def plan(connection: Connection): IO[XxxResponse] =
    for
      ...
    yield response
```

当前只有 result APIs 部分接近这个目标：

- `BattleResultListAPIMessage` 已经实现 `APIWithTokenMessage[BattleResultListResponse]`。
- `BattleResultRecordAPIMessage` 已经实现 `APIWithTokenMessage[BattleResultRecordResponse]`。
- 但它们为了兼容 File/InMemory contract tests 和运行模式，仍然同时实现 `APIWithTokenContextMessage[BattleResultStorage, ...]`。

当前仍然没有达到目标的 API：

- `BattleQueueJoinAPIMessage` 仍是 `APIWithTokenContextMessage[BattleQueueJoinAPIContext, BattleQueueSnapshotResponse]`。
- `BattleQueueStatusAPIMessage` 仍是 `APIWithTokenContextMessage[BattleQueueService, BattleQueueSnapshotResponse]`。
- `BattleQueueLeaveAPIMessage` 仍是 `APIWithTokenContextMessage[BattleQueueService, BattleQueueLeaveResponse]`。
- `BattleRoomSnapshotAPIMessage` 仍是 `APIWithTokenContextMessage[BattleQueueService, RealtimeRoomSnapshotResponse]`。
- `BattleRoomHeartbeatAPIMessage` 仍是 `APIWithTokenContextMessage[BattleQueueService, RealtimeRoomSnapshotResponse]`。
- `BattleStateReadAPIMessage` 仍是 `APIWithTokenContextMessage[BattleStateService, BattleStateResponse]`。
- `BattleCommandAPIMessage` 仍是 `APIWithTokenContextMessage[BattleStateService, BattleCommandAcceptedResponse]`。

这意味着当前 battle API 还不是完全由 `plan(connection)` 驱动。

### 19.4 database Table/Initializer 覆盖情况

当前真正有 table/initializer 的 battle 子域主要是 result：

- `database/results/BattleResultTable.scala`
- `database/results/BattleResultTableInitializer.scala`

queue/session/state/command 目前仍主要是内存 runtime service：

- `database/queue/InMemoryBattleQueueService`
- `database/session/InMemoryBattleStateService`
- runtime rules under `database/runtime`

所以如果现在直接把 queue/state/command API 改成 `APIWithTokenMessage + plan(connection)`，会立刻遇到两个问题：

- 没有 queue/session 对应的 table schema。
- 当前运行时仍依赖内存中的 room、ticket、battle state、tick 推进和 projector。

这不是简单改继承就能完成的迁移。

### 19.5 为什么不能直接删除 repository/context-backed 分支

当前 `BackendHttp4sApp` 会按 storage mode 选择 result backend：

- Postgres 使用 `ConnectionBacked`。
- InMemory/File 使用 `RepositoryBacked`。

当前 `BattleHttpRouteContractSuites` 也仍然用 `RepositoryBacked(resultRepository)` 测 battle result route。

因此现在直接删除 result API 的 context-backed 分支会破坏：

- InMemory 模式。
- File 模式。
- 现有 contract tests。

要删除它，必须先决定一件事：

- 是否正式放弃 battle result API 的 File/InMemory route 兼容。
- 或者为 File/InMemory 提供同样形态的 connection/resource adapter。
- 或者把 tests 迁移到 Postgres/table-backed contract。

在这个决策之前，保留 result 的双路径是一个兼容性过渡，而不是最终目标。

### 19.6 当前类型安全结构

当前已经在向 ADT/value object 收敛：

- ID 类：`BattleId`、`RoomId`、`TicketId`、`PlayerId`、`HeroId` 等。
- 时间类：`EpochMillis`、`DurationMillis`、`ElapsedMillis`、`BattleTick`。
- 数值类：`Score`、`KillCount`、`HitPoints`、`Stamina`、`AmmoCount`、`BattleWeaponHeat`。
- enum/ADT：`BattleMode`、`BattlePhase`、`WeaponKind`、`ProjectileKind`、`SkillKind`、`BattleArtifactStatus`、`BattleQueueLeaveOutcome` 等。

当前仍然存在的合理 primitive：

- `Boolean` 在 API response 中用于 wire 兼容，例如 `alive`、`isBot`、`resultReady`、`replayReady`。
- `String` 在展示文案中仍存在，例如 `finishedAtLabel`、`currentLoadout`、event message。

当前仍然需要继续收紧的点：

- 部分展示文案可以后续 value object 化，例如 `BattleFinishedAtLabel`、`BattleLoadoutLabel`、`BattleEventMessage`。
- API decode error 还有一些地方通过 `DecodingFailure.message` 做桥接，这不是最终最强类型安全形态。
- queue/state/command API 的 `ContextMessage` 仍说明应用层依赖注入没有完全迁移进 message plan。

### 19.7 Circe / Cats Effect 使用情况

当前已经使用：

- Circe `Decoder` / `Encoder` 处理 API DTO 和 response。
- `io.circe.syntax.*` 输出 JSON。
- `cats.effect.IO` 表达 APIMessage plan。
- `IO.blocking` 包住阻塞 repository/table/service 调用。
- `Resource[IO, Connection]` 管理 http4s route 的 JDBC connection。

当前还不理想：

- File JSON result 仍有专门的 file parser/renderer，虽然已经使用 Circe，但仍属于兼容层。
- 部分 API decoder 把 typed error 降级为 `DecodingFailure.message`，再从字符串恢复 HTTP error。
- queue/session runtime 仍是内存 mutable service，副作用边界依赖 synchronized state，而不是 table-backed transaction。

### 19.8 Render 技术边界

后端 battle 不应该负责 Phaser/render。

后端当前和 render 相关的职责应只限于：

- 输出 state response。
- 输出 replay frame JSON。
- 输出 mapId、worldSize、players、projectiles、pickups、events 等可渲染状态。
- 保持字段名、enum wire value、nullable/optional 结构稳定。

当前后端 render-facing 输出主要在：

- `objects/apiTypes/state`
- `BattleStateReadAPIMessage`
- `database/projections/BattleReplayFramesJsonRenderer`

这部分应该继续保持“后端权威状态 -> typed response -> JSON”的方向，不能把 Phaser 视图逻辑反向塞进后端。

### 19.9 推荐的下一步

不建议下一步直接大迁移 queue/state/command 到 table-backed，因为缺少 table/initializer 与 transaction 设计，会导致一次性修改过大。

推荐下一步做一个较小但明确的 ticket：

- `BE-BATTLE-RESULT-API-86`
- 目标：只处理 result API。
- 内容：明确 result API 的最终运行模式，是保留 File/InMemory fallback，还是迁移为 pure connection-backed。
- 如果选择 pure connection-backed，则删除 `BattleResultStorage`、删除 result repository-backed API 注册、改 contract tests 为 connection-backed。
- 如果选择继续兼容 File/InMemory，则在报告里把 result dual-path 标记为“兼容边界”，暂不强行删除。

之后再做：

- `BE-BATTLE-QUEUE-TABLE-87`：设计 queue room/ticket table 与 initializer。
- `BE-BATTLE-QUEUE-API-88`：把 queue join/status/leave 迁移到 `APIWithTokenMessage + plan(connection)`。
- `BE-BATTLE-ROOM-API-89`：把 room snapshot/heartbeat 迁移到 `APIWithTokenMessage + plan(connection)`。
- `BE-BATTLE-SESSION-TABLE-90`：设计 battle session/state persistence boundary。
- `BE-BATTLE-STATE-COMMAND-API-91`：迁移 state read/command。

### 19.10 当前结论

当前结构方向是对的，但还不是完成态。

已经完成的是：

- 顶层目录方向。
- API 文件命名方向。
- BattleRoutes typed registration。
- result API 的部分 `plan(connection)`。
- 多个重要字段的 value object/ADT 化。

尚未完成的是：

- 所有 APIMessage 统一成 `APIWithTokenMessage[Response]`。
- queue/session/state/command table-backed。
- 删除 result 的 repository/context fallback。
- 消除所有 stringly decode error bridge。
- 明确 File/InMemory 是否仍是正式运行模式。

## 20. 模块级实现逻辑与技术边界清单

本节基于当前 worktree 重新审计，目的是把 `services/battle` 的真实代码结构整理到可以做下一步决策的粒度。

### 20.1 文件分布

当前顶层分布：

| 顶层目录 | 文件数 | 当前职责 |
| --- | ---: | --- |
| `api` | 9 | APIMessage use-case 入口，负责 decode 后的业务编排、调用 service/table、返回 response |
| `objects` | 46 | domain value object、ADT、state model、API DTO、Circe encoder/decoder |
| `routes` | 2 | battle APIMessage 注册表与 runtime context |
| `database` | 89 | queue/session/runtime/world/combat/actors/abilities/results/projections 的规则、状态服务、表、存储 |

当前 `api` 子域：

| API 子域 | 文件 |
| --- | --- |
| `api/command` | `BattleCommandAPIMessage.scala` |
| `api/queue` | `BattleQueueJoinAPIMessage.scala`, `BattleQueueStatusAPIMessage.scala`, `BattleQueueLeaveAPIMessage.scala` |
| `api/room` | `BattleRoomSnapshotAPIMessage.scala`, `BattleRoomHeartbeatAPIMessage.scala` |
| `api/state` | `BattleStateReadAPIMessage.scala` |
| `api/results` | `BattleResultListAPIMessage.scala`, `BattleResultRecordAPIMessage.scala` |

当前 `database` 子域：

| database 子域 | 文件数 | 主要实现逻辑 |
| --- | ---: | --- |
| `queue` | 15 | 排队、房间等待、heartbeat、ticket、room lifecycle、session bootstrap |
| `session` | 11 | battle state service、command accept、stored battle advance、finish projection 状态 |
| `runtime` | 12 | tick 推进、runtime step、finalization、event/replay frame capture、retention |
| `world` | 7 | 地图、碰撞、出生点、几何、移动、map spec loader |
| `combat` | 10 | weapon、fire、projectile factory/runtime/motion/impact/terminal |
| `actors` | 5 | player input、bot 行为、player lifecycle、player runtime update |
| `abilities` | 6 | skill、pickup、slow field、能力命令 |
| `results` | 10 | result repository、file JSON 兼容、Postgres result table、table initializer |
| `projections` | 13 | finish projection、settlement、mail/replay/result artifacts、replay frames JSON |

当前 `objects` 子域：

| objects 子域 | 文件数 | 主要类型结构 |
| --- | ---: | --- |
| `apiTypes` | 17 | request/response DTO 与 Circe encoder/decoder |
| `core` | 3 | ID、时间、数值 value object、aggregate state |
| `command` | 1 | battle command request/accepted/skill outcome |
| `queue` | 1 | queue snapshot、participant、session descriptor/bootstrap |
| `player` | 4 | player state、life state、participant kind、survival outcome |
| `weapon` | 4 | weapon state、switch direction/index、thermal state |
| `projectile` | 1 | projectile state/terminal state |
| `pickup` | 3 | pickup state、availability、definition |
| `skill` | 2 | skill intents、slow field |
| `event` | 1 | battle event state |
| `replay` | 2 | replay frame state、replay hero life state |
| `result` | 3 | result record、finish projection contracts/status |

### 20.2 APIMessage 完成度

| APIMessage | 当前继承 | 是否满足最终 `plan(connection)` 目标 | 主要阻碍 |
| --- | --- | --- | --- |
| `BattleResultListAPIMessage` | `APIWithTokenMessage` + `APIWithTokenContextMessage` | 部分满足 | 仍保留 repository fallback |
| `BattleResultRecordAPIMessage` | `APIWithTokenMessage` + `APIWithTokenContextMessage` | 部分满足 | 仍保留 repository fallback |
| `BattleQueueJoinAPIMessage` | `APIWithTokenContextMessage` | 未满足 | queue state 仍在 `BattleQueueService` 内存服务中 |
| `BattleQueueStatusAPIMessage` | `APIWithTokenContextMessage` | 未满足 | ticket/room 没有 table-backed query |
| `BattleQueueLeaveAPIMessage` | `APIWithTokenContextMessage` | 未满足 | leave transition 仍修改内存 queue maps |
| `BattleRoomSnapshotAPIMessage` | `APIWithTokenContextMessage` | 未满足 | room snapshot 仍来自 queue service |
| `BattleRoomHeartbeatAPIMessage` | `APIWithTokenContextMessage` | 未满足 | heartbeat 仍更新 queue service 内存房间 |
| `BattleStateReadAPIMessage` | `APIWithTokenContextMessage` | 未满足 | battle state 仍由 `BattleStateService` lazy bootstrap/tick 推进 |
| `BattleCommandAPIMessage` | `APIWithTokenContextMessage` | 未满足 | command accept/apply 仍依赖 `BattleStateService` |

结论：

当前 API 文件位置和命名已经接近目标，但执行模型还没有完全迁移。最不应该做的是只把 trait 改成 `APIWithTokenMessage`，然后在 `plan(connection)` 里偷偷调用全局 singleton 或内存服务；那会让类型表面看起来正确，但副作用边界更差。

### 20.3 类型安全与 ADT 结构

已经明确类型化的业务概念：

| 概念 | 类型 |
| --- | --- |
| 战斗/房间/票据/玩家/英雄 ID | `BattleId`, `RoomId`, `TicketId`, `PlayerId`, `HeroId` |
| 时间与 tick | `EpochMillis`, `DurationMillis`, `ElapsedMillis`, `BattleTick`, `ClientCommandSeq` |
| 战斗数值 | `Score`, `KillCount`, `HitPoints`, `Stamina`, `AmmoCount`, `Damage`, `Radius`, `BattleWeaponHeat` |
| 地图/展示标签 | `BattleMapId`, `BattleResultLabel`, `BattleModeLabel`, `BattleMapLabel`, `BattleHighlightLine`, `BattlePlayersLine`, `BattleTimelineHint` |
| finite state | `BattleMode`, `BattlePhase`, `BattleArtifactStatus`, `MatchmakingRoomPhase`, `WeaponKind`, `ProjectileKind`, `SkillKind`, `PickupKind` |
| battle outcome | `BattleQueueLeaveOutcome`, `BattleSurvivalOutcome`, `BattleWeaponThermalState`, `BattlePlayerLifeState` |

仍然保留 primitive 的地方：

| primitive | 位置 | 当前判断 |
| --- | --- | --- |
| `String` | 展示文案，如 `finishedAtLabel`, `currentLoadout`, event message | 可以接受为 wire/display 边界，但后续可继续 value object 化 |
| `Boolean` | response wire 字段，如 `alive`, `isBot`, `resultReady`, `replayReady` | 作为前端兼容输出可以接受，但内部 domain 应继续使用 ADT |
| `Int` | `currentWeaponIndex`、部分 API helper limit load 参数 | 部分可以继续收紧，例如 weapon slot/index 已经有 `BattleWeaponSwitchIndex` |
| `Double` | vector 坐标 | 坐标向量已被 `BattleVector2` 包裹，单个 x/y 在 DTO 层可以接受 |

### 20.4 Circe 边界

当前 Circe 使用位置基本符合边界原则：

- `objects/apiTypes/...` 负责 request decoder 和 response encoder。
- `api/.../XXXAPIMessage.scala` 有 message decoder，用于把注入后的 JSON payload 解成 message。
- `database/results/BattleResultFileJsonParser.scala` 使用 Circe 处理 file storage 兼容格式。
- `database/projections/BattleReplayFramesJsonRenderer.scala` 使用 Circe 把 replay frame payload 转为 JSON 字符串。

当前不理想的地方：

- 一些 decode error 通过 `DecodingFailure.message` 字符串反推业务错误，仍有 stringly bridge。
- `BattleStateResponse` 目前包的是 `Json` payload，而不是 fully typed root response 直接由 router 编码；这是 render-facing 兼容边界，但不是最强类型结构。
- `BattleReplayFramesJsonRenderer` 位于 `database/projections`，虽然它确实服务于 replay artifact 写入，但名字带 renderer，容易和前端 render 混淆。它实际职责是 wire JSON projection，不是视觉渲染。

### 20.5 Cats Effect / 副作用边界

当前 Cats Effect 使用方式：

- `APIMessage.plan(...)` 返回 `IO[Response]`。
- API 中调用阻塞 service/repository/table 时使用 `IO.blocking`。
- `APIMessageRouter` 使用 `Resource[IO, Connection]` 包住 JDBC connection 生命周期。
- result record 写入使用 `PostgresSupport.withTransactionIO(connection)`。

当前问题：

- queue/session 的真实状态仍在内存服务中，用 `synchronized` 和 mutable maps 管理。
- `plan(connection)` 尚未成为 queue/session/command/state 的真实 transaction 边界。
- `routes` 仍要携带 `BattleAPIRuntimeContext`，说明 route 层还知道 queue/state service 的存在。

目标状态应该是：

- route 只知道 `RegisteredAPIMessage`。
- APIMessage 只通过 `Connection` 和自己的 private pure helpers 完成 use-case。
- database 子域提供 table/query/update 函数。
- 内存服务如果保留，只能是 test adapter 或明确兼容 adapter，而不是主路径。

### 20.6 Render-facing 后端输出

后端不负责 Phaser 渲染，但负责给前端渲染提供稳定 contract。

当前 render-facing 输出包括：

| 输出 | 位置 | 内容 |
| --- | --- | --- |
| 实时 state response | `api/state/BattleStateReadAPIMessage.scala` + `objects/apiTypes/state` | `mapId`, `worldSize`, `players`, `projectiles`, `projectileTerminals`, `slowFields`, `pickups`, `events`, winner |
| queue/room lobby response | `api/queue`, `api/room`, `objects/apiTypes/shared` | `participants`, `battleSession`, `bootstrap`, `mapId`, `phase`, room timing |
| replay frame JSON | `database/projections/BattleReplayFramesJsonRenderer.scala` | replay viewer 所需 heroes/projectiles/pickups/timeline |
| battle result response | `api/results`, `objects/apiTypes/results` | 战报列表、展示标签、rating、placement、loadout |

当前状态输出的好处：

- 后端输出的是权威 state，不包含 Phaser 视图对象。
- 前端可以根据 `mapId/worldSize/entities/projectiles/pickups/events` 做渲染。
- value object 在 encoder 边界降级为 JSON primitive，wire contract 稳定。

当前状态输出的风险：

- `BattleStateResponse(payload: Json)` 让 root response 的类型安全在最后一步变弱。
- event message 和 result display text 仍是字符串展示层。
- replay JSON renderer 和 state response renderer 是两套 projection，后续可能发生字段漂移。

### 20.7 依赖方向审计

当前健康的依赖方向：

- `routes/BattleRoutes.scala` 依赖 `api`、`objects/apiTypes`、少量 database service 类型。
- `api` 依赖 `objects/apiTypes`、domain objects、database service/table。
- `objects` 基本不依赖 route。
- `database` 基本不依赖 route/api。

当前不理想的依赖方向：

- `routes/BattleAPIRuntimeContext.scala` 仍依赖 `database.queue.BattleQueueService` 和 `database.session.BattleStateService`。
- queue service 依赖 session seed/lookup，session state service 又通过 room lifecycle sink 回写 queue，queue/session 之间不是完全独立。
- `database/runtime` 横向调用 `abilities`、`actors`、`combat`、`world`，这是游戏 loop 的自然聚合，但不适合强行拆成互不相干微服务。
- `api/results/BattleResultListAPIMessage` 调用 `BattleResultRecordAPIMessage.renderRecordResponse`，这是同一子域内复用，可以接受；如果以后要更严格，可提到 `objects/apiTypes/results` 的 presenter/helper。

结论：

当前不能把已有 `database/runtime`、`actors`、`combat`、`abilities`、`world` 直接机械拆成互不调用的文件夹，因为它们现在共同构成一个权威 battle loop。

但这不等于不能拆微服务。更准确的目标是：

- battle 仍是一个 cohesive bounded context。
- 如果要拆微服务，目录应该放在 `services/battle/microservices` 下。
- `services/battle/microservices` 下的每个子服务按业务能力划分，例如 queue、session、combat、world、results。
- 微服务之间不允许直接 import 对方的 database table、runtime rule、repository 或 mutable service 实现。
- 微服务之间通过 typed `APIMessagePlanner` 通信。
- `APIMessagePlanner` 的输入和输出必须来自 `objects` 或 `objects/apiTypes` 中的 ADT/value object/DTO。
- 一个 planner 可以在自己的 `plan(connection)` 内调用本服务私有 table/rules，但不能越界调用另一个微服务的内部实现。
- 如果前端需要调用某个 battle microservice，对应前端 API client/type 必须同步对齐后端 APIMessage request/response。

因此，当前阶段不应在 `database` 下继续假装拆微服务；真正的微服务化应作为后续新边界：

```text
services/battle/
  api/
  objects/
  routes/
  database/
  microservices/
    queue/
    session/
    combat/
    world/
    results/
```

推荐依赖方向：

```text
routes -> api -> microservices/* planners -> database/private rules -> objects
frontend api client -> backend APIMessage contract -> objects/apiTypes
```

禁止方向：

```text
microservices/queue -> microservices/session/internal table
microservices/session -> microservices/queue/internal mutable service
frontend hand-written type -> backend unrelated DTO
database/runtime -> api/routes
objects -> api/database/routes
```

### 20.8 下一步最小安全迁移建议

不建议下一步直接迁移 queue/session/state/command 到 `plan(connection)`，因为这会同时要求：

- 设计 queue room/ticket table。
- 设计 session/state table 或 event log。
- 重写 InMemoryBattleQueueService 和 InMemoryBattleStateService 主路径。
- 重写 route contract tests。
- 处理 battle tick 推进的 transaction/locking。

建议先做一个不会跨太多边界的小票：

`BE-BATTLE-STATE-RESPONSE-86`

目标：

- 把 `BattleStateResponse` 从 `payload: Json` 改成持有 typed `BattleStateRootResponse`。
- Encoder 在 `objects/apiTypes/state` 边界统一把 root response 编成 JSON。
- `BattleStateReadAPIMessage` 不再自己 `.asJson` 包 payload。

为什么这个票合适：

- 它不改变数据库和运行时。
- 它不改变前端 wire contract。
- 它直接提升 render-facing contract 的类型安全。
- 它符合 `objects/apiType` 负责 encoder/decoder、APIMessage 负责 plan 编排的目标。

验收：

- `BattleStateResponse` 不再持有裸 `Json`。
- `BattleStateReadAPIMessage` 返回 typed response。
- 前端收到的 state JSON 字段不变。
- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。

## 21. Objects ADT 声明清单与 API 重复声明审计

本轮按用户要求重新审计 `services/battle/objects` 里的 ADT/value object/DTO 声明，并用这些声明反查 `services/battle/api` 与 `objects/apiTypes` 里是否有重复业务概念。

### 21.1 objects 中的核心 ADT / value object 清单

`objects/BattleEnums.scala`：

- `MatchmakingRoomPhase`
- `BattleMode`
- `BattlePhase`
- `BattleArtifactStatus`
- `WeaponKind`
- `ProjectileKind`
- `SkillKind`
- `BattleCommandStatus`
- `BattleCommandReason`
- `SkillOutcomeStatus`
- `SkillOutcomeReason`
- `PickupKind`
- `ProjectileTerminalReason`
- `BattleEventKind`

`objects/BattleUseCaseCommands.scala`：

- `BattleQueueJoinCommand`
- `RealtimeRoomHeartbeatCommand`
- `BattleQueueLeaveOutcome`
- `BattleResultRecordCommand`
- `BattleResultListQuery`

`objects/BattleAPIRequestError.scala`：

- `BattleCommandRequestField`
- `BattleAPIRequestError`

`objects/core/BattleIds.scala`：

- `TicketId`
- `QueueRequestId`
- `RoomId`
- `BattleId`
- `PlayerId`
- `HeroId`
- `ProjectileId`
- `SlowFieldId`
- `PickupId`
- `BattleEventId`
- `BattleResultId`

`objects/core/BattleScalars.scala`：

- `EpochMillis`
- `DurationMillis`
- `ElapsedMillis`
- `BattleTick`
- `ClientCommandSeq`
- `SeatIndex`
- `SpawnPointIndex`
- `BattleCapacity`
- `Rating`
- `RatingDelta`
- `BattleResultListLimit`
- `BattleMapId`
- `BattleAvatarKey`
- `BattleSkinKey`
- `BattleResultLabel`
- `BattleModeLabel`
- `BattleMapLabel`
- `BattleHighlightLine`
- `BattlePlayersLine`
- `BattleTimelineHint`
- `BattlePlacement`
- `Score`
- `KillCount`
- `HitPoints`
- `Stamina`
- `AmmoCount`
- `CooldownMillis`
- `FacingRadians`
- `Radius`
- `Damage`
- `BattleWeaponHeat`
- `BattleWeaponHeatRatePerSecond`
- `BattleVector2`

`objects/core/BattleAggregateState.scala`：

- `BattleAggregateState`

`objects/command/BattleCommandModels.scala`：

- `BattleCommandVector`
- `BattleCommandRequest`
- `BattleCommandSkillOutcome`
- `BattleCommandAccepted`

`objects/event/BattleEventState.scala`：

- `BattleEventParticipant`
- `BattleEventState`

`objects/pickup`：

- `BattlePickupAvailability`
- `BattlePickupDefinition`
- `BattlePickupState`

`objects/player`：

- `BattleParticipantKind`
- `BattlePlayerLifeState`
- `BattleSurvivalOutcome`
- `BattlePlayerSkillState`
- `BattlePlayerState`

`objects/projectile`：

- `BattleProjectileState`
- `BattleProjectileTerminalState`

`objects/queue`：

- `BattleQueueParticipant`
- `BattleSessionRosterEntry`
- `BattleSessionBootstrapSeat`
- `BattleSessionBootstrap`
- `BattleSessionDescriptor`
- `BattleQueueSnapshot`
- `RealtimeRoomSnapshot`

`objects/replay`：

- `BattleReplayHeroLifeState`
- `BattleReplayHeroFrameState`
- `BattleReplayProjectileFrameState`
- `BattleReplayPickupFrameState`
- `BattleReplayFrameState`

`objects/result`：

- `BattleResultRecord`
- `BattleFinishProjectionOutcome`
- `BattleFinishProjectionStatus`
- `BattleFinishProjector`
- `NoopBattleFinishProjector`

`objects/skill`：

- `BattleCommandSkillIntents`
- `BattleSlowFieldState`

`objects/weapon`：

- `BattleWeaponState`
- `BattleWeaponSwitchDirection`
- `BattleWeaponSwitchIndex`
- `BattleWeaponThermalState`

### 21.2 objects/apiTypes 中的 wire DTO 清单

这些声明属于 wire contract DTO，允许存在，但不能重复表达已经有的 domain ADT 业务状态：

- `BattleCommandAPIRequest`
- `BattleCommandAcceptedResponse`
- `BattleQueueJoinRequest`
- `BattleQueueStatusRequest`
- `BattleQueueSnapshotResponse`
- `BattleQueueLeaveRequest`
- `BattleQueueLeaveResponse`
- `BattleRoomSnapshotRequest`
- `RealtimeRoomSnapshotResponse`
- `BattleRoomHeartbeatRequest`
- `BattleResultListAPIRequest`
- `BattleResultRecordAPIRequest`
- `BattleResultRecordResponse`
- `BattleResultListResponse`
- `BattleStateReadAPIRequest`
- `BattleStateResponse`
- `BattleStateRootResponse`
- `BattleStateVectorResponse`
- `BattleStateWeaponResponse`
- `BattleStateSkillResponse`
- `BattleStatePlayerResponse`
- `BattleStateProjectileResponse`
- `BattleStateProjectileTerminalResponse`
- `BattleStateSlowFieldResponse`
- `BattleStateEventParticipantResponse`
- `BattleStateEventResponse`
- `BattleStatePickupResponse`
- `BattleQueueParticipantResponse`
- `BattleSessionRosterEntryResponse`
- `BattleSessionBootstrapSeatResponse`
- `BattleSessionBootstrapResponse`
- `BattleSessionDescriptorResponse`

### 21.3 本轮发现并删除的重复声明

发现的高置信度重复：

- `objects.command.BattleCommandSkillOutcome`
- `objects/apiTypes/command.BattleCommandSkillOutcomeResponse`

二者表达的是同一个业务概念：一次技能命令的执行结果。

旧结构：

- domain 层有 `BattleCommandSkillOutcome(action, outcomeStatus, reason)`。
- API response 层又声明 `BattleCommandSkillOutcomeResponse(action, status, reason)`。
- `BattleCommandAPIMessage` 还要手动 `buildOutcomeResponse` 做重复映射。

本轮修改后：

- 删除重复的 `BattleCommandSkillOutcomeResponse` case class。
- `BattleCommandAcceptedResponse.outcomes` 直接使用 `Vector[BattleCommandSkillOutcome]`。
- 在 `BattleCommandAcceptedResponse` companion 中给 domain `BattleCommandSkillOutcome` 提供 wire `Encoder`。
- wire JSON 字段仍是 `action`、`status`、`reason`，前端 contract 不变。
- `BattleCommandAPIMessage` 删除 `buildOutcomeResponse`，直接传 `accepted.outcomes`。

类型安全收益：

- 技能结果不再在 API DTO 层重复声明一套“几乎相同但字段名略不同”的类型。
- 业务状态只由 `objects.command.BattleCommandSkillOutcome` 表达。
- API 层只负责把 domain ADT 编码成 wire 字段名。

验证：

- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。
- 源码检索确认 `BattleCommandSkillOutcomeResponse` 已无残留引用；报告中的出现只用于记录本轮删除的历史重复类型。

### 21.4 剩余重复风险点

下列 DTO 与 domain model 结构接近，但目前仍可能是必要 wire DTO，不能盲删：

- `BattleQueueParticipantResponse` vs `BattleQueueParticipant`
- `BattleSessionRosterEntryResponse` vs `BattleSessionRosterEntry`
- `BattleSessionBootstrapSeatResponse` vs `BattleSessionBootstrapSeat`
- `RealtimeRoomSnapshotResponse` vs `RealtimeRoomSnapshot`
- `BattleResultRecordResponse` vs `BattleResultRecord`
- `BattleStatePlayerResponse` vs `BattlePlayerState`
- `BattleStateProjectileResponse` vs `BattleProjectileState`
- `BattleStateEventResponse` vs `BattleEventState`
- `BattleStatePickupResponse` vs `BattlePickupState`

当前判断：

- 如果 DTO 只是字段裁剪、字段重命名、wire enum 编码、隐藏内部状态，它可以保留。
- 如果 DTO 与 domain object 一一同构，只是名字不同，则应该删除 DTO，改为给 domain object 提供边界 encoder。
- 以后每轮只能处理一个高置信度重复，避免误删前端 contract 需要的 wire shape。

## 22. 本轮补充：state response 从裸 Json 收紧为 typed root response

本轮处理 render-facing contract 的一个类型安全缺口。

旧结构：

- `BattleStateResponse` 持有 `payload: Json`。
- `BattleStateReadAPIMessage` 先构造 `BattleStateRootResponse`，再调用 `.asJson` 降级为 Circe `Json`。
- 这意味着 APIMessage 和 response wrapper 之间的最后一步已经丢失 typed root response 信息。

新结构：

- `BattleStateResponse` 持有 `root: BattleStateRootResponse`。
- `BattleStateResponse` 的 companion 负责把 `root.asJson` 编码成原来的 JSON 根对象。
- `BattleStateReadAPIMessage` 只返回 typed `BattleStateResponse(renderRootResponse(state))`。
- 前端 wire JSON shape 不变，仍然是原来的根对象字段，而不是多包一层 `root`。

类型安全收益：

- state/read API 的 response contract 现在在 Scala 类型上保留到 encoder 边界。
- `BattleStateReadAPIMessage` 不再手动持有或传递裸 `Json` response。
- render-facing 字段仍集中在 `objects/apiTypes/state` 的 typed DTO 和 encoder 中。

验证：

- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。
- 残留检索未发现 `BattleStateResponse(payload: Json)` 或 `renderRootResponse(state).asJson`。

## 23. 本轮补充：command accepted response 去重复声明

本轮继续执行 objects ADT 单一来源检查，处理 command API 中的第二个高置信度重复。

发现的问题：

- `objects.command.BattleCommandAccepted` 已经声明了命令接收结果的完整业务对象。
- API 层原本又有 `BattleCommandAcceptedResponse` case class，字段与 domain `BattleCommandAccepted` 完全同构。
- 这种重复会让 command accepted contract 未来可能出现字段名、optional、enum 表达漂移。

本轮修改后的结构：

- 删除重复的 `BattleCommandAcceptedResponse` case class。
- 保留 `object BattleCommandAcceptedResponse` 作为 wire encoder namespace，只负责把 domain `BattleCommandAccepted` 编码为前端需要的 JSON。
- `BattleCommandAPIMessage` 的 response 类型直接改为 `BattleCommandAccepted`。
- `BattleRoutes` 的 API 注册也直接使用 `BattleCommandAccepted`，并从 `objects/apiTypes/command` 引入对应 encoder。

类型安全收益：

- 命令接收结果只由 `objects.command.BattleCommandAccepted` 一个 ADT/业务对象表达。
- API 层不再复制业务对象结构，只保留边界编码职责。
- wire JSON shape 保持不变，前端 contract 不受影响。

验证：

- `rg -n "BattleCommandAcceptedResponse\\(" backend/src/main/scala backend/src/test/scala` 未发现构造调用残留。
- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。

## 24. 本轮补充：queue participant response 去重复声明

本轮继续执行 objects ADT 单一来源检查，处理 queue/room 共享 response 中的高置信度重复。

发现的问题：

- `objects.queue.BattleQueueParticipant` 已经声明了等待区参与者的完整业务对象。
- `objects/apiTypes/shared.BattleQueueParticipantResponse` 又声明了一份字段完全相同的 response case class。
- 两者字段均为 `playerId`、`handle`、`joinedAt`、`lastSeen`、`rating`、`avatar`、`skin`，没有 wire 裁剪、重命名或隐藏内部状态。
- `BattleQueueStatusAPIMessage` 和 `BattleRoomSnapshotAPIMessage` 还要手动把 domain participant 复制成 response participant。

本轮修改后的结构：

- 删除重复的 `BattleQueueParticipantResponse` case class。
- 保留 `object BattleQueueParticipantResponse` 作为 wire encoder namespace，只负责给 domain `BattleQueueParticipant` 提供 encoder。
- `BattleQueueSnapshotResponse.participants` 改为 `Vector[BattleQueueParticipant]`。
- `RealtimeRoomSnapshotResponse.participants` 改为 `Vector[BattleQueueParticipant]`。
- queue status 和 room snapshot 的 response 构造直接使用 `snapshot.participants`，不再手写字段复制。

类型安全收益：

- 等待区参与者只由 `objects.queue.BattleQueueParticipant` 一个业务对象表达。
- API DTO 不再复制同构业务结构，降低字段漂移风险。
- wire JSON shape 保持不变，仍然输出 `playerId`、`handle`、`joinedAt`、`lastSeen`、`rating`、`avatar`、`skin`。

保留项：

- `BattleSessionBootstrapSeatResponse` 暂不删除，因为它把 domain 的 `participantKind` 转成 wire 的 `isBot`，不是完全同构。
- `BattleSessionDescriptorResponse` 暂不删除，因为它额外输出 `modeLabel`、`mapId`、`mapLabel` 等前端展示字段。

验证：

- `rg -n "BattleQueueParticipantResponse\\(|BattleQueueParticipantResponse\\b" backend/src/main/scala/services/battle backend/src/test/scala` 确认只剩 encoder namespace 和 given import。
- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。

## 25. 本轮补充：session roster entry response 去重复声明

本轮继续处理 queue/session 共享 response 中的高置信度重复。

发现的问题：

- `objects.queue.BattleSessionRosterEntry` 已经声明了战局 roster 成员的完整业务对象。
- `objects/apiTypes/shared.BattleSessionRosterEntryResponse` 又声明了一份字段完全相同的 response case class。
- 两者字段均为 `seat`、`playerId`、`handle`、`joinedAt`、`rating`、`avatar`、`skin`，没有 wire 裁剪、字段重命名或隐藏内部状态。
- queue status 和 room snapshot 在构造 session descriptor response 时还要手动 map roster，属于无意义字段复制。

本轮修改后的结构：

- 删除重复的 `BattleSessionRosterEntryResponse` case class。
- 保留 `object BattleSessionRosterEntryResponse` 作为 wire encoder namespace，只负责给 domain `BattleSessionRosterEntry` 提供 encoder。
- `BattleSessionDescriptorResponse.roster` 改为 `Vector[BattleSessionRosterEntry]`。
- queue status 和 room snapshot 的 session descriptor 构造直接使用 `session.roster`。

类型安全收益：

- 战局 roster 成员只由 `objects.queue.BattleSessionRosterEntry` 一个业务对象表达。
- API DTO 不再复制同构结构，降低字段漂移风险。
- wire JSON shape 保持不变，仍然输出 `seat`、`playerId`、`handle`、`joinedAt`、`rating`、`avatar`、`skin`。

验证：

- `rg -n "BattleSessionRosterEntryResponse\\(|BattleSessionRosterEntryResponse\\b|renderRosterEntryResponse|roster = session\\.roster\\.map" backend/src/main/scala/services/battle backend/src/test/scala` 确认只剩 encoder namespace 和 given import。
- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。

## 26. 本轮补充：session bootstrap response 去重复声明

本轮继续处理 session bootstrap response 的单一类型来源问题。

发现的问题：

- `objects.queue.BattleSessionBootstrap` 已经声明了战局启动 seat 集合。
- `objects.queue.BattleSessionBootstrapSeat` 已经声明了每个 seat 的完整业务对象，其中 `participantKind` 是真实领域状态，`isBot` 是纯派生方法。
- `objects/apiTypes/shared` 里额外声明了 `BattleSessionBootstrapResponse` 和 `BattleSessionBootstrapSeatResponse` 两个 response case class。
- API 层需要手动 `renderBootstrapResponse` / `renderBootstrapSeatResponse` 复制字段，其中 `isBot` 来自 domain 方法。

本轮修改后的结构：

- 删除重复的 `BattleSessionBootstrapResponse` case class。
- 删除重复的 `BattleSessionBootstrapSeatResponse` case class。
- 保留同名 companion object 作为 wire encoder namespace。
- `BattleSessionBootstrapSeatResponse` 现在提供 `Encoder[BattleSessionBootstrapSeat]`，继续把 domain `participantKind` 通过 `seat.isBot` 输出为 wire `isBot`。
- `BattleSessionBootstrapResponse` 现在提供 `Encoder[BattleSessionBootstrap]`。
- `BattleSessionDescriptorResponse.bootstrap` 改为 `Option[BattleSessionBootstrap]`。
- queue status 和 room snapshot 直接使用 `session.bootstrap`，不再手写 bootstrap 字段复制。

类型安全收益：

- 战局启动 seat 集合与 seat 成员只由 `objects.queue` 中的 domain ADT 表达。
- API 层不再复制可由 domain 对象表达的结构。
- `isBot` 仍然是前端 wire contract 字段，但它是由 domain `BattleSessionBootstrapSeat.isBot` 纯方法派生，不引入第二套业务状态。
- wire JSON shape 保持不变：`bootstrap.seats[]` 仍然包含 `seat`、`playerId`、`heroId`、`handle`、`displayName`、`joinedAt`、`isBot`、`spawnPointIndex`、`rating`、`avatar`、`skin`。

验证：

- `rg -n "BattleSessionBootstrap(Response|SeatResponse)\\(|renderBootstrap(Response|SeatResponse)|bootstrap = session\\.bootstrap\\.map" backend/src/main/scala/services/battle backend/src/test/scala` 未发现 response 构造或手写 bootstrap map 残留。
- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。

## 27. 本轮补充：battle result record response 去重复声明

本轮处理 results API 中的 response DTO 重复问题。

发现的问题：

- `objects.result.BattleResultRecord` 已经声明了战斗结算记录的完整业务对象。
- `objects/apiTypes/results.BattleResultRecordResponse` 又声明了一份 response case class。
- `BattleResultRecordResponse` 的差异字段并不是独立业务状态，而是 domain 可纯派生的 wire 投影：
  - `resultId` 来自 `record.resultId`。
  - `aliveAtEnd` 来自 `record.aliveAtEnd`。
- API 层原本需要 `renderRecordResponse` 手动复制 20 个字段，增加字段漂移风险。

本轮修改后的结构：

- 删除重复的 `BattleResultRecordResponse` case class。
- 保留 `object BattleResultRecordResponse` 作为 wire encoder namespace，只负责给 domain `BattleResultRecord` 提供 encoder。
- `BattleResultRecordAPIMessage` 的 response 类型直接改为 `BattleResultRecord`。
- `BattleResultListResponse.results` 改为 `Vector[BattleResultRecord]`。
- `BattleResultListAPIMessage` 不再手动 map `renderRecordResponse`，直接返回 domain record 集合。
- `BattleRoutes` 注册 result record API 时使用 `BattleResultRecord`，并显式引入 result record encoder。

类型安全收益：

- 战斗结算记录只由 `objects.result.BattleResultRecord` 一个业务对象表达。
- `resultId` 和 `aliveAtEnd` 保持为 domain 对象的纯派生投影，而不是 API 层复制状态。
- result API 不再手写 20 字段映射，降低字段名、optional、值对象拆箱规则漂移风险。
- wire JSON shape 保持不变，仍然输出 `resultId`、`battleId`、`handle`、`displayName`、`finishedAt`、`aliveAtEnd` 等原字段。

验证：

- `rg -n "BattleResultRecordResponse\\(|BattleResultRecordResponse\\b|renderRecordResponse|BattleResultListResponse\\(records\\.map" backend/src/main/scala/services/battle backend/src/test/scala` 确认只剩 encoder namespace 和 given import。
- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。

## 28. 本轮补充：state vector response 去重复声明

本轮开始审计 state response。由于 state response 是前端渲染直接消费的投影层，不能盲删 player/projectile/pickup 等复杂 DTO；本轮只处理一个完全同构的基础值对象。

发现的问题：

- `objects.core.BattleVector2` 已经声明了战斗世界坐标/向量值对象，字段为 `x`、`y`。
- `objects/apiTypes/state.BattleStateVectorResponse` 又声明了一份完全相同的 response case class。
- `BattleStateReadAPIMessage` 因此需要到处调用 `renderVectorResponse`，把 `BattleVector2` 手动复制成 `BattleStateVectorResponse`。

本轮修改后的结构：

- 删除重复的 `BattleStateVectorResponse` case class。
- 保留 `object BattleStateVectorResponse` 作为 wire encoder namespace，只负责给 domain `BattleVector2` 提供 encoder。
- `BattleStateRootResponse.worldSize` 改为 `BattleVector2`。
- player/projectile/projectile terminal/slow field/pickup response 中的坐标字段改为 `BattleVector2`。
- `BattleStateReadAPIMessage` 删除 `renderVectorResponse`，直接把 domain vector 放入 response。

类型安全收益：

- 战斗坐标/向量只由 `objects.core.BattleVector2` 一个值对象表达。
- state API 不再复制坐标结构，也不再手写 `x/y` 映射函数。
- wire JSON shape 保持不变，所有向量仍然输出 `{ "x": number, "y": number }`。
- 后续如继续收敛 projectile/player response，可以直接复用同一个 vector encoder。

验证：

- `rg -n "BattleStateVectorResponse\\(|renderVectorResponse" backend/src/main/scala/services/battle backend/src/test/scala` 未发现 response 构造或手写 vector renderer 残留。
- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。

## 29. 本轮补充：state projectile response 去重复声明

本轮继续审计 state response 中的 projectile 投影。

发现的问题：

- `objects.projectile.BattleProjectileState` 已经声明了飞行中 projectile 的完整业务对象。
- `objects.projectile.BattleProjectileTerminalState` 已经声明了 projectile 终止事件的完整业务对象。
- `objects/apiTypes/state` 中又声明了 `BattleStateProjectileResponse` 和 `BattleStateProjectileTerminalResponse` 两个 response case class。
- 两组 response 与 domain state 基本同构，唯一明显 wire 差异是 domain 字段 `projectileKind` 输出为前端字段 `kind`。
- `BattleStateReadAPIMessage` 因此需要手写 `renderProjectileResponse` 和 `renderProjectileTerminalResponse`，复制 projectile 字段。

本轮修改后的结构：

- 删除重复的 `BattleStateProjectileResponse` case class。
- 删除重复的 `BattleStateProjectileTerminalResponse` case class。
- 保留同名 object 作为 wire encoder namespace。
- `BattleStateProjectileResponse` 提供 `Encoder[BattleProjectileState]`，继续把 domain `projectileKind` 编码为 wire `kind`。
- `BattleStateProjectileTerminalResponse` 提供 `Encoder[BattleProjectileTerminalState]`，继续把 domain `projectileKind` 编码为 wire `kind`。
- `BattleStateRootResponse.projectiles` 改为 `Vector[BattleProjectileState]`。
- `BattleStateRootResponse.projectileTerminals` 改为 `Vector[BattleProjectileTerminalState]`。
- `BattleStateReadAPIMessage` 直接使用 `state.projectiles` 和 `state.projectileTerminals`。

类型安全收益：

- projectile 运行状态只由 `objects.projectile` 中的 domain ADT 表达。
- API 层不再复制 projectile 状态结构，减少字段漂移风险。
- wire 字段名兼容仍由 encoder 边界负责，前端仍收到 `kind` 而不是 `projectileKind`。
- 删除了 state read 的 projectile 手写映射函数，降低 route/APIMessage 层渲染投影复杂度。

验证：

- `rg -n "BattleStateProjectile(Response|TerminalResponse)\\(|renderProjectile(Response|TerminalResponse)|projectiles = state\\.projectiles\\.map|projectileTerminals = state\\.projectileTerminals\\.map" backend/src/main/scala/services/battle backend/src/test/scala` 确认无 response 构造或手写 projectile renderer 残留。
- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。

## 30. 本轮补充：state slow field response 去重复声明

本轮继续审计 state response 中的 slow field 投影。

发现的问题：

- `objects.skill.BattleSlowFieldState` 已经声明了减速场运行状态，字段为 `fieldId`、`ownerPlayerId`、`ownerHeroId`、`position`、`radius`、`ttlMs`、`durationMs`。
- `objects/apiTypes/state.BattleStateSlowFieldResponse` 又声明了一份字段完全相同的 response case class。
- `BattleStateReadAPIMessage` 因此需要手写 `renderSlowFieldResponse` 复制字段。

本轮修改后的结构：

- 删除重复的 `BattleStateSlowFieldResponse` case class。
- 保留 `object BattleStateSlowFieldResponse` 作为 wire encoder namespace，只负责给 domain `BattleSlowFieldState` 提供 encoder。
- `BattleStateRootResponse.slowFields` 改为 `Vector[BattleSlowFieldState]`。
- `BattleStateReadAPIMessage` 直接使用 `state.slowFields`，不再手写 slow field renderer。

类型安全收益：

- 减速场运行状态只由 `objects.skill.BattleSlowFieldState` 一个业务对象表达。
- API 层不再复制 slow field 结构，降低字段漂移风险。
- wire JSON shape 保持不变，仍然输出 `fieldId`、`ownerPlayerId`、`ownerHeroId`、`position`、`radius`、`ttlMs`、`durationMs`。

验证：

- `rg -n "BattleStateSlowFieldResponse\\(|renderSlowFieldResponse|slowFields = state\\.slowFields\\.map" backend/src/main/scala/services/battle backend/src/test/scala` 未发现 response 构造或手写 slow field renderer 残留。
- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。

## 31. 本轮补充：state pickup response 去重复声明

本轮继续审计 state response 中的 pickup 投影。

发现的问题：

- `objects.pickup.BattlePickupState` 已经声明了拾取物运行状态。
- `objects/apiTypes/state.BattleStatePickupResponse` 又声明了一份 response case class。
- response 中的 `kind` 对应 domain 的 `pickupKind`，这是 wire 字段名投影。
- response 中的 `available` 和 `respawnMs` 不是独立业务状态，而是 domain `BattlePickupState.available` 与 `BattlePickupState.respawnMs` 的纯派生字段。
- `BattleStateReadAPIMessage` 因此需要手写 `renderPickupResponse` 复制字段。

本轮修改后的结构：

- 删除重复的 `BattleStatePickupResponse` case class。
- 保留 `object BattleStatePickupResponse` 作为 wire encoder namespace。
- `BattleStatePickupResponse` 提供 `Encoder[BattlePickupState]`，继续输出前端需要的 `kind`、`available`、`respawnMs` 字段。
- `BattleStateRootResponse.pickups` 改为 `Vector[BattlePickupState]`。
- `BattleStateReadAPIMessage` 直接使用 `state.pickups`。

类型安全收益：

- 拾取物运行状态只由 `objects.pickup.BattlePickupState` 一个业务对象表达。
- `available` 和 `respawnMs` 保持由 domain ADT 纯派生，不在 API 层复制状态。
- wire 字段兼容仍由 encoder 边界负责，前端仍收到 `kind` 而不是 `pickupKind`。
- 删除 state read 中的 pickup 手写映射函数，降低 response 字段漂移风险。

验证：

- `rg -n "BattleStatePickupResponse\\(|renderPickupResponse|pickups = state\\.pickups\\.map" backend/src/main/scala/services/battle/api backend/src/main/scala/services/battle/objects/apiTypes backend/src/test/scala` 未发现 state API response 构造或手写 pickup renderer 残留。
- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。

备注：

- `database/abilities/BattlePickupRules` 中的 `state.pickups.map` 是业务状态更新逻辑。
- `database/projections/BattleReplayFramesJsonRenderer` 中的 `state.pickups.map` 是 replay 投影逻辑。
- 这两处不是 state API response DTO 重复映射，本轮不处理。

## 32. 本轮补充：state weapon response 去重复声明

本轮继续审计 state response 中的 weapon 投影。

发现的问题：

- `objects.weapon.BattleWeaponState` 已经声明了武器运行状态。
- `objects/apiTypes/state.BattleStateWeaponResponse` 又声明了一份 response case class。
- response 中的 `overheated` 和 `overheatRemainingMs` 不是独立业务状态，而是 domain `BattleWeaponState.overheated` 与 `BattleWeaponState.overheatRemainingMs` 的纯派生字段。
- `BattleStateReadAPIMessage` 因此需要手写 `renderWeaponResponse` 复制字段。

本轮修改后的结构：

- 删除重复的 `BattleStateWeaponResponse` case class。
- 保留 `object BattleStateWeaponResponse` 作为 wire encoder namespace。
- `BattleStateWeaponResponse` 提供 `Encoder[BattleWeaponState]`，继续输出前端需要的 `overheated`、`overheatRemainingMs` 字段。
- `BattleStatePlayerResponse.weapons` 改为 `Vector[BattleWeaponState]`。
- `BattleStateReadAPIMessage` 直接使用 `player.weapons`。

类型安全收益：

- 武器运行状态只由 `objects.weapon.BattleWeaponState` 一个业务对象表达。
- 过热状态仍由 domain `BattleWeaponThermalState` ADT 管理，API encoder 只做 wire 投影。
- 删除 state read 中的 weapon 手写映射函数，降低 response 字段漂移风险。
- wire JSON shape 保持不变，`weapons[]` 仍然输出 `weaponKind`、`ammoInMagazine`、`magazineSize`、`reserveAmmo`、`fireCooldownMs`、`reloadRemainingMs`、`heat`、`overheated`、`overheatRemainingMs`。

验证：

- `rg -n "BattleStateWeaponResponse\\(|renderWeaponResponse|weapons = player\\.weapons\\.map" backend/src/main/scala/services/battle/api backend/src/main/scala/services/battle/objects/apiTypes backend/src/test/scala` 未发现 state API response 构造或手写 weapon renderer 残留。
- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。

备注：

- `database/actors/BattlePlayerLifecycleRules` 和 `database/combat/BattleWeaponRules` 中的 `weapons.map` 是业务状态更新逻辑，不是 API response DTO 重复映射。

## 33. 本轮补充：state skill response 去重复声明

本轮继续审计 state response 中的 skill 投影。

发现的问题：

- `objects.player.BattlePlayerSkillState` 已经声明了玩家技能运行状态。
- `objects/apiTypes/state.BattleStateSkillResponse` 又声明了一份 response case class。
- response 中的 `kind` 对应 domain 的 `skillKind`，这是 wire 字段名投影。
- `BattleStateReadAPIMessage` 因此需要手写 `renderSkillResponse` 复制字段。

本轮修改后的结构：

- 删除重复的 `BattleStateSkillResponse` case class。
- 保留 `object BattleStateSkillResponse` 作为 wire encoder namespace。
- `BattleStateSkillResponse` 提供 `Encoder[BattlePlayerSkillState]`，继续输出前端需要的 `kind`、`cooldownMs`、`activeMs` 字段。
- `BattleStatePlayerResponse.skills` 改为 `Vector[BattlePlayerSkillState]`。
- `BattleStateReadAPIMessage` 直接使用 `player.skills`。

类型安全收益：

- 玩家技能运行状态只由 `objects.player.BattlePlayerSkillState` 一个业务对象表达。
- API 层不再复制 skill 结构，降低字段漂移风险。
- wire 字段兼容仍由 encoder 边界负责，前端仍收到 `kind` 而不是 `skillKind`。
- 删除 state read 中的 skill 手写映射函数。

验证：

- `rg -n "BattleStateSkillResponse\\(|renderSkillResponse|skills = player\\.skills\\.map" backend/src/main/scala/services/battle/api backend/src/main/scala/services/battle/objects/apiTypes backend/src/test/scala` 未发现 state API response 构造或手写 skill renderer 残留。
- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。

备注：

- `database/actors/BattlePlayerRuntimeRules` 和 `database/actors/BattlePlayerLifecycleRules` 中的 `skills.map` 是业务状态推进逻辑，不是 API response DTO 重复映射。

## 34. 本轮补充：state event response 去重复声明

本轮继续审计 state response 中的 event 投影。

发现的问题：

- `objects.event.BattleEventParticipant` 已经声明了战斗事件参与者。
- `objects.event.BattleEventState` 已经声明了战斗事件状态。
- `objects/apiTypes/state` 中又声明了 `BattleStateEventParticipantResponse` 和 `BattleStateEventResponse` 两个 response case class。
- event response 中的 `type` 和 `kind` 不是两份独立业务状态，而是同一个 domain `eventKind` 的 wire 兼容双字段投影。
- `BattleStateReadAPIMessage` 因此需要手写 `renderEventParticipantResponse` 与 `renderEventResponse`。

本轮修改后的结构：

- 删除重复的 `BattleStateEventParticipantResponse` case class。
- 删除重复的 `BattleStateEventResponse` case class。
- 保留同名 object 作为 wire encoder namespace。
- `BattleStateEventParticipantResponse` 提供 `Encoder[BattleEventParticipant]`。
- `BattleStateEventResponse` 提供 `Encoder[BattleEventState]`，继续把 domain `eventKind` 同时输出为 wire `type` 和 `kind`。
- `BattleStateRootResponse.events` 改为 `Vector[BattleEventState]`。
- `BattleStateReadAPIMessage` 直接使用 `state.events`。

类型安全收益：

- 战斗事件状态只由 `objects.event` 中的 domain ADT 表达。
- `type/kind` 兼容字段由 encoder 边界统一处理，不再在 APIMessage 层复制状态。
- 删除 state read 中的 event 手写映射函数，降低字段漂移风险。
- wire JSON shape 保持不变，前端仍收到 `eventId`、`type`、`kind`、`elapsedMs`、`message`、`source`、`target`。

验证：

- `rg -n "BattleStateEvent(Participant)?Response\\(|renderEvent(Participant)?Response|events = state\\.events\\.map" backend/src/main/scala/services/battle/api backend/src/main/scala/services/battle/objects/apiTypes backend/src/test/scala` 未发现 state API response 构造或手写 event renderer 残留。
- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。

## 35. 本轮补充：state player response 去重复声明

本轮处理 state response 中最大的一块 player 投影。

发现的问题：

- `objects.player.BattlePlayerState` 已经声明了玩家运行状态。
- `objects/apiTypes/state.BattleStatePlayerResponse` 又声明了一份大型 response case class。
- response 中多数字段直接复制自 domain player。
- response 中的 `isBot`、`alive`、`eliminatedAtMs`、`respawnMs` 是 domain 纯派生字段。
- response 中的 `ammoInMagazine`、`magazineSize`、`reserveAmmo`、`fireCooldownMs`、`reloadRemainingMs`、`heat`、`overheated`、`overheatRemainingMs` 是当前武器的 wire 展开字段。
- `BattleStateReadAPIMessage` 因此需要手写 `renderPlayerResponse`，并在 APIMessage 层管理当前武器 fallback。

本轮修改后的结构：

- 删除重复的 `BattleStatePlayerResponse` case class。
- 保留 `object BattleStatePlayerResponse` 作为 wire encoder namespace。
- `BattleStatePlayerResponse` 提供 `Encoder[BattlePlayerState]`。
- `BattleStateRootResponse.players` 改为 `Vector[BattlePlayerState]`。
- `BattleStateReadAPIMessage` 直接使用 `state.players`。
- 当前武器展开逻辑移动到 `BattleStatePlayerResponse` encoder 内部：
  - 当前武器存在时使用该武器字段。
  - 当前武器缺失时维持原兼容 fallback：ammo/magazine/cooldown/heat 为 0，overheated 为 false。

类型安全收益：

- 玩家运行状态只由 `objects.player.BattlePlayerState` 一个业务对象表达。
- API 层不再复制大型 player response 结构，字段漂移风险显著降低。
- `participantKind`、`lifeState`、`BattleWeaponThermalState` 等 ADT 仍由 domain 管理，wire 的 `isBot`、`alive`、`respawnMs`、`overheated` 只在 encoder 边界投影。
- `BattleStateReadAPIMessage` 现在只负责读取 state 和组装 root response，不再负责 player 字段级渲染。
- wire JSON shape 保持不变，前端仍收到原来的 player 字段。

验证：

- `rg -n "BattleStatePlayerResponse\\(|renderPlayerResponse|players = state\\.players\\.map" backend/src/main/scala/services/battle/api backend/src/main/scala/services/battle/objects/apiTypes backend/src/test/scala` 未发现 state API response 构造或手写 player renderer 残留。
- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。

备注：

- `database/runtime/BattleAggregateUpdateRules` 中的 `state.players.map` 是业务状态替换逻辑，不是 API response DTO 重复映射。

## 36. 本轮补充：queue/room snapshot response 去重复声明

本轮回到 queue/room lobby snapshot 边界，处理等待区和房间快照的 response DTO 重复。

发现的问题：

- `objects.queue.BattleQueueSnapshot` 已经声明了排队/等待区快照。
- `objects.queue.RealtimeRoomSnapshot` 已经声明了实时房间快照。
- `objects.queue.BattleSessionDescriptor` 已经声明了战局 session 描述。
- `objects/apiTypes/queue.BattleQueueSnapshotResponse`、`objects/apiTypes/room.RealtimeRoomSnapshotResponse`、`objects/apiTypes/shared.BattleSessionDescriptorResponse` 又声明了对应 response case class。
- 这些 response 的额外字段 `modeLabel`、`mapId`、`mapLabel` 是由 domain `battleMode` 可纯派生的前端展示字段，不是独立业务状态。
- queue/room APIMessage 因此需要手写 `renderSnapshotResponse` 和 `renderSessionDescriptorResponse`。

本轮修改后的结构：

- 删除重复的 `BattleQueueSnapshotResponse` case class。
- 删除重复的 `RealtimeRoomSnapshotResponse` case class。
- 删除重复的 `BattleSessionDescriptorResponse` case class。
- 保留同名 object 作为 wire encoder namespace。
- `BattleQueueSnapshotResponse` 提供 `Encoder[BattleQueueSnapshot]`，继续输出 `modeId`、`modeLabel`、`mapId`、`mapLabel`。
- `RealtimeRoomSnapshotResponse` 提供 `Encoder[RealtimeRoomSnapshot]`，继续输出 `modeId`、`modeLabel`、`mapId`、`mapLabel`。
- `BattleSessionDescriptorResponse` 提供 `Encoder[BattleSessionDescriptor]`。
- `BattleQueueJoinAPIMessage` 和 `BattleQueueStatusAPIMessage` 直接返回 `BattleQueueSnapshot`。
- `BattleRoomSnapshotAPIMessage` 和 `BattleRoomHeartbeatAPIMessage` 直接返回 `RealtimeRoomSnapshot`。
- `BattleRoutes` 注册 queue/room API 时使用 domain snapshot 类型，并显式引入对应 encoder。

类型安全收益：

- queue/room/session snapshot 只由 `objects.queue` 中的 domain ADT 表达。
- 展示字段由 encoder 边界统一投影，不再由 APIMessage 手写复制。
- 删除 queue/room APIMessage 中的 snapshot renderer，APIMessage 更接近“读取/调用 service -> 返回 domain result”的结构。
- wire JSON shape 保持不变，前端仍收到 `modeId`、`modeLabel`、`mapId`、`mapLabel`、`battleSession` 等原字段。

验证：

- `rg -n "BattleQueueSnapshotResponse\\(|RealtimeRoomSnapshotResponse\\(|BattleSessionDescriptorResponse\\(|renderSnapshotResponse|renderSessionDescriptorResponse" backend/src/main/scala/services/battle backend/src/test/scala` 未发现 response 构造或手写 snapshot renderer 残留。
- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。

## 37. 本轮补充：state root response 去重复声明

本轮完成 state response 的根对象收敛。

发现的问题：

- `objects.core.BattleAggregateState` 已经声明了战斗当前状态根对象。
- `objects/apiTypes/state.BattleStateRootResponse` 又声明了一份 root response case class。
- `BattleStateResponse(root)` 只是为了把 root response 编码成 JSON 根对象的 wrapper。
- `BattleStateReadAPIMessage` 因此还保留 `BattleStateResponseRenderer` 和 `renderRootResponse`，把 domain aggregate state 手写复制成 root response。
- root response 的主要差异不是独立业务状态，而是 wire 投影：
  - 隐藏 `artifactStatus`，输出 `resultReady` / `replayReady`。
  - 隐藏 `replayFrames`，state/read API 不直接返回回放帧。
  - 过滤空的 `winnerPlayerId` / `winnerHeroId`。

本轮修改后的结构：

- 删除重复的 `BattleStateRootResponse` case class。
- 删除 `BattleStateResponse(root)` wrapper case class。
- 保留 `object BattleStateRootResponse` 作为 wire encoder namespace。
- `BattleStateRootResponse` 提供 `Encoder[BattleAggregateState]`，继续输出原 state/read JSON 根对象。
- `BattleStateReadAPIMessage` 直接返回 `BattleAggregateState`。
- `BattleRoutes` 注册 state read API 时使用 `BattleAggregateState`，并显式引入 root encoder。

类型安全收益：

- 战斗当前状态根对象只由 `objects.core.BattleAggregateState` 一个业务对象表达。
- `artifactStatus -> resultReady/replayReady` 的协议投影集中在 encoder 边界。
- `BattleStateReadAPIMessage` 现在只负责读取 state 和返回 domain result，不再维护根对象字段复制。
- state/read wire JSON shape 保持不变，前端仍收到原来的根字段。

验证：

- `rg -n "BattleState(Response|RootResponse)\\(|renderRootResponse|BattleStateResponseRenderer" backend/src/main/scala/services/battle backend/src/test/scala` 未发现 wrapper 构造或手写 root renderer 残留。
- `npm run backend:compile` 通过。
- `npm run backend:test-contracts` 通过。
