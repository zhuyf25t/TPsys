# Data Closure Repair Plan

`npm run data:closure-repair-plan` is a read-only dry-run planner for historical data closure findings. It turns the current `npm run audit:data-closure` findings into aggregate repair categories and capped review samples, but it is not a migration and it does not write `backend/data/*.json`.

The planner reads:

- `backend/data/battle-results.json`
- `backend/data/replay-records.json`
- `backend/data/mails.json`
- `backend/data/identity-accounts.json`

The script prints ASCII-safe JSON samples with non-ASCII text escaped, so PowerShell output remains reviewable.

## Critical Preflight

Before emitting repair suggestions, the planner checks the existing critical audit cleanup class:

- Visitor-like or non-playable handles in battle results, mails, replay records, or identity accounts.
- Duplicate battle result logical rows keyed by normalized `battleId + handle`.

If either class is present, the output sets `blocked_by_critical_audit_findings: true`, skips all downstream repair suggestions, and points reviewers to the existing cleanup flow. Those rows must be cleaned first because later continuity, replay, and mail suggestions depend on stable playable identities and unique battle result projections.

## Planned Categories

- `rating_continuity_break` findings are marked `unsafe_auto_repair`. The plan prints the handle, previous result, next result, and expected next `ratingBefore`, but it never generates an automatic patch because changing historical ratings can cascade through later rows.
- `replay_without_result` findings are classified as `likely_system_or_bot` when the handle starts with `cpu-` or `battleId`/`replayId` starts with `contract-smoke-`; the rest are `needs_result_decision`. The planner never synthesizes missing battle results.
- `battle_result_without_battle_mail` findings emit `suggested_mail` samples only. The suggested id is `mail-battle-${resultId}`, `ownerHandle` comes from the result handle, `kind` is `battle`, and review fields include Chinese `title`/`body`, `createdAt`, `read`, `important`, `sourceBattleId`, and `sourceResultId`. Samples also include the current stored mail schema fields `subject`, `excerpt`, `senderLabel`, and `unread`.

## When Apply Is Allowed

An apply version should only be introduced after all of these are true:

- `npm run audit:data-closure` has no critical visitor-like/non-playable or duplicate battle result findings.
- The dry-run plan has been reviewed and the owner accepts which `suggested_mail` rows should be written.
- Any `needs_result_decision` replay rows have an explicit product/data decision.
- Rating continuity fixes have a documented rating policy and are not inferred from the planner alone.
- The target mail schema is confirmed, especially whether future writes should persist only `subject`/`excerpt`/`unread` or also add `title`/`body`/`read`/`source*` fields.

The apply version should be a separate ticket with backups, validation, and a small bounded write surface.

## Not Auto-Fixable

The planner intentionally does not auto-fix:

- Rating continuity breaks.
- Missing replay results.
- Bot/system or contract-smoke replay artifacts.
- Critical visitor-like/non-playable cleanup or duplicate battle result cleanup.

Those require either the existing cleanup tool or an explicit data-owner decision before any write path is safe.
