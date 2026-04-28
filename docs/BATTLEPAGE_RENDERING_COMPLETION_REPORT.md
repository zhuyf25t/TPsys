# BattlePage Rendering Completion Report

更新时间：2026-04-28 Asia/Shanghai

## 结论

BattlePage 当前程序化渲染与手感专项已达到阶段性完成标准。

这里的“完成”指：authoritative multiplayer battle 的实战画面、输入反馈、远端同步、武器可读性、projectile / terminal VFX、HUD 遮挡与性能指标已经形成可玩级闭环，并通过 headless、headful、feel suite 与 API probe 验证。

这不等于最终商业美术完成。金属风格 + 空洞骑士融合风格资产、角色/武器/地图贴图、动画帧和美术统一性属于后续美术资产阶段。

## 当前可以玩到的程度

- 两个浏览器账号可以进入同一 authoritative battle，并在同一局内移动、开火、拾取武器、触发技能和同步结果。
- 本地玩家移动和枪口反馈即时显示，不等待服务端回包才产生手感反馈。
- 远端玩家、远端 projectile 和 terminal VFX 走 authoritative frame + interpolation，避免把远端抖动套到本地手感上。
- 手枪、火箭炮、加特林、霰弹枪都进入权威后端、前端 adapter、HUD 和 renderer 链路。
- 角色手中武器、武器 pickup、projectile、muzzle、terminal VFX 已按武器类型区分。
- 火箭炮终点使用 shockwave 表达真实范围爆炸半径；加特林高频 VFX 已降噪；手枪不再使用长轨道枪特效。
- HUD 小地图静态障碍层缓存有效，验证中静态层重绘为 0。
- 权威 HUD 武器栏显示完整武器列表，不再只显示当前武器；加特林显示热量语义，不再误显示为 0/0 弹药。
- 本地 projectile terminal tracer 已降噪，避免本地即时 tracer 与服务端 terminal tracer 形成双轨错位。

## 本轮完成项

- Matchmaking / smoke 等待从 10 秒压到 5 秒。
- 恢复服务端多武器 inventory，不再每次命令把 `currentWeaponIndex` 重置为 0。
- 恢复服务端 `switchWeaponDirection`、拾取补给、当前武器切换。
- 接回手枪、火箭炮、加特林、霰弹枪的服务端后坐力。
- 接回火箭炮 projectile terminal 范围伤害链路。
- 前端场景层补强滚轮切枪输入。
- 加特林本地和远端高频 VFX 降噪。
- 火箭 terminal VFX 从 filled pulse 改为 shockwave。
- 权威 HUD 显示完整武器栏。
- 本地 terminal tracer / correction tracer 降噪，保留远端火力可读性。
- 更新 `now.md` 记录当前状态、验证结果和剩余边界。

## 验证结果

已通过：

```powershell
npm run build
npm run backend:compile
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bp28-render-feel-smoke.ps1 -Scenario MixedMovement -SummaryPath .runtime\render-pass-headful-local-terminal-tracer-suppressed-summary.json -InputDurationMs 1800 -FrameSampleSeconds 2 -Headful
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bp44-battle-feel-suite.ps1
```

关键结果：

- `npm run build` 通过，仅剩既有 Vite chunk / React Router `"use client"` 警告。
- `npm run backend:compile` 通过。
- `bp44-battle-feel-suite` 五个场景全部通过：`MixedMovement`、`SkillPressure`、`TargetedSkillPressure`、`DualClientPressure`、`StraightFire`。
- feel suite 中全部场景 `sameBattle=true`、warnings `0`、hit-dispute failures `0`。
- 本地 motion / muzzle 延迟约 3-11ms。
- `MixedMovement` RAF over25 / over40 均为 0。
- HUD minimap static redraw 为 0。
- 权威武器切换 / 后坐力 API probe 通过：`Pistol/Gatling`，切枪 `Gatling -> Pistol -> Gatling`，加特林后坐力 `dx=-2.88`。
- 火箭拾取 / terminal API probe 通过：武器栏 `Pistol/Gatling/RocketLauncher`，当前 `RocketLauncher`，rocket terminal `hit`，bot HP `100 -> 40`，damage `60`。

## 保留特效命名

`piercing-rail-tracer-long`

这是早期手枪误用的长白色束流特效，已经从手枪默认表现中移除。该效果适合后续用作狙击枪、穿透枪、轨道枪或高阶技能，不再用于普通手枪。

## 剩余边界

以下不再归入本轮程序化渲染专项：

- 最终美术资产：角色 sprite、武器 sprite、地图 tileset、爆炸序列帧、UI 图标统一风格。
- 更强的地图编辑 / 地图包扩展能力。
- bot 社区 SDK / 可插拔 bot 交互库。
- replay、mail、rating、profile、forum、admin 产品化收口。
- 大规模类型安全 / 声明式 / 微服务代码风格重整。

## 后续建议

下一阶段不应继续无限堆 BattlePage 程序化特效。更合理的顺序是：

1. 规则与战斗体验微调：武器数值、移动速度、火箭爆炸半径、加特林热量、霰弹扩散。
2. 代码风格整理：类型安全、声明式边界、前后端同名 API contract、课程格式要求。
3. 美术资产阶段：确定金属风格 + 空洞骑士融合风格，替换程序化几何素材。
4. 扩展性阶段：地图配置化、技能配置化、bot SDK、社区 bot 接口。
