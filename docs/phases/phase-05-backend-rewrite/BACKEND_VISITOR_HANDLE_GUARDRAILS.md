# Backend Visitor-like Handle Guardrails

The rewritten backend uses `HandlePolicy` for visitor-like handle checks at route and service boundaries.

Reserved visitor-like handles are rejected case-insensitively after trimming:

- `Visitor`
- `guest`
- `anonymous`
- `anon`
- `访客`
- `游客`
- `未登录`

Empty handles are not playable identity handles. Identity registration returns `invalid_handle` for these values. Login, session, and account listing paths filter visitor-like stored accounts; builtin `admin` remains available as the explicit service account.

Mail service owner handles use `HandlePolicy.isPlayableIdentityHandle` at runtime. `DefaultMailService.list` returns no rows for empty or visitor-like owners and does not create welcome mail for them. `markRead` returns `MailReadError.MailNotFound` without touching storage when the owner is not playable.

Authoritative battle finish projection records only non-bot playable human settlements. `playersLine` still preserves participant display text so battle summaries remain readable, but visitor-like handles do not receive formal result, replay, rating, or mail projection.

Result and replay services also apply service-layer guardrails before returning stored data. `DefaultBattleResultService.record` skips persistence for visitor-like owners, `list` returns no rows for visitor-like requested handles, and list output filters historical visitor-like result owners after repository reads.

`DefaultReplayService.record` skips persistence for visitor-like replay owners. `list` and `load` hide visitor-like replay owners. Comment writes reject visitor-like authors with `ReplayCommentError.InvalidAuthor`; comment reads require the replay to remain visible and hide visitor-like comment authors.

Executable coverage lives in `VisitorHandleGuardrailContractTest`, included by `npm run backend:test-contracts`.

There is currently no separate backend user rating/profile read service. Backend rating/profile views are derived from battle result reads and replay rating hydration, so their visitor-like boundary is the battle result service filter plus the replay detail/catalog hydration filter.
