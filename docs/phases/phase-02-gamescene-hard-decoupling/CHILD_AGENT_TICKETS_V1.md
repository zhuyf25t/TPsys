# CHILD_AGENT_TICKETS_V1

本文件只定义后续子 agent 可执行 ticket，不在本轮执行。

所有 ticket 默认前提：

- 不重写 battle 手感
- 优先保留现有 `GameScene` 与 `Hud.ts`
- 新代码必须遵守 `docs/COMMANDER.md`
- 除非 ticket 明确允许，否则不得修改 battle 数值平衡

## Ticket 01：battle 文件边界盘点与 legacy 下线清单

### 任务目标

列出 `GameScene.ts` 中所有 `legacy*` 路径、重复逻辑、临时诊断路径，并给出可下线路径清单。

### 允许修改的文件范围

- `docs/`

### 禁止修改的范围

- `src/`
- `public/`

### 输入契约

- 当前仓库源码
- `docs/COMMANDER.md`
- `docs/FRONTEND_AUDIT.md`

### 输出契约

- 新增 `docs/BATTLE_LEGACY_CLEANUP_PLAN.md`

### 验收标准

- 明确列出每个 `legacy*` 方法当前是否仍在运行路径
- 明确列出调试链路与运行链路
- 每一项都给出“可删 / 可归档 / 暂保留”结论

### 风险提醒

- 不允许在未核实运行路径前直接删除代码

## Ticket 02：battle contracts 草案

### 任务目标

基于当前 `domain/types.ts` 与 `COMMANDER.md`，设计 battle typed contracts 草案。

### 允许修改的文件范围

- `docs/`

### 禁止修改的范围

- `src/`

### 输入契约

- 当前 battle 类型定义
- `COMMANDER.md` battle contract 约束

### 输出契约

- 新增 `docs/BATTLE_CONTRACTS_DRAFT_V1.md`

### 验收标准

- 至少定义 command / snapshot / hud view / result view / event 五类 DTO 草案
- 明确哪些字段来自当前 battle 前端
- 明确哪些字段必须由后端产生

### 风险提醒

- 不要把 Phaser sprite、DOM 节点、local-only runtime state 混入 contract

## Ticket 03：battle HUD presenter 抽离设计

### 任务目标

把 `GameScene.updateHud()` 目前做的 view model 拼装拆成 presenter 设计稿。

### 允许修改的文件范围

- `docs/`

### 禁止修改的范围

- `src/scenes/GameScene.ts`
- `src/ui/Hud.ts`

### 输入契约

- `GameScene.updateHud()`
- `Hud.ts` 的 `HudState`

### 输出契约

- 新增 `docs/BATTLE_HUD_PRESENTER_PLAN.md`

### 验收标准

- 明确 `HudState` 来源
- 明确 presenter 输入与输出
- 明确 scene 中哪些逻辑会迁出

### 风险提醒

- 不要在 presenter 里夹带 Phaser 依赖

## Ticket 04：battle page shell 设计

### 任务目标

为未来 `/battle` 页面定义 page shell、queue modal、loading、result return 的前端壳层结构。

### 允许修改的文件范围

- `docs/`

### 禁止修改的范围

- `src/`

### 输入契约

- `COMMANDER.md` 第 3 节页面树
- `COMMANDER.md` 第 12 节 battle 前端收编原则

### 输出契约

- 新增 `docs/BATTLE_PAGE_SHELL_PLAN.md`

### 验收标准

- 画出 battle 页面壳状态机
- 明确 queue -> session -> result -> return 的切换
- 明确 Phaser canvas 在页面壳中的挂载方式

### 风险提醒

- 不要把 page shell 重新做成 Phaser 内 UI

## Ticket 05：battle adapter 设计

### 任务目标

设计 battle 前端与 future backend 的 typed adapter 接口。

### 允许修改的文件范围

- `docs/`

### 禁止修改的范围

- `src/`

### 输入契约

- 当前输入读取路径
- 当前 snapshot 数据结构
- `COMMANDER.md` 的 contract 原则

### 输出契约

- 新增 `docs/BATTLE_ADAPTER_PLAN.md`

### 验收标准

- 至少定义输入发送接口、snapshot 接收接口、生命周期接口
- 明确不让 `GameScene` 直接处理 HTTP / WebSocket

### 风险提醒

- adapter 不能倒逼 battle renderer 感知 transport 细节

## Ticket 06：battle constants / map / asset 拆分实施

### 任务目标

把当前 `constants.ts` 拆成更清晰的配置模块，但保持行为不变。

### 允许修改的文件范围

- `src/game/constants.ts`
- 新增 `src/game/` 下配置文件
- 允许同步修改 `GameScene.ts` 的 import

### 禁止修改的范围

- battle 玩法数值
- `Hud.ts`
- `domain/types.ts`

### 输入契约

- 当前常量值必须保留
- 地图布局、素材路径、系统参数分开

### 输出契约

- 新的配置模块结构
- 行为不变的 battle 运行结果

### 验收标准

- build 通过
- 常量按三类拆开：系统参数、素材路径、地图布局
- 运行时视觉和玩法无明显变化

### 风险提醒

- 不要顺手改平衡数值

## Ticket 07：battle runtime-local 第一刀

### 任务目标

从 `GameScene` 抽出不依赖 Phaser 的本地 battle runtime helpers。

### 允许修改的文件范围

- `src/scenes/GameScene.ts`
- 新增 `src/game/runtime-local/` 或等价目录
- `src/domain/types.ts`

### 禁止修改的范围

- `Hud.ts`
- 页面壳
- public assets

### 输入契约

- 抽出的逻辑不得依赖 Phaser sprite
- battle 行为保持一致

### 输出契约

- 至少抽出一类纯逻辑：reload / weapon state / pickup resolve / damage helper 之一

### 验收标准

- build 通过
- 抽出的 helper 不依赖 Phaser scene
- `GameScene` 行数明显下降

### 风险提醒

- 不要一次性重构 projectile、camera、HUD 全部路径

## Ticket 08：battle debug 诊断链路隔离

### 任务目标

把当前 wheel/debug combat 临时诊断逻辑隔离成 debug 开关，不污染默认运行路径。

### 允许修改的文件范围

- `src/main.ts`
- `src/scenes/GameScene.ts`
- `src/ui/Hud.ts`

### 禁止修改的范围

- 武器平衡
- 命中规则
- 地图结构

### 输入契约

- 默认体验不变
- 诊断模式可单独打开

### 输出契约

- 一个明确 debug 开关
- 诊断代码位置集中化

### 验收标准

- 默认 HUD 不显示调试信息
- 打开 debug 时仍能看到 wheel / hit logs

### 风险提醒

- 不要删除当前仍用于定位 bug 的必要日志，先隔离再裁剪

## Ticket 09：battle DOM HUD 文本与编码清洗

### 任务目标

修复当前 battle HUD / 武器名 /角色名中的编码污染，统一 UTF-8 文本源。

### 允许修改的文件范围

- `src/ui/Hud.ts`
- `src/game/weapons.ts`
- `src/game/spawn.ts`
- `src/scenes/GameScene.ts`

### 禁止修改的范围

- 玩法逻辑
- 命中结算
- 地图布局

### 输入契约

- 所有玩家可见文字必须继续是中文

### 输出契约

- UTF-8 正常显示的中文文本

### 验收标准

- build 通过
- battle HUD 中不再出现乱码
- 控制台与屏幕提示中文一致

### 风险提醒

- 编码清洗时不要破坏逻辑字符串匹配

## Ticket 10：battle page 接入最小 app shell

### 任务目标

为当前 battle 原型加入最小页面壳，让 `/battle` 成为未来多页面应用的可嵌入模块。

### 允许修改的文件范围

- `src/main.ts`
- 新增 `src/app/`
- 新增 `src/pages/battle/`
- 允许调整 `index.html`

### 禁止修改的范围

- `GameScene` 战斗核心逻辑
- public assets

### 输入契约

- 当前 battle 仍能正常启动
- 新页面壳不改变 battle 手感

### 输出契约

- battle 入口由页面壳管理
- Phaser mount 生命周期清晰

### 验收标准

- `npm run dev` 正常启动
- battle 仍可玩
- 页面壳与 battle renderer 已分层

### 风险提醒

- 不要在这一票里同时接后端

## 建议的执行顺序

建议优先级：

1. Ticket 02：battle contracts 草案
2. Ticket 03：battle HUD presenter 抽离设计
3. Ticket 04：battle page shell 设计
4. Ticket 05：battle adapter 设计
5. Ticket 06：constants / map / asset 拆分
6. Ticket 07：runtime-local 第一刀
7. Ticket 09：编码清洗
8. Ticket 10：最小 app shell

## 当前最值得优先发给子 agent 的第一票

`Ticket 02：battle contracts 草案`

原因：

- 它不会破坏现有 battle 体验
- 它直接决定未来前后端边界
- 它是 battle renderer 被正式收编的前置条件
