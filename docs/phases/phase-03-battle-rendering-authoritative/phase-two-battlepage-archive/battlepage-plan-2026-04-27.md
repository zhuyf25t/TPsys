# Slay Demo 总体计划

更新时间：2026-04-27 19:44 Asia/Shanghai

## 当前判断

BattlePage 已经从“能跑但手感糟糕”推进到“多人联机可玩、移动/开火反馈明显变顺、主要诊断链路可用”的阶段，但还不能宣布最终完成。当前最高优先级是 battle 本体体验：渲染丝滑度、命中可信度、多人稳定性。

用户复现过“第二轮仍继承第一轮剩余时间”。该问题已完成分层闭环：BP-34B 堵住前端默认 `/battle` 恢复旧 active session，BP-40 让新匹配带 fresh `queueRequestId` 并避免后端按 handle 复用 waiting queue，BP-40C 真实浏览器 smoke 证明后端 elapsed/localStorage 都是新的但 HUD 显示被污染，BP-40D 修正 shared authoritative runtime 不再用 Phaser scene-local time 覆盖 `snapshot.elapsedMs`。主控复跑 browser smoke 通过，第二局 HUD timer 回到 `04:59` 且 local/backend elapsed 差约 `12ms`。随后 BP-42B 已完成 VFX churn 降噪第一轮，BP-43 已完成 HUD/minimap 静态层缓存与 `hudMetric` 诊断，BP-44A 已完成 battle feel suite，BP-39A 已完成视野/尺度/屏幕速度参照诊断，BP-39B 已完成 camera zoom 纯视觉标定。BP-44B 已把 `SkillPressure` 纳入 suite，并暴露出技能位移导致的本地/权威 hard snap；BP-45A 已完成最小闭环，Dash 本地显示预测和 unacked replay 已对齐，完整 suite 内 `SkillPressure hardSnap=0`。下一步优先修正 `SkillPressure` fire/muzzle 测试口径，再继续完整 skill prediction pass。

估时口径：以下时间按 Codex GPT-5.5 xhigh 的有效工程时间估算，不含用户长时间实机体验等待，不含素材生成排队时间。

## 这段时间已完成的主要工作

1. GameScene 硬解耦完成并验收。
   - `GameScene.ts` 已收束为 scene lifecycle / renderer host / glue layer。
   - 主要 battle runtime、world sync、projectile、pickup、weapon、combat frame 等职责已移出 scene。
   - 已生成 hard gate completion report。

2. Authoritative multiplayer 主链路打通。
   - 前端进入共享 battle。
   - 后端维护 authoritative state。
   - 多客户端能看到同一局。
   - 命令 POST/ack 探针显示命令链路不是当前主要瓶颈。

3. 一命模式完成第一轮。
   - 死亡后不再 respawn。
   - alive 玩家数 `<= 1` 或时间结束时结算。
   - UI 文案残留做过第一轮清理。

4. 移动手感做了多轮单变量优化。
   - 后端 sprint multiplier 提到 `1.75`。
   - 移动中 correction deadzone 提到 `24px`。
   - 停止后 correction deadzone 提到 `10px`。
   - camera follow 去阻尼、deadzone 归零、offset lerp 即时化。
   - camera `roundPixels=false`，用户实机反馈明显变顺。

5. 本地与远程渲染通道分离推进。
   - 本地开火 muzzle/tracer ghost 立即反馈。
   - 远程 hero / projectile 使用插值缓冲。
   - 状态通道与 VFX 通道保持分离。
   - 本地 own projectile 不再走 remote interpolation。

6. Projectile 命中可信度完成第一轮闭环。
   - 后端 projectile 使用 swept/segment 命中。
   - 后端记录 projectile terminal reason/position。
   - 前端 terminal VFX 使用 server terminal truth。
   - 诊断能区分 hit / obstacle / world / ttl。

7. HUD 与战场视觉完成第一轮升级。
   - HUD 收紧，减少遮挡。
   - 金属竞技场风格初步建立。
   - 角色轮廓、底盘、世界血条、武器朝向 cue 已增强。
   - 大障碍物补了底边/侧边，减少“看起来能走但实际不能走”的误导。

8. Smoke/诊断体系增强。
   - MixedMovement 长采样可跑。
   - page-side input event timestamp 已加入。
   - movement 与 fire latency 分开计算。
   - `/battle/commands` fetch probe 已加入。
   - BP-37A 修复了长采样 sample window 截断导致的假 `4416ms` motion latency。
   - BP-38 增加 RAF/Long Task 分相位与 CDP performance delta，确认 9 秒 MixedMovement 输入期 Long Task 为 `0`，剩余风险集中到双客户端混战压力与 VFX/HUD churn。
   - BP-41 增加 `DualClientPressure`，确认 A/B 双端同时移动、换向、瞄准、开火时输入期 Long Task 为 `0`，RAF `>25ms/>40ms` 为 `0/0`，命令 POST 失败为 `0`。
   - BP-42A 收敛手枪本地 tracer：短管曳光 `42px/78ms/alpha 0.32`，关闭手枪侧向 glint，长束效果归档为 `piercing-rail-tracer-long`。
   - BP-42B 降低 VFX 热路径 churn：ring effect 原地更新，新增 `vfxMetric`，主控 MixedMovement smoke 中两端 created/destroyed 对齐且 warnings 为 `0`。
   - BP-43 降低 HUD/minimap canvas 热路径：静态地图层离屏缓存，新增 `hudMetric`，MixedMovement 与 DualClientPressure 中静态层重绘 delta 都为 `0`。
   - BP-44A 建立 battle feel suite：MixedMovement、DualClientPressure、StraightFire 三场景 compact summary 均通过，输入期 RAF、命令失败、HUD static redraw、correction 均未显示新瓶颈。
   - BP-44B 扩展技能压力场景：`SkillPressure` 覆盖移动、移动瞄准、短开火和 Q/E/R 技能连按；suite 四场景通过但暴露 `hardSnapDelta=1` 的技能位移校正问题，因此该票作为诊断扩展接受，不作为手感完成信号。
   - BP-45A 修复 Dash 技能位移 hard snap：新增 Dash 预测 helper，本地 display motion 和 authoritative replay 都按服务端同规则预测 Dash，短 TTL pending dash target 托住未 ack 的旧权威帧；完整 suite 中 `SkillPressure hardA/hardB=0/0`。
   - BP-39A 建立视野/尺度/屏幕速度诊断：基线 zoom `1.32`、worldView 约 `952x476`、MixedMovement 平均屏幕速度约 `261-267px/s`、DualClientPressure A/B 平均约 `337/277px/s`。
   - BP-39B 完成 camera zoom 纯视觉标定：zoom `1.32 -> 1.40`、worldView 约 `897x449`、headful MixedMovement 平均屏幕速度约 `277px/s`，不改 gameplay。
   - BP-40C/BP-40D 闭环真实浏览器新局 HUD 倒计时残余：后端与 localStorage elapsed 接近 0，但旧 HUD 显示 `04:48`；修复后第二局 `timer=04:59`、local `446ms`、backend `458ms`。

## P0 立即计划

| 编号 | 任务 | 目标 | 预计时间 |
| --- | --- | --- | --- |
| BP-40 | 新局倒计时/旧 battle 继承根治 | 已完成：`/battle?new=1`、开始新比赛、结果页再开一局都走 fresh queue request，不复用上一局 active room/battle state | 已完成 |
| BP-40A | 新局新鲜度自动化复现 | 已完成：同账号连续两局 smoke，第一局推进后第二局 battleId 不同且 elapsed 接近 0 | 已完成 |
| BP-40B | 后端队列/room 生命周期审计 | 已完成第一轮：后端 queue participant 增加 `queueRequestId`，same handle 不同 fresh request 不复用 waiting room | 已完成第一轮 |
| BP-40C | 倒计时继承残余复现专项 | 已完成：真实浏览器 smoke 证明后端 elapsed/localStorage 都是新的，错误来自 HUD 显示时钟污染，不是 queue/battleId 继承 | 已完成 |
| BP-40D | Shared authoritative HUD 时钟修复 | 已完成：`GameScene` 在 shared authoritative runtime 下不再用 Phaser scene-local `time` 覆盖 `snapshot.elapsedMs`，HUD timer 对齐 backend authoritative elapsed | 已完成 |

BP-40D 主控验收结果：`npm run build` 通过；`npm run demo:bp40-freshness` 通过，round2 elapsed `32ms`；`scripts/bp40-browser-session-freshness-smoke.ps1` 通过，第二局 `timer=04:59`、local elapsed `446ms`、backend elapsed `458ms`；短 MixedMovement smoke 通过，motion/muzzle local feedback `8ms/8ms`。如果用户仍复现 03:xx，应先排查旧浏览器 tab、旧 bundle 或未刷新入口。

## BattlePage 渲染与手感计划

| 编号 | 任务 | 目标 | 预计时间 |
| --- | --- | --- | --- |
| BP-38 | startup/load 与 gameplay input 负载分层 | 已完成：输入期 Long Task 为 `0`，clientB 输入期仅有 `7` 个 25-33ms 级 RAF 小抖动，无 >40ms gameplay 帧 | 已完成 |
| BP-41 | 真实双客户端压力场景 | 已完成：A/B 两端都移动、换向、瞄准、开火；输入期 Long Task 为 `0`，RAF `>25ms/>40ms=0/0`，命令 POST 失败为 `0` | 已完成 |
| BP-42A | 手枪 tracer 收敛与长束特效归档 | 已完成：手枪改短、关闭侧向 glint、长束特效命名为 `piercing-rail-tracer-long` 并保留给未来穿透/狙击武器 | 已完成 |
| BP-42B | VFX churn 降噪第一轮 | 已完成：ring effect 原地 TTL 更新与数组压缩，新增 `vfxMetric` 观测 active/slot/ring/create/destroy/peak，MixedMovement smoke warnings 为 `0` | 已完成 |
| BP-39A | 视野/尺度/屏幕速度参照诊断 | 已完成：采集 zoom、camera worldView、hero screen speed、look-ahead offset 并接入 suite；MixedMovement 平均约 `261-267px/s`，DualClientPressure A/B 平均约 `337/277px/s` | 已完成 |
| BP-39B | 单变量视觉速度/视野标定 | 已完成：camera zoom `1.32 -> 1.40`，worldView 约 `897x449`；headful MixedMovement correction `0/0`，保留一次 suite soft correction 噪声观察项 | 已完成 |
| BP-42C | VFX churn 第二轮条件票 | 仅当 `vfxMetric` 或实机 GC 感继续指向 VFX 时，再做 transient/tween 池化或更细粒度上限策略 | 90-180 分钟 |
| BP-43 | HUD/minimap 更新节流复核 | 已完成：minimap 静态层离屏缓存，`hudMetric` 接入 smoke；MixedMovement/DualClientPressure 静态层重绘 delta 均为 `0` | 已完成 |
| BP-44A | battle feel suite | 已完成：MixedMovement、DualClientPressure、StraightFire 聚合为 compact summary，作为后续手感回归基线 | 已完成 |
| BP-44B | battle feel 实机复核包扩展 | 已完成第一轮：新增 `SkillPressure` 技能压力场景并接入 suite；诊断发现技能位移会触发 hard snap，因此进入 BP-45A 修复 | 已完成第一轮 |
| BP-45A | 技能位移 hard snap 根因与修复 | 已完成：Dash 本地 display prediction 与 authoritative replay 纳入同规则位移预测；完整 suite 中 SkillPressure hard snap 降为 `0` | 已完成 |
| BP-44C | SkillPressure fire/muzzle 测试口径修正 | 当前 SkillPressure 的 fire event probe 读取过早，muzzle latency 会回退到 first keydown；需把 fire/skill 输入基准改成输入窗口后读取或独立事件通道 | 45-90 分钟 |

阶段目标：真实窗口下输入到本地反馈稳定 1 帧内，输入期 RAF p95 接近 16.8ms，无 >40ms gameplay 帧；用户主观不再觉得粘、慢、卡。

## 命中可信度与战斗语义计划

| 编号 | 任务 | 目标 | 预计时间 |
| --- | --- | --- | --- |
| BP-33F | disputed terminal 复现 | 如果再次出现“子弹碰到身体但没扣血”，用诊断样本定位是显示错觉、插值差、还是后端补偿不足 | 60-120 分钟 |
| BP-45 | shooter advantage / lag compensation 审计 | 只在诊断证明必要时做，不凭感觉扩大命中半径 | 90-180 分钟 |
| BP-46 | weapon feel pass | 手枪、冲锋枪、霰弹枪的后坐力、弹道长度、换弹反馈、命中反馈分开调，不混改伤害 | 120-240 分钟 |
| BP-47 | skill prediction pass | Dash/Blink/Freeze 的完整本地预演、失败回滚、冷却显示和服务器 outcome 对齐；BP-45A 已处理 Dash hard snap 的最小闭环 | 120-240 分钟 |

阶段目标：玩家看到的命中/未命中与服务器结果一致；VFX 可以炫，但不能撒谎。

## 视觉与素材计划

| 编号 | 任务 | 目标 | 预计时间 |
| --- | --- | --- | --- |
| ART-01 | 金属竞技场 + 空洞骑士式角色方向定稿 | 形成角色、掩体、地板、HUD、武器、技能 VFX 的统一视觉语言 | 90-180 分钟 |
| ART-02 | 箱子/障碍物 skin 重做 | 保留碰撞语义，换掉当前临时边框不够好看的箱子视觉 | 60-150 分钟 |
| ART-03 | 角色 sprite / silhouette pass | 提升角色圆润度、阵营识别、朝向、受击可读性 | 120-300 分钟 |
| ART-04 | 技能与武器 VFX pass | 冲刺、冰爆、闪现、枪口、弹道、命中特效统一成高辨识风格 | 180-360 分钟 |
| ART-05 | 主页/大厅视觉产品化 | 参考用户给的金属主界面方向，但先不抢 BattlePage P0 优先级 | 240-480 分钟 |

阶段目标：素材服务于手感、判定和可读性；不让美术覆盖真实问题。

## Bot SDK 计划

| 编号 | 任务 | 目标 | 预计时间 |
| --- | --- | --- | --- |
| BOT-01 | bot brain 接口设计 | 定义 snapshot/profile -> intent/command 的纯逻辑接口，禁止依赖 Phaser/DOM/后端写入 | 45-90 分钟 |
| BOT-02 | scaffold 与示例 bot | 给外部贡献者一个可独立编辑的 bot 策略模块和测试样例 | 90-180 分钟 |
| BOT-03 | bot vs authoritative runtime 测试 | 确保 bot 只通过正式 command 输入，不绕过服务器真相 | 90-180 分钟 |
| BOT-04 | bot 行为分层 | 巡航、寻敌、躲弹、拾取、换弹、技能释放分层，方便 Plus 贡献者只写策略 | 180-360 分钟 |

阶段目标：朋友可以贡献 bot 策略，但不会污染 Phaser、后端或 battle runtime 边界。

## Battle 结果闭环与外围系统计划

| 编号 | 任务 | 目标 | 预计时间 |
| --- | --- | --- | --- |
| SYS-01 | result/replay 稳定性复核 | 确保 authoritative finish 后 result/replay ready 不丢、不重复、不乱码 | 90-180 分钟 |
| SYS-02 | rating 闭环 | 战斗结果进入 rating，profile 能看到可信历史 | 120-240 分钟 |
| SYS-03 | mails 奖励闭环 | 战斗奖励、系统邮件、领取状态收口 | 120-240 分钟 |
| SYS-04 | profile 展示 | 玩家战绩、回放、评分、头像/皮肤统一展示 | 120-240 分钟 |
| SYS-05 | forum/discussion | 战斗外社区功能产品化，晚于 BattlePage 核心体验 | 240-480 分钟 |
| SYS-06 | admin | 管理入口、数据审计、调试面板，晚于核心玩法 | 240-480 分钟 |

阶段目标：外围系统不抢 battle 本体优先级；battle 好玩之后再收口产品闭环。

## 账号与数据一致性 waiting list

这些问题重要，但排在 BattlePage 渲染/手感和 BP-40 新局新鲜度之后处理。

| 编号 | 任务 | 目标 | 预计时间 |
| --- | --- | --- | --- |
| ID-01 | 多账号/异地登录身份模型 | 明确允许同一真人多账号登录，但同一 battle 内不能让同一主体占多个席位；需要 sessionToken/deviceId/handle 的规则 | 120-240 分钟 |
| ID-02 | 同人双账号占位防护 | 队列层增加同设备/同 session/同账号族的占位策略，至少在 ranked battle 禁止一人多席 | 120-240 分钟 |
| ID-03 | Visitor 虚拟账号隔离 | 访客应是临时身份；确认是否允许写入 rating/replay/profile。默认策略应是不进入正式 rating 榜，或迁移成正式账号后再入库 | 90-180 分钟 |
| ID-04 | Rating 串号/曲线异常审计 | 用户截图显示 rating 曲线中段疑似混入其他账号数据，需要检查 result owner、handle normalization、battleId 关联和缓存读取 | 120-240 分钟 |
| ID-05 | Rating 原子更新 | 防止同一 battle 重复投影、多个玩家结果交叉覆盖、刷新/重放导致重复加分 | 180-360 分钟 |
| ID-06 | Profile/Rating cache policy | 明确前端缓存、后端列表、文件存储的 invalidation 规则，避免旧账号数据展示到新账号 | 90-180 分钟 |

当前观察：

- Rating 页面出现 `Visitor` 数据不符合“访客为虚拟账号”的直觉，应后续确认产品规则。
- 玩家档案的 rating 曲线中段疑似出现异常下跌/串号，优先怀疑 result/rating 存储或查询按 handle/battleId 关联不严。
- 这些问题不要在 BP-41 中混改，避免真实双客户端渲染压力和数据一致性问题无法归因。

## 微服务与类型安全计划

| 编号 | 任务 | 目标 | 预计时间 |
| --- | --- | --- | --- |
| API-01 | battle API contract 收口 | 前后端 DTO、错误码、状态枚举、nullable 字段统一 | 120-240 分钟 |
| API-02 | typed client 生成/校验 | 减少手写解析偏差，强化类型安全 | 180-360 分钟 |
| API-03 | backend service boundary | queue、battle、result、replay、rating、mails 分清服务边界 | 240-480 分钟 |
| API-04 | persistence 策略 | 从当前文件/内存 demo 逐步走向可恢复、可迁移的数据层 | 360-720 分钟 |

阶段目标：声明式、类型安全、前后端 API 对齐；但不在 BattlePage P0 未稳定前大范围重构。

## 总体时间预估

- BattlePage 达到稳定可玩、倒计时不继承、输入/渲染主要问题闭环：约 6-12 小时有效 Codex 时间。
- BattlePage 达到接近用户参考图方向的一轮完整视觉与 VFX 质量：约 1.5-3 天有效 Codex 时间。
- Bot SDK 第一版可交给外部贡献者：约 0.5-1 天有效 Codex 时间。
- result/replay/rating/profile/mails/forum/admin 全部产品化收口：约 4-8 天有效 Codex 时间。
- 如果要求“英雄联盟/王者荣耀级别丝滑 + 完整美术资产 + 完整产品闭环”，需要持续多轮实机反馈，预计 1-2 周有效工程推进更现实。

## 当前执行顺序

1. 当前 BP-40C/BP-40D 已验收，倒计时残余从 open bug 降级为实机观察。
2. 当前 BP-42B 已验收，VFX ring 热路径 churn 和可观测性完成第一轮收口。
3. 当前 BP-43 已验收，HUD/minimap 静态层缓存和 `hudMetric` 完成第一轮收口。
4. 当前 BP-44A 已验收，battle feel suite 成为后续回归基线。
5. 当前 BP-39A 已验收，屏幕速度/视野尺度已可量化。
6. 当前 BP-39B 已验收，camera zoom `1.40` 作为纯视觉标定进入实机观察。
7. 当前 BP-44B 已完成第一轮，`SkillPressure` 已纳入 suite 并暴露技能位移 hard snap。
8. 当前 BP-45A 已验收，完整 suite 中 `SkillPressure hardSnap=0`；`npm run build` 通过，仅保留既有 Vite/chunk 警告。
9. 下一步执行 BP-44C，修正 `SkillPressure` fire/muzzle 探针读取时机，避免 muzzle latency 回退到 first keydown。
10. BattlePage 核心稳定后，再进入 bot SDK、素材精修、账号/rating 数据一致性和外围系统。
