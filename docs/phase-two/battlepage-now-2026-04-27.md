# 当前状态
更新时间：2026-04-27 19:44 Asia/Shanghai

## 1. 当前项目判断

项目已经不是纯前端原型。当前已经具备：

- 前端 React/Phaser battle 壳与外围页面。
- Scala/SBT 后端 authoritative battle skeleton。
- 多客户端进入同一 battle 的主链路。
- battle result / replay / rating / profile / mails 的基础闭环。
- `GameScene.ts` hard-decoupling 已完成，当前保持 scene shell / renderer host / glue layer 边界。

当前仍不能宣布“最终好玩 / 最终丝滑 / 最终渲染完成”。主线继续压在 BattlePage 本体体验：渲染手感、技能位移、命中可信度、多客户端稳定性。

## 2. 当前运行状态

- 前端 dev server 正常运行在 `http://127.0.0.1:5173`。
- 后端正常运行在 `http://127.0.0.1:8080`。
- 2026-04-27 19:09 复查 `/health` 返回 `{"status":"ok","service":"slay-demo-backend","port":8080}`。
- 当前 8080 被 Java/sbt 后端 PID `8500` 占用。用户另开终端执行 `sbt run` 看到 `Address already in use: bind` 是端口冲突，不是后端代码坏了。
- 当前不需要停后端；用户可以直接体验现有前后端。
- 不运行 `git`，不依赖 git 回滚。

## 3. 最近完成的重点

- BP-40C/BP-40D：闭环新局倒计时继承问题。真实浏览器 smoke 证明后端和 localStorage elapsed 接近 0，旧问题来自 HUD 显示时钟污染；修复后第二局回到 `04:59`。
- BP-42A：收敛手枪 tracer。短管曳光保留给手枪，原长束特效归档为 `piercing-rail-tracer-long`，以后可给穿透/狙击类武器使用。
- BP-42B：降低 VFX 热路径 churn。ring effects 不再每帧 clone，新增 `vfxMetric`。
- BP-43：HUD/minimap 静态层离屏缓存，新增 `hudMetric`，静态层重绘 delta 已压到 `0`。
- BP-44A：建立 battle feel suite，聚合 `MixedMovement`、`DualClientPressure`、`StraightFire`。
- BP-39A/B：建立视野/屏幕速度诊断，并把 camera zoom 从 `1.32` 标定到 `1.40`，不改 gameplay。
- BP-44B：新增 `SkillPressure` 场景，覆盖移动、移动瞄准、短开火和 Q/E/R 技能连按。该票作为诊断扩展接受，因为它暴露了技能位移 hard snap。
- BP-45A：修复 Dash 技能位移 hard snap。新增 Dash 预测 helper，本地 display motion 和 authoritative replay 都按服务端同规则预测 Dash，短 TTL pending dash target 托住未 ack 的旧权威帧。

## 4. 正在进行

当前刚完成：BP-45A 技能位移 hard snap 根因与修复。

已知事实：

- BP-44B 的 `SkillPressure` 稳定暴露 `hardSnapDelta=1`。
- 根因候选是 Dash/Blink/Freeze 这类服务端位移/状态技能没有完整进入本地 display prediction 和 authoritative replay 链。
- BP-45A 已做最小闭环：让本地 Dash display pose 立即按服务端同规则预测，并让 unacked command replay 考虑 `castDash` / `aim` / Dash cooldown gate。
- 单场景 `.runtime\bp45a-skillpressure-summary.json` 显示 `SkillPressure hardA=0`、`hardB=0`、warnings `0`、命令失败 `0`。
- 完整 `.runtime\bp45a-suite\suite-summary.json` 显示 MixedMovement / SkillPressure / DualClientPressure / StraightFire 全部 `ok=true`、warnings `0`，全场景 clientA/clientB `hardSnap=0`。
- 主控复核 `npm run build` 通过，仅保留既有 Vite/chunk 警告。

当前结论：

> BP-45A 可接受：Dash hard snap 最小闭环完成。但这不等于完整 skill prediction pass 完成，Blink/Freeze 的完整预演、失败回滚和 outcome 对齐仍在 BP-47。

## 5. 下一步队列

1. BP-44C 测试口径修正：`SkillPressure` 当前 fire/muzzle 探针读取过早，导致 muzzle latency 回退到 first keydown；需要把 fire event probe 读到输入窗口之后或增加技能/fire 专用基准。
2. BP-47 skill prediction pass：Dash/Blink/Freeze 的完整本地预演、失败回滚、冷却显示、服务器 outcome 对齐。
3. BP-46 weapon feel pass：手枪、冲锋枪、霰弹枪分别调后坐力、弹道长度、换弹反馈、命中反馈，不混改伤害。
4. ART-01/02：金属竞技场 + 空洞骑士式角色方向，以及箱子/障碍物 skin 重做。素材服务于判定和可读性，不抢手感 P0。

## 6. Waiting List

- 多账号/异地登录身份模型。
- 同人双账号占位防护。
- Visitor 虚拟账号隔离，默认不应进入正式 rating 榜。
- Rating 曲线串号/缓存污染审计。
- Profile/Rating cache policy。
- Bot SDK：可拆成纯逻辑 `snapshot/profile -> intent/command` 策略库，让外部贡献者写 bot brain，不依赖 Phaser/DOM/后端写入。

## 7. 运行命令

前端：

```powershell
npm run dev
```

后端：

```powershell
npm run backend:dev
```

如果 8080 已被当前后端占用，重复运行后端会报 `Address already in use: bind`。这时不要重复启动，直接使用现有后端即可。

构建：

```powershell
npm run build
```

手感 smoke：

```powershell
npm run demo:bp44-feel-suite
```

技能压力单场景：

```powershell
npm run demo:bp28-render-feel-smoke -- -Scenario SkillPressure -InputDurationMs 3500
```

## 8. 下一任 Codex 阅读顺序

1. `AGENTS.md`
2. `now.md`
3. `main.md`
4. `plan.md`
5. `docs/GAMESCENE_HARD_GATE_COMPLETION_REPORT.md`
6. `docs/notes/渲染/`
7. `frontend/src/features/battle/renderer/authoritativeLocalHeroMotion.ts`
8. `frontend/src/features/battle/renderer/authoritativeLocalHeroReplay.ts`
9. `frontend/src/features/battle/renderer/authoritativeFrameSnapshotApplier.ts`
10. `frontend/src/features/battle/page/useBattlePageRuntime.ts`

## 9. 一句话 Handoff

当前不要切到外围页面。继续把 BattlePage 做到真正顺滑：BP-45A 已让 Dash hard snap 在完整 suite 中归零；下一步先修正 `SkillPressure` 的 fire/muzzle 测试口径，然后进入完整 skill prediction pass。
