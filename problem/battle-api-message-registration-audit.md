# Battle APIMessage Registration Audit

更新日期：2026-05-27

## 结论

当前 `services/battle/routes/BattleRoutes.scala` 已经没有 `List[String]`、`apiName("...")` 或手写 API path catalog。battle API 名称由 `system.api.RegisteredAPIMessage` 通过 `ClassTag` 和 `APIMessage.apiNameFromClass[Message]` 推导。

也就是说，下面这种旧形式已经不应该再出现：

```scala
apiName("BattleQueueJoinAPIMessage")
List[String]("battlequeuejoin")
```

当前仍未完全达到 sample 里 `val apiMessages = List(apiWithToken[...])` 的原因，不是 route 还在写字符串，而是部分 `APIMessage` 构造函数仍然携带运行时 service，例如 `BattleQueueService` 和 `BattleStateService`。这些 service 不能也不应该从 JSON 反序列化出来。

## 当前 system/api 能力

`system.api.APIMessage.scala` 当前已经提供类型注册能力：

```scala
RegisteredAPIMessage.api[Message, Response]
RegisteredAPIMessage.apiWithToken[Message, Response]
RegisteredAPIMessage.apiWithTokenFromJson[Message, Response](buildMessage)
```

其中 `api` 和 `apiWithToken` 的 API 名称来自：

```scala
APIMessage.apiNameFromClass[Message]
```

`apiWithTokenFromJson` 也使用同一个 `ClassTag[Message]` 推导 API 名称，所以它不是字符串注册。它和 `apiWithToken` 的区别是：它允许在构造 message 时显式注入运行时能力。

## 当前 BattleRoutes 状态

`BattleRoutes.connectionBackedResultApiMessages` 已经是 sample 风格的无参 typed list：

```scala
val connectionBackedResultApiMessages: List[RegisteredAPIMessage] =
  List(
    apiWithToken[BattleResultListAPIMessage, BattleResultListResponse],
    apiWithToken[BattleResultRecordAPIMessage, BattleResultRecordResponse]
  )
```

这部分满足：

- API 名称不手写。
- request 由 Circe `Decoder[Message]` 解码。
- response 由 Circe `Encoder[Response]` 编码。
- `plan(connection)` 是实际 effect boundary。
- Postgres 路径下直接使用 `BattleResultTable`。

## 仍然 runtime-backed 的 API

下面这些 API 仍然需要 `registered(...)` factory：

| APIMessage | 运行时依赖 | 当前注册方式 | 不能直接 `apiWithToken[...]` 的原因 |
|---|---|---|---|
| `BattleQueueJoinAPIMessage` | `BattleQueueService`, `BattleQueueJoinAuthorizationService` | `apiWithTokenFromJson` | join queue 和 session/handle 授权依赖运行时 service |
| `BattleQueueStatusAPIMessage` | `BattleQueueService` | `apiWithTokenFromJson` | queue ticket snapshot 存在于 queue runtime |
| `BattleQueueLeaveAPIMessage` | `BattleQueueService` | `apiWithTokenFromJson` | leave queue 是 runtime side effect |
| `BattleRoomSnapshotAPIMessage` | `BattleQueueService` | `apiWithTokenFromJson` | room snapshot 由 queue runtime 管理 |
| `BattleRoomHeartbeatAPIMessage` | `BattleQueueService` | `apiWithTokenFromJson` | heartbeat 更新 runtime 参与者状态 |
| `BattleStateReadAPIMessage` | `BattleStateService` | `apiWithTokenFromJson` | authoritative battle state 存在于 session runtime |
| `BattleCommandAPIMessage` | `BattleStateService` | `apiWithTokenFromJson` | command accept/apply 需要 authoritative runtime |

这些 factory 的合理性在于：

- API 名称仍然通过 `BattleQueueJoinAPIMessage` 等 class name 推导。
- service 是显式参数，不是全局 singleton。
- route 不解析 JSON，不判断 path，不处理业务错误。
- runtime-backed API 被集中隔离在 `serviceInjectedRuntimeApiMessages(context)`。

## 为什么不能强行改成无参 val

如果现在把所有 battle API 都写成：

```scala
val apiMessages: List[RegisteredAPIMessage] = List(
  apiWithToken[BattleQueueJoinAPIMessage, BattleQueueSnapshotResponse],
  apiWithToken[BattleCommandAPIMessage, BattleCommandAcceptedResponse]
)
```

必须让 Circe 能直接 decode 出完整的 `BattleQueueJoinAPIMessage` / `BattleCommandAPIMessage`。但这些 message 当前包含 service 字段：

```scala
queueService: BattleQueueService
stateService: BattleStateService
```

这会导致三种错误方案：

- 给 service 写 fake decoder：把运行时能力伪装成 request data，破坏 contract。
- 从全局 singleton 取 service：隐藏副作用边界，破坏测试隔离。
- 把 service 放进 `objects/apiTypes`：把 wire DTO 和后端 runtime implementation 混在一起。

因此，当前不能只靠改 `BattleRoutes` 达到完整 sample 形态。必须先处理 queue/session runtime 的依赖获取方式。

## 类型安全评价

当前已满足的部分：

- API 名称不是裸 `String` catalog，而是 `APIName` value object。
- API 名称由 message class 推导，避免 route 和 APIMessage 命名漂移。
- result list/record 已使用 `apiWithToken[Message, Response]`。
- battle API 都是 `APIWithTokenMessage[Response]`。
- `plan(connection): IO[Response]` 已经是统一执行接口。

当前仍不理想的部分：

- queue/room/state/command 的 request decode 仍分散在各自 `registered(...)` 中。
- 每个 runtime-backed API 仍各自解析 `userId`。
- 运行时 service 仍作为 APIMessage case class 字段存在。
- `BattleRoutes.apiMessages(...)` 仍是带 context 的 `def`，不是 sample 的无参 `val`。

## 下一步建议

不要在当前状态下强行删除 `registered(...)` factory。更安全的迁移顺序是：

1. 保持 `BattleRoutes` 不出现字符串 API name。
2. 逐个把 runtime-backed API 的 JSON decode 收敛到 typed request decoder，减少手写 cursor/helper。
3. 设计一个显式的 runtime context 方案，或者把对应 API 真正改成 connection/table-backed。
4. 当某个 APIMessage 不再携带 service 字段后，再把它迁入 `apiWithToken[Message, Response]`。

下一张最小票据建议：

```text
ID: BE-BATTLE-RUNTIME-API-DECODE-10
Goal:
  选择 BattleQueueLeaveAPIMessage，把 request decode 从手写 Json cursor 改为 typed Decoder 边界，同时保持 service 显式注入。
Boundary:
  - services/battle/api/queue/BattleQueueLeaveAPIMessage.scala
  - services/battle/objects/apiTypes/queue/BattleQueueLeaveApiTypes.scala
  - 如确实需要，system/api 只允许新增小型泛型 helper
Forbidden:
  - 不改前端字段
  - 不改 queue 业务行为
  - 不引入全局 service locator
  - 不把 service 放进 apiTypes
Verification:
  - npm run backend:compile
  - npm run backend:test-contracts
Acceptance:
  - API name 仍由 class 推导
  - BattleQueueLeave request decode 不再手写字段读取
  - token/userId 注入语义不变
```

## 已完成：BE-BATTLE-RUNTIME-API-DECODE-10

本轮已完成 `BattleQueueLeaveAPIMessage` 的最小迁移：

- `BattleQueueLeaveRequest` 在 `objects/apiTypes/queue/BattleQueueLeaveApiTypes.scala` 中新增 Circe `Decoder`。
- `BattleQueueLeaveAPIMessage` 不再手写读取 `payload.asObject("ticketId").asString`。
- `BattleQueueLeaveAPIMessage` 仍保留 `apiWithTokenFromJson`，因为它还需要显式注入 `BattleQueueService`。
- `ticketId` 的空值和缺失仍映射为 `ticketId is required.`。
- 非 JSON object 请求仍映射为 `Invalid battle queue leave request.`。
- API name 仍由 `BattleQueueLeaveAPIMessage` class name 推导，没有新增字符串 path。

验证结果：

```text
npm run backend:compile        passed
npm run backend:test-contracts passed
```

剩余差距：

- `BattleQueueLeaveAPIMessage` 仍然是 runtime-backed APIMessage，因为 `queueService` 还在 message 字段中。
- 只有当 queue runtime 被显式 context 化或 table-backed 化后，才能把它改成完全 sample 风格的 `apiWithToken[BattleQueueLeaveAPIMessage, BattleQueueLeaveResponse]`。

## 已完成：BE-BATTLE-RUNTIME-API-DECODE-11

本轮已完成 `BattleQueueStatusAPIMessage` 的同类迁移：

- `BattleQueueStatusRequest` 在 `objects/apiTypes/queue/BattleQueueStatusApiTypes.scala` 中新增 Circe `Decoder`。
- `BattleQueueStatusAPIMessage` 不再手写读取 `payload.asObject("ticketId").asString`。
- `BattleQueueStatusAPIMessage` 仍保留 `apiWithTokenFromJson`，因为它还需要显式注入 `BattleQueueService`。
- `ticketId` 的空值和缺失仍映射为 `ticketId is required.`。
- 非 JSON object 请求仍映射为 `Invalid battle queue status request.`。
- API name 仍由 `BattleQueueStatusAPIMessage` class name 推导，没有新增字符串 path。

验证结果：

```text
npm run backend:compile        passed
npm run backend:test-contracts passed
```

剩余差距：

- `BattleQueueStatusAPIMessage` 仍然是 runtime-backed APIMessage，因为 `queueService` 还在 message 字段中。
- queue status 目前只完成了 request decode 边界收敛，还没有改成完全 sample 风格的 `apiWithToken[BattleQueueStatusAPIMessage, BattleQueueSnapshotResponse]`。

## 已完成：BE-BATTLE-RUNTIME-API-DECODE-12

本轮已完成 `BattleRoomSnapshotAPIMessage` 的同类迁移：

- `BattleRoomSnapshotRequest` 在 `objects/apiTypes/room/BattleRoomSnapshotApiTypes.scala` 中新增 Circe `Decoder`。
- `BattleRoomSnapshotAPIMessage` 不再手写读取 `payload.asObject("roomId").asString`。
- `BattleRoomSnapshotAPIMessage` 仍保留 `apiWithTokenFromJson`，因为它还需要显式注入 `BattleQueueService`。
- `roomId` 的空值和缺失仍映射为 `roomId is required.`。
- 非 JSON object 请求仍映射为 `Invalid battle room snapshot request.`。
- API name 仍由 `BattleRoomSnapshotAPIMessage` class name 推导，没有新增字符串 path。

验证结果：

```text
npm run backend:compile        passed
npm run backend:test-contracts passed
```

剩余差距：

- `BattleRoomSnapshotAPIMessage` 仍然是 runtime-backed APIMessage，因为 `queueService` 还在 message 字段中。
- room snapshot 目前只完成了 request decode 边界收敛，还没有改成完全 sample 风格的 `apiWithToken[BattleRoomSnapshotAPIMessage, RealtimeRoomSnapshotResponse]`。

## 已完成：BE-BATTLE-RUNTIME-API-DECODE-13

本轮已完成 `BattleRoomHeartbeatAPIMessage` 的同类迁移：

- `BattleRoomHeartbeatRequest` 在 `objects/apiTypes/room/BattleRoomHeartbeatApiTypes.scala` 中新增 Circe `Decoder`。
- `BattleRoomHeartbeatAPIMessage` 不再手写读取 `roomId`、`ticketId`、`handle` 三个可选字段。
- 为保持兼容，字段缺失、字段为 `null` 或字段类型不是 string 时仍被视为 `None`，后续由 `RealtimeRoomHeartbeatCommand` 和 queue runtime 判断是否缺少必要 room 信息。
- `BattleRoomHeartbeatAPIMessage` 仍保留 `apiWithTokenFromJson`，因为它还需要显式注入 `BattleQueueService`。
- 非 JSON object 请求仍映射为 `Invalid battle room heartbeat request.`。
- API name 仍由 `BattleRoomHeartbeatAPIMessage` class name 推导，没有新增字符串 path。

验证结果：

```text
npm run backend:compile        passed
npm run backend:test-contracts passed
```

剩余差距：

- `BattleRoomHeartbeatAPIMessage` 仍然是 runtime-backed APIMessage，因为 `queueService` 还在 message 字段中。
- heartbeat 目前只完成了 request decode 边界收敛，还没有改成完全 sample 风格的 `apiWithToken[BattleRoomHeartbeatAPIMessage, RealtimeRoomSnapshotResponse]`。

## 已完成：BE-BATTLE-RUNTIME-API-DECODE-14

本轮已完成 `BattleStateReadAPIMessage` 的同类迁移：

- `BattleStateReadAPIRequest` 在 `objects/apiTypes/state/BattleStateApiTypes.scala` 中改为必需的 `battleId: String`。
- `BattleStateReadAPIRequest` 的 Circe `Decoder` 负责读取并校验非空 `battleId`。
- `BattleStateReadAPIMessage` 不再处理 `Option[String]`、空字符串过滤和 missing battleId 分支，只负责把已校验的 request 转成 `BattleId`。
- `battleId` 的空值、缺失和错误类型仍映射为 `battleId is required.`。
- `BattleStateReadAPIMessage` 仍保留 `apiWithTokenFromJson`，因为它还需要显式注入 `BattleStateService`。
- API name 仍由 `BattleStateReadAPIMessage` class name 推导，没有新增字符串 path。

验证结果：

```text
npm run backend:compile        passed
npm run backend:test-contracts passed
```

剩余差距：

- `BattleStateReadAPIMessage` 仍然是 runtime-backed APIMessage，因为 `stateService` 还在 message 字段中。
- state read 目前只完成了 request decode 边界收敛，还没有改成完全 sample 风格的 `apiWithToken[BattleStateReadAPIMessage, BattleStateResponse]`。

## 已完成：BE-BATTLE-RUNTIME-API-DECODE-15

本轮已完成 `BattleCommandAPIMessage` 的字段级 request decode 收敛：

- `BattleCommandRequestField` 从 APIMessage 内部迁到 `objects/apiTypes/command/BattleCommandRequestApiTypes.scala`，作为 command request 字段错误的 ADT。
- `BattleCommandAPIRequest` 新增 Circe `Decoder`，负责读取并校验 `battleId`、`playerId`、`clientTick`、`movement`、`aim`、`primaryHeld`、`reloadPressed`、`switchWeaponDirection` 等字段。
- `BattleCommandAPIRequest` 的 Decoder 继续保留旧兼容行为：`ticketId` 类型错误时视为 `None`，再由 APIMessage 映射为 `command_not_authorized`。
- `BattleCommandVectorRequest` 的有限数值校验从 APIMessage helper 下沉到 command request Decoder。
- `BattleCommandAPIMessage` 不再维护 `HCursor`、`required`、`optional`、`requiredVector`、`optionalVector`、`requiredFiniteDouble` 等字段级 JSON helper。
- `BattleCommandAPIMessage` 仍负责把 wire request DTO 转成 domain `BattleCommandRequest`，并保持 `BattleWeaponSwitchDirection`、`BattleWeaponSwitchIndex`、`BattleCommandSkillIntents` 的 domain 转换逻辑。
- `BattleCommandAPIMessage` 仍保留 `apiWithTokenFromJson`，因为它还需要显式注入 `BattleStateService`。
- API name 仍由 `BattleCommandAPIMessage` class name 推导，没有新增字符串 path。

验证结果：

```text
npm run backend:compile        passed
npm run backend:test-contracts passed
```

剩余差距：

- `BattleCommandAPIMessage` 仍然是 runtime-backed APIMessage，因为 `stateService` 还在 message 字段中。
- command 目前只完成了字段级 request decode 边界收敛，还没有改成完全 sample 风格的 `apiWithToken[BattleCommandAPIMessage, BattleCommandAcceptedResponse]`。

## 已完成：BE-BATTLE-RUNTIME-API-DECODE-16

本轮已完成 `BattleQueueJoinAPIMessage` 的字段级 request decode 收敛：

- `BattleQueueJoinRequest` 在 `objects/apiTypes/queue/BattleQueueJoinApiTypes.scala` 中新增 Circe `Decoder`。
- `BattleQueueJoinAPIMessage` 不再手写读取 `handle`、`sessionToken`、`modeId`、`queueRequestId`、`rating`、`avatar`、`skin`。
- 为保持兼容，普通可选文本字段缺失、`null` 或非 string 类型时仍被视为 `None`。
- `rating` 继续支持 JSON number 和数字字符串；空字符串视为 `None`；非法字符串或非法类型仍映射为 `Invalid battle queue join request.`。
- `BattleQueueJoinAPIMessage` 仍负责把 wire DTO 转成 domain `BattleQueueJoinCommand`，包括 `PlayerHandle`、`SessionToken`、`BattleMode`、`QueueRequestId`、`Rating` 的领域转换。
- `BattleQueueJoinAPIMessage` 仍保留 `apiWithTokenFromJson`，因为它还需要显式注入 `BattleQueueService` 和 `BattleQueueJoinAuthorizationService`。
- API name 仍由 `BattleQueueJoinAPIMessage` class name 推导，没有新增字符串 path。

验证结果：

```text
npm run backend:compile        passed
npm run backend:test-contracts passed
```

剩余差距：

- `BattleQueueJoinAPIMessage` 仍然是 runtime-backed APIMessage，因为 `queueService` 和 `authorizationService` 还在 message 字段中。
- queue join 目前只完成了字段级 request decode 边界收敛，还没有改成完全 sample 风格的 `apiWithToken[BattleQueueJoinAPIMessage, BattleQueueSnapshotResponse]`。

## 已完成：BE-BATTLE-API-DECODE-RESIDUAL-AUDIT-17

本轮完成 battle API request decode 残余审计。

### 证据命令

```text
rg "payload\\.hcursor|get\\[Option|cursor\\.get|downField|asObject|decodeRequestValue|Decoder\\.instance|DecodingFailure|fromJson|planJson" backend/src/main/scala/services/battle/api backend/src/main/scala/services/battle/objects/apiTypes backend/src/main/scala/services/battle/routes -n

rg "package services\\.battle\\.(application|engine|persistence|ports)|services\\.battle\\.(application|engine|persistence|ports)" backend/src/main/scala backend/src/test/scala -n

rg "apiName\\(|apiMessageNames|List\\[String\\]|BattleAPIMessageSpec|battlequeuejoin|battlequeuestatus|battlequeueleave|battleroomsnapshot|battleroomheartbeat|battlestateread|battlecommand" backend/src/main/scala/services/battle backend/src/main/scala/route -n

rg "apiWithTokenFromJson|registered\\(|apiWithToken\\[" backend/src/main/scala/services/battle/api backend/src/main/scala/services/battle/routes -n

rg "object .*Table|TableInitializer|trait .*Repository|final class .*Repository" backend/src/main/scala/services/battle/database -n
```

### 审计结论

已完成的部分：

- `BattleQueueJoinAPIMessage`、`BattleQueueStatusAPIMessage`、`BattleQueueLeaveAPIMessage`、`BattleRoomSnapshotAPIMessage`、`BattleRoomHeartbeatAPIMessage`、`BattleStateReadAPIMessage`、`BattleCommandAPIMessage` 的业务 request 字段级 decode 已经下沉到 `objects/apiTypes/*` 的 Circe `Decoder`。
- battle APIMessage 内剩余的 `payload.hcursor.get[String]("userId")` 是 `APIMessageRouter` 把 `userToken` 替换为 `userId` 后的身份读取，不是业务 request 字段解析。
- battle routes 下没有 `apiName(...)`、`apiMessageNames`、`List[String]` 或 `BattleAPIMessageSpec`。
- `services/battle` 当前没有旧 `application`、`engine`、`persistence`、`ports` package/import 残余。
- connection-backed result API 已经通过 `apiWithToken[BattleResultListAPIMessage, BattleResultListResponse]` 和 `apiWithToken[BattleResultRecordAPIMessage, BattleResultRecordResponse]` 注册。
- `database/results` 是当前唯一明确 table-backed 的 battle database 子域，包含 `BattleResultTable` 和 `BattleResultTableInitializer`。

仍未完成的部分：

- queue/room/state/command 仍然通过 `apiWithTokenFromJson` 注册，因为对应 APIMessage 仍携带 `BattleQueueService`、`BattleQueueJoinAuthorizationService` 或 `BattleStateService`。
- `BattleRoutes.apiMessages(...)` 仍然是带 runtime context 的 `def`，不是完整 sample 风格的无参 `val apiMessages = List(...)`。
- `database/queue` 和 `database/session` 仍然是 runtime service/store，不是 table-backed database。
- `objects/apiTypes/command/BattleCommandRequestApiTypes.scala` 内有 `BattleCommandRequestField` enum，用于 typed decode error。它提升了字段错误类型安全，但严格来说超出了“apiTypes 只放 final case class + response companion”的最窄形态。后续如果要更严格，可以把该 enum 迁到 `api/command` 并让 request Decoder 返回稳定错误 code，但这需要重新设计 Decoder 错误传递。

下一步真正瓶颈已经不是手写业务字段 JSON decode，而是 runtime dependency：

```text
BattleQueueService
BattleQueueJoinAuthorizationService
BattleStateService
```

要继续接近 sample 风格，需要二选一：

- 方案 A：设计类型安全的 `apiWithTokenAndContext[Context, Message, Response](context)`，让 message 不携带 service 字段，但 `plan` 仍可显式使用 runtime context。
- 方案 B：把 queue/session 真的 table-backed 化，让 `plan(connection)` 可以直接通过 `Connection` 访问队列和战斗状态。

当前建议优先方案 A。方案 B 会涉及实时匹配、heartbeat、command accept/apply、tick 推进和并发一致性，不适合混入当前 APIMessage 注册清理票据。

## 已完成：BE-BATTLE-USER-ID-DECODE-18

本轮完成 token 注入后 `userId` 读取逻辑的收敛。

### 变更

- `system.api.APIMessage` 新增 `injectedUserId(payload)`，返回 `IO[UserId]`，失败时统一抛出 `APIMessageError.Unauthorized("Login is required.")`。
- `system.api.APIMessage` 新增 `injectedUserIdValue(payload)`，返回 `Either[String, UserId]`，用于 connection-backed `Decoder[Message]` 场景。
- queue、room、state、command、results 的 battle APIMessage 不再各自维护 `decodeUserId(payload)` 私有函数。
- `BattleResultListAPIMessage` 和 `BattleResultRecordAPIMessage` 的 connection-backed Circe Decoder 也复用 `APIMessage.injectedUserIdValue`。

### 验证证据

```text
rg 'private def decodeUserId|decodeUserIdValue|payload\.hcursor\.get\[String\]\("userId"\)|cursor\.get\[String\]\("userId"\)' backend/src/main/scala/services/battle/api backend/src/main/scala/system/api -n
```

结果：无匹配。

```text
npm run backend:compile        passed
npm run backend:test-contracts passed
```

### 影响

- 这一步不改变 request/response JSON 字段。
- 这一步不改变 token 注入字段名，仍然依赖 `APIMessageRouter` 把 `userToken` 替换为 `userId`。
- 这一步消除了 battle APIMessage 中重复的身份边界解析逻辑，让 token 后身份读取有单一实现。

剩余差距不变：

- queue/room/state/command 仍是 runtime-backed APIMessage。
- 下一步如果继续向 sample 风格推进，应设计显式 `Context` 注册能力，避免把 service 作为 message 字段，同时也不要引入全局 singleton。

## 已完成：BE-BATTLE-CONTEXT-REGISTER-19

本轮完成 typed context 注册方案的最小垂直切片：`BattleQueueLeaveAPIMessage`。

### system/api 变更

新增：

```scala
trait APIMessageWithContext[Context, Response]:
  def plan(context: Context, connection: Connection): IO[Response]

trait APIWithTokenContextMessage[Context, Response]
  extends APIMessageWithContext[Context, Response]
```

新增注册 helper：

```scala
RegisteredAPIMessage.apiWithTokenAndContext[Context, Message, Response](
  context,
  decodeFailure
)
```

它的语义是：

- API name 仍由 `Message` class name 推导。
- request 仍由 Circe `Decoder[Message]` 解码。
- response 仍由 Circe `Encoder[Response]` 编码。
- token 仍由 `APIMessageRouter` 先注入 `userId`。
- runtime service 不再进入 `Message` 字段，而是作为显式 `context` 传给 `plan(context, connection)`。
- 不使用全局 singleton。

### BattleQueueLeaveAPIMessage 变更

迁移前：

```scala
final case class BattleQueueLeaveAPIMessage(
  userId: UserId,
  ticketId: TicketId,
  queueService: BattleQueueService
) extends APIWithTokenMessage[BattleQueueLeaveResponse]
```

迁移后：

```scala
final case class BattleQueueLeaveAPIMessage(
  userId: UserId,
  ticketId: TicketId
) extends APIWithTokenContextMessage[BattleQueueService, BattleQueueLeaveResponse]
```

现在 `BattleQueueLeaveAPIMessage` 本身只携带请求数据和 token 注入后的身份数据，不再携带 `BattleQueueService`。`BattleQueueService` 通过 `apiWithTokenAndContext` 显式传入：

```scala
RegisteredAPIMessage.apiWithTokenAndContext[
  BattleQueueService,
  BattleQueueLeaveAPIMessage,
  BattleQueueLeaveResponse
](
  context = queueService,
  decodeFailure = requestDecodeFailure
)
```

### 验证结果

```text
npm run backend:compile        passed
npm run backend:test-contracts passed
```

### 结论

这个方案比把 service 塞进 `APIMessage` 字段更接近 sample 风格：

- message 可以由 Decoder 直接构造。
- service 依赖是显式 context，不是全局状态。
- route 仍然不用写 API path string。

但它还不是最终目标里的无参：

```scala
val apiMessages = List(apiWithToken[...])
```

原因是 queue/runtime 依赖仍然存在，只是从 message 字段移到了 typed context。下一步可以按同样方式迁移 `BattleQueueStatusAPIMessage`，再逐步覆盖 room/state/command。

## 已完成：BE-BATTLE-CONTEXT-REGISTER-20

本轮继续 typed context 注册方案，迁移 `BattleQueueStatusAPIMessage`。

### BattleQueueStatusAPIMessage 变更

迁移前：

```scala
final case class BattleQueueStatusAPIMessage(
  userId: UserId,
  ticketId: TicketId,
  queueService: BattleQueueService
) extends APIWithTokenMessage[BattleQueueSnapshotResponse]
```

迁移后：

```scala
final case class BattleQueueStatusAPIMessage(
  userId: UserId,
  ticketId: TicketId
) extends APIWithTokenContextMessage[BattleQueueService, BattleQueueSnapshotResponse]
```

现在 `BattleQueueStatusAPIMessage` 本身只携带请求数据和 token 注入后的身份数据，不再携带 `BattleQueueService`。`BattleQueueService` 通过 `apiWithTokenAndContext` 显式传入：

```scala
RegisteredAPIMessage.apiWithTokenAndContext[
  BattleQueueService,
  BattleQueueStatusAPIMessage,
  BattleQueueSnapshotResponse
](
  context = queueService,
  decodeFailure = requestDecodeFailure
)
```

### 验证结果

```text
npm run backend:compile        passed
npm run backend:test-contracts passed
```

### 结论

`BattleQueueLeaveAPIMessage` 和 `BattleQueueStatusAPIMessage` 已经证明 typed context 方案可用于 queue runtime API：

- message 不携带 service 字段。
- service 依赖通过显式 context 进入 `plan(context, connection)`。
- route 仍不手写 API path string。
- request/response JSON shape 不变。

下一步可以迁移 `BattleQueueJoinAPIMessage`，但它需要两个 context dependency：`BattleQueueService` 和 `BattleQueueJoinAuthorizationService`。建议用现有 `BattleAPIRuntimeContext` 或新增 queue-specific context，而不是把两个 service 重新塞回 message 字段。

## 已完成：BE-BATTLE-CONTEXT-REGISTER-21

本轮继续 typed context 注册方案，迁移 `BattleQueueJoinAPIMessage`。

### BattleQueueJoinAPIContext

`BattleQueueJoinAPIMessage.scala` 内新增 queue join 专用 context：

```scala
final case class BattleQueueJoinAPIContext(
  queueService: BattleQueueService,
  authorizationService: BattleQueueJoinAuthorizationService
)
```

它放在 APIMessage 文件内，而不是 routes 包内，原因是：

- 避免 `api` 反向依赖 `routes`。
- 避免使用裸 tuple 传两个 service。
- 避免把 service 放回 message 字段。

### BattleQueueJoinAPIMessage 变更

迁移前：

```scala
final case class BattleQueueJoinAPIMessage(
  userId: UserId,
  request: BattleQueueJoinRequest,
  queueService: BattleQueueService,
  authorizationService: BattleQueueJoinAuthorizationService
) extends APIWithTokenMessage[BattleQueueSnapshotResponse]
```

迁移后：

```scala
final case class BattleQueueJoinAPIMessage(
  userId: UserId,
  request: BattleQueueJoinRequest
) extends APIWithTokenContextMessage[BattleQueueJoinAPIContext, BattleQueueSnapshotResponse]
```

现在 `BattleQueueJoinAPIMessage` 本身只携带 request DTO 和 token 注入后的身份数据。`BattleQueueService` 与 `BattleQueueJoinAuthorizationService` 通过 `BattleQueueJoinAPIContext` 显式传入 `plan(context, connection)`。

### 验证结果

```text
npm run backend:compile        passed
npm run backend:test-contracts passed
```

### 结论

queue 三个 API 的 runtime dependency 状态现在是：

- `BattleQueueJoinAPIMessage`：service 依赖已迁入 typed context。
- `BattleQueueStatusAPIMessage`：service 依赖已迁入 typed context。
- `BattleQueueLeaveAPIMessage`：service 依赖已迁入 typed context。

这说明 queue APIMessage 已经不再把 runtime service 当作 message 字段。下一步可按同样方案迁移 room APIMessage，因为它们也依赖 `BattleQueueService`。

## 已完成：BE-BATTLE-CONTEXT-REGISTER-22

本轮继续 typed context 注册方案，迁移 `BattleRoomSnapshotAPIMessage`。

### BattleRoomSnapshotAPIMessage 变更

迁移前：

```scala
final case class BattleRoomSnapshotAPIMessage(
  userId: UserId,
  roomId: RoomId,
  queueService: BattleQueueService
) extends APIWithTokenMessage[RealtimeRoomSnapshotResponse]
```

迁移后：

```scala
final case class BattleRoomSnapshotAPIMessage(
  userId: UserId,
  roomId: RoomId
) extends APIWithTokenContextMessage[BattleQueueService, RealtimeRoomSnapshotResponse]
```

现在 `BattleRoomSnapshotAPIMessage` 本身只携带请求数据和 token 注入后的身份数据，不再携带 `BattleQueueService`。`BattleQueueService` 通过 `apiWithTokenAndContext` 显式传入 `plan(queueService, connection)`。

### 验证结果

```text
npm run backend:compile        passed
npm run backend:test-contracts passed
```

### 结论

`BattleRoomSnapshotAPIMessage` 已经与 queue status/leave 一样完成 typed context 化：

- message 不携带 service 字段。
- service 依赖通过显式 context 进入 `plan(context, connection)`。
- request/response JSON shape 不变。
- route 仍不手写 API path string。

下一步可迁移 `BattleRoomHeartbeatAPIMessage`，完成 room 域两个 API 的 context 化。

## 已完成：BE-BATTLE-CONTEXT-REGISTER-23

本轮继续 typed context 注册方案，迁移 `BattleRoomHeartbeatAPIMessage`。

### BattleRoomHeartbeatAPIMessage 变更

迁移前：

```scala
final case class BattleRoomHeartbeatAPIMessage(
  userId: UserId,
  command: RealtimeRoomHeartbeatCommand,
  queueService: BattleQueueService
) extends APIWithTokenMessage[RealtimeRoomSnapshotResponse]
```

迁移后：

```scala
final case class BattleRoomHeartbeatAPIMessage(
  userId: UserId,
  command: RealtimeRoomHeartbeatCommand
) extends APIWithTokenContextMessage[BattleQueueService, RealtimeRoomSnapshotResponse]
```

现在 `BattleRoomHeartbeatAPIMessage` 本身只携带 token 注入后的身份数据和已构造的 heartbeat command，不再携带 `BattleQueueService`。`BattleQueueService` 通过 `apiWithTokenAndContext` 显式传入 `plan(queueService, connection)`。

### 验证结果

```text
npm run backend:compile        passed
npm run backend:test-contracts passed
```

### 结论

room 两个 API 已完成 typed context 化：

- `BattleRoomSnapshotAPIMessage`
- `BattleRoomHeartbeatAPIMessage`

queue 与 room 这两个 runtime-backed 域现在都不再把 `BattleQueueService` 作为 message 字段。当前仍需要处理的 runtime-backed API 主要是：

- `BattleStateReadAPIMessage`
- `BattleCommandAPIMessage`

它们都依赖 `BattleStateService`，可按同样方式迁移到 `APIWithTokenContextMessage[BattleStateService, Response]`。

## 已完成：BE-BATTLE-API-CATALOG-25

本轮按 sample 的 `List[RegisteredAPIMessage]` 风格继续收敛 battle API catalog，重点处理 `BattleStateReadAPIMessage`。

### BattleStateReadAPIMessage 变更

迁移前：

```scala
final case class BattleStateReadAPIMessage(
  userId: UserId,
  battleId: BattleId,
  stateService: BattleStateService
) extends APIWithTokenMessage[BattleStateResponse]
```

迁移后：

```scala
final case class BattleStateReadAPIMessage(
  userId: UserId,
  battleId: BattleId
) extends APIWithTokenContextMessage[BattleStateService, BattleStateResponse]
```

现在 `BattleStateReadAPIMessage` 不再携带 `BattleStateService`。它通过 `RegisteredAPIMessage.apiWithTokenAndContext` 注册，API name 继续由 `BattleStateReadAPIMessage` 类型名自动推导为 `/api/battlestateread`，不需要 route catalog 里出现手写 `String`。

### 验证结果

```text
npm run backend:compile        passed
npm run backend:test-contracts passed
```

### 结论

当前 battle route catalog 的方向已经和 sample 一致：对外是 `List[RegisteredAPIMessage]`，不是 `List[String]`。剩余未完全迁移的是 `BattleCommandAPIMessage`，它仍然通过 `apiWithTokenFromJson` 构造并携带 `BattleStateService`，下一步应该按同样方式改成 typed context + Decoder。

## 已完成：BE-BATTLE-API-CATALOG-26

本轮继续按 typed APIMessage catalog 风格迁移 `BattleCommandAPIMessage`。

### BattleCommandAPIMessage 变更

迁移前：

```scala
final case class BattleCommandAPIMessage(
  userId: UserId,
  command: BattleCommandRequest,
  stateService: BattleStateService
) extends APIWithTokenMessage[BattleCommandAcceptedResponse]
```

迁移后：

```scala
final case class BattleCommandAPIMessage(
  userId: UserId,
  request: BattleCommandAPIRequest
) extends APIWithTokenContextMessage[BattleStateService, BattleCommandAcceptedResponse]
```

`BattleStateService` 不再作为 message 字段存在，而是由 `RegisteredAPIMessage.apiWithTokenAndContext` 注入 `plan(stateService, connection)`。`BattleCommandAPIMessage` 的 Decoder 只负责把 wire request 解成 typed request DTO 和注入后的 `UserId`；`BattleCommandRequest` 仍在 `plan` 内通过 ADT 错误分支构造，避免把 `MissingTicket`、非法字段等业务失败压进 route 或字符串 path catalog。

### 验证结果

```text
npm run backend:compile        passed
npm run backend:test-contracts passed
```

### 结论

queue、room、state、command 这四组 runtime-backed battle API 现在都不再把 runtime service 放进 message 字段。当前 `apiWithTokenFromJson` 的剩余使用只在 result repository-backed 兼容路径：

- `BattleResultListAPIMessage.registered(resultRepository)`
- `BattleResultRecordAPIMessage.registered(resultRepository)`

下一步可以继续处理 result repository-backed API，或者先整理 `BattleRoutes.apiMessages`，让 connection-backed 与 repository-backed 结果注册的边界更清晰。

## 已完成：BE-BATTLE-API-CATALOG-27

本轮处理 result API 的 repository-backed 兼容注册残留，移除 battle API 下最后的 `apiWithTokenFromJson` 使用。

### BattleResultListAPIMessage 变更

迁移前：

```scala
final case class BattleResultListAPIMessage(
  userId: UserId,
  query: BattleResultListQuery,
  storage: BattleResultStorage = BattleResultStorage.ConnectionTable
) extends APIWithTokenMessage[BattleResultListResponse]
```

迁移后：

```scala
final case class BattleResultListAPIMessage(
  userId: UserId,
  query: BattleResultListQuery
) extends APIWithTokenContextMessage[BattleResultStorage, BattleResultListResponse]
```

### BattleResultRecordAPIMessage 变更

迁移前：

```scala
final case class BattleResultRecordAPIMessage(
  userId: UserId,
  command: BattleResultRecordCommand,
  storage: BattleResultStorage = BattleResultStorage.ConnectionTable
) extends APIWithTokenMessage[BattleResultRecordResponse]
```

迁移后：

```scala
final case class BattleResultRecordAPIMessage(
  userId: UserId,
  command: BattleResultRecordCommand
) extends APIWithTokenContextMessage[BattleResultStorage, BattleResultRecordResponse]
```

`BattleResultStorage` 现在作为 typed context 注入 `plan(storage, connection)`。connection-backed 和 repository-backed 两条路径都通过 `registered(...)` 生成 `RegisteredAPIMessage`，route catalog 不再需要 `apiWithToken[...]` 直接注册 result message，也不再需要 `apiWithTokenFromJson`。

### 验证结果

```text
npm run backend:compile        passed
npm run backend:test-contracts passed
```

### 结论

当前 `services/battle/api` 下已无 `apiWithTokenFromJson` 残留。battle API catalog 的注册形态已经统一到 typed `RegisteredAPIMessage`：

- queue/room/state/command 通过 runtime service typed context；
- results 通过 `BattleResultStorage` typed context；
- API name 由 `APIMessage.apiNameFromClass[Message]` 根据 message 类型名推导。
