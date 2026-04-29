# Now

更新时间：2026-04-29 Asia/Shanghai

## 当前阶段

项目已经完成 `GameScene` 硬解耦，并进入“非 BattlePage 素材的产品闭环与扩展性推进”阶段。BattlePage 的角色、地图、箱子、地面等素材暂时冻结，不再继续替换审美素材；除非是明确 bug、手感回归或武器机制缺失，否则不动战斗页素材层。

当前总原则：

- 类型安全、声明式边界、前后端 API 同名、可拆微服务方向继续保持。
- BattlePage 素材冻结，但战斗规则、武器机制、数据闭环、入口防护、扩展性、后台审计可以继续推进。
- 课程代码风格大整理暂缓，等功能闭环稳定后再集中处理不可变性、DTO、Parser、Renderer、Scala `var/val` 等风格问题。
- 架构推进采用“一张票、一个 worker、一轮审查、一个合并决定”，避免多条业务线同时改导致语义漂移。

## 当前可以玩到什么程度

已登录账号可以进入 authoritative multiplayer battle，与服务器同步的玩家和 bot 对战。滚轮/数字切枪、手枪、火箭炮、加特林、霰弹枪、技能、拾取、击中反馈、火箭 AoE、加特林热量和后坐力等已经进入可玩状态。BattlePage 已恢复到原来的 Kenney top-down PNG 玩家风格，不再使用之前偏离预期的昆虫/虫壳方向素材。

对局结束后的基础闭环已经打通：战报、回放、rating/profile、站内信都可以生成和读取。Visitor/访客正式开战、写入战绩、写入回放、写入评分等路径已经有前后端防护；历史 Visitor-like 脏数据已经清理过一轮。

大厅正在从普通功能入口推进到“金属战争指挥台”方向。当前不是最终美术版，但已经有更密集的状态卡、战备信息、邮件/好友/回放摘要、中部 CTA 和 Operation Deck。下一步可以继续打磨大厅结构，但不触碰 BattlePage 素材。

## 最近已完成

- `GameScene` 硬解耦完成，并生成 `docs/GAMESCENE_HARD_GATE_COMPLETION_REPORT.md`。
- BattlePage 渲染与手感完成阶段性收口，当前素材层冻结。
- 新增 `npm run dev:status` 与 `npm run dev:start`，可诊断并启动缺失的前后端服务，不主动杀进程。
- 好友入口第一轮接入真实 friend request preview，badge 只统计当前用户收到的待处理请求。
- 站内信第二轮优化完成：概览统计、战报筛选、合并战报提示、批量标记已读、失败回滚。
- 大厅视觉结构第二轮完成：新增“战备指挥台 / Operation Deck”，展示账号在线状态、评分、未读邮件、好友待处理、最近回放等真实数据。
- 扩展性第二轮完成：新增社区 bot package manifest 示例、离线 package audit、README/SDK 文档。外部贡献者可以按 `*.plugin.json + .mjs strategy` 格式提交 bot，不需要改核心战斗代码。
- 扩展性第三轮完成：`audit:battle-contracts` 现在会校验地图边界、ID 唯一性、拾取物引用、武器数值范围、热量武器字段、技能 key/effect/activation 必填字段，并在失败时输出明确路径。
- 数据闭环第二轮完成：`audit:data-closure` 现在不仅检查 Visitor-like/non-playable 和 duplicate battle result，还会检查 rating 算术、按 handle 的 rating 连续性、replay/result 关联、battle mail 覆盖，并且保持只读。

## 当前数据风险

最新 `npm run audit:data-closure` 暴露的是历史数据一致性问题，不是当前构建失败：

- Visitor-like/non-playable：battle results、mails、replay records、identity accounts 当前均为 0。
- Duplicate battle result：0。
- Rating arithmetic：0 个算术错误。
- Rating continuity：10 个历史断点，主要是旧数据或旧逻辑重置导致的 `ratingBefore` 与上一场 `ratingAfter` 不连续。
- Replay/result association：54 条 replay 找不到对应 result，样例里包含旧 bot/contract smoke 数据。
- Battle mail coverage：22 条历史 result 找不到 battle mail，主要是旧 `replay-*` 结果。

这些问题下一步应分成“历史数据迁移/清理”和“线上写入硬防护”两类处理，不能在审计脚本里偷偷修。

## 当前风险与注意事项

- 如果 8080 已有 `BackendApp` 运行，再执行 `sbt run` 出现 `Address already in use` 是端口占用，不等于后端代码损坏。
- 如果 5173 有多个 Vite listener，说明前端可能被重复启动过；优先用 `npm run dev:status` 判断状态。
- Vite build 目前有既有 warning：React Router `"use client"`、`battleTruthStore` 动静态 import chunk、chunk size 大于 500 KB；它们不是本轮新增失败。
- BattlePage 素材方向已锁住，避免再次偏离用户审美预期。

## 下一步计划

1. 数据闭环第三轮：预计 2-4 小时。
   目标：把第二轮审计暴露的问题转成明确修复策略。优先做只读报告和可回滚 migration 脚本，不直接静默改 `backend/data`；重点处理 rating continuity 断点、旧 replay/result 缺口、旧 battle mail 缺口。

2. 大厅视觉结构第三轮：预计 3-6 小时。
   目标：继续靠近参考图的金属战争大厅结构，强化榜单、玩家卡、邮件/好友入口和中心开始区的版面密度。只使用程序化 CSS 与现有资源，不触碰 BattlePage 素材。

3. 地图/武器/技能扩展协议第三轮：预计 4-8 小时。
   目标：把地图、武器、技能、bot package 的贡献边界继续文档化和脚本化，形成更清楚的社区扩展接口，减少外部贡献者误改核心 runtime 的风险。

4. 站内信/好友后续：预计 3-6 小时。
   目标：先整理好友消息模型边界、实时同步策略和 UI 入口；真正聊天系统可以后置，不在当前轮次强行铺大。

5. 课程代码风格整理：暂缓。
   目标：等用户确认老师要求后，再统一处理不可变性、模块边界、DTO 契约、前后端 API 同名和微服务拆分形式。

## 当前执行策略

下一张票优先做数据闭环第三轮，因为第二轮审计已经给出可量化风险，继续推进可以提高 battle result、replay、mail、rating/profile 的可信度。完成并审查后，再切到大厅视觉第三轮或扩展协议第三轮。
