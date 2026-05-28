# battle 四层迁移决策清单

更新时间：2026-05-28

这份清单用于在继续改 `services/battle` 源码前做决策。当前代码已经证明：

- `api/results` 已接近目标：`APIWithTokenMessage + plan(connection)`。
- `queue/room/state/command` 仍依赖 `APIWithTokenContextMessage` 和 runtime service。
- `microservices/` 仍有 90 个 Scala 文件，是最大的不符合项。
- `database/` 顶层目前只有 `results`，其余 Table/Initializer 仍在 `microservices/<domain>/database`。

## 必须决策的问题

### A. pure game rules 放在哪里

选项 A1：严格四层，只放 `api / objects / routes / database`。

结果：

- runtime/world/combat/actors/abilities 的 pure rule functions 必须放入 `objects/<domain>`。
- 满足四层要求。
- 代价是 `objects` 不再只是 passive data，会包含纯规则函数。

选项 A2：允许增加 `rules/` 或 `engine/`。

结果：

- domain data、pure rules、API、database 边界更干净。
- 代价是超出“只用四层”的严格要求。

我的建议：如果你坚持当前目标，选 A1。

### B. queue/session 是否现在就 PostgreSQL 化

选项 B1：现在就迁 queue/session 到 PostgreSQL。

结果：

- 可以最终删除 `BattleAPIRuntimeContext`。
- `BattleRoutes` 可以变成 `val apiMessages: List[RegisteredAPIMessage]`。
- 风险大，需要 queue/session/schema/contract test 一起迁。

选项 B2：先做目录四层化和 APIMessage 边界，之后再 PostgreSQL 化 queue/session。

结果：

- 风险小，可逐步验证。
- 短期仍保留 `APIWithTokenContextMessage`。
- 不会立刻完成最终目标。

我的建议：选 B2。如果你明确要求下一票就消灭 context service，则选 B1。

### C. `objects/apiTypes` 是否允许 private decoder helper

选项 C1：只允许 `final case class + object Response + deriveEncoder/deriveDecoder`。

结果：

- 文件更简单。
- 复杂 command request 的验证逻辑会被迫回到 APIMessage 或 route。

选项 C2：允许 `objects/apiTypes` 内有少量 private decoder helper。

结果：

- JSON contract 和字段级验证集中在 apiTypes。
- APIMessage 更薄。
- 更适合 `BattleCommandRequest` 这种复杂请求。

我的建议：选 C2。

### D. `BattleEnums.scala` 是否继续统一保留

选项 D1：短期保留统一 enum 文件。

结果：

- 保持 enum 单一事实来源。
- 避免迁移期间重复声明 enum。

选项 D2：现在按业务域拆 enum。

结果：

- 长期更局部。
- 当前风险高，容易出现 wire value drift。

我的建议：选 D1。

### E. `Table` 是否允许最小 JDBC 方法

选项 E1：`Table` 可以有 `save/list/load/upsert` 这类最小 JDBC 方法。

结果：

- APIMessage 不需要直接写 SQL。
- Table 是 persistence adapter，不是业务 service。
- 符合当前 `BattleResultTable` 风格。

选项 E2：`Table` 只放 SQL 字符串和 row mapping，不提供方法。

结果：

- 数据库文件更“纯”。
- 但 SQL 执行逻辑会被挤到 APIMessage 或其他层，容易让 APIMessage 变厚。

我的建议：选 E1。

## 推荐组合

低风险渐进组合：

```text
A1 + B2 + C2 + D1 + E1
```

含义：

- 先严格保留四层，不新增 `rules/engine`。
- 不立刻大规模迁 queue/session PostgreSQL。
- 允许 apiTypes 内有必要 decoder helper。
- 统一 enum 暂时保留。
- Table 保留最小 JDBC 方法。

更彻底但风险更高的组合：

```text
A1 + B1 + C2 + D1 + E1
```

含义：

- 下一阶段直接改 queue/session 状态存储。
- 目标更快接近 `plan(connection)`。
- 需要接受一次较大的 backend battle 迁移。

更长期可维护但超出四层的组合：

```text
A2 + B2 + C2 + D1 + E1
```

含义：

- 允许 `rules/engine`，结构更干净。
- 但不满足“只有四层”的严格口径。

## 如果你选推荐组合，下一票怎么做

建议票据：

```text
BE-BATTLE-FLAT-MICROSERVICES-RULECONFIG-01
```

目标：

- 只把 `microservices/<domain>/objects` 中的 rule config ADT 搬到 `objects/<domain>`。
- 只把 `microservices/<domain>/database` 中的 Table/Initializer 搬到 `database/<domain>`。
- 不动 queue/session 的 runtime service。
- 不改 JSON contract。

为什么先做这个：

- 它直接减少 `microservices` 残留。
- 不碰最危险的 authoritative state。
- 能先验证四层路径是否可持续。

验收：

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
rg "services\.battle\.microservices\.(abilities|actors|combat|runtime|world)\.(objects|database)" backend/src/main/scala -n
```

## 如果你选 B1，下一票怎么做

建议票据：

```text
BE-BATTLE-QUEUE-POSTGRES-01
```

目标：

- 新建 `database/queue/BattleQueueTable.scala`。
- 新建 `database/queue/BattleQueueTableInitializer.scala`。
- 让 `BattleQueueJoinAPIMessage`、`BattleQueueStatusAPIMessage`、`BattleQueueLeaveAPIMessage` 走 `plan(connection)`。
- 暂时不动 room/state/command。

验收：

```text
sbt compile
sbt "Test/runMain route.contract.BackendContractTestRunner"
新增 queue table focused contract test
```

风险：

- 需要设计 ticket、room、heartbeat、lifecycle 的表结构。
- 需要处理并发 join/leave。
- 需要保证前端等待房间行为不变。

## 当前不能直接宣布目标完成的原因

目标要求的最终状态尚未满足：

- `services/battle/microservices` 仍存在 90 个 Scala 文件。
- `BattleRoutes` 仍是 `def apiMessages(context)`，不是纯 `val apiMessages`。
- queue/room/state/command API 仍然是 `APIWithTokenContextMessage`。
- 顶层 `database` 只有 results。
- queue/session 仍有内存状态和锁。

所以当前阶段只能进入“等待决策后继续迁移”，不能标记 goal complete。
