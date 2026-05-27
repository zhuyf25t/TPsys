# Battle API 类型安全重构解决方案

## 目标

本方案针对 `problem/5_26.md` 中记录的问题，目标是把 battle API 从“route 层手写 JSON + String DTO + 手动错误消息”调整为“typed APIPlanner + domain objects + Circe 边界 codec + 薄 HTTP adapter”。

最终结构应满足：

- `APIPlanner` 属于 `services.battle.api`，不是 `services.battle.routes`。
- API request / response 优先使用 `services.battle.objects` 中已有的 value object、enum、ADT。
- `String` 只允许出现在最终 JSON wire codec、兼容性文件格式、纯展示文案中。
- route 只负责 HTTP adapter，不负责 battle 业务 decode、service 调用细节和错误分支展开。
- 每个 API 都有明确的 typed message、typed response、typed error。
- wire shape 尽量不变，避免破坏前端现有契约。

## 目标目录结构

建议目标结构：

```text
backend/src/main/scala/
  route/battle/
    BattleHttp4sRoutes.scala

  services/battle/api/
    BattleAPIPlannerServices.scala
    BattleApiCodecs.scala
    BattleApiErrors.scala
    BattleQueueLeaveAPIPlanner.scala
    BattleQueueJoinAPIPlanner.scala
    BattleRoomSnapshotAPIPlanner.scala
    BattleStateReadAPIPlanner.scala
    BattleCommandAPIPlanner.scala
    BattleResultAPIPlanner.scala
```

保留：

- `route/battle/BattleHttp4sRoutes.scala`

删除或迁出：

- `services/battle/routes/BattleAPIMessageRegistry.scala`
- `services/battle/routes/BattleAPIMessageServices.scala`
- `services/battle/routes/BattleAPIMessageSupport.scala`
- `services/battle/routes/*APIMessage.scala`

`route/battle/BattleHttp4sRoutes.scala` 最终只应做一件事：

```scala
APIMessageRouter.routes(BattleAPIMessageRegistry.registered(services))
```

它不应该知道具体 battle API 的 decode、业务错误、service 方法和 response 结构。

## 核心设计

### 1. APIPlanner 是 API contract 执行中心

每个 battle API 应被建模为 typed request/response 加一个 planner：

```scala
final case class BattleStateReadRequest(
  battleId: BattleId
)

final case class BattleStateReadAPIPlanner(...):
  def plan(request: BattleStateReadRequest): IO[BattleStateReadResponse] =
    for
      state <- readState(request.battleId)
      response <- buildResponse(state)
    yield response
```

request/response 字段应直接使用 domain object：

- `BattleId`
- `PlayerId`
- `TicketId`
- `RoomId`
- `BattleMode`
- `WeaponKind`
- `BattlePhase`
- `BattleCommandStatus`
- `BattleCommandReason`

不要在 request/response 内部使用：

```scala
battleId: String
playerId: String
ticketId: Option[String]
weaponKind: String
phase: String
```

`plan` 内必须使用 full for-comprehension。分支逻辑、错误映射和条件判断下沉到有名字的 private helper，不在 `plan` 主体里写 `if else`。

### 2. Codec 是 wire string 的唯一合法位置

需要在 `BattleApiCodecs.scala` 中集中提供 Circe codec：

```scala
given Decoder[BattleId]
given Encoder[BattleId]
given Decoder[PlayerId]
given Encoder[PlayerId]
given Decoder[TicketId]
given Encoder[TicketId]
given Decoder[BattleMode]
given Encoder[BattleMode]
given Decoder[WeaponKind]
given Encoder[WeaponKind]
```

这些 codec 可以使用 `value`、`wireValue`、`fromWire`，但 API case class 字段本身不应该是 `String`。

原则：

- Decode 阶段：JSON string -> domain object。
- API / application 阶段：一直保持 domain object。
- Encode 阶段：domain object -> JSON string。

### 3. Error 使用 typed ADT，不直接传 message string

每个 API 应有明确错误 ADT，例如：

```scala
enum BattleCommandAPIError:
  case InvalidJsonObject
  case MissingTicket
  case BattleNotFound
  case PlayerNotFound
  case BotCommandsNotSupported
  case CommandNotAuthorized
```

错误 ADT 再统一映射到：

- HTTP status
- machine-readable error code
- human-readable message

不要在业务分支里直接写：

```scala
badRequest("player_not_found")
notFound("battle_not_found")
forbidden("command_not_authorized")
```

应该先得到 typed error，再由 presenter/encoder 统一渲染。

### 4. apiTypes 不再作为 DTO 大杂烩

当前 `BattleCommandApiTypes.scala`、`BattleQueueApiTypes.scala`、`BattleStateApiTypes.scala` 等文件承担了过多职责。

重构后它们应逐步拆成：

- APIMessage：请求入口和 plan。
- Response model：typed response。
- Error ADT：typed API error。
- Codec：wire JSON 编解码。

如果文件体积不大，可以按业务 API 聚合在一个 `BattleCommandAPIMessage.scala` 中；如果体积过大，再拆出 `BattleCommandApiCodecs.scala` 和 `BattleCommandApiErrors.scala`。

不要继续维护一个包含所有 request、response、codec、projection helper 的 `ApiTypes` 大文件。

## 迁移阶段

### Phase 0：建立基线

目的：确保后续每一步都知道是否破坏现有功能。

操作：

- 运行 backend compile。
- 运行现有 contract tests。
- 记录当前 battle API 路径：
  - `/api/battlequeuejoin`
  - `/api/battlequeuestatus`
  - `/api/battlequeueleave`
  - `/api/battleroomsnapshot`
  - `/api/battleroomheartbeat`
  - `/api/battlestateread`
  - `/api/battlecommand`
  - `/api/battleresultlist`
  - `/api/battleresultrecord`

验收：

- 得到明确 baseline。
- 不修改业务代码。

### Phase 1：迁移 APIMessage 所属包，不改行为

目的：先修正分层，不同时改变 contract。

操作：

- 将 `services.battle.routes.*APIMessage` 迁到 `services.battle.api`。
- 将 `BattleAPIMessageRegistry` 迁到 `services.battle.api`。
- 将 `BattleAPIMessageServices` 迁到 `services.battle.api`。
- 更新 `route/battle/BattleHttp4sRoutes.scala` 的 import。
- 保持 API name、URL、request JSON、response JSON 不变。
- 暂时允许原有 `BattleAPIMessageSupport` 迁到 `api`，但标记为后续删除对象。

验收：

- 编译通过。
- contract tests 通过。
- `services/battle/routes` 不再包含 APIMessage。

### Phase 2：建立 battle API codec 基础层

目的：让 domain object 可以直接作为 API 字段使用。

操作：

- 新建或整理 `BattleApiCodecs.scala`。
- 为 battle API 需要的 value object / enum 提供 Circe `Encoder` / `Decoder`。
- Decode 时校验空字符串、非法 enum wire value。
- Encode 时保持当前前端使用的 wire string 不变。

优先覆盖：

- `BattleId`
- `PlayerId`
- `TicketId`
- `RoomId`
- `BattleMode`
- `WeaponKind`
- `BattlePhase`
- `BattleCommandStatus`
- `BattleCommandReason`
- `SkillKind`
- `SkillOutcomeStatus`
- `ProjectileKind`
- `PickupKind`

验收：

- 不再需要在 API request case class 内部写 `battleId: String` 后再 `.map(BattleId.apply)`。
- codec 错误能被 APIMessageRouter 映射成合理 bad request。

### Phase 3：先迁移低风险 API vertical slice

目的：用最小 API 验证新模式。

优先顺序：

1. `BattleStateReadAPIMessage`
2. `BattleQueueStatusAPIMessage`
3. `BattleQueueLeaveAPIMessage`
4. `BattleRoomSnapshotAPIMessage`
5. `BattleRoomHeartbeatAPIMessage`

原因：

- 这些 API 输入较少。
- 更容易验证 value object decode。
- 不涉及复杂 command input 和技能状态。

每个 API 的改法：

- APIMessage case class 直接持有 typed domain object。
- `plan` 调用 application service。
- service error 先转 typed API error。
- typed response 保留 enum / value object。
- response encoder 最后输出原 wire JSON。
- 删除对应旧 decode helper。

验收：

- 每迁移一个 API，运行 backend compile 和对应 contract test。
- 前端请求路径和 JSON shape 不变。

### Phase 4：迁移 BattleCommandAPIMessage

目的：解决最关键的类型安全问题。

重点处理：

- `battleId: BattleId`
- `playerId: PlayerId`
- `ticketId: TicketId`
- `clientTick: BattleTick`
- `clientCommandSeq: ClientCommandSeq`
- `switchWeaponDirection: BattleWeaponSwitchDirection`
- `switchWeaponIndex: Option[BattleWeaponSwitchIndex]`
- `commandStatus: BattleCommandStatus`
- `commandReason: Option[BattleCommandReason]`
- `outcomes.action: SkillKind`
- `outcomes.status: SkillOutcomeStatus`
- `outcomes.reason: Option[SkillOutcomeReason]`

不要继续保留：

```scala
commandStatus: String
status: String
action: String
reason: Option[String]
```

这些只能出现在 encoder 输出 JSON 时。

验收：

- `BattleCommandAPIMessage` 不再通过 `Json => IO[Json]` 手动 decode。
- request decoder 直接产出 typed message。
- command accepted response 内部没有业务状态 `String`。
- 编译器能检查 response 状态映射是否穷尽。

### Phase 5：迁移 Queue Join 和 Result API

目的：处理输入更复杂、兼容性更多的 API。

Queue Join 重点：

- `handle` 应保持 identity domain 类型，例如 `PlayerHandle`。
- `sessionToken` 应保持 `SessionToken`。
- `modeId` 应 decode 为 `BattleMode`。
- `rating` 应使用 `Rating`。
- `avatar`、`skin` 如果只是资源 key，可以保留 `Option[String]`，但需要在注释或类型命名上说明它们是 presentation asset key，不是业务状态。

Result API 重点：

- `BattleId`、`BattlePlacement`、result status 不要用裸字符串。
- replay/result 文件格式如果需要兼容旧数据，可以保留在 persistence compatibility boundary，不要污染 APIMessage contract。

验收：

- queue 和 result API 的业务状态字段不再使用裸 `String`。
- legacy file JSON 兼容逻辑只留在 persistence 层。

### Phase 6：删除旧 support 和旧 apiTypes

目的：清理重复逻辑，避免新旧路径并存。

删除条件：

- 所有 battle APIMessage 都已迁到 `services.battle.api`。
- 所有 API 都通过 `RegisteredAPIMessage.api[...]` 或等价 typed registration 注册。
- 不再有 `BattleAPIMessageSupport.registered(className)(Json => IO[Json])`。
- route 层没有 battle decode / service error mapping。

可删除：

- `services/battle/routes/BattleAPIMessageSupport.scala`
- 旧 `services/battle/routes/*APIMessage.scala`
- 已被替代的 `Battle*ApiTypes.scala` 中的 String DTO 和 decode helper。

验收：

- `rg "Json => IO\\[Json\\]|BattleAPIMessageSupport|battleId: String|weaponKind: String|commandStatus: String" backend/src/main/scala/services/battle` 不再命中 API contract 中的业务状态字段。

## 保持前后端兼容的规则

本次重构优先改变 Scala 内部类型结构，不主动改变 wire JSON。

必须保持：

- API path 不变。
- request field name 不变。
- response field name 不变。
- enum wire value 不变。
- HTTP status 行为不变，除非旧行为本身明显错误并单独记录。

允许改变：

- Scala 内部 request / response 字段类型。
- APIMessage 所在 package。
- codec 实现方式。
- route 内部 import。
- tests 的组织结构。

不允许混入：

- 前端重构。
- gameplay 规则变化。
- battle engine 行为变化。
- 数据库 schema 变化。
- 新依赖版本升级。

## 推荐优先级

最高优先级不是一次性清空所有 `String`，而是先保证 APIMessage 链路变正确。

推荐顺序：

1. 移动 APIMessage 到 `services.battle.api`。
2. 建立 `BattleApiCodecs`。
3. 迁移 `BattleStateReadAPIMessage`。
4. 迁移 `BattleCommandAPIMessage`。
5. 迁移 queue/room/result 其余 API。
6. 删除 `BattleAPIMessageSupport` 和旧 `ApiTypes` 弱类型 DTO。

这个顺序高效的原因是：

- 第一阶段只改包结构，便于 review。
- 第二阶段建立复用 codec，减少后续重复。
- 第三个 API 输入简单，可以快速验证模式。
- 第四个 API 价值最高，直接解决 command 链路的状态字符串问题。
- 最后再清理，避免半迁移期间误删仍被使用的代码。

## 验证策略

每个 phase 至少运行：

```powershell
sbt backend/compile
```

涉及 API wire shape 时运行：

```powershell
sbt backend/test-contracts
```

如果当前 sbt alias 不同，应以 `build.sbt` 中实际任务为准。

每个迁移后的 API 应补充或更新 contract test，覆盖：

- 正常请求。
- 缺失必填字段。
- 非法 id。
- 非法 enum wire value。
- service 返回 not found / forbidden / bad request。
- response JSON 字段名和旧前端一致。

## 完成标准

完成后应满足：

- `services.battle.routes` 不再存在 battle APIMessage。
- `route/battle` 只保留 HTTP adapter。
- `services.battle.api` 是 battle API contract 的唯一入口。
- API request / response 内部字段优先使用 domain object。
- `wireValue` / `fromWire` 集中在 codec 边界。
- 业务错误用 typed ADT 表达。
- 前端 wire JSON 不被破坏。
- compile 和 contract tests 通过。

## 风险与控制

主要风险：

- 一次性迁移所有 API 容易破坏前端契约。
- 过早删除旧 apiTypes 可能遗漏仍在使用的 response projection。
- 把 legacy file JSON 和 API JSON 混在一起会继续污染 contract。
- 错误码如果顺手改名，会导致前端错误处理漂移。

控制方式：

- 每次只迁移一个 API vertical slice。
- 每个 API 迁移前后对比 JSON golden sample。
- 先迁移内部类型，再删除旧代码。
- legacy persistence codec 与 API codec 分开。
- 所有 wire value 变更必须单独开票，不混入本次类型安全迁移。
