# FRONTEND_AUDIT

## 1. 审计范围与结论

本次审计只基于当前 `slay-demo` 仓库真实文件，不假设任何未来代码。

当前仓库本质上是：

- 一个 `Phaser 3 + TypeScript + Vite` 的单页面 battle 前端原型
- 唯一真实页面是 battle 画面
- 目前没有前端路由系统、没有多页面壳层、没有 API 层、没有 typed contracts 层
- 当前最值钱的资产是 `src/scenes/GameScene.ts` 驱动出的 battle 体验

当前仓库已经具备：

- 可运行 battle 场景
- 局部视角 camera
- 地图、障碍物、拾取物、角色、子弹、命中特效
- DOM HUD、小地图、排行榜、状态栏
- 基础数据类型、武器配置、技能配置、出生点配置

当前仓库尚不具备：

- 面向未来系统的前端页面壳
- battle 前后端 typed contract
- scene / renderer / domain / adapter 的明确边界
- 可维护的 feature 目录结构
- 可复用的 battle session 生命周期层

## 2. 当前目录结构

当前仓库顶层目录：

```text
slay-demo/
  docs/
    COMMANDER.md
  public/
    assets/kenney-top-down-shooter/...
  src/
    main.ts
    domain/
      types.ts
    game/
      constants.ts
      skills.ts
      spawn.ts
      weapons.ts
    scenes/
      GameScene.ts
    ui/
      Hud.ts
  index.html
  package.json
  tsconfig.json
  vite.config.ts
  dist/
  node_modules/
```

说明：

- `src/` 只有 8 个业务文件，说明当前是高耦合的单场景原型，而不是系统化前端。
- `public/assets/kenney-top-down-shooter/` 是 battle 视觉素材的核心资源池。
- `dist/` 是构建产物，属于可删除产物，不是资产源。
- `docs/` 目前只有 `COMMANDER.md`，缺少前端资产地图、收编方案和任务拆分文档。

## 3. 入口文件与运行骨架

### 3.1 运行入口

入口文件是 `src/main.ts`。

其职责：

- 初始化浏览器 viewport 锁定
- 注册 `window` 级 wheel / contextmenu 监听
- 构造 Phaser GameConfig
- 以 `GameScene` 作为唯一 scene 启动游戏

这说明当前前端不是“页面应用”，而是“启动后直接进入 battle”。

### 3.2 页面壳

`index.html` 目前只提供：

- `#app` 作为 Phaser canvas 容器
- `#hud-root` 作为 DOM HUD 容器

这是一层极薄的 page shell，但已经是未来 battle page shell 的最小雏形。

## 4. 当前 battle 相关核心文件

### 4.1 核心文件清单

#### `src/scenes/GameScene.ts`

当前 battle 的绝对核心资产。

承担了几乎全部运行时职责：

- 资源 preload
- 世界创建
- 地图与障碍物搭建
- hero / projectile / pickup 渲染
- 输入读取
- 玩家移动
- camera 跟随与 offset
- 自动拾取
- 技能释放
- 武器切换与开火
- projectile 更新与命中结算
- 伤害、击杀、复活
- HUD 数据组装
- minimap 数据组装

结论：

- 这是 battle 体验的主资产
- 也是当前最大的结构债来源

#### `src/ui/Hud.ts`

当前 DOM HUD 资产。

承担：

- HUD DOM 树创建
- HUD CSS 注入
- 左上、右上、左下、右下、顶部中央布局
- 小地图 canvas 绘制
- battle HUD 数据的呈现

结论：

- 这是 battle HUD 表现层资产
- 适合保留为 renderer/ui 基础
- 但不应继续承载 battle 业务拼装逻辑

#### `src/domain/types.ts`

当前 battle 数据类型定义入口。

当前包含：

- `Vec2`
- `Hero`
- `WeaponState`
- `WeaponInventory`
- `WeaponPickup`
- `ItemPickup`
- `Projectile`
- `GameEvent`
- `GameSnapshot`
- `PlayerCommand`

结论：

- 这是未来 typed contracts 过渡层的最重要挂点
- 但当前仍然是“前端本地模拟类型”，不是“跨前后端合同类型”

#### `src/game/weapons.ts`

当前武器定义资产。

承担：

- 武器静态定义
- starter inventory
- weapon state 创建
- weapon refill / depleted 判断
- weapon index 循环

结论：

- 值得保留
- 未来应拆成 `catalog` 与 `runtime rule helpers`

#### `src/game/skills.ts`

当前技能定义资产。

承担：

- 技能静态定义
- 默认技能集合
- 获取 hero 技能状态

结论：

- 值得保留
- 但只是临时配置文件，不是完整 skill module

#### `src/game/spawn.ts`

当前 battle 初始内容配置资产。

承担：

- 初始 hero 定义
- hero visuals
- 初始 weapon pickup / medkit 定义
- hero / pickup spawn points
- respawn 随机选择

结论：

- 值得保留
- 未来应拆分为 `battle seed data` 与 `map spawn config`

#### `src/game/constants.ts`

当前 battle 常量中心。

承担：

- world 尺寸
- camera 与移动常量
- stamina / pickup / respawn / jump / weapon switch 常量
- 材质和素材 key
- arena obstacle 静态布局

结论：

- 值得保留
- 但已经混合了“系统参数”“素材路径”“地图布局”三类东西，未来必须拆开

## 5. HUD / minimap / weapon / skill / timer / result 资产位置

### 5.1 HUD

- DOM HUD renderer: `src/ui/Hud.ts`
- HUD 数据组装: `GameScene.updateHud()`

### 5.2 Minimap

- minimap DOM canvas: `src/ui/Hud.ts`
- minimap 数据组装: `GameScene.buildHudMinimap()`

### 5.3 Weapon 面板与武器状态显示

- 武器配置: `src/game/weapons.ts`
- 当前武器与弹药状态拼装: `GameScene.updateHud()`
- 武器切换与换枪条逻辑: `GameScene.requestSwitchWeapon()`、`updateHeroStateTimers()`、`syncHeroViews()`

### 5.4 Skill 面板与技能状态

- 技能定义: `src/game/skills.ts`
- 技能输入与施放: `GameScene.handleSkillInputs()`
- 技能栏数据拼装: `GameScene.updateHud()`
- 技能指示器: `GameScene.syncIndicators()`

### 5.5 Match Timer

- 时间格式化: `GameScene.formatMatchTime()`
- HUD 输出: `GameScene.updateHud()`

### 5.6 Result

当前没有独立 result 页面或 result modal。

现状：

- 只有击杀、分数、复活、事件 feed
- 没有正式 battle result return hook

结论：

- result 目前是假壳子状态
- 未来必须在 page shell / battle session lifecycle 层补齐

## 6. 依赖库审计

`package.json` 当前只有 1 个运行时依赖：

- `phaser`

开发依赖：

- `typescript`
- `vite`
- `@types/node`

结论：

- 当前 battle 前端极度轻量
- 没有 React/Vue/Svelte 路由系统
- 没有状态库
- 没有 schema validator
- 没有 API client

这既是优点也是风险：

- 优点：battle 体验资产简单直接，收编成本低
- 风险：一旦开始接系统，若没有清晰边界，很容易继续把所有逻辑都堆进 `GameScene`

## 7. 当前 battle 数据流

当前 battle 数据流是单场景内本地闭环：

```text
window / Phaser input
  -> GameScene.readPlayerCommand()
  -> GameScene.updatePlayerMovement() / handleSkillInputs() / handleWeaponFireAction()
  -> GameScene.updateProjectiles() / applyDamage() / pushEvent()
  -> GameScene.syncHeroViews() / syncProjectileViews() / syncPickupViews()
  -> GameScene.updateHud()
  -> Hud.update()
```

核心特征：

- 单一运行时状态源：`GameScene.snapshot`
- 单一 orchestrator：`GameScene.update()`
- `Hud` 不直接读 battle state，只接收组装好的 HUD view data
- 领域类型是纯数据，但运行逻辑几乎全都压在 scene

结论：

- 当前数据流是“原型合理、产品阶段不可持续”的典型结构
- 未来应该保留“scene 作为 renderer driver”，但不能继续让它同时扮演 runtime / planner / adapter / hud presenter

## 8. battle 真资产 / 假壳子 / 技术债

### 8.1 真资产

以下内容是应该优先保留的 battle 真资产：

1. `GameScene` 中成熟的即时战斗反馈
2. DOM HUD + minimap 的叠加方案
3. Kenney 素材选型与 arena 视觉拼装
4. 当前 camera 跟随、局部视野、鼠标 offset 体验
5. 纯数据类型雏形：`Hero` / `Projectile` / `WeaponState` / `GameSnapshot`

### 8.2 假壳子

以下内容是看起来像系统，其实还没有真正成立的壳子：

1. result 流程
2. page shell
3. battle session lifecycle
4. typed API adapter
5. replay / mails / rating / profile / discussion 的前端入口

### 8.3 技术债

当前最明显的技术债：

1. `GameScene.ts` 2480 行，职责过载
2. battle runtime、renderer、HUD presenter、input adapter 混在一个 scene
3. `constants.ts` 同时承担参数表、素材表、地图布局
4. `spawn.ts` 同时承担 seed data、visual mapping、spawn rules
5. 存在多处 `legacy*` 方法，说明逻辑演进未完成，旧路径仍滞留
6. 存在调试链路直接留在 battle 运行路径中，如 wheel/debug combat
7. 部分中文字符串出现编码污染，尤其在 `Hud.ts`、`weapons.ts`、`spawn.ts`

## 9. 保留 / 重构 / 删除建议

### 9.1 直接保留

- `public/assets/kenney-top-down-shooter/`
- `src/ui/Hud.ts` 的 DOM HUD 思路
- `src/domain/types.ts` 的“纯数据对象”方向
- `src/game/weapons.ts`
- `src/game/skills.ts`
- `src/game/spawn.ts` 中的 seed/spawn 数据
- `src/game/constants.ts` 中经过验证的 battle 参数
- `src/scenes/GameScene.ts` 中经过验证的表现逻辑

### 9.2 必须重构

- `src/scenes/GameScene.ts`
- `src/game/constants.ts`
- `src/game/spawn.ts`
- `src/ui/Hud.ts` 与 `GameScene.updateHud()` 的边界
- `src/domain/types.ts` 向 typed contracts 的过渡层

### 9.3 应删除或归档

以下不应该继续作为长期运行路径存在：

- `dist/`
- `GameScene.ts` 中全部 `legacy*` 方法
- 未来调通后移除的 wheel/debug combat 诊断代码
- 非 battle 真资产的临时拼装逻辑

建议：

- 新建 `archive/` 或 `docs/archive/` 记录旧实现取舍
- 不要直接删除未核实逻辑，先从运行路径摘出，再归档

## 10. battle 体验核心资产文件清单

如果只挑未来 battle 收编最值钱的 8 个文件，优先级如下：

1. `src/scenes/GameScene.ts`
2. `src/ui/Hud.ts`
3. `src/domain/types.ts`
4. `src/game/weapons.ts`
5. `src/game/skills.ts`
6. `src/game/spawn.ts`
7. `src/game/constants.ts`
8. `public/assets/kenney-top-down-shooter/...`

## 11. 风险点

### 11.1 结构风险

- 单场景单文件过大，后续任何 feature 接入都会继续恶化
- battle 表现逻辑与 battle 规则逻辑未分层

### 11.2 契约风险

- 当前 `GameSnapshot` 是前端本地模拟结构，不是未来后端 battle snapshot 合同
- `PlayerCommand` 仍是本地输入结构，未来要拆成 client input DTO 与 local input adapter

### 11.3 资产风险

- 当前 battle 体验非常值钱，但高度依赖 `GameScene` 这个大文件的隐式协作
- 若贸然“重构纯净化”，很容易把最重要的 battle 手感重构没了

### 11.4 文本与编码风险

- 现有代码中已有中文字符串编码污染迹象
- 后续若不先统一 UTF-8 文件规范，battle UI 文案会持续劣化

## 12. 审计结论

当前 `slay-demo` 不适合作为“直接演进成完整前端应用”的基础，但非常适合作为：

- `battle renderer prototype`
- `battle UX asset bundle`
- `future battle page` 的表现核心

正确策略不是重写 battle，而是：

1. 保留现有 battle 表现资产
2. 在 battle 外层建立 typed adapter、page shell、session lifecycle
3. 逐步把 runtime 决策和 contracts 收归到新结构
4. 最终让 `GameScene` 只负责“渲染与局部交互”，不再负责“整个系统”
