# Backend Rewrite Handoff

更新时间：2026-04-30 Asia/Shanghai

## 当前状态

前端和后端开发进程已关闭，8080 与 5173 端口已释放。

旧后端文件夹已从 `backend/` 更名为 `backend-legacy/`。这是有意为之：

- 把 `backend-legacy/` 改回 `backend/` 后，旧 Scala 后端理论上仍可按原方式运行。
- 但当前目标不是继续修旧后端，而是在根目录重新创建新的 `backend/`，完整重写后端。
- `backend-legacy/` 是参考实现、历史数据来源和迁移样本，不应在重写阶段作为主开发目录继续演进。
- 当前 `package.json` 的 `backend:dev` 仍指向 `backend`，因此在新后端创建前该脚本不可用，这是预期状态。

## 数据与数据库

旧后端数据位于 `backend-legacy/data/`，必须谨慎对待：

- `identity-accounts.json`
- `battle-results.json`
- `replay-records.json`
- `mails.json`
- `friend-requests.json`
- `forum.json`
- `bot-profiles.json`
- `governance-contribution-adjustments.json`
- `governance-review-notifications.json`
- 以及若干 `.bak-*` 历史备份。

旧后端也支持 Postgres 环境变量：

- `SLAY_DEMO_DATABASE_URL` 或 `DATABASE_URL`
- `SLAY_DEMO_DATABASE_USER`
- `SLAY_DEMO_DATABASE_PASSWORD`

新后端需要明确数据策略：继续兼容这些 JSON 文件、设计迁移脚本，或建立新的存储层。不要静默覆盖 `backend-legacy/data/`。

## 旧后端 API 面

前端当前依赖的主要 HTTP 路径来自旧 `BackendApp`：

- `GET/HEAD /health`
- `/identity/register`
- `/identity/session`
- `/identity/me`
- `/identity/accounts`
- `/battle/queue`
- `/battle/rooms`
- `/battle/state`
- `/battle/commands`
- `/battle/results`
- `/social/friend-requests`
- `/social/friend-requests/respond`
- `/governance/contribution-adjustments`
- `/governance/admin-notifications`
- `/forum/topics`
- `/bots/profiles`
- `/bot/profiles`
- `/mails`
- `/mails/read`
- `/replay/catalog`

新后端必须先对齐前端 gateway 的真实调用契约，再决定是否保留这些路径或提供兼容层。原则上前后端 API 名称、字段和语义必须同名对齐。

## 当前产品进度

已完成：

- `GameScene` 硬解耦完成，旧报告在 `phase-02-gamescene-hard-decoupling/`。
- Authoritative multiplayer battle 已可玩。
- Battle result、replay、rating/profile、mails 基础闭环已经打通。
- Visitor/访客禁止正式开战已有前后端防护方向。
- Bot SDK、battle content contract audit、data closure audit/repair-plan 已建立基础。
- BattlePage 素材当前冻结，不作为后端重写阶段目标。

当前明显问题：

- 旧后端是 Scala 3 + JDK HttpServer + 文件/Postgres repository 混合实现，已经积累过多历史妥协。
- 用户即将用新的 `AGENTS.md` 覆盖编码规则，后端需要按新的课程/工程风格重写。
- 历史数据存在已知一致性问题，见 `phase-04-product-data-extension/DATA_CLOSURE_AUDIT.md` 与 `DATA_CLOSURE_REPAIR_PLAN.md`。

## 后端重写目标

新 Codex 的主线不是小修旧后端，而是重建 `backend/`：

1. 先阅读新的 `AGENTS.md`，严格按用户新的编码规则执行。
2. 建立清晰后端架构：类型安全、声明式、微服务边界、薄 routes、强 contracts、可测试 service/repository。
3. 对齐前端 API，避免前端/后端字段名漂移。
4. 保护并迁移 `backend-legacy/data/`，不要无备份写历史数据。
5. 优先恢复最小可运行闭环：identity/session、battle queue/room/state/commands/results、replay、mails、rating/profile。
6. 再逐步恢复 forum、governance、social、bot profiles。
7. 保持 Visitor 禁战：未登录/Visitor/guest/anonymous/空 handle 不能创建正式 battle ticket、不能写 result/replay/rating/profile。

## 建议启动顺序

1. 读根目录新的 `AGENTS.md`。
2. 读根目录 `now.md`。
3. 读本文件。
4. 读 `NEXT_CODEX_BACKEND_REWRITE_PROMPT.md`。
5. 只读检查 `frontend/src/features/**` gateway，反推前端需要的 API contracts。
6. 只读检查 `backend-legacy/README.md`、`backend-legacy/src/main/scala/slaydemo/backend/BackendApp.scala`、`backend-legacy/data/`。
7. 写新的 backend 重写计划，然后创建新的 `backend/`。

## 工作方式要求

新 Codex 是总设计师 / 总架构师 / 总规划师，不是杂活工。

- 架构、边界、风险和合并决策由主 Codex 负责。
- 大量阅读、迁移、文件编辑、测试修复可交给子 agent。
- 一次只推进一个明确 ticket，避免多个 worker 同时改同一边界。
- 不在乎额度；如果无事可做，就继续读上下文、拆计划、推进下一项。
- 除非用户明确叫停，或者出现真正 hard stop，否则持续推进。

## 不要做的事

- 不要继续美术素材替换。
- 不要把 `backend-legacy/` 当作新主线继续堆改。
- 不要静默迁移/清洗历史数据。
- 不要绕过前端 API contract。
- 不要因为旧文档很多就机械执行旧计划；当前阶段是后端重写。
