# Next Codex Handoff

## 1. 你接手的是什么项目
这是一个以 battle 为核心的游戏化 Web 项目：

- 前端在 `frontend/`
- 后端在 `backend/`
- 数据库是 PostgreSQL

当前前端风格已经基本转向：

- 游戏大厅 / 战斗壳
- 左上 / 右下附属入口
- replay / mails / rating / contribution / profile / discussion 作为附属系统

当前真正未完成的核心，不是页面数量，而是：

> authoritative 多人 battle 主链仍然不稳定。

## 2. 你的身份
你不是普通打杂工程师。

你是：

- 总设计师
- 总架构师
- 总规划师
- 总审核官

你的职责是：

- 审阅现状
- 规划正确顺序
- 保证边界
- 在必要时派子 agent 做具体业务代码
- 不要让项目再次退化成一坨耦合原型

## 3. 你必须服从的原则

### 类型安全
- 前后端所有通信都走明确 API / DTO / contract
- 字段名必须严格对齐
- 不允许靠随意对象拼装去赌运行成功

### 声明式
- React 层尽量保持声明式数据流
- battle 页面和外围页的数据应该来自 hook / service / gateway / contract
- 不要到处写命令式状态污染

### 微服务导向
- 前后端边界按服务拆：
  - identity
  - battle
  - replay
  - forum
  - governance
  - social / mails / bots
- 如果发现庞大文件或庞大职责块，主动拆解

### 总设计师不下沉打杂
- 你可以亲自修小问题
- 但中大型业务代码更适合交给单个 worker/subagent
- 保持一票一审

## 4. 你必须先建立的认知

### 当前已经有的
- 前后端 API 基本骨架
- PostgreSQL 落地
- battle 外壳
- replay / mails / rating / contribution / profile / discussion 页面
- replay catalog 与 comments 基础链路
- governance / contribution / social 的初步入口
- 同房等待房间

### 当前仍然缺的
- 稳定 authoritative 多人 battle
- battle 结束后稳定的 result / replay / mails / rating 闭环
- 用户交互细节的一致性
- 数据正确性清洗

## 5. 先读什么
按这个顺序：

1. `AGENTS.md`
2. `now.md`
3. `docs/COMMANDER.md`
4. `docs/REALTIME_MULTIPLAYER_PLAN.md`
5. `docs/REALTIME_ROOM_HEARTBEAT_SEAM.md`
6. `docs/DEMO_BACKEND_RUNBOOK.md`
7. `docs/notes/渲染/第一章：网络架构的基石.md`
8. `docs/notes/渲染/第二章：掩盖延迟的“欺骗”.md`
9. `docs/notes/渲染/第三章：刀光剑影.md`
10. `docs/notes/渲染/第四章：延迟补偿与命中判定.md`

然后看代码：

### 前端 battle 关键点
- `frontend/src/features/battle/page/useBattlePageRuntime.ts`
- `frontend/src/features/battle/renderer/createBattleRuntime.ts`
- `frontend/src/pages/BattlePage.tsx`
- `frontend/src/shared/ui/UserActionDot.tsx`

### 后端 battle / replay / identity / social 关键点
- `backend/src/main/scala/battle/`
- `backend/src/main/scala/replay/`
- `backend/src/main/scala/identity/`
- `backend/src/main/scala/slaydemo/backend/social/`
- `backend/src/main/scala/slaydemo/backend/mails/`
- `backend/src/main/scala/slaydemo/backend/BackendApp.scala`

## 6. 当前优先级顺序

### Priority 1：多人实时 battle
必须先做稳：

1. 等待房间倒计时同步
2. 同房玩家进入同一 authoritative battle
3. 多个玩家的：
   - 移动
   - 开火
   - 技能
   - 受击
   - 死亡
   - 结算
   真正共享同一份 runtime
4. 一个真实玩家加入时替换一个 bot

### Priority 2：battle 收尾闭环
1. battle 正常结束一定写 result
2. result 一定生成 replay
3. result 一定生成 mails
4. rating/profile 一定更新

### Priority 3：交互修补
1. `UserActionDot` 不再躲鼠标
2. mails 支持链接到来源
3. replay / mails / rating 页面压缩废话
4. profile / rating 数据正确性修复

## 7. 你要避免的错误

- 不要再回到“全前端假数据 demo”
- 不要因为页面好看就忽略 battle 主链
- 不要再制造新的本地缓存污染
- 不要把核心状态同时保存在多个相互冲突的地方
- 不要忽视 API 字段对齐
- 不要在一个超大文件里继续堆逻辑

## 8. 当前未清的已知 bug

- `UserActionDot` 仍可能躲鼠标
- replay 列表有时不出现刚刚打完的局
- 某些 replay 时间线不完整
- battle 只剩 bot 时的收尾不稳定
- 多人 battle 有橡皮筋/同步问题
- rating 曲线和历史变化存在异常记录

## 9. 交接原则
你接手后，不需要重新发明整体路线。

路线已经明确：

> 先把多人 battle authoritative 主链做稳，
> 再补 replay / mails / governance / profile 的真实闭环。

如果一时无事可做：

1. 回到 `now.md`
2. 回到 `docs/notes/渲染/`
3. 重新找 battle 主链上的下一个最小堵点
4. 继续推进

不要空转，不要停。
