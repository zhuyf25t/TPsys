# 后端结构审查结论

本文记录当前 `multimodule` 分支后端结构的事实判断。它不是迁移计划，也不是新设计稿，而是对你手动调整后的代码进行一次结构审查。

## 1. 总体判断

当前后端结构已经明显接近 `sample` 的思路：顶层收敛成 `route / services / system`，原来的 `slaydemo.backend.http4s` 目录已经被迁出，业务域也基本放进了 `services/<domain>`。

目前能编译、contract runner 能跑通，说明这次移动不是纯目录摆放，主入口和依赖也基本连上了。

但是它还没有完全达到 sample 的架构状态。最主要的差异是：sample 的 `system.api.APIMessageRouter` 是全量 HTTP API 入口，而当前项目只是把 `HealthAPIMessage` 和 `ReplayCatalogAPIMessage` 作为 vertical slice 接入了 `system/api`。大多数业务 API 仍然由各自的 domain route module 直接处理。

## 2. 当前真实运行入口

当前真正运行的后端入口是：

```text
npm run backend:dev
  -> cd backend && sbt "runMain route.BackendHttp4sApp"
  -> backend/src/main/scala/route/BackendHttp4sApp.scala
```

`backend/build.sbt` 也指向同一个入口：

```scala
Compile / mainClass := Some("route.BackendHttp4sApp")
```

实际启动链路是：

```text
route.BackendHttp4sApp
  -> services.BackendEnvironment.load()
  -> services.BackendRuntime.fromEnvironment(env)
  -> route.HttpApiModules.routes(httpApiServices(runtime))
  -> EmberServerBuilder.withHttpApp(...)
```

结论：当前后端主链路来自 `backend/src/main/scala` 下的 http4s 后端，不是 `backend-legacy`，也不是 `src/test`。

## 3. 当前源码层级

当前 `backend/src/main/scala` 顶层实际分成三块：

```text
route/
  HTTP adapter 层。负责 http4s 入口、CORS、request decode、response render、error mapping 和 domain HTTP module。

services/
  业务域层。包含 battle、identity、mail、social、forum、governance、replay、bots 等 domain 的 objects、database、services。

system/
  跨业务基础设施。包含 APIMessage 实验层、shared objects、health/error response、storage config、Postgres support 和基础 policies。
```

当前 package 语义基本对齐：

```text
route/**     -> package route.*
services/**  -> package services.*
system/**    -> package system.*
```

仍然需要注意：`BackendConfig.scala`、`BackendEnvironment.scala`、`BackendLiveRepositoryFactories.scala`、`BackendRepositories.scala`、`BackendRuntime.scala` 物理上在 `backend/src/main/scala` 根目录，但声明的是 `package services`。这能编译，但路径和 package 不一致，长期维护时容易误判归属。更标准的摆放应该是 `backend/src/main/scala/services/BackendRuntime.scala` 这一类路径。

## 4. 和 sample 的相似点

当前已经接近 sample 的地方：

| 维度 | 当前项目状态 |
| --- | --- |
| 顶层结构 | 已经从旧 `slaydemo.backend.*` 收敛为 `route / services / system` |
| HTTP 入口 | 使用 http4s + Ember server，入口集中在 `route.BackendHttp4sApp` |
| 业务域 | 大部分业务都进入 `services/<domain>` |
| 共享基础设施 | storage、Postgres、shared id、health/error response 已进入 `system` |
| API 类型 | request/response 类型基本靠各 domain 的 `objects/apiTypes` 承载 |
| 构建入口 | `backend:dev`、`backend:compile`、`backend:test-contracts` 已指向新 package |

这说明你的手动调整方向是对的，不是简单把目录名字改掉，而是已经把主入口、构建脚本和多数 import 同步过来了。

## 5. 和 sample 的关键差异

sample 的核心模式是：

```text
routes.ApiRouter
  -> HealthRouter.routes
  -> APIMessageRouter.routes(UserRoutes.apiMessages ++ BooksRoutes.apiMessages, ...)
```

也就是说，sample 的每个业务 API 都被注册成 `RegisteredAPIMessage`，由 `APIMessageRouter` 统一处理 `/api/<xxxapi>`。

当前项目的真实模式是：

```text
route.BackendHttp4sApp
  -> route.HttpApiModules
  -> HealthHttpModule
  -> IdentityHttpModule
  -> MailHttpModule
  -> SocialHttpModule
  -> ForumHttpModule
  -> GovernanceHttpModule
  -> ReplayHttpModule
  -> BotProfileHttpModule
  -> BattleHttpModule
```

当前 `system/api/APIMessageRouter.scala` 已经接入两条 APIMessage 路径：`HealthAPIMessage` 根据类名自动推导出 `/api/health`，`ReplayCatalogAPIMessage` 根据类名自动推导出 `/api/replaycatalog`。这两条路径不再依赖普通 route alias 或 path rewrite；dev proxy 也不再把 `/api/...` 改写成 `/...`，后端直接接收真实 API path。

结论：当前结构已经开始接入 sample 的 APIMessageRouter 模式，但仍然是渐进状态。除 health 和 replay catalog APIMessage 外，主要 HTTP API 运行机制仍然是 domain route module 模式。

## 6. 当前真正起作用的代码

这些代码在启动和 contract runner 中属于主链路：

| 区域 | 当前作用 |
| --- | --- |
| `route.BackendHttp4sApp` | 进程入口，加载环境，创建 runtime，启动 Ember HTTP server |
| `route.HttpApiModules` | HTTP API 总组装入口，把各 domain module 合并成一个 http4s routes |
| `route.HttpApiServices` | 将 runtime 中的 service 打包给 HTTP module 使用 |
| `route.<domain>.*HttpModule` | 每个业务域对外暴露的 HTTP module |
| `route.<domain>.*Http4sRoutes` | 每个业务域的具体 path/method/body/error/response 处理 |
| `services.BackendRuntime` | 组装当前运行所需的 service 和 repository |
| `services.<domain>.objects` | 业务对象、值对象、枚举、DTO、API request/response 类型 |
| `services.<domain>.services` | use-case service、业务规则和状态推进 |
| `services.<domain>.database` | repository、file/in-memory/postgres 实现 |
| `system.database` | Postgres/Hikari 和原子文件写入基础设施 |
| `system.storage` | storage mode 和 Postgres 连接配置 |
| `system.objects` | shared id、service port/name、health/error response |

## 7. 当前已接入和仍未接入的 system/api 代码

`system/api` 现在不是完全悬空，但也不是全量入口。

| 文件或区域 | 当前判断 |
| --- | --- |
| `system/api/APIMessage.scala` | 已用于 `HealthAPIMessage`、`ReplayCatalogAPIMessage` 的自动 apiName 推导和 `RegisteredAPIMessage` 定义 |
| `system/api/APIMessageRouter.scala` | 已经通过 `HealthHttpModule` 接入 `/api/health`，通过 `ReplayHttpModule` 接入 `/api/replaycatalog` |
| 其他 domain API | 仍未迁移到 APIMessageRouter，继续由各自的 `*Http4sRoutes` 处理 |
| `scripts/replace-backend-services-prefix.ps1` | 迁移辅助脚本，不属于运行时。保留可以，但不要让它继续代表当前架构 |
| `backend-legacy/` | 不属于当前 `backend:dev` 启动链路 |

这里最需要决策的是后续迁移节奏：如果未来要继续对齐 sample，就应该逐个 domain 把 API 注册进 APIMessageRouter；如果不打算全量采用 message router，就应该只保留少量明确有价值的 APIMessage vertical slice，避免双入口长期并存。

## 8. 当前 route 层的问题

`route` 层比之前干净，但仍然偏厚。

典型 route 文件仍然同时处理：

```text
path 判断
method 分支
body decode
query/path 参数解析
业务错误到 HTTP 错误映射
IO.blocking 包装 service 调用
response DTO 渲染
CORS/OPTIONS/HEAD
```

这不是立刻错误，因为它能跑，也比旧结构清楚。但如果继续发展，route 文件仍然会变成“手写 HTTP 状态机”。sample 用 APIMessageRouter 把很多重复 HTTP 处理收束到了系统层，这是它更简洁的主要原因。

当前建议不是马上删除 route，而是先决定方向：

| 方向 | 影响 |
| --- | --- |
| 继续 domain route module | 改动小，当前代码稳定，但 route 层样板代码会继续存在 |
| 渐进接入 APIMessageRouter | 更接近 sample，route 会变短，但需要逐个 domain 迁移并补 contract |

## 9. 当前 services 层的问题

`services` 层现在按 domain 拆开了，这是正确方向。

但是 `battle` 仍然体量很大，内部虽然拆成了 `queue / session / runtime / world / combat / actors / abilities / results`，但很多文件仍然偏规则碎片化，阅读时需要频繁跨文件跳转。

更重要的是，当前 `services` 不是微服务。它们是同一个 JVM 进程内的领域模块，彼此通过 `BackendRuntime` 和 repository/service 注入组合。不要把它们理解成独立部署、互不调包的微服务。

后续优化重点应该是：

```text
先保证 contract 覆盖
再压缩重复 route/service 样板
最后再考虑 battle 内部规则聚合或进一步拆分
```

如果先继续大规模压缩 service，很容易遗漏 API 行为或 battle runtime 行为。

## 10. 当前测试覆盖状态

当前测试入口：

```text
npm run backend:test-contracts
  -> cd backend && sbt "Test / runMain route.contract.BackendContractTestRunner"
```

当前 runner 已覆盖：

```text
storage config
repository wiring
password hasher
Postgres boundary
health REST `/health` and `/api/health`
health APIMessage `/api/health`
identity register/accounts/current
battle queue join/status
battle room snapshot/heartbeat
battle state read
battle command submit
battle state runtime core lazy bootstrap/ownership/sequence/movement/finish projection
battle state runtime detail projectile delayed hit/pickup contention/medkit respawn/skill fire suppression
battle state runtime bot control/replay frame capture/replay frame retention
battle state runtime obstacle movement/projectile obstacle first-intersection terminal
battle state runtime projectile large-read-gap/held-primary fixed-step catch-up/old short TTL
battle state runtime projectile terminal retention/event retention
battle state runtime sprint stamina/medkit heal
battle state runtime player elimination/no-respawn/dead-command cleanup
battle state runtime pistol cooldown/manual reload/empty-magazine auto reload
battle state runtime non-pistol weapon authority/gatling hit/rocket splash
battle finish projection
battle result list/record
mail list/read
social friend request list/create/respond
forum topic list/load/create/reply/topic vote/reply vote
governance contribution adjustment list/create
governance admin notification list/create
replay catalog/detail/record/comments/comment create
bots profile list
```

仍未恢复同等级覆盖：

```text
detailed battle runtime parity contract: dash/blink/freeze/slow-field
```

结论：当前 contract runner 已覆盖主要 HTTP 主链路、battle runtime 主链路、关键 projectile/pickup/skill 切片、bot runtime、replay frame 捕捉/保留边界、基础 obstacle/projectile 碰撞、projectile 大 read gap、held fire fixed-step catch-up、旧短 TTL 回归、projectile terminal/event retention、sprint stamina/medkit heal、player elimination/no-respawn/dead-command cleanup，以及 pistol cooldown/manual reload/empty-magazine auto reload。但还不能证明所有细粒度战斗 runtime 行为都恢复到了迁移前的覆盖水平。

## 11. Git 分支和 staging 风险

当前 worktree 是一次大规模后端结构迁移，`git status` 显示大量 staged rename、delete、add。

当前还存在一个未跟踪文件：

```text
mapplanning.md
```

它不属于本次后端结构审查，不应该被误加入提交。

提交前要特别注意：这次 diff 很大，包含旧 `slaydemo/backend/...` 到新 `route/ services/ system/` 的路径迁移，以及大量旧测试删除和单一 contract runner 新增。review 时应优先看 package/import 是否真实对齐，而不是只看 rename 统计。

## 12. 已验证事实

本轮审查中已经验证：

```text
npm run backend:test-contracts
  通过

npm run backend:compile
  通过

git diff --check
  通过

git diff --cached --check
  通过
```

第一次并行运行 `backend:compile` 和 `backend:test-contracts` 时触发 Windows sbt named-pipe lock。串行重跑后 `backend:compile` 通过，所以那不是 Scala 代码编译失败。

sbt 输出的 `sun.misc.Unsafe` 警告来自 sbt/Scala launcher，不是当前项目代码里的 unsafe cast 或 unchecked cast。

## 13. 结论

当前结构已经比之前健康很多，也确实更接近 sample：

```text
route 负责 HTTP 边界
services 负责业务域
system 负责基础设施和共享对象
```

但是目前还有三个核心问题：

```text
1. system/api/APIMessage* 目前只接入了 HealthAPIMessage 和 ReplayCatalogAPIMessage，还不是全量 HTTP API 入口。
2. 顶层 Backend*.scala 的物理路径和 package services 不一致。
3. 测试覆盖从迁移前的大量细粒度 contract 收缩成单一 runner；battle state runtime 主链路、projectile/pickup/skill 关键切片、bot runtime、replay frame history、基础 obstacle/projectile 碰撞、projectile fixed-step 长尾、projectile/event retention、sprint/medkit heal、player elimination、weapon reload 和 non-pistol/splash 已恢复，但 dash/blink/freeze/slow-field 还没恢复到迁移前水平。
```

我的建议是：先不要继续大规模删 route 或重写 service。下一步应该继续恢复 battle runtime 的细节契约覆盖，优先顺序是：

```text
detailed battle runtime parity contract
```

等这些 API 契约重新被测试保护后，再决定是否继续把更多 domain API 接入 `APIMessageRouter`。这样可以避免“结构像 sample 了，但旧 API 行为被迁移过程漏掉”的问题。
