# CHILD_AGENT_TICKETS_V2_GAMESCENE_SPLIT

本文件定义专门用于拆分 `GameScene.ts` 的子 agent tickets。

总原则：

- 每票都必须小
- 每票都要可 build、可回退
- 不允许把“结构重组”和“玩法优化”混在同一票
- 不允许一次性拆完整个 `GameScene`

---

## Ticket GS-01：抽出 HUD Presenter

### 标题

从 `GameScene.updateHud()` 抽出 `hudPresenter`

### 目标

把 HUD view model 拼装从 scene 中抽离，保留现有 DOM HUD 表现不变。

### 允许修改的文件范围

- `src/scenes/GameScene.ts`
- `src/ui/Hud.ts`
- 新增 `src/features/battle/presenters/hudPresenter.ts` 或等价目录

### 禁止修改的范围

- weapon / skill / projectile 规则
- camera
- map
- pickup 逻辑

### 输入契约

- 输入仍然是当前 `GameScene.snapshot` 与本地 UI 状态
- `Hud.ts` 的 `HudState` 结构暂不大改

### 输出契约

- `createHudState(...)` 或等价 presenter 函数
- `GameScene.updateHud()` 只做调用和渲染桥接

### 验收标准

- `npm run build` 通过
- DOM HUD 显示内容与当前一致
- `GameScene.ts` 中 HUD 拼装逻辑明显减少

### battle 手感保护要求

- 不允许改 HUD 文案风格
- 不允许改 HUD 位置
- 不允许改 weapon / skill 状态逻辑

### 风险提示

- presenter 不要依赖 Phaser scene 对象

---

## Ticket GS-02：抽出 Minimap Presenter

### 标题

从 `buildHudMinimap()` 抽出 `minimapPresenter`

### 目标

把 minimap 数据拼装从 scene 中抽出，保留 minimap 可视结果一致。

### 允许修改的文件范围

- `src/scenes/GameScene.ts`
- `src/ui/Hud.ts`
- 新增 `src/features/battle/presenters/minimapPresenter.ts`

### 禁止修改的范围

- camera 行为
- minimap 样式
- battle 规则

### 输入契约

- 输入仍来自当前 world/pickup/hero/obstacle 数据

### 输出契约

- `createMinimapData(...)` 或等价函数

### 验收标准

- minimap 点位、camera rect、obstacle rect 与当前一致
- build 通过

### battle 手感保护要求

- 不得改变 minimap 上玩家/敌人/pickup 可见性规则

### 风险提示

- 不要在这一票里顺手改 minimap 缩放或颜色

---

## Ticket GS-03：抽出 Input Command Mapper

### 标题

拆出本地输入采集与 `PlayerCommand` 映射

### 目标

把 `readPlayerCommand()` 中的输入解释独立成 mapper，为未来 typed input adapter 建立挂点。

### 允许修改的文件范围

- `src/scenes/GameScene.ts`
- `src/domain/types.ts`
- 新增 `src/features/battle/input/playerCommandMapper.ts`

### 禁止修改的范围

- weapon fire 规则
- skill 规则
- wheel 切枪执行逻辑

### 输入契约

- 仍使用 Phaser 当前输入源
- 输出仍是当前 `PlayerCommand`

### 输出契约

- `mapInputToPlayerCommand(...)`

### 验收标准

- build 通过
- WASD、LMB、RMB、Q/E、reload、wheel 相关输入行为不变

### battle 手感保护要求

- 不得改输入触发时机
- 不得改 pointerWorld / aim 的计算逻辑

### 风险提示

- 不要把 wheel 的执行逻辑也一起搬走，只搬映射层

---

## Ticket GS-04：抽出 Wheel Switch Adapter

### 标题

把 Phaser wheel 与 window wheel 双链路收口为单独 adapter

### 目标

让滚轮切枪的事件采集与 scene 主逻辑分开，但保持当前双链路兜底方案。

### 允许修改的文件范围

- `src/main.ts`
- `src/scenes/GameScene.ts`
- 新增 `src/features/battle/input/wheelSwitchAdapter.ts`

### 禁止修改的范围

- weapon switch 数值
- HUD
- damage 逻辑

### 输入契约

- 保留 `game-wheel-switch` 自定义事件方案
- 保留 Phaser `this.input.on("wheel")`

### 输出契约

- 独立 wheel adapter 或 helper

### 验收标准

- 普通滚轮切枪可用
- `Ctrl + 滚轮` 仍阻止浏览器缩放
- build 通过

### battle 手感保护要求

- 切枪条时长不变
- 切枪逻辑顺序不变

### 风险提示

- 不要在这一票里顺手删除调试信息

---

## Ticket GS-05：抽出 Pickup Spawn Resolver

### 标题

拆出 pickup 重生点合法性与重选逻辑

### 目标

把 `resolvePickupSpawnPoint()`、`isPickupSpawnPointAvailable()`、`isPickupSpawnPointValid()` 从 scene 中抽出。

### 允许修改的文件范围

- `src/scenes/GameScene.ts`
- `src/game/spawn.ts`
- 新增 `src/features/battle/runtime-local/pickups/pickupSpawnResolver.ts`

### 禁止修改的范围

- pickup 视觉表现
- pickup 文案
- weapon 数值

### 输入契约

- 输入为 spawn points、obstacle bounds、occludable bounds、world size

### 输出契约

- 独立 spawn resolver 函数

### 验收标准

- pickup 仍然只生成在合法 pad 区域
- build 通过

### battle 手感保护要求

- 不能改变现有 pickup pad 设计意图

### 风险提示

- 不要把随机策略改掉，除非 ticket 明确允许

---

## Ticket GS-06：抽出 Automatic Pickup Controller

### 标题

拆出自动拾取规则控制器

### 目标

把 `handleAutomaticWeaponPickup()` 与 `handleAutomaticItemPickup()` 抽成 pickup controller。

### 允许修改的文件范围

- `src/scenes/GameScene.ts`
- `src/game/weapons.ts`
- `src/domain/types.ts`
- 新增 `src/features/battle/runtime-local/pickups/pickupController.ts`

### 禁止修改的范围

- pickup 视觉层
- HUD 样式
- map

### 输入契约

- 输入为 player、pickup collections、weapon definitions

### 输出契约

- 返回 pickup effect / state mutation 结果

### 验收标准

- 自动拾枪仍正常
- medkit 仍可拾取
- 浮动提示与 feed 行为不变

### battle 手感保护要求

- 不改自动拾取半径
- 不改 medkit 规则

### 风险提示

- 不要把 scene effect 触发也混进 controller

---

## Ticket GS-07：抽出 Hero / Weapon / Skill Timers Helper

### 标题

拆出 hero timers 与 weapon switch timers

### 目标

把 `updateHeroStateTimers()` 中非渲染逻辑抽出到 helper。

### 允许修改的文件范围

- `src/scenes/GameScene.ts`
- `src/domain/types.ts`
- 新增 `src/features/battle/runtime-local/clock/heroTimers.ts`

### 禁止修改的范围

- projectile 更新
- combat 逻辑
- camera

### 输入契约

- 输入仍是当前 hero/weapon/skill state

### 输出契约

- 独立 `tickHeroTimers(...)` / `tickWeaponSwitch(...)`

### 验收标准

- reload、cooldown、overheat、jump cooldown 行为不变
- build 通过

### battle 手感保护要求

- 数值和时序保持不变

### 风险提示

- 切枪完成时索引更新的顺序必须保持一致

---

## Ticket GS-08：抽出 Event Feed Clock

### 标题

拆出 battle feed 事件 TTL 更新

### 目标

把 `updateEvents()` 独立成小模块，建立 future event pipeline 的最小边界。

### 允许修改的文件范围

- `src/scenes/GameScene.ts`
- `src/domain/types.ts`
- 新增 `src/features/battle/runtime-local/clock/eventFeedClock.ts`

### 禁止修改的范围

- feed 呈现样式
- HUD 布局

### 输入契约

- 输入当前 `GameEvent[]`

### 输出契约

- 输出更新后的 `GameEvent[]`

### 验收标准

- feed 衰减与清理行为不变

### battle 手感保护要求

- 不能改变 feed 最多显示条数和衰减节奏

### 风险提示

- 小票，不要扩大成“整个 session 事件系统”

---

## Ticket GS-09：抽出 Movement Controller

### 标题

拆出普通移动、sprint 与 lastMoveDirection 更新

### 目标

先只拆 `updatePlayerMovement()` 中的基础运动逻辑，不动 jump / blink / dash tween。

### 允许修改的文件范围

- `src/scenes/GameScene.ts`
- `src/domain/types.ts`
- 新增 `src/features/battle/runtime-local/movement/movementController.ts`

### 禁止修改的范围

- `startPlayerMotion()`
- jump / dash / blink 具体动画
- camera

### 输入契约

- 输入为 player、command、base speed、stamina 参数

### 输出契约

- 更新后的移动状态

### 验收标准

- WASD 与 sprint 行为不变
- stamina 消耗/恢复不变

### battle 手感保护要求

- 移动速度、加速倍率、体力节奏不变

### 风险提示

- 这是手感区，不能顺手改移动算法

---

## Ticket GS-10：抽出 Motion Destination Helper

### 标题

拆出 `findDashDestination()` 与占位合法性判定 helper

### 目标

把位移落点求解逻辑独立出来，为 jump / dash / blink 共用。

### 允许修改的文件范围

- `src/scenes/GameScene.ts`
- 新增 `src/features/battle/runtime-local/movement/motionDestination.ts`

### 禁止修改的范围

- `startPlayerMotion()`
- jump / dash / blink 触发规则

### 输入契约

- 输入为 position、direction、distance、radius、obstacle bounds、world bounds

### 输出契约

- 合法目标点

### 验收标准

- jump / dash / blink 落点与当前一致

### battle 手感保护要求

- 不能改变“撞墙时截断”的当前行为

### 风险提示

- 这是中高风险区，必须有实玩回归

---

## Ticket GS-11：抽出 Weapon State Controller

### 标题

拆出 weapon switch / reload / depletion 控制器

### 目标

把 weapon state 管理从 scene 里抽离，但先不碰 projectile hit 逻辑。

### 允许修改的文件范围

- `src/scenes/GameScene.ts`
- `src/game/weapons.ts`
- `src/domain/types.ts`
- 新增 `src/features/battle/runtime-local/weapons/weaponController.ts`

### 禁止修改的范围

- projectile hit / damage
- camera
- HUD 样式

### 输入契约

- 输入仍为 hero.weapons / currentWeaponIndex / command

### 输出契约

- weapon state mutation helper

### 验收标准

- reload、switch、ammo、heat 行为不变
- build 通过

### battle 手感保护要求

- 不改切枪时间、不改换弹时间、不改 heat 数值

### 风险提示

- 不要和 projectile spawn 一起拆

---

## Ticket GS-12：抽出 Projectile Spawn Helper

### 标题

把 `spawnProjectile()` 从 scene orchestration 中解耦

### 目标

只抽 projectile 创建，不动命中与伤害结算。

### 允许修改的文件范围

- `src/scenes/GameScene.ts`
- `src/domain/types.ts`
- 新增 `src/features/battle/runtime-local/projectiles/projectileFactory.ts`

### 禁止修改的范围

- `updateProjectiles()`
- `applyDamage()`

### 输入契约

- 输入为 player、weapon definition、angle

### 输出契约

- 新 projectile 对象

### 验收标准

- 手枪/加特林/火箭/霰弹的 spawn 参数不变

### battle 手感保护要求

- projectile 初始偏移、速度、半径不变

### 风险提示

- 不要在这一票里处理命中 bug

---

## Ticket GS-13：抽出 Debug Reporter

### 标题

把 combat / wheel debug 输出隔离到 debug helper

### 目标

把 `debugCombat`、`logHit()`、`logNoDamage()`、部分 wheel debug 输出隔离出去。

### 允许修改的文件范围

- `src/scenes/GameScene.ts`
- `src/ui/Hud.ts`
- 新增 `src/features/battle/debug/combatDebugReporter.ts`

### 禁止修改的范围

- 命中规则
- wheel 执行逻辑
- HUD 样式

### 输入契约

- 默认 battle 运行逻辑保持不变

### 输出契约

- debug helper

### 验收标准

- debug 开关仍可工作
- build 通过

### battle 手感保护要求

- 不得删除当前还用于诊断的关键信息

### 风险提示

- 先隔离，再考虑关闭

---

## Ticket GS-14：legacy 路径标记与隔离

### 标题

对 `legacy*` 方法做显式隔离与注释，不做删除

### 目标

确认 legacy 路径不在主调用链后，把它们集中标记到兼容区。

### 允许修改的文件范围

- `src/scenes/GameScene.ts`
- 新增 `src/features/battle/debug/legacyCompatibility.ts`

### 禁止修改的范围

- battle 主逻辑
- HUD
- 任何 runtime 数值

### 输入契约

- 运行路径必须先核实

### 输出契约

- legacy 方法被集中注释和组织

### 验收标准

- build 通过
- 无行为变化

### battle 手感保护要求

- 不得提前删除 legacy 代码

### 风险提示

- 这是整理票，不是清理票

---

## 建议执行顺序

建议顺序如下：

1. GS-01 HUD Presenter
2. GS-02 Minimap Presenter
3. GS-03 Input Command Mapper
4. GS-05 Pickup Spawn Resolver
5. GS-06 Automatic Pickup Controller
6. GS-07 Hero / Weapon / Skill Timers Helper
7. GS-08 Event Feed Clock
8. GS-09 Movement Controller
9. GS-10 Motion Destination Helper
10. GS-11 Weapon State Controller
11. GS-12 Projectile Spawn Helper
12. GS-13 Debug Reporter
13. GS-14 legacy 路径隔离

原因：

- 先拆 presenter 与低风险 controller
- 再拆本地 runtime 中等风险区
- 暂不直接动 hit/damage/camera 核心

---

## 当前不建议立刻立项的票

以下票现在不应第一批执行：

- 直接拆 `updateProjectiles()`
- 直接拆 `applyDamage()`
- 直接重写 `startPlayerMotion()`
- 直接重做 `configureCamera()` / `updateCameraTarget()`

原因：

- 这些区域最容易破坏当前 battle 手感与公平性
- 应等前面的小边界拆完后再进入

---

## 第一张最值得交给子 agent 的实现票

首推：

`GS-01：抽出 HUD Presenter`

原因：

- 收益高
- 风险低
- 能立刻建立“scene 只调 presenter”的新边界
- 不会先碰战斗手感核心
