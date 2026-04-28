# Visitor Data Guardrails

Frontend rating, profile, and replay rating hydration use `frontend/src/features/identity/identityHandlePolicy.ts` as the shared handle boundary.

- `normalizePlayerHandleKey` trims handles and compares them case-insensitively.
- `isVisitorLikeHandle` treats `Visitor`, guest/anonymous aliases, and Chinese visitor labels as visitor-like.
- `isPlayableIdentityHandle` rejects empty and visitor-like handles before building official rating/profile data.

Current boundaries:

- Rating entries skip visitor-like or empty handles from battle records and identity accounts, so default 1200 visitor rows are not created.
- Profile loading returns `undefined` for empty or visitor-like handles and does not normalize empty handles into a visitor profile.
- Replay playback is still available, but rating hydration does not use empty or visitor-like requested/auth/remote/display handles as rating lookup handles.
- Local battle truth fallback now uses the same playable-handle boundary before writing official local records, battle mails, rating/profile views, and backend backfill/sync. Visitor-like settlements can return a transient disabled replay/summary, but they are not persisted as official local battle truth.
- Local auth fallback now rejects visitor-like handles during register/login, filters old visitor-like users from local account storage, and refuses to restore visitor-like sessions as playable users.
