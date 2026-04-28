# Now

更新时间：2026-04-28 Asia/Shanghai

## 当前状态

`BP-46B-2` 已收口：命中可信度 smoke gate 已从“记录样本”升级为“可失败的自动验收门”。

这次只改了脚本，不改运行时业务逻辑：

- `scripts/bp28-render-feel-smoke.ps1`
- `scripts/bp44-battle-feel-suite.ps1`

当前 runtime 不受影响：没有改 `GameScene.ts`、后端、projectile 伤害、速度、半径、生命周期、碰撞或渲染代码。

## 本次完成了什么

现在 smoke 能自动判定这些问题：

- human/input-relevant projectile 的服务端 terminal 数量大于客户端 terminal diagnostics 数量时，必须失败。
- `terminalNearButNoDamage=true` 会成为 failure，而不是只记录在 summary 里。
- `hit` terminal 必须有 target、hp、damage 等关键字段。
- suite 中单个 scenario 失败后不会直接中断整套脚本，会继续跑完并写出 suite summary，方便定位。
- PowerShell helper 支持 `[ordered]` / `OrderedDictionary` 字段读取，避免 summary 里有字段但断言读取不到。

## 验收结果

已验证：

- PowerShell parser check 通过。
- standalone `StraightFire` 通过：`.runtime\bp46b-hit-assert-v3-straight-fire\StraightFire-summary.json`
- `bp44-feel-suite` 现在按预期失败：`.runtime\bp46b-hit-assert-v3-feel-suite\suite-summary.json`

suite 失败不是脚本回归，而是脚本开始抓到真实缺口：

| scenario | ok | relevantServer | relevantClient | failureCount |
|---|---:|---:|---:|---:|
| MixedMovement | true | 4 | 4 | 0 |
| SkillPressure | true | 3 | 3 | 0 |
| TargetedSkillPressure | true | 0 | 0 | 0 |
| DualClientPressure | false | 22 | 21 | 4 |
| StraightFire | false | 9 | 8 | 1 |

这说明：至少在 `DualClientPressure` 和 suite 内的 `StraightFire` 场景里，仍有 human/input projectile terminal 没有完整进入客户端 diagnostics/feedback 闭环。

## 当前暂停点

按用户要求，当前票完成后暂停继续推进新功能。

这里不是项目完成，而是一个明确的工程暂停点：

- 不继续开 `BP-46B-3`。
- 不继续做 `BP-46C` 本地/远程显示通道分层。
- 不继续做美术资源、倒计时缓存、rating/profile、bot SDK。
- 下一步先转入“代码格式和课程要求整理”。

## 下一阶段：课程格式整理

目标：让项目结构、代码说明、运行方式和课程交付材料更清楚，同时不改变战斗语义。

建议顺序：

1. 整理课程交付说明。
   - 预计：30-60 分钟 Codex 时间。
   - 输出：项目简介、运行方式、核心功能、技术栈、目录结构、测试方式、已知限制。

2. 梳理模块职责。
   - 预计：60-120 分钟 Codex 时间。
   - 重点：battle frontend、backend authoritative state、renderer/effects、scripts smoke suite、profile/rating/replay 的边界。

3. 检查类型安全和 API 对齐。
   - 预计：60-180 分钟 Codex 时间。
   - 重点：前后端字段同名同义、nullable 显式、DTO/枚举不漂移、不用 `any` 掩盖 contract 问题。

4. 做低风险格式整理。
   - 预计：2-6 小时 Codex 时间。
   - 范围：命名、注释、README、脚本入口说明、文档归档、目录说明。
   - 原则：不偷偷改 gameplay、渲染手感、后端状态机或 rating 语义。

5. 课程整理后再回到 battle 修复。
   - 第一优先级：`BP-46B-3`，修复当前 gate 暴露出的 relevant terminal diagnostics/feedback 缺口。
   - 然后：继续 `BP-46C` 本地/远程显示通道分层。

## 当前硬原则

- 类型安全：DTO、枚举、nullable 语义必须显式。
- 声明式：主链路按 snapshot -> command -> authoritative frame -> presenter/renderer 推进。
- 微服务边界：battle、result、replay、rating、mails、profile、forum、admin 不互相乱写。
- API 同名同义：public contract 的 endpoint、JSON 字段、枚举值和错误码必须前后端一致。
- `GameScene.ts` 不回填业务逻辑。
- 不使用 git。

## Waiting List

- `BP-46B-3`：修复 suite 暴露出的 projectile terminal diagnostics/feedback 缺口。
- 新局倒计时继承上一局剩余时间：继续压测多账号、多 tab、恢复 session。
- 多账号异地登录：允许作为产品策略，但必须防止同一局占多个账号，防止 rating/replay/profile 串号。
- Visitor 数据隔离：访客应是虚拟账号，不能污染正式 rating/profile。
- rating 曲线串号：审计 result -> rating -> profile 的原子更新和缓存来源。
- bot SDK：拆成独立策略库，让外部贡献者只写 bot brain，不碰 Phaser、DOM 或后端写入。
- 本地/远程显示通道继续分层：课程整理暂停窗口结束后再推进。
