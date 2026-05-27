# Battle Current-State Audit

## 2026-05-26 update: queue/room API types shared contract

本轮小票 `BE-BATTLE-ROOM-SHARED-APITYPES-SPLIT-01` 已完成一个很小的结构修正：`room` API contract 不再直接复用 `queue` 子包里的 DTO。现在 queue 和 room 都依赖 `objects/apiTypes/shared/BattleLobbySharedApiTypes.scala` 中的共享 lobby DTO。

当前 `objects/apiTypes` 结构为：

```text
objects/apiTypes/
  command/
    BattleCommandApiTypes.scala
  queue/
    BattleQueueJoinApiTypes.scala
    BattleQueueLeaveApiTypes.scala
    BattleQueueStatusApiTypes.scala
  room/
    BattleRoomApiTypes.scala
  shared/
    BattleLobbySharedApiTypes.scala
  results/
    BattleResultApiCodec.scala
    BattleResultApiTypes.scala
  state/
    BattleStateApiTypes.scala
```

这次修正的含义：

- `BattleQueueParticipantResponse` 和 `BattleSessionDescriptorResponse` 是 queue/status 与 room/snapshot 共同暴露给前端的 lobby wire contract，因此放到 `shared`，而不是让 `room` 反向 import `queue`。
- `BattleRoomApiTypes.scala` 已移动到 `objects/apiTypes/room`，room request/response codec 有了明确业务归属。
- `queue` 与 `room` 共享的是 API 边界 DTO，不是彼此的业务实现，所以这次拆分没有改变运行时逻辑和 HTTP response shape。
- 随后的 `BE-BATTLE-COMMAND-STATE-APITYPES-SPLIT-01` 已把 command/state API contract 下沉到 `objects/apiTypes/command` 与 `objects/apiTypes/state`。`objects/apiTypes` 根目录现在只作为 contract 分组目录，不再直接放业务源码文件。

验证结果：

```text
npm run backend:compile        passed
npm run backend:test-contracts passed
```

日期：2026-05-26

## 结论

当前 `services/battle` 已经从历史结构收敛到四个根目录：

```text
services/battle/
  api/
  database/
  objects/
  routes/
```

这符合用户要求的根目录方向，但还没有完全达到最终目标。主要差距是：

- `api` 已经都是 `XXXAPIMessage.scala` 文件。`BattleResultListAPIMessage` 和 `BattleResultRecordAPIMessage` 在 Postgres route 下已经可以通过 `RegisteredAPIMessage.apiWithToken` 泛型注册并用 `plan(connection)` 调用 `BattleResultTable`；queue/room/state/command 仍手写 `Json => IO[Json]`，并通过注入的 service 执行业务。
- `objects/apiTypes` 已经承载 request/response DTO 与 Circe codec，但部分文件仍包含 decode helper、错误 ADT 和响应渲染逻辑，不是“只有 final case class + object encoder/decoder”的最窄形态。
- 原 `objects/simulation` 过渡目录已经拆掉，纯战斗模拟规则按业务域迁入 `objects/world`、`objects/actors`、`objects/combat`、`objects/abilities`、`objects/runtime`。根目录不再有 `engine/rules/simulation`，但这意味着 `objects` 不再是纯数据对象目录。
- `database/results` 已经有 `BattleResultTable.scala` 与 `BattleResultTableInitializer.scala`；`database/queue` 和 `database/session` 仍是内存 runtime store，不是 table-backed database。
- `routes/BattleRoutes.scala` 现在只做 API message 注册清单，方向正确；但因为 APIMessage 仍依赖 runtime service，所以这里仍通过 `BattleAPIRuntimeContext` 注入运行时能力。
- Postgres storage 模式下，battle API router 已经能收到真实 JDBC `Connection`；非 Postgres 模式仍使用 router 默认 connection 行为，避免影响 in-memory/file 流程。

## 当前模块实现逻辑

### `battle/api`

职责：每个 HTTP API 的业务入口，负责把 JSON payload 转为 typed request，再调用 queue/session/result runtime，最后返回 typed response。

当前文件：

```text
api/queue/BattleQueueJoinAPIMessage.scala
api/queue/BattleQueueStatusAPIMessage.scala
api/queue/BattleQueueLeaveAPIMessage.scala
api/room/BattleRoomSnapshotAPIMessage.scala
api/room/BattleRoomHeartbeatAPIMessage.scala
api/state/BattleStateReadAPIMessage.scala
api/command/BattleCommandAPIMessage.scala
api/results/BattleResultListAPIMessage.scala
api/results/BattleResultRecordAPIMessage.scala
```

类型安全结构：

- 每个 API 文件都有 `final case class XXXAPIMessage(...) extends APIWithTokenMessage[XXXResponse]`。
- `plan(connection: Connection): IO[XXXResponse]` 已存在，和 sample 风格一致。
- 所有 battle API 目前通过 `requiresUserToken = true` 强制 token。
- API 请求字段已经尽量转为 value object，例如 `BattleId`、`TicketId`、`UserId`、`PlayerHandle`、`SessionToken`、`BattleMode`。

已推进：

- `BattleResultListAPIMessage` 已有 `given Decoder[BattleResultListAPIMessage]`，Postgres battle route 会使用 `RegisteredAPIMessage.apiWithToken[BattleResultListAPIMessage, BattleResultListResponse]`。
- `BattleResultRecordAPIMessage` 已有 `given Decoder[BattleResultRecordAPIMessage]`，Postgres battle route 会使用 `RegisteredAPIMessage.apiWithToken[BattleResultRecordAPIMessage, BattleResultRecordResponse]`。
- result list 路径在 `plan(connection)` 中调用 `BattleResultTable.list(connection, ...)`，result record 路径在 `plan(connection)` 中调用 `BattleResultTable.save(connection, ...)`。
- record API 的 visitor/authorization 业务判断保留在 `plan`，没有放进 Decoder，避免泛型 Decoder 失败把原来的 `403 Forbidden` 降级成 `400 BadRequest`。
- 非 Postgres route 暂时保留 repository-backed 注册，保证 in-memory/file contract tests 不被强行 JDBC 化破坏。

仍不理想：

- 除 result list/record 的 Postgres 路径外，其他 API 的 `registered(...)` 还手写 `payload.as[...]` / `decodeUserId(payload)` / `message.plan(connection)`，没有完全使用 `RegisteredAPIMessage.apiWithToken[Message, Response]`。
- queue/room/state/command APIMessage 构造函数仍注入 `BattleQueueService` 或 `BattleStateService`。这说明 `connection` 还没有成为全部 battle API 的唯一 effect boundary。
- `plan` 内部仍直接调用 service/repository，尤其 queue/session 依赖内存 runtime；这比旧 route 厚度低，但还不是“APIMessage + Table”最终形态。

### `battle/routes`

职责：只记录 battle 支持哪些 API message。

当前文件：

```text
routes/BattleRoutes.scala
routes/BattleAPIRuntimeContext.scala
```

当前实现：

```scala
object BattleRoutes {
  def apiMessages(context: BattleAPIRuntimeContext, resultRegistration: BattleResultAPIRegistration): List[RegisteredAPIMessage] =
    List(
      BattleQueueJoinAPIMessage.registered(...),
      ...
    )
}
```

类型安全结构：

- route 层不再直接解析 battle JSON。
- route 层不再 match battle 业务错误。
- route 层只组装 `RegisteredAPIMessage`。

仍不理想：

- 目标里的 `val apiMessages = List(...)` 还没有做到，因为当前 APIMessage 注册仍需要注入 runtime services。
- 若要改成无参 `val`，必须先让 APIMessage 本身可由 Circe decode，并在 `plan(connection)` 中通过 table/runtime adapter 获取能力。

### `battle/objects`

职责：battle 的领域对象、value object、有限状态 enum、API request/response DTO、以及当前过渡期的纯模拟规则。

主要结构：

```text
objects/BattleEnums.scala
objects/core/
objects/player/
objects/weapon/
objects/projectile/
objects/pickup/
objects/skill/
objects/queue/
objects/result/
objects/replay/
objects/apiTypes/
  queue/
  results/
objects/world/
objects/actors/
objects/combat/
objects/abilities/
objects/runtime/
```

类型安全结构：

- ID/value object：`BattleId`、`PlayerId`、`TicketId`、`RoomId`、`EpochMillis`、`DurationMillis`、`ElapsedMillis`、`BattleTick`、`ClientCommandSeq`。
- 业务 enum：`BattleMode`、`BattlePhase`、`WeaponKind`、`ProjectileKind`、`PickupKind`、`SkillKind`、`BattleCommandStatus`、`SkillOutcomeStatus` 等。
- aggregate state：`BattleAggregateState`、`BattlePlayerState`、`BattleProjectileState`、`BattlePickupState`、`BattleQueueSnapshot`、`BattleResultRecord`。
- enum companion 中有 `wireValue` 与 `fromWire`，用于统一序列化和反序列化。

Circe 边界：

- `objects/apiTypes` 中定义 API request/response case class 和 `given Encoder/Decoder`；queue 相关契约已下沉到 `objects/apiTypes/queue`，result 相关契约已下沉到 `objects/apiTypes/results`。
- state response render 在 `BattleStateApiTypes.scala`。
- command decode/render 在 `BattleCommandApiTypes.scala`。
- queue/room/result request/response codec 分别在对应 apiTypes 文件。

仍不理想：

- `objects/world`、`objects/actors`、`objects/combat`、`objects/abilities`、`objects/runtime` 包含原 `engine` 迁移后的纯规则层，覆盖 tick、bot、weapon、projectile、pickup、collision 等纯函数规则。它们不是普通 object DTO。
- `objects/apiTypes` 中仍有较多手写 decode helper 和 error mapping，这些是边界逻辑，可以接受，但不满足“只放 final case class + companion codec”的极窄规则。
- result 和 queue apiTypes 已先按业务域拆出，room/state/command 仍停留在 `objects/apiTypes` flat package。
- `BattleRoomApiTypes` 显式复用 queue response 中的 participant/session descriptor DTO，说明 queue/room wire contract 存在共享结构；后续应抽到 `objects/apiTypes/shared` 或统一的 lobby/room contract，避免业务子包互相粘连。
- `BattleEnums.scala` 集中 enum 有利于 wire 映射统一，但长期会变成过大的枚举总文件。更好的形态是“统一 wire 规则 + 按领域拆分 enum 文件”。

### `battle/database`

职责：后端 battle 的持久化、运行时存储、投影输出和结果查询。

主要结构：

```text
database/results/
database/queue/
database/session/
database/projections/
```

`database/results`：

- `BattleResultTable.scala`：Postgres battle result SQL/table/upsert/list/get。
- `BattleResultTableInitializer.scala`：建表和初始化。
- `BattleResultRepository.scala`：repository port。
- `FileBattleResultRepository.scala`、`InMemoryBattleResultRepository.scala`、`PostgresBattleResultRepository.scala`：不同存储实现。
- `BattleResultFileJsonParser.scala`、`BattleResultFileJsonRenderer.scala`：legacy file JSON compatibility boundary。

`database/queue`：

- 当前是 in-memory matchmaking/room runtime。
- 管理 join/status/leave/heartbeat/room snapshot/session lookup。
- 不是真正 database table。

`database/session`：

- 当前是 in-memory authoritative battle state runtime。
- 管理 `BattleStateService`、`InMemoryBattleStateService`、command accept、stored battle advance/init。
- 调用 `objects/simulation.BattleEngine` 推进权威状态。
- 不是真正 database table。

`database/projections`：

- 管理战斗结束后的结果投影、replay frame render、mail/replay artifact writer。
- 使用 `BattleProjectionPorts` 对外写 mail/replay，避免模拟规则直接依赖其他业务模块。

仍不理想：

- `database/queue` 与 `database/session` 从名字上看像持久化层，但实际是 runtime memory adapter。建议后续改成 `database/runtime`、`database/sessionStore`，或明确补 Table 设计。
- 当前只有 battle result 有标准 `Table` 和 `TableInitializer`。

## ADT 与类型安全审计

已经做得较好的部分：

- 有 value object 包装重要业务字段，避免大量裸 `String/Long/Int`。
- 有 enum/ADT 表达有限状态，避免 `status: String` 作为核心状态。
- battle API 入口已经通过 typed request/response 组织，不再把 route 当业务主体。
- 业务失败多数使用 ADT，例如 queue/status、state read、command submit、decode error。

仍需要修的部分：

- APIMessage 注册层仍手写 error string，例如 `"battle_not_found"`、`"command_not_authorized"`，这些应该逐步收敛为 API error ADT + centralized encoder。
- `BattleQueueJoinRequest` 等 request payload 仍需要先解成 raw `Option[String]`，再转 domain value object。这是 JSON 边界允许的，但 raw payload 应保持 private。
- `objects/apiTypes` 仍包含过多转换逻辑，后续应把“domain -> response”的 render 函数按 response companion 管理。

## Circe 使用审计

当前使用方式：

- `system/api.RegisteredAPIMessage` 已经支持基于 `Decoder[Message]` 和 `Encoder[Response]` 的自动 decode/encode。
- battle API 仍大多绕过泛型注册，手写 `RegisteredAPIMessage(...)`。
- state/command response 使用 `Encoder.forProductN` 和 `Encoder.instance`。
- result file/replay frame compatibility boundary 已使用 Circe parser/encoder，不再是手写 JSON 字符串拼接。

建议：

- 下一步优先把一个 API 改成 `RegisteredAPIMessage.apiWithToken[Message, Response]` 形态，证明 battle API 可以不再手写 `planJson`。
- 给 token 注入字段建立统一 typed contract，避免每个 API 都手写 `decodeUserId(payload)`。
- 保留 Circe cursor/helper 于 JSON 边界，不要让它进入 `objects/core` 或 simulation rules。

## Cats Effect 与 side-effect boundary 审计

当前结构：

- `APIMessage.plan(connection: Connection): IO[Response]` 已经完成基础设施迁移。
- `APIMessageRouter` 在请求边界提供 connection。
- battle API 中对同步 runtime/repository 的调用使用 `IO.blocking` 或 `IO.fromEither`。

已推进：

- `route.BackendHttp4sApp` 会在 `StorageConfig.Postgres` 下为 battle routes 提供 `PostgresSupport.connectionResource`。
- `route.battle.BattleHttp4sRoutes` 会把该 connection resource 传入 `APIMessageRouter.routes`。
- 这使 `plan(connection)` 在 Postgres battle API 下不再只是形式参数。

仍需要修的部分：

- queue/session 的权威状态仍是内存 service，而不是通过 `connection` 访问的 Table。
- `BattleResultListAPIMessage` 和 `BattleResultRecordAPIMessage` 的 Postgres 路径已经在 `plan(connection)` 内直接调用 `BattleResultTable`；非 Postgres result 兼容路径仍通过注入 `BattleResultRepository`。
- 如果最终目标是 sample 风格，那么 `plan(connection)` 应该是真正的 effect boundary，而不是形式参数。

## Render 技术边界

这里的 render 指后端把领域状态渲染成 wire/API/file/replay JSON，不是前端 Phaser 渲染。

当前 render 分布：

- API state render：`objects/apiTypes/BattleStateApiTypes.scala`
- command response render：`objects/apiTypes/BattleCommandApiTypes.scala`
- queue/room response render：`objects/apiTypes/BattleQueueStatusApiTypes.scala`、`BattleRoomApiTypes.scala`
- result API render：`objects/apiTypes/results/BattleResultApiTypes.scala`
- replay frame render：`database/projections/BattleReplayFramesJsonRenderer.scala`
- file result render：`database/results/BattleResultFileJsonRenderer.scala`

建议：

- API render 保持在 `objects/apiTypes`。
- replay/file render 是 persistence compatibility，继续留在 `database`。
- 不要把 render 放回 route。

## 当前路线合理性

合理部分：

- 根目录限制为 `api/database/objects/routes` 能显著减少旧 `application/engine/persistence/ports/routes` 混杂。
- `route` 变薄是正确方向。
- APIMessage 比旧 route handler 更适合表达 API contract。
- `objects/apiTypes` 用 Circe 管理 request/response contract 是正确方向。
- Table/TableInitializer 明确了 Postgres 边界，优于散落 SQL。

需要警惕：

- 强制去掉 `engine/rules` 后，纯战斗规则必须有新家。现在放在 `objects/simulation` 是过渡方案，但它使 `objects` 不再是纯 DTO/domain object。
- 强制所有 API 都 token 化已经改变契约，前端必须同步，否则 battle API 会拒绝请求。
- 如果把所有 service 调用都塞进 APIMessage，APIMessage 会变成新的 god object。
- 如果把 queue/session 直接 table 化，会引入事务、锁、tick 性能和一致性问题，不应混入当前结构迁移。

## 下一步推荐小票

### Ticket A：继续统一 battle API 注册方式

目标：继续选择一个 API，把它从手写 `RegisteredAPIMessage` 改成泛型 `RegisteredAPIMessage.apiWithToken`。result list/record 的 Postgres 路径已经完成第一步，下一个候选应从 queue/room/state/command 里选择，但这些依赖内存 runtime store，风险高于 result。

前提：

- APIMessage case class 必须只包含可由 Circe decode 的 request 字段。
- service/repository 依赖不能出现在 case class 字段里。
- 若仍需要 service，需要先设计 runtime registry 或从 connection 访问 table/store。

风险：中等。会暴露当前 service injection 与 sample 风格之间的根本冲突。

### Ticket B：继续收窄 `objects` 内规则边界

已完成：`objects/simulation` 已按业务域拆成 `objects/world`、`objects/combat`、`objects/actors`、`objects/abilities`、`objects/runtime` 下的规则文件，`simulation` 过渡命名已删除。

后续目标：如果严格执行“objects 只有 case class/codec”，需要为这些规则另定归属。但用户当前要求 battle 根目录只保留 `api/database/objects/routes`，所以这些纯规则只能暂时留在 `objects` 内部的业务子域中。

风险：目前使用跨规则域 wildcard import 来保持迁移安全，后续可以逐步收窄 import，让依赖方向更清楚。

### Ticket C：database result 完成 Table 化

目标：把 result API 从 repository 注入逐步改为 `BattleResultTable` + `Connection` 直连，使 `plan(connection)` 真正使用 connection。

风险：中。需要保证 file/in-memory test repository 的 contract 仍能覆盖。

### 推荐

已完成 Ticket C 的两个最小垂直切片：Postgres `BattleResultListAPIMessage` 的 `plan(connection)` 直接使用 `BattleResultTable.list(connection, query)`，Postgres `BattleResultRecordAPIMessage` 的 `plan(connection)` 直接使用 `BattleResultTable.save(connection, record)`。

下一步建议不要立刻 table 化 queue/session；优先继续把 room/state/command 的 `objects/apiTypes` 按业务域拆包，并把 queue/room 共享 DTO 抽到 shared contract。
