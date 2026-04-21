# HIGH_RISK_MAINCHAIN_PROTOCOL

## 1. 文档目的

本文件定义 `GS-09 ~ GS-12` 的施工协议、回归标准和止损规则。

它的目标不是鼓励继续快拆，而是确保在进入 battle 主链时：

- 不破坏现有 battle 手感
- 不把结构拆分和玩法变更混在同一票
- 不再允许低风险阶段那种自动连续推进方式
- 每张高风险票都必须小步、可回退、可审计

---

## 2. 为什么 GS-09 ~ GS-12 属于高风险主链

### 2.1 GS-09：Movement Controller

它会触碰：

- `updatePlayerMovement()`
- sprint 节奏
- `lastMoveDirection`
- 玩家与 physics actor 的移动同步

高风险原因：

- movement 是最直接的主观手感来源
- 任何速度、加速度、输入采样顺序、stamina 消耗时机变化，都会立刻改变 battle 体验
- 它还会间接影响 jump / dash / blink 的后续落点与方向语义

### 2.2 GS-10：Motion Destination Helper

它会触碰：

- `findDashDestination()`
- 距离截断
- 障碍碰撞修正
- dash / blink / jump 的目标点合法性

高风险原因：

- 这是位移技能的几何语义核心
- 一旦目标点求解规则变了，玩家会立即感知“位移不对劲”
- 即便 build 通过，也可能出现“手感变了但代码看起来正确”的情况

### 2.3 GS-11：Weapon State Controller

它会触碰：

- weapon switch
- reload
- depletion
- ammo / reserve / heat / cooldown / overheat

高风险原因：

- 它是 fire gating 的前置层
- 虽然还不直接处理 hit / damage，但它决定“何时能开火、何时不能开火”
- 很容易引入 reload 时序、切枪时序、heat 恢复节奏变化

### 2.4 GS-12：Projectile Spawn Helper

它会触碰：

- `spawnProjectile()`
- projectile 初始位置
- projectile 初始速度
- spread / angle / spawn offset

高风险原因：

- 这是 combat 主链入口
- 即使不改 hit / damage，单改 projectile 初始条件也会改变命中率、散布、节奏和武器手感
- 它是进入 hit / damage 主链前最后一道边界

### 2.5 为什么 session / result pipeline 也属于高风险主链

虽然不在本轮 GS-09 ~ GS-12 中直接施工，但它与下列逻辑强耦合：

- death order
- score
- respawn
- match timer
- battle result

这些逻辑一旦与 movement / weapon / projectile 链一起被误碰，就不再属于“局部重构”，而是战斗闭环主链改动。

结论：

- `GS-09 ~ GS-12` 不再属于低风险自动拆分区
- 从这里开始，每张票都必须当作 battle 主链票处理

---

## 3. 各票目标与边界

### 3.1 GS-09：Movement Controller

负责什么：

- 抽出 `updatePlayerMovement()` 中的基础移动推进
- 抽出 sprint 与 stamina 的纯推进部分
- 抽出 `lastMoveDirection` 更新逻辑

不负责什么：

- `startPlayerMotion()`
- jump / blink / dash tween 本身
- `findDashDestination()`
- camera 跟随与偏移

允许修改文件：

- `src/scenes/GameScene.ts`
- `src/domain/types.ts`
- 新增 `src/features/battle/runtime-local/movement/movementController.ts`

禁止修改文件：

- `src/features/battle/input/**`
- `src/features/battle/presenters/**`
- `src/features/battle/runtime-local/projectiles/**`
- `src/features/battle/runtime-local/weapons/**`
- `src/game/constants.ts`

依赖的既有边界：

- GS-03 input mapper
- GS-07 timers helper

最容易被破坏的手感点：

- WASD 响应性
- sprint 起停节奏
- stamina 消耗 / 恢复时机
- 移动方向与角色 facing 的一致性

### 3.2 GS-10：Motion Destination Helper

负责什么：

- 抽出 `findDashDestination()` 的几何求解逻辑
- 抽出位移目标点合法性判断

不负责什么：

- `startPlayerMotion()`
- jump / dash / blink 的触发规则
- cooldown
- 动画和特效

允许修改文件：

- `src/scenes/GameScene.ts`
- 新增 `src/features/battle/runtime-local/movement/motionDestination.ts`

禁止修改文件：

- `src/features/battle/runtime-local/weapons/**`
- `src/features/battle/runtime-local/projectiles/**`
- `src/features/battle/presenters/**`
- `src/main.ts`

依赖的既有边界：

- GS-09 movement controller 如果已完成，应与其解耦
- pickup / obstacle bounds 只读消费

最容易被破坏的手感点：

- jump 距离
- blink 合法落点
- dash 被墙截断的感觉
- 位移失败提示是否仍符合原逻辑

### 3.3 GS-11：Weapon State Controller

负责什么：

- 抽出 weapon switch / reload / depletion / ammo / heat / cooldown / overheat 的状态推进
- 保持 `handleWeaponFireAction()` 的 gating 语义一致

不负责什么：

- projectile spawn
- hit / damage / kill
- recoil / knockback
- HUD 样式

允许修改文件：

- `src/scenes/GameScene.ts`
- `src/domain/types.ts`
- `src/game/weapons.ts`
- 新增 `src/features/battle/runtime-local/weapons/weaponController.ts`

禁止修改文件：

- `src/features/battle/runtime-local/projectiles/**`
- `src/features/battle/runtime-local/movement/**`
- `src/features/battle/presenters/**`
- `src/game/skills.ts`

依赖的既有边界：

- GS-07 timers helper
- GS-04 wheel switch adapter

最容易被破坏的手感点：

- 切枪延迟
- reload 启动与完成时机
- 弹药耗尽提示
- Gatling heat / overheat 节奏

### 3.4 GS-12：Projectile Spawn Helper

负责什么：

- 抽出 `spawnProjectile()` 的创建逻辑
- 保持 projectile 初始位置、速度、角度、寿命与武器定义一致

不负责什么：

- `updateProjectiles()`
- `onProjectileHit()`
- `applyDamage()`
- `explodeRocket()`

允许修改文件：

- `src/scenes/GameScene.ts`
- `src/domain/types.ts`
- 新增 `src/features/battle/runtime-local/projectiles/projectileFactory.ts`

禁止修改文件：

- `src/features/battle/runtime-local/weapons/**`
- `src/features/battle/runtime-local/movement/**`
- `src/features/battle/presenters/**`
- `src/main.ts`

依赖的既有边界：

- GS-11 weapon state controller 如果已完成，应只消费其结果，不重写 weapon gating

最容易被破坏的手感点：

- 手枪初速与手感
- 霰弹枪散布
- 火箭炮初始偏移和速度
- 加特林子弹连续性

---

## 4. 回归检查清单

每张高风险票结束后，至少执行以下检查：

### 4.1 构建与类型

- `npm run build` 通过
- `tsc` 通过
- 不新增 TypeScript `any` 污染主链

### 4.2 movement 手感

- WASD 方向正确
- 斜向移动速度无意外变化
- Shift sprint 仍按原节奏工作
- stamina 消耗与恢复仍与原行为一致

### 4.3 dash / blink / jump 语义

- dash 仍朝原定方向执行
- blink 目标判定仍一致
- jump 冷却、起跳、落地语义不变
- motion tween 的进入 / 退出条件不变

### 4.4 weapon switch / reload / fire 语义

- 滚轮切枪仍有效
- 切枪条仍正确显示
- reload 自动触发 / 手动触发语义不变
- ammo / reserve / heat / overheat 显示值仍一致

### 4.5 projectile 命中反馈

- 子弹生成位置不偏移
- 霰弹枪 pellet 数量与散布不变
- 火箭炮生成、飞行、爆炸触发条件不变
- Gatling 连射节奏不变

### 4.6 damage / kill / respawn

- 看起来命中时仍稳定扣血
- 击杀后分数仍增加
- 死亡与复活循环不变

### 4.7 camera / pointer offset / occlusion

- camera 跟随仍稳定
- pointer offset 不漂移
- 墙体 / 树体遮挡透明仍正常

### 4.8 HUD 关键数据

- HP / stamina 正常
- 当前武器与弹药 / heat 正常
- skill cooldown 正常
- minimap、feed、leaderboard 不丢字段

---

## 5. 止损规则

### 5.1 立刻停止

出现以下任一项，必须立即停止：

- `npm run build` 失败
- typecheck 失败
- 输入语义不确定
- movement 手感明显变化
- dash / blink / jump 语义疑似变化
- projectile 看起来生成了但轨迹异常
- 命中稳定性下降

### 5.2 必须回退

出现以下任一项，应默认回退当前票：

- 改动超出本票允许文件范围
- 把结构抽离和玩法调整混在同一票
- 需要额外补丁才能勉强恢复原行为
- build 虽过但 battle 手感无法证明一致

### 5.3 必须人工确认

出现以下情况，必须由你人工确认：

- 票据执行需要扩展允许文件范围
- 票据需要同时动 movement 和 weapon
- 票据需要碰 `startPlayerMotion()`
- 票据需要碰 `updateProjectiles()`、`onProjectileHit()`、`applyDamage()`
- 票据需要改变 constants / map / layout 才能继续

### 5.4 不得继续自动推进

从进入 `GS-09` 开始：

- 不得连续自动推进多张主链票
- 不得在单张票后直接自动跳到下一张
- 每张高风险票都必须单独审计、单独汇报、单独裁定

---

## 6. 施工顺序建议

建议顺序：

1. `GS-09：Movement Controller`
2. `GS-10：Motion Destination Helper`
3. `GS-11：Weapon State Controller`
4. `GS-12：Projectile Spawn Helper`

为什么这样排：

- 先拆 movement 基础层，再拆位移落点 helper，顺序更自然
- weapon state controller 先于 projectile spawn，有助于把 fire gating 先稳定下来
- projectile spawn 最后做，避免过早接近 hit / damage 主链

哪些票之间不能并行：

- `GS-09` 和 `GS-10` 不能并行
- `GS-11` 和 `GS-12` 不能并行
- 高风险阶段所有票都不建议并行

---

## 7. 进入高风险阶段前的执行原则

- 每张票一次只派一个子 agent
- 每张票完成后必须由总审核官亲自复核
- 每张票必须单独记录：
  - 改动文件
  - 语义是否保持一致
  - build / typecheck 状态
  - 是否建议 merge
- 不得因为前一张票“看起来没问题”就自动进入下一张

---

## 8. 结论

低风险自动拆分阶段已经基本完成。

从 `GS-09` 开始，施工目标不再是“继续削薄 scene”这么简单，而是：

- 在不破坏 battle 手感的前提下
- 小步收编 movement / weapon / projectile 这些主链边界
- 每张票都按主链票审计

因此：

- 现在不应恢复自动推进
- 现在应先按本协议进入人工把关的高风险施工阶段
