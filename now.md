# Now

更新时间：2026-04-29 Asia/Shanghai

## 当前阶段

项目已经从 BattlePage 渲染急救与 `GameScene` 硬解耦阶段，进入“扩展性、交付脚本、产品闭环与外围体验收口”阶段。

当前原则：

- 暂不继续改 BattlePage 角色、地图、箱子等素材方向，避免再次偏离用户审美预期。
- 允许继续处理大厅视觉结构、数据闭环、bot/地图/技能/武器扩展性、启动/验收脚本、站内信/好友等产品系统。
- 课程代码风格大重构暂缓，后续等功能闭环稳定后集中处理。
- 保持类型安全、声明式边界、前后端 API 同名、微服务化可拆分方向。

## 当前可以玩到什么程度

当前已登录账号可以进入 authoritative multiplayer battle，进行服务器权威同步的多人/机器人对战。BattlePage 已恢复到 Kenney top-down PNG 玩家风格，不再使用之前不满意的昆虫化角色素材。武器栏、滚轮/数字切枪、手枪、火箭炮、加特林、霰弹枪、技能、拾取、击中反馈、火箭 AoE、加特林热量/后坐力等已进入可玩状态。

对局结束后，基础闭环已经打通：战报、回放、rating/profile、站内信可以生成和读取。Visitor/访客正式开战、写入战绩、写入回放、写入评分等路径已经做了前后端防护，并且本地历史脏数据已经用清理脚本清掉一轮。

## 最近已完成

### GameScene 硬解耦

- `frontend/src/scenes/GameScene.ts` 已从巨型 battle runtime class 收敛为 scene shell / renderer host / glue layer。
- arena builder、world view factory、projectile runtime、hit/damage/respawn、pickup、weapon、combat frame、display label、geometry resolver 等责任已经迁出或被证明不属于主链残留。
- 完成报告：`docs/GAMESCENE_HARD_GATE_COMPLETION_REPORT.md`。

### BattlePage 渲染与手感阶段性收口

- 本地玩家与远程玩家渲染链路已分开处理。
- 小地图静态层缓存、VFX 生命周期、枪口反馈、弹道、后坐力、拾取反馈、命中反馈都做过阶段性优化。
- 火箭炮 AoE、加特林热量/后坐力、滚轮和数字键切枪已恢复并对齐。
- BattlePage 素材方向已按用户要求停止继续改动，后续只做必要 bug 修复，不做审美替换。

### 扩展性基础

- 地图：已有前后端 map catalog 第一轮，默认地图的 spawn、obstacle、pickup 进入 catalog 化。
- 武器：前端 weapon definition 字段与后端同名第一轮完成，recoil 从运行时散落配置收敛到 definition。
- 技能：前端 skill profile 改为更显式的 union/profile，后端也有 skillDefinitions map。
- Bot：已有 manifest/discovery/test harness、外部策略模板、离线 smoke，以及显式模块注册桥接。
- 合约审计：`npm run audit:battle-contracts` 已可动态检查 battle catalog 契约漂移。

### 数据闭环

- Visitor-like identity 在前端 rating/profile/replay、本地 battle truth fallback、后端 result/replay/mail/projector 等路径做了过滤。
- `scripts/audit-data-closure.mjs` 可只读审计历史脏数据。
- `scripts/cleanup-data-closure.mjs` 可 dry-run 或显式 `--apply` 清理历史 Visitor-like 数据和 result 逻辑重复。
- 本地已执行一轮 `--apply`：Visitor battle results、mails、replay records 已清为 0，duplicate groups 为 0。

### 站内信第一轮

- 站内信卡片已从过度空旷的纵向布局改为横向 `main + side`。
- 查看来源/查看回放/标为已读等动作移到右侧动作区。
- 未读筛选和已读操作的交互边界已修补，右侧链接不会误触整卡标记已读。

### 大厅视觉结构第一轮

- 大厅中心区增加了更紧凑的信息卡：战斗模式、同步协议、战报链路、赛季系统。
- 清理了一批重复 CSS 覆盖，保留金属战争大厅、ring spin、particle drift、scanline 等视觉方向。
- 目前仍是程序化 CSS + 现有背景资产，距离目标参考图的商业级金属大厅还需要继续打磨。

### 启动/诊断脚本

- 新增 `scripts/dev-port-status.ps1` 与 `npm run dev:status`：只读诊断 5173/8080、PID、进程角色和命令行，不杀进程。
- 新增 `scripts/dev-start.ps1` 与 `npm run dev:start`：只启动缺失服务；端口已监听时只报告，不重复启动，不调用 `Stop-Process`。
- 支持 `-FrontendOnly`、`-BackendOnly`、`-OpenStatus`。
- 当前验证状态：5173 有两个 Vite listener，8080 有一个 `BackendApp`，`dev-start -OpenStatus` 没有重复启动，也没有杀进程。

### 好友/社交入口真实状态第一轮

- 新增只读好友申请缓存快照入口，首页、配装页和 BattlePage 数据链可以读取真实 friend request records。
- 新增统一 presenter，把好友申请转为 quick preview items、中文状态、相对时间和 badge count。
- 好友 badge 只统计“当前用户收到的 pending 请求”，已发出、已同意、已拒绝的请求不会误算成待处理红点。
- 未登录状态显示“登录后查看好友申请”，并禁用远端好友请求加载，不触发写入。
- BattlePage 保留好友状态数据链，但实战 `playing` 阶段不显示这些角落入口，避免遮挡 HUD、战斗日志或右下武器/技能区域。

### 站内信第二轮

- 站内信概览区新增 compact stats：总计、未读、战报、好友、已合并战报，顶部不再只有一行说明和一排筛选。
- 新增“战报”筛选 chip，可以只看 `battle` 类型邮件。
- 合并后的战报邮件会显示“合并 N 条”状态，明确告诉用户战报和 rating 变动已经归并到同一条通知。
- 新增“全部标为已读 / 当前筛选标为已读”按钮，只处理当前筛选可见的未读邮件。
- 批量已读复用单封邮件的同步逻辑：本地 battle mail 走本地 read，远端 mail 走 `markMailAsReadRemote`；失败时恢复对应邮件状态并显示失败提示。
- 没有改后端业务语义，没有改 BattlePage 素材。

## 当前风险

- 5173 现在有两个 Vite listener，说明前端可能被重复启动过；这不影响当前脚本验收，但用户体验时可能需要手动决定保留哪一个。
- 8080 当前已有后端运行；如果再手动 `sbt run`，出现 `Address already in use` 是正常端口占用，不代表后端代码坏了。
- `now.md` 之前出现过编码污染，本轮已重写为 UTF-8 可读版本。
- 后端 `backend:compile` 在后端已运行时可能受 sbt named-pipe/boot server 干扰，必要时应先用 `npm run dev:status` 判断，不应直接认为是编译失败。

## 下一步计划

1. 大厅视觉结构第二轮。
   预计：0.5-1 天。
   目标：在不动 BattlePage 素材的前提下，把大厅向目标参考图的金属战争大屏方向推进：模块层级、入口状态、榜单/邮件/好友入口、背景粒子和中心 CTA。

2. 扩展性第二轮。
   预计：0.5-1 天。
   目标：继续补 bot 社区接入、地图/武器/技能示例包、契约审计和外部贡献边界。重点是让朋友能写 bot，而不是让他改核心战斗代码。

3. 数据闭环第二轮。
   预计：0.5 天。
   目标：继续查 duplicate projection、多账号/多标签、同账号异地登录、rating/profile 派生一致性；不急着做大型账号系统重构。

4. 课程代码风格大重构。
   预计：暂缓。
   目标：等用户确认老师要求后，再统一处理 Scala `var/val`、parser/renderer 风格、模块边界、DTO 契约和微服务拆分。现在不主动推进，避免功能开发中途大面积扰动。

## 当前执行策略

下一票优先继续做“大厅视觉结构第二轮”或“扩展性第二轮”这类不会碰 BattlePage 素材、收益明确、风险可控的任务。大改功能代码继续按一票一 worker、一票一审查、一票一接受的方式推进。
