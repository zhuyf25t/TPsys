# Now

更新时间：2026-04-28 Asia/Shanghai

## 当前状态

项目当前处在“扩展性与数据闭环收口”阶段。

已经完成的基础可玩闭环：

- 已登录账号可以进入 authoritative multiplayer battle，同一局内进行服务器权威对战。
- BattlePage 已完成阶段性渲染打磨：本地玩家和远端玩家的显示链路、枪口反馈、弹道、武器栏、拾取、火箭 AoE、加特林热量、后坐力、击中反馈都已经进入可玩状态。
- `GameScene.ts` 已完成硬解耦验收，现在是 scene shell / renderer host / glue layer。报告见 `docs/GAMESCENE_HARD_GATE_COMPLETION_REPORT.md`。
- battle result、replay、rating、profile、mails 已形成基础闭环：对局结束后会生成战报、回放、评分变化和站内信。
- Visitor 正式开战和正式写入已经在后端拦截；本轮又补上了前端 rating/profile/replay 展示边界、本地 battle truth fallback 边界和后端投影容错，避免 Visitor/访客虚拟身份进入榜单、档案、回放、站内信和评分写入链路。

还没有完成到最终愿景的部分：

- 美术仍以程序化绘制和现有素材为主，还没有达到最终“金属战争 + 空洞骑士剪影 + 技能特效”的统一资产质量。
- 地图、武器、技能、bot 虽然已经开始 catalog/profile/SDK 化，但还没有做到完整内容包、社区 bot 插件或地图编辑器级别。
- 数据闭环仍需要继续清理历史脏数据、重复投影边界、同账号多标签占位和 result/replay/rating/profile 一致性。
- 聊天系统暂缓；课程风格的大范围类型安全/声明式/微服务整理也暂缓，等功能闭环更稳后再集中做。

## 最近完成

### GameScene 硬解耦

已完成。`GameScene.ts` 当前不再直接实现 arena builder、world view factory、projectile update、hit/damage/respawn、pickup lifecycle、weapon runtime、display label、geometry resolver 等主业务链。

### Battle 内容扩展入口

已完成第一轮：

- 后端 battle 内容抽到 `BattleContentCatalog`。
- 前端 battle 内容抽到 `battleContentCatalog.ts`。
- 前端地图默认配置抽到 `battleMapCatalog.ts`。
- 前端本地 weapon runtime profile 已落地。
- 前端 skill runtime profile 已落地。
- bot SDK 最小边界已落地，可通过 strategy command 接入外部 bot。

### Authoritative Gatling

已完成。前后端都使用 heat / overheat 模型，不再是前端热量、后端弹匣的双语义。

### Visitor 数据展示边界

已完成本轮第一刀：

- 新增 `frontend/src/features/identity/identityHandlePolicy.ts`。
- `ratingGateway` 过滤空 handle、Visitor、guest、anonymous、访客、游客、未登录等 visitor-like 身份。
- `profileGateway` 对空/Visitor-like handle 直接返回 `undefined`，不再把空 handle 补成“访客”档案。
- `replayGateway` 不再使用 Visitor-like handle 做 rating hydration 查询。
- 文档见 `docs/DATA_CLOSURE_VISITOR_GUARDRAILS.md`。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。

### 后端 Visitor-like 身份边界

已完成本轮第二刀：

- 新增 `backend/src/main/scala/shared/rules/HandleRules.scala`。
- 后端 visitor-like 判断现在覆盖空身份、`Visitor`、`guest`、`anonymous`、`anon`、`访客`、`游客`、`未登录`。
- `BattleRules.isVisitorHandle` 保留老 API，但内部委托共享规则。
- `DefaultIdentityService` 注册、登录、session、账号列表会过滤 visitor-like 正式账号；内置 `admin` 不受影响。
- 文档见 `docs/BACKEND_VISITOR_HANDLE_GUARDRAILS.md`。

验证：

- `npm run backend:compile` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。

### Mail 服务层 Visitor-like owner 边界

已完成本轮第三刀：

- `DefaultMailService.list` 对空 owner 或 visitor-like owner 返回空列表，不读取 repository。
- `DefaultMailService.markRead` 对空 owner 或 visitor-like owner 返回 `false`，不触发 repository。
- `DefaultMailService.create` 对空 owner 或 visitor-like owner 不保存，仅返回原 record，保持调用方签名不变。
- 这会隐藏历史本地 `backend/data/mails.json` 中的 Visitor 邮件，也阻止未来继续写入 visitor-like 邮件。
- 文档已补充到 `docs/BACKEND_VISITOR_HANDLE_GUARDRAILS.md`。

验证：

- `npm run backend:compile` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。

### Authoritative projection Visitor-like 容错

已完成本轮第四刀：

- `AuthoritativeBattleFinishProjector` 现在只给 playable human 生成正式 result。
- replay owner 选择顺序改为：可玩真人胜者、排名最高的可玩真人、server summary。
- 如果历史或异常状态中混入 Visitor-like 玩家，不会再因为第一个 Visitor-like result/replay 被拒绝而阻断同局真实账号的结算写入。
- `playersLine` 仍保留原始参赛文本，避免历史战局摘要丢信息。
- 文档已补充到 `docs/BACKEND_VISITOR_HANDLE_GUARDRAILS.md`。

验证：

- `npm run backend:compile` 通过。

### 前端本地 battle truth Visitor-like 边界

已完成本轮第五刀：

- `finalizeBattleAndPersist` 现在只把 `normalizePlayableIdentityHandle(getCurrentAuthHandle())` 解析出的 playable 身份作为正式本地结算身份。
- 未登录、Visitor、guest、anonymous、访客、游客、未登录等 visitor-like 结算只返回临时 disabled summary/replay，不写入本地战绩、站内信、rating/profile，也不参与后端 result/replay backfill。
- 最新战报、回放列表、rating/profile 分组、邮件读取和邮件 replay 来源映射都会过滤非 playable 历史记录，避免旧 Visitor 脏数据继续从读取侧露出。
- 文档已补充到 `docs/DATA_CLOSURE_VISITOR_GUARDRAILS.md`。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。

## 当前正在做

当前主线：数据闭环加固第六刀。

目标不是做大迁移，而是继续收紧真实对局数据的可信边界：

- 审计前端 auth/local fallback 入口，防止本地注册、登录、会话恢复再制造 Visitor-like 正式账号。
- 审计并处理 `backend/data` 与前端 localStorage 中历史 Visitor result/mail/replay 脏数据。
- 确认 replay/result/mail/rating 在服务重启、重复 projection、多标签页面下不会重复刷榜、重复刷信或写错账号。
- 优先做服务层过滤和幂等证明；只有必要时才做一次性数据清理脚本。

## 下一步计划

1. 前端 auth/local fallback 的 Visitor-like 入口防护。
   预计：1-2 小时。
   目标：本地注册、登录、session 恢复、fallback account 列表都复用 `identityHandlePolicy`，禁止 Visitor-like 身份成为 playable account。

2. 历史 Visitor 脏数据清理与服务层防护。
   预计：1-3 小时。
   目标：让历史 `backend/data` 与前端 localStorage 中的 Visitor result/mail/replay 不再污染当前榜单、档案、站内信和回放列表；如果清理文件风险可控，再做可审计的清理脚本或迁移。

3. Result/replay/rating/mail 幂等审计。
   预计：2-4 小时。
   目标：证明或修复重复投影、后端重启、同局多账号结算、同账号多标签页造成的重复写入问题。

4. Authoritative battle 规则小收口。
   预计：0.5-1 天。
   目标：检查一命模式、时间清零、武器拾取保留当前枪、滚轮切枪、火箭 AoE、加特林热量和后坐力是否在权威链路中完全一致。

5. 扩展性第二轮。
   预计：1-2 天。
   目标：把后端地图/武器/技能内容也进一步 profile 化，形成更清楚的前后端同名契约，为之后地图、技能、bot 社区做基础。

6. 主界面视觉重构第一轮。
   预计：1-2 天。
   目标：按参考图做金属大厅结构、核心 CTA、排行/档案/配装/邮件入口、背景机械动效和粒子层。

7. BattlePage 美术资产第一轮。
   预计：2-4 天。
   目标：建立“自然 + 金属战争 + 空洞骑士剪影”的统一战斗视觉语言，同时保持命中判定可读、弹道可读、技能范围可读。

8. 启动、验收、交付脚本。
   预计：0.5-1 天。
   目标：一键关闭旧进程、一键启动前后端、一键 build/backend compile/smoke，减少端口占用和 sbt pipe 误解。

## 暂缓事项

- 聊天系统暂缓。好友申请和站内信先维持现状，之后再统一 notification/message channel。
- 课程风格大重构暂缓。包括全项目 var/val 清理、JSON parser/renderer 大迁移、微服务边界大拆分、前后端 DTO 全量契约迁移。
- 大规模数值平衡暂缓。当前只做保守微调，最终手感需要实战试玩后再定。

## 总体时间判断

不包含课程风格大重构：

- 可展示完整闭环：约 2-4 天。
- 扩展性、数据闭环、主界面和基础美术统一到较完整状态：约 5-10 天。
- 接近商业级 polish：10 天以上，主要消耗在素材、动画、音效、平衡、稳定在线服务和反复试玩。

当前执行策略：继续推进数据闭环和扩展性，不切换到聊天系统，不做课程风格大改。
