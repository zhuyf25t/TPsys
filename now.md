# Now

更新时间：2026-04-30 Asia/Shanghai

## 当前阶段

项目已切换到“后端重写准备阶段”。

前端和后端开发进程已关闭，8080 与 5173 端口已释放。旧后端文件夹已从 `backend/` 更名为 `backend-legacy/`。当前根目录没有新的 `backend/`，这是有意状态：下一位 Codex 应按新的 `AGENTS.md` 编码规则重新创建并重写整个后端。

## 关键事实

- `backend-legacy/` 是旧 Scala 后端，保留为参考实现和历史数据来源。
- 把 `backend-legacy/` 改回 `backend/` 后，旧后端理论上可按原方式运行，但这不是当前目标。
- 当前目标是在根目录创建新的 `backend/`，重写后端。
- `backend-legacy/data/` 内有历史数据与备份，不能静默覆盖。
- `package.json` 里的 `backend:dev` 仍指向 `backend`；新后端创建前该脚本不可用，这是预期状态。
- 用户会用新的编码规则覆盖根目录 `AGENTS.md`；新 Codex 必须先读它。

## 当前项目能力概况

前端已有大厅、登录、配装、BattlePage、回放、站内信、评分榜、论坛、好友入口等页面和 gateway。BattlePage 的 authoritative multiplayer battle 已经能跑过一轮可玩闭环，支持武器、技能、拾取、bot、结算、replay、rating/profile、mails 等基础链路。

BattlePage 素材当前冻结，不作为后端重写阶段目标。大厅视觉也暂时不继续推进。

## 文档入口

当前 docs 已按阶段归档：

- `docs/README.md`
- `docs/phases/phase-01-foundation-and-product/`
- `docs/phases/phase-02-gamescene-hard-decoupling/`
- `docs/phases/phase-03-battle-rendering-authoritative/`
- `docs/phases/phase-04-product-data-extension/`
- `docs/phases/phase-05-backend-rewrite/`

新 Codex 的重点入口：

1. `AGENTS.md`
2. `now.md`
3. `docs/README.md`
4. `docs/phases/phase-05-backend-rewrite/BACKEND_REWRITE_HANDOFF.md`
5. `docs/phases/phase-05-backend-rewrite/NEXT_CODEX_BACKEND_REWRITE_PROMPT.md`

## 下一步

新 Codex 应作为总设计师 / 总架构师 / 总规划师接管，先根据新的 `AGENTS.md` 制定后端重写计划，然后创建新的 `backend/`。

优先恢复：

1. `health`
2. identity/session
3. battle queue/rooms/state/commands/results
4. replay
5. mails
6. rating/profile
7. social/forum/governance/bot profiles

原则：类型安全、声明式、微服务边界、前后端 API 同名、数据迁移可审查、Visitor 禁止正式开战。
