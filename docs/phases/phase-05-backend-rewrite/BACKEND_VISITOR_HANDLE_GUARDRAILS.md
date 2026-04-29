# Backend Visitor-like Handle Guardrails

Backend formal identity and battle/replay data paths share `HandleRules` for visitor-like handle checks.

Reserved visitor-like handles are rejected case-insensitively after trimming:

- `Visitor`
- `guest`
- `anonymous`
- `anon`
- `访客`
- `游客`
- `未登录`

Empty handles are not playable identity handles. Identity registration returns the existing `invalid_handle` error for these values. Login/session/account listing paths filter visitor-like stored accounts without migrating `backend/data/*.json`; builtin `admin` remains available as the explicit service account.

Mail service owner handles also use `HandleRules.isPlayableIdentityHandle` at runtime. `DefaultMailService.list` returns no rows for empty or visitor-like owners, `markRead` returns `false` without touching storage, and `create` returns the original record without saving it when the owner is not playable. This keeps historical local `backend/data/mails.json` visitor-like records hidden without editing data files.

Authoritative battle finish projection skips visitor-like non-bot players when choosing formal result owners. Replay ownership also prefers a playable human winner, then the best ranked playable human, then the server summary. `playersLine` still preserves the original participant text so historical battle summaries remain readable, but visitor-like handles no longer block real accounts from receiving result/replay/rating projection.

Result and replay read paths also apply service-layer guardrails before returning stored data. `DefaultBattleResultService.list` returns no rows for empty or visitor-like requested handles, filters historical visitor-like result owners after repository reads, and over-fetches before filtering so dirty local records do not occupy the requested result window. `DefaultReplayService.list` similarly over-fetches and hides visitor-like replay owners; `load` returns `None` for visitor-like replay owners; comment reads require the replay to remain visible and hide visitor-like comment authors.

There is currently no separate backend user rating/profile read service. Backend rating/profile views are derived from battle result reads and replay rating hydration, so their backend visitor-like boundary is the battle result service filter plus the replay detail/catalog hydration filter.
