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
