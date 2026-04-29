# Now

更新时间：2026-04-29 Asia/Shanghai

## 当前状态

项目当前处在“扩展性与数据闭环收口”阶段。

已经完成的基础可玩闭环：

- 已登录账号可以进入 authoritative multiplayer battle，同一局内进行服务器权威对战。
- BattlePage 已完成阶段性渲染打磨：本地玩家和远端玩家的显示链路、枪口反馈、弹道、武器栏、拾取、火箭 AoE、加特林热量、后坐力、击中反馈都已经进入可玩状态。
- `GameScene.ts` 已完成硬解耦验收，现在是 scene shell / renderer host / glue layer。报告见 `docs/GAMESCENE_HARD_GATE_COMPLETION_REPORT.md`。
- battle result、replay、rating、profile、mails 已形成基础闭环：对局结束后会生成战报、回放、评分变化和站内信。
- Visitor 正式开战和正式写入已经在后端拦截；本轮又补上了前端 rating/profile/replay 展示边界、本地 battle truth fallback 边界和后端投影容错，避免 Visitor/访客虚拟身份进入榜单、档案、回放、站内信和评分写入链路。

还没有完成到最终愿景的部分：

- 美术仍以程序化绘制和现有素材为主，还没有达到最终“金属战争 + 空洞骑士剪影 + 技能特效”的统一资产质量。
- 地图、武器、技能、bot 虽然已经开始 catalog/profile/SDK 化，但还没有做到完整内容包、社区 bot 插件或地图编辑器级别。
- 数据闭环仍需要继续清理历史脏数据、重复投影边界、同账号多标签占位和 result/replay/rating/profile 一致性。
- 聊天系统暂缓；课程风格的大范围类型安全/声明式/微服务整理也暂缓，等功能闭环更稳后再集中做。

## 最近完成

### GameScene 硬解耦

已完成。`GameScene.ts` 当前不再直接实现 arena builder、world view factory、projectile update、hit/damage/respawn、pickup lifecycle、weapon runtime、display label、geometry resolver 等主业务链。

### Battle 内容扩展入口

已完成第一轮：

- 后端 battle 内容抽到 `BattleContentCatalog`。
- 前端 battle 内容抽到 `battleContentCatalog.ts`。
- 前端地图默认配置抽到 `battleMapCatalog.ts`。
- 前端本地 weapon runtime profile 已落地。
- 前端 skill runtime profile 已落地。
- bot SDK 最小边界已落地，可通过 strategy command 接入外部 bot。

### Authoritative Gatling

已完成。前后端都使用 heat / overheat 模型，不再是前端热量、后端弹匣的双语义。

### Visitor 数据展示边界

已完成本轮第一刀：

- 新增 `frontend/src/features/identity/identityHandlePolicy.ts`。
- `ratingGateway` 过滤空 handle、Visitor、guest、anonymous、访客、游客、未登录等 visitor-like 身份。
- `profileGateway` 对空/Visitor-like handle 直接返回 `undefined`，不再把空 handle 补成“访客”档案。
- `replayGateway` 不再使用 Visitor-like handle 做 rating hydration 查询。
- 文档见 `docs/DATA_CLOSURE_VISITOR_GUARDRAILS.md`。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。

### 后端 Visitor-like 身份边界

已完成本轮第二刀：

- 新增 `backend/src/main/scala/shared/rules/HandleRules.scala`。
- 后端 visitor-like 判断现在覆盖空身份、`Visitor`、`guest`、`anonymous`、`anon`、`访客`、`游客`、`未登录`。
- `BattleRules.isVisitorHandle` 保留老 API，但内部委托共享规则。
- `DefaultIdentityService` 注册、登录、session、账号列表会过滤 visitor-like 正式账号；内置 `admin` 不受影响。
- 文档见 `docs/BACKEND_VISITOR_HANDLE_GUARDRAILS.md`。

验证：

- `npm run backend:compile` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。

### Mail 服务层 Visitor-like owner 边界

已完成本轮第三刀：

- `DefaultMailService.list` 对空 owner 或 visitor-like owner 返回空列表，不读取 repository。
- `DefaultMailService.markRead` 对空 owner 或 visitor-like owner 返回 `false`，不触发 repository。
- `DefaultMailService.create` 对空 owner 或 visitor-like owner 不保存，仅返回原 record，保持调用方签名不变。
- 这会隐藏历史本地 `backend/data/mails.json` 中的 Visitor 邮件，也阻止未来继续写入 visitor-like 邮件。
- 文档已补充到 `docs/BACKEND_VISITOR_HANDLE_GUARDRAILS.md`。

验证：

- `npm run backend:compile` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。

### Authoritative projection Visitor-like 容错

已完成本轮第四刀：

- `AuthoritativeBattleFinishProjector` 现在只给 playable human 生成正式 result。
- replay owner 选择顺序改为：可玩真人胜者、排名最高的可玩真人、server summary。
- 如果历史或异常状态中混入 Visitor-like 玩家，不会再因为第一个 Visitor-like result/replay 被拒绝而阻断同局真实账号的结算写入。
- `playersLine` 仍保留原始参赛文本，避免历史战局摘要丢信息。
- 文档已补充到 `docs/BACKEND_VISITOR_HANDLE_GUARDRAILS.md`。

验证：

- `npm run backend:compile` 通过。

### 前端本地 battle truth Visitor-like 边界

已完成本轮第五刀：

- `finalizeBattleAndPersist` 现在只把 `normalizePlayableIdentityHandle(getCurrentAuthHandle())` 解析出的 playable 身份作为正式本地结算身份。
- 未登录、Visitor、guest、anonymous、访客、游客、未登录等 visitor-like 结算只返回临时 disabled summary/replay，不写入本地战绩、站内信、rating/profile，也不参与后端 result/replay backfill。
- 最新战报、回放列表、rating/profile 分组、邮件读取和邮件 replay 来源映射都会过滤非 playable 历史记录，避免旧 Visitor 脏数据继续从读取侧露出。
- 文档已补充到 `docs/DATA_CLOSURE_VISITOR_GUARDRAILS.md`。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。

### 前端 auth/local fallback Visitor-like 入口防护

已完成本轮第六刀：

- `authGateway` 现在复用 `identityHandlePolicy` 的 playable handle 规则。
- 本地 fallback 注册和登录会拒绝空 handle、Visitor、guest、anonymous、anon、访客、游客、未登录等 visitor-like 身份。
- `readUsers` / `writeUsers` 会过滤历史 localStorage 中的 visitor-like 账号。
- 本地 session hydrate/current-user 恢复链路不会把 visitor-like 身份恢复成 current playable user；必要时会清理 session。
- 内置 `admin` 保持可用，未改动战斗、邮件、论坛或后端。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。

### 后端 result/replay 历史 Visitor-like 读取侧防护

已完成本轮第七刀：

- `DefaultBattleResultService.list` 现在对 visitor-like 查询 handle 直接返回空，并在 repository 读取后过滤历史 visitor-like result owner。
- result 列表会有限 over-fetch 后再过滤，避免少量历史脏记录占住榜单/档案读取窗口。
- `DefaultReplayService.list/load` 现在隐藏 visitor-like replay owner；comment 读取要求 replay 本身仍可见，并过滤 visitor-like comment author。
- `addComment` 不再允许给隐藏 replay 写评论。
- 后端当前没有独立 rating/profile 读服务；后端 rating/profile 由 result 读取和 replay hydration 派生，所以本轮防护覆盖了后端派生入口。
- 目前没有直接删除 `backend/data`，历史脏记录仍保留在文件中，但正式读取侧不会返回。

验证：

- `npm run backend:compile` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。

### Battle result/replay/mail 幂等第一轮

已完成本轮第八刀：

- `DefaultBattleResultService.record` 现在会先查同一 `handle + battleId` 的既有 result。
- 重复 result 提交会保留既有 `ratingBefore/ratingDelta/ratingAfter`，避免 completed-session 恢复、多标签页或重复 projection 把评分三元组再次覆盖成累计后的值。
- 重复 result 仍允许更新展示字段，例如战报文本、时间、placement、loadout，用于补全更完整的后续战报。
- `FileBattleResultRepository.save` 会按同一 `battleId + handle` 逻辑键清掉旧重复行，再保存当前 result，避免文件存储中残留同逻辑重复记录。
- replay 幂等已由 `replayId` 主键/Map key 承担；mail 幂等已由 `mail-battle-${resultId}` 和 `(ownerHandle, id)` 承担。

验证：

- `npm run backend:compile` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。

残留风险：

- Postgres 正常路径已由 service + `result_id` upsert 覆盖，但数据库层还没有 `lower(battle_id), lower(handle)` 唯一约束；极端并发首写和 battleId 大小写异常仍应通过后续小迁移封死。

### Postgres battle result 逻辑唯一约束

已完成本轮第九刀：

- `PostgresBattleResultRepository` 初始化时会锁定 `battle_results`，按 `lower(trim(battle_id)), lower(trim(handle))` 逻辑键清理历史重复行。
- 清理策略保留 `finished_at DESC, result_id ASC` 排序下的一条记录，删除同逻辑键其余记录。
- 初始化随后创建唯一 expression index：`battle_results_logical_key_unique_idx`。
- 原有 `result_id` 主键和 `ON CONFLICT (result_id) DO UPDATE` 保持不变；新增唯一索引只是额外封死大小写/空白归一后的逻辑重复。

验证：

- `npm run backend:compile` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。

残留风险：

- 极端并发下，如果两个不同 `result_id` 但同一逻辑键的首写同时发生，数据库会拒绝其中一个重复写入；当前服务层还没有把这个 unique violation 转成优雅幂等返回。正常重复 projection/backfill/multi-tab 路径已由服务层查询和 `result_id` upsert 覆盖。

### 历史数据闭环只读审计工具

已完成本轮第十刀：

- 新增 `scripts/audit-data-closure.mjs`，通过 `npm run audit:data-closure` 执行。
- 脚本只读 `backend/data/*.json`，不删除、不重写、不迁移数据文件。
- 当前报告结果：Visitor-like battle results 为 `6 / 901`，Visitor-like mails 为 `16 / 1965`，Visitor-like replay records 为 `4 / 514`，Visitor-like identity accounts 为 `0 / 808`。
- battle result duplicate logical groups 为 `0`，说明当前文件数据中没有同一 `lower(trim(battleId)) + lower(trim(handle))` 的重复 result 组。
- `backend/data` 在脚本运行后没有 git 变更。

验证：

- `npm run audit:data-closure` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。

### Authoritative battle 规则小收口第一轮

已完成本轮第十一刀：

- 一命模式复核：本地 respawn controller 当前不产生复活效果，后端淘汰后 `respawnMs = 0`，后端按 alive 数和时间上限结束战斗。
- 新局时间复核：本地新局初始 snapshot 为 `elapsedMs = 0`，`startNewMatch` 会发布新 session epoch、清 active/completed session、清 deadline 和 battleId。
- 拾取武器复核：本地和后端拾取武器都只加入/补给武器，不主动切换当前武器。
- 滚轮切枪复核：滚轮仍走 `switchWeaponDirection`，并继续上传 authoritative command。
- 数字键切枪补齐：新增 `switchWeaponIndex`，前端本地、authoritative input、DTO、authoritative client、后端 route、`BattleCommandRequest`、后端 runtime 保持同名链路，支持 `1-4` 槽位。
- 火箭范围攻击修复：本地火箭直击爆点改为弹道圆交点，splash 排除发射者，并补齐 6px shooter-advantage hit 半径以贴近后端。
- 加特林复核：heat、overheat lock、冷却、后坐力公式前后端一致；本轮命中半径补齐也覆盖加特林 direct-hit 链路。

验证：

- `npm run build` 通过。
- `npm run backend:compile` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。

残留风险：

- 未启动真实后端与浏览器双端手感 smoke；本轮验证限于类型构建、后端编译和代码审计。
- 后端 authoritative 切枪仍是立即生效语义，本地保留切枪过渡表现；当前与既有滚轮切枪语义一致，未在本轮引入后端切枪延迟。

### 地图扩展 catalog 第一轮

已完成本轮第十二刀：

- 后端新增 `BattleMapCatalog`，默认地图集中声明 `mapId`、`displayName`、`themeId`、`worldSize`、`heroSpawnPoints`、`innerObstacles`、`weaponPickupDefinitions`、`itemPickupDefinitions`。
- `AuthoritativeArenaGeometry` 的 `WorldSize` 和内部障碍物来源改为 `BattleMapCatalog.defaultMap`，geometry 只保留边界障碍生成、碰撞和运动解析职责。
- `BattleContentCatalog.spawnPoints`、`weaponPickupDefinitions`、`medkitPickupDefinitions` 保持原公开名称，但数据源改为默认地图 catalog。
- 前端 `WeaponPickupDefinition` 字段从 `weaponId` 改为 `pickupId`，与后端 map schema 同名；运行时 `WeaponPickup.weaponId` 仍保留，避免影响战斗 runtime、回放和 HUD 现有语义。
- 默认地图显示名保持中文：`默认工业竞技场`。

验证：

- `npm run build` 通过。
- `npm run backend:compile` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。

残留风险：

- 前后端地图 catalog 仍是双语言双文件维护，只是字段形状和后端内部重复硬编码已经收敛；后续若要完全单源，需要引入共享 JSON/生成器或契约校验脚本。
- 地图视觉层、碰撞层、边界生成策略、tile/asset layer 还没有声明式化。

### 武器 definition schema 同名第一轮

已完成本轮第十三刀：

- 前端 `WeaponDefinition` 字段改为与后端 `BattleContentCatalog.WeaponDefinition` 同名：`projectileSpeedPerSecond`、`projectileDamage`、`projectileLifetimeMs`、`projectileRadius`。
- `recoilStrength` 从前端 runtime profile 移入前端 weapon content definition，和后端同一语义位置对齐。
- `WeaponDefinition.reserveAmmo` 改成 `number`，Gatling 使用 `0`，与后端 schema 对齐。
- 运行时 `WeaponState.reserveAmmo` 语义保持不变：heat weapon 创建时仍转成 `null`，所以 HUD、reload、heat 逻辑仍按原方式工作。
- projectile factory、枪口出生距离、后坐力读取点已更新到新字段名。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。

残留风险：

- 前后端 weapon catalog 仍是双源维护；本轮先消除字段名不一致和 recoil 双源，后续可加契约 diff/audit 脚本或共享 JSON 生成。
- 本轮未改后端 weapon 数值，也未做真实多人局浏览器 smoke。

### 技能 definition/profile schema 第一轮

已完成本轮第十四刀：

- 前端 `SkillDefinition` 从“所有技能共享一堆隐式 0 字段”改为显式 union profile。
- 技能 profile 现在包含 `skillKind`、`activationKind`、`effectType`、`cooldownMs`、`activeMs`，并按技能类型显式声明 `range`、`radius`、`durationMs`、`distance`、`speedMultiplier`。
- 后端 `BattleContentCatalog` 新增 `SkillDefinition` 与 `skillDefinitions` map；原有 `dashDistance`、`dashCooldownMs`、`blinkRange`、`freezeRadius` 等公开 val 保持不变，但改为从 profile 派生。
- 前端 Dash/Blink/Freeze active 状态计时不再硬编码，改从 `SKILL_DEFINITIONS` 读取。
- 前端 Dash `activeMs` 从旧硬编码 `220` 对齐到后端 `180`；实际 dash 位移播放时长仍保持原 `140ms`，本轮不改手感。

验证：

- `npm run build` 通过。
- `npm run backend:compile` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。

残留风险：

- 技能 profile 仍未完全驱动后端 runtime 分支；后端 cast 逻辑仍按 Dash/Blink/Freeze 三个方法执行，但数值来源已经收束到 profile。
- 前后端技能 profile 仍是双源维护；后续适合加契约 diff/audit，或引入共享 JSON 生成。

### Bot 插件 manifest 第一轮

已完成本轮第十五刀：

- 新增 `BotPluginManifest`，固定 `apiVersion = bot-sdk/v1`，声明 `pluginId`、`displayName`、`version`、`author`、`description`、`strategyIds`、`botIds`、`permissions`。
- 新增内置本地机器人 manifest：`builtin-local-bots`，覆盖当前五个 bot profile 和五个策略标签。
- 新增 manifest discovery helper：按 strategy id 或 bot id 查询插件来源，并提供重复 id 校验 helper。
- `botRegistry` 只新增只读 `getBotPluginManifestForProfile`，不参与 bot 决策。
- 新增 `npm run audit:bot-plugins`，静态检查 manifest apiVersion、pluginId、strategyIds、botIds 和重复 id。

验证：

- `npm run build` 通过。
- `npm run audit:bot-plugins` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。

残留风险：

- 目前只是 manifest/discovery/test harness 第一轮，还没有动态加载外部 bot 包。
- 社区 bot 仍需要后续定义目录结构、提交格式、示例策略、沙箱限制和离线对战测试。

### 主界面视觉重构第一轮

已完成本轮第十六刀：

- Home/Lobby 中心品牌改为 `OMEGALOMANIA`，副标题强化为“快节奏 3v3 竞技场 · 6 人钢铁大厅待命”。
- 主 CTA 改为“开始游戏”，配装入口改为“调整配装”，战备状态、榜单表头、预览浮层 eyebrow 等明显英文 UI 已中文化。
- 首页 menu body 增加战备品牌牌、当前主武器/战术模块、核心在线/装甲锁定/投放就绪三段状态条。
- `lobby-shell.css` 增加金属战争大厅第一轮视觉：机械环背景、粒子漂移、扫描线、金黑铜蓝面板、角标、内发光、榜单高亮和移动端约束。
- 本轮只改视觉结构和文案，没有改 auth、routing、battle、mail/rating 数据读取或后端。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。

残留风险：

- 这仍是 CSS/布局第一轮，没有新增真正的高质量背景资产、角色立绘或按钮贴图。
- 后续若继续贴近参考图，需要做可复用大厅组件层、真实背景素材/动效资产、以及邮件/好友/配装入口的视觉联动。

### Battle content 契约审计脚本第一轮

已完成本轮第十七刀：

- 新增 `npm run audit:battle-content`。
- 脚本只读前后端 catalog，不执行 TS/Scala 业务代码，不写数据文件。
- 审计默认地图：`mapId`、`themeId`、`worldSize`、`heroSpawnPoints`、`innerObstacles`、weapon/item pickup definitions。
- 审计四种武器定义：projectile、cooldown、reload、damage、radius、splash、ammo、heat、recoil 等关键字段。
- 审计三种技能定义：`skillKind`、`activationKind`、`effectType`、`cooldownMs`、`activeMs`、`range/radius/duration/distance/speedMultiplier`。
- 失败时会输出具体 path 和 frontend/backend 值；成功时输出地图、障碍、pickup、weapon、skill 摘要。

验证：

- `npm run audit:battle-content` 通过。
- `npm run build` 通过。
- `npm run backend:compile` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。

残留风险：

- 脚本是轻量静态解析，不是完整 TS/Scala AST parser；当前 catalog 写法受控时足够有效。
- 长期最稳方案仍是共享 JSON/生成器或正式 schema/codegen。

### BattlePage SVG 美术资产第一轮

已完成本轮第十八刀：

- 新增 `frontend/public/assets/battle/**` SVG 战斗资产第一批。
- Arena 资产覆盖金属地板、虚空外场、面板 tile、trim tile、封闭 crate、墙段、金属碎石。
- Actor 资产新增原创圆润暗色骑士俯视剪影，暂由多个 hero texture key 复用，并继续依赖现有 tint 区分身份。
- Projectile 和 pickup 资产覆盖 energy bullet、rocket shell、手枪、加特林、霰弹枪、火箭炮图标。
- `frontend/src/game/constants.ts` 只替换 `ASSET_PATHS` 路径，不改 battle runtime、hitbox、地图、武器、技能、后端或 `GameScene.ts`。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。
- 静态 asset path 审计通过：`ASSET_PATHS` 中 28 个 `/assets/...` 路径都能落到 `frontend/public/assets`。
- SVG 静态扫描未发现 `<text>` 或字体类元素。
- `bp28-render-feel-smoke` headless `MixedMovement` 通过：`ok=true`、`sameBattle=true`、两端进入 playing、warnings `0`、小地图静态层重绘 delta `0`。

残留风险：

- 还没有做人工 headful 视觉审美验收；当前确认的是构建、路径有效和 headless battle smoke 未被资产阻断。
- SVG 第一轮仍是轻量 repo-native 资产，不等于最终商业级序列帧和完整素材包。

### BattlePage hero silhouette variants 第二轮

已完成本轮第十九刀：

- 新增 8 个原创 64x64 俯视 hero SVG variants：moss knight、gold lancer、ember brute、violet shade、cyan scout、red wraith、steel sentinel、bone rover。
- `player` 继续使用 `hero_body_dark_knight.svg`，其余 hero texture key 分别指向不同 body SVG。
- 本轮只改 `ASSET_PATHS` 中 hero texture 路径和新增 actor SVG，没有改 `GameScene.ts`、runtime、后端、地图、武器、技能、HUD 或数值。

验证：

- actor SVG 尺寸审计通过：9 个 actor SVG 均为 `64x64` / `viewBox="0 0 64 64"`。
- 静态 asset path 审计通过：`ASSET_PATHS` 中 28 个 `/assets/...` 路径都能落到 `frontend/public/assets`。
- SVG 静态扫描未发现 `<text>` 或字体类元素。
- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。
- `bp28-render-feel-smoke` headless `MixedMovement` 通过：`ok=true`、`sameBattle=true`、两端进入 playing、warnings `0`、小地图静态层重绘 delta `0`。

残留风险：

- Phaser `setTint` 会继续叠加到 SVG 基础色上，最终角色辨识度仍需要人工 headful 视觉审美验收。
- 当前只有 body variants，武器 overlay 和动作帧仍是程序化/静态组合，未达到最终商业动画资产级别。

### BattlePage weapon overlay 可读性第二轮

已完成本轮第二十刀：

- `HeroView` 新增 renderer-only weapon overlay primitives，每个 hero 固定 3 个轻量 Phaser GameObject。
- Pistol、RocketLauncher、Gatling、Shotgun 现在有不同手持轮廓：短手枪、粗长橙色火箭筒、双管加特林热芯、宽短霰弹双管。
- overlay 随 hero display facing 同步位置与旋转，并在死亡/隐藏分支同步隐藏。
- 本轮只改 `frontend/src/features/battle/renderer/entities/worldViewFactory.ts` 的视觉层，没有改 hitbox、projectile spawn、伤害、射程、后坐力、冷却、弹药、同步、AI、后端或 `GameScene.ts`。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。
- `bp28-render-feel-smoke` headless `MixedMovement` 通过：`ok=true`、`sameBattle=true`、两端进入 playing、warnings `0`、小地图静态层重绘 delta `0`、VFX active transient count `0`。

残留风险：

- 仍未做人工 headful 视觉审美验收；headless smoke 只能确认加载、同步、基础性能和无 warning。
- overlay 仍是程序化 primitive，不是最终武器 sprite sheet；后续如果要更接近商业美术，需要武器 SVG/PNG overlay 或序列帧。

### BattlePage weapon overlay helper 抽离

已完成本轮第二十一刀：

- 新增 `frontend/src/features/battle/renderer/entities/heroWeaponOverlayView.ts`。
- 从 `worldViewFactory.ts` 抽出 `HeroWeaponOverlayView`、overlay 创建、显隐同步、四类武器 overlay 同步和内部 primitive helper。
- `worldViewFactory.ts` 从约 `1844` 行降到约 `1630` 行，只保留创建/隐藏/同步调用。
- 本轮是边界清理，视觉参数保持原值；未改 gameplay、domain、backend、AI、`GameScene.ts` 或任何武器/技能数值。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。
- `bp28-render-feel-smoke` headless `MixedMovement` 通过：`ok=true`、`sameBattle=true`、两端进入 playing、warnings `0`、小地图静态层重绘 delta `0`、VFX active transient count `0`。

残留风险：

- 这是纯搬移型重构，headless smoke 已覆盖基础加载和同步；人工视觉对比仍需要后续 headful 检查。
- `worldViewFactory.ts` 仍然偏大，后续 pickup view、projectile view、slow field view 也适合逐步拆出。

### BattlePage pickup presentation helper 与可读性增强

已完成本轮第二十二刀：

- 新增 `frontend/src/features/battle/renderer/entities/pickupViewPresentation.ts`。
- 从 `worldViewFactory.ts` 抽出 pickup style、`PickupView` 类型、weapon/item pickup 创建、显隐和同步逻辑。
- `worldViewFactory.ts` 从约 `1630` 行降到约 `1478` 行，继续向 renderer host/factory 边界收敛。
- weapon pickup 和医疗包 pickup 新增内环、标签底板和 glint；中文 label 仍由文本渲染，不把文字写进资产或 primitive。
- 本轮没有改 pickup radius、auto pickup、respawn、weapon ammo、item effect、map spawn、后端、domain type、`GameScene.ts` 或 battle rules。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。
- `bp28-render-feel-smoke` headless `MixedMovement` 通过：`ok=true`、`sameBattle=true`、两端进入 playing、warnings `0`、小地图静态层重绘 delta `0`、VFX active transient count `0`。

残留风险：

- pickup 的 label 底板/glint 亮度仍需要人工 headful 审美验收，尤其要确认不会遮挡角色脚下状态圈。
- `worldViewFactory.ts` 仍直接包含 projectile view、slow field view、hero interpolation 等逻辑，后续仍可继续拆。

### BattlePage arena obstacle skin helper 与封闭轮廓增强

已完成本轮第二十三刀：

- 新增 `frontend/src/features/battle/renderer/arena/obstacleSkinPresenter.ts`。
- 从 `arenaBuilder.ts` 抽出 static obstacle metal skin、corner plates、footprint cues 和 border 判断。
- `arenaBuilder.ts` 现在更集中于 arena/world construction、physics static body、`obstacleBounds` 和 occludables 注册。
- crate/wall 的视觉轮廓补强了闭合顶边、底边、四角和箱体封闭感，降低 “N 型开口” 误读。
- 本轮没有改 `setDisplaySize`、`wallBodies.add`、`obstacleBounds.push`、`registerOccludable`、`INNER_OBSTACLES`、`WORLD_SIZE`、map catalog、backend、`GameScene.ts`、hitbox、碰撞或移动边界。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。
- `bp28-render-feel-smoke` headless `MixedMovement` 通过：`ok=true`、`sameBattle=true`、两端进入 playing、warnings `0`、HUD obstacle count 仍为 `170`、小地图静态层重绘 delta `0`、VFX active transient count `0`。

残留风险：

- 每个 obstacle 增加了少量视觉 primitive，当前 smoke 未显示问题，但低端设备上仍需后续真实试玩观察。
- `arenaBuilder.ts` 中 floor/boundary/decorations 仍可进一步拆成 presenter/helper，但本轮先只处理 obstacle skin。

### BattlePage arena background/boundary presenter 抽离

已完成本轮第二十四刀：

- 新增 `frontend/src/features/battle/renderer/arena/arenaBackgroundPresenter.ts`。
- 从 `arenaBuilder.ts` 抽出 arena background、metal floor、panel seams、out-of-bounds shadow、boundary readability layer 和边界提示线。
- `arenaBuilder.ts` 现在委托 `createArenaPresentationLayers(scene)` 创建纯表现层，自己继续保留 pickup/decorations、physics border walls、inner structures、`obstacleBounds` 和 occludables 注册。
- 本轮没有改 `createBorderWalls`、`createInnerStructures`、`createStaticObstacle`、`setDisplaySize`、`wallBodies.add`、`obstacleBounds.push`、`registerOccludable`、map catalog、backend、`GameScene.ts`、hitbox、碰撞、出生点或移动边界。
- `arenaBuilder.ts` 已降到约 `220` LOC，后续可以继续拆 pickup pads / decorations，让 builder 更接近 world construction host。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。
- `bp28-render-feel-smoke` headless `MixedMovement` 通过：`ok=true`、`sameBattle=true`、两端进入 playing、warnings `0`、HUD obstacle count 仍为 `170`、小地图静态层重绘 delta `0`、VFX active transient count `0`。

残留风险：

- 本轮是表现层搬移，headless smoke 能证明加载、同局、HUD 和基础 VFX 指标稳定，但还不是 headful 像素级审美验收。
- `arenaBuilder.ts` 中 pickup pads 和 decorative occludables 仍在 builder 内，下一刀适合继续拆到 decoration/pickup-pad presenter。

### BattlePage arena decoration/pickup pad presenter 抽离

已完成本轮第二十五刀：

- 新增 `frontend/src/features/battle/renderer/arena/arenaDecorationPresenter.ts`。
- 从 `arenaBuilder.ts` 抽出 `createPickupPads(scene)` 和 `createArenaDecorations(scene, occludables)`。
- decorative occludables 注册语义保留：pylon 仍以 `baseAlpha = 0.96` 注册，machinery 仍以 `baseAlpha = 0.92` 注册，并继续用 `getBounds()` 写入 `OccludableView[]`。
- `arenaBuilder.ts` 现在约 `117` LOC，只保留 arena construction host、presentation/decorations 调用、border wall、inner structures、static obstacle physics、`obstacleBounds` 和 occludable 注册。
- 本轮没有改 `createBorderWalls`、`createInnerStructures`、`createStaticObstacle`、physics bodies、`obstacleBounds`、`INNER_OBSTACLES`、`WORLD_SIZE`、pickup spawn points、map catalog、backend、`GameScene.ts`、hitbox 或 collision。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。
- `bp28-render-feel-smoke` headless `MixedMovement` 通过：`ok=true`、`sameBattle=true`、两端进入 playing、warnings `0`、HUD obstacle count 仍为 `170`、小地图静态层重绘 delta `0`、VFX active transient count `0`。

残留风险：

- `arenaBuilder.ts` 已经收敛到很小，后续 BattlePage 渲染边界的主要大文件变成 `worldViewFactory.ts`、`battleFeedbackSceneBridge.ts`、`sceneVfxController.ts`。
- decorative presenter 仍是程序化美术，真实审美还需要后续 headful 人工验收和资产替换。

### BattlePage projectile/slow-field view presenter 抽离

已完成本轮第二十六刀：

- 新增 `frontend/src/features/battle/renderer/entities/projectileAndFieldViewPresentation.ts`。
- 从 `worldViewFactory.ts` 抽出 `ProjectileView`、`SlowFieldView`、`ProjectileInterpolationBuffer`、projectile view 创建/同步/销毁、remote authoritative projectile interpolation、slow-field view 创建/同步。
- `worldViewFactory.ts` 继续保留 `WorldViewState` 字段名和 `getProjectileDisplayPosition(...)` wrapper，对外 API 不变。
- 本地 projectile 仍不走 authoritative interpolation；远端 projectile 仍使用原 buffer/delay/snap/smoothing 参数。
- projectile 渲染参数保持原值：bullet/rocket texture、scale、tint、trail/glow style、TTL alpha、destroy 流程未改。
- slow-field 渲染参数保持原值：fill/rim depth、alpha、stroke、TTL alpha 语义未改。
- `worldViewFactory.ts` 从约 `1312` LOC 降到约 `998` LOC。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。
- `bp28-render-feel-smoke` headless `MixedMovement` 通过：`ok=true`、`sameBattle=true`、两端进入 playing、warnings `0`、HUD obstacle count 仍为 `170`、小地图静态层重绘 delta `0`、VFX active transient count `0`。

残留风险：

- 这是搬移型重构，未做人眼 headful 视觉对比；当前只确认构建、同局、HUD、VFX 生命周期稳定。
- `worldViewFactory.ts` 仍包含 hero creation/readability/local motion streak/remote hero interpolation/indicator/pickup sync 等职责，下一刀适合拆 hero readability 或 remote hero interpolation。

### BattlePage remote hero interpolation helper 抽离

已完成本轮第二十七刀：

- 新增 `frontend/src/features/battle/renderer/entities/remoteHeroInterpolationView.ts`。
- 从 `worldViewFactory.ts` 抽出 `RemoteHeroInterpolationBuffer`、远端 hero sample 记录、buffer cleanup、插值、fallback smoothing、render-time 解析、facing interpolation、finite vector 和 smoothing helper。
- `WorldViewState` 仍保留 `remoteHeroInterpolationBuffers` 和 `scratchActiveRemoteHeroIds` 字段名，对外结构不变。
- `syncHeroViews(...)` 的关键分支不变：本地玩家仍优先 `localHeroDisplayOverride`；只有 `sharedAuthoritativeRuntime && !isPlayer && remoteAuthoritativeHeroIds.has(hero.heroId)` 的远端英雄走插值。
- cleanup 语义不变：非 shared authoritative runtime 清空 buffers；shared 时只保留 alive、非玩家、在 `remoteAuthoritativeHeroIds`、且已有 hero view 的 heroId。
- 插值参数保持原值：snap distance `150`、smoothing `58`、interpolation delay `70`、buffer cap `10`、position epsilon `0.05`、facing epsilon `0.001`。
- `worldViewFactory.ts` 从约 `998` LOC 降到约 `804` LOC。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。
- `bp28-render-feel-smoke` headless `MixedMovement` 通过：`ok=true`、`sameBattle=true`、两端进入 playing、warnings `0`、HUD hero count 仍为 `6`、小地图静态层重绘 delta `0`、VFX active transient count `0`。

残留风险：

- 这是搬移型重构，未做人眼 headful 视觉对比；当前确认远端 hero 基础同局显示链路未被破坏。
- `worldViewFactory.ts` 仍包含 hero readability、local motion streak、hero health/weapon cue、pickup sync 和 indicators，下一刀适合拆 hero readability/local motion streak。

### BattlePage local hero motion streak helper 抽离

已完成本轮第二十八刀：

- 新增 `frontend/src/features/battle/renderer/entities/localHeroMotionStreakView.ts`。
- 从 `worldViewFactory.ts` 抽出 `LocalHeroMotionStreakView`、本地移动拖影常量、`createLocalHeroMotionStreakView`、`syncLocalHeroMotionStreaks`、`hideLocalHeroMotionStreaks`。
- `HeroView.localMotionStreaks` 字段保留；`createWorldViewState(...)` 仍只为本地玩家创建 streak，远端为 `null`。
- `syncHeroViews(...)` 仍在 hero display position 更新后调用 streak sync；死亡/隐藏分支仍调用 `hideLocalHeroMotionStreaks(..., true)`。
- 原始速度/强度/alpha/offset/display size 公式保持不变：count `3`、depth `31`、min speed `70`、max speed `470`、decay `0.34`、tint `0x8fe8ff`。
- `worldViewFactory.ts` 从约 `804` LOC 降到约 `718` LOC。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。
- `bp28-render-feel-smoke` headless `MixedMovement` 通过：`ok=true`、`sameBattle=true`、两端进入 playing、warnings `0`、HUD hero count 仍为 `6`、小地图静态层重绘 delta `0`、VFX active transient count `0`。

残留风险：

- 这是本地表现层搬移，headless smoke 不能确认拖影审美，只确认移动输入、同局、HUD、VFX 生命周期未被破坏。
- `worldViewFactory.ts` 仍包含 hero readability、hero health/weapon cue、pickup sync 和 indicators；下一刀适合拆 hero readability/health/cue presenter。

### BattlePage hero readability/health/cue presenter 抽离

已完成本轮第二十九刀：

- 新增 `frontend/src/features/battle/renderer/entities/heroReadabilityView.ts`。
- 从 `worldViewFactory.ts` 抽出 hero readability constants、weapon cue readability styles、readability primitive 创建、weapon cue 同步、slow-field 判断、health ratio/fill 同步和 finite vector helper。
- `HeroView` 外部字段名保留：`shadow/bodyDisc/silhouetteRing/hitRing/statusRing/weaponStock/weaponCue/weaponMuzzle/marker/healthBackground/healthFill` 仍通过类型组合存在。
- alive/dead visibility 分支保持原样；`setHeroWeaponOverlayVisible(...)` 行为不变。
- `syncHeroWeaponOverlayVisuals(...)` 仍在 hero readability sync 内被调用，`weaponKind/displayPosition/displayFacing/radius/cueOriginOffset/cueLength/alpha/strokeAlpha` 参数语义不变。
- radius/depth/color/alpha/stroke、weapon cue formula、health fill formula、slow-field 判断公式保持原值。
- `worldViewFactory.ts` 从约 `718` LOC 降到约 `431` LOC。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。
- `bp28-render-feel-smoke` headless `MixedMovement` 通过：`ok=true`、`sameBattle=true`、两端进入 playing、warnings `0`、HUD hero count 仍为 `6`、小地图静态层重绘 delta `0`、VFX active transient count `0`。

残留风险：

- 这是表现层搬移，headless smoke 不能替代人工 headful 审美验收；当前确认 hero 显示链路和基础指标未被破坏。
- `worldViewFactory.ts` 现在主要剩 pickup sync、indicator sync、hero wrapper/visibility orchestration；下一刀适合拆 pickup sync 或 indicator sync，之后再转向 `battleFeedbackSceneBridge.ts` / `sceneVfxController.ts`。

### BattlePage pickup view sync helper 抽离

已完成本轮第三十刀：

- 新增 `frontend/src/features/battle/renderer/entities/pickupViewSync.ts`。
- 从 `worldViewFactory.ts` 抽出 `syncPickupViews(...)` 主体、`scratchLiveWeaponPickupIds` / `scratchLiveItemPickupIds` 维护、weapon/item pickup view 不可见处理、`syncWeaponPickupView(...)` / `syncItemPickupView(...)` 调用。
- `worldViewFactory.ts` 继续 re-export 同名 `syncPickupViews`，`syncWorldViews(context)` 调用语义保持不变。
- `WorldViewState` 仍保留 `pickupViews`、`itemPickupViews`、`scratchLiveWeaponPickupIds`、`scratchLiveItemPickupIds` 字段名。
- `createWorldViewState(...)` 仍创建初始 weapon/item pickup views；live-id 收集顺序、不可见处理顺序、`snapshot.elapsedMs` 传入顺序保持原样。
- `worldViewFactory.ts` 从约 `431` LOC 降到约 `392` LOC。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。
- `bp28-render-feel-smoke` headless `MixedMovement` 通过：`ok=true`、`sameBattle=true`、两端进入 playing、warnings `0`、HUD pickup count 仍为 `6`、小地图静态层重绘 delta `0`、VFX active transient count `0`。

残留风险：

- 这是 pickup 显示同步搬移，未改变 pickup spawn、radius、auto pickup、respawn、weapon ammo、item effect、map/backend/domain。
- `worldViewFactory.ts` 现在主要剩 indicator sync 和 hero visibility/action orchestration；下一刀适合拆 indicator sync。

### BattlePage prepared skill indicator sync helper 抽离

已完成本轮第三十一刀：

- 新增 `frontend/src/features/battle/renderer/entities/preparedSkillIndicatorViewSync.ts`。
- 从 `worldViewFactory.ts` 抽出 `syncIndicators(...)` 主体、prepared target skill profile 读取、Blink/Freeze 目标合法性判断、range/target indicator 显隐和颜色同步。
- `worldViewFactory.ts` 继续保留同名 `syncIndicators(...)` wrapper，`syncWorldViews(context)` 调用语义保持不变。
- `WorldViewState` 仍保留 `rangeIndicator` 和 `targetIndicator` 字段名；Arc 对象仍由 world view factory 创建。
- 本轮没有改 prepared skill、Blink、Freeze、cooldown、range、indicator radius、valid/invalid color、local hero display override、shared authoritative runtime 或任何 battle rule。
- `worldViewFactory.ts` 从约 `392` LOC 降到约 `324` LOC，已经基本收敛为 view state 装配、hero wrapper/visibility orchestration 和顶层 sync host。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。
- `bp28-render-feel-smoke` headless `MixedMovement` 通过：`ok=true`、`sameBattle=true`、两端进入 playing、warnings `0`、HUD hero count 仍为 `6`、小地图静态层重绘 delta `0`、VFX active transient count `0`。

残留风险：

- 这是 prepared skill 指示器显示同步搬移，headless smoke 覆盖基础加载和同步，但没有专门模拟长时间 prepared-target 瞄准；后续需要 targeted skill smoke 或人工 headful 验证 Blink/Freeze 指示器审美。
- BattlePage 渲染边界的主要大文件已从 `worldViewFactory.ts` 转移到 `battleFeedbackSceneBridge.ts` 和 `sceneVfxController.ts`。

### BattlePage VFX terminal policy helper 抽离

已完成本轮第三十二刀：

- 新增 `frontend/src/features/battle/renderer/effects/projectileTerminalFeedbackPolicy.ts`。
- 从 `battleFeedbackSceneBridge.ts` 抽出 authoritative projectile terminal frame/state/type、key/elapsed/queue 判断、VFX key/drop/strategy、tracer options、rocket radius、tracer noise、`softenColor`、terminal/projectile direction、diagnostic projectile state、remote birth position、nearest hero/distance、local projectile skip predicate。
- `battleFeedbackSceneBridge.ts` 保留职责：seen/played/queued terminal 私有状态、freshness baseline 状态更新、snapshot 编排、通过 `options` 触发 VFX/HUD/diagnostics、hero/pickup/ammo feedback 编排。
- 本轮没有改 BattlePage 角色/地图/素材，没有改枪口、弹道、命中、火箭 AoE、queue limit、budget reason、diagnostic 字段、local projectile skip 或任何视觉数值。
- `battleFeedbackSceneBridge.ts` 从约 `1060` LOC 降到约 `620` LOC。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。
- `bp28-render-feel-smoke` headless `MixedMovement` 通过：`ok=true`、`sameBattle=true`、两端进入 playing、warnings `0`、HUD hero count 仍为 `6`、小地图静态层重绘 delta `0`、VFX active transient count `0`。

残留风险：

- 这是 VFX terminal 结构搬移，headless smoke 能证明基础运行和 transient 回收稳定，但不能替代人工 headful 对枪口/命中/爆炸瞬时观感的逐帧检查。
- `sceneVfxController.ts` 仍然同时包含具体图元绘制、技能反馈、muzzle/tracer 绘制、transient 池和 diagnostics，下一刀应从 transient lifecycle 或 tracer renderer 中选择一个低风险边界。

### BattlePage transient VFX lifecycle helper 抽离

已完成本轮第三十三刀：

- 新增 `frontend/src/features/battle/renderer/effects/transientVfxLifecycle.ts`。
- 从 `sceneVfxController.ts` 抽出 transient record/map/count/head 状态、transient cap/compaction 常量、track/destroy/destroyAll、oldest destroy、release、trim、compact、peak diagnostics 和 diagnostics publish。
- `sceneVfxController.ts` 继续保留具体 VFX 创建、Phaser object/tween 参数、muzzle/tracer/skill/floating text 绘制、ring TTL 更新和 active ring count。
- 通过 `getActiveRingCount` 回调保持 `diagnosticsRoot.vfx.activeRingCount` 字段和值来源等价。
- 继续从 `sceneVfxController.ts` re-export `SceneVfxDiagnosticsSnapshot`，避免外部旧 import 边界断裂。
- 本轮没有改 BattlePage 角色/地图/素材，没有改任何 VFX shape、alpha、depth、blend mode、duration、muzzle/tracer/skill/floating text 参数或 transient cap。
- `sceneVfxController.ts` 从约 `833` LOC 降到约 `680` LOC。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。
- `bp28-render-feel-smoke` headless `MixedMovement` 通过：`ok=true`、`sameBattle=true`、两端进入 playing、warnings `0`、HUD hero count 仍为 `6`、小地图静态层重绘 delta `0`、VFX created/destroyed delta 对齐、active transient count `0`、active ring count `0`。

残留风险：

- 这是 lifecycle 搬移，headless smoke 覆盖 transient 回收与基础 VFX 指标，但不替代 headful 的瞬时视觉验收。
- `sceneVfxController.ts` 仍包含 projectile tracer renderer、muzzle burst、skill feedback 和 floating text 绘制；下一刀适合拆 projectile tracer renderer 或 skill feedback presenter。

### BattlePage projectile tracer renderer helper 抽离

已完成本轮第三十四刀：

- 新增 `frontend/src/features/battle/renderer/effects/projectileTracerVfxRenderer.ts`。
- 从 `sceneVfxController.ts` 抽出 `ProjectileTracerOptions`、`DEFAULT_TRACER_DURATION_MS`、`TRACER_GHOST_RADIUS_SCALE` 和 `createProjectileTracer(...)` 主体。
- `sceneVfxController.ts` 继续保留同名 public facade `createProjectileTracer(options)`，通过 `scene / trackTransient / destroyTransient` 最小依赖委托 renderer。
- 继续从 `sceneVfxController.ts` re-export `ProjectileTracerOptions`，保持外部类型入口不破坏。
- 本轮没有改 BattlePage 角色/地图/素材，没有改 tracer depth、blend mode、alpha、scale、duration、glint randomness、core/ghost/underglow 条件或任何枪口/弹道/命中数值。
- `sceneVfxController.ts` 从约 `680` LOC 降到约 `537` LOC。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。
- `bp28-render-feel-smoke` headless `MixedMovement` 通过：`ok=true`、`sameBattle=true`、两端进入 playing、warnings `0`、HUD hero count 仍为 `6`、小地图静态层重绘 delta `0`、VFX created/destroyed delta 对齐、active transient count `0`、active ring count `0`。

残留风险：

- 这是 tracer renderer 搬移，headless smoke 确认基础运行和 transient 回收，但不替代人工 headful 对弹道瞬时观感的审查。
- `sceneVfxController.ts` 仍包含 muzzle burst、skill feedback、hit confirm、floating text 和 ring TTL；下一刀适合拆 skill feedback presenter 或 muzzle/hit presenter。

### BattlePage skill feedback VFX presenter 抽离

已完成本轮第三十五刀：

- 新增 `frontend/src/features/battle/renderer/effects/skillFeedbackVfxPresenter.ts`。
- 从 `sceneVfxController.ts` 抽出 `SkillFeedbackIntent`、Blink/Freeze/Dash/Reject 技能反馈常量、四个技能反馈绘制方法和私有几何 helper。
- `sceneVfxController.ts` 保留四个同名 public facade，并继续 re-export `SkillFeedbackIntent`，外部调用边界不变。
- transient 对象仍通过 `TransientVfxLifecycle` 统一 track/destroy，技能反馈 presenter 只接收 `scene / trackTransient / destroyTransient` 最小依赖。
- 本轮没有改 BattlePage 角色/地图/素材，没有改 Blink、Freeze、Dash、Reject 的颜色、半径、alpha、depth、blend mode、duration 或 cooldown/skill rule。
- `sceneVfxController.ts` 从约 `537` LOC 降到约 `366` LOC。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。
- `bp28-render-feel-smoke` headless `MixedMovement` 通过：`ok=true`、`sameBattle=true`、两端进入 playing、warnings `0`、HUD hero count 仍为 `6`、小地图静态层重绘 delta `0`、VFX created/destroyed delta 对齐、active transient count `0`、active ring count `0`。

残留风险：

- 这是 skill feedback renderer 搬移，headless smoke 确认基础运行和 transient 回收，但不能替代人工 headful 对 Blink/Freeze/Dash/Reject 瞬时观感的审查。
- `sceneVfxController.ts` 现在主要剩 muzzle burst、impact/dissipate/hit confirm、floating text 和 ring TTL；下一刀适合拆 muzzle/hit presenter 或 floating text presenter。

### BattlePage muzzle/hit VFX presenter 抽离

已完成本轮第三十六刀：

- 新增 `frontend/src/features/battle/renderer/effects/muzzleAndHitVfxPresenter.ts`。
- 从 `sceneVfxController.ts` 抽出 `createImpactSpark`、`createProjectileDissipate`、`createHitConfirm`、`createMuzzleBurst`、`createShockwave`、`MAX_MUZZLE_SPARKS` 和方向几何 helper。
- `sceneVfxController.ts` 保留五个同名 public facade，外部 `GameScene`、feedback bridge、weapon action presenter 的调用边界不变。
- `createMuzzleBurst` 原有 `createPulse(position, radius, color)` 行为通过 `createRingPulse` callback 保留，ring TTL 和 diagnostics 仍由 `sceneVfxController.ts` 管。
- transient 对象仍统一走 `TransientVfxLifecycle.track/destroyObject`，没有直接持久持有 Phaser 临时对象。
- 本轮没有改 BattlePage 角色/地图/素材，没有改枪口、命中、弹道消散、冲击波的 depth、alpha、color、duration、scale、blend mode、stroke 或随机范围。
- `sceneVfxController.ts` 从约 `366` LOC 降到约 `169` LOC。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。
- `bp28-render-feel-smoke` headless `MixedMovement` 通过：`ok=true`、`sameBattle=true`、两端进入 playing、warnings `0`、HUD hero count 仍为 `6`、小地图静态层重绘 delta `0`、VFX created/destroyed delta 对齐、active transient count `0`、active ring count `0`。

残留风险：

- 这是 muzzle/hit renderer 搬移，headless smoke 确认基础运行和 transient 回收，但不能替代人工 headful 对枪口和命中瞬时观感的逐帧审查。
- `sceneVfxController.ts` 现在只剩 ring pulse TTL、projectile tracer facade、skill feedback facade、muzzle/hit facade、floating text 和 diagnostics glue；后续如果继续拆，应优先抽 floating text presenter 或转向 `battleFeedbackSceneBridge.ts` 的编排边界。

### BattlePage hero/pickup feedback presenter 抽离

已完成本轮第三十七刀：

- 新增 `frontend/src/features/battle/renderer/effects/heroAndPickupFeedbackPresenter.ts`。
- 从 `battleFeedbackSceneBridge.ts` 抽出 hero hp/score/ammo/death feedback、weapon/item pickup feedback、`HeroFeedbackState`、`PickupFeedbackState` 和对应 state factory。
- `BattleFeedbackSceneBridge` 继续持有 hero/weapon pickup/item pickup 状态 Map，继续保留 `update/applyAuthoritativeFrame/capture` 主流程和 projectile terminal 主链。
- 文案、颜色、半径、shake duration/intensity、damage/heal/ammo/pickup 条件保持原值：`出局`、`击败 +...`、`-${damage}`、`+hp`、`弹药 +...`、`拾取武器`、`拾取补给` 未改。
- 本轮没有改 BattlePage 角色/地图/素材，没有改 projectile terminal、弹道、命中判定、拾取判定或后端。
- `battleFeedbackSceneBridge.ts` 从约 `620` LOC 降到约 `516` LOC。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。
- `bp28-render-feel-smoke` headless `MixedMovement` 通过：`ok=true`、`sameBattle=true`、两端进入 playing、warnings `0`、HUD hero count 仍为 `6`、HUD pickup count 仍为 `6`、小地图静态层重绘 delta `0`、VFX created/destroyed delta 对齐、active transient count `0`、active ring count `0`。

残留风险：

- 这是反馈编排搬移，headless smoke 能确认基础运行和 VFX 生命周期，但不能专门证明每一种伤害、回血、出局、拾取文案都在人眼观感上完全一致。
- `battleFeedbackSceneBridge.ts` 现在最大剩余职责是 projectile terminal 队列、budget、诊断和 tracer/correction VFX 编排；下一刀应只拆其中一个子边界，避免改动命中/终止语义。

### BattlePage projectile terminal VFX presenter 抽离

已完成本轮第三十八刀：

- 新增 `frontend/src/features/battle/renderer/effects/projectileTerminalVfxPresenter.ts`。
- 从 `battleFeedbackSceneBridge.ts` 抽出 projectile terminal 的 VFX/tracer 展示 helper：snapshot-diff terminal tracer、correction tracer、dissipate、rocket impact/shockwave，以及 authoritative terminal tracer、correction tracer、reason VFX。
- `BattleFeedbackSceneBridge` 继续持有 projectile terminal queue、played/seen sets、budget selection、diagnostics recording、freshness baseline、capture 主流程。
- VFX 顺序和参数保持原值：impact spark weak/strong、pulse radius、shockwave radius、dissipate、tracer option builders、soften color、rocket splash visual radius 都未改。
- 本轮没有改 BattlePage 角色/地图/素材，没有改 projectile terminal 生成、终止原因、队列上限、每帧 budget、diagnostics 字段或命中语义。
- `battleFeedbackSceneBridge.ts` 从约 `516` LOC 降到约 `432` LOC。

验证：

- `npm run build` 通过。
- `git diff --check` 通过，仅有既有 LF/CRLF 提示。
- `bp28-render-feel-smoke` headless `MixedMovement` 通过：`ok=true`、`sameBattle=true`、两端进入 playing、warnings `0`、HUD hero count 仍为 `6`、HUD pickup count 仍为 `6`、小地图静态层重绘 delta `0`、VFX created/destroyed delta 对齐、active transient count `0`、active ring count `0`。

残留风险：

- 这是 terminal VFX presenter 搬移，headless smoke 覆盖基础运行和 transient 回收，但不能逐帧确认所有 terminal reason 的视觉观感。
- `battleFeedbackSceneBridge.ts` 现在主要剩 terminal 状态队列、diagnostics、snapshot capture 和 remote projectile birth feedback；下一刀若继续拆，应优先拆 remote projectile birth 或 terminal diagnostics recorder，避免同一票混入多种职责。

## 当前正在做

当前主线：BattlePage renderer host 边界继续收口。BattlePage 角色素材已按用户要求恢复旧 Kenney top-down PNG，后续暂不再碰 BattlePage 角色/地图等素材方向；结构层继续推进。`arenaBuilder.ts` 已收敛到约 `117` LOC，`worldViewFactory.ts` 已从约 `1312` LOC 降到约 `324` LOC，`battleFeedbackSceneBridge.ts` 已从约 `1060` LOC 降到约 `432` LOC，`sceneVfxController.ts` 已从约 `833` LOC 降到约 `169` LOC；下一步在不改素材和视觉参数的前提下，拆 `battleFeedbackSceneBridge.ts` 的 remote projectile birth 或 terminal diagnostics 子边界，或收掉 `sceneVfxController.ts` 的 floating text 小尾巴。

扩展性基础第一轮已经覆盖：

- 地图：已完成后端默认 map catalog 和前后端 pickup id 字段同名第一轮。
- 武器：已完成字段同名和 recoil 单源第一轮；下一步考虑契约 diff/audit，而不是继续手工比对。
- 技能：已完成 `activationKind/effectType/activeMs` profile 第一轮；后续考虑契约 diff/audit，暂不直接重写 cast runtime。
- Bot：已完成 manifest/discovery/test harness 第一轮；下一步可做示例外部策略模板和离线 bot 对战 harness。

下一阶段候选：

- BattlePage world view 剩余边界整理：把 indicator sync 从 `worldViewFactory.ts` 拆成 focused helper，让主工厂只保留 view state 装配和顶层同步编排。
- 主界面视觉第二轮：拆出更清晰的大厅面板组件、压缩 CSS 叠层、做邮件/好友/配装入口的细化。
- Bot 社区第二轮：示例外部策略模板和离线 bot 对战 harness。

## 下一步计划

1. BattlePage VFX controller 边界整理第二轮。
   预计：2-5 小时。
   目标：继续拆 `battleFeedbackSceneBridge.ts` 的 remote projectile birth / terminal diagnostics focused helper，或收掉 `sceneVfxController.ts` 的 floating text 小尾巴；不改变枪口、弹道、命中、火箭 AoE、技能反馈或 transient 回收语义。

2. 主界面视觉重构第二轮。
   预计：0.5-1.5 天。
   目标：组件化大厅面板、收束 CSS 叠层、强化邮件/好友/配装入口。

3. Bot 社区化第二轮。
   预计：0.5-1 天。
   目标：补一个可复制的外部 bot 策略模板和离线 harness。

4. 启动、验收、交付脚本。
   预计：0.5-1 天。
   目标：一键关闭旧进程、一键启动前后端、一键 build/backend compile/smoke，减少端口占用和 sbt pipe 误解。

## 暂缓事项

- 聊天系统暂缓。好友申请和站内信先维持现状，之后再统一 notification/message channel。
- 课程风格大重构暂缓。包括全项目 var/val 清理、JSON parser/renderer 大迁移、微服务边界大拆分、前后端 DTO 全量契约迁移。
- 大规模数值平衡暂缓。当前只做保守微调，最终手感需要实战试玩后再定。

## 总体时间判断

不包含课程风格大重构：

- 可展示完整闭环：约 2-4 天。
- 扩展性、数据闭环、主界面和基础美术统一到较完整状态：约 5-10 天。
- 接近商业级 polish：10 天以上，主要消耗在素材、动画、音效、平衡、稳定在线服务和反复试玩。

当前执行策略：暂不继续改 BattlePage 角色/地图素材；继续推进 renderer host 结构边界、主界面视觉结构、Bot/地图/技能扩展性和数据闭环，不切换到聊天系统，不做课程风格大改。
