# Slay Demo Backend

This is the new backend rewrite root.

- Scala 3 / SBT project wiring
- explicit application startup boundary
- typed shared health contract
- in-memory frontend-facing API contracts
- explicit storage configuration
- explicit Postgres repository wiring for persistent read/write modules

Default startup is memory-only. Database-backed persistence is selected only with `SLAY_DEMO_STORAGE_MODE=postgres`.

For local development, `BackendHttp4sApp` is the default backend entrypoint and loads ignored `.env` / `.env.local` files through the shared backend environment loader. This lets both `cd backend; sbt run` and the root npm scripts share the repository-root `.env.local`. Process environment values still take precedence over local files. The legacy JDK `HttpServer` entrypoint remains available through `npm run backend:dev:legacy` for rollback and contract comparison.

## Storage Configuration

Storage mode is explicit and safe by default:

- Default: `SLAY_DEMO_STORAGE_MODE=memory`
- File mode: `SLAY_DEMO_STORAGE_MODE=file` plus `SLAY_DEMO_DATA_DIR`.
- Postgres mode: `SLAY_DEMO_STORAGE_MODE=postgres` plus `SLAY_DEMO_DATABASE_URL`.

The new backend does not honor generic `DATABASE_URL` by itself. This prevents inherited shell or CI environment variables from silently switching the application into Postgres mode.

The health response includes the selected storage mode:

```json
{"status":"ok","service":"slay-demo-backend","port":8080,"storageMode":"memory"}
```

Parsing Postgres settings does not open a database connection. In explicit Postgres mode, the wired repositories open connections and initialize their own tables during backend startup.

File-backed and Postgres-backed repositories are currently wired for identity accounts, battle results, mail, bot profiles, replay records/comments, social friend requests, forum topics/replies/votes, and governance adjustments/notifications.

File mode stores data under `SLAY_DEMO_DATA_DIR` using the legacy-compatible filenames:

- `identity-accounts.json`
- `battle-results.json`
- `mails.json`
- `bot-profiles.json`
- `replay-records.json`
- `friend-requests.json`
- `forum.json`
- `governance-contribution-adjustments.json`
- `governance-review-notifications.json`

Live battle queue, room, and authoritative battle state remain process-memory runtime state. That is an explicit realtime boundary: restarting the backend loses active tickets, rooms, and in-progress battles.

Real Postgres smoke testing requires a scoped temporary database or explicit credentials. Do not point the rebuilt backend at an existing database unless the database is intended for this rewrite.
