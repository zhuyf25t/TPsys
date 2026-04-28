# Now

更新时间：2026-04-29 Asia/Shanghai

## 当前状态

项目当前已经从“BattlePage 程序化渲染与多人联机原型”推进到“架构扩展前置阶段”。

现在可以玩的程度：

- 两个已登录账号可以进入同一局 authoritative battle。
- 本地移动、瞄准、枪口反馈、滚轮切枪、拾取武器后的武器栏切换已经可用。
- 手枪、加特林、火箭炮、霰弹枪已经进入 authoritative runtime、前端 HUD 和 renderer 链路。
- 火箭炮 AoE、重武器后坐力、命中/撞墙/射程耗尽 terminal 表现已经区分。
- Battle result、replay、rating、profile、mails 已经形成基础闭环；战报和 rating 变化合并为一封站内信。
- Visitor 正式开战和正式数据写入已经被拦截到基础可用水平。
- `GameScene.ts` 已完成硬解耦阶段性终态：它现在是 scene shell / renderer host / glue layer，不再直接承担主要 battle runtime 责任。

这仍然不是最终成品：

- 美术资产仍主要是程序化绘制，不是最终“金属战争 + 空洞骑士剪影”风格资产。
- 地图、技能、武器、bot 还没有达到真正内容包 / 社区扩展级别。
- 课程风格要求下的底层类型安全、声明式、微服务边界整理暂缓，等待你确认老师要求后再做大改。
- 聊天系统暂缓，不进入当前无人推进主线。

## 已完成的关键收口

- BattlePage 渲染专项已阶段性完成，报告见 `docs/BATTLEPAGE_RENDERING_COMPLETION_REPORT.md`。
- GameScene 硬解耦硬门已完成，报告见 `docs/GAMESCENE_HARD_GATE_COMPLETION_REPORT.md`。
- 后端 authoritative battle 内容参数已抽到 `BattleContentCatalog`，runtime 主类不再直接承载武器、技能、pickup、出生点、bot tuning 等静态内容表。
- 前端 battle 内容参数已抽到 `frontend/src/game/battleContentCatalog.ts`，`weapons.ts`、`skills.ts`、`spawn.ts` 保留原 public API 并从 catalog 读取。
- 第一轮前后端内容契约审计已完成，记录见 `docs/BATTLE_CONTENT_CONTRACT_AUDIT.md`。
- 加特林模型已统一为 authoritative heat / overheat，不再是前端热量、后端弹匣近似。
- 当前 GitHub main 已保存：
  - `6150a4e Unify authoritative Gatling heat contract`
  - `fd52c80 Align battle content contracts`
  - `7e2cc28 Extract frontend battle content catalog`
  - `7adf04b Extract backend battle content catalog`
  - `03d81b7 Rewrite current roadmap`
  - `fcc435f Refresh GameScene hard gate report`
- 最近验证包括：
  - `npm run build`
  - `npm run backend:compile`
  - battle feel smoke / authoritative weapon switch / rocket terminal / mail single notification / pickup preserves current weapon probes

## 当前正在做的事情

当前主线：扩展性基础。

刚完成的单一任务：

- 加特林武器契约统一。
- 后端 `BattleWeaponState` / player scalar mirror 新增 `heat`、`overheated`、`overheatRemainingMs`。
- 后端 Gatling 改为 `usesHeat=true`、`magazineSize=0`、不消耗 ammo、按 heat/overheat 控制开火。
- 前端 authoritative client、frame bridge、snapshot applier 已把服务器 heat 字段写入 `WeaponState`。

结果：

- `npm run backend:compile` 通过。
- `npm run build` 通过。
- 审核确认非 Gatling 武器保持原弹药语义。

下一票：

- bot SDK 最小接口设计与落地。
- 目标是定义 bot observation/action/tick/权限边界，并给朋友或同学一个不直接改 runtime 的贡献入口。

## 下一步计划

1. 后端内容 catalog 抽离。
   状态：已完成。
   实际结果：authoritative runtime 不再直接承载主要静态内容表，内容入口集中到 `BattleContentCatalog`。
   目的：把 authoritative runtime 从“硬编码内容表”推进到“可扩展内容入口”。

2. 前端内容 catalog 对齐。
   状态：已完成。
   实际结果：`weapons.ts`、`skills.ts`、`spawn.ts` 保留原 public API，但数据来源集中到 `battleContentCatalog.ts`。
   目的：把 `frontend/src/game/weapons.ts`、`skills.ts`、spawn/pickup/arena 常量整理成更明确的 battle content 层，减少散落硬编码。

3. 前后端 battle 内容契约对齐审计。
   状态：已完成第一轮。
   实际结果：Dash 冷却、医疗包点位、Gatling 热量模型已经对齐；审计记录见 `docs/BATTLE_CONTENT_CONTRACT_AUDIT.md`。
   目的：检查 WeaponKind、SkillKind、ProjectileKind、pickup kind、字段命名、数值语义是否同名同义。必要时只做小范围修正，不做课程风格大改。

4. bot SDK 最小接口。
   状态：下一票。
   预计：0.5-1 天。
   目的：定义 bot 可读 observation、可输出 command、tick 频率、权限边界和一个样例 bot，让朋友以后能贡献 bot，而不是直接改 authoritative runtime。

5. 地图配置化第一刀。
   预计：0.5-1 天。
   目的：把地图尺寸、出生点、障碍物、pickup 点、视觉主题从硬编码结构变成可替换配置。第一版只支持内置配置，不急着做外部编辑器。

6. 技能/武器扩展接口第一刀。
   预计：0.5-1 天。
   目的：新增技能或武器时不需要修改 `GameScene.ts`，并尽量减少 runtime 主流程改动。

7. 数据闭环加固。
   预计：0.5-1.5 天。
   目的：处理历史 Visitor 脏数据、rating 幂等性、同账号多标签页占位、result/replay/rating/profile 一致性。

8. 主界面视觉重构第一轮。
   预计：0.5-1.5 天。
   目的：按你给的参考图做金属大厅结构、核心 CTA、排行榜/玩家档案/装配/站内信入口和背景粒子/机械动效。

9. BattlePage 美术资产第一轮。
   预计：1-3 天。
   目的：建立“自然 + 金属战争 + 空洞骑士剪影”视觉规范，生成或接入角色、武器、墙体、箱子、pickup、技能 VFX 的候选资产，并保持命中判定可读。

10. 启动、验收、交付脚本。
    预计：0.5-1 天。
    目的：一键关闭旧进程、一键启动前后端、一键 build/backend compile/smoke，减少端口占用和 sbt pipe 误解。

## 暂缓事项

- 聊天系统暂缓。好友申请和站内信先保持现状，后续统一做 notification/message channel。
- 课程风格大重构暂缓。包括全项目 var/val 清理、JSON parser/renderer 大迁移、微服务边界大拆分、前后端 DTO 全量契约迁移。
- 大规模数值平衡暂缓。当前只允许保守微调，最终“好不好玩”仍需要你回来看实战手感后拍板。

## 总体时间判断

无人值守可继续推进的内容，不包含课程风格大重构：

- 最短可展示闭环：2-4 天。
- 比较完整的扩展性、数据闭环、主界面和基础美术统一：5-10 天。
- 接近商业级 polish：10 天以上，主要消耗在素材、动画、音效、平衡、稳定在线服务和反复试玩。

当前我会继续推进，不切换到聊天系统，不做代码风格大改，先把扩展性基础做实。
