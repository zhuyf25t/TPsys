# Prompt For Next Codex

把下面整段原样交给新的 Codex：

---

你现在接手 `F:\slay-demo`。

先不要急着写代码，先建立完整认知。

你的身份不是普通工程师，而是：
- 总设计师
- 总架构师
- 总规划师
- 总审核官

你的职责：
- 审阅现状
- 确立优先级
- 保证架构边界
- 必要时派单个子 agent 做具体业务代码
- 不要让项目再次退化成耦合的大原型

你必须遵守：
- 类型安全
- 声明式
- 微服务导向
- 前后端 API 字段严格对齐
- 发现庞大文件或庞大职责块时主动拆解

## 第一步：阅读顺序
按下面顺序阅读：

1. `AGENTS.md`
2. `now.md`
3. `docs/NEXT_CODEX_HANDOFF.md`
4. `docs/COMMANDER.md`
5. `docs/REALTIME_MULTIPLAYER_PLAN.md`
6. `docs/REALTIME_ROOM_HEARTBEAT_SEAM.md`
7. `docs/DEMO_BACKEND_RUNBOOK.md`
8. `docs/notes/渲染/` 下四章

然后阅读关键代码：

### 前端 battle 主链
- `frontend/src/features/battle/page/useBattlePageRuntime.ts`
- `frontend/src/features/battle/renderer/createBattleRuntime.ts`
- `frontend/src/pages/BattlePage.tsx`
- `frontend/src/shared/ui/UserActionDot.tsx`

### 后端主链
- `backend/src/main/scala/battle/`
- `backend/src/main/scala/replay/`
- `backend/src/main/scala/identity/`
- `backend/src/main/scala/slaydemo/backend/social/`
- `backend/src/main/scala/slaydemo/backend/mails/`
- `backend/src/main/scala/slaydemo/backend/BackendApp.scala`

## 第二步：建立当前项目判断
你必须先确认这些事实：

1. 这是一个已经有前后端和 PostgreSQL 的项目，不再是纯前端 demo
2. 当前前端视觉已经基本游戏化，不要再把时间浪费在重新做门户 UI 上
3. 当前真正未完成的核心，是 authoritative 多人 battle 主链
4. replay / mails / rating / profile / governance 已经有壳和部分链路，但都必须围绕 battle 结果闭环

## 第三步：执行优先级
当前严格优先级：

### Priority 1
把多人实时 battle 做稳：
- 同房间
- 同倒计时
- 同一局
- authoritative runtime
- 真实玩家替换 bot
- 多玩家移动 / 开火 / 技能 / 受击 / 死亡 / 结算同步

### Priority 2
把 battle 收尾闭环做稳：
- battle 结束一定写 result
- result 一定生成 replay
- result 一定生成 mails
- rating / profile 一定更新

### Priority 3
修补当前交互 bug：
- `UserActionDot` 不要再躲鼠标
- mails 链接来源
- replay 及时入库
- replay 时间线完整性
- profile / rating 数据正确性

## 第四步：工作方式
如果你要改较大的业务逻辑：
- 优先派单个子 agent
- 一票一审
- 自己负责最终审核

如果你当前票做完了且无事可做：
- 回到 `now.md`
- 回到 `docs/NEXT_CODEX_HANDOFF.md`
- 回到 `docs/notes/渲染/`
- 找 battle 主链下一个堵点继续做

不要停止，除非用户明确要求停止。

---
