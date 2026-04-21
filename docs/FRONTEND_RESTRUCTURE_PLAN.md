# FRONTEND_RESTRUCTURE_PLAN

## 1. 目标

本文件规划新的前端结构，但本轮不执行代码迁移。

目标：

- 保留当前 battle 表现资产
- 形成可接入未来 typed contracts 的前端骨架
- 让 `/battle` 成为未来系统中的一个 feature，而不是整个应用本身

## 2. 新前端总体结构

建议未来前端结构：

```text
frontend/
  src/
    app/
      bootstrap/
      router/
      providers/
      layout/

    pages/
      home/
      register/
      login/
      loadout/
      battle/
      replay/
      replay-detail/
      discussion/
      discussion-detail/
      rating/
      contribution/
      profile/
      mails/

    features/
      battle/
        contracts/
        adapters/
        renderer/
        runtime-local/
        presenters/
        state/
        ui/
        hooks/

      replay/
      forum/
      mails/
      rating/
      contribution/
      profile/
      identity/

    shared/
      contracts/
      components/
      ui/
      state/
      utils/
      types/

    assets/
```

注意：

- 当前 `slay-demo` 还没有必要物理改成这个目录
- 但后续子 agent 必须按这个规划收编，而不是继续平铺到 `src/`

## 3. 当前仓库到未来结构的映射

### 3.1 可以原地保留并迁移的文件

| 当前文件 | 未来位置 | 说明 |
|---|---|---|
| `src/scenes/GameScene.ts` | `features/battle/renderer/GameScene.ts` | Phaser battle renderer 主资产 |
| `src/ui/Hud.ts` | `features/battle/ui/Hud.ts` | DOM HUD renderer |
| `src/domain/types.ts` | 拆分到 `features/battle/contracts/local/` 与 `shared/contracts/` | 不能原样整体搬运 |
| `src/game/weapons.ts` | `features/battle/runtime-local/weapons/weaponCatalog.ts` | 武器配置与本地模拟规则 |
| `src/game/skills.ts` | `features/battle/runtime-local/skills/skillCatalog.ts` | 技能配置 |
| `src/game/spawn.ts` | `features/battle/runtime-local/map/spawnConfig.ts` | 出生点与资源点配置 |
| `src/game/constants.ts` | 拆分为 `renderer/config` + `runtime-local/config` + `map/layout` | 当前混杂度太高 |
| `src/main.ts` | `app/bootstrap/gameEntry.ts` 或 `pages/battle/entry.ts` | 当前只是单页面引导 |

### 3.2 应新建但当前不存在的层

- `features/battle/adapters/`
- `features/battle/presenters/`
- `features/battle/state/`
- `features/battle/contracts/`
- `pages/battle/`
- `shared/contracts/`
- `shared/components/`
- `shared/state/`

## 4. 新页面树与目录映射

依据 `COMMANDER.md`，页面树建议如下：

```text
pages/
  home/
    HomePage.tsx

  register/
    RegisterPage.tsx

  login/
    LoginPage.tsx

  loadout/
    LoadoutPage.tsx

  battle/
    BattlePage.tsx
    BattlePageShell.tsx
    useBattleSession.ts

  replay/
    ReplayLibraryPage.tsx

  replay-detail/
    ReplayDetailPage.tsx

  discussion/
    DiscussionListPage.tsx

  discussion-detail/
    DiscussionDetailPage.tsx

  rating/
    RatingPage.tsx

  contribution/
    ContributionPage.tsx

  profile/
    ProfilePage.tsx

  mails/
    MailsPage.tsx
```

说明：

- 当前 battle 前端最终只会挂接在 `pages/battle/`
- 其他页面未来全部在 app shell 中承载，不应再塞进 battle scene

## 5. battle 页面如何挂接未来系统

未来 `/battle` 页面建议由四层组成：

```text
BattlePage
  -> BattlePageShell
    -> Queue / Loading / Result UI
    -> BattleSessionController
      -> BattleClientAdapter
      -> GameSceneBridge
        -> Phaser GameScene
        -> Hud
```

### 5.1 BattlePageShell 负责

- 匹配弹窗
- 连接状态
- 进入战斗动画
- 战斗结束回收
- result return hook

### 5.2 BattleClientAdapter 负责

- 调后端 battle API
- 建立 websocket / event stream
- 输入命令发送
- 接收 snapshot DTO

### 5.3 GameSceneBridge 负责

- 把 typed snapshot 注入 Phaser scene
- 把本地输入采样转为 typed command
- 不让 scene 直接依赖网络

### 5.4 GameScene 负责

- 渲染
- camera
- 特效
- 本地视觉反馈
- DOM HUD 驱动

## 6. battle 收编方案

### 阶段 1：包一层，不动 battle 手感

- 先保留当前 `GameScene`
- 从外层加入 page shell 和 adapter
- 让 battle 依旧能本地模拟运行

### 阶段 2：抽 presenter

- 把 `updateHud()` 里的 view model 拼接抽走
- scene 只调用 presenter 输出的 HUD state

### 阶段 3：抽 local runtime

- 把开火、命中、复活、pickup、技能结算从 scene 抽到 `runtime-local`
- scene 只消费 runtime 输出结果

### 阶段 4：切换真实 snapshot

- 引入 battle typed contracts
- scene/renderer 改为消费后端 snapshot

## 7. typed contracts 的接入方案

建议新增：

```text
src/
  contracts/
    shared/
    battle/
      commands/
      snapshots/
      views/
      events/
```

battle 合同至少包括：

- `BattleSessionId`
- `BattlePhaseView`
- `BattleSnapshotView`
- `HeroView`
- `ProjectileView`
- `PickupView`
- `HudStateView`
- `PlayerCommandRequest`
- `BattleResultView`

迁移原则：

- 当前 `domain/types.ts` 不直接成为正式 contract
- 先复制出 contract DTO，再逐步让 scene 改用 DTO

## 8. 共享组件与共享状态

### 8.1 共享组件

未来需要抽出的共享组件：

- 面板容器
- 排行榜列表
- 计时器
- 空状态与加载状态
- 用户卡片
- 邮件条目
- replay 卡片
- profile 指标条

当前 battle HUD 不应直接成为全局组件库，但其面板视觉可以提炼主题变量。

### 8.2 状态管理

未来至少拆为：

- app shell state
- auth state
- battle session state
- replay list/detail state
- mails state
- rating/contribution state
- profile state

battle 内部再拆：

- renderer local state
- session snapshot state
- input state
- ui overlay state

## 9. 未来 feature 目录落点

### 9.1 replay

- `features/replay/`
- 包含 replay list、detail、timeline、report/suggestion adapter

### 9.2 mails

- `features/mails/`
- 包含 mail list、mail detail、red dot summary

### 9.3 rating

- `features/rating/`
- 包含 leaderboard view、history trend、summary card

### 9.4 contribution

- `features/contribution/`
- 包含 contribution board、admin adjustment history

### 9.5 profile

- `features/profile/`
- 包含 public profile、performance summary、recent matches

### 9.6 discussion

- `features/forum/`
- 包含 thread list、detail、comment、report hook

## 10. 新旧结构的保留策略

### 10.1 保留

- battle renderer 手感
- 素材体系
- DOM HUD 方案
- 现成的武器/技能/地图原型数据

### 10.2 重命名并迁移

- `domain` -> `contracts/local` 或 `runtime-local/model`
- `game` -> `battle/runtime-local` + `battle/renderer/config`
- `scenes` -> `battle/renderer/scenes`
- `ui/Hud.ts` -> `battle/ui/Hud.ts`

### 10.3 不再扩展的方向

- 继续把新页面塞进 Phaser
- 继续在 `GameScene` 里堆系统逻辑
- 继续让 scene 直接维护 battle 全状态

## 11. 近期最合理的重构顺序

1. 新建 battle 文档与资产地图
2. 新建 contracts 占位层
3. 新建 battle page shell 占位层
4. 抽离 HUD presenter
5. 抽离 input adapter
6. 抽离 local runtime helpers
7. 再考虑后端接入

## 12. 本轮规划结论

新的前端结构不应围绕“Phaser 工程”展开，而应围绕“系统页面 + battle feature”展开。

当前 `slay-demo` 应被定义为：

- 新项目 battle feature 的 renderer 原型
- 未来 `/battle` 页面中的表现核心
- typed adapter 的下游消费端

因此，后续任何子 agent 任务都必须围绕“如何收编 battle 资产”来做，而不是继续在当前散装结构里横向追加功能。
