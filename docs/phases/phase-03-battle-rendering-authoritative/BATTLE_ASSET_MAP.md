# BATTLE_ASSET_MAP

## 1. 文档目的

本文件定义当前 `slay-demo` battle 前端各模块的真实职责边界，并给出未来迁移方向。

依据：

- 当前仓库真实代码
- `docs/COMMANDER.md` 中第 12 节 “Battle 与现有前端的关系”

核心原则：

- 视觉不先动
- battle renderer 是展示资产
- 先包 typed adapter，再逐步替换 runtime 数据来源

## 2. 当前 battle 模块图

```text
main.ts
  -> Phaser Game
    -> GameScene
      -> 本地 snapshot
      -> 输入读取
      -> camera
      -> 角色 / 子弹 / pickup 渲染
      -> HUD 数据组装
      -> battle 规则推进
      -> combat 结算
      -> 事件 feed
      -> minimap 数据
      -> DOM Hud.update()

Hud.ts
  -> DOM HUD renderer
  -> minimap canvas renderer
```

## 3. 当前模块职责边界

### 3.1 Renderer / Scene

当前对应：

- `src/scenes/GameScene.ts`

当前已承担的职责：

- 地图和世界可视内容创建
- hero / projectile / pickup 可视对象同步
- camera 跟随、偏移、遮挡透明
- 技能圈、落点提示、各种特效
- HUD 视图模型拼装

未来应保留的职责：

- Phaser renderer driver
- world object view sync
- VFX / SFX 触发点
- camera / indicator / afterimage / pulse / shockwave 等表现逻辑

未来应移出的职责：

- battle runtime 主规则推进
- 命中结算与伤害规则
- 复活与得分规则
- 拾取决策
- 输入命令解释的业务语义
- HUD view model 业务拼装

结论：

- `GameScene` 以后应是 battle renderer，不应是 battle service

### 3.2 HUD

当前对应：

- `src/ui/Hud.ts`
- `GameScene.updateHud()`

现状：

- `Hud.ts` 是纯表现层，方向正确
- 但 `GameScene.updateHud()` 仍直接从 battle state 拼接 view model

未来分层应为：

```text
BattleSnapshot / ClientSessionState
  -> hud presenter / selector
  -> HudStateView
  -> Hud.ts
```

建议：

- 保留 `Hud.ts`
- 新增 `battle/presenters/hudPresenter.ts`
- 由 presenter 负责把 typed snapshot 转成 HUD view

### 3.3 输入层

当前对应：

- `handlePointerDown`
- `handleMouseWheel`
- `onGlobalWheelSwitch`
- `readPlayerCommand`

现状：

- 直接从 Phaser / window 读取输入
- 直接拼成 `PlayerCommand`
- scene 中直接决定切枪、闪现、冲刺、开火

未来应拆为：

- input adapter：采集浏览器/Phaser输入
- client command mapper：转成 typed client command
- battle gateway：发给本地模拟器或未来 battle service

### 3.4 状态层

当前对应：

- `GameScene.snapshot`

现状：

- `snapshot` 是 scene 内的全局 battle state
- 包含 heroes、projectiles、pickups、events、worldSize、elapsedMs

问题：

- 这是“运行时状态 + 前端视图状态 + 规则状态”的混合体
- 未来不能直接当后端 contract 使用

未来应拆为至少三层：

1. `BattleSnapshotView`
   前端渲染用快照
2. `BattleSessionState`
   battle runtime 内部状态
3. `BattleContracts`
   前后端传输 DTO / event

### 3.5 页面壳

当前对应：

- `index.html`
- `src/main.ts`

现状：

- 只有 canvas shell 与 hud shell
- 没有 battle 页面外层 session shell

未来应承担：

- queue modal
- connecting / matchmaking / loading / in-session / result 状态切换
- battle 断线 / 结束 / 回收跳转逻辑

## 4. 哪些逻辑应该前移 / 后移

### 4.1 未来应移到 typed contracts 层

这些逻辑不应继续只存在于 `GameScene`：

- `PlayerCommand` 结构定义
- `GameSnapshot` / `Hero` / `Projectile` / `WeaponState` 的公共合同版本
- `BattleOutcome`
- `KillFeedEvent`
- `PickupView`
- `HudStateView`
- `MatchTimerView`

具体建议：

- 当前 `src/domain/types.ts` 留作 battle-prototype local types
- 未来新增 `src/contracts/battle/` 作为正式 typed DTO 层

### 4.2 未来应移到 battle 后端 service

以下逻辑不应长期留在前端本地：

- session 创建与 phase 流转
- bot 决策
- 真正的命中判定与伤害结算
- 击杀顺序与性能统计
- 结算结果生成
- replay timeline 产出
- 评分与战绩事件发射

当前前端中对应方法包括：

- `updateProjectiles()`
- `explodeRocket()`
- `onProjectileHit()`
- `applyDamage()`
- `updateRespawnTimers()`
- `respawnHero()`
- `pushEvent()`

这些以后应成为 battle runtime / battle service 的职责。

### 4.3 未来应保留在前端本地表现层

这些逻辑可以继续保留在 battle renderer：

- camera 跟随与 pointer offset
- occlusion alpha
- pickup bob 动画
- pulse / shockwave / floating text / afterimage
- HUD DOM 渲染
- minimap canvas 绘制
- 局部预测动画与反馈

当前对应方法：

- `configureCamera()`
- `updateCameraTarget()`
- `updateOccludableAlpha()`
- `syncPickupViews()`
- `createPulse()`
- `createShockwave()`
- `createFloatingText()`
- `createAfterimage()`
- `syncIndicators()`

### 4.4 应保留在前端，但通过 adapter 获取

以下内容应继续前端展示，但数据来源未来来自 typed snapshot：

- HUD 当前武器、技能、分数、时间
- leaderboard
- kill feed
- minimap dots
- hero hp/action bar

即：

- “显示”留在前端
- “生成这些状态”以后主要来自后端或 typed adapter

## 5. battle 前端在新项目中的角色定位

基于 `COMMANDER.md`，现有 battle 前端以后应扮演：

### 5.1 Battle Renderer Module

不是整个 battle feature 本身，而是：

- `battle renderer module`
- `battle UX asset bundle`
- `battle scene adapter host`

### 5.2 Battle Page 内核

未来 `/battle` 页面应包含：

- queue modal
- matchmaking status
- session HUD shell
- Phaser battle surface
- result return hook

其中 Phaser 部分正是当前 battle 前端的收编目标。

## 6. future backend 的接口边界

未来 battle 前端与 backend 的边界建议定义为：

### 6.1 输入边界

前端发送：

- `BattleJoinRequest`
- `ClientReadyEvent`
- `PlayerCommandInput`
- `UseSkillCommand`
- `WeaponSwitchCommand`
- `ReloadCommand`

### 6.2 快照边界

后端推送或前端轮询：

- `BattleSnapshotView`
- `MatchStatusView`
- `HudStateView`
- `ResultSummaryView`

### 6.3 生命周期边界

battle 页面壳负责：

- 建立 session
- 绑定 renderer
- 消费 snapshot
- 结束后跳 result / replay / home

### 6.4 前端 adapter 挂点

最适合挂 adapter 的位置：

1. `GameScene.create()` 前后
2. `readPlayerCommand()` 输出位置
3. `updateHud()` 输入位置
4. `syncHeroViews()` / `syncProjectileViews()` / `syncPickupViews()` 前

原则：

- 不直接让后端 DTO 污染 Phaser sprite
- 不直接让 scene 知道 HTTP / WebSocket

## 7. battle 资产的收编次序

建议顺序：

1. 保留 `GameScene` 与 `Hud.ts`
2. 在外层建立 battle page shell
3. 新增 typed battle contracts
4. 新增 battle client adapter
5. 把 HUD 拼装逻辑从 scene 抽到 presenter
6. 把 snapshot 驱动从“scene 内本地模拟”切换到“adapter 提供”
7. 再逐步抽走 combat/runtime 规则

## 8. 当前最重要的 typed adapter 挂点

### 挂点 1：输入适配

从：

- `readPlayerCommand()`

变成：

- `collectLocalInput()`
- `mapToBattleCommand()`

### 挂点 2：快照注入

从：

- scene 内直接修改 `this.snapshot`

变成：

- `BattleSceneAdapter.applySnapshot(snapshotView)`

### 挂点 3：HUD 视图模型

从：

- `updateHud()` 直接拼接

变成：

- `createHudView(snapshotView, localUiState)`

### 挂点 4：特效触发

从：

- 规则结算后直接 `createPulse()` / `flashHero()`

变成：

- 根据 typed combat events 触发表现效果

## 9. 结论

当前 battle 前端最合理的未来定位不是“继续堆功能”，而是：

- 作为 battle renderer 被系统收编
- 作为 `/battle` 页面中的实时表现层
- 作为 typed battle snapshot 的消费端

一旦未来 battle service 接管 runtime，本前端仍然大部分可保留，只需要更换：

- 数据来源
- 规则归属
- 生命周期壳层

这也是当前 battle 前端作为“真资产”最重要的价值所在。
