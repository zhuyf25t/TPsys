# Demo 后端 / PostgreSQL / 跨电脑联机 Runbook

这份文档只解决一件事：让所有电脑访问同一套后端和同一个 PostgreSQL 数据库。

## 1. psql 打开时怎么填

打开 Windows 的 `SQL Shell (psql)` 后按下面填写：

```text
Server [localhost]: 直接回车
Database [postgres]: 直接回车
Port [5432]: 直接回车
Username [postgres]: 直接回车
Password: 输入你安装 PostgreSQL 时给 postgres 设置的密码
```

这里的密码不是 `secret`。`secret` 是本项目演示用户 `slay_user` 的数据库密码。

如果看到：

```text
postgres=#
```

说明已经进入 PostgreSQL 管理控制台。

## 2. 创建数据库和项目用户

在 `postgres=#` 后执行：

```sql
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'slay_user') THEN
    CREATE ROLE slay_user LOGIN PASSWORD 'secret';
  ELSE
    ALTER ROLE slay_user WITH LOGIN PASSWORD 'secret';
  END IF;
END
$$;

SELECT 'CREATE DATABASE slay_demo OWNER slay_user'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'slay_demo')\gexec

ALTER DATABASE slay_demo OWNER TO slay_user;
\c slay_demo
GRANT USAGE, CREATE ON SCHEMA public TO slay_user;
```

如果提示用户或数据库已经存在，不是问题。确认最后能切到 `slay_demo` 并执行 `GRANT` 即可。

## 3. 启动后端

打开第一个 PowerShell：

```powershell
cd F:\slay-demo
$env:SLAY_DEMO_DATABASE_URL="jdbc:postgresql://localhost:5432/slay_demo"
$env:SLAY_DEMO_DATABASE_USER="slay_user"
$env:SLAY_DEMO_DATABASE_PASSWORD="secret"
$env:SLAY_DEMO_BACKEND_PORT="8080"
$env:SBT_OPTS="-Dsbt.server.forcestart=true"
npm run backend:dev
```

看到类似下面输出，说明后端启动成功：

```text
Slay demo backend listening on http://127.0.0.1:8080
```

本机检查：

```text
http://127.0.0.1:8080/health
```

预期返回：

```json
{"status":"ok","service":"slay-demo-backend","port":8080}
```

## 4. 如果 8080 被占用

如果看到：

```text
Address already in use: bind
```

说明 8080 已经有旧后端进程。

查进程：

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object LocalAddress,LocalPort,OwningProcess
Get-Process -Id <OwningProcess>
```

确认是旧 Java / sbt 后端后停止：

```powershell
Stop-Process -Id <OwningProcess>
```

然后重新启动后端。不要同时启动两个 8080 后端。

## 5. 启动前端

打开第二个 PowerShell：

```powershell
cd F:\slay-demo
npm run dev
```

你当前 Vite 输出里出现过：

```text
Local:   http://localhost:5173/
Network: http://183.172.160.197:5173/
```

你自己可以打开：

```text
http://localhost:5173/
```

朋友必须打开你的 Network 地址：

```text
http://183.172.160.197:5173/
```

朋友不要打开他自己电脑的 `localhost:5173`。朋友电脑上的 `localhost` 指的是朋友自己的电脑，不是你的电脑。

## 6. 为什么朋友要访问你的 5173

当前前端 API 默认走：

```text
/api
```

Vite 会把 `/api` 代理到你的本机后端：

```text
http://127.0.0.1:8080
```

所以朋友访问：

```text
http://183.172.160.197:5173/
```

时，朋友浏览器请求：

```text
http://183.172.160.197:5173/api/identity/accounts
```

会先到你的 Vite，再由你的 Vite 转发到你的 8080 后端。这样两台电脑看到的就是同一个 PostgreSQL。

## 7. 手动检查是不是同一套后端

你自己打开：

```text
http://localhost:5173/api/identity/accounts
```

朋友打开：

```text
http://183.172.160.197:5173/api/identity/accounts
```

如果两边看到同一批账号，说明 API 已经走到同一套后端。

也可以检查：

```text
http://localhost:5173/api/health
http://localhost:5173/api/battle/results?limit=10
http://localhost:5173/api/replay/catalog
http://localhost:5173/api/forum/topics
```

朋友对应替换成：

```text
http://183.172.160.197:5173/api/health
http://183.172.160.197:5173/api/battle/results?limit=10
http://183.172.160.197:5173/api/replay/catalog
http://183.172.160.197:5173/api/forum/topics
```

## 8. 自动 smoke

本机执行：

```powershell
cd F:\slay-demo
npm run demo:smoke
```

它会检查：

- `/health`
- `/identity/accounts`
- 注册临时账号
- `/battle/results`
- `/replay/catalog`
- `/forum/topics`
- 好友申请发送、邮箱可见、接受/拒绝同步

如果 smoke 过了，本机 API 链路基本可用。

## 9. 演示前最小检查清单

1. PostgreSQL 已启动。
2. `slay_demo` 数据库存在。
3. `slay_user / secret` 可用。
4. 后端 8080 是当前代码，不是旧进程。
5. `http://127.0.0.1:8080/health` 返回 `ok`。
6. 前端 Vite 已启动，并输出 Network 地址。
7. 朋友打开 `http://183.172.160.197:5173/`，不是他自己的 localhost。
8. 你和朋友打开 `/api/identity/accounts` 能看到同一批账号。

## 10. 常见错误

- `fe_sendauth: no password supplied`：psql 登录时没有输入 `postgres` 用户密码，或直接回车跳过了密码。
- `Address already in use: bind`：8080 已经被旧后端占用。
- 朋友榜单和你不同：朋友打开了自己的 `localhost`，或某个页面仍在读 localStorage fallback。
- 注册后榜单没有账号：后端没启动、API 没走 `/api`、或前端还在看旧缓存。先检查 `/api/identity/accounts`。
