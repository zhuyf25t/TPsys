# Battle apiTypes Boundary Audit

更新日期：2026-05-27

## 1. 票据

```text
ID: BE-BATTLE-APITYPES-BOUNDARY-07
Goal: 审计 services/battle/objects/apiTypes 是否仍有非 DTO/codec 逻辑，并规划最小迁移路径。
Verification:
  - npm run backend:compile passed
  - npm run backend:test-contracts passed
Result:
  - objects/apiTypes 当前基本只承载 DTO 与 Circe codec。
  - 审计发现的非平凡 helper 是 BattleStatePlayerResponse.mergeEncodedObjects。
  - 后续 BE-BATTLE-PLAYER-DTO-FLAT-08 已删除该 helper。
```

## 2. 后续实现票据

```text
ID: BE-BATTLE-PLAYER-DTO-FLAT-08
Goal: 将 BattleStatePlayerResponse 改为显式扁平 DTO，删除 mergeEncodedObjects，同时保持输出 JSON 字段完全不变。
Verification:
  - npm run backend:compile passed
  - npm run backend:test-contracts passed
Result:
  - BattleStatePlayerResponse 已改为显式扁平 DTO。
  - identity/control/weapon/vitals 四个中间分组 DTO 已删除。
  - mergeEncodedObjects 已删除。
  - BattleStateResponseRenderer 直接填充扁平 player response 字段。
```

## 3. 当前状态

`objects/apiTypes` 现在满足当前阶段的边界要求：

- request/response shape 由 `final case class` 表达。
- JSON 边界由 Circe `Encoder` / `Decoder` 表达。
- 没有 service/repository/table/http4s/IO/Connection 依赖。
- 没有 API path 判断。
- 没有业务状态转移规则。

`BattleStatePlayerResponse` 仍保持前端现有 flat player JSON contract，不改字段名、不改嵌套结构。

## 4. 剩余风险

apiTypes 中仍然存在手写 `Json.obj` encoder，这属于 codec 边界，不是业务逻辑；但如果继续追求更机械的结构，可以后续按 DTO 大小拆文件或引入更系统的 codec helper。

不建议为了去掉手写 encoder 而改变 player JSON 为 nested structure，因为这会破坏前后端 contract。

## 5. 下一步建议

```text
ID: BE-BATTLE-COMPLETION-AUDIT-09
Goal: 对照原始目标做一次 services/battle 完成度审计，列出已满足、未满足、无法安全强推的要求。
Boundary:
  - backend/src/main/scala/services/battle
  - problem 文档
Forbidden:
  - 不改业务行为
  - 不改 JSON 字段
  - 不改前端
Verification:
  - npm run backend:compile
  - npm run backend:test-contracts
Acceptance:
  - 明确四顶层目录是否成立
  - 明确 APIMessage 形态是否成立
  - 明确 objects/apiTypes 边界是否成立
  - 明确 routes val apiMessages 与 runtime context 的剩余差距
  - 明确 database Table/TableInitializer 覆盖范围
```
