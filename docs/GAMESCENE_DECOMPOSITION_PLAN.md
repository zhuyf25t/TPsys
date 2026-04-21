# GAMESCENE_DECOMPOSITION_PLAN

## 1. 文档目的

本文件专门针对 [src/scenes/GameScene.ts](/F:/slay-demo/src/scenes/GameScene.ts) 做拆分设计。

本文件不修改代码，只回答三件事：

1. `GameScene.ts` 当前到底承担了哪些职责
2. 它未来应该拆成哪些模块
3. 后续应该按什么顺序、小步拆出去，才能不破坏 battle 手感

约束来源：

- [docs/COMMANDER.md](/F:/slay-demo/docs/COMMANDER.md)
- [docs/FRONTEND_AUDIT.md](/F:/slay-demo/docs/FRONTEND_AUDIT.md)
- [docs/BATTLE_ASSET_MAP.md](/F:/slay-demo/docs/BATTLE_ASSET_MAP.md)
- “battle renderer 是展示资产，要在外部包 typed adapter”

---

## 2. 当前 `GameScene.ts` 的职责分解

当前 `GameScene.ts` 不是单纯的 scene，而是 battle 前端的总控文件。按真实方法划分，当前至少承担 13 组职责。

### 2.1 Scene 生命周期与全局 orchestration

对应方法：

- `constructor()`
- `preload()`
- `create()`
- `update()`

当前负责：

- 加载素材
- 注册输入监听
- 初始化 snapshot
- 创建世界、实体、HUD、camera
- 每帧驱动 battle 全流程

问题：

- 这里既是 scene shell，也是 runtime main loop
- 后续如果不先拆边界，任何新需求都会继续塞进 `update()`

### 2.2 地图构建与 arena 静态内容

对应方法：

- `createArena()`
- `createPatternRect()`
- `createPickupPads()`
- `createArenaDecorations()`
- `createBorderWalls()`
- `createStaticObstacle()`
- `registerOccludable()`

当前负责：

- 世界底图和 global background
- pickup pad 地板
- 树、石块、灌木等装饰
- 外圈墙体和内部障碍物
- 遮挡物可透明化注册

问题：

- 地图布局、视觉铺设、碰撞体创建混在一个类里
- `constants.ts` 的 obstacle layout 直接和 scene 构建强耦合

### 2.3 实体 view 创建

对应方法：

- `createPlayerActor()`
- `createCameraTarget()`
- `createHeroViews()`
- `createPickupViews()`
- `createIndicators()`

当前负责：

- player physics actor
- camera follow target
- hero sprite / name / hp bar / action bar / marker
- weapon pickup / medkit sprite 与 label
- blink 范围圈与目标点

问题：

- actor、render view、ui-like world labels 混在一起创建
- 后续如果要接 typed snapshot，很难单独替换 view sync

### 2.4 HUD 与 screen-space UI 桥接

对应方法：

- `createHud()`
- `layoutHud()`
- `handleResize()`
- `updateHud()`
- `buildHudLeaderboard()`
- `buildHudFeed()`
- `buildHudMinimap()`

当前负责：

- DOM HUD 创建
- resize 重布局
- HUD state 拼装
- minimap 数据拼装
- leaderboard / feed / weapon panel / skill panel / debug lines 拼装

问题：

- `Hud.ts` 已经是纯 renderer，但 `GameScene.updateHud()` 仍承担 presenter 角色
- scene 知道太多 UI 细节

### 2.5 Camera 与局部视野控制

对应方法：

- `configureCamera()`
- `updateCameraTarget()`
- `calculateCameraOffsetByPointer()`
- `updateOccludableAlpha()`

当前负责：

- camera bounds
- zoom
- follow 与 deadzone
- pointer offset
- 墙体/树体视觉遮挡透明

问题：

- 这是高度 battle 手感敏感的区域
- 逻辑应拆，但不能早期大动

### 2.6 输入采集与输入解释

对应方法：

- `createControls()`
- `handlePointerDown()`
- `handleMouseWheel()`
- `onGlobalWheelSwitch()`
- `requestSwitchWeapon()`
- `readPlayerCommand()`

当前负责：

- 键鼠注册
- Phaser / window 双链路 wheel
- 左右键状态采样
- 将本地输入拼成 `PlayerCommand`
- 直接触发切枪请求

问题：

- 同时承担 input collection、input normalization、input intent dispatch
- 未来接后端时，应该有单独的 input adapter / command mapper

### 2.7 Hero 定时器、武器状态、技能状态与局部 session 计时

对应方法：

- `updateHeroStateTimers()`
- `updateEvents()`
- `updateWeaponPickups()`
- `updateRespawnTimers()`
- `respawnHero()`

当前负责：

- weapon cooldown / reload / overheat
- hero jump cooldown
- skill cooldown / active
- feed 事件 ttl
- pickup respawn
- hero respawn
- weapon switch progress

问题：

- 这是典型 runtime 状态推进逻辑，不应长期留在 renderer scene

### 2.8 位置同步与移动

对应方法：

- `syncPlayerHeroFromPhysics()`
- `updatePlayerMovement()`
- `handleJumpAction()`
- `isPlayerMotionActive()`
- `stopPlayerMotion()`
- `startPlayerMotion()`
- `findDashDestination()`
- `setHeroPosition()`

当前负责：

- 从 physics actor 回写 player data
- 玩家普通移动
- sprint
- jump
- dash / blink 动作插值
- move motion tween
- 位移合法性与落点查找

问题：

- 运动学、技能位移、动作 tween、physics 同步耦合在一起
- 这个区域可以拆，但必须保行为一致

### 2.9 拾取系统

对应方法：

- `handleAutomaticWeaponPickup()`
- `handleAutomaticItemPickup()`
- `resolvePickupSpawnPoint()`
- `isPickupSpawnPointAvailable()`
- `isPickupSpawnPointValid()`
- `findNearbyPickup()`
- `findNearbyItemPickup()`

当前负责：

- 自动拾取
- medkit 拾取
- pickup respawn 点重选
- pickup 合法性判定
- proximity 查询

问题：

- pickup policy、pickup inventory effect、pickup scene presentation 混杂
- 是适合早期抽离的纯逻辑区域之一

### 2.10 技能逻辑

对应方法：

- `handleSkillInputs()`
- `isBlinkTargetValid()`

间接受影响的方法：

- `syncIndicators()`
- `startPlayerMotion()`

当前负责：

- Q Blink 准备/取消/释放
- E Dash 立即施放
- cooldown 生效
- 目标合法性
- 技能视觉反馈触发

问题：

- 技能 intent、skill rules、skill VFX 耦合
- 这是未来 typed contracts 与 battle service 的重要边界

### 2.11 武器逻辑

对应方法：

- `handleWeaponSwitchAction()`
- `handleWeaponFireAction()`
- `tryFireWeapon()`
- `getCurrentWeapon()`
- `startReload()`
- `finishReload()`
- `pruneDepletedWeapon()`

当前负责：

- 当前武器选择
- 切枪条
- 开火 gating
- reload
- overheat
- ammo / reserve
- 武器耗尽移除

问题：

- 这块适合拆成独立的 weapon controller
- 但它和 projectile spawn、HUD、recoil、combat 仍高度耦合

### 2.12 投射物、命中、伤害与击杀

对应方法：

- `spawnProjectile()`
- `updateProjectiles()`
- `explodeRocket()`
- `onProjectileHit()`
- `applyDamage()`
- `findHeroHitAlongPath()`
- `getSegmentHitTime()`
- `applyRecoil()`
- `applyKnockback()`

当前负责：

- projectile 生命周期
- 命中检测
- rocket AoE
- 击退
- 统一伤害入口
- 击杀、分数、复活计时触发

问题：

- 这是未来 battle service 的核心逻辑
- 也是当前最容易“一动就破手感/破公平性”的区域

### 2.13 特效、表现同步、文本与 debug/legacy

对应方法：

- `createPulse()`
- `createImpactSpark()`
- `createMuzzleBurst()`
- `createShockwave()`
- `createFloatingText()`
- `showFloatingText()`
- `updateVisualEffects()`
- `syncHeroViews()`
- `syncProjectileViews()`
- `createProjectileView()`
- `syncPickupViews()`
- `flashHero()`
- `createAfterimage()`
- `getWeaponLabel()`
- `getProjectileLabel()`
- `getItemPickupLabel()`
- `logHit()`
- `logNoDamage()`
- `legacyHandleAutomaticPickup()`
- `legacyHandleJump()`
- `legacyHandleWeaponSwitch()`
- `legacyHandleWeaponFire()`

当前负责：

- 所有即时视觉反馈
- world object 和 snapshot 的同步
- combat debug 输出
- left-top debug HUD lines
- 历史遗留逻辑保留

问题：

- 真资产和调试债堆在一起
- 需要拆出表现层 helper，但不能过早删除 legacy 路径

---

## 3. 目标模块图

目标不是把 `GameScene.ts` 消灭，而是把它收缩成 scene shell。

目标模块图：

```text
features/battle/
  renderer/
    scenes/
      GameScene.ts                <- 最终只保留 scene shell 与 glue code
    arena/
      arenaBuilder.ts
      obstacleOcclusion.ts
    camera/
      cameraDirector.ts
    entities/
      heroViewFactory.ts
      pickupViewFactory.ts
      projectileViewFactory.ts
      worldViewSync.ts
    fx/
      effectFactory.ts
      floatingTextLayer.ts
    hud/
      hudBridge.ts
      minimapBridge.ts

  input/
    controlRegistry.ts
    pointerInputAdapter.ts
    wheelSwitchAdapter.ts
    playerCommandMapper.ts

  runtime-local/
    clock/
      heroTimers.ts
      sessionTimers.ts
    movement/
      movementController.ts
      motionController.ts
    weapons/
      weaponController.ts
      reloadController.ts
    skills/
      skillController.ts
    pickups/
      pickupController.ts
      pickupSpawnResolver.ts
    projectiles/
      projectileController.ts
      hitResolver.ts
      damageResolver.ts

  presenters/
    hudPresenter.ts
    leaderboardPresenter.ts
    feedPresenter.ts
    minimapPresenter.ts

  adapters/
    battleStateAdapter.ts
    sceneSnapshotAdapter.ts
    battleContractsMapper.ts

  debug/
    combatDebugReporter.ts
    legacyCompatibility.ts
```

---

## 4. 各模块边界定义

### 4.1 Scene Shell

建议文件：

- `features/battle/renderer/scenes/GameScene.ts`

负责什么：

- Phaser 生命周期
- 组装各 controller / presenter / bridge
- 保持 `create()` / `update()` 的主调度

不负责什么：

- 具体武器规则
- 具体技能规则
- pickup 规则
- HUD 视图模型拼装
- 命中结算细节

与 typed contracts 的关系：

- 只消费 adapter 转好的 scene-ready 数据
- 不直接定义合同

### 4.2 Arena Builder

建议目录：

- `features/battle/renderer/arena/`

负责什么：

- 地图 tile 铺设
- border / obstacle / pickup pad 创建
- occludable 注册

不负责什么：

- pickup 逻辑
- combat 逻辑
- HUD

与 typed contracts 的关系：

- 几乎无直接关系
- 主要消费 map config

### 4.3 Camera Director

建议目录：

- `features/battle/renderer/camera/`

负责什么：

- camera 配置
- follow target 更新
- pointer offset
- deadzone / lerp
- 遮挡透明判定

不负责什么：

- 命中、武器、技能

与 typed contracts 的关系：

- 只依赖 hero position / pointer state 这种本地展示数据

### 4.4 Input Interpreter

建议目录：

- `features/battle/input/`

负责什么：

- 键鼠注册
- Phaser / window 输入采样
- wheel 双链路兜底
- 生成 typed `PlayerCommand`

不负责什么：

- 切枪执行
- 武器开火
- 技能施放

与 typed contracts 的关系：

- 是 future `PlayerCommandRequest` 的前端映射入口

### 4.5 Movement Controller

建议目录：

- `features/battle/runtime-local/movement/`

负责什么：

- 普通移动
- sprint
- last move direction
- jump / dash / blink motion destination 计算
- tween motion 封装

不负责什么：

- 技能 cooldown
- 伤害结算

与 typed contracts 的关系：

- 当前是 local runtime helper
- 未来 battle service 接管后，前端保留 motion rendering 子集

### 4.6 Weapon Controller

建议目录：

- `features/battle/runtime-local/weapons/`

负责什么：

- 切枪请求与切枪条
- 当前武器选择
- reload
- ammo / reserve / heat
- 是否允许开火

不负责什么：

- projectile 命中
- hero 受伤
- HUD 呈现

与 typed contracts 的关系：

- 当前可作为 local runtime
- 未来应映射到 backend battle weapon state

### 4.7 Skill Controller

建议目录：

- `features/battle/runtime-local/skills/`

负责什么：

- Q/E 技能输入解释
- prepared skill 状态
- cooldown gating
- blink target validity

不负责什么：

- 技能动画绘制
- HUD skill panel 呈现

与 typed contracts 的关系：

- 未来会对应 `UseSkillCommand` 与 `SkillStateView`

### 4.8 Pickup Controller

建议目录：

- `features/battle/runtime-local/pickups/`

负责什么：

- 自动拾取判定
- medkit / weapon pickup 效果
- pickup respawn
- spawn point 合法性

不负责什么：

- pickup world sprite 创建
- pickup label 渲染

与 typed contracts 的关系：

- 未来可对应 `PickupView` / `PickupEvent`
- 规则最终应后移到 battle service

### 4.9 Projectile Controller

建议目录：

- `features/battle/runtime-local/projectiles/`

负责什么：

- projectile spawn
- projectile tick
- rocket explode
- projectile expiry

不负责什么：

- HUD
- minimap

与 typed contracts 的关系：

- 未来会映射为 projectile snapshot / combat event

### 4.10 Hit / Damage Resolver

建议目录：

- `features/battle/runtime-local/projectiles/hitResolver.ts`
- `features/battle/runtime-local/projectiles/damageResolver.ts`

负责什么：

- 命中判定
- 重复命中保护
- applyDamage
- 击杀与 score

不负责什么：

- 特效绘制
- HUD 文案

与 typed contracts 的关系：

- 这是未来 battle backend service 的核心迁移目标之一
- 本地实现只应作为过渡 runtime

### 4.11 HUD Presenter Bridge

建议目录：

- `features/battle/presenters/hudPresenter.ts`
- `features/battle/renderer/hud/hudBridge.ts`

负责什么：

- 从 snapshot + local ui state 生成 `HudState`
- scene 调用 `Hud.update()`

不负责什么：

- DOM 创建
- 业务规则推进

与 typed contracts 的关系：

- 是 `BattleSnapshotView` -> `HudStateView` 的第一步桥接层

### 4.12 Minimap Bridge

建议目录：

- `features/battle/presenters/minimapPresenter.ts`
- `features/battle/renderer/hud/minimapBridge.ts`

负责什么：

- minimap 数据缩放与点位视图模型

不负责什么：

- canvas 绘制本身之外的 battle 规则

与 typed contracts 的关系：

- 未来消费 typed snapshot 中的 hero/pickup/object view

### 4.13 Debug / Legacy Cleanup Area

建议目录：

- `features/battle/debug/`

负责什么：

- combat debug logging
- wheel diagnosis
- legacy compatibility wrapper

不负责什么：

- 默认 battle 正常运行逻辑

与 typed contracts 的关系：

- 无直接关系

### 4.14 Bot Controller

现状：

- 当前 bot 基本是静止 targets，并不存在成熟 bot controller

建议：

- 现在不要为 bot controller 造复杂抽象
- 先在规划里留位，不进入第一批拆分

---

## 5. 推荐目录归属

建议未来 battle 相关目录以 `features/battle/` 为中心，而不是继续堆在 `src/game/` 与 `src/scenes/`：

```text
src/
  features/
    battle/
      renderer/
      input/
      runtime-local/
      presenters/
      adapters/
      debug/
```

当前文件迁移方向：

- `src/scenes/GameScene.ts`
  -> `src/features/battle/renderer/scenes/GameScene.ts`
- `src/ui/Hud.ts`
  -> `src/features/battle/renderer/hud/Hud.ts`
- `src/game/weapons.ts`
  -> `src/features/battle/runtime-local/weapons/weaponCatalog.ts`
- `src/game/skills.ts`
  -> `src/features/battle/runtime-local/skills/skillCatalog.ts`
- `src/game/spawn.ts`
  -> `src/features/battle/runtime-local/map/spawnConfig.ts`

---

## 6. 拆分优先级

拆分原则：

- 先抽“纯边界”和“低手感风险”模块
- 后抽“高耦合但可回归验证”的模块
- 最后才动 combat 核心和 camera 手感核心

### 6.1 第一批先拆

第一批建议先拆出 3 组：

1. HUD presenter / minimap presenter
2. input adapter / player command mapper
3. pickup controller / pickup spawn resolver

原因：

- 这些区域边界清楚
- 对 battle 手感影响较小
- 能快速减少 `GameScene` 的职责密度
- 适合建立“scene shell + external helper”模式

### 6.2 第二批再拆

第二批建议拆：

1. weapon controller
2. hero/session timers
3. movement controller
4. effect helper / world view sync helper

原因：

- 这些模块仍在本地 runtime 内，但拆分收益高
- 有较多纯逻辑和中度耦合逻辑，适合小步迁移

### 6.3 第三批再拆

第三批建议拆：

1. projectile controller
2. hit resolver
3. damage resolver
4. skill controller

原因：

- 这是 battle 公平性与手感核心
- 任何改动都可能导致“看起来击中却不扣血”“火箭 AoE 失真”“技能释放时序变了”
- 必须在前两批建立稳定 helper/adapter 模式后再动

### 6.4 暂时不能动或不该先动

以下区域不应作为第一批：

1. camera director
2. projectile/hit/damage
3. `startPlayerMotion()` 相关位移手感核心

原因：

- 它们是 battle 体验的主观手感核心
- 很难只靠静态检查判断是否保持一致
- 必须等周边边界稳定后再拆

---

## 7. 哪些模块未来会对接 typed contracts / backend battle service

### 7.1 强对接 typed contracts 的模块

- input adapter / command mapper
- hud presenter
- minimap presenter
- scene snapshot adapter
- battle state adapter

这些模块未来要消费或输出：

- `PlayerCommandRequest`
- `BattleSnapshotView`
- `HudStateView`
- `BattleResultView`

### 7.2 未来会后移到 backend battle service 的模块

- projectile controller
- hit resolver
- damage resolver
- skill rules
- pickup rules
- respawn / score / battle clock 规则

这些当前可保留 local runtime 版本，但设计时必须避免绑死 Phaser。

### 7.3 长期保留在前端 renderer 的模块

- arena builder
- camera director
- occlusion
- view factories
- world view sync
- fx helpers
- DOM HUD renderer

---

## 8. 风险点

### 8.1 最大风险：拆模块时顺手改行为

禁止把“结构重组”和“玩法调整”混在一票里。

### 8.2 第二风险：过早重写 `update()` 主循环

`update()` 现在虽然很重，但它是 battle 运行顺序的事实来源。
早期拆分应保持调用顺序不变，只把内部逻辑搬出去。

### 8.3 第三风险：把 renderer helper 误做成 runtime service

例如：

- HUD presenter 不应偷偷改 battle state
- camera director 不应参与命中逻辑
- effect helper 不应决定是否造成伤害

### 8.4 第四风险：提前删 legacy

`legacy*` 方法本轮不应删。
应先文档化、确认运行路径、逐步摘除，再删。

### 8.5 第五风险：把 typed contracts 直接等同于当前 `domain/types.ts`

当前本地类型只是过渡，不是最终合同。

---

## 9. battle 手感保护原则

后续所有子 agent 拆分 `GameScene.ts` 时，必须遵守：

1. 先搬逻辑，再谈改行为
2. 每张 ticket 只拆一个小边界
3. 保持 `update()` 主流程顺序不变，除非 ticket 明确允许
4. 不同时修改 camera、combat、movement 三个敏感区
5. 每次拆分后都要可 build、可运行、可回退
6. 如果无法证明行为一致，就不要在同一票里顺手优化

---

## 10. 推荐的首轮拆分路径

最建议的第一个施工顺序：

1. 抽 HUD presenter
2. 抽 minimap presenter
3. 抽 input command mapper
4. 抽 pickup controller
5. 抽 hero/session timer helpers

这样做的好处：

- 先把“scene 既是 presenter 又是 runtime”的问题削掉一层
- 不直接碰 combat 核心
- 为 battle typed adapter 建立明确挂点

---

## 11. 结论

`GameScene.ts` 当前不是“scene 文件太长”这么简单，而是：

- scene shell
- renderer
- runtime
- input interpreter
- HUD presenter
- combat resolver
- pickup/session controller

六类角色叠在一起。

正确目标不是“一次性拆完”，而是：

- 先把低风险边界抽出来
- 把 `GameScene` 缩成总调度 + glue code
- 逐步让 runtime、presenter、adapter 独立

只有这样，battle 前端才能在不失去当前手感的前提下，被未来 typed contracts 和 backend battle service 安全收编。
