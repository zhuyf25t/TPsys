# BattlePage 总控进度报告

更新时间：2026-04-27 19:09 Asia/Shanghai

## 当前结论

BattlePage 已经进入“多人联机可运行，但还不能宣布好玩/丝滑/最终渲染完成”的阶段。当前最重要的工作仍然是 battle 渲染、手感、同步可信度、命中可信度，而不是外围页面、素材先行或文档数量。

我当前按“总设计师 / 总架构师 / 总规划师”方式推进：主 Codex 负责拆票、边界、验收、下一步决策；大块业务代码改动交给 worker；每次只接受一个明确代码票，避免多个手感变量混在一起无法归因。

最新决策：BP-44B 诊断扩展已完成第一轮，但结论是“发现下一处手感瓶颈”，不是“手感完成”。新加入 `SkillPressure` 场景，覆盖移动、移动瞄准、短开火和 Q/E/R 技能连按；suite 四场景均 `ok=true`、warnings `0`、命令失败 `0`。同时它稳定暴露出技能位移导致的本地/权威位置硬校正：`SkillPressure` 出现 `hardSnapDelta=1`，复跑仍出现 `hardSnapDelta=1`、`softCorrectionDelta=1`。下一张 P0 票应进入技能位移预测/校正边界，而不是继续盲调视觉素材。

重要残余：用户在 2026-04-27 截到的 `03:42` / `03:xx` 新局倒计时问题已找到前端显示层根因并修复。真实浏览器专项 smoke 现在覆盖 `/battle?new=1` 连续开局、旧 tab 写回窗口、返回大厅后普通 `/battle`，均通过。该问题从 open waiting 降级为实机观察项：如果用户再次看到 03:xx，新疑点优先是旧 bundle/旧浏览器 tab 未刷新或另一个入口未走当前 runtime，而不是后端 queue 继承。

约束：

- 不运行任何 `git` 命令。
- 不因为 build 通过、smoke 通过、文档更新，就宣布 BattlePage 达标。
- 用户实机体感优先级高于自动化指标；如果玩家仍觉得卡、慢、粘、判定不可信，就继续修。
- 当前主线仍然围绕 battle，不切换到 forum/profile/admin 等外围功能。

## 当前运行状态

- 前端 dev server 正常运行在 `http://127.0.0.1:5173`。
- 后端正常运行在 `http://127.0.0.1:8080`，2026-04-27 19:09 复查 `/health` 返回 `{"status":"ok","service":"slay-demo-backend","port":8080}`。
- 当前监听进程：前端 node PID `21640`，后端 Java PID `8500`。用户在另一个终端执行 `sbt run` 时看到的 `Address already in use: bind` 是端口 `8080` 已被现有后端占用，不是后端代码启动失败；若要由用户终端接管后端，需先停掉 PID `8500`。
- BP-31A sprint `1.75` 后端重启验证已完成；旧后端 JVM 启动于 `02:47:47`，早于 runtime 文件 `05:23:47` 写入，已被限定范围重启。
- `GameScene.ts` hard-decoupling 已完成并完成最新报告刷新：522 physical lines / 496 non-empty LOC / 24,989 bytes。当前职责仍压在 scene lifecycle / renderer host / glue layer，不再把主要 battle runtime 放回 scene。
- BP-34A/B 计时器/新局会话新鲜度已修复并通过专项验证。
- BP-31O 长时间/混合输入渲染 smoke 已完成并验收。
- BP-31P 已校准 smoke 的 dispatch 起点，排除了旧 inputStart 把 focus/准备开销算进 local feedback 的假高问题。
- BP-31Q 已加入 page-side input event timestamp；MixedMovement headless/headful 均通过，真实页面输入事件到 motion 反馈约 `1ms/9ms`，RAF p95 `16.8ms`，无 >25ms 帧。
- BP-31R 已拆分 movement 与 fire 的输入事件基准；真实 `mousedown` 到 muzzle 反馈约 `9ms`，不再把开火反馈误算到第一个 movement keydown 上。
- BP-31S 已加入 `/battle/commands` fetch 探针；命令 POST/ack 本身较快，旧 confirm latency 不应被当成网络延迟。
- BP-34B 已修复普通 `/battle` 默认恢复旧局的问题；同 profile 复现验证显示旧 battleId 未被恢复，新局 elapsed 回到约 `1s`。
- BP-35 已修复本地枪口 VFX/tracer 与真实 projectile 轨迹平行偏差的主要代码原因：本地 own projectile 不再使用 remote interpolation，pistol muzzle 起点改为服务端 birth distance `30px`。
- BP-36A 已修复大障碍物/掩体视觉缺底：inner wall/crate 增加底边、左右边和角板，cover footprint cue 补底部提示线；未改碰撞。
- BP-36B headful 复核通过：BP-35/BP-36A 后真实窗口 MixedMovement 没有性能或同步回退。
- BP-37 长时 headful 复核通过：9 秒 MixedMovement、双客户端同战局、命令 POST `292` 次失败 `0`，RAF p95 约 `16.8ms`、无 >40ms 帧；但 Long Task 仍存在，且 motion latency 诊断被样本窗口截断误算，已拆为 BP-37A。
- BP-37A 已修复长采样诊断口径：sample window 截断时使用 channel `firstAtMs` 合成最小首样本元数据，长采样复核 motion `11ms`、muzzle `10ms`、RAF p95 `16.8ms`、warnings `0`。
- BP-40C/BP-40D 已闭环倒计时残余：BP-40C 真实浏览器复现显示后端 elapsed 约 `991ms`、localStorage active session 也接近 0，但 HUD 显示 `04:48`；BP-40D 修正 shared authoritative runtime 不再用 Phaser scene-local time 覆盖 `snapshot.elapsedMs`。主控复跑 browser smoke 通过：第二局 `timer=04:59`、local elapsed `446ms`、backend elapsed `458ms`；普通 `/battle` `timer=04:59`、backend elapsed `658ms`。
- BP-38 已完成 startup/load 与 gameplay input 负载分层：smoke summary 现在输出 RAF/Long Task 分相位和 CDP `beforeInput -> afterInput` delta；9 秒 MixedMovement 证明输入期 Long Task 为 `0`，clientA 输入期 `>25ms/>40ms=0/0`，clientB 输入期 `>25ms/>40ms=7/0`，命令 POST `289` 次失败 `0`。
- BP-41 已完成真实双客户端压力 smoke：新增 `DualClientPressure`，A/B 两端都移动、换向、瞄准、开火并安装 input/command probes；9 秒 headless 下 A/B 输入期 Long Task `0/0`，RAF `>25ms/>40ms=0/0`，command fetch A `273` 次失败 `0`、B `275` 次失败 `0`。
- BP-42A 已完成手枪 tracer 收敛：Pistol tracer `148px -> 42px`、thickness `3 -> 2`、duration `118ms -> 78ms`、alpha `0.62 -> 0.32`、ghostScale `1.9 -> 0.7`，手枪 glint 关闭；muzzle 起点仍是 authoritative birth distance `30px`；原长束特效命名归档为 `piercing-rail-tracer-long`。
- BP-42B 已完成 VFX churn 降噪第一轮：ring effects 不再每帧 clone 记录对象，diagnostics 开启时输出 active/slot/ring/created/destroyed/peak 指标，smoke summary 新增 `vfxMetric`；主控复验 MixedMovement 中 clientA `createdDelta=47 destroyedDelta=47 peak=27`、clientB `createdDelta=44 destroyedDelta=44 peak=32`、warnings `0`。
- BP-43 已完成 HUD/minimap 热路径优化第一轮：minimap 静态层离屏缓存，动态层仍实时绘制；MixedMovement 中 A/B minimap render delta `54/54`、static redraw delta `0/0`；DualClientPressure 中 A/B minimap render delta `53/53`、static redraw delta `0/0`，warnings `0`。
- BP-44A 已完成 battle feel suite：新增 `npm run demo:bp44-feel-suite`，主控完整复跑三场景通过；MixedMovement motion/muzzle `11ms/11ms`，DualClientPressure `1ms/12ms`，StraightFire `6ms/5ms`；三场景 RAF input over25/over40 均 `0/0`，命令失败 `0`，correction hard/soft 均 `0/0`。
- BP-39A 已完成视野/尺度/屏幕速度参照诊断：新增 `visionMetric`，基线 zoom `1.32`、可视世界约 `952x476`、MixedMovement 平均屏幕速度约 `261-267px/s`、DualClientPressure A/B 平均约 `337/277px/s`；未改 gameplay。
- BP-39B 已完成视觉单变量 zoom 标定：camera zoom `1.32 -> 1.40`，可视世界约 `897x449`，真实窗口 MixedMovement 平均屏幕速度约 `277px/s`；未改 gameplay。
- BP-44B 已完成技能压力诊断扩展第一轮：`SkillPressure` 加入 smoke/suite，记录 `skillTapCount`、`skillKeys`、fire/aim 样本。主控复核显示 `SkillPressure` 可以稳定触发 hard snap，根因候选是 Dash/Blink/Freeze 等服务端位移技能未进入本地显示预测链；该问题升级为下一张 P0 手感票。
- 规划 BP-BOT-SDK：可给外部贡献者拆出纯逻辑 bot brain SDK。边界是 snapshot/profile -> intent/command，不允许依赖 Phaser/DOM/后端写入。

## 已完成主线

- GameScene 硬解耦：已生成 hard gate completion report，`GameScene.ts` 已从巨型 runtime 类收束为场景壳/渲染宿主/胶水层。
- Authoritative multiplayer：前端能进入共享 battle，后端维护 authoritative state，多客户端能看到同一局。
- Battle result / replay 基础闭环：authoritative battle 结束后具备 result/replay ready 轮询与回收路径。
- 速度和移动手感第一轮：基础速度恢复到旧本地手感区间，移动 resolver 加入分轴滑墙，减少贴墙 full-stop。
- Sprint/stamina：后端 authoritative stamina、前端 HUD、展示层预测、replay consistency 已对齐。
- 地图/碰撞/小地图一致性第一轮：静态障碍显示尺寸、碰撞声明、minimap obstacleBounds 已统一；边界可读性增强。
- 一命模式：死亡后不再 respawn，alive 玩家数降到 `<= 1` 或时间结束时结算。
- Projectile 命中链第一轮：后端 projectile 改为 swept/segment 命中，并加了小幅 shooter advantage，降低“擦到身体但没判”的挫败感。
- Projectile terminal 闭环第一轮：后端现在记录 projectile 被移除的 server reason 和真实 terminal position，前端 terminal VFX 已接入该 server truth；hit/obstacle/world/ttl 的收尾语义不再只靠上一帧视觉位置猜。
- 本地开火反馈：本地 muzzle/tracer ghost 立即播放，authoritative 命中后显示 hit-confirm，状态通道和 VFX 通道保持分离。
- 远程 projectile 显示：真实 projectile 显示时间轴与远程玩家插值对齐，减少“子弹视觉领先角色导致看着穿身”的错觉。
- 角色轮廓/命中体积可读性第一轮：hero 底盘、剪影圈、命中圈已加入显示层，并修正到 projectile 层以下，避免遮挡弹道。
- HUD 金属风格第一轮：DOM HUD 已从透明黑框推进到深色金属面板、细金/青蓝描边、切角感和更硬朗层级，尺寸仍保持紧凑。
- Reconciliation 第一轮：client command seq/history/ack 已接上，本地 correction 改为平滑纠偏，未确认 movement/sprint/stamina 会参与 display correction target。
- Local correction 粘滞降噪第一轮：移动中 deadzone 已提升到 `24px`，停下后 stationary deadzone 已提升到 `10px`；当前 headful smoke 下本地 hero hard snap `0`、soft correction `0`。
- BP-34A 计时器/会话新鲜度：`/battle?new=1` 和“开始新比赛”现在会清理当前账号 active/completed session，旧 runtime 的 pagehide/teardown 不再把上一把重新写回；shared authoritative restore 必须先验证后端 battleId 相同、未 finished、elapsed 未过 duration，否则丢弃旧 session 并重新匹配。
- 热路径优化第一轮：diagnostics 默认关闭；VFX transient 管理从频繁全量 `filter` 改为 record/Map/head index；authoritative frame apply 对 projectile/pickup/slowField/weapon/skill 做了 in-place sync，减少 30Hz state apply 的对象 churn。
- 技能交互：Blink/Freeze 改为技能键准备、左键确认；Dash 保持即时；prepared target validity、HUD 状态、拒绝反馈已对齐。
- 技能 no-op 反馈：Dash/Blink/Freeze 的服务器 accepted 响应已带 explicit outcome，前端会用中文 transient notice 表达冷却、目标太远、目标无效、被阻挡等原因。
- BP-28S-10G：authoritative projectile 渲染延迟已经从远程英雄插值延迟中解耦。远程英雄仍保持 `100ms` 插值缓冲；projectile 改为独立 `66ms`，只影响 shared authoritative projectile 的显示时间轴，不改后端命中、武器数值、速度、HUD、GameScene 或 API。
- BP-28S-10H：摄像机 follow lerp 从 `0.16/0.16` 改为 `1/1`，去掉摄像机本体阻尼，只保留已有 pointer look-ahead offset lerp。目标是降低“RAF 稳定但画面慢半拍/粘”的感知延迟；不改移动速度、输入、reconciliation、projectile、后端或 GameScene。
- BP-28S-10I：摄像机 deadzone 从 `112x82` 归零为 `0x0`，去掉“小幅移动后摄像机才开始跟”的中心死区。目标是进一步降低“移动了但画面还没动”的粘滞感；不改 pointer look-ahead、offset lerp、速度、输入、reconciliation、projectile、后端或 GameScene。
- BP-28S-10J：远程实体显示时间线已统一。remote hero 和 authoritative projectile 不再分别使用 `100ms` / `66ms`，而是共用 `AUTHORITATIVE_REMOTE_ENTITY_INTERPOLATION_DELAY_MS = 83`。目标是降低“看着 projectile 穿过远程身体但没判”的视觉时间错位；本地 muzzle/tracer ghost 仍负责即时开火反馈。
- BP-28S-10K：projectile terminal visual 已延展。shared authoritative projectile 从 snapshot 消失时，真实 projectile view 仍立即销毁，但 feedback 层额外播放 180ms terminal tracer，终点落在上一帧 projectile display position，同时保留 terminal spark。目标是减少“子弹突然消失/射程缩短”的错觉；不改 TTL、speed、damage、radius、hit 判定或后端。
- BP-28S-10L：pointer look-ahead offset lerp 从 `0.16/0.16` 改为 `1/1`。camera follow 已即时、deadzone 已归零后，剩余 offset 平滑会让 camera target 和 `pointerWorld` 继续漂移；10L 让准星/枪口方向更直接，不改 look-ahead ratio/max、pointer world 读取、武器或 projectile。
- BP-31N：camera `roundPixels` 从 `true` 改为 `false`。在 `zoom=1.32` 与本地亚像素预测位移并存时，摄像机像素取整会把连续运动量化为不均匀屏幕步进；本票只关闭 camera rounding，不改 zoom、look-ahead、速度、correction、后端或 GameScene。用户实机反馈为体感明显更顺。
- BP-31O：render-feel smoke 已从固定右移+开火扩展为 `StraightFire` / `MixedMovement` 两个场景，并支持 `InputDurationMs`。MixedMovement 覆盖多方向移动、短暂停顿/换向和开火；summary 保留原指标结构并新增 `scenario` / `inputDurationMs` / fire offset 字段。
- BP-31P：localFeedback latency 改为基于 `inputDispatchStartPageMs`，并新增 `preDispatchOverheadMs`。该票只校准 smoke 统计，不改 gameplay；校准后 MixedMovement headful/headless 的本地反馈从旧 `46-77ms` 区间回落到约 `38-41ms`。
- BP-31Q：render-feel smoke 新增 page-side `keydown/mousedown` 输入事件探针，localFeedback latency 优先基于 `firstInputEventPageMs`。MixedMovement headless/headful 验证显示 motion latency 约 `1ms/9ms`，说明移动本地反馈链路已经是 1 帧内；muzzle latency 当前仍共用第一个输入事件基准，下一步需要拆出 fire mousedown 专用指标。
- BP-31R：render-feel smoke 将 movement keydown 与 fire mousedown 的输入事件基准拆开，motion latency 使用 `firstMovementInputEventPageMs`，muzzle latency 使用 `firstFireInputEventPageMs`。主控 headless/headful 复跑显示 motion `10ms/9ms`、muzzle `9ms/9ms`，确认本地移动和第一发枪口反馈都在 1 帧内。
- BP-28S-10N：地图/边界视觉可读性第一轮收口。`arenaBuilder.ts` 中 out-of-bounds shadow 从高透明大块 padding 矩形改为低透明远场雾化、窄边缘暗带、护栏线和 tick cue；边界暗带/警示线收细降透明。该票只改 Phaser visual layer，不改碰撞、`obstacleBounds`、world size、minimap、HUD、GameScene 或后端。
- BP-28S-10O：HUD/战场遮挡第一轮收口。`Hud.ts` 中右上小地图/排行、右下技能/武器、左上战斗日志都更紧凑，技能面板改为 3 列小卡，减少对主战场和瞄准区域的遮挡。该票只改 DOM HUD 表现层，不改 HudState、presenter、minimap 数据、技能/武器语义、GameScene 或后端。

## 最新验收：BP-40C / BP-40D 新局 HUD 倒计时显示根因修复

触发原因：

- 用户多次实机截图显示第二局刚进入 battle 时 HUD timer 仍为 `03:xx`，像是继承上一局剩余时间。
- 早前 BP-40 API/queue smoke 已证明新局 battleId 不同且后端 elapsed 接近 0，但真实浏览器仍能看到错误 timer，因此需要区分“后端继承”与“前端显示污染”。

诊断结论：

- BP-40C 新增真实浏览器专项 smoke：`scripts/bp40-browser-session-freshness-smoke.ps1`。
- 复现结果显示后端 `/battle/state` elapsed 约 `991ms`，localStorage active session elapsed 也接近 0，但 DOM HUD timer 显示 `04:48`，等价于前端显示 elapsed 约 `12000ms`。
- 根因不是 queue room / battleId / active session 继承，而是 shared authoritative 模式下 `GameScene.update(time)` 每帧用 Phaser scene-local `time` 覆盖 `snapshot.elapsedMs`，把场景启动/等待时间混入了战斗 timer。

代码处理：

- 文件：`frontend/src/scenes/GameScene.ts`。
- 新增 `advanceRuntimeLocalClock(time, delta)`，只在非 shared authoritative runtime 下执行旧逻辑：`elapsedOffsetMs + time` 与 `temporalFrameBridge.update()`。
- shared authoritative runtime 下不再用本地 Phaser time 覆盖 `snapshot.elapsedMs`；elapsed 来源保持为 `authoritativeFrameSnapshotApplier.ts` 从后端 frame 写入的 authoritative elapsed。
- 未修改后端、queue、weapon、movement、VFX、HUD 布局、命中判定或 gameplay 数值。

主控验收：

- `npm run build` 通过，仅有既有 Vite/React Router/chunk warnings。
- `npm run demo:bp40-freshness` 通过：round2 battleId 不同，round2 elapsed `32ms`，remaining 回到 `299968ms`。
- `scripts/bp40-browser-session-freshness-smoke.ps1` 通过：第一局 `timer=04:59` / backend `958ms`；第二局 `timer=04:59` / local `446ms` / backend `458ms`；普通 `/battle` `timer=04:59` / backend `658ms`。
- `npm run demo:bp28-render-feel-smoke -- -Scenario MixedMovement -InputDurationMs 3500 -SummaryPath .\.runtime\bp40d-mixedmovement-3500-summary.json` 通过：`ok=true`，`sameBattle=true`，command failed `0`，motion/muzzle local feedback `8ms/8ms`，输入期 RAF `>25ms/>40ms=0/0`。

结论：

- BP-40C/BP-40D 可接受：用户看到的 `03:xx` 倒计时残余已定位并修复为前端显示时钟污染，不是 authoritative battle 继承。
- 后续若用户再次看到 03:xx，优先排查旧浏览器 tab / 旧 bundle / 未刷新入口，而不是先改后端 queue。

## 最新验收：BP-42B VFX 热路径 Churn 降噪

触发原因：
- 用户实机体感已经明显改善，但仍要求继续向“丝滑、稳定、多人联机可玩”推进。
- BP-38/BP-41 已证明输入期 RAF 与命令链路稳定，剩余可疑点之一是 VFX transient/tween/ring 在混战中造成对象 churn 或 GC 压力。

代码处理：
- 文件：`frontend/src/features/battle/renderer/effects/sceneVfxController.ts`。
- `updateVisualEffects()` 从每帧构造 `remaining` 并 `push({ ...effect })`，改为原地更新 `ttlMs`、write index 压缩数组、最后截断 `visualEffects.length`。
- diagnostics 开启时发布 `window.__slayDemoBattleDiagnostics.vfx`：`activeTransientCount`、`trackedTransientSlotCount`、`activeRingCount`、`createdCount`、`destroyedCount`、`peakActiveTransientCount`。
- 文件：`scripts/bp28-render-feel-smoke.ps1`。
- render-feel smoke summary 新增 `vfxMetric`，采样 input 前后两端 VFX 创建/销毁 delta、活跃 transient、tracked slot、ring 和峰值。
- diagnostics 默认关闭时只经过 cached boolean guard，不在常规热路径发布 snapshot。
- 未修改 VFX 参数、武器/技能/伤害/移动/camera/HUD 布局、后端、queue、命中判定或 `GameScene.ts`。

主控验收：
- `npm run build` 通过；仅保留既有 React Router/Vite/chunk warning。
- `npm run demo:bp28-render-feel-smoke -- -Scenario MixedMovement -InputDurationMs 3500 -SummaryPath .\.runtime\bp42b-architect-mixedmovement-3500-summary.json` 通过。
- summary：`ok=true`、`sameBattle=true`、`warnings=[]`。
- VFX 指标：clientA `createdDelta=47`、`destroyedDelta=47`、`peakActiveTransientCount=27`；clientB `createdDelta=44`、`destroyedDelta=44`、`peakActiveTransientCount=32`。

结论：
- BP-42B accepted。它降低 ring effect 记录对象的每帧分配，并把 VFX churn 纳入可观测指标，但不宣称已经完成最终渲染。
- 下一张渲染/手感票应优先看 HUD/minimap 更新节流，因为 minimap 每次 HUD tick 都重绘 obstacle/hero/pickup，在多人混战和高频状态变化下可能贡献 DOM/canvas 侧体感噪声。

## 最新验收：BP-43 HUD / Minimap 更新节流复核

触发原因：
- BP-38/BP-41/BP-42B 已分别把输入期 Long Task、命令链路、VFX churn 做到可观测且第一轮收口。
- HUD/minimap 仍是明确热路径：每次 HUD update 都会重画背景、170 个 obstacle/clearance、centerLimit、camera、pickups、heroes。

代码处理：
- 文件：`frontend/src/ui/Hud.ts`。
- minimap 静态层缓存到离屏 canvas：背景、clearanceObstacles、obstacles、centerLimitRect 只在 world/canvas/静态数据签名变化时重画。
- 每次 `renderMinimap()` 仍 `clearRect + drawImage(staticLayer)`，再绘制动态层 cameraRect、pickups、heroes，显示语义保持不变。
- 新增本地轻量 HUD diagnostics gate，默认关闭；开启时写入 `window.__slayDemoBattleDiagnostics.hud`。
- diagnostics 字段：`minimapRenderCount`、`minimapStaticLayerRedrawCount`、`lastObstacleCount`、`lastHeroCount`、`lastPickupCount`。
- 文件：`scripts/bp28-render-feel-smoke.ps1`。
- render-feel smoke summary 新增 `hudMetric`，读取 input 前后 HUD/minimap 诊断。
- 未修改 HUD 文案含义、布局尺寸、panel 位置、battle state、movement、weapon、skill、camera、后端、queue、命中判定或 `GameScene.ts`。

主控验收：
- `npm run build` 通过；仅保留既有 React Router/Vite/chunk warning。
- MixedMovement：`npm run demo:bp28-render-feel-smoke -- -Scenario MixedMovement -InputDurationMs 3500 -SummaryPath .\.runtime\bp43-architect-mixedmovement-3500-summary.json` 通过。
- MixedMovement 指标：`ok=true`、`sameBattle=true`、warnings `0`；clientA minimap render delta `54`、static redraw delta `0`、static redraw count `1`；clientB minimap render delta `54`、static redraw delta `0`、static redraw count `1`。
- DualClientPressure：`npm run demo:bp28-render-feel-smoke -- -Scenario DualClientPressure -InputDurationMs 3500 -SummaryPath .\.runtime\bp43-architect-dualclientpressure-3500-summary.json` 通过。
- DualClientPressure 指标：`ok=true`、`sameBattle=true`、warnings `0`；clientA/clientB minimap render delta `53/53`、static redraw delta `0/0`、static redraw count `1/1`；A/B command failed `0/0`。

结论：
- BP-43 accepted。它消除了 HUD/minimap 每 tick 重画静态地图层的重复 canvas 工作，同时保留动态战场信息实时更新。
- 该票不代表 HUD 最终视觉完成；它只降低热路径成本并补齐 HUD/minimap 可观测性。

## 最新验收：BP-44A Battle Feel Suite

触发原因：
- BP-31Q/R、BP-41、BP-42B、BP-43 都显示输入、命令、VFX、HUD 热路径已经可观测且第一轮稳定。
- 继续单点调参容易凭感觉破坏手感；需要一个稳定复核包把典型 battle feel 场景压成 compact 指标，作为下一步决策依据。

代码处理：
- 新增 `scripts/bp44-battle-feel-suite.ps1`。
- 新增 npm script：`npm run demo:bp44-feel-suite`。
- suite 顺序调用现有 `bp28-render-feel-smoke.ps1`，默认覆盖 `MixedMovement 3500ms`、`DualClientPressure 3500ms`、`StraightFire 1800ms`。
- 每个子场景保留完整 summary/log，最终生成 `suite-summary.json`，只聚合高信号字段：sameBattle、warnings、motion/muzzle latency、command fetch、RAF input phase、VFX peak、HUD minimap static redraw、local correction。
- 未修改 gameplay、renderer、HUD、VFX、backend、queue、weapon、movement、skill、damage、rating/profile 或 `GameScene.ts`。

主控验收：
- `npm run build` 通过；仅保留既有 React Router/Vite/chunk warning。
- `npm run demo:bp44-feel-suite -- -OutputDir .\.runtime\bp44-architect-suite` 通过，输出 `.runtime\bp44-architect-suite\suite-summary.json`。
- MixedMovement：`ok=true`、`sameBattle=true`、warnings `0`、motion/muzzle `11ms/11ms`、command failed `0`、RAF input `>25/>40=0/0`、VFX peak A/B `32/32`、HUD static redraw A/B `0/0`、correction hard/soft `0/0`。
- DualClientPressure：`ok=true`、`sameBattle=true`、warnings `0`、motion/muzzle `1ms/12ms`、A/B command failed `0/0`、RAF input `>25/>40=0/0`、VFX peak A/B `43/38`、HUD static redraw A/B `0/0`、correction hard/soft `0/0`。
- StraightFire：`ok=true`、`sameBattle=true`、warnings `0`、motion/muzzle `6ms/5ms`、command failed `0`、RAF input `>25/>40=0/0`、VFX peak A/B `42/41`、HUD static redraw A/B `0/0`、correction hard/soft `0/0`。

结论：
- BP-44A accepted。当前自动化复核不再支持“本地输入链路明显卡顿”这一假设。
- 下一步应进入 BP-39A：视野/尺度/屏幕速度参照诊断。目标是解释用户可能仍觉得“慢/晕/不够丝滑”的主观来源，而不是继续改已经通过 suite 的 command、RAF、VFX 或 HUD 热路径。

## 最新验收：BP-39A 视野 / 尺度 / 屏幕速度诊断

触发原因：
- BP-44A 已证明输入、命令、RAF、HUD/minimap 静态层、VFX churn 和 correction 都没有显示明确瓶颈。
- 用户仍以实机体感为准，觉得 battle 需要继续向“更丝滑、更爽、更好玩”推进；因此需要先量化画面尺度和屏幕速度，而不是直接盲调速度、zoom 或 camera。

代码处理：
- 新增 `frontend/src/features/battle/renderer/visionDiagnostics.ts`，在 diagnostics 开启时发布 `window.__slayDemoBattleDiagnostics.vision`。
- `battleCameraDirector.ts` 只在 diagnostics 开启时记录 look-ahead 指标；camera 常量保持不变：zoom `1.32`、deadzone `0x0`、look-ahead ratio `0.38`、max `260x260`、lerp `1/1`、roundPixels `false`。
- `gameScenePresentationBridge.ts` 在 HUD 更新前记录 camera/viewport/player display position；主控复核后修正为只读取一次 `localHeroDisplay.read()`，避免诊断造成额外热路径读。
- `scripts/bp28-render-feel-smoke.ps1` 新增 `visionMetric`，输出 camera、viewport、lookAhead、本地英雄 world/screen motion。
- `scripts/bp44-battle-feel-suite.ps1` 将 `visionMetric` 纳入 compact summary。
- 未修改移动速度、sprint、camera 参数、武器、projectile、命中、HUD 布局、VFX 参数、后端、queue、rating/profile 或 `GameScene.ts`。

主控验收：
- `npm run build` 通过；仅保留既有 React Router/Vite/chunk warning。
- MixedMovement：`npm run demo:bp28-render-feel-smoke -- -Scenario MixedMovement -InputDurationMs 3500 -SummaryPath .\.runtime\bp39a-architect-mixedmovement-3500-summary.json` 通过。
- MixedMovement 指标：`ok=true`、`sameBattle=true`、warnings `0`、motion/muzzle `13ms/12ms`、camera zoom `1.32`、worldView 约 `952x476`、screen scale 约 `1.319px/world`、平均屏幕速度约 `260.783px/s`、max 约 `359.72px/s`、look-ahead offset 约 `86.02 world units`。
- BP-44 suite 复跑：`npm run demo:bp44-feel-suite -- -OutputDir .\.runtime\bp39a-architect-feel-suite -SkipStraightFire` 通过。
- Suite 指标：MixedMovement motion/muzzle `1ms/14ms`、平均/最大屏幕速度 A `266.704/357.429px/s`；DualClientPressure motion/muzzle `1ms/5ms`、平均/最大屏幕速度 A `336.799/371.631px/s`、B `276.774/376.168px/s`；两场景 warnings `0`。

结论：
- BP-39A accepted。它把“慢/晕/不够爽”的下一步判断从主观描述推进到可量化的 screen-speed / zoom / worldView / look-ahead 数据。
- 当前证据不支持继续优先修输入链路或后端命令链路；下一票应是 BP-39B 单变量视觉速度/视野标定。可选方向包括小幅调整 `BASE_MOVE_SPEED` / sprint、调整 zoom/worldView，或扩展 headful 视觉复核；不能多变量混改。

## 最新验收：BP-39B Camera Zoom 单变量标定

触发原因：
- BP-39A 证明本地输入、命令、RAF、HUD/VFX 热路径不是当前首要瓶颈，剩余“慢/不够爽”更可能来自屏幕速度感与视觉尺度。
- 直接改 `BASE_MOVE_SPEED` 会触碰前后端 authoritative 移动语义、碰撞、stamina、技能落点和命中节奏；因此先做纯视觉单变量。

代码处理：
- 文件：`frontend/src/features/battle/renderer/camera/battleCameraDirector.ts`。
- 只改 `camera.setZoom(1.32)` 为 `camera.setZoom(1.40)`。
- 未修改移动速度、sprint、stamina、look-ahead ratio/max、camera deadzone、offset lerp、roundPixels、武器、projectile、命中、HUD、VFX、后端、queue、rating/profile 或 `GameScene.ts`。

主控验收：
- worker 复跑 `npm run build` 通过；仅保留既有 React Router/Vite/chunk warning。
- 主控确认改动范围：camera zoom `1.40`，look-ahead/deadzone/lerp/roundPixels 均未变。
- MixedMovement 单场：`ok=true`、warnings `0`、zoom `1.40`、worldView `897x449`、screen scale 约 `1.399px/world`、motion/muzzle `2ms/14ms`、命令失败 `0`、HUD static redraw `0`。
- BP-44 full suite：MixedMovement、DualClientPressure、StraightFire 均 `ok=true`、warnings `0`；DualClientPressure A/B 平均屏幕速度约 `356/306px/s`，StraightFire A 平均约 `355px/s`。
- MixedMovement 补跑：`ok=true`、warnings `0`、motion/muzzle `11ms/10ms`、correction `0/0`、平均/最大屏幕速度 `240/382px/s`。
- Headful MixedMovement：`ok=true`、warnings `0`、zoom `1.40`、worldView `897x449`、平均/最大屏幕速度 `277/394px/s`、motion/muzzle `11ms/10ms`、correction `0/0`、命令失败 `0`、HUD static redraw `0`。

结论：
- BP-39B accepted。它是纯视觉尺度/屏幕速度感调整，不改变 authoritative gameplay 语义。
- 观察项：一次 full suite MixedMovement 出现 soft correction `36`，但专门补跑和 headful 均为 `0/0`，且 zoom 不参与 world-space correction 计算。后续 BP-44B 必须继续把 correction 作为验收指标，不允许在后续调参中把它忽略。

## 最新验收：BP-42A 手枪 Tracer 收敛与长束特效归档

触发原因：

- 用户实机截图显示手枪白色枪口/tracer 与真实橙色 projectile/trail 仍有平行轨道错位残余。
- 当前手枪 tracer 视觉过强，像狙击/穿透/轨道枪特效，不适合普通手枪。

代码处理：

- 文件：`frontend/src/features/battle/renderer/effects/sharedAuthoritativeLocalFeedbackSceneBridge.ts`。
- Pistol tracer 改为 `pistol-short-muzzle-tracer`：length `148 -> 42`，thickness `3 -> 2`，duration `118ms -> 78ms`，alpha `0.62 -> 0.32`，ghostScale `1.9 -> 0.7`。
- Pistol muzzle burst 收敛：radius `10 -> 8`，sparks `3 -> 2`。
- Pistol muzzle 起点未改，仍为 `AUTHORITATIVE_PISTOL_PROJECTILE_BIRTH_DISTANCE = 30`，对应服务端 birth distance。
- 文件：`frontend/src/features/battle/renderer/effects/sceneVfxController.ts`。
- `createProjectileTracer` 支持可选 `glintAlphaScale`；手枪传 `0`，关闭侧向白色 glint，避免表现层制造“平行轨道”错觉。
- 原长束参数保留为未来 `piercing-rail-tracer-long` / rail-piercing weapon style。
- 文件：`docs/BATTLE_VFX_CATALOG.md`。
- 新增 `pistol-short-muzzle-tracer` 和 `piercing-rail-tracer-long` 稳定命名。
- 未修改后端、判定、damage、TTL、projectile speed、hit radius、HUD、`GameScene.ts` 或 gameplay 数值。

验收：

- `npm run build` 通过，只有既有 Vite/React Router/chunk warnings。
- headless MixedMovement：`.runtime/bp42a-mixedmovement-3500-summary.json` 通过，`ok=true`，`sameBattle=true`，warnings `0`，motion `10ms`，muzzle `10ms`，command failed `0`。
- headless DualClientPressure：`.runtime/bp42a-dualclientpressure-3500-summary.json` 通过，`ok=true`，`sameBattle=true`，warnings `0`，A/B command failed `0/0`。
- headful MixedMovement：`.runtime/bp42a-mixedmovement-headful-summary.json` 通过，`ok=true`，`sameBattle=true`，warnings `0`，motion `10ms`，muzzle `9ms`，command failed `0`，input Long Task `0/0`。

结论：

- BP-42A 可接受：代码层已移除手枪长束和侧向 glint 这两个最明显的“手枪太炫/平行轨道”来源，同时保留了长束特效的设计资产。
- 主观视觉仍需要用户实机确认是否“够短、够弱、不卡真实 projectile”；如果仍显得过强，下一步只继续调 `pistol-short-muzzle-tracer` 参数，不改判定。

## 最新验收：BP-41 真实双客户端压力场景

触发原因：

- BP-38 证明单主控 MixedMovement 输入期没有 Long Task，但仍主要是 clientA 操作、clientB 观察。
- 需要验证真实多人压力：两个客户端同时移动、换向、瞄准、开火时，RAF、命令链路、输入探针和 Long Task 是否仍稳定。

代码处理：

- 文件：`scripts/bp28-render-feel-smoke.ps1`。
- 新增 scenario：`DualClientPressure`。
- A/B 两端在同一 input window 内交替注入移动换向、鼠标瞄准轨迹、连续/间歇开火。
- A/B 两端都安装 input event probe 和 command fetch probe。
- A/B 两端都设置 RAF input phase/window，因此 `raf.client*.byDiagnosticPhase` 与 `longTasks.byPhase` 能分别归因各自输入期。
- summary 保留旧字段，并新增 `sameBattle`、`input.clientA/clientB`、`dualClientPressureMetric`。
- 未加入 Q/E/R 技能压力；原因是脚本当前只有安全移动/开火 CDP 输入原语，技能压力留作后续小票。
- 未修改 gameplay、renderer business、backend、HUD、美术、`GameScene.ts`。

验收：

- worker 跑 `npm run build` 通过。
- worker 跑 `npm run demo:bp28-render-feel-smoke -- -Scenario DualClientPressure -InputDurationMs 9000 -SummaryPath .runtime/bp41-dual-pressure-9000-summary.json` 通过。
- 主控复核 summary：`ok=true`，`sameBattle=true`，scenario `DualClientPressure`。
- clientA input Long Task：`0`；clientB input Long Task：`0`。
- clientA input RAF：`556` 帧，p95 `16.8ms`，max `16.9ms`，`>25ms=0`，`>40ms=0`。
- clientB input RAF：`556` 帧，p95 `16.8ms`，max `16.9ms`，`>25ms=0`，`>40ms=0`。
- clientA command fetch：`273` 次，失败 `0`，p95 `9.7ms`，max `17.3ms`。
- clientB command fetch：`275` 次，失败 `0`，p95 `10.3ms`，max `16.8ms`。

结论：

- BP-41 可接受：真实双客户端移动/开火压力下，当前自动化证据不支持“输入期主线程阻塞”或“命令链路卡顿”作为主要问题。
- 下一步应处理用户实机直接指出的 VFX 语义问题：手枪 tracer 过长、枪口特效和真实 projectile 仍有平行轨道错位残余。该问题属于渲染优先级，且比继续盲调性能更直接。

## 最新验收：BP-38 Startup/Input 负载分层

触发原因：

- BP-37 长时 headful 里仍有 Long Task 峰值，但当时无法稳定区分它们属于启动加载、入场资源峰值，还是 gameplay 输入期卡顿。
- BP-40 修完新局新鲜度后，下一步需要判断用户体感卡顿是否仍来自输入链路，还是来自双客户端压力、VFX/HUD churn 或资源/GC。

代码处理：

- 文件：`scripts/bp28-render-feel-smoke.ps1`。
- `raf.client*.longTasks.byPhase` 新增 `startup/pre-input`、`input`、`post-input` 聚合。
- `raf.client*.byDiagnosticPhase` 新增 RAF 分相位统计，直接输出各相位 `over25ms/over40ms`。
- `summary.performanceDelta.clientA/clientB` 新增 CDP `beforeInput -> afterInput` delta，包含 `Resources`、`Nodes`、`JSHeapUsedSize`、`LayoutCount`、`RecalcStyleCount`、`ScriptDuration`、`TaskDuration`。
- 只改诊断脚本，未改玩法、渲染业务、后端、HUD、美术、`GameScene.ts`。

验收：

- worker 跑 `npm run build` 通过，只有既有 Vite chunk/module directive warnings。
- worker 跑 9 秒 headless MixedMovement：`.runtime/bp38-mixedmovement-9000-summary.json` 通过。
- 主控复核 summary：`ok=true`，`sameBattle=true`，命令 POST `289` 次、失败 `0`、p95 `7.6ms`、max `22.5ms`。
- Long Task 分相位：clientA startup/pre-input `5`、input `0`、post-input `0`；clientB startup/pre-input `5`、input `0`、post-input `0`。
- RAF 分相位：clientA input `>25ms=0`、`>40ms=0`；clientB input `>25ms=7`、`>40ms=0`，最大约 `33.4ms`。
- CDP delta：clientA resources `+328`、nodes `+38`、JS heap `+1.68MB`；clientB resources `+323`、nodes `+10`、JS heap `+19.45MB`。

结论：

- BP-38 可接受：当前自动化证据不支持“输入期 Long Task 卡住本地反馈”这个假设。
- 剩余渲染风险应进入 BP-41：让两个真实客户端同时移动、瞄准、开火、释放技能，才能暴露真实多人混战压力；同时保留对 clientB 25-33ms RAF 小抖动和 JS heap 增量的跟踪。

## 最新验收：BP-34B 默认新局 / Active Session Restore 显式化

触发原因：

- 用户实机截图显示刚进入 battle 就是 `03:27`，说明仍有入口会把上一局 active session 当成当前局恢复。
- 后端队列只允许未开始 room 加入，当前更可疑的是前端默认 `/battle` 恢复本地 active session。

代码处理：

- 文件：`frontend/src/features/battle/page/useBattlePageRuntime.ts`。
- 默认 `/battle` 不再恢复 active session；只有显式 `resume=1` 且没有 `new=1` 时才允许恢复。
- `/battle?new=1` 仍强制清理 active/completed session 并开新局。
- 若发现旧 active session 但当前不是显式 resume，则清理 active progress，避免旧 elapsed 污染新局。
- 文件：`frontend/src/features/home/homeGateway.ts`、`frontend/src/pages/ContributionPage.tsx`、`frontend/src/pages/ReplayPage.tsx`、`frontend/src/pages/MailsPage.tsx`、`frontend/src/pages/RatingPage.tsx`。
- “开始/进入战斗”类 CTA 统一指向 `/battle?new=1`。顶部导航保留 `/battle`，因为默认行为现在也安全。
- 未修改 `frontend/src/scenes/GameScene.ts`，未改玩法数值、后端队列、渲染层或结算链。

验收：

- worker 跑 `npm run build` 通过。
- 主控复跑 `npm run build` 通过，只有既有 Vite/Rollup warning。
- 主控跑 MixedMovement smoke：`.runtime/summary-bp34b-mixed-main.json` 通过，`sameBattle=true`，motion latency `9ms`，muzzle latency `12ms`，command fetch `117` 次、失败 `0`、p95 `12.9ms`，warnings `0`。
- 主控做同 profile 专项复现：先用 `/battle?new=1` 进入并存下旧 active session，再不带 `new=1` 直接进 `/battle?diagnostics=1`。
- 专项结果：旧 battleId `battle-7f346f17-3aa5-4a60-985b-226fbb4bda06`，新 battleId `battle-525827d3-9a97-4a4a-b4ff-75d084e83186`，`restoredOldBattle=false`，新局 elapsed 约 `1014ms`。

结论：

- BP-34B 可接受：用户截图里的“新进局继承上一局剩余时间”主因已收口到前端 active session 默认恢复，并已改为显式 resume。
- 若用户仍看到异常倒计时，下一步应检查是否浏览器仍在旧前端 bundle/dev server 热更新前页面，或是否有第三方入口绕过当前路由；不是优先怀疑后端 room 复用。

## 最新验收：BP-35 本地枪口 / Projectile 视觉锚点一致性

触发原因：

- 用户实机截图显示本地白色枪口/tracer 与真实橙色 projectile/trail 有平行偏差。
- 审计发现本地即时 VFX 用本地 display pose 与 `player.radius + 14` 前向偏移，而服务端 pistol projectile birth 是 `18 + 8 + 4 = 30px`。
- shared authoritative runtime 中本地玩家自己的 projectile 也走远程 projectile 插值延迟，会把真实 projectile 显示时间线从本地即时 VFX 中拉开。

代码处理：

- 文件：`frontend/src/features/battle/renderer/entities/worldViewFactory.ts`。
- shared authoritative runtime 下，`projectile.ownerHeroId === snapshot.playerHeroId` 的本地玩家 projectile 不再走 remote interpolation delay；远程玩家 projectile 仍保留原有插值。
- 不使用插值的 projectile 会清理对应 interpolation buffer，避免旧样本残留。
- 文件：`frontend/src/features/battle/renderer/effects/sharedAuthoritativeLocalFeedbackSceneBridge.ts`。
- 新增 pistol authoritative birth distance 常量 `30`，本地 pistol muzzle/tracer 起点改为该距离；其他武器继续使用原视觉偏移。
- 未修改后端、伤害、射程、TTL、速度、命中半径、GameScene、HUD 或地图。

验收：

- worker 跑 `npm run build` 通过。
- 主控复跑 `npm run build` 通过，只有既有 Vite/Rollup warning。
- 主控 MixedMovement smoke：`.runtime/summary-bp35-mixed-main.json` 通过，`sameBattle=true`，motion latency `9ms`，muzzle latency `9ms`，command fetch `122` 次、失败 `0`、p95 `11.3ms`，warnings `0`。

结论：

- BP-35 可接受：它修复的是“本地即时 VFX 与本地真实 projectile 视觉时间线/锚点不同源”的问题，不改变任何战斗语义。
- 后续仍需要用户实机确认截图中的平行偏差是否消失；如果仍存在，下一步应抓 headful 帧并比较 projectile display position、muzzle diagnostic position 与 hero display pose。

## 最新验收：BP-36A 障碍物四边闭合视觉修复

触发原因：

- 用户截图指出小 crate 看起来四边封闭，但大 wall/obstacle 像 N 形或缺底，会误导玩家以为下方可通过。
- 审计确认碰撞体和 `obstacleBounds` 没有缺口，问题在 arena visual skin 的闭合感不足。

代码处理：

- 文件：`frontend/src/features/battle/renderer/arena/arenaBuilder.ts`。
- `createStaticObstacleMetalSkin` 为 inner wall/crate 增加底边、左右边和四角角板。
- crate 原来的中线式上沿改为更接近顶部的上沿；wall 保留中部 brace，同时底部更明显封闭。
- `createCoverFootprintCues` 增加底部 footprint 提示线，强化“这里是实体掩体/墙”的读法。
- 未修改 `setDisplaySize`、`refreshBody`、`wallBodies.add`、`obstacleBounds.push`、`INNER_OBSTACLES`、world size、minimap、碰撞、后端、GameScene 或玩法语义。

验收：

- worker 跑 `npm run build` 通过。
- 主控复跑 `npm run build` 通过，只有既有 Vite/Rollup warning。
- 主控 MixedMovement smoke：`.runtime/summary-bp36a-mixed-main.json` 通过，`sameBattle=true`，motion latency `2ms`，muzzle latency `12ms`，command fetch `122` 次、失败 `0`、p95 `11.1ms`，warnings `0`。

结论：

- BP-36A 可接受：这是 visual-only 修复，降低地图误读风险，不改变碰撞和路径语义。
- 后续需要 headful 截图复核闭合边框是否足够清楚，同时确认新增窄边框没有明显遮挡角色、弹道或拾取物。

## 最新验收：BP-36B BP-35/BP-36A Headful 回归复核

验证目标：

- 确认本地 projectile 显示时间线调整与障碍物闭合视觉层在真实窗口路径下没有造成性能、同步或本地反馈回退。

验收：

- 命令：`scripts/bp28-render-feel-smoke.ps1 -Headful -Scenario MixedMovement -InputDurationMs 3500 -SummaryPath .\.runtime\summary-bp36a-mixed-headful-main.json`。
- 结果：`ok=true`，`sameBattle=true`，battleId `battle-8c374544-d5c7-4c4c-9630-1ac4ba468ad4`。
- 指标：motion latency `8ms`，muzzle latency `9ms`，command fetch `128` 次、失败 `0`、p95 `14.5ms`、max `26.1ms`，warnings `0`。

结论：

- BP-35/BP-36A 在 headful 路径下可接受，没有引入可见的自动化回归。
- 仍需用户实机确认截图中的平行弹道和缺底障碍物是否主观解决；下一步应转向更长真实混战采样，而不是继续在同一短脚本上反复调。

## 最新验收：BP-37 长时 Headful 混合输入复核

验证目标：

- 用更长的真实窗口采样确认 BP-35/BP-36A 后没有在长时间移动、开火、同步和命令链路上引入回退。
- 观察是否还有卡顿峰值、Long Task 或诊断指标异常，避免只靠 3.5 秒短 smoke 判断渲染完成。

验收：

- 命令：`scripts/bp28-render-feel-smoke.ps1 -Headful -Scenario MixedMovement -InputDurationMs 9000 -SummaryPath .\.runtime\summary-bp37-long-headful-main.json`。
- 结果：`ok=true`，`sameBattle=true`，battleId `battle-3e813df0-44f9-492d-aecd-a269def9e300`。
- 命令链路：`/battle/commands` 请求 `292` 次，失败 `0`，p95 `11.8ms`，max `35ms`。
- 帧稳定性：clientA RAF p95 `16.8ms`、>25ms 帧 `3`、>40ms 帧 `0`；clientB RAF p95 `16.8ms`、>25ms 帧 `6`、>40ms 帧 `0`。
- Long Task：两端各记录 `6` 次，最大约 `473-474ms`；复核后它们都发生在输入开始前约 `2.9-14.5s`，不属于输入期 RAF 卡顿。这不是命令链路问题，后续需要把 startup/load 与 gameplay input 指标分开。
- 诊断异常：summary 报 `motionLatencyMs=4416`，但同一份数据里 `after.motion.firstAtMs=15059.8`，`firstMovementInputEventPageMs=15057.5`，真实首个 motion sample 约 `2.3ms`。该差异来自长采样 capped sample window 把早期样本截掉后仍用窗口内第一条样本计算 latency。

结论：

- BP-37 可接受为“长时 headful 无同步/命令/帧率硬回退”，但不能宣布渲染最终完成。
- 下一票 BP-37A 必须先修复 smoke 诊断口径：local feedback latency 不能被 sampleWindow 截断影响，否则后续会把不存在的 4 秒本地反馈延迟误判为 gameplay 或渲染问题。
- 用户实机反馈 BP-35/BP-36 后“非常棒、明显优化”，说明当前方向有效；箱子边框美术只是临时可接受，后续可以通过素材替换或更精细 metal skin 改善。

## 最新验收：BP-37A 长采样 Local Feedback Latency 口径修复

触发原因：

- BP-37 长时 headful summary 报 `motionLatencyMs=4416`，但同一份数据里 `after.motion.firstAtMs=15059.8`、`firstMovementInputEventPageMs=15057.5`，真实首个 motion sample 约 `2.3ms`。
- 根因不是 gameplay 卡顿，而是 `sampleWindowSize=240` 在长采样中截断了早期 `recentSamples`，脚本仍用窗口内第一条样本计算 latency。

代码处理：

- 文件：`scripts/bp28-render-feel-smoke.ps1`。
- `Find-FirstLocalFeedbackSampleAfterCount` 仍优先使用短采样窗口内的真实样本。
- 当 `BeforeCount=0` 且窗口首样本明显晚于 channel `firstAtMs` 时，使用 `channel.firstAtMs` 合成最小首样本元数据计算 latency。
- 合成样本记录 `source="channel.firstAtMs"`、`sampleWindowTruncated=true`、`windowedFirstSampleAtMs`、`channelSampleCount` 和 `sampleWindowSize`，便于后续判断指标来源。
- 未修改玩法、渲染业务、后端、`GameScene.ts`、HUD、武器/技能数值或命中判定。

验收：

- worker 运行 `npm run build` 通过，仅有既有 Vite/chunk warning。
- worker 长采样 headless：`scripts/bp28-render-feel-smoke.ps1 -Scenario MixedMovement -InputDurationMs 9000 -SummaryPath .runtime/summary-bp37a-long-headless-mixedmovement.json`。
- 主控复核 summary：`ok=true`，`sameBattle=true`，motion latency `11ms`，muzzle latency `10ms`。
- 关键证据：`motion.firstSample.source=channel.firstAtMs`，`motion.firstSample.windowedFirstSampleAtMs=18943`，`after.motion.firstAtMs=15012.2`，说明已避开窗口截断误算。
- 运行指标：command fetch `273` 次、失败 `0`、p95 `15.9ms`、max `49.9ms`；clientA RAF p95 `16.8ms`、>25ms `1`、>40ms `0`；clientB RAF p95 `16.8ms`、>25ms `2`、>40ms `0`；warnings `0`。

结论：

- BP-37A 可接受：这是诊断可信度修复，不改变 battle 语义。
- 后续性能判断必须以修复后的 local feedback latency 为准；当前不能再把 BP-37 的 `4416ms` 当成真实本地输入延迟。
- BP-38 已完成该分层；后续继续用分相位指标判断 BP-41 真实双客户端压力下的剩余体感风险。

## 最新验收：BP-40 连续新局倒计时新鲜度

触发原因：

- 用户实机复现第二轮刚进入 battle 仍显示上一轮剩余时间，例如 `03:18` / `03:27`。
- BP-34B 已堵住默认 `/battle` 恢复本地 active session，但实机仍复现，说明还有后端 queue/room 或旧异步写回路径。

代码处理：

- 文件：`frontend/src/features/battle/page/useBattlePageRuntime.ts`。
- 每个 runtime effect 生成新的 `queueRequestId`，`joinMatchmakingQueue` 时提交给后端。
- 旧 authoritative finalization 在 `queueJoinCancelled` 或已 finalized 后不再写回 active session，避免旧 runtime 异步结果污染新局。
- 文件：`frontend/src/features/battle/page/matchmakingQueueGateway.ts`。
- `joinMatchmakingQueue` 支持可选 `queueRequestId` 并提交到 `/battle/queue/join`。
- 文件：`backend/src/main/scala/battle/api/BattleQueueApi.scala`、`backend/src/main/scala/battle/routes/BattleQueueRoutes.scala`、`backend/src/main/scala/battle/services/BattleQueueService.scala`。
- 后端 queue participant 保存 `queueRequestId`；同 handle + 同 `queueRequestId` 仍保持幂等；同 handle + 不同 fresh `queueRequestId` 不复用同一个 waiting ticket/room。
- 文件：`scripts/bp40-battle-session-freshness-smoke.ps1`、`package.json`。
- 新增 `npm run demo:bp40-freshness`，覆盖同 request 幂等、不同 request 不复用 waiting room、连续两局 battleId/elapsed/remaining 新鲜度。

验收：

- `npm run build` 通过，仅有既有 Vite/chunk warning。
- `npm run backend:compile` 通过；旧后端 PID `32076` 已停止，新后端已启动，`/health` 返回 `status=ok`。
- `npm run demo:bp40-freshness` 通过：round1 battleId `battle-0ff449d5-ea0b-4fb1-a1b3-6fbf3b1c8149`，round1 elapsed `1678ms`；round2 battleId `battle-a1767e75-2028-491f-a78a-fc21779e6f1a`，round2 elapsed `85ms`，remaining 从 `298322ms` 回到 `299915ms`。
- 回归 smoke：`.runtime/summary-bp40-mixed-main.json` 通过，`sameBattle=true`，battleId `battle-f4a64207-fdf0-4d5a-ac97-56e8d9814f43`，motion latency `6ms`，muzzle latency `5ms`，command fetch `104` 次失败 `0`，warnings `0`。

结论：

- BP-40 可接受：连续新局不应再继承上一局剩余时间。
- 回归 smoke 中仍有少量约 `50ms` 帧，归入 BP-38/BP-41 的负载分层与真实双客户端压力，而不是 BP-40 session 新鲜度问题。
- 如果用户仍复现倒计时继承，下一步应优先检查是否浏览器仍停留在热更新前页面，或是否存在未走当前 queue join 的第三方入口。

## 最新验收：BP-31S Command Fetch Probe

触发原因：

- BP-31R 已证明本地 movement/muzzle 反馈是 1 帧内，但旧 `confirmLatencyMs` 仍在数百毫秒区间。
- 需要确认这是不是 `/battle/commands` 网络 POST 慢，避免误判优化方向。

代码处理：

- 文件：`scripts/bp28-render-feel-smoke.ps1`。
- 在 page-side 输入前安装短生命周期 `window.fetch` 探针，仅记录 URL 包含 `/battle/commands` 的请求。
- 记录 request count、failed count、first/p95/max duration、HTTP status、`clientCommandSeq`、`primaryHeld` 和 movement body。
- 探针在读取 summary 后恢复原始 fetch，不进入业务代码。
- 未修改前端业务、后端、GameScene、玩法数值或渲染。

验收：

- worker 跑 `npm run build` 与 smoke 通过。
- 主控复跑 `npm run build` 通过。
- 主控 headless MixedMovement：`.runtime/summary-bp31s-mixed-headless-main.json` 通过，command count `120`，failed `0`，first duration `6.9ms`，p95 `11.2ms`，max `33.1ms`，confirm `904ms`，motion `3ms`，muzzle `14ms`，RAF p95 `16.8ms`，warnings `0`。
- 主控 headful MixedMovement：`.runtime/summary-bp31s-mixed-headful-main.json` 通过，command count `110`，failed `0`，first duration `5.8ms`，p95 `26.5ms`，max `67.2ms`，confirm `941ms`，motion `7ms`，muzzle `5ms`，RAF p95 `16.8ms`，warnings `0`。

结论：

- `/battle/commands` POST/ack 本身不是当前主要卡顿瓶颈。
- 旧 `confirmLatencyMs` 更像“状态效果被采样确认”的延迟，不应被直接解释成网络请求耗时。
- 后续手感优先级应转向视觉锚点一致性、远程显示时间线、状态/VFX通道分离与真实混战压力，而不是盲目优化 command fetch。

## 最新验收：BP-31R Movement / Fire Input Basis Split

触发原因：

- BP-31Q 证明真实页面输入事件到 motion 反馈已经是 1 帧内，但 muzzle latency 仍共用第一个输入事件基准。
- 在 MixedMovement 中，第一个输入事件通常是 `keydown d`，不能等价为 `mousedown` 到枪口反馈。

代码处理：

- 文件：`scripts/bp28-render-feel-smoke.ps1`。
- page-side probe 保留 `firstInputEvent*` 兼容字段，同时新增 `firstMovementInputEvent*` 与 `firstFireInputEvent*`。
- `New-LocalFeedbackLatencyMetric` 中 motion 使用 movement 基准，muzzle 使用 fire 基准；某条专用基准缺失时只对该通道回退，不让 movement/fire 互相污染。
- summary 顶层 `input`、`inputProbe.localFeedback`、最终 `localFeedbackLatencyMetric` 都输出 `motionLatencyBasis` / `muzzleLatencyBasis` 和对应输入时间。
- 不改输入动作顺序、持续时间、阈值、玩法逻辑、前端业务代码、后端代码或 `GameScene.ts`。

验收：

- `npm run build` 通过；仅保留既有 Vite/Rollup 警告。
- Worker 初跑 MixedMovement 3500ms 通过：movement=`keydown d`，fire=`mousedown 0`，motion basis=`firstMovementInputEventPageMs`，muzzle basis=`firstFireInputEventPageMs`，motion `7ms`，muzzle `7ms`，warnings `0`。
- 主控 Headless MixedMovement 3500ms：`summary-bp31r-mixed-headless-main.json` 通过，`sameBattle=true`，motion `10ms`，muzzle `9ms`，confirm `679ms`，RAF p95 `16.8ms`，>25ms 帧 `0`，hard snap `0`，soft correction `0`，warnings `0`。
- 主控 Headful MixedMovement 3500ms：`summary-bp31r-mixed-headful-main.json` 通过，`sameBattle=true`，motion `9ms`，muzzle `9ms`，confirm `704ms`，RAF p95 `16.8ms`，>25ms 帧 `1`，hard snap `0`，soft correction `1`，warnings `0`。

结论：

- BP-31R 作为测试基准票已接受。
- 本地玩家移动反馈和第一发枪口反馈都已经在 1 帧内；不应继续把“卡顿/不好玩”归因到本地输入采样或本地 VFX 创建。
- 下一步优先级转向：远程实体/弹道显示时间线、命中争议样本、视觉层级和 correction 体感。若用户继续感到卡顿，主控应先抓取真实 headful 画面/指标，而不是盲目改速度或本地输入链路。

## 最新验收：BP-31Q Page-side Input Event Timestamp

触发原因：

- BP-31P 把 localFeedback 基准从旧 `inputStartPageMs` 推进到 `inputDispatchStartPageMs` 后，MixedMovement 仍显示约 `38-41ms`。
- 该数值仍包含 CDP 命令发出到浏览器实际派发 `keydown/mousedown` 的注入开销，不能直接当作游戏业务链路延迟。

代码处理：

- 文件：`scripts/bp28-render-feel-smoke.ps1`。
- 在真实输入注入前安装短生命周期 page-side probe，只监听 `keydown` 与 `mousedown`，捕获第一个真实页面输入事件。
- 读取后立即 cleanup 事件监听器并删除 `window.__bp28InputEventProbe`，不进入游戏业务运行时。
- summary 新增 `firstInputEventPageMs`、`firstInputEventType`、`firstInputEventKeyOrButton`、`firstInputEventWallMs`、`dispatchToEventOverheadMs`。
- localFeedback latency 优先使用 `firstInputEventPageMs`，缺失时回退到 `inputDispatchStartPageMs`。

验收：

- `npm run build` 通过；仅保留既有 Vite/Rollup 警告。
- Headless MixedMovement 3500ms：`summary-bp31q-mixed-headless-main.json` 通过，`sameBattle=true`，first event 为 `keydown d`，dispatch-to-event overhead `34.4ms`，latencyBasis=`firstInputEventPageMs`，motion `1ms`，muzzle `20ms`，confirm `427ms`，RAF p95 `16.8ms`，>25ms 帧 `0`，hard snap `0`，soft correction `0`，warnings `0`。
- Headful MixedMovement 3500ms：`summary-bp31q-mixed-headful-main.json` 通过，`sameBattle=true`，first event 为 `keydown d`，dispatch-to-event overhead `60.7ms`，latencyBasis=`firstInputEventPageMs`，motion `9ms`，muzzle `11ms`，confirm `452ms`，RAF p95 `16.8ms`，>25ms 帧 `0`，hard snap `0`，soft correction `4`，warnings `0`。

结论：

- BP-31Q 作为测试基准票已接受；它证明“移动本地反馈 38-41ms”主要是测试注入开销，不是游戏移动链路真实慢。
- 真实页面输入事件到本地 motion 反馈目前在 1 帧内，渲染卡顿主因不应继续优先怀疑移动输入采样。
- 当前 muzzle latency 仍以第一个输入事件为基准；MixedMovement 的第一个事件通常是 `keydown d`，所以 muzzle 指标还不能等价为 mousedown-to-muzzle。
- 下一张票应继续只改 smoke：拆分 `firstMovementInputEventPageMs` 与 `firstFireInputEventPageMs`，让 motion 和 muzzle 分别使用正确基准。若 fire 基准下仍超过 1-2 帧，再进入开火本地反馈链路优化。

## 最新验收：BP-34A 新局计时器 / Active Session Freshness

触发原因：

- 用户用两个账号实测时发现两边计时器可能不同，其中一个账号像是把上一把剩余时间继承到了新一把。
- 该问题必须现在修，而不是排在渲染之后；因为它会污染 battle 生命周期、多人同步可信度和所有后续 smoke 的时间基准。

代码处理：

- 文件：`frontend/src/features/battle/page/useBattlePageRuntime.ts`。
- `/battle?new=1` 和 `startNewMatch()` 进入一次性新局 reset：清理当前账号 active/completed battle session。
- 旧 runtime 的 `pagehide` / teardown 如果处于新局 reset，不再把上一把 active session 重新写回 localStorage。
- shared authoritative session 只允许在后端 state 可加载、battleId 一致、phase 未 finished、`elapsedMs < durationMs` 时恢复。
- 如果 restored session 的 battleId 与新 queue/backend battleId 不一致，直接丢弃旧 session 并使用新 authoritative state。

验收：

- 常规 render-feel smoke：`summary-bp34a-continuous-profile-run1.json` 通过。`sameBattle=true`，confirm `319ms`，本地移动反馈 `14ms`，枪口反馈 `16ms`，clientA/clientB RAF p95 `16.8ms`，`>25ms=0`，hard snap `0`，soft correction `0`，warnings `0`。
- 专项连续 profile 验证：先让旧 active session 存在，再绕过 `bp14-client` 清缓存启动器，直接用同一浏览器 profile 进入 `/battle?new=1`。
- 专项结果：旧 session 为 `battle-15206508-1139-449d-ac69-05abff84dba8`，旧 elapsed `61567ms`；新局进入 `battle-c80944c2-3b01-4562-b90a-c541e5777ca8`，两客户端同一 battle，前端 elapsed 约 `364ms/365ms`，后端 elapsed 约 `485ms`。

结论：

- “上一把剩余时间继承到下一把”的前端会话恢复链路已修掉。
- 剩余风险在后端 matchmaking 仍主要按 handle/ticket/room 组织；如果未来出现“仍在倒计时房间内重复点开始”导致复用同一 waiting room，需要单独做 backend fresh-join 语义。但已开始的旧 battle 不会通过当前前端 active session 恢复污染新局。
- BP-31O 长时间/混合输入渲染 smoke 现在可以继续执行。

## 最新验收：BP-31O 长时间 / 混合输入 render-feel smoke

触发原因：

- 旧 smoke 只覆盖固定右移 + 持续开火，无法代表玩家实际的换向、停顿、多方向移动和长输入窗口。
- 用户主观反馈的“卡顿/不够丝滑”不一定体现在 RAF 掉帧上，因此需要把自动化从帧率检测扩展到 input-to-render 首帧延迟检测。

代码处理：

- 文件：`scripts/bp28-render-feel-smoke.ps1`。
- 新增参数 `-Scenario StraightFire|MixedMovement`，默认 `StraightFire`，兼容旧调用。
- 新增参数 `-InputDurationMs`，默认 `1800`，并限制必须 `>= 1000`，避免无效输入制造假失败。
- `MixedMovement` 在同一输入窗口内覆盖 `D`、停顿、`S+D`、`A`、停顿、`W+A`、`D`，并在开局短按开火，保证 muzzle latency 仍可与默认场景横向比较。
- summary 顶层新增 `scenario` / `inputDurationMs`，`input` 内新增 `scenario` / `fireStartOffsetMs` / `fireEndOffsetMs` / `durationMs`，原指标结构保持不变。

验收：

- `npm run build` 通过，仅有既有 Vite/Rollup 警告。
- 默认 StraightFire：`summary-bp31o-straight-main2.json` 通过。`sameBattle=true`，confirm `395ms`，移动反馈 `29ms`，枪口反馈 `49ms`，RAF p95 `16.8ms`，`>25ms=0`，hard snap `0`，soft correction `0`，warnings `0`。
- Headless MixedMovement 3500ms：`summary-bp31o-mixed-main2.json` 通过。`sameBattle=true`，confirm `402ms`，移动反馈 `46ms`，枪口反馈 `49ms`，RAF p95 `16.8ms`，`>25ms=0`，hard snap `0`，soft correction `0`，warnings `0`。
- Headful MixedMovement 3500ms：`summary-bp31o-mixed-headful-main.json` 通过。`sameBattle=true`，confirm `559ms`，移动反馈 `58ms`，枪口反馈 `77ms`，RAF p95 `16.8ms`，`>25ms=0`，hard snap `0`，soft correction `3`，warnings `0`。

结论：

- BP-31O 作为测试能力票已接受：它没有改变 gameplay，只让 smoke 能覆盖更接近实机的长输入/换向场景。
- 指标给出一个明确方向：当前 RAF 很稳，但 input-to-render 首帧延迟在 headful MixedMovement 下可到 `58ms/77ms`。这解释了为什么玩家可能仍觉得“帧率没掉但手感不够贴手”。
- 下一张主线票应审计本地输入采样、authoritative local feedback、Phaser update/diagnostics 记录点，目标是把首个移动/枪口反馈重新压回接近 1 帧到 2 帧，而不是继续先调视觉素材或摄像机。

## 最新验收：BP-31P input-to-render smoke 起点校准

触发原因：

- BP-31O 暴露 MixedMovement headful 下 local feedback `58ms/77ms`，但只读审计确认旧测量起点早于实际 CDP 输入注入。
- 如果不先校准测试基准，后续会把 focus、CDP 调度、脚本准备开销误判为游戏业务延迟。

代码处理：

- 文件：`scripts/bp28-render-feel-smoke.ps1`。
- 保留旧 `inputStartPageMs` / `inputStartPageWallMs`，避免破坏旧 summary 兼容。
- 新增 `inputDispatchStartPageMs` / `inputDispatchStartWallMs`，在首个实际 key/mouse dispatch 前读取。
- 新增 `preDispatchOverheadMs`，表达旧起点到 dispatch 起点的准备开销。
- localFeedback latency 改用 `inputDispatchStartPageMs` 作为基准，并在 metric 中标注 `latencyBasis=inputDispatchStartPageMs`。
- authoritative/pageSnapshot/remoteView 确认延迟继续沿用旧 wall-clock 结构，避免改变既有确认指标语义。

验收：

- `npm run build` 通过，仅有既有 Vite/Rollup 警告。
- Headless MixedMovement 3500ms：`summary-bp31p-mixed-headless-main.json` 通过。`sameBattle=true`，`preDispatchOverheadMs=7`，移动反馈 `39ms`，枪口反馈 `41ms`，confirm `405ms`，RAF p95 `16.8ms`，`>25ms=0`，hard snap `0`，soft correction `1`，warnings `0`。
- Headful MixedMovement 3500ms：`summary-bp31p-mixed-headful-main.json` 通过。`sameBattle=true`，`preDispatchOverheadMs=6`，移动反馈 `38ms`，枪口反馈 `41ms`，confirm `401ms`，RAF p95 `16.8ms`，`>25ms=0`，hard snap `0`，soft correction `0`，warnings `0`。

结论：

- 之前 BP-31O 的 `58ms/77ms` 明显包含测量基准偏早造成的假高；BP-31P 后降到约 `38-41ms`。
- 但 `38-41ms` 仍然不应直接当作真实玩家输入延迟，因为 dispatch 起点仍在 CDP 命令发出前，不是浏览器实际收到 `keydown/mousedown` 的时间。
- 下一步应让 smoke 在页面内记录 first keydown/mousedown event timestamp，用 page-side input event 作为 localFeedback latency 基准。只有那之后，才能判断是否需要优化真实业务链路。

## 最新验收：BP-28S-10F 渲染/手感 smoke

已运行：

```powershell
npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-10e.json
```

结果：通过。

关键指标：

- 两个客户端进入同一个 battle：`sameBattle=true`。
- 权威输入确认：移动和开火都被后端确认，确认延迟约 `324ms`，且发生在输入窗口内。
- 本地反馈延迟：移动反馈约 `11ms`，枪口反馈约 `15ms`。
- RAF：clientA/clientB 平均约 `16.68ms`，p95 约 `16.8ms`，p99 约 `16.9ms`，`>25ms` 帧为 `0`，`>40ms` 帧为 `0`。
- 远程视图：远程英雄被观察到，输入后远程采样延迟约 `4ms`，远程 projectile birth 延迟约 `90ms`。
- 本地 correction：无 hard snap，无 soft correction；129 次误差都落在 deadzone，被忽略。preDistance 平均约 `2.84px`，最大约 `9.40px`，p95 约 `6.51px`。
- warnings：空。

结论：自动化 smoke 证明当前链路没有明显掉帧、输入反馈有本地即时反馈、远端视图与 projectile birth 可观测、校正没有硬拉回。但这还不能代表“实机体感已经达到最终标准”，因为 headless/脚本输入不能覆盖玩家主观卡顿、地图复杂区域、多人混战、技能连发、武器切换、命中争议等场景。

## 最新验收：BP-28S-10G Projectile 渲染延迟解耦

改动边界：

- 文件：`frontend/src/features/battle/renderer/entities/worldViewFactory.ts`。
- `AUTHORITATIVE_REMOTE_HERO_INTERPOLATION_DELAY_MS` 保持 `100`。
- `AUTHORITATIVE_PROJECTILE_INTERPOLATION_DELAY_MS` 从复用远程英雄延迟，改为独立 `66`。
- 不改后端、命中、武器数值、角色速度、HUD 文案、BattlePage 壳、路由、结算、匹配、GameScene。

验收：

- worker 运行 `npm run build` 通过。
- 主控复核常量使用点：远程英雄 display state 仍使用 hero delay；projectile display state 使用 projectile delay。
- 主控运行 `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-10g.json` 通过。

10G smoke 关键指标：

- `sameBattle=true`，两个客户端进入同一 battle。
- 权威输入确认延迟约 `316ms`，移动和开火均确认，且发生在输入窗口内。
- 本地反馈延迟：移动约 `13ms`，枪口约 `16ms`。
- RAF：clientA/clientB 平均约 `16.68ms`，p95 约 `16.8ms`，`>25ms` 帧为 `0`，`>40ms` 帧为 `0`。
- 远程 projectile birth 延迟约 `101ms`，projectile birth delta 为 `9`，可观测。
- 本地 correction：无 hard snap；仅 1 次 soft correction，129 次 deadzone ignored；preDistance 平均约 `3.40px`，最大约 `8.89px`，p95 约 `7.43px`。

10G 结论：这是低风险、单变量渲染手感改动。它应该减少“命中/扣血反馈已经发生，但 projectile 视觉还慢半拍”的错觉，但仍需实机手感验证，不能视为最终完成。

## 最新验收：BP-28S-10H Camera Follow 去阻尼

改动边界：

- 文件：`frontend/src/features/battle/renderer/gameSceneCameraBridge.ts`。
- `camera.startFollow(cameraTarget, true, 0.16, 0.16)` 改为 `camera.startFollow(cameraTarget, true, 1, 1)`。
- 不改 camera deadzone、pointer look-ahead、移动速度、冲刺、reconciliation、projectile、命中、HUD、后端或 GameScene。

验收：

- worker 运行 `npm run build` 通过。
- 主控复核 GameScene 未被改动，仍约 514 LOC / 24,711 bytes。
- 主控运行 `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-10h.json` 通过。
- 主控补跑 headful：`scripts/bp28-render-feel-smoke.ps1 -Headful -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-headful-10h.json` 通过。

10H smoke 关键指标：

- `sameBattle=true`，两个客户端进入同一 battle。
- 权威输入确认延迟约 `335ms`，移动和开火均确认，且发生在输入窗口内。
- 本地反馈延迟：移动约 `13ms`，枪口约 `16ms`。
- RAF：clientA/clientB 平均约 `16.68ms`，p95 约 `16.8ms`，p99 约 `16.9ms`，`>25ms` 帧为 `0`，`>40ms` 帧为 `0`。
- 远程 projectile birth 延迟约 `97ms`，可观测。
- 本地 correction：无 hard snap，无 soft correction；131 次误差都落在 deadzone，被忽略。preDistance 平均约 `2.18px`，最大约 `6.49px`，p95 约 `5.93px`。
- Headful 复核：RAF 仍约 `16.68ms`，`>25ms`/`>40ms` 帧均为 `0`；无 hard snap，仅 2 次 soft correction；远程 projectile birth 延迟约 `122ms`。

10H 结论：这是已接受的单变量手感票。它不会解决所有“卡顿”问题，但如果用户之前感受到的是摄像机追随慢半拍，这一票应当明显改善跟手感。风险是画面可能比旧版本更直接、更少缓冲；如果实机出现轻微抖动，下一票单独处理 camera deadzone 或 pointer look-ahead，而不是回到多变量混改。

## 最新验收：BP-28S-10I Camera Deadzone 归零

改动边界：

- 文件：`frontend/src/features/battle/renderer/camera/battleCameraDirector.ts`。
- `CAMERA_DEADZONE` 从 `{ width: 112, height: 82 }` 改为 `{ width: 0, height: 0 }`。
- 不改 `gameSceneCameraBridge.ts`、camera follow lerp、pointer look-ahead、offset lerp、移动速度、冲刺、reconciliation、projectile、命中、HUD、后端或 GameScene。

验收：

- worker 运行 `npm run build` 通过。
- 主控复核 GameScene 未被改动，仍约 514 LOC / 24,711 bytes。
- 主控运行 `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-10i.json` 通过。
- 主控补跑 headful：`scripts/bp28-render-feel-smoke.ps1 -Headful -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-headful-10i.json` 通过。

10I smoke 关键指标：

- `sameBattle=true`，两个客户端进入同一 battle。
- 权威输入确认延迟约 `337ms`，移动和开火均确认，且发生在输入窗口内。
- 本地反馈延迟：移动约 `13ms`，枪口约 `16ms`。
- RAF：clientA/clientB 平均约 `16.68ms`，p95 约 `16.8ms`，p99 约 `16.9ms`，`>25ms` 帧为 `0`，`>40ms` 帧为 `0`。
- 远程 projectile birth 延迟约 `132ms`，可观测。
- 本地 correction：无 hard snap；仅 1 次 soft correction，130 次 deadzone ignored。preDistance 平均约 `3.74px`，最大约 `12.34px`，p95 约 `10.10px`。
- Headful 复核：RAF 仍约 `16.68ms`，`>25ms`/`>40ms` 帧均为 `0`；无 hard snap，无 soft correction；远程 projectile birth 延迟约 `118ms`。

10I 结论：这是已接受的单变量摄像机手感票。它应当减少中心死区导致的“角色动了但画面还没跟”的粘滞感。风险是小位移时画面更敏感；如果实机出现抖动，下一票只处理 offset lerp/look-ahead，不回退到多变量混改。

## 最新验收：BP-28S-10J 远程实体 Render-Time 对齐

改动边界：

- 文件：`frontend/src/features/battle/renderer/entities/worldViewFactory.ts`。
- remote hero interpolation delay 从独立 `100ms` 改为共享 `83ms`。
- authoritative projectile interpolation delay 从独立 `66ms` 改为共享 `83ms`。
- 两个 display state 的 `renderAtMs` 都使用 `AUTHORITATIVE_REMOTE_ENTITY_INTERPOLATION_DELAY_MS`。
- 不改 smoothing、snapDistance、buffer cap、武器数值、projectile speed/TTL/radius/damage、VFX 参数、GameScene、HUD、API 或后端判定。

验收：

- worker 运行 `npm run build` 通过。
- 主控复核常量使用点：remote hero 与 projectile 不再互相偏移显示时间线。
- 主控复核 GameScene 未被改动，仍约 514 LOC / 24,711 bytes。
- 主控运行 `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-10j.json` 通过。

10J smoke 关键指标：

- `sameBattle=true`，两个客户端进入同一 battle。
- 权威输入确认延迟约 `329ms`，移动和开火均确认，且发生在输入窗口内。
- 本地反馈延迟：移动约 `12ms`，枪口约 `16ms`。
- RAF：clientA/clientB 平均约 `16.68ms`，p95 约 `16.8ms`，p99 约 `16.8-16.9ms`，`>25ms` 帧为 `0`，`>40ms` 帧为 `0`。
- 远程 projectile birth 延迟约 `131ms`，可观测。
- 本地 correction：无 hard snap；8 次 soft correction，122 次 deadzone ignored。preDistance 平均约 `5.97px`，最大约 `14.44px`，p95 约 `11.41px`。

10J 结论：这是已接受的单变量视觉可信度票。它牺牲了一点 10G 的 projectile 低延迟显示，但换回 remote hero/projectile 同一 render-time，降低“看着打中但服务端未判”的错觉。真正的本地开火爽感继续由 zero-latency muzzle/tracer ghost 提供，而不让真实 projectile 视觉对远程身体撒谎。

## 最新验收：BP-28S-10K Projectile Terminal Trail

改动边界：

- 文件：`frontend/src/features/battle/renderer/effects/battleFeedbackSceneBridge.ts`。
- 文件：`frontend/src/features/battle/renderer/gameSceneFeedbackBridgeFactory.ts`。
- `ProjectileFeedbackState` 记录最后 `direction`。
- terminal feedback 保留现有 `createImpactSpark`，并新增短 terminal tracer。
- tracer 统一 `180ms`；长度：pistol/gatling `34`，shotgun `22`，rocket `48`；终点落在上一帧 projectile display position。
- 不改 `worldViewFactory.ts` 的 projectile view 销毁语义，不改 backend、snapshot、TTL、speed、damage、radius、hit 判定、GameScene、HUD 或 API。

验收：

- worker 运行 `npm run build` 通过。
- 主控复核改动边界：只改 feedback bridge 和 factory，GameScene 未被改动，仍约 514 LOC / 24,711 bytes。
- 主控运行 `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-10k.json` 通过。
- 主控补跑 headful：`scripts/bp28-render-feel-smoke.ps1 -Headful -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-headful-10k.json` 通过。

10K smoke 关键指标：

- `sameBattle=true`，两个客户端进入同一 battle。
- 权威输入确认延迟约 `328ms`，移动和开火均确认，且发生在输入窗口内。
- 本地反馈延迟：移动约 `13ms`，枪口约 `16ms`。
- RAF：clientA/clientB 平均约 `16.68ms`，p95 约 `16.8ms`，p99 约 `16.8-16.9ms`，`>25ms` 帧为 `0`，`>40ms` 帧为 `0`。
- 远程 projectile birth 延迟约 `113ms`，可观测。
- 本地 correction：无 hard snap；5 次 soft correction，125 次 deadzone ignored。preDistance 平均约 `3.84px`，最大约 `9.26px`，p95 约 `7.72px`。
- Headful 复核：RAF 仍约 `16.68ms`，`>25ms`/`>40ms` 帧均为 `0`；无 hard snap，无 soft correction；本地反馈移动约 `12ms`、枪口约 `15ms`；远程 projectile birth 延迟约 `123ms`。

10K 结论：这是已接受的 VFX-only 可信度票。它不会让 projectile 伪造存活，也不会改变射程或命中，只是在服务端移除 projectile 时给玩家一个短残影和 spark，解释“子弹到这里结束了”。

## 最新验收：BP-28S-10L Camera Offset Lerp 即时化

改动边界：

- 文件：`frontend/src/features/battle/renderer/camera/battleCameraDirector.ts`。
- `CAMERA_OFFSET_LERP` 从 `{ x: 0.16, y: 0.16 }` 改为 `{ x: 1, y: 1 }`。
- 不改 camera follow lerp、deadzone、zoom、bounds、pointer look-ahead ratio/max、`pointerWorld` 读取、input、weapon、projectile、VFX、reconciliation、GameScene、HUD 或后端。

验收：

- worker 运行 `npm run build` 通过。
- 主控复核改动边界：只改 offset lerp，GameScene 未被改动，仍约 514 LOC / 24,711 bytes。
- 主控运行 `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-10l.json` 通过。
- 主控补跑 headful：`scripts/bp28-render-feel-smoke.ps1 -Headful -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-headful-10l.json` 通过。

10L smoke 关键指标：

- Headless：RAF 平均约 `16.68ms`，`>25ms`/`>40ms` 帧均为 `0`；本地反馈移动约 `13ms`、枪口约 `17ms`；无 hard snap，无 soft correction；preDistance 平均约 `1.52px`。
- Headful：RAF 平均约 `16.68ms`，`>25ms`/`>40ms` 帧均为 `0`；本地反馈移动约 `13ms`、枪口约 `17ms`；无 hard snap，6 次 soft correction；preDistance 平均约 `3.52px`，最大约 `8.03px`。

10L 结论：这是已接受的单变量摄像机/准星手感票。它应减少 pointer look-ahead offset 缓动导致的准星/枪口方向慢半拍或漂移；风险是鼠标大幅甩动时视角更直接，如果用户实机觉得跳，需要下一票单独处理 look-ahead ratio/max，而不是回退多个摄像机改动。

## 最新验收：BP-28S-10M Headful 画面复核

执行：

- 运行 `scripts/bp28-render-feel-smoke.ps1 -Headful -KeepBrowsersOpen -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-headful-10m-live.json`。
- 通过 CDP 抓取实际窗口截图：
- `.runtime/bp28-render-feel-smoke/screen-clientA-10m.png`
- `.runtime/bp28-render-feel-smoke/screen-clientB-10m.png`
- 抓图后已清理本轮保留的 Edge 进程。

10M 结论：

- 多人链路仍可运行，smoke 为 `ok=true`，两个客户端进入同一 battle。
- 真实画面仍明显偏调试/玩具感，距离用户给出的“金属竞技场 + 空洞骑士式高辨识角色轮廓”的目标很远。
- 边界/不可走区虽然比之前更可读，但当前 out-of-bounds/边界暗化在画面里会出现大块半透明矩形，可能继续造成“这里到底能不能走”的误解。
- HUD 信息密度和视觉层级仍偏粗糙，尤其右侧技能/武器面板遮挡战场，画风与目标 UI 不统一。
- 当前不能宣布 battle 渲染完成；下一步应先做地图/边界视觉可读性收口，再进入整体美术风格升级。

## 最新验收：BP-28S-10N 地图/边界视觉可读性

改动边界：

- 文件：`frontend/src/features/battle/renderer/arena/arenaBuilder.ts`。
- `createOutOfBoundsShadow` 不再用高透明度大块 padding rectangle 作为主表达，改为低透明远场雾化、局部边缘暗带、护栏线和边缘 tick cue。
- `createBoundaryReadabilityLayer` 的边界暗带和警示线已收细、降低透明度，让墙、边界、地面层级更清楚。
- 未修改 `frontend/src/scenes/GameScene.ts`，GameScene 仍约 `488 LOC / 24,714 bytes`。
- 未改碰撞、static body、`obstacleBounds.push`、world size、常量、minimap、HUD、weapon/projectile/camera/reconciliation 或后端。

验收：

- worker 跑 `npm run build` 通过；主控复跑 `npm run build` 通过，只有既有 Vite/Rollup 警告。
- 主控跑 `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-10n.json` 通过。
- 主控跑 headful：`scripts/bp28-render-feel-smoke.ps1 -Headful -KeepBrowsersOpen -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-headful-10n-live.json` 通过。
- 10N 截图路径：`.runtime/bp28-render-feel-smoke/screen-clientA-10n.png`、`.runtime/bp28-render-feel-smoke/screen-clientB-10n.png`。
- 抓图后已清理本轮保留的 Edge 进程。

10N 指标：

- Headless：`sameBattle=true`；权威输入确认约 `321ms`；本地移动反馈约 `14ms`，枪口反馈约 `17ms`；clientA/clientB RAF 平均约 `16.68ms`，p95 约 `16.8ms`，`>25ms` 帧为 `0`；无 hard snap，5 次 soft correction；warnings 为空。
- Headful：`sameBattle=true`；权威输入确认约 `341ms`；本地移动反馈约 `14ms`，枪口反馈约 `17ms`；clientA/clientB RAF 平均约 `16.68ms`，p95 约 `16.8ms`，`>25ms` 帧为 `0`；无 hard snap，无 soft correction；warnings 为空。

10N 结论：

- 这是已接受的 visual-only 可读性票。它降低了大块 out-of-bounds 暗遮罩造成的“随机不可走区域”误读风险。
- 截图显示小地图边框和主画面边界更明确，但整体画面仍是 Kenney/调试原型质感，不是目标的“金属竞技场 + 空洞骑士式圆润高辨识角色轮廓”。
- 当前仍不能宣布 battle 渲染完成。下一步应继续做视觉层级/角色轮廓/HUD 信息密度，而不是切到外围页面。

## 最新验收：BP-28S-10O HUD/战场遮挡与信息密度

改动边界：

- 文件：`frontend/src/ui/Hud.ts`。
- 右上小地图/排行面板收窄，小地图 CSS 显示从 `140px` 收到 `118px`，排行列表增加限高。
- 右下武器面板收紧宽度、字体、间距和 status chip。
- 技能面板改为更紧凑的 3 列小卡，降低卡片高度、gap 和 padding。
- 战斗日志略缩窄、降低最小高度和透明度；左下生命/体力保持清晰，仅轻微收口尺寸。
- 未修改 `frontend/src/scenes/GameScene.ts`，GameScene 仍约 `488 LOC / 24,714 bytes`。
- 未改 HudState 数据结构、HUD presenter、contracts、minimap 数据、camera、projectile、weapon、skill、碰撞、reconciliation、API 或后端。

验收：

- worker 跑 `npm run build` 通过；主控复跑 `npm run build` 通过，只有既有 Vite/Rollup 警告。
- 主控跑 `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-10o.json` 通过。
- 主控跑 headful：`scripts/bp28-render-feel-smoke.ps1 -Headful -KeepBrowsersOpen -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-headful-10o-live.json` 通过。
- 10O 截图路径：`.runtime/bp28-render-feel-smoke/screen-clientA-10o.png`、`.runtime/bp28-render-feel-smoke/screen-clientB-10o.png`。
- 抓图后已清理本轮保留的 Edge 进程。

10O 指标：

- Headless：`sameBattle=true`；权威输入确认约 `338ms`；本地移动反馈约 `14ms`，枪口反馈约 `16ms`；clientA/clientB RAF 平均约 `16.68ms`，p95 约 `16.8ms`，`>25ms` 帧为 `0`；无 hard snap，无 soft correction；warnings 为空。
- Headful：`sameBattle=true`；权威输入确认约 `353ms`；本地移动反馈约 `13ms`，枪口反馈约 `16ms`；clientA/clientB RAF 平均约 `16.68ms`，p95 约 `16.8ms`，`>25ms` 帧为 `0`；无 hard snap，无 soft correction；warnings 为空。

10O 结论：

- 这是已接受的 DOM HUD visual-only 票。它让右上/右下 HUD 不再大面积压住战场中心，尤其技能/武器区比 10N 截图更紧凑。
- 信息仍为中文，HUD 数据语义未变。
- 当前 HUD 仍不是最终金属风格 UI，只是第一轮可玩性遮挡收口；后续需要统一美术语言、图标、边框和动效。

## 最新验收：BP-28S-10P 角色轮廓与命中体积可读性

改动边界：

- 文件：`frontend/src/features/battle/renderer/entities/worldViewFactory.ts`。
- `HeroView` 增加 `shadow`、`bodyDisc`、`silhouetteRing`、`hitRing` 四个 Phaser Arc 显示对象。
- 新增 `HERO_READABILITY_MIN_RADIUS = 18`，让贴图较小或旋转时仍有稳定可读的圆润轮廓。
- 本地玩家和远程玩家都同步可读性底盘/轮廓，死亡时与 sprite、血条、名字一起隐藏。
- 二次 review 后要求 worker 修复 depth：`marker/shadow/bodyDisc/silhouetteRing/hitRing` 固定为 `32/33/34/35/36`，低于 projectile `trail/glow/sprite` 的 `41/42/43`，不挡子弹、拖尾、枪口反馈。
- 未修改 `frontend/src/scenes/GameScene.ts`，GameScene 仍约 `488 LOC / 24,714 bytes`。
- 未改 hero domain radius、碰撞、命中判定、projectile、weapon、combat、HUD、contracts、API 或后端。

验收：

- worker 跑 `npm run build` 通过；主控复跑 `npm run build` 通过，只有既有 Vite/Rollup 警告。
- 主控先跑 `summary-after-10p.json` 通过，但因 depth 风险未接受。
- depth 修复后，主控跑 `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-10p-repair.json` 通过。
- depth 修复后，主控跑 headful：`scripts/bp28-render-feel-smoke.ps1 -Headful -KeepBrowsersOpen -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-headful-10p-repair-live.json` 通过。
- 10P 修复后截图路径：`.runtime/bp28-render-feel-smoke/screen-clientA-10p-repair.png`、`.runtime/bp28-render-feel-smoke/screen-clientB-10p-repair.png`。
- 抓图后已清理本轮保留的 Edge 进程。

10P 指标：

- Headless 修复后：`sameBattle=true`；权威输入确认约 `345ms`；本地移动反馈约 `14ms`，枪口反馈约 `16ms`；clientA/clientB RAF 平均约 `16.68ms`，p95 约 `16.8ms`，`>25ms` 帧为 `0`；无 hard snap，3 次 soft correction；warnings 为空。
- Headful 修复后：`sameBattle=true`；权威输入确认约 `357ms`；本地移动反馈约 `13ms`，枪口反馈约 `15ms`；clientA/clientB RAF 平均约 `16.68ms`，p95 约 `16.8ms`，`>25ms` 帧为 `0`；无 hard snap，1 次 soft correction；warnings 为空。

10P 结论：

- 这是已接受的 visual-only 可读性票。它让角色更接近“圆润高辨识轮廓”，玩家更容易判断身体边缘、子弹擦身、命中反馈位置。
- depth 修复是必要的：第一版虽然指标通过，但新 ring 对本地玩家可能高于 projectile 层；修复后可读性层低于弹道层，避免用视觉辅助反而遮挡战斗信息。
- 当前角色仍不是最终美术资产，仍偏 Kenney/原型风；后续需要统一金属竞技场地面、角色材质、武器/VFX 与 HUD 风格。

## 最新验收：BP-28S-10Q HUD 金属风格第一轮

改动边界：

- 文件：`frontend/src/ui/Hud.ts`。
- `#hud-root` 增加局部金属 HUD 变量，不引入外部素材、网络字体或新依赖。
- `.hud-panel`、`.hud-timer`、血/体力条、minimap、leaderboard 当前项、weapon 当前项、status chip、skill grid 统一为深色金属渐变、细金/青蓝描边、内阴影和切角感。
- 保留 10O 后的紧凑布局尺寸：右上 minimap 仍为 `118px` CSS 显示，右上面板 `140px`，右下 weapon `196px`，skill `154px`，没有把 HUD 扩大压住战场。
- 未修改 `HudState` interface、HUD 数据更新逻辑、minimap 绘制语义、battle runtime、renderer、GameScene、contracts、API 或后端。

验收：

- worker 跑 `npm run build` 通过；主控复跑 `npm run build` 通过，只有既有 Vite/Rollup 警告。
- 主控跑 `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-10q.json` 通过。
- 主控跑 headful：`scripts/bp28-render-feel-smoke.ps1 -Headful -KeepBrowsersOpen -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-headful-10q-live.json` 通过。
- 10Q 截图路径：`.runtime/bp28-render-feel-smoke/screen-clientA-10q.png`、`.runtime/bp28-render-feel-smoke/screen-clientB-10q.png`。
- 抓图后已清理本轮保留的 Edge 进程。

10Q 指标：

- Headless：`sameBattle=true`；权威输入确认约 `332ms`；本地移动反馈约 `13ms`，枪口反馈约 `15ms`；clientA/clientB RAF 平均约 `16.68ms`，p95 约 `16.8ms`，`>25ms` 帧为 `0`；无 hard snap，无 soft correction；warnings 为空。
- Headful：`sameBattle=true`；权威输入确认约 `365ms`；本地移动反馈约 `12ms`，枪口反馈约 `14ms`；clientA/clientB RAF 平均约 `16.68ms`，p95 约 `16.8ms`，`>25ms` 帧为 `0`；无 hard snap，无 soft correction；warnings 为空。

10Q 结论：

- 这是已接受的 DOM HUD visual-only 票。它让 HUD 从透明黑框进入金属竞技 UI 第一轮，但没有改变 HUD 语义和战场遮挡边界。
- 截图显示风格比 10O 更统一，尤其计时器、右上 minimap/排行、右下技能/武器面板更接近参考图的硬朗金属面板。
- 当前画面最大短板已经转移到地图/地面/障碍物仍偏 Kenney/原型风；下一票应做 10R 地图/地面 metal-arena code-native 第一轮。

## 最新验收：BP-28S-10R 地图/地面金属竞技场第一轮

改动边界：

- 文件：`frontend/src/features/battle/renderer/arena/arenaBuilder.ts`。
- 将主战场从草地/木箱/浅色原型地砖转为暗色金属竞技场：深钢地板、面板缝、铆钉、中心平台、青蓝/金色灯带、工业角落暗部。
- 静态障碍物增加 visual-only 金属皮肤：暗影、细边框、金/青蓝 rim、wall 横向 brace、crate 顶部光条，降低旧橙色 Kenney 原型块的割裂感。
- pickup pad 调整为金属底座语义，树/石头/灌木类装饰改为 pylons、machinery、low deck plates 等偏工业装饰。
- 未修改 `frontend/src/scenes/GameScene.ts`，GameScene 当前仍为 `488 LOC / 24,714 bytes`。
- 未修改 `setDisplaySize(obstacle.size.x, obstacle.size.y)`、`refreshBody()`、`wallBodies.add(staticImage)`、`obstacleBounds.push(...)`、`INNER_OBSTACLES`、world size、spawn、碰撞链、projectile、weapon、hit 判定、HUD 状态、contracts、API 或后端。

验收：

- worker 跑 `npm run build` 通过；主控复跑 `npm run build` 通过，只有既有 Vite/Rollup 警告。
- 主控跑 `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-10r-repair.json` 通过。
- 主控跑 headful：`scripts/bp28-render-feel-smoke.ps1 -Headful -KeepBrowsersOpen -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-headful-10r-repair-live.json` 通过。
- 10R 修复后截图路径：`.runtime/bp28-render-feel-smoke/screen-clientA-10r-repair.png`、`.runtime/bp28-render-feel-smoke/screen-clientB-10r-repair.png`。
- 抓图后已清理本轮保留的 Edge 进程。

10R 指标：

- Headless：`sameBattle=true`；权威输入确认约 `342ms`；本地移动反馈约 `13ms`，枪口反馈约 `15ms`；clientA/clientB RAF 平均约 `16.68ms`，p95 约 `16.8ms`，`>25ms` 帧为 `0`；无 hard snap，4 次 soft correction；warnings 为空。
- Headful：`sameBattle=true`；权威输入确认约 `365ms`；本地移动反馈约 `10ms`，枪口反馈约 `13ms`；clientA/clientB RAF 平均约 `16.68ms`，p95 约 `16.8ms`，`>25ms` 帧为 `0`；无 hard snap，1 次 soft correction；warnings 为空。

10R 结论：

- 这是已接受的 visual-only 地图风格票。它没有让地图达到最终参考图质量，但已经把主战场从原型草地/木箱感推进到“暗色金属竞技场”第一轮。
- 截图显示地图、边界、障碍物和 10Q HUD 的金属 UI 语言已经比 10N/10O 更统一；当前短板转移到武器、弹道、命中、受击与技能 VFX 还不够有冲击力。
- 下一票应做 10S 武器/弹道/命中 VFX 第一轮，继续严格保持状态通道和特效通道分离，不把 VFX 反写到 authoritative state。

## 最新验收：BP-28S-10S 武器/弹道/命中 VFX 第一轮

改动边界：

- 文件：`frontend/src/features/battle/renderer/effects/sceneVfxController.ts`。
- 文件：`frontend/src/features/battle/renderer/effects/sharedAuthoritativeLocalFeedbackSceneBridge.ts`。
- 文件：`frontend/src/features/battle/renderer/effects/weaponActionPlanPresenter.ts`。
- 文件：`frontend/src/features/battle/renderer/effects/weaponActionSceneBridge.ts`。
- 文件：`frontend/src/features/battle/renderer/gameSceneFeedbackBridgeFactory.ts`。
- 文件：`frontend/src/scenes/GameScene.ts`，仅一处 scene-side VFX adapter 透传 `direction`，未改其它 GameScene 逻辑。GameScene 当前为 `489 LOC / 24,744 bytes`，仍低于硬门槛。
- `createMuzzleBurst` 支持可选 `direction`，本地 shared authoritative 开火和 legacy/local weapon action path 都把 aim direction 透传到 VFX，修掉枪口火花默认只向右散的漏口。
- muzzle VFX 增加定向 core glow、短线 flash 和按瞄准方向/垂直方向展开的 sparks；projectile tracer 增加 underglow、core line、glint 和 tip ghost；impact spark/hit confirm 增强短命可读性。
- 未修改 authoritative state、HP、ammo、cooldown、reload、weapon values、projectile speed/range/TTL/radius/damage、碰撞、命中判定、reconciliation、后端、HUD、arena 或 contracts。
- 所有新增效果仍是 Phaser transient 图元/tween，继续走 `MAX_TRANSIENT_VFX = 120` 的上限管理。

验收：

- worker 跑 `npm run build` 通过；主控复跑 `npm run build` 通过，只有既有 Vite/Rollup 警告。
- 主控跑 `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-10s.json` 通过。
- 主控跑 headful：`scripts/bp28-render-feel-smoke.ps1 -Headful -KeepBrowsersOpen -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-headful-10s-live.json` 通过。
- 10S 普通截图路径：`.runtime/bp28-render-feel-smoke/screen-clientA-10s.png`、`.runtime/bp28-render-feel-smoke/screen-clientB-10s.png`。
- 10S 瞬时开火截图路径：`.runtime/bp28-render-feel-smoke/screen-clientA-10s-fire-035ms.png`、`.runtime/bp28-render-feel-smoke/screen-clientA-10s-fire-095ms.png`、`.runtime/bp28-render-feel-smoke/screen-clientA-10s-fire-left-045ms.png`。
- 抓图后已清理本轮保留的 Edge 进程。

10S 指标：

- Headless：`sameBattle=true`；权威输入确认约 `326ms`；本地移动反馈约 `12ms`，枪口反馈约 `14ms`；muzzle feedback delta 为 `7`；clientA/clientB RAF 平均约 `16.68ms`，p95 约 `16.8ms`，`>25ms` 帧为 `0`；无 hard snap，2 次 soft correction；warnings 为空。
- Headful：`sameBattle=true`；权威输入确认约 `343ms`；本地移动反馈约 `12ms`，枪口反馈约 `14ms`；muzzle feedback delta 为 `7`；clientA/clientB RAF 平均约 `16.68ms`，p95 约 `16.8ms`，`>25ms` 帧为 `0`；无 hard snap，5 次 soft correction；warnings 为空。

10S 结论：

- 这是已接受的 VFX-only 战斗可读性票。它增强“开火发生了、子弹往哪里走、命中在哪里发生”的感知，但不改变真实射程、伤害、命中或服务器结算。
- 瞬时截图确认右向和左向开火都能显示与准星方向一致的枪口/弹道残影；“火花只向右散”的 VFX 漏口已经收口。
- 这还不是最终武器/技能美术。下一阶段仍需要技能 VFX、角色材质、HUD 图标和可能的素材/prompt 规划，但不能用素材替代判定、同步和手感。

## 最新验收：BP-30 技能 VFX 与战斗状态提示第一轮

改动边界：

- 文件：`frontend/src/features/battle/renderer/effects/sceneVfxController.ts`。
- 文件：`frontend/src/features/battle/renderer/effects/sharedAuthoritativeLocalFeedbackSceneBridge.ts`。
- 文件：`frontend/src/features/battle/renderer/gameSceneFeedbackBridgeFactory.ts`。
- 新增 Blink / Freeze / Dash 专用 transient VFX：Blink 合法目标 cue、Freeze 冰蓝范围/碎片 cue、Dash 方向性环与拖尾、非法目标红色破裂 X。
- `sharedAuthoritativeLocalFeedbackSceneBridge` 继续使用既有 `isSharedAuthoritativeTargetValid` 判断，只把原普通 pulse 切换成更明确的技能 VFX。
- 未修改 `frontend/src/scenes/GameScene.ts`。GameScene 当前仍为 `489 LOC / 24,744 bytes`。
- 未修改 Dash/Blink/Freeze 距离、冷却、目标合法性、冻结半径/时长、伤害、HP、position、alive、ammo、projectiles、authoritative state、reconciliation、后端、HUD、arena、worldViewFactory 或 contracts。
- 未新增英文 UI 文本；本票没有新增文本，只新增图形反馈。

验收：

- worker 跑 `npm run build` 通过；主控复跑 `npm run build` 通过，只有既有 Vite/Rollup 警告。
- 主控跑 `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp30.json` 通过。
- 主控跑 headful：`scripts/bp28-render-feel-smoke.ps1 -Headful -KeepBrowsersOpen -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-headful-bp30-live.json` 通过。
- 主控额外启动新鲜临时 skill smoke 客户端，确认角色存活后分别触发 E/Q/R 并截图。
- BP-30 新鲜技能截图路径：`.runtime/bp28-render-feel-smoke/screen-clientA-bp30-fresh-dash-045ms.png`、`.runtime/bp28-render-feel-smoke/screen-clientA-bp30-fresh-blink-065ms.png`、`.runtime/bp28-render-feel-smoke/screen-clientA-bp30-fresh-freeze-075ms.png`。
- 抓图后已清理 BP-28/BP-30 临时 Edge 进程。

BP-30 指标：

- Headless：`sameBattle=true`；权威输入确认约 `345ms`；本地移动反馈约 `12ms`，枪口反馈约 `14ms`；muzzle feedback delta 为 `7`；clientA/clientB RAF 平均约 `16.68ms`，p95 约 `16.8ms`，`>25ms` 帧为 `0`；无 hard snap，2 次 soft correction；warnings 为空。
- Headful：`sameBattle=true`；权威输入确认约 `332ms`；本地移动反馈约 `11ms`，枪口反馈约 `15ms`；muzzle feedback delta 为 `7`；clientA/clientB RAF 平均约 `16.68ms`，p95 约 `16.8ms`，`>25ms` 帧为 `0`；无 hard snap，8 次 soft correction；warnings 为空。

BP-30 结论：

- 这是已接受的 VFX-only 技能可读性票。它增强“技能准备/目标/释放反馈”的可见性，但不改变任何技能判定、冷却、位移、冻结或服务端结算。
- 新鲜截图确认 E/Q/R 都有实际画面反馈；之前第一次截图失败是因为 smoke 后角色已经 0 HP，技能反馈按设计不会触发。
- 当前渲染主线仍未完成：技能 VFX 第一轮可读，但角色/武器/技能的最终资产、动画节奏、击中特效和命中补偿仍需继续推进。

## 最新验收：BP-31A Sprint 体感单变量调参

改动边界：

- 文件：`frontend/src/game/constants.ts`。
- 文件：`backend/src/main/scala/battle/runtime/InMemoryAuthoritativeBattleRuntime.scala`。
- 唯一语义改动：玩家 sprint multiplier 从 `1.55` 提高到 `1.75`，前端本地预测常量与后端 authoritative runtime 常量同步。
- 未修改 `BASE_MOVE_SPEED` / `playerMoveSpeedPerSecond`、stamina drain/recover、max stamina、Dash/Blink/Freeze、weapon、projectile、collision、hit、reconciliation、HUD、arena、contracts 或 GameScene。
- GameScene 当前仍为 `489 LOC / 24,744 bytes`。

验收：

- worker 跑 `npm run build` 通过；主控复跑 `npm run build` 通过，只有既有 Vite/Rollup 警告。
- worker 尝试后端 `sbt compile`，但未进入编译阶段，失败原因是 Windows sbt server/named pipe lock：`ServerAlreadyBootingException` / `Could not create lock for \\.\pipe\sbt-load-..._lock, error 5`。按当前策略未强杀进程。
- 主控复核源码确认只改两处 multiplier。

BP-31A 结论：

- 这是已接受的单变量手感票。它应当让 Shift 加速差异更明显，但不改变普通移动速度和体力系统。
- 注意：如果后端当前正在运行旧 JVM，本次后端 multiplier 需要重启后端后才会在 authoritative runtime 生效；前端构建已包含新常量。
- 这不是“移动最终完成”。后续还要实机复核是否需要基础速度、camera、地图尺度、输入采样或 reconciliation 继续调，但下一步仍应保持单变量。

## 最新验收：GS-HARD-GATE-REFRESH GameScene 硬门报告刷新

触发原因：

- 当前根指令 `AGENTS.md` 明确要求先完成 `GameScene.ts` hard-decoupling hard gate，不能只按旧 battle/rendering 主线继续推进。
- 旧硬门报告存在，但落后于当前代码，未列出 `setAuthoritativePreparedSkill`、`applyAuthoritativePreparedSkillOverride`、`isLatestPlayerCommandMovementActive` 等后续新增方法。

本轮处理：

- 派单个 worker 只审计并更新 `docs/GAMESCENE_HARD_GATE_COMPLETION_REPORT.md`，不允许修改业务代码。
- 主控复核当前 `frontend/src/scenes/GameScene.ts`：514 physical lines / 489 non-empty LOC / 24,744 bytes。
- 主控复跑 `npm run build` 通过，只有既有 Vite/Rollup 警告。
- 主控复核 `GameScene.ts` 中的 respawn/pickup/projectile/weapon 只剩桥对象和注入点，没有直接 runtime 链回流。

结论：

- `GameScene.ts` 当前满足 hard gate：低于 25KB、低于 700 LOC，也低于 550 LOC stretch。
- 当前剩余方法可归类为 Phaser lifecycle、top-level orchestration、camera/physics/HUD/VFX glue、shared-authoritative renderer-host adapter 或最小输入状态 predicate。
- `docs/GAMESCENE_HARD_GATE_COMPLETION_REPORT.md` 已刷新并记录主控 build 验证。
- GameScene 硬解耦阶段可以视为当前代码终态通过；接下来应回到 battle 主线，不再把 runtime 逻辑塞回 scene。

## 最新验收：BP-31B 一命模式 authoritative 收口复核

改动边界：

- 本票是审计型票据，未修改业务代码。
- 复核目标是确认“一命模式”不是只写在文档里，而是在 authoritative runtime、前端 shared renderer、结果闭环中真实成立。

主控复核结论：

- 后端 authoritative eliminate 链会把死亡玩家置为 `alive=false`、`hp=0`、`respawnMs=0`，并清理移动/武器运行态。
- 后端 finish 条件是 `elapsedMs >= durationMs || players.count(_.alive) <= 1`，因此 alive 玩家数降到 `<= 1` 会结束 battle。
- finish projector 在 `phase == "finished"` 后仍走 result 和 replay 投影，`resultReady/replayReady` 轮询闭环不因一命模式被切断。
- 前端 `GameScene` 在 shared authoritative runtime 下不调用 `localBattleFrameBridge.update()`，所以不会触发本地 `RespawnSceneBridge.updateRespawnTimers()` 把死亡玩家拉起。
- 前端保留的 `respawning` 显示分支属于通用/legacy 能力；当前 authoritative 后端不会给死亡玩家发 `respawnMs > 0`，因此 shared battle 中死亡态应显示为 dead 而不是等待复活。

验收：

- worker 跑 `npm run build` 通过；主控复跑 `npm run build` 通过，只有既有 Vite/Rollup 警告。
- worker 尝试 `npm run backend:compile`，但未进入 Scala 编译阶段，失败原因仍是 Windows sbt named pipe lock：`Could not create lock for \\.\pipe\sbt-load-3179060235032599056_lock, error 5`。按当前策略未强杀用户后端。

BP-31B 结论：

- authoritative 一命模式按当前代码成立，不需要再改 respawn/finish/result 链。
- 剩余风险是后端 compile 受 sbt lock 阻塞，未能在本轮完成编译验证；但本票没有改后端代码，风险可接受。
- 下一项应进入 BP-29 reconciliation，不要再围绕“一命模式”重复修改。

## 最新验收：BP-29A-01 Sprint Stamina 本地 Replay 预测

改动边界：

- 文件：`frontend/src/features/battle/renderer/authoritativeLocalHeroReplay.ts`。
- 文件：`frontend/src/features/battle/renderer/authoritativeFrameSnapshotApplier.ts`。
- 未修改 `frontend/src/scenes/GameScene.ts`，GameScene 仍为 514 physical lines / 489 non-empty LOC / 24,744 bytes。
- 未修改后端、API/contract、移动速度、sprint multiplier、stamina drain/recover 常量、camera、VFX、HUD 样式。
- 未预测 weapon switch、reload、ammo、weapon cooldown、skill cast/cooldown；这些仍属于 BP-29 后续票。

本轮处理：

- `resolveAuthoritativeLocalHeroReplayTarget` 返回值从单一 `Vec2` 扩展为 replay projection：`position + stamina + hasPredictedStamina`。
- authoritative frame applier 继续使用 replay 后的 `position` 作为 local correction target，保持原有 position correction 语义。
- 只有存在未确认的 `sprint && movement` command replay 时，才把 predicted stamina 写回本地 hero。
- 无 command history、无 unacked command、invalid input、hard snap/dead、无 sprint 输入时，`hasPredictedStamina=false`，本地 stamina 保持权威帧值。

验收：

- worker 跑 `npm run build` 通过；主控复跑 `npm run build` 通过，只有既有 Vite/Rollup 警告。
- 主控跑 `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp29a-01.json` 通过。

BP-29A-01 smoke 指标：

- `sameBattle=true`，两个客户端进入同一 battle。
- 权威输入确认约 `341ms`；移动和开火均确认。
- 本地反馈：移动约 `12ms`，枪口约 `16ms`，muzzle delta 为 `7`。
- RAF：clientA 平均约 `16.68ms`、p95 约 `16.8ms`、`>25ms` 为 `0`；clientB 平均约 `16.68ms`、p95 约 `16.8ms`、`>25ms` 为 `0`。
- 本地 correction：无 hard snap，4 次 soft correction，125 次 deadzone ignored。
- warnings 为空。

BP-29A-01 结论：

- 这是已接受的 reconciliation 小票。它补齐了 sprint 期间本地 HUD/下一帧 gating 仍等待权威 stamina 的一处粘滞点。
- 这不是完整 reconciliation 完成；weapon/reload/ammo/cooldown/skill 仍是“发命令 + 等权威帧”，后续必须继续拆小票推进。

## 最新验收：BP-29A-02 Authoritative Weapon Index Reconciliation

改动边界：

- 文件：`frontend/src/features/battle/renderer/authoritativeFrameSnapshotApplier.ts`。
- 未修改 `frontend/src/scenes/GameScene.ts`。
- 未修改后端、API/contract、weapon values、ammo/reload/cooldown 语义、runtime-local weapon mutation 链。
- 未实现服务器多武器切换；本票只修前端 applier 尊重 authoritative frame 的 `currentWeaponIndex`。

本轮处理：

- 移除 applier 中 `hero.currentWeaponIndex = 0` 的硬写死逻辑。
- 在 `syncAuthoritativeWeaponStates(...)` 之后，用 `authoritativeHero.currentWeaponIndex` 按同步后的 `hero.weapons.length` clamp，再写入 `hero.currentWeaponIndex`。
- 无武器或非有限 index 时安全回落到 `0`。
- `ammoInMagazine`、`reserveAmmo`、`cooldownRemaining`、`reloadRemaining` 仍完全来自 authoritative weapon frame。

验收：

- worker 跑 `npm run build` 通过；主控复跑 `npm run build` 通过，只有既有 Vite/Rollup 警告。
- 主控跑 `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp29a-02.json` 通过。

BP-29A-02 smoke 指标：

- `sameBattle=true`，两个客户端进入同一 battle。
- 权威输入确认约 `328ms`；移动和开火均确认。
- 本地反馈：移动约 `11ms`，枪口约 `14ms`，muzzle delta 为 `7`。
- RAF：clientA 平均约 `16.68ms`、p95 约 `16.8ms`、`>25ms` 为 `0`；clientB 平均约 `16.68ms`、p95 约 `16.8ms`、`>25ms` 为 `0`。
- 本地 correction：无 hard snap，无 soft correction，132 次 deadzone ignored。
- warnings 为空。

BP-29A-02 结论：

- 这是已接受的 reconciliation 小票。它修掉了前端 applier 对 authoritative weapon index 的硬编码，避免未来多武器 authoritative state 出现时 HUD/反馈继续显示错误武器。
- 它不是 ammo/reload/cooldown prediction；这些仍必须继续以更小的边界拆票推进，不能把 runtime-local weapon mutation 链接进 shared authoritative 主路径。

## 最新验收：BP-29A-03 Reload Intent Feedback

改动边界：

- 文件：`frontend/src/features/battle/renderer/effects/sharedAuthoritativeLocalFeedbackSceneBridge.ts`。
- 文件：`frontend/src/features/battle/renderer/gameSceneFeedbackBridgeFactory.ts`。
- 未修改 `frontend/src/scenes/GameScene.ts`。
- 未修改后端、HUD weapon state、hudPresenter、authoritative applier 的 ammo/reload/cooldown 映射。
- 未提前扣 ammo、未提前设置 reload/cooldown、未生成 projectile、未接 runtime-local weapon mutation 链。

本轮处理：

- shared authoritative local feedback bridge 新增 reload intent 只读判定和 `520ms` 节流。
- 仅当玩家存活、`command.reloadPressed`、当前武器存在、非 `Gatling`、`reloadRemaining <= 0`、弹匣未满、`reserveAmmo > 0` 时显示非 HUD 浮字。
- 文案为中文“换弹请求”，只表示本地 reload 输入被捕获/已发起请求，不表示服务器确认或正在换弹。
- factory 只注入已有 `vfx.showFloatingText`。

验收：

- worker 跑 `npm run build` 通过；主控复跑 `npm run build` 通过，只有既有 Vite/Rollup 警告。
- 主控跑 `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp29a-03.json` 通过。
- 主控复核源码确认没有新增“服务器已收到”或“正在换弹”这类误导性文案；HUD 的“正在换弹/冷却”仍只来自 authoritative weapon state。

BP-29A-03 smoke 指标：

- `sameBattle=true`，两个客户端进入同一 battle。
- 权威输入确认约 `341ms`；移动和开火均确认。
- 本地反馈：移动约 `12ms`，枪口约 `15ms`，muzzle delta 为 `7`。
- RAF：clientA 平均约 `16.68ms`、p95 约 `16.8ms`、`>25ms` 为 `0`；clientB 平均约 `16.68ms`、p95 约 `16.8ms`、`>25ms` 为 `0`。
- 本地 correction：无 hard snap，1 次 soft correction，129 次 deadzone ignored。
- warnings 为空。

BP-29A-03 结论：

- 这是已接受的 feedback-only 小票。它给 reload 输入增加即时“请求”反馈，但不污染真实 weapon state。
- 自动化 smoke 没有专门按 `T` 触发 reload，所以“换弹请求”可见性仍需 headful/人工复核；主链未被破坏已经通过自动化验证。

## 最新验收：BP-29A-04 Reload Intent Headful 可见性复核

改动边界：

- 本票是验证票，未修改代码。
- 目标是复核 BP-29A-03 的“换弹请求”浮字真的可见，并确认 HUD 不提前显示假的 reload 状态。

执行：

- 运行 headful smoke 并保留浏览器：`scripts/bp28-render-feel-smoke.ps1 -Headful -KeepBrowsersOpen -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-headful-bp29a-04-live.json`。
- 通过 CDP 对 clientA 触发 `T` reload，并截图。
- 截图路径：`.runtime/bp28-render-feel-smoke/screen-clientA-bp29a-04-reload-intent.png`。
- 验证后清理本轮 Edge 进程；主控复核残留匹配进程为 `0`。

复核结果：

- 截图中 clientA 角色上方可见中文“换弹请求”。
- reload 前置条件成立：summary 记录 clientA 开火后 ammo 从 `12` 到 `11`；实际按 `T` 前 HUD 采样为 `4 / 48`，仍满足可 reload。
- 按 `T` 后约 `80ms` 的 DOM 采样仍为 `4 / 48 | 冷却 0.0 秒`，没有提前显示假的“换弹中”。
- 稍后 HUD 出现“换弹中 / 换弹 0.9秒、0.5秒”，这是后端 authoritative reload 确认后的真实状态，允许出现。

BP-29A-04 headful 指标：

- `sameBattle=true`，headful 双客户端进入同一 battle。
- 权威输入确认约 `334ms`。
- 本地反馈：移动约 `12ms`，枪口约 `15ms`。
- clientA RAF 平均约 `16.68ms`，`>25ms` 为 `0`。
- 本地 correction：无 hard snap，1 次 soft correction。
- warnings 为空。

BP-29A-04 结论：

- BP-29A-03 的 reload intent feedback 通过可见性复核，并且没有发现 HUD 误导。
- 剩余限制：Phaser floating text 未能通过程序化枚举断言，当前证据主要来自截图和 CDP DOM 时序采样；可接受。

## 最新验收：BP-29B-01 Unready Prepared Skill Toggle 抑制

改动边界：

- 文件：`frontend/src/features/battle/renderer/gameSceneInputBridge.ts`。
- 未修改 `frontend/src/scenes/GameScene.ts`。
- 未修改后端、HUD、技能数值、cooldown/active 状态、position、slowFields。
- 未做全量 skill prediction；不本地预测 Dash/Blink 位移，不本地创建 Freeze slowField。

本轮处理：

- shared authoritative 输入读路径中，先用当前权威 `player.skills` 判断 Blink/Freeze 是否 ready。
- ready 口径：对应 skill 存在且 `cooldownMs <= 0 && activeMs <= 0`。
- 不 ready 时清掉 `command.toggleBlink/toggleFreeze`，再调用既有 `suppressInvalidAuthoritativePreparedConfirm`。
- 这样无效 toggle 不会进入 `installPlayerCommandTap()`，也不会让 `useBattlePageRuntime` 打开本地 prepared override。

验收：

- worker 跑 `npm run build` 通过；主控复跑 `npm run build` 通过，只有既有 Vite/Rollup 警告。
- 主控跑 `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp29b-01.json` 通过。
- 主控源码复核确认输入桥没有写 `cooldownMs`、`activeMs`、`position` 或 `slowFields`。

BP-29B-01 smoke 指标：

- `sameBattle=true`，两个客户端进入同一 battle。
- 权威输入确认约 `331ms`；移动和开火均确认。
- 本地反馈：移动约 `13ms`，枪口约 `16ms`，muzzle delta 为 `7`。
- RAF：clientA 平均约 `16.68ms`、p95 约 `16.8ms`、`>25ms` 为 `0`；clientB 平均约 `16.68ms`、p95 约 `16.8ms`、`>25ms` 为 `0`。
- 本地 correction：无 hard snap，1 次 soft correction，131 次 deadzone ignored。
- warnings 为空。

BP-29B-01 结论：

- 这是已接受的 skill reconciliation 小票。它修掉了 cooldown/active 中 Blink/Freeze 仍可进入本地 prepared 的状态撒谎问题。
- 自动化 smoke 没有专门按技能键验证 cooldown 中 toggle 被抑制；后续可做 targeted headful 验证，但主链未破坏且源码边界清晰。

## 最新验收：BP-29B-02 Blink Cooldown Toggle Targeted 验证

改动边界：

- 本票是验证票，未修改代码。
- 目标是验证 BP-29B-01 在技能 cooldown 中是否真的阻止本地 prepared 状态打开。

执行：

- 运行 headful smoke 并保留浏览器：`scripts/bp28-render-feel-smoke.ps1 -Headful -KeepBrowsersOpen -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-headful-bp29b-02-live.json`。
- 进入 shared authoritative 双客户端 battle 后，通过 authoritative command 让 clientA Blink 进入 cooldown。
- 在 cooldown 期间向 clientA 发送 `Q`。
- 截图路径：
- `.runtime/bp28-render-feel-smoke/bp29b-02-clientA-cooldown-before-q.png`
- `.runtime/bp28-render-feel-smoke/bp29b-02-clientA-after-q.png`
- 验证后清理本轮 Edge 进程；主控复核残留匹配进程为 `0`。

复核结果：

- summary 显示 `sameBattle=true`，clientA runtime active session 为 shared。
- cooldown 期间按 `Q` 后采样为 `preparedSkill: null`。
- HUD prepared 列表为空：`hudPrepared: []`。
- command tap 中多条 `/battle/commands` 的 `castBlink=false`、`castFreeze=false`。
- 因此可以确认 Blink cooldown 期间按 `Q` 没有打开本地 prepared 状态，也没有上行 Blink cast。

BP-29B-02 headful 指标：

- 权威输入确认约 `326ms`。
- 本地反馈：移动约 `12ms`，枪口约 `14ms`。
- clientA RAF 平均约 `16.68ms`，`>25ms` 为 `0`。
- 本地 correction：无 hard snap，4 次 soft correction。
- warnings 为空。

BP-29B-02 结论：

- BP-29B-01 的 Blink 分支通过 targeted headful 验证。
- Freeze 分支尚未完成 targeted 验证；由于 Blink/Freeze 走同一过滤函数和同一 ready 口径，代码层风险低，但不能把本轮验证说成双分支全覆盖。
- validation JSON 未成功写出，原因是自动化最后构造报告时 PowerShell 正则解析中文字符报错；关键 runtime/HUD/command tap 采样已在终端输出中得到，截图和 summary 均存在。

## 最新验收：BP-29B-03 Freeze Toggle 静态等价证明

改动边界：

- 本票是审计/证明票，未修改代码。
- 目标是判断是否必须再跑 Freeze headful targeted 验证。

证明链：

- `gameSceneInputBridge.ts` 在 shared authoritative runtime 下，先调用 `suppressUnreadyAuthoritativePreparedToggle(command, player)`，再调用既有非法目标确认抑制。
- 该函数对 Blink 和 Freeze 同构处理：`toggleBlink = command.toggleBlink && isAuthoritativeSkillReady(player, "Blink")`，`toggleFreeze = command.toggleFreeze && isAuthoritativeSkillReady(player, "Freeze")`。
- ready 口径相同：对应 skill 存在且 `cooldownMs <= 0 && activeMs <= 0`。
- `skillBindingInputAdapter.ts` 与 `phaserPlayerCommandReader.ts` 确认 Freeze 按键来源确实进入 `command.toggleFreeze`。
- `createBattleRuntime.ts` 的 command tap 包装的是 scene `readPlayerCommand`，而 scene 读取已经经过 `readGameScenePlayerCommand` 过滤；因此 unready Freeze press 不会被缓存成 pending toggle。
- `useBattlePageRuntime.ts` 打开 Freeze prepared 的入口只依赖 `runtimeCommand.toggleFreeze`，没有其它绕过入口。

BP-29B-03 结论：

- Freeze 分支与 Blink 分支在 BP-29B-01 的 unready prepared toggle suppression 上静态等价。
- 非 ready Freeze toggle 会在 shared authoritative input bridge 中被清零，不会进入 command tap，也不会打开 prepared override。
- 不需要额外跑 Freeze headful；Blink targeted 验证 + Freeze 静态等价证明足以接受 BP-29B-01。

## 最新审计：BP-29C-01 技能成功/失败反馈边界

改动边界：

- 本票是审计/设计决策票，未修改业务代码。
- 审计目标是确认技能反馈是否存在“看起来成功但实际未成功”的撒谎风险，并决定下一步是否值得实现本地失败提示。

当前状态：

- Dash 本地会在 `castDash` 且本地 cooldown 看起来 ready 时播放即时反馈；真正位移仍等待后端 authoritative frame。
- Blink/Freeze 的 prepared、target、release VFX 会在本地预检通过后立即反馈；真正 Blink 位移与 Freeze slowField 仍等待后端 authoritative state。
- 后端 `cast*IfReady` 对失败/拒绝多数是 no-op，没有返回明确 rejection reason。
- `sendAuthoritativeBattleCommand` 当前只给出 command 是否被 HTTP 接收/ack 的结果，没有“技能被服务器拒绝”的语义事件。

决策：

- 暂不实现“通过状态差异推断技能未确认”的提示。原因是 Dash 被障碍挡住、Blink 目标距离很近、Freeze 重复释放、网络帧延迟等情况都可能被误判成失败，反而破坏可信度。
- 可接受的后续方案有两种：一是后端增加明确 rejection/skill event，再做准确反馈；二是先做更窄的“技能请求未送达/网络失败”提示，只在 command 没被发送或没被接收时显示。
- 本阶段继续坚持：技能成功状态不本地伪造，位置、slowField、cooldown、activeMs 不靠前端猜测写入。

BP-29C-01 结论：

- 技能反馈边界当前可以接受，但不是最终工业级。
- 不应为了“更有反馈”而做高误判的本地成功/失败推断。
- 下一步优先回到用户实机最敏感的移动/渲染体感，而不是扩大技能预测范围。

## 最新验收：BP-31C-01/02 移动体感第二单变量审计与后端重启验证

改动边界：

- 本票未修改业务代码。
- 目标是决定 BP-31A 后是否需要继续调基础速度、sprint、camera、输入采样或 reconciliation。

审计结论：

- 前端 `BASE_MOVE_SPEED = 255`、`SPRINT_MULTIPLIER = 1.75`。
- 后端 `playerMoveSpeedPerSecond = 255.0`、`playerSprintMultiplier = 1.75`。
- camera 当前已是强跟随：follow lerp `1/1`、deadzone `0`、offset lerp `1`、zoom `1.32`，不应优先再调 camera。
- 输入上行约 `33ms`，本地显示移动每帧使用最新 command，不应优先调 input sampling。
- reconciliation 的 moving correction 可能影响“粘”，但在确认后端是否加载新 sprint 常量前，不应调 smoothing 去掩盖权威不一致。

发现的问题：

- 运行中的旧后端 Java PID `17872` 启动于 `2026-04-27 02:47:47`。
- authoritative runtime 文件写入时间为 `2026-04-27 05:23:47`，也就是旧后端早于 BP-31A 后端常量改动。
- 因此前端可能已经用 `1.75` 做本地预测，但旧后端仍用旧 sprint multiplier，实机表现会是持续被权威帧拉回，主观上就是慢、粘、Shift 不明显。

本轮处理：

- 限定范围停止旧 8080 后端链：Java PID `17872`、cmd PID `32160`、PowerShell PID `31244`。
- 重新启动后端：cmd PID `21624` -> Java/sbt PID `31388`。
- 启动日志：`.runtime/backend/backend-restart-20260427-063430.log`。
- `/health` 已恢复 `status=ok`。

验证：

- `npm run build` 通过，只有既有 Vite/Rollup warning。
- `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp31c-01-backend-restart.json` 通过。

BP-31C smoke 指标：

- `sameBattle=true`。
- 权威输入确认约 `323ms`。
- 本地反馈：移动约 `13ms`，枪口约 `16ms`，muzzle delta `7`，motion delta `106`。
- clientA RAF 平均约 `16.68ms`，p95 约 `16.8ms`，`>25ms` 为 `0`；clientB 同级别稳定。
- correction：hard snap `0`，soft correction `1`，deadzone ignored `129`。
- warnings 为空。

BP-31C 结论：

- 当前不接受新的移动速度/camera/reconciliation 变量改动。
- 先让重启后的 authoritative sprint `1.75` 进入实机体验，再由用户体感判断是否仍慢。
- 如果后端确认新代码后仍慢，下一张单变量候选应是基础速度 `BASE_MOVE_SPEED` / `playerMoveSpeedPerSecond`，而不是继续拉 sprint 或改 camera。

## 最新验收：BP-28S-11 视觉素材 / Prompt 队列

改动边界：

- 新增 `docs/BATTLE_VISUAL_PROMPT_QUEUE.md`。
- 本票只做视觉方向与资产生成队列，不修改业务代码，不声明任何资产已经交付。

已记录的视觉北极星：

- 金属科幻竞技场 + 类 Hollow Knight 的圆润高可读角色剪影。
- 战斗视角保持俯视 top-down shooter。
- 外部 UI 语言为中文；生成资产本身原则上不内嵌文字、数字、Logo 或伪文字。

队列内容：

- P0：竞技场地板 tiles、墙体/掩体/箱体、英雄剪影/body variants、projectile/tracer/muzzle/hit VFX、Dash/Blink/Freeze VFX。
- P1：武器 overlays、HUD panels/icons/minimap ornaments。
- P2：lobby/key art。
- 每类都包含 prompt、negative prompt、技术规格、接入备注和“不得影响 hitbox/状态通道/同步”的约束。

BP-28S-11 结论：

- 素材路线已经有可执行 prompt 队列，但素材不是当前主阻塞。
- 下一步优先做不依赖外部素材的 BP-28S-12 表现层提升，让当前 BattlePage 先在代码原生图形上更清楚、更硬朗、更可信。

## 最新验收：BP-28S-12 角色 / 武器朝向可读性

改动边界：

- 文件：`frontend/src/features/battle/renderer/entities/worldViewFactory.ts`。
- 本票只改 renderer 表现层；未修改 `GameScene.ts`、后端、常量、移动、碰撞、命中、武器数值、技能、同步或 authoritative state。

本轮处理：

- `HeroView` 新增持久 `weaponCue` 矩形，每个 hero 一个对象，不做逐帧临时分配。
- `weaponCue` 使用当前 `displayState.facing` 旋转，跟随同一个 display position，因此本地玩家走 local display override，远程 authoritative hero 走插值/平滑后的显示状态。
- 死亡时与 shadow/body/ring/sprite 一起隐藏。
- 本地玩家的 cue 略长、alpha 略高；远程玩家更轻，避免噪声。
- cue 深度低于 hero sprite 和 projectile，不遮挡血条、名字、弹道，也不暗示更大的 hitbox。

验证：

- `npm run build` 通过，只有既有 Vite/Rollup warning。
- `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp28s-12-weapon-cue.json` 通过。

BP-28S-12 smoke 指标：

- `sameBattle=true`。
- 权威输入确认约 `358ms`。
- 本地反馈：移动约 `4ms`，枪口约 `22ms`，muzzle delta `7`，motion delta `107`。
- clientA/clientB RAF 平均约 `16.68ms`，p95 约 `16.8ms`，`>25ms` 为 `0`。
- correction：hard snap `0`，soft correction `4`，deadzone ignored `128`。
- warnings 为空。

BP-28S-12 结论：

- 这是已接受的 code-native 表现层小票。
- 它提升角色朝向和武器姿态可读性，但不代表最终角色素材完成，也不代表 hit-feel 或判定闭环完成。

## 最新验收：BP-28S-13 地板 / 掩体金属层级第二轮

改动边界：

- 文件：`frontend/src/features/battle/renderer/arena/arenaBuilder.ts`。
- 本票只改 buildArena 阶段创建的静态视觉 primitives；未修改 `GameScene.ts`、后端、常量、`INNER_OBSTACLES`、spawn、wallBodies 语义、obstacleBounds 语义、小地图或 gameplay。

本轮处理：

- 在 metal floor 上新增克制的中央面板层级与金/青能量 seam，增强金属竞技场方向。
- 在非边界静态掩体周围新增 footprint shadow、细描边和 bevel 高光，帮助玩家更快识别掩体轮廓。
- 边界/out-of-bounds 逻辑没有新增混淆性遮罩；下边界可读性不回退。
- `createStaticObstacle` 仍用原始 `obstacle.size` 设置 physics image display size，仍在原位置调用 `refreshBody()`、`wallBodies.add(...)` 和 `obstacleBounds.push(...)`。

验证：

- `npm run build` 通过，只有既有 Vite/Rollup warning。
- `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp28s-13-arena-metal.json` 通过。

BP-28S-13 smoke 指标：

- `sameBattle=true`。
- 权威输入确认约 `364ms`。
- 本地反馈：移动约 `20ms`，枪口约 `23ms`，muzzle delta `7`，motion delta `107`。
- clientA/clientB RAF 平均约 `16.68ms`，p95 约 `16.8ms`，`>25ms` 为 `0`。
- correction：hard snap `0`，soft correction `6`，deadzone ignored `124`。
- warnings 为空。

BP-28S-13 结论：

- 这是已接受的 code-native arena 表现层小票。
- 它增强地板/掩体金属层级和 cover 可读性，但不代表最终地图美术完成，也不改变任何碰撞或小地图语义。

## 最新验收：BP-28S-15 视觉 Headful 复核

改动边界：

- 本票是截图复核票，未修改代码。
- 目标是检查 BP-28S-12/13 是否导致画面过花、弹道遮挡、边界误读或掩体误读。

执行：

- 运行 headful 双客户端 smoke 并保留窗口：`scripts/bp28-render-feel-smoke.ps1 -Headful -KeepBrowsersOpen -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-headful-bp28s-15-visual-review.json`。
- 通过 CDP 截图：
- `.runtime/bp28-render-feel-smoke/screen-clientA-bp28s-15-visual-review.png`
- `.runtime/bp28-render-feel-smoke/screen-clientB-bp28s-15-visual-review.png`
- 复核后清理本批 BP28 Edge 进程，剩余匹配进程为 `0`。

复核结果：

- clientB 存活视角中，weapon cue、角色环、血条、右侧 HUD 可读，没有看到新增 cue 遮挡血条/名字/弹道。
- arena 中央金属层级与掩体 footprint 能增强方向感，但没有把非通行边界伪装成可走区。
- clientA 截图处于死亡视角，主要用于复核全局地板/边界层级；死亡隐藏逻辑正常，未见残留角色 cue。

BP-28S-15 headful 指标：

- `sameBattle=true`。
- 权威输入确认约 `346ms`。
- 本地反馈：移动约 `12ms`，枪口约 `14ms`。
- clientA RAF 平均约 `16.68ms`，p95 约 `16.8ms`，`>25ms` 为 `0`。
- correction：hard snap `0`，soft correction `3`。
- warnings 为空。

BP-28S-15 结论：

- BP-28S-12/13 当前不需要回收。
- 视觉仍未完成，但可以继续向受击/低血量/死亡短反馈推进。

## 最新验收：BP-28S-14 低血量世界血条可读性

改动边界：

- 文件：`frontend/src/features/battle/renderer/entities/worldViewFactory.ts`。
- 本票只改 world-space HP bar 的颜色/背景 alpha；未修改 `GameScene.ts`、后端、authoritative applier、HP、alive、damage、score、result、projectile、hitbox 或碰撞。

审计结论：

- 当前命中反馈已有：authoritative HP delta 会触发 flash、impact spark、hit confirm、伤害浮字；死亡会触发“出局”、红色 pulse，本地玩家死亡还有轻微 shake。
- 当前缺口不是“再造命中事件”，而是 alive hero 的世界血条在低血量时缺少状态可读性。

本轮处理：

- alive hero 每帧根据当前 `hero.hp / hero.maxHp` 计算 clamped HP ratio。
- 血量 `>55%`：血条保持 hero tint。
- 血量 `<=55%`：血条变为 amber warning。
- 血量 `<=30%`：血条变为 red danger。
- `<=30%` 时只让 health background 做非常轻的 alpha pulse，并显式 clamp 到 `[0.95, 1]`。
- 死亡隐藏路径保持不变，不显示低血量效果。

验证：

- `npm run build` 通过，只有既有 Vite/Rollup warning。
- `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp28s-14-lowhp-bars.json` 通过。

BP-28S-14 smoke 指标：

- `sameBattle=true`。
- 权威输入确认约 `331ms`。
- 本地反馈：移动约 `13ms`，枪口约 `15ms`，muzzle delta `7`，motion delta `104`。
- clientA/clientB RAF 平均约 `16.68ms`，p95 约 `16.8ms`，`>25ms` 为 `0`。
- correction：hard snap `0`，soft correction `0`，deadzone ignored `131`。
- warnings 为空。

BP-28S-14 结论：

- 这是已接受的状态可读性小票。
- 它只让当前 HP 状态更清楚，不推断事件、不修改伤害、不改变死亡/结算语义。

## 最新验收：BP-28S-16 低血量 / 命中反馈 Headful 复核

改动边界：

- 本票未修改业务代码。
- 本票只做 targeted headful visual review：用独立 Edge 窗口和 CDP 在前端 dev server 中动态 import 现有 battle runtime，启动隔离 renderer 沙箱。
- 该路径验证真实前端 renderer / HUD / feedback bridge，但不声明后端权威语义、匹配房间或真实对局结果。
- 未修改 `GameScene.ts`、后端、命中判定、HP 语义、死亡语义、HUD state schema 或任何 asset。

执行：

- 子 agent 只读排查 diagnostics/debug/CDP 入口，结论是 `window.__slayDemoBattleDiagnostics` 目前是只读观测面，不能安全制造低血量；推荐使用隔离 renderer 沙箱。
- 主控通过 Edge remote debugging port `61516` 启动独立页面 `http://127.0.0.1:5173/?diagnostics=1`。
- 在沙箱中先应用满血 authoritative frame，再把本地 hero 从 `100/100` 降到 `25/100`。
- 输出 summary：`.runtime/bp28-render-feel-smoke/summary-bp28s16-lowhp-headful-v2.json`。
- 输出截图：`.runtime/bp28-render-feel-smoke/screen-bp28s16-lowhp-hit-150ms.png`、`.runtime/bp28-render-feel-smoke/screen-bp28s16-lowhp-hit-350ms.png`、`.runtime/bp28-render-feel-smoke/screen-bp28s16-lowhp-settled-v2.png`。
- 复核后已清理本次 `bp28s16-edge-profile` Edge 进程，剩余匹配进程为 `0`。

复核结果：

- `applyAuthoritativeState` 返回 `true`，HP 状态成功从 `100/100` 降为 `25/100`，hero 仍为 alive。
- 150ms 截图中可见 `-75` 浮字，说明 authoritative HP delta 的短命中反馈仍然触发。
- 350ms / settled 截图中短 VFX 退出，只保留低血量红色世界血条和左下 HUD 生命危险状态。
- 低血量世界血条没有扩大角色 hitbox 暗示，也没有遮挡弹道、名字、武器 cue 或右下技能/武器 HUD。
- 死亡隐藏路径未被本票改动；本轮仅验证 alive low-HP 状态。

BP-28S-16 结论：

- BP-28S-14 的低血量世界血条可接受，不需要回收。
- 当前状态通道与 VFX 通道边界仍成立：HP/血条是状态层，`-75` 浮字是短命中反馈层。
- 不应继续在低血量血条上加更大光圈、危险区域或常驻红雾；那会误导 hitbox 和可通行区域。
- 下一步回到用户更敏感的命中可信度：检查“看着打中但未命中”和 projectile terminal VFX 是否还会造成误判。

## 最新验收：BP-28S-17 弹道 / 命中可信度第二轮审计

改动边界：

- 本票是只读审计，未修改代码。
- 审计范围集中在 projectile display timeline、terminal VFX、authoritative HP delta feedback、后端 swept hit 判定事实。

审计事实：

- Authoritative frame 会把服务器 projectile 覆盖进 snapshot；删除的 projectile 直接从 `snapshot.projectiles` 消失，当前没有“命中点 / 墙碰撞点 / TTL 消失原因”事件。
- shared authoritative 模式下 projectile 显示使用 `83ms` 插值延迟；样本不足时用 `55ms` 平滑追服务器点，`260px` 以上才 snap。
- BP-28S-10K 之前加入的 terminal VFX 使用上一帧 display position 做终点，不是服务器真实最后位置。
- 命中反馈来自 authoritative HP delta，而不是 projectile 视觉碰撞；这是正确边界。
- 后端命中判定是 segment-circle sweep：上一服务器位置到下一服务器位置，与目标圆相交；有效半径为 projectile 半径 + hero 半径 + 小幅 shooter advantage，且命中必须发生在墙 / 出界 block 之前。
- 后端 pistol 理论射程来自 `920px/s * 900ms`，约 `828px`；在 slow field 中速度减半但 TTL 仍减少，所以实际距离会缩短。

BP-28S-17 结论：

- 当前最大嫌疑不是后端漏判，而是 visual/display timeline 误导：玩家看到的 remote hero / projectile 是 display timeline，不是 server hit timeline。
- terminal spark/tracer 若落在上一帧 display position，会让 TTL、墙、出界或 block 前消失看起来像“身体旁边莫名消失”。
- projectile trail/glow/readability 会扩大视觉轮廓，可能让玩家视觉判断比实际 center/path 更宽。
- 角色 hit ring 半径与后端 hero radius 对齐，当前不是主要不一致来源。
- 不接受先改后端 `projectileLifetimeMs`、`projectileSpeedPerSecond`、`projectileShooterAdvantageRadius` 或 hit radius；否则会把射程、手感和显示误导混在一起。

## 最新验收：BP-28S-18 Projectile Terminal VFX 诚实化 / 诊断化

改动边界：

- 文件：`frontend/src/features/battle/renderer/effects/battleFeedbackSceneBridge.ts`。
- 文件：`frontend/src/features/battle/renderer/remoteViewDiagnostics.ts`。
- 未修改 `GameScene.ts`、后端、projectile runtime、hitbox、TTL、speed、damage、weapon 数值或 API。

本轮处理：

- `ProjectileFeedbackState` 同时保留 `displayPosition` 与 `authoritativePosition`。
- capture 时 `displayPosition` 来自 `getProjectileDisplayPosition(projectileId)` 或 fallback；`authoritativePosition` 来自 `projectile.position`。
- projectile 消失时的 terminal tracer 改为以 authoritative position 为终点，避免把显示插值位置误当服务器终点。
- 非 rocket terminal 不再播放 full impact spark，避免把普通子弹 TTL / 墙 / 出界消失表现成命中火花。
- rocket terminal 仍可保留 spark / pulse，但位置使用 authoritative position。
- 当 display position 与 authoritative position 有中等距离差时，播放极短、低 alpha、细 correction tracer，从显示点指向权威终点；跨屏大差异不画线，避免制造新误导。
- diagnostics 新增 `window.__slayDemoBattleDiagnostics.remoteView.projectileTerminals`，记录 projectileId、kind、display/auth position、位置差、TTL、最近 hero 的权威边缘距离和显示边缘距离。

验证：

- `npm run build` 通过，只有既有 Vite/Rollup warning。
- `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp28s-18-terminal-vfx.json` 通过。
- BP28 smoke 指标：`sameBattle=true`，权威确认约 `330ms`，本地移动反馈约 `12ms`，枪口反馈约 `14ms`，clientA/clientB RAF p95 约 `16.8ms`，`>25ms` 帧为 `0`，hard snap `0`，soft correction `0`，warnings `0`。
- targeted CDP 沙箱验证：`.runtime/bp28-render-feel-smoke/summary-bp28s18-terminal-diagnostics-sandbox.json` 中 `projectileTerminals.count=1`，成功记录 display/auth position、`displayToAuthoritativeDistance`、TTL 和最近 hero edge distance。
- targeted 截图：`.runtime/bp28-render-feel-smoke/screen-bp28s18-terminal-diagnostics.png`。该截图主要用于确认运行稳定；terminal 线太短命，视觉证据以 diagnostics 为准。

BP-28S-18 结论：

- 这是已接受的 VFX/display 层小票。
- 它没有提高命中半径，也没有延长射程；只是让 projectile 消失反馈更诚实、更可诊断。
- 后续若用户仍反馈“打中未判”，下一步应基于 `projectileTerminals` 样本判断是 display/auth 时间线差、TTL/墙/出界，还是后端 swept hit 真的需要复核。

## 最新验收：BP-28S-19 / 19A Projectile Terminal Headful 复核

改动边界：

- BP-28S-19 是 headful 复核票，先不改代码。
- BP-28S-19A 是小修票，只修改 `frontend/src/features/battle/renderer/effects/battleFeedbackSceneBridge.ts`。
- 未修改 `GameScene.ts`、后端、hitbox、TTL、speed、damage、weapon 数值、API 或 diagnostics schema。

复核过程：

- 用 controlled CDP sandbox 制造 display/auth position 差异后删除 projectile。
- 首轮样本：`.runtime/bp28-render-feel-smoke/summary-bp28s19-terminal-headful.json`，display/auth 差异约 `74.65px`。
- 避开 HUD 复核样本：`.runtime/bp28-render-feel-smoke/summary-bp28s19-terminal-headful-clear.json`，display/auth 差异约 `66.31px`。
- 截图：`.runtime/bp28-render-feel-smoke/screen-bp28s19-terminal-30ms.png`、`.runtime/bp28-render-feel-smoke/screen-bp28s19-terminal-90ms.png`、`.runtime/bp28-render-feel-smoke/screen-bp28s19-terminal-clear-30ms.png`、`.runtime/bp28-render-feel-smoke/screen-bp28s19-terminal-clear-90ms.png`。

复核结论：

- 普通子弹 terminal 已经不会再呈现成明显命中火花，这是正确方向。
- correction tracer 有 diagnostics 证据触发，但截图中可见度偏弱；继续大幅加粗会重新制造“命中/危险范围”暗示，所以只做小幅可读性提升。

BP-28S-19A 小修：

- correction tracer 从 `90ms / alpha 0.2 / thickness 1` 调整为 `140ms / alpha 0.38 / thickness 2`。
- `ghostScale` 保持 `0.35`，不变成 impact dot。
- 非 rocket terminal 仍不播放 impact spark。

验证：

- `npm run build` 通过，只有既有 Vite/Rollup warning。
- 复跑 clear sandbox：`.runtime/bp28-render-feel-smoke/summary-bp28s19a-terminal-headful-clear.json`，display/auth 差异约 `66.26px`，`projectileTerminals.count=1`。
- 复跑截图：`.runtime/bp28-render-feel-smoke/screen-bp28s19a-terminal-clear-30ms.png`、`.runtime/bp28-render-feel-smoke/screen-bp28s19a-terminal-clear-90ms.png`。
- `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp28s-19a-correction-tracer.json` 通过。
- BP28 smoke 指标：`sameBattle=true`，权威确认约 `313ms`，本地移动反馈约 `12ms`，枪口反馈约 `14ms`，clientA/clientB RAF p95 约 `16.8ms`，`>25ms` 帧为 `0`，hard snap `0`，soft correction `4`，warnings `0`。

BP-28S-19 结论：

- terminal VFX 当前可接受：它不会把普通子弹消失伪装成命中。
- correction tracer 的解释性仍不是最终形态；更好的长期方案是采集真实争议样本并做回放/诊断，而不是继续靠肉眼调粗 VFX。

## 最新验收：BP-28S-20 命中争议样本采集

改动边界：

- 文件：`scripts/bp28-render-feel-smoke.ps1`。
- 未修改 `GameScene.ts`、前端 runtime/renderer、后端、API、projectile 判定、武器数值、replay/result schema 或 gameplay 语义。
- 该票只扩展 smoke summary 输出，不改变现有 smoke pass/fail 断言；没有 terminal 样本也不会失败。

本轮处理：

- 在 summary 顶层新增 `hitDisputeSamples`。
- 数据源优先使用 `remoteView.after.diagnostics.projectileTerminals`，缺失时回退到 before diagnostics。
- 每个样本包含 projectile terminal 的 sequence、atMs、sampleWallMs、projectileId、kind、display/auth position、display/auth 距离、TTL、最近 hero 的显示/权威 edge distance。
- 每个样本尽量关联 before/after `/battle/state/{battleId}` 中最近 hero 的 HP、alive、position，生成 `hpDelta`、`damageObserved`、`terminalNearButNoDamage`。
- 近距离未伤害阈值记录在 `thresholds.terminalNearEdgeDistancePx = 24`。

验证：

- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/bp28-render-feel-smoke.ps1 -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp28s-20-hit-dispute-samples.json` 通过。
- 本次真实 smoke 采到 `hitDisputeSamples.sampleCount=7`，`terminalCount=7`。
- 7 个样本均关联到 `bot-3 / Ember`，before HP `100`、after HP `28`，`hpDelta=-72`。
- `damageObserved=7`，`terminalNearButNoDamage=0`，warnings `0`。

BP-28S-20 结论：

- 现在“看着打中但未命中”可以被采样成结构化数据，而不是只能靠肉眼争论。
- 本轮样本显示 projectile terminal 与 HP delta 能关联上，当前没有采到 near-but-no-damage 争议样本。
- 下一步应使用 headful/真实双客户端长一点的采样，专门寻找 `terminalNearButNoDamage=true` 的样本；只有采到这种样本后，才有资格判断是否需要后端 hit 判定复核。

## 最新验收：BP-28S-21 真实 Headful 命中争议采样

改动边界：

- 本票未修改代码。
- 使用 BP-28S-20 已扩展的 smoke summary 做真实 headful 双客户端采样。

执行：

- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/bp28-render-feel-smoke.ps1 -Headful -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-headful-bp28s-21-hit-dispute-real.json` 通过。

关键指标：

- `sameBattle=true`，headful 模式。
- 权威确认约 `355ms`。
- 本地移动反馈约 `12ms`，枪口反馈约 `14ms`。
- clientA/clientB RAF p95 均约 `16.8ms`，`>25ms` 帧为 `0`。
- local correction：hard snap `0`，soft correction `1`，deadzone ignored `131`。
- warnings `0`。

命中争议样本：

- `hitDisputeSamples.sampleCount=6`，`terminalCount=6`。
- `terminalNearButNoDamage=0`。
- `damageObserved=2`。
- 最近目标包括 `Ember`、本轮 clientA handle、`Nova`。
- 最小 authoritative edge distance 约 `15.243px`，最小 display edge distance 约 `63.742px`。

BP-28S-21 结论：

- 本轮真实 headful 采样没有抓到“近距离但未伤害”的争议样本，不触发 BP-28S-22 后端 hit 判定复核。
- 数据继续支持当前判断：视觉 display timeline 与 authoritative terminal timeline 有差异，用户的“看着碰到”更可能来自显示时间线/拖尾/终点解释，而不是后端立即漏判。
- 下一步优先级应回到用户持续反馈的移动/手感/卡顿，而不是继续盲目调大命中半径。

## 当前执行：BP-31D/BP-31E 移动手感单变量收敛

BP-31D 只读审计结论：

- 前后端基础移动核心常量已对齐：基础速度 `255`、冲刺倍率 `1.75`、耐力 `100`、消耗 `38/s`、恢复 `24/s`。
- 输入采样不是当前首要嫌疑：WASD/Shift 直接读取按键状态，没有 debounce 或节流。
- 相机不是当前首要嫌疑：follow 和 offset 已经偏即时，不像“慢半拍”的主因。
- 当前最可疑单点是本地英雄移动中的 authoritative correction smoothing：移动中 half-life 为 `480ms`，在持续收到权威帧时容易表现成“被服务器位置慢慢拖住”。

BP-31E 当前执行决策：

- 只改一个变量：`LOCAL_AUTHORITATIVE_MOVING_CORRECTION.halfLifeMs` 从 `480` 降到 `240`。
- 不同时改速度、冲刺倍率、耐力、输入、相机、deadzone、hard snap 或 roll-forward 上限，避免把手感变化混在一起。
- 目标不是让角色“数值更快”，而是减少本地预测后被慢速权威校正拖拽的粘滞感。
- 验收看 `hardSnapDelta` 是否保持 `0` 或极低、`softCorrectionDelta` 是否不暴涨、RAF 是否仍稳定、用户实机 Shift/WASD 转向是否少了“被拉住”的感觉。

BP-31E 当前验收结果：

- 改动文件：`frontend/src/features/battle/renderer/localAuthoritativeHeroCorrection.ts`。
- 实际变更：移动中 correction half-life `480ms -> 240ms`，`deadzone: 12` 保持不变。
- `npm run build` 通过，只有既有 Vite/Rollup chunk warning。
- `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp31e-moving-correction-240.json` 通过。
- smoke 指标：`sameBattle=true`，权威确认约 `334ms`，本地移动反馈约 `11ms`，枪口反馈约 `14ms`，clientA/clientB RAF p95 约 `16.8ms`，`>25ms` 帧为 `0`，hard snap `0`，soft correction `0`，deadzone ignored `131`，warnings `0`。

BP-31E 结论：

- 这是一项可接受的单变量手感改动：理论上减少“移动中被权威位置慢慢拖住”的粘滞感，自动化指标未显示抖动或瞬移风险。
- 它不解决所有“慢”的主观问题；如果用户实机仍觉得慢，下一步不能继续盲调 correction，而要单独审计地图尺度、视觉速度感、基础速度数值与镜头可读性。

## 当前执行：BP-31F 移动速度感审计

BP-31F 只读审计结论：

- 当前数值速度不低：walk `255 world px/s`，sprint `446.25 world px/s`。
- 在 battle camera `zoom=1.32` 下，屏幕速度约为 walk `337 px/s`、sprint `589 px/s`。
- 当前 `FLOOR_TILE_SIZE=64`，折算屏幕 tile 约 `84.5px`，walk 约 `4 tiles/s`，sprint 约 `7 tiles/s`。
- 当前可视世界约 `970 x 545 world units`，sprint 横穿一屏约 `2.2s`，不应优先继续调高 `BASE_MOVE_SPEED` 或 `SPRINT_MULTIPLIER`。
- 镜头确实缺少速度反馈，但 camera 变更会影响瞄准、空间感和多人可读性，风险高于本地表现通道。
- 普通 walk/sprint 明显缺少动势：角色主要是 sprite 平移、朝向条、圆形轮廓、血条；projectile 和技能有 trail/afterimage，但普通移动没有脚步、气流、拖尾或身体倾斜。
- 地图/碰撞也可能让贴角时产生“卡”的体感，但下一步不应先动地图和碰撞，因为这会改变战斗路径和语义。

BP-31F 下一票决策：

- 下一张代码票定为 BP-31G：只给本地玩家增加 renderer-only 普通移动/冲刺动势通道。
- 范围限制在 `frontend/src/features/battle/renderer/entities/worldViewFactory.ts`，必要时只在同目录抽 helper。
- 不改速度、耐力、碰撞、权威同步、local correction、相机、地图、远程插值。
- 目标是让玩家看到“正在高速移动”的反馈，而不是改变真实位移。

## 最新验收：BP-31G 本地玩家移动动势表现

改动边界：

- 文件：`frontend/src/features/battle/renderer/entities/worldViewFactory.ts`。
- 未修改 `GameScene.ts`、速度常量、冲刺倍率、耐力、碰撞、权威 replay/correction、相机、地图、后端、API 或 smoke 脚本。
- 表现只对 local hero 生效；remote hero 不走该预测表现。

本轮处理：

- `HeroView` 新增 local-only `localMotionStreaks`。
- local hero 创建 3 条低 alpha 蓝色 motion streak，深度低于英雄主体、血条、weapon cue、projectile 和拾取物。
- 每帧根据 local display position 的帧间位移估算速度，低速快速衰减，高速/冲刺时更明显。
- 死亡或隐藏时会隐藏残影并重置上一帧位置。
- 不改变任何真实位移、碰撞、stamina、hp、weapon、projectile 或 authoritative state。

验证：

- `npm run build` 通过，只有既有 Vite/Rollup warning。
- `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp31g-local-motion-streak.json` 通过。
- smoke 指标：`sameBattle=true`，权威确认约 `321ms`，本地移动反馈约 `13ms`，枪口反馈约 `15ms`，clientA/clientB RAF p95 约 `16.8ms`，`>25ms` 帧为 `0`，hard snap `0`，soft correction `4`，warnings `0`。

BP-31G 结论：

- 这是安全的 renderer-only 速度感增强，可以接受。
- 它解决的是“看起来平移、缺少动势”的问题，不解决贴角碰撞顿挫或地图路线窄的问题。
- 如果用户实机仍觉得“卡住/下不去/贴墙顿”，下一步进入 BP-31H，只审计 obstacle clearance 与滑墙，不动速度和表现层。

## 当前执行：BP-31H 碰撞 clearance / 滑墙 / 地图可读性审计

BP-31H 只读审计结论：

- 前后端核心几何对齐：`WORLD_SIZE=2560x1600`、`HERO_RADIUS=18`、内部障碍 id/kind/position/size、边界墙生成、AABB 扩张、16px 分步移动、X/Y 分离滑墙算法均一致。
- 当前风险不是前后端 drift，而是可读性没有表达真实 clearance。
- 边界墙视觉占用 `0..64` / `1536..1600`，但角色中心还要退 `HERO_RADIUS=18`，实际中心可走范围约为 `x/y >= 82`、`x <= 2478`、`y <= 1518`。
- 小地图目前画的是 raw obstacle rect，没有画 hero-radius-expanded 的不可通行区；玩家会以为贴着障碍或边界还有空间。
- 64px 视觉缝隙扣掉双侧 hero radius 后只剩约 `28px` 中心余量；斜向贴角会自然产生“卡/顿/下不去”的体感。
- 不应优先改碰撞半径、障碍尺寸或滑墙算法，因为这会改变战斗语义。

BP-31H 下一票决策：

- 下一张代码票定为 BP-31I：collision clearance readability overlay。
- 优先在小地图和 arena 边界表达“角色中心不可越过/不可通行扩张区”，而不是放宽真实碰撞。
- 允许范围：`frontend/src/features/battle/presenters/minimapPresenter.ts`、`frontend/src/ui/Hud.ts`、`frontend/src/features/battle/renderer/arena/arenaBuilder.ts`，必要时只加前端 presentation helper。
- 不改后端 runtime、runtime-local movement、`constants.ts` gameplay 常量、obstacle/spawn 数据。

## 最新验收：BP-31I collision clearance 可读性覆盖层

改动边界：

- 文件：`frontend/src/features/battle/presenters/minimapPresenter.ts`、`frontend/src/ui/Hud.ts`、`frontend/src/features/battle/renderer/arena/arenaBuilder.ts`。
- 未修改 `GameScene.ts`、后端 runtime、runtime-local movement/collision、`constants.ts`、obstacle/spawn gameplay 数据、速度、半径或滑墙算法。
- `obstacleBounds` 仍保持 raw 数据，只用于真实碰撞；clearance 只在 HUD/arena 表现层派生。

本轮处理：

- 小地图新增 `clearanceObstacles`：按 `HERO_RADIUS` 从 raw obstacle 派生低 alpha 不可通行扩张层。
- 小地图新增 `centerLimitRect`：用青蓝虚线显示角色中心可走范围，表达边界墙 + hero radius 后的真实限制。
- arena 边界新增静态内侧危险带和角色中心限制线，提示玩家边界附近不是可贴边通行区域。
- 不改变移动、碰撞、spawn、pickup、权威同步或任何 battle state。

验证：

- `npm run build` 通过，只有既有 Vite/Rollup warning。
- `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp31i-clearance-readability.json` 通过。
- smoke 指标：`sameBattle=true`，权威确认约 `324ms`，本地移动反馈约 `12ms`，枪口反馈约 `15ms`，clientA/clientB RAF p95 约 `16.8ms`，`>25ms` 帧为 `0`，hard snap `0`，soft correction `0`，warnings `0`。

BP-31I 结论：

- 该票可接受：它解释真实 clearance，不改变真实碰撞。
- 用户之前“看起来能下去但实际下不去”的反馈现在有更清楚的视觉依据。
- 若后续仍有具体坐标卡角，再进入 BP-31J，用坐标复现决定是否需要语义级滑墙/布局调整。

## 当前执行：BP-32A battle 可玩性下一轮总审计

BP-32A 只读审计结论：

- 一命模式主链已经基本成立：后端死亡后 `respawnMs=0`，`players.count(_.alive) <= 1` 会结束 battle；前端本地死亡也写 `alive=false / lifeState=dead / respawnMs=0`，本地完成条件同样是存活人数 `<= 1`。
- 仍有 respawn 类型、`RespawnSceneBridge`、`respawn` HUD tone 等历史残留，但当前 `advanceCombatRespawns()` 返回空数组，属于清理/表达一致性票，不是最高可玩性阻断。
- battle 结果闭环主线已接上：authoritative finished state 会投影 result/replay，前端等待 `resultReady && replayReady` 后拉结果；后续仍需长期产品化，但不是当前最高风险。
- 当前最大用户可感知洞是 authoritative command outcome：网络失败、HTTP 非 2xx、解析失败、服务器 no-op、cooldown、invalid target、dead/finished 都可能只表现为“按了没反应”或“本地播了意图 VFX 但服务器没发生”。
- 这会直接破坏多人战斗可信度，优先级高于清理 respawn 残留。

BP-32A 下一票决策：

- 下一张代码票定为 BP-32B：Authoritative command outcome / no-op feedback bridge。
- 目标：多人 authoritative 模式下命令失败和服务器 no-op 必须有可感知反馈；本地 optimistic VFX 只能表达“意图/预备”，不能让用户误以为服务器已生效。
- 允许范围：`frontend/src/features/battle/adapters/authoritativeBattleClient.ts`、`frontend/src/features/battle/page/useBattlePageRuntime.ts`、`frontend/src/features/battle/page/authoritativeCommandHistory.ts`、`frontend/src/features/battle/renderer/effects/sharedAuthoritativeLocalFeedbackSceneBridge.ts`，必要时新增小型 page helper。
- 不改一命/死亡/结算规则、命中判定、伤害/冷却数值、移动/校正算法、replay/mail/rating/profile 页面、GameScene。

## 最新验收：BP-32B authoritative command outcome / no-op feedback

改动边界：

- 文件：`frontend/src/features/battle/adapters/authoritativeBattleClient.ts`、`frontend/src/features/battle/page/useBattlePageRuntime.ts`、`frontend/src/pages/BattlePage.tsx`、`frontend/src/app/styles/battle-shell.css`。
- 未修改 `GameScene.ts`、后端 Scala、combat 规则、命中判定、移动/校正、技能冷却/数值、HUD feed/snapshot event、replay/mail/rating/profile 页面。
- 本票不做技能状态 diff，不推断具体 cooldown/invalid target 成因。

本轮处理：

- `sendAuthoritativeBattleCommand` 现在返回结构化 outcome：成功、HTTP error、network/abort、parse/invalid payload。
- HTTP non-2xx 会读取 `{ error }`，页面层映射成中文提示。
- 正常 accepted 且 `acceptedCommandSeq >= outbound.clientCommandSeq`：保持现有 prune 行为，不显示提示。
- accepted 但 seq 未覆盖当前命令：显示保守提示，例如“对战已结束”“你已被淘汰”“命令未应用”。
- failure：显示页面级 transient notice，例如“网络同步中断”“服务器响应异常”“命令被服务器拒绝”“对战已结束或不存在”“玩家状态未同步”“命令提交失败”。
- notice 不进入 HUD feed，不污染 authoritative snapshot events；同文案 `1200ms` 去重，`2000ms` 后自动消失。
- 失败后不停止 uplink loop，不清空 command history，不影响 result/replay 主链。

验证：

- `npm run build` 通过，只有既有 Vite/Rollup warning。
- `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp32b-command-outcome-notice.json` 通过。
- 本次 smoke 未模拟 failure，正常成功路径未显示 notice。
- smoke 指标：`sameBattle=true`，权威确认约 `326ms`，本地移动反馈约 `12ms`，枪口反馈约 `14ms`，clientA/clientB RAF p95 约 `16.8ms`，`>25ms` 帧为 `0`，hard snap `0`，soft correction `0`，warnings `0`。

BP-32B 结论：

- 该票可接受：多人 authoritative 命令提交失败和保守 no-op 现在不会被静默吞掉。
- 它不解决具体技能 no-op reason；若要区分 cooldown/非法目标/目标被阻挡，需要后端 contract 给 explicit outcome，不能靠前端状态差分硬猜。

## 当前执行：BP-32C 一命模式表达残留审计

BP-32C 只读审计结论：

- 一命模式主链已成立，当前要清的是表达残留，不是改规则。
- 可安全清理的残留是 UI-only：HUD feed 中 `respawn` tone 的“复活”标签/蓝色返场语义，以及 renderer feedback 中本地非权威不可达的“复活”浮字。
- 不应删除 `respawnMs`、`respawning`、`respawn` event/tone DTO：它们是协议/旧 payload 兼容字段；pickup 的 `respawnMs` 仍是有效补给刷新机制。
- 不应本票删除 `RespawnSceneBridge`、`respawnController` 或 `advanceCombatRespawns()`：这些属于 dead-code cleanup，会触碰 `GameScene`/本地 runtime 构造链，需单独票。

BP-32C 下一票决策：

- 下一张代码票定为 BP-32C-1：UI-only one-life wording cleanup。
- 允许范围：`frontend/src/ui/Hud.ts`、`frontend/src/features/battle/renderer/effects/battleFeedbackSceneBridge.ts`，可选 `hudPresenter.ts` 仅加兼容注释。
- 不改 domain/contracts/authoritative adapter/backend/GameScene/respawn controller/bridge 删除链。

## 最新验收：BP-32C-1 UI-only one-life wording cleanup

改动边界：

- 文件：`frontend/src/ui/Hud.ts`、`frontend/src/features/battle/renderer/effects/battleFeedbackSceneBridge.ts`。
- 未修改 `GameScene.ts`、domain/types、contracts、authoritative adapter、后端、respawn controller/bridge 删除链、combat 规则。
- `respawn` tone/class/DTO 兼容字段仍保留，pickup `respawnMs` 不受影响。

本轮处理：

- HUD feed 的 `respawn` tone 标签从“复活”改为“淘汰”。
- `.hud-feed-entry.respawn` 仍保留 class 名称，但视觉从蓝色返场语义改为橙红 warning/danger 语义。
- 本地非权威 legacy `!previous.alive && hero.alive` 分支不再显示“复活”浮字，只保留兼容 pulse，并注明一命模式下不能表达玩家返场。

验证：

- `rg "复活|返场" frontend/src` 无匹配。
- `npm run build` 通过，只有既有 Vite/Rollup warning。
- `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp32c1-one-life-wording.json` 通过。
- smoke 指标：`sameBattle=true`，权威确认约 `343ms`，本地移动反馈约 `12ms`，枪口反馈约 `16ms`，clientA/clientB RAF p95 约 `16.8ms`，`>25ms` 帧为 `0`，hard snap `0`，soft correction `0`，warnings `0`。

BP-32C-1 结论：

- 该票可接受：玩家可见 UI 不再出现“复活/返场”表达，一命模式叙事更一致。
- 后续如要删除 respawn dead code，需要单独审计 `RespawnSceneBridge` 与本地 frame bridge，不应混入玩法/渲染票。

## 最新验收：BP-32D skill no-op explicit outcome

改动边界：

- 文件：`backend/src/main/scala/battle/api/BattleCommandApi.scala`、`backend/src/main/scala/battle/runtime/BattleRuntime.scala`、`backend/src/main/scala/battle/runtime/InMemoryAuthoritativeBattleRuntime.scala`、`backend/src/main/scala/battle/services/InMemoryAuthoritativeBattleService.scala`、`backend/src/main/scala/battle/routes/BattleRoutes.scala`。
- 文件：`frontend/src/features/battle/adapters/authoritativeBattleClient.ts`、`frontend/src/features/battle/page/useBattlePageRuntime.ts`。
- 未修改 `GameScene.ts`、weapon/projectile/pickup 语义、移动/命中数值、技能距离/冷却数值、HUD feed 事件、replay/mail/rating/profile/forum/admin。
- 本票只表达 Dash/Blink/Freeze 的服务器技能 outcome，不声称开火、命中、换弹、拾取是否成功。

本轮处理：

- command submit 成功响应保留旧字段 `battleId / acceptedTick / acceptedCommandSeq / serverTime`。
- 新增 `commandStatus: "applied" | "ignored"`、`commandReason?: "battle_finished" | "battle_inactive" | "player_dead"`、`outcomes`。
- `outcomes` 只在本次请求 `castDash/castBlink/castFreeze` 为 true 时产生，形如 `{ action, status, reason }`。
- 技能 no-op reason 当前为 `skill_not_owned / cooldown / missing_target / out_of_range / invalid_target / no_direction / blocked`。
- 前端 authoritative client 兼容旧响应：旧后端没有新字段时默认 `commandStatus="applied"`、`outcomes=[]`。
- 页面层只根据服务器返回的 outcome 显示短中文 notice，例如“冷却中”“没有目标”“目标太远”“目标无效”“没有方向”“被障碍阻挡”“已被淘汰”。

验证：

- `npm run build` 通过，只有既有 Vite/Rollup warning。
- `sbt compile` 在清理旧后端 sbt/java 链后通过，实际编译 6 个 Scala sources。
- 后端已用新编译源码重启，`/health` 返回 `status=ok`。
- `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp32d-skill-outcomes-recompiled.json` 通过。
- smoke 指标：`sameBattle=true`，权威确认约 `343ms`，本地移动反馈约 `12ms`，枪口反馈约 `15ms`，clientA/clientB RAF p95 约 `16.8ms`，`>25ms` 帧为 `0`，hard snap `0`，soft correction `2`，warnings `0`。
- 手工 contract 探针：独立入队开战后发送非法 Blink，后端返回 `commandStatus=applied`、`outcomes[0]={ action: "Blink", status: "noop", reason: "invalid_target" }`。

BP-32D 结论：

- 该票可接受：技能“按了没反应”的核心 no-op 原因现在由服务器明确返回，前端不再靠状态差分猜。
- 这提高的是多人 battle 可信度和反馈清晰度，不是最终技能预测系统；cooldown/技能重演/本地预演仍可继续深化。

## 最新验收：BP-33A / BP-33B projectile 命中可信度诊断

BP-33A 只读审计结论：

- 当前不应直接调大 hitbox 或延长射程。后端 swept 命中已经使用 `projectile radius 8 + hero radius 18 + shooter advantage 6 = 32px` 的判定半径，基础判定并不小。
- 用户“子弹碰到身体但没扣血”的可疑来源更可能是 display timeline、贴图外缘大于真实 hit ring、终端位置不可见、墙/边界/TTL 移除没有解释。
- 用户“子弹莫名消失/射程缩短”的直接缺口是：后端移除 projectile 时没有暴露 `hit / obstacle / world / ttl` 原因，也没有暴露真实 terminal point，前端只能用上一帧位置补 terminal tracer。
- 因此下一步先补服务端真值诊断，而不是改玩法或 VFX。

BP-33B 改动边界：

- 文件：`backend/src/main/scala/battle/objects/BattleAggregateState.scala`、`backend/src/main/scala/battle/runtime/InMemoryAuthoritativeBattleRuntime.scala`、`backend/src/main/scala/battle/routes/BattleRoutes.scala`、`scripts/bp28-render-feel-smoke.ps1`。
- 未修改 `GameScene.ts`、frontend renderer/VFX、hit radius、damage、TTL、projectile speed、tick、weapon 参数、shooter advantage 或外围页面。
- 新增 `/battle/state` 字段 `projectileTerminals`，最多保留最近 64 条。

新增 terminal 字段：

- `projectileId`、`kind`、`ownerPlayerId`、`ownerHeroId`、`reason`。
- `reason` 为 `hit | obstacle | world | ttl`。
- `start`、`end`、`terminalPosition`、`ttlBefore`、`ttlAfter`、`elapsedMs`。
- 命中时附带 `targetPlayerId`、`targetHeroId`、`hpBefore`、`hpAfter`、`damage`。

验证：

- `npm run build` 通过，只有既有 Vite/Rollup warning。
- `sbt compile` 在 `backend/` 目录通过。
- `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp33b-projectile-terminal-reasons.json` 通过。
- smoke 指标：`sameBattle=true`，权威确认约 `335ms`，本地移动反馈约 `12ms`，枪口反馈约 `14ms`，clientA/clientB RAF p95 约 `16.8ms`，hard snap `0`，soft correction `0`，warnings `0`。
- 本次 hit-dispute 来源已切到 `api.afterState.projectileTerminals`，server reason 汇总：`hit=2`、`obstacle=4`、`world=0`、`ttl=2`、`other=0`。

BP-33B 结论：

- 该票可接受：现在能够用服务端真值解释 projectile 为什么终止。
- 本次 smoke 已显示一部分“子弹消失”来自 obstacle/TTL，而不是命中漏判。
- 下一步应该把 server terminal reason / terminal position 接入前端 terminal VFX，优先让玩家看到“撞墙/到达射程/命中”的不同收尾，而不是继续盲调判定。

## 最新验收：BP-33C server projectile terminal -> frontend VFX bridge

改动边界：

- 文件：`frontend/src/features/battle/adapters/authoritativeBattleClient.ts`、`frontend/src/features/battle/renderer/authoritativeBattleStateBridge.ts`、`frontend/src/features/battle/renderer/effects/battleFeedbackSceneBridge.ts`、`frontend/src/features/battle/renderer/remoteViewDiagnostics.ts`。
- `frontend/src/scenes/GameScene.ts` 仅新增一行 bridge 调用：把 authoritative frame 转交给 `BattleFeedbackSceneBridge`，未在 scene 中保存 terminal 状态。
- 未修改 backend、`frontend/src/domain/types.ts`、`authoritativeFrameSnapshotApplier.ts`、local combat/projectile/weapon/pickup runtime、world interpolation、HUD/CSS、replay/session persistence。

本轮处理：

- 前端 authoritative client 现在 normalize `/battle/state.projectileTerminals`；旧响应缺字段时默认为空数组。
- renderer authoritative frame 承载 server terminal truth，但不进入 `GameSnapshot`，也不进入玩法同步 applier。
- feedback bridge 优先使用服务端 `terminalPosition/reason` 播放 projectile 收尾 tracer；如果没有 server terminal，保留旧的 snapshot-diff fallback。
- 因后端保留最近 64 条 terminal，前端按 terminal identity 去重，并用“本 bridge 已见过 live projectile”的 gate 避免 bootstrap 历史 terminal 批量重播。
- terminal VFX 按 reason 区分：`hit/obstacle` 播清晰收尾 spark；`world` 播弱化边界反馈；`ttl` 只播 tracer/fade，不再播放撞击火花，避免被误读成命中或撞墙。
- diagnostics terminal 样本新增 server/source/reason/terminalPosition/target/hp/damage 字段，争议样本可以区分 `hit / obstacle / world / ttl`。

验证：

- `npm run build` 通过，只有既有 Vite/Rollup warning。
- `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp33c-server-terminal-vfx-r1.json` 通过。
- BP-33C smoke 指标：`sameBattle=true`，权威确认约 `342ms`，本地移动反馈约 `11ms`，枪口反馈约 `13ms`，clientA/clientB RAF p95 约 `16.8ms`，`>25ms` 帧为 `0`，hard snap `0`，soft correction `1`，warnings `0`。
- hit-dispute 来源仍为 `api.afterState.projectileTerminals`，server reason 汇总：`hit=2`、`obstacle=4`、`world=0`、`ttl=2`、`other=0`。

BP-33C 结论：

- 该票可接受：projectile 消失/收尾现在由服务端 terminal truth 驱动前端 VFX 和诊断，不再只靠前端猜测上一帧位置。
- 这提升的是“命中/撞墙/边界/射程耗尽”的可解释性和视觉可信度；它没有改变 hit radius、damage、TTL、projectile speed、tick、weapon 参数或命中补偿。
- 残余风险：极短生命周期 projectile 如果从未被 renderer 捕获为 live，会被 seen-live gate 抑制；这是为了避免刚进局时重播历史 retained terminals，后续如果要补这类边缘弹，需要单独设计 event-age/visibility gate。

## 最新验收：BP-33D terminal diagnostics smoke field closure

改动边界：

- 文件：`scripts/bp28-render-feel-smoke.ps1`。
- 未修改 frontend 业务代码、backend、`GameScene.ts`、domain/types、authoritative frame applier、玩法、文档结构或页面。
- 本票只增强 smoke summary 输出，不改变测试动作、输入时长、battle runtime 或 VFX 逻辑。

本轮处理：

- `hitDisputeSamples.samples[]` 现在直接输出前端 remoteView terminal diagnostics 自身字段：`clientSource`、`diagnosticSource`、`clientReason`、`clientTerminalPosition`、`clientTargetPlayerId`、`clientTargetHeroId`、`clientHpBefore`、`clientHpAfter`、`clientDamage`。
- 保留已有 `serverReason/serverTerminalPosition/serverHpBefore/serverDamage/...` 字段，用于对照后端 API terminal truth。
- `serverState` fallback 样本也稳定包含 client 字段，值为 `null`，避免 summary shape 不稳定。
- 新增 `clientReasonSummary` 与 `clientSourceSummary`，用于判断前端诊断样本是否真的来自 server terminal bridge，而不是 snapshot-diff fallback。

验证：

- `npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp33d-terminal-diagnostics-fields.json` 通过。
- BP-33D smoke 指标：`sameBattle=true`，权威确认约 `321ms`，本地移动反馈约 `13ms`，枪口反馈约 `15ms`，clientA/clientB RAF p95 约 `16.8ms`，`>25ms` 帧为 `0`，hard snap `0`，soft correction `2`，warnings `0`。
- summary 直接证明前端 diagnostics 接通：`clientSourceSummary.server=6`、`clientSourceSummary.snapshot-diff=0`；`clientReasonSummary` 为 `hit=1`、`obstacle=4`、`ttl=1`、`world=0`。
- 样本示例中 `clientSource="server"`、`clientReason="obstacle"`、`clientTerminalPosition` 与 `serverTerminalPosition` 对齐。

BP-33D 结论：

- 该票可接受：现在 smoke 不只证明后端 API 有 terminal truth，也能证明前端 VFX/diagnostics 通道确实收到了 server terminal reason/position。
- 这仍然不是“渲染完成”；它只是把 projectile terminal 可解释性的自动化验收链补完整。

## 最新验收：BP-33E projectile terminal VFX headful 复核

处理边界：

- 未修改代码。
- 运行 headful smoke 复核真实可视浏览器环境下的 terminal diagnostics 和帧指标。

验证命令：

```powershell
npm run demo:bp28-render-feel-smoke -- -Headful -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp33e-terminal-vfx-headful.json
```

结果：

- smoke 通过：`ok=true`、`headless=false`、`sameBattle=true`。
- 输入与帧指标：权威确认约 `353ms`，本地移动反馈约 `11ms`，枪口反馈约 `14ms`，远程 projectile birth 约 `92ms`，clientA/clientB RAF p95 约 `16.8ms`，`>25ms` 帧为 `0`，hard snap `0`，soft correction `0`，warnings `0`。
- headful terminal 样本：server terminal `9` 条，client remoteView terminal `7` 条。
- server reason 汇总：`hit=7`、`obstacle=1`、`ttl=1`、`world=0`。
- client reason 汇总：`hit=5`、`obstacle=1`、`ttl=1`、`world=0`。
- client source 汇总：`server=7`、`snapshot-diff=0`、`other=0`。
- client/server 对齐：remoteView 样本中 client reason 与 server reason 不一致数为 `0`，client terminalPosition 与 server terminalPosition 不一致数为 `0`。
- `terminalNearButNoDamage=true` 样本数为 `0`。

BP-33E 结论：

- 该复核可接受：headful 环境下前端 terminal VFX/diagnostics 确实使用 server terminal truth，而不是 snapshot-diff fallback。
- 仍需注意：旧的 nearest-hero HP delta 可能把同一时间窗内其它 projectile 的伤害显示在 `ttl` 样本旁边；现在 `clientReason/serverReason/serverTargetHeroId/serverDamage` 已能澄清该 projectile 是否真正命中。
- projectile terminal 可解释性第一轮闭环完成，但这仍不等于 BattlePage 渲染和手感最终完成。

## 最新验收：BP-31K / BP-31L 本地移动粘滞审计与单变量修复

BP-31K 只读审计结论：

- RAF/input latency 正常只能证明帧调度和采样没有明显阻塞，不能证明体感一定丝滑。
- 前后端移动常量已对齐：`BASE_MOVE_SPEED=255`、`SPRINT_MULTIPLIER=1.75`、stamina drain/recover `38/24`。
- 本地玩家 body 没有走 remote interpolation；shared authoritative 下每帧仍会本地预测推进 display pose。
- 最可疑的“移动中粘滞”来源是 reconciliation tug：命令被 ack/prune 后，服务端位移可能还没完全模拟到本地预测位置，客户端再用 moving correction 轻微拉回。
- camera deadzone/follow lerp 已不是慢跟随，剩余 camera 风险是 pointer look-ahead、roundPixels、zoom 对视觉参照的影响，需后续单独票处理。

BP-31L 改动边界：

- 文件：`frontend/src/features/battle/renderer/localAuthoritativeHeroCorrection.ts`。
- 只改 `LOCAL_AUTHORITATIVE_MOVING_CORRECTION.deadzone`：`12px -> 24px`。
- 未修改 camera、速度常量、输入/uplink、后端 runtime、worldView、VFX、技能、武器、projectile 或 `GameScene.ts`。

设计意图：

- 移动中允许更大的本地预测/权威微误差留在 display 层，不再每次小偏差都进入 smooth correction。
- 这是一张“减弱移动中拉回感”的单变量票，不解决停下后回收、camera look-ahead、输入 ack/prune 根因。

验证：

- `npm run build` 通过，只有既有 Vite/Rollup warning。
- headless smoke：`npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp31l-moving-correction-deadzone24.json` 通过。
- headless 指标：`sameBattle=true`，权威确认约 `330ms`，本地移动反馈约 `13ms`，枪口反馈约 `15ms`，RAF p95 约 `16.8ms`，hard snap `0`，warnings `0`。
- headless correction：moving 期间小误差大多进入 deadzone；preDistance max 约 `18.6px`，低于新的 moving deadzone `24px`。smooth correction 主要出现在输入结束后约 200ms 内。
- headful smoke：`npm run demo:bp28-render-feel-smoke -- -Headful -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp31l-moving-correction-deadzone24-headful.json` 通过。
- headful 指标：`sameBattle=true`，权威确认约 `341ms`，本地移动反馈约 `12ms`，枪口反馈约 `14ms`，RAF p95 约 `16.8ms`，hard snap `0`，soft correction `7`，deadzone ignored `122`，warnings `0`。
- headful correction：preDistance avg 约 `3.35px`，max 约 `9.06px`，p95 约 `5.72px`；无大拉回。

BP-31L 结论：

- 该票可接受：移动中 reconciliation 小拉回被明显降噪，且没有引入 hard snap、掉帧或明显大误差。
- 残余问题：松开移动后仍可能出现小幅 stationary smooth correction；这应作为 BP-31M 单独审计/调参，不应和移动中 correction、camera、速度混改。

## 最新验收：BP-31M 停下后 stationary correction 降噪

审计结论：

- BP-31L headful smoke 中剩余的 smooth correction 全部出现在输入结束后，第一段约在松开移动后 `35ms` 开始，持续到约 `238ms`。
- 这些 correction 的 preDistance 主要在 `4-9px` 区间，不是大偏移，也没有 hard snap；体感风险是“松手后角色/镜头轻微回收”，而不是服务端把玩家硬拉回。
- 当前 camera follow/deadzone/offset lerp 已经即时化，继续先动 local correction 的 stationary deadzone 比同时改 camera 更容易归因。

BP-31M 改动边界：

- 文件：`frontend/src/features/battle/renderer/localAuthoritativeHeroCorrection.ts`。
- 只改 `LOCAL_AUTHORITATIVE_STATIONARY_CORRECTION.deadzone`：`4px -> 10px`。
- 不改 moving deadzone、half-life、camera、速度常量、输入/uplink、后端 runtime、worldView、VFX、技能、武器、projectile 或 `GameScene.ts`。

设计意图：

- 停止移动后，`<= 10px` 的本地预测/权威微误差不再触发 smooth correction，避免玩家刚松手时看到小幅“回收/粘滞”。
- 大于 stationary deadzone 的偏移仍会走原有 smooth correction；本票不是关闭 reconciliation，也不是相信客户端位置。

验证：

- `npm run build` 通过，只有既有 Vite/Rollup warning。
- headless smoke：`npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp31m-stationary-correction-deadzone10.json` 通过。
- headless 指标：`sameBattle=true`，权威确认约 `332ms`，本地移动反馈约 `12ms`，枪口反馈约 `14ms`，clientA/clientB RAF p95 约 `16.8ms`，`>25ms` 帧为 `0`，hard snap `0`，soft correction `0`，warnings `0`。
- headless correction：`129` 次误差全部进入 deadzone，applied correction `0`；preDistance avg 约 `5.44px`，max 约 `14.90px`，p95 约 `14.34px`。
- headful smoke：`npm run demo:bp28-render-feel-smoke -- -Headful -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp31m-stationary-correction-deadzone10-headful.json` 通过。
- headful 指标：`sameBattle=true`，权威确认约 `321ms`，本地移动反馈约 `12ms`，枪口反馈约 `15ms`，clientA/clientB RAF p95 约 `16.8ms`，`>25ms` 帧为 `0`，hard snap `0`，soft correction `0`，warnings `0`。
- headful correction：`131` 次误差全部进入 deadzone，applied correction `0`；preDistance avg 约 `2.92px`，max 约 `10.86px`，p95 约 `9.22px`。
- terminal/VFX 旁路复核：headful 样本仍来自 server terminal bridge，`clientSourceSummary.server=5`、`snapshot-diff=0`，未发现本票破坏 projectile terminal 诊断链。

BP-31M 结论：

- 该票可接受：停止后的小幅 smooth correction 已被压掉，自动化和 headful 下均没有 hard snap、soft correction、掉帧或 warning。
- 这仍不能宣布“渲染完成”或“手感达到最终丝滑”。它只解决一类明确的 reconciliation 视觉回拉；下一步要看 camera 视觉参照、roundPixels/zoom、地图尺度与真实多人混战压力。

## 最新验收：BP-31N Camera roundPixels 单变量修复

只读审计结论：

- 当前 camera follow 已是即时：`camera.startFollow(cameraTarget, true, 1, 1)`。
- camera deadzone 已是 `0x0`，offset lerp 已是 `1/1`；因此摄像机主链路不是慢跟随。
- 更可疑的视觉变量是 `camera.roundPixels=true` 与 `camera.setZoom(1.32)` 叠加：非整数 zoom 下，摄像机取整可能把本地预测产生的连续亚像素位移量化成不均匀屏幕步进，表现为微抖、慢半拍或不够丝滑。

BP-31N 改动边界：

- 文件：`frontend/src/features/battle/renderer/camera/battleCameraDirector.ts`。
- 只改 `camera.roundPixels`：`true -> false`。
- 不改 zoom、pointer look-ahead ratio/max、camera follow、deadzone、offset lerp、移动速度、local correction、输入/uplink、后端 runtime、worldView、VFX、技能、武器、projectile 或 `GameScene.ts`。

验证：

- `npm run build` 通过，只有既有 Vite/Rollup warning。
- headless smoke：`npm run demo:bp28-render-feel-smoke -- -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp31n-camera-roundpixels-off.json` 通过。
- headless 指标：`sameBattle=true`，权威确认约 `409ms`，本地移动反馈约 `10ms`，枪口反馈约 `14ms`，clientA RAF p95 约 `16.8ms`，clientA `>25ms` 为 `0`，hard snap `0`，soft correction `0`，warnings `0`。
- headless 旁路：clientB 偶发 `>25ms` 为 `1`，因此补跑 headful 复核。
- headful smoke：`npm run demo:bp28-render-feel-smoke -- -Headful -SummaryPath .\.runtime\bp28-render-feel-smoke\summary-after-bp31n-camera-roundpixels-off-headful.json` 通过。
- headful 指标：`sameBattle=true`，权威确认约 `342ms`，本地移动反馈约 `11ms`，枪口反馈约 `14ms`，clientA/clientB RAF p95 约 `16.8ms`，`>25ms` 帧为 `0`，hard snap `0`，soft correction `0`，warnings `0`。
- headful correction：`129` 次误差全部进入 deadzone，applied correction `0`；preDistance avg 约 `2.92px`，max 约 `9.35px`，p95 约 `8.79px`。
- projectile terminal 旁路：headful 样本仍来自 server terminal bridge，`clientSourceSummary.server=7`、`snapshot-diff=0`，未破坏命中/terminal 诊断链。
- 用户实机反馈：BP-31N 后“效果有明显优化”，体感明显更顺。

BP-31N 结论：

- 该票可接受：关闭摄像机像素取整是当前 BattlePage 手感优化中的高收益低风险单变量，已经得到自动化、headful 与用户实机三方支持。
- 仍不能宣布最终完成：下一步需要用更长、更接近真实战斗的双客户端输入序列，覆盖转向、停走、连续开火、技能和多人压力，而不是只靠直线移动短 smoke。

## 当前仍不能宣布完成的内容

- 不能宣布 BattlePage 渲染完成。
- 不能宣布 battle 手感达到“英雄联盟/王者荣耀级别丝滑”。
- 不能宣布多人联机体验已经最终可玩。
- 不能只靠 smoke 通过就忽略用户实机反馈。

## 当前真实问题清单

- 用户实机卡顿、慢、粘已明显缓解但未最终闭环：BP-31L/BP-31M 已压掉移动中与停下后的小幅 correction 拉回，BP-31N 关闭 camera `roundPixels` 后用户实机反馈明显变顺，BP-37 长时 headful RAF p95 稳在 `16.8ms` 且无 >40ms 帧；BP-38 证明输入期 Long Task 为 `0`；BP-41 证明双客户端移动/开火压力下输入期 RAF 和命令链路稳定。剩余风险转向 VFX 语义/锚点、技能压力、资源/GC，以及 zoom/look-ahead 视野参照。
- 新进局倒计时继承已做 BP-34B + BP-40 + BP-40C/BP-40D：普通 `/battle` 默认不再恢复旧 active session，只有 `resume=1` 才恢复；新匹配带 fresh `queueRequestId`，后端不再按 handle 复用 waiting queue；真实浏览器残余根因已定位为 shared authoritative HUD 显示时钟被 Phaser scene-local time 污染，现已改为保留后端 authoritative elapsed。该项从 open bug 降级为实机观察。
- 弹道视觉锚点已做 BP-35 + BP-42A：本地 own projectile 不再使用 remote interpolation，pistol muzzle 起点对齐服务端 birth distance `30px`；手枪即时 tracer 已收短并关闭侧向 glint，当前长束特效已归档为未来 `piercing-rail-tracer-long`。
- 命中可信度仍需实机验收：9O/9P/10A 已减少“看着打中但未命中”和“子弹无声消失”的错觉；BP-33B/BP-33C 已完成 server projectile terminal reason/position 到前端收尾 VFX/诊断的第一轮闭环，但仍需玩家实机确认 `hit / obstacle / world / ttl` 的视觉差异是否足够清楚。
- 地图下边界/小地图可读性已做第一轮收口：10N 降低了 out-of-bounds 大块遮罩感并补了截图复核；BP-28S-13 增强了地板/掩体金属层级；BP-36A 补了大障碍物四边闭合视觉。用户认为箱子临时可接受但不如之前好看，后续应走素材/skin 精修，不应回退碰撞语义。
- HUD 遮挡和金属风格已做第一轮收口：10O 缩小右上/右下面板，10Q 加入金属面板语言；后续仍需要图标、动效和整体美术统一。
- 角色轮廓已做第一轮收口：10P 加入圆润底盘/剪影/命中圈并修正 depth；BP-28S-12 已加入持久 weapon cue 提升朝向/武器姿态可读性；BP-28S-14 已加入低血量世界血条状态色，BP-28S-16 已完成低血量/受击 headful 复核，但角色素材和整体受击演出还没有达到最终画风。
- 武器/弹道/命中 VFX 已做第二轮收口：10S 修复枪口方向性并增强枪口、弹道、命中短命特效；BP-28S-18 已让 terminal VFX 使用 authoritative position，并减少非命中消失被误读为命中的火花。但这仍是 VFX 通道增强，不代表后端命中补偿或最终战斗美术完成。
- 技能 VFX 已做第一轮收口，技能 no-op contract 已做 BP-32D：Blink/Freeze/Dash 的目标、释放、失败反馈和服务器 reason 已更清晰。但这仍不代表技能预测、冷却重演或本地预演已经最终工业级。
- Sprint 体感已做 BP-31A 单变量调参：multiplier `1.55 -> 1.75`；BP-31C 已发现旧后端早于改动并完成后端重启，仍需用户实机复核 Shift 是否明显且不过头。
- Reconciliation 还不是最终工业级：movement/sprint/stamina 已加入 roll-forward，但 cooldown、技能、武器切换、复杂输入历史还没有完整重演。
- 素材不是当前主因：视觉方向已定为“金属竞技场 + 空洞骑士式圆润高辨识角色轮廓”，但素材不能替代判定、手感和同步修复。

## 渲染架构原则

当前执行原则来自 `docs/notes/渲染/`：

- 服务端 authoritative 是唯一真相；客户端只发 input、做本地预测、展示平滑纠偏。
- 本地玩家与远程玩家必须分开渲染：本地玩家走即时预测和轻量 reconciliation；远程玩家走插值缓冲，不能把远程同步抖动套到本地手感上。
- 状态通道与特效通道必须分开：HP、ammo、position、alive 属于状态；枪口火光、弹道拖尾、命中特效、屏幕震动属于 VFX。VFX 不反向修改 authoritative state。
- 本地开火需要零延迟反馈：枪口、后坐力、短生命周期 ghost projectile 可以先出，但最终命中/扣血以服务端事件和状态为准。
- 远程 projectile 要“可读但不撒谎”：插值、拖尾、impact VFX 可以提升可读性，但不能制造明显打中却未命中的错觉。
- 后台恢复或网络堆积后，旧 VFX burst 应丢弃或压缩，优先保证当前画面稳定。

## 下一步任务与预计时间

- BP-39A（已验收）：视野/尺度/屏幕速度参照诊断。已采集 zoom、camera worldView、hero screen speed、look-ahead offset，并接入 BP-44 suite。
- BP-39B（已验收）：单变量视觉速度/视野标定。camera zoom `1.32 -> 1.40`，worldView 约 `897x449`；提升屏幕速度感，不改 gameplay。
- BP-42B（已验收）：VFX churn 降噪第一轮。已削减 ring effect 每帧对象 clone，并把 VFX active/slot/ring/create/destroy/peak 指标接入 smoke；后续如果实机仍有 GC 感，再基于 `vfxMetric` 做第二轮 transient/tween 池化。
- BP-43（已验收）：HUD/minimap 更新节流复核。已把 minimap 静态地图层离屏缓存，并把 `hudMetric` 接入 smoke；后续如果 HUD 仍造成体感噪声，再基于该指标处理 DOM 子树或面板更新频率。
- BP-44A（已验收）：battle feel suite。已把 MixedMovement、DualClientPressure、StraightFire 聚合成 compact summary，作为后续手感回归基线。
- BP-44B（下一主线）：battle feel 实机复核包扩展。预计 60-120 分钟；补低血量、技能释放、近身混战、拾取/换弹等更接近实机的场景，并继续监控 correction 噪声。
- BP-33F（条件触发）：如果用户实机仍反馈“子弹碰到身体但没扣血”且 smoke 出现 `terminalNearButNoDamage=true`，再做 disputed terminal 复现与补偿审计。预计 60-120 分钟。
- BP-BOT-SDK：为外部贡献者拆出 bot brain 纯逻辑接口。预计架构设计 45-90 分钟，第一版 scaffold 90-180 分钟。边界是 snapshot/profile -> intent/command，禁止依赖 Phaser/DOM/后端写入。
- BP-32C-2（暂缓）：respawn dead-code cleanup 审计。预计 60-120 分钟。只有在不改 `GameScene` 边界或可由 worker 单独抽离时处理。

## 短期判断

当前不是“什么都没做”的状态：底层同步、命中、反馈、性能热路径、技能语义和 GameScene 解耦已经有实质进展。但也不是“渲染完成”的状态。下一阶段要以用户实机体感为最高标准，继续把 battle 的响应性、可读性、命中可信度和多人稳定性往真正可玩级推进。
