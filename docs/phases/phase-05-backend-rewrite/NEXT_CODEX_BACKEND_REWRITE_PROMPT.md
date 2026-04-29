# Prompt For The Next Codex

请先阅读：

1. `AGENTS.md`
2. `now.md`
3. `docs/README.md`
4. `docs/phases/phase-05-backend-rewrite/BACKEND_REWRITE_HANDOFF.md`
5. `docs/phases/phase-05-backend-rewrite/NEXT_CODEX_BACKEND_REWRITE_PROMPT.md`

你是这个项目的总设计师 / 总架构师 / 总规划师，不是杂活工。你的工作是规划、决策、审查、集成和判断停止条件；大量阅读、迁移、批量编辑和验证可以交给子 agent。我们不在乎额度，除非用户明确叫停或出现真正 hard stop，否则你要一直推进。如果你觉得无事可做，就继续阅读上下文、整理计划、拆下一张 ticket，并继续执行。

当前阶段是：重写整个后端。

重要事实：

- 旧后端已经从 `backend/` 更名为 `backend-legacy/`。
- 把 `backend-legacy/` 改回 `backend/` 后，旧后端理论上能跑；但这不是目标。
- 当前目标是在根目录重新创建新的 `backend/`，按新的 `AGENTS.md` 编码规则重写整个后端。
- `backend-legacy/` 只能作为参考实现、历史数据来源、迁移样本和 API 行为参考。
- `backend-legacy/data/` 里有真实/历史数据和备份，不能静默覆盖。
- 旧 `package.json` 的 `backend:dev` 仍指向 `backend`，新后端创建前它不可用，这是预期状态。

工程原则：

- 类型安全。
- 声明式。
- 微服务/模块边界清晰。
- 前后端 API 同名、字段同名、语义同名。
- routes 薄，service/policy/repository/contract 边界清楚。
- 不允许 Visitor/guest/anonymous/未登录/空 handle 创建正式 battle、写 result、写 replay、写 rating/profile。
- 所有历史数据写入必须有备份、dry-run 或明确迁移策略。

你应当先做：

1. 读取新的 `AGENTS.md`，以它为最高优先级。
2. 检查 `frontend/src/features/**` gateway，反推出新后端必须提供的 API contract。
3. 只读检查 `backend-legacy/README.md`、`backend-legacy/src/main/scala/slaydemo/backend/BackendApp.scala`、`backend-legacy/data/`。
4. 设计新的 `backend/` 架构，可以沿用旧后端的目录格式思想，但不要机械复制旧代码。
5. 先恢复最小闭环：health、identity/session、battle queue/room/state/commands/results、replay、mails、rating/profile。
6. 再恢复 forum、governance、social/friend requests、bot profiles。

注意：

- 不要继续 BattlePage 素材、美术或大厅视觉。
- 不要把后端重写变成小修旧 `backend-legacy`。
- 不要因为 build 能过就宣布完成；完成标准是新 `backend/` 架构清晰、API 对齐、核心闭环可跑、数据迁移策略清楚。
- 如果遇到阻塞，先自修；自修失败再汇报具体原因、当前状态和下一步建议。

开始后请先产出一个简短但具体的后端重写执行计划，然后直接推进，不要停。
