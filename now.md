# Now

更新时间：2026-04-28 Asia/Shanghai

## 当前主线

当前 BattlePage 程序化渲染与手感专项已达到阶段性收口标准，不再建议继续无限堆程序化特效。收口报告见 `docs/BATTLEPAGE_RENDERING_COMPLETION_REPORT.md`。

已完成目标：authoritative multiplayer battle 的实战画面做到本地反馈即时、远端同步平滑、武器与 projectile 可辨、VFX 不误导命中、HUD 不压视野、状态通道与特效通道清楚分离。

## 现在可以玩到什么程度

当前 BattlePage 已经是可多人联机的 authoritative battle 原型：

- 两个浏览器账号可以进入同一局，双方移动/开火会同步到同一场战斗。
- 本地玩家移动和枪口反馈走即时表现，不等待服务端回包才动。
- 远端玩家和远端 projectile 走 authoritative frame + interpolation，不把远端同步抖动套到本地手感上。
- 手枪、火箭筒、加特林、霰弹枪已经重新进入后端 authoritative runtime、前端 adapter、HUD 和 renderer 链路。
- 命中、撞墙、出界、射程耗尽已经有不同 terminal 表现；TTL 耗尽不再伪装成命中或撞墙。
- HUD 小地图静态层已经缓存，不在每帧重绘障碍物；右下角武器/技能面板已经压缩，减少遮挡。

这还不是最终美术完成版。现在是“可玩级程序化渲染/手感阶段性完成”，不是素材最终版，也不是完整商业级视觉收口。

## 本轮已完成

1. 多武器 authoritative 与渲染链路恢复。
   后端 authoritative runtime 已支持 `Pistol`、`RocketLauncher`、`Gatling`、`Shotgun` 的 pickup、current weapon、ammo 和 projectile kind。前端 authoritative adapter、frame bridge、snapshot applier、HUD presenter 和 world renderer 已接受这些 weapon kind。

2. 角色手中武器不再只是一条线。
   `worldViewFactory.ts` 中每个英雄现在有武器后托、主枪管和枪口节点三层；不同武器有不同长度、粗细、枪口半径、颜色和透明度。手枪短，火箭筒粗，加特林长而细，霰弹枪宽而钝。

3. 枪口反馈锚点对齐后端 projectile birth。
   前端本地枪口反馈和远端 projectile birth VFX 现在按后端公式镜像：`hero radius + projectile radius + 4px clearance`。这只改表现锚点，不改命中、伤害、碰撞或服务端判定。

4. 手枪 VFX 已降噪。
   普通手枪本地 tracer 现在是短、轻、低误导版本，关闭低收益 underglow / ghost / glint。之前截图里很炫的长白色束流已保留为未来特效名 `piercing-rail-tracer-long`，适合以后做狙击枪、穿透枪、轨道枪，不再作为手枪默认效果。

5. projectile / terminal 表现继续分层。
   手枪弹道短且轻；火箭更粗更亮；加特林更稳定；霰弹 pellet 更短更宽。命中/撞墙走 spark；TTL 或射程耗尽走轻量 dissipate ring，不暗示命中。

6. pickup 可读性增强。
   不同武器 pickup 有不同 halo / 底座颜色和尺寸；医疗包保持绿色。实战画面和小地图外的主画面更容易区分武器与补给。

7. 状态通道新增减速状态环。
   英雄如果被 authoritative slow field 覆盖，会显示独立冰青状态环。这是持续状态可读层，不是瞬时 VFX，也不反向修改 gameplay state。

8. HUD 信息层压缩。
   小地图、右下武器栏、技能栏、状态 chip、血条宽度和字体做了小幅压缩，减少挡视野。小地图静态层仍保持缓存。

9. render smoke 的 hit-dispute 误报已修。
   smoke 现在会优先检查 server/client hit target 的 `hpBefore/hpAfter/damage`，真实造成伤害时不会因为附近另一个非目标英雄而误报 near-but-no-damage。

10. 测试等待时间已从 10 秒压到 5 秒。
    前后端 matchmaking 时长已经统一改为 5 秒：后端 `BattleRules.MatchmakingDurationMs = 5_000L`，前端 `BATTLE_MATCHMAKING_DURATION_MS = 5_000`。render smoke 的输入事件探针兜底清理也从 10 秒改为 5 秒，后续自动化测试会更快进入实战段。

11. authoritative 多武器主链缺口已修。
    后端不再把 `currentWeaponIndex` 每次命令重置为 0，也不再把 `weapons` 压扁成单武器。拾取武器会加入或补给当前武器栏，`switchWeaponDirection` 会在服务端切换当前武器，前端 HUD / renderer 会跟随 authoritative state。

12. 火箭炮 AoE 与重武器后坐力已接回 authoritative runtime。
    火箭弹现在会在命中、撞墙、出界或 TTL 终点按 `splashRadius` 结算范围伤害；前端 rocket terminal pulse 半径对齐 `RocketLauncher.splashRadius`，不再只显示小火花。服务端开火后坐力也已接入：手枪、火箭炮、加特林、霰弹枪按各自 recoil strength 推动 authoritative 位置，避免本地表现和服务端状态打架。

13. 加特林高频 VFX 已完成第一轮降噪。
    本地加特林枪口反馈从重型 spark / 长 tracer 调成短、轻、低透明度版本；远端加特林 projectile birth 不再创建重 spark，改为轻量短 tracer；加特林 terminal 不再额外创建 spark / pulse。目标是保留开火方向可读性，但避免高射速武器每帧堆 transient 造成卡顿或 GC 感。

14. 火箭炮 AoE 视觉从大 pulse 改为 shockwave。
    火箭 projectile 的 hit / obstacle / world / ttl 终点现在都用 `RocketLauncher.splashRadius` 画 shockwave 半径圈，表达“这里发生范围爆炸”，而不是用一整块 filled pulse 压视野。这个改动只影响表现，不改变服务端伤害判定。

15. 权威模式滚轮切枪输入补强。
    `GameScene` 现在会在 Phaser wheel 和全局 wheel bridge 两条路径中都写入本帧 `pendingWeaponSwitchDirection`，外层 page fallback 仍保留。这样滚轮切枪不再只依赖页面级 input capture，HUD / runtime command 都能更稳定地拿到切枪方向。

16. 权威 HUD 武器栏补齐。
    权威战斗模式右下角武器栏不再只显示当前武器，而是显示完整 `playerHero.weapons` 列表，并按 `currentWeaponIndex` 标记当前武器。加特林不再误显示为 `0 / 0` 弹药，而是显示热量语义。

17. 权威武器切换 / 后坐力 API probe 通过。
    新注册测试账号进入真实 battle 后，出生点拾取加特林得到 `["Pistol","Gatling"]`；服务端 `switchWeaponDirection=1` 从 `Gatling/1` 切到 `Pistol/0`，`switchWeaponDirection=-1` 切回 `Gatling/1`；加特林开火后服务端位置产生 `dx=-2.88` 后坐力。结果已写入 `.runtime/authoritative-weapon-switch-recoil-probe.json`。

18. 本地 projectile terminal tracer 已降噪。
    本地玩家自己的 projectile 已经有即时 muzzle/tracer，因此服务端 terminal 回来时不再额外画 terminal tracer / correction tracer，避免形成两条平行但错位的轨迹。远端玩家 projectile 仍保留 terminal tracer，保证敌方火力可读；命中、爆炸、消散结果仍会显示。

19. 火箭炮拾取 / 开火 / terminal API probe 通过。
    新注册测试账号从出生点移动到火箭拾取点后，服务端武器栏为 `["Pistol","Gatling","RocketLauncher"]`，当前武器为 `RocketLauncher`；向 bot 开火后产生 rocket terminal，目标 HP `100 -> 40`，terminal damage `60`。结果已写入 `.runtime/authoritative-rocket-pickup-terminal-probe.json`。

20. 渲染收口报告已写入。
    `docs/BATTLEPAGE_RENDERING_COMPLETION_REPORT.md` 已记录当前可玩程度、本轮完成项、验证结果、保留特效命名、剩余边界和后续建议。

21. 站内信战报 / 评分通知合并。
    后端 battle result 现在只生成一封 `mail-battle-*` 邮件，主题为“战斗结算与评分更新”，正文同时包含战报和 rating 变化，不再额外生成 `mail-rating-*`。本地 fallback 也同步为单封 battle mail；旧数据不删除，而是在前端 mails gateway 中按 battle 合并显示。

22. 站内信页面布局收紧。
    `/mails` 顶部新增右侧快捷区，概览 chip 改为可点击筛选：总计、未读、已读、重要、好友申请。邮件卡片左侧显示状态 / 标题 / 摘要，右侧作为来源、回放、治理、好友申请操作区，减少大面积空白。

23. 旧战报邮件兼容已读。
    旧版同一局的 `mail-battle-*` 与 `mail-rating-*` 会被合并为一条 `MailSummary`，并保留 `relatedMailIds`。用户点击合并后的邮件时，会把关联旧邮件一起标记已读，避免旧评分邮件残留未读。

24. 拾枪规则改为保留当前武器。
    权威端和本地 fallback 都已改成：接触武器 pickup 只加入武器栏或补给该武器，不再自动切换当前武器。玩家需要滚动鼠标滚轮 / 发送 `switchWeaponDirection` 后才切到新武器。HUD 文案已同步为“加入武器栏，滚轮切换”。

## 当前验证结果

已通过：

```powershell
npm run build
npm run backend:compile
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bp28-render-feel-smoke.ps1 -Scenario MixedMovement -SummaryPath .runtime\render-pass-weapon-fix-mixed-summary.json -InputDurationMs 1200 -FrameSampleSeconds 1 -DisableGpu
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bp28-render-feel-smoke.ps1 -Scenario MixedMovement -SummaryPath .runtime\render-pass-matchmaking-5s-summary.json -InputDurationMs 1200 -FrameSampleSeconds 1 -DisableGpu
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bp28-render-feel-smoke.ps1 -Scenario StraightFire -SummaryPath .runtime\render-pass-pistol-vfx-lite-summary.json -InputDurationMs 4500 -FrameSampleSeconds 4 -DisableGpu
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bp28-render-feel-smoke.ps1 -Scenario MixedMovement -SummaryPath .runtime\render-pass-hud-compact-summary.json -InputDurationMs 3500 -FrameSampleSeconds 4 -DisableGpu
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bp28-render-feel-smoke.ps1 -Scenario SkillPressure -SummaryPath .runtime\render-pass-status-channel-summary.json -InputDurationMs 4500 -FrameSampleSeconds 4 -DisableGpu
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bp28-render-feel-smoke.ps1 -Scenario DualClientPressure -SummaryPath .runtime\render-pass-dual-client-summary.json -InputDurationMs 4500 -FrameSampleSeconds 4 -DisableGpu
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bp28-render-feel-smoke.ps1 -Scenario MixedMovement -SummaryPath .runtime\render-pass-vfx-gatling-lite-summary.json -InputDurationMs 1200 -FrameSampleSeconds 1 -DisableGpu
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bp28-render-feel-smoke.ps1 -Scenario MixedMovement -SummaryPath .runtime\render-pass-headful-vfx-gatling-lite-summary.json -InputDurationMs 1800 -FrameSampleSeconds 2 -Headful
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bp28-render-feel-smoke.ps1 -Scenario MixedMovement -SummaryPath .runtime\render-pass-rocket-shockwave-wheel-fix-summary.json -InputDurationMs 1800 -FrameSampleSeconds 2 -DisableGpu
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bp28-render-feel-smoke.ps1 -Scenario MixedMovement -SummaryPath .runtime\render-pass-authoritative-weapon-hud-summary.json -InputDurationMs 1800 -FrameSampleSeconds 2 -DisableGpu
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bp28-render-feel-smoke.ps1 -Scenario MixedMovement -SummaryPath .runtime\render-pass-headful-authoritative-weapon-hud-summary.json -InputDurationMs 1800 -FrameSampleSeconds 2 -Headful
inline authoritative weapon switch/recoil API probe -> .runtime/authoritative-weapon-switch-recoil-probe.json
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bp28-render-feel-smoke.ps1 -Scenario MixedMovement -SummaryPath .runtime\render-pass-local-terminal-tracer-suppressed-summary.json -InputDurationMs 1800 -FrameSampleSeconds 2 -DisableGpu
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bp28-render-feel-smoke.ps1 -Scenario MixedMovement -SummaryPath .runtime\render-pass-headful-local-terminal-tracer-suppressed-summary.json -InputDurationMs 1800 -FrameSampleSeconds 2 -Headful
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bp44-battle-feel-suite.ps1
inline authoritative rocket pickup/terminal API probe -> .runtime/authoritative-rocket-pickup-terminal-probe.json
inline mail single battle/rating notification API probe
inline authoritative pickup-preserves-current-weapon API probe
```

关键结果：

- `build` 通过，仅有既有 Vite chunk / React Router `"use client"` 警告。
- `backend:compile` 通过；旧后端占用 8080 后已重启，新代码已生效。
- targeted API probe 通过：出生点拾取加特林后武器栏为 2，把 `Gatling/1` 切到 `Pistol/0`，再切回加特林后开火产生服务端后坐力 `dx=-2.88`。
- `render-pass-weapon-fix-mixed`: `ok: true`, warnings 为空，确认多武器/后坐力改动没有破坏双客户端渲染 smoke。
- `render-pass-matchmaking-5s`: `ok: true`, warnings 为空，双客户端进入同一局，验证 5 秒 matchmaking / 探针等待没有破坏实战 smoke。
- `render-pass-vfx-gatling-lite`: `ok: true`, warnings 为空；VFX transient createdDelta 从上一轮多武器 smoke 的约 `342/324` 降到 `84/87`，peak active transient 从约 `84/97` 降到 `32/35`，HUD 小地图静态层仍为 `0` 重绘。
- `render-pass-headful-vfx-gatling-lite`: `ok: true`, warnings 为空；真实窗口 VFX createdDelta `52/54`，peak `21/23`。
- `render-pass-rocket-shockwave-wheel-fix`: `ok: true`, warnings 为空；火箭 shockwave / 场景层滚轮输入补丁后，VFX createdDelta `90/66`，peak `24/24`，HUD 小地图静态层 `0` 重绘。
- `render-pass-authoritative-weapon-hud`: `ok: true`, warnings 为空；完整权威武器栏补丁后，VFX createdDelta `79/60`，peak `20/18`，HUD 小地图静态层 `0` 重绘。
- `render-pass-headful-authoritative-weapon-hud`: `ok: true`, warnings 为空；真实窗口 VFX createdDelta `72/73`，peak `31/35`，HUD 小地图静态层 `0` 重绘。
- `authoritative-weapon-switch-recoil-probe`: `ok: true`；武器栏 `Pistol/Gatling`，切枪 `Gatling -> Pistol -> Gatling`，加特林后坐力 `dx=-2.88`。
- `render-pass-local-terminal-tracer-suppressed`: `ok: true`, warnings 为空；本地 terminal tracer 降噪后，headless VFX createdDelta `50/87`，peak `14/40`，HUD 小地图静态层 `0` 重绘。
- `render-pass-headful-local-terminal-tracer-suppressed`: `ok: true`, warnings 为空；真实窗口 VFX createdDelta `61/77`，peak `19/27`，HUD 小地图静态层 `0` 重绘。
- `bp44-battle-feel-suite`: `ok: true`；`MixedMovement`、`SkillPressure`、`TargetedSkillPressure`、`DualClientPressure`、`StraightFire` 全部同局、warnings `0`、hit-dispute failures `0`。本地 motion/muzzle 延迟约 `3-11ms`，`MixedMovement` RAF over25/over40 均为 `0`，HUD 小地图静态层重绘仍为 `0`。
- `authoritative-rocket-pickup-terminal-probe`: `ok: true`；武器栏 `Pistol/Gatling/RocketLauncher`，当前 `RocketLauncher`，rocket terminal `hit`，bot HP `100 -> 40`，damage `60`。
- `mail single notification probe`: `ok: true`；提交 ratingDelta `+20` 的战斗结果后，只生成一封 `mail-battle-*`，主题“战斗结算与评分更新”，`mail-rating-*` 数量为 `0`。
- `pickup-preserves-current-weapon probe`: `ok: true`；出生点拾取加特林后武器栏为 `["Pistol","Gatling"]`，当前仍为 `Pistol/0`；发送 `switchWeaponDirection=1` 后才切到 `Gatling/1`。
- `StraightFire`: `ok: true`, warnings 为空，hit-dispute assertions 为空，`nearButNoDamage = 0`。
- `MixedMovement`: `ok: true`, warnings 为空，远端 display-to-target p95 约 21.9px，本地移动/枪口反馈约 1/3ms。
- `SkillPressure`: `ok: true`, warnings 为空，技能链路有 outcome，VFX peak 约 94/83，仍低于 transient 上限 120。
- `DualClientPressure`: `ok: true`, warnings 为空，双客户端同局，双方都有移动/开火证据；本地反馈约 1/4ms，远端 display-to-target p95 约 21.2px，VFX peak 约 65/59。
- HUD 小地图静态层在上述 smoke 中均保持 `staticRedraw = 0`。

## 完成边界

BattlePage 程序化渲染与手感专项已经阶段性完成。这里的完成边界是“可玩级程序化渲染”，不是最终美术资产。

已完成：

- 本地反馈即时。
- 远端同步可读。
- 多武器表现接入。
- projectile / terminal VFX 分层。
- 火箭 shockwave 半径表达。
- 加特林高频 VFX 降噪。
- HUD 遮挡与小地图缓存优化。
- 组合 smoke / feel suite / API probe 通过。

仍属于后续阶段：

- `GameScene.ts` 硬解耦与代码边界证明。
- 基础规则与战斗体验最终定稿。
- 地图、技能、武器、bot 的配置化扩展。
- 类型安全 / 声明式 / 微服务 / 前后端 API 同名契约整理。
- replay / mails / rating / profile / forum / admin 产品化收口。
- 最终素材美术与整体视觉统一。

## 距离原始愿景还差什么

这里的估时按 Codex / GPT-5.5 xhigh 的实际工程效率估算，包含阅读、实现、构建、smoke、必要返工，不按人工课堂开发速度估算。若只做“能交付课程演示”的版本，预计还需要 2-4 天。若要接近我们原本说的“可持续扩展、可多人玩、数据闭环清楚、视觉统一”的完整愿景，预计还需要 5-10 个高强度 Codex 工作日。若再追求接近商业游戏级美术和大量平衡打磨，时间会继续增加。

## 当前保存点

已按要求把当前结构保存到 GitHub：

- 分支：`main`
- 远端：`origin https://github.com/zhuyf25t/TPsys.git`
- 提交：`aaf93eb Save battle rendering and systems checkpoint`
- 状态：Battle 渲染收口、站内信合并、拾枪不自动切枪、5 秒 matchmaking、多武器 authoritative、火箭 AoE、HUD 武器栏等当前成果已经有远端保存点。

后续如果进入素材、美术、地图、bot、扩展性阶段，可以在这个点上继续推进；如果某个大方向失败，也有明确回退锚点。

## 你不在场也能推进的任务

下面这些任务不需要你实时盯着，我可以按总设计师视角拆票、派 worker、做验收，然后持续推进。你回来后只需要看阶段结果和做最终取舍。

1. `GameScene.ts` 硬解耦。
   这是当前仓库规则里的最高优先级，也是架构扩展的前置条件。它不属于“课程代码风格整理”，而是把巨型 scene 瘦成 renderer host / glue layer，避免后面地图、技能、bot、素材继续堆到 scene 里。可无人推进。预计 0.5-1.5 天。

2. 地图配置化。
   可以把地图尺寸、障碍物、出生点、资源点、视觉主题抽成配置，让后续新增地图不改 runtime 主逻辑。可无人推进。预计 0.5-1 天。

3. 技能 / 武器配置化。
   可以把技能冷却、范围、持续时间、VFX key、武器数值、弹道 profile、后坐力、pickup 行为抽成共享规则表。数值我会保守调整，不做激进平衡。可无人推进。预计 0.5-1.5 天。

4. bot SDK / bot 社区接口。
   可以先做最小 bot interface：输入观测、输出动作、tick 频率、权限边界、样例 bot。你的朋友后续可以按接口贡献 bot，而不是直接改 battle runtime。可无人推进。预计 1-2 天。

5. 数据闭环硬化。
   可以继续做 result / replay / mails / rating / profile 的幂等性、旧 Visitor 数据隐藏、重复 rating 防护、多标签页重复提交防护。可无人推进。预计 1-2 天。

6. 社交 / 好友 / forum / admin 产品化。
   好友列表、好友异步留言、统一通知、forum 举报治理、admin 审计入口都可以继续做。可无人推进。预计 1-3 天。

7. 主界面视觉重构。
   参考你给的金属科幻大厅结构，可以重构首页：中央品牌与开战 CTA、左右排行榜 / 角色卡 / 装备卡、右侧状态栏、底部邮件与好友入口、背景转动浮动和粒子层。内容会替换成我们项目真实功能，不照搬参考图的无关信息。可无人推进，但最终审美最好你回来确认。预计 0.5-1.5 天。

8. BattlePage 美术方向第一轮。
   可以先做“自然 + 金属战争 + 空洞骑士剪影”的渲染规范与素材 prompt queue：角色轮廓、武器轮廓、地面 tile、墙体、箱子、医疗包、弹药包、技能特效、UI frame。也可以生成部分候选 bitmap 素材，但正式替换前要做性能和可读性验收。可无人推进，最终风格需你确认。预计 1-3 天。

9. 启动 / 验收脚本。
   可以做一键关闭旧前后端、一键启动、一键 build + backend compile + smoke 的脚本，减少 sbt pipe / 8080 占用造成的误解。可无人推进。预计 0.5-1 天。

## 暂缓或需要你确认的任务

1. F 类“课程代码风格 / 类型安全底层整理”暂缓。
   你正在研究老师具体要求，所以暂时不做大规模风格重写、不做全项目 var/val 清零、不做手写 JSON 全面替换、不做 API contract 大迁移。只允许做为了当前功能必要的局部类型安全修正。

2. 最终数值平衡需要你试玩确认。
   我可以保守估计和自动 smoke，但“好不好玩”最终需要你实际玩几局。无人推进时只做小幅、可回退的数值调整。

3. 最终美术风格需要你审美确认。
   我可以生成素材 prompt、做候选图、接入第一版视觉，但最终到底偏金属、偏空洞骑士、偏自然魔法、偏科幻，需要你回来拍板。

4. 课程展示取舍需要你确认。
   如果老师更看重函数式/类型安全/微服务结构，优先级要压过美术；如果老师更看重可玩成品，优先级可以偏功能闭环和展示。

## 美术与界面方向判断

主界面参考图的结构是可用的，但内容需要替换成我们自己的系统：

- 中央：游戏名、核心模式、开始游戏、配装、回放 / 排行。
- 左侧：玩家档案、当前装配、技能、贡献榜或近期动态。
- 右侧：在线区服、竞技场人数、模式、回合时间、rating、评分榜。
- 底部：登录 / 开始游戏 / 配装主 CTA，右下角邮件和好友。
- 背景：金属大厅、圆形机械平台、慢速旋转光环、微粒、局部蓝色能量灯。

BattlePage 美术方向建议不是纯科幻，也不是纯空洞骑士：

- 角色：空洞骑士式清晰剪影 + 金属护甲边缘，高对比轮廓，保证小尺寸下可读。
- 武器：枪炮要实际、短而明确，枪口和 projectile birth 必须对齐判定。
- 技能：自然 / 灵魂 / 冰霜 / 冲刺可以更炫，但特效通道必须和命中通道分离。
- 地图：金属战争地板 + 自然侵蚀 / 矿洞感边缘，障碍物需要封边明确，不能让玩家误判能否通过。
- UI：金属框架 + 少量蓝绿技能光，不要过度发光挡视野。

素材策略：

- 第一阶段先写 prompt queue 和视觉规格，不急着把所有素材替换进引擎。
- 第二阶段生成角色 / 武器 / 地图 tile 候选。
- 第三阶段只替换最影响观感的元素：主页背景、角色、墙体/箱子、pickup、技能图标。
- 每替换一批素材都跑 render smoke，避免素材尺寸、透明边、锚点导致碰撞和视觉错位。

### A. 当前最高结构风险：GameScene 硬解耦

状态：未完成。

为什么重要：`GameScene.ts` 仍是项目的最大架构风险。BattlePage 现在能跑、能玩、能渲染，但如果 `GameScene` 继续承载过多 runtime、renderer、pickup、weapon、projectile、HUD glue 之外的责任，后续扩展地图、技能、bot、素材时会越来越难改，也不符合“总设计师 / 总架构师”要的代码边界。

需要完成：

- 方法级审计 `GameScene.ts`，确认每个保留方法是否真属于 scene lifecycle / camera host / physics glue / HUD bridge / VFX adapter。
- 抽出剩余非 scene host 责任：世界构建、world view sync、projectile runtime、pickup runtime、weapon runtime、combat frame orchestration、格式化 helper、geometry resolver。
- 生成 `docs/GAMESCENE_HARD_GATE_COMPLETION_REPORT.md`，列出最终 LOC、文件大小、保留方法、抽出责任、遗留债务。
- 目标：`GameScene.ts <= 25KB` 且 `<= 700 LOC`；如果未达标，必须逐方法证明为什么仍属于 scene host。

预计时间：0.5-1.5 天。

优先级：最高。它不直接增加功能，但决定之后扩展是否会变成堆屎山。

### B. 基础可玩规则最终收口

状态：部分完成。

已完成：authoritative 多人战斗、基础命中、拾取、武器栏、火箭 AoE、加特林后坐力、滚轮切枪、站内信结果通知等已经可用。

还没完成：

- 一命模式规则最终确认：是否全局一命、是否允许 bot 重生、是否保留倒计时结束判胜。
- 胜负条件定稿：最后幸存、时间到按击杀/伤害/存活判定、掉线怎么判、双败怎么判。
- 武器数值二轮平衡：手枪默认强度、加特林热量、火箭炮爆炸半径/射速、霰弹距离衰减、后坐力是否影响爽感。
- 移动与冲刺调优：速度、加速度、减速、shift 体感、体力恢复、被击中反馈。
- pickup 规则定稿：拾枪只入栏已完成，但还需要决定重复拾取是补弹、刷新热量、还是只补 reserve。
- 地图出生点与资源点公平性检查。

交付标准：

- 双账号实战 5-10 局，规则没有明显误解或不公平。
- `bp44-battle-feel-suite` 继续通过。
- 至少一份规则文档能解释“怎么玩、怎么赢、为什么公平”。

预计时间：0.5-1.5 天。

### C. 扩展性：地图 / 技能 / 武器 / bot 社区

状态：未完成到愿景级。

当前情况：代码里已经有多个武器、技能、pickup、地图障碍等概念，但还不是一个真正可扩展的内容系统。现在更多是“工程内硬编码可跑”，不是“外部配置 / 社区 bot / 多地图内容包”。

需要完成：

- 地图配置化：地图尺寸、障碍物、遮挡层、出生点、pickup 点、视觉主题都从配置读取。
- 技能配置化：技能 ID、冷却、范围、持续时间、状态效果、VFX key、HUD 文案解耦。
- 武器配置化：武器数值、弹道类型、后坐力、reload、pickup 行为、VFX profile 从共享规则表读取。
- bot SDK：定义 bot 可读取的观测数据、可输出的动作、tick 频率、限制条件、防作弊边界。
- bot 社区接口：允许外部 bot 以独立模块/脚本形式接入，但不能直接改 server authoritative state。
- 文档：给同学或朋友写 bot 的最小 API 文档和样例 bot。

交付标准：

- 新增一张地图不需要改 battle runtime 主逻辑。
- 新增一个技能不需要改 `GameScene.ts`。
- 新增一个 bot 只需要实现一个明确接口。
- bot 不能越权读写服务端内部状态。

预计时间：1-3 天。

### D. 数据闭环：result / replay / mails / rating / profile

状态：部分完成。

已完成：battle result 可以入库；replay 可以查看；rating 已有曲线与列表；profile 有战绩统计；mails 已经把战报和 rating 合并为一条；Visitor 正式写入已基本被挡住。

还没完成：

- 清理历史脏数据：旧 `Visitor`、旧重复 rating、旧分裂邮件、旧异常战绩需要迁移或隐藏。
- rating 幂等性：同一 battle result 不能重复加分；同账号多标签页不能重复提交结果。
- 多账号 / 多地登录策略：允许异地登录浏览，但同一个账号不能在同一局占多个 seat。
- replay 与 result 一致性：战报、回放、rating、profile 必须指向同一个 battleId / ownerHandle。
- profile 统计可信度：胜率、平均名次、最近表现、rating 历史要只读真实入库战局。
- mails 实时同步：当前是轮询刷新，好友私聊和实时通知阶段需要更明确的同步策略。

交付标准：

- 同一局只写一次 result / replay / rating。
- 榜单不再出现 Visitor。
- profile 不会混入其他账号的数据。
- mails 中一局只显示一条可读通知。

预计时间：1-2 天。

### E. 社交 / 好友 / 论坛 / 管理后台

状态：基础入口存在，但未产品化。

已完成：好友申请和同意/拒绝已有基础链路；forum / admin / governance 有页面和部分数据通道。

还没完成：

- 好友列表：通过申请后应能看到好友关系，而不只是看到申请状态。
- 好友私聊：还未实现。需要消息模型、发送接口、收件箱同步、未读状态。
- 实时消息同步：私聊、系统通知、战报通知最好统一走一个 notification/message channel。
- forum 收口：帖子、回复、战报引用、举报、管理处理需要更一致的权限边界。
- admin 收口：用户、战绩、举报、治理通知、rating 调整要有清晰入口和审计记录。

交付标准：

- 好友申请通过后能进入好友列表。
- 至少支持好友间异步留言。
- 管理员能看到并处理举报/治理事项。
- 所有社交通知进入统一站内信或通知中心。

预计时间：1-3 天。

### F. 类型安全 / 声明式 / 微服务 / API 同名契约

状态：未完成到课程要求级。

当前情况：项目已经比最初更类型化，但仍有大量字符串 JSON、手写 parser、前后端 DTO 重复定义、部分 runtime 硬编码。对于课程风格要求，还需要系统整理。

需要完成：

- Scala 后端减少可变状态暴露，能用 `val` 和不可变数据结构的地方继续收敛。
- API DTO 命名统一：前端 contract 与后端 case class 字段同名，避免 `sourceBattleId` / `battleId` / `resultId` 语义漂移。
- 手写 JSON renderer/parser 分层：至少把路由层 JSON 字段集中到 adapter，不散落业务服务。
- 服务边界拆分：identity、battle、mails、rating、replay、social、forum、governance 形成清楚 service interface。
- 前端声明式数据流：页面只组合 presenter/view model，不直接揉业务规则。
- 类型安全 smoke：增加 API contract field smoke 覆盖新增邮件合并、拾枪不自动切枪、多武器 inventory。

交付标准：

- 主要 API 字段前后端同名。
- 页面层不直接拼业务规则。
- 后端 service / repository / routes 分层清楚。
- 构建和 contract smoke 通过。

预计时间：1-3 天。

### G. 美术与整体视觉统一

状态：程序化可玩完成，最终美术未完成。

当前情况：BattlePage 已经从粗糙原型提升到可读、可玩、不卡顿的程序化渲染；大厅、mails、profile、rating 等页面也有金属暗色风格。但还不是最终素材风格。

需要完成：

- 统一视觉方向：金属科幻 + 空洞骑士式剪影 / 轮廓 / 氛围。
- 角色素材：玩家、bot、不同阵营、受击、死亡、冲刺、状态效果。
- 武器素材：手枪、加特林、火箭炮、霰弹枪，不只是程序化几何枪身。
- 地图素材：地板、墙体、箱子、封边、门、资源点、遮挡层，避免“障碍物像临时块”。
- UI 素材：按钮、面板、排行榜、站内信、结算页、回放页统一语言。
- 视觉性能预算：素材替换后仍要保持 RAF / VFX / HUD smoke 稳定。

交付标准：

- battle 主画面截图不再像 debug prototype。
- 主页、战斗、站内信、榜单、profile 的风格一致。
- 素材不影响命中判定可读性。

预计时间：1-3 天；如果大量 AI 生成素材需要反复挑选，可能 3-5 天。

### H. 测试、启动、交付与 GitHub 备份

状态：部分完成。

已完成：已有多条 smoke、feel suite、API probe；前后端可构建；GitHub 曾按要求上传过一版。

还没完成：

- 一键启动脚本：干净关闭旧后端 / 前端，启动新后端 / 前端，避免 sbt pipe 和 8080 占用误解。
- 一键验收脚本：build + backend compile + core smoke + API probe。
- CI 或至少本地 release checklist。
- 运行说明：老师或同学如何启动、注册、开战、查看战报。
- GitHub 主线持续备份：大重构前后要明确保存点。

交付标准：

- 新机器按 README 能跑起来。
- 运行测试不需要手动猜端口和进程。
- 关键功能有 smoke 兜底。

预计时间：0.5-1 天。

## 建议执行顺序

0. 当前版本已 GitHub 保存。
   已提交并推送 `aaf93eb Save battle rendering and systems checkpoint`。

1. `GameScene` 硬解耦。
   先解决最大架构风险，避免之后扩展地图、技能、bot、素材时继续堆在 scene 中。预计 0.5-1.5 天。

2. 基础规则最终收口。
   定稿一命模式、胜负条件、武器数值、pickup 规则。预计 0.5-1.5 天。

3. 扩展性第一轮。
   地图配置、技能配置、武器配置、bot SDK 最小接口。预计 1-2 天。

4. 数据闭环与产品页收口。
   replay / mails / rating / profile / forum / admin 做一致性、幂等性和脏数据处理。预计 1-2 天。

5. 主界面视觉重构。
   按金属科幻大厅结构重构首页，并加入高级背景动效和粒子。预计 0.5-1.5 天。

6. BattlePage 美术资产与整体视觉统一。
   在规则和结构稳定后换素材，避免素材返工。预计 1-3 天。

7. 最终交付整理。
   README、启动脚本、验收脚本、GitHub 保存点。预计 0.5-1 天。

8. 课程风格 / 类型安全整理。
   暂缓，等你确认老师要求后再做。预计 1-3 天，不纳入当前无人推进主线。

## 总体时间判断

最短课程演示路径：2-4 天。

这个路径会优先保证：架构能解释、battle 能玩、数据不乱、站内信/rating/profile 能闭环、启动方式清楚。美术只做必要统一，不追求大量素材。

完整愿景路径：5-10 天。

这个路径会继续完成：GameScene 真正瘦身、地图/技能/bot 可扩展、社交/论坛/admin 产品化、类型安全和 API 契约整理、美术风格统一。

商业级 polish 路径：10 天以上。

这个路径才会追求接近成熟竞技游戏的长期数值平衡、大量素材、动画、音效、复杂实时社交、稳定在线服务和持续 CI。
