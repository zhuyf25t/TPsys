# Data Closure Audit

`npm run audit:data-closure` is a read-only local data audit. It reads `backend/data/*.json`, prints aggregate counts and capped samples, and does not write or repair data.

The report currently covers:

- Visitor-like/non-playable rows in battle results, mails, replay records, and identity accounts. Reserved aliases are kept in source and output as Unicode escapes for `\u8bbf\u5ba2`, `\u6e38\u5ba2`, and `\u672a\u767b\u5f55`.
- Duplicate battle result projections keyed by normalized `battleId + handle`.
- Rating arithmetic per result: `ratingBefore + ratingDelta === ratingAfter`, with finite-number validation.
- Per-handle rating continuity ordered by `finishedAt` ascending. Breaks are historical consistency findings only.
- Replay/result association by `battleId + handle`, including rating, score, and placement projection mismatches. Replays without matching results are reported for old-data visibility and are not auto-fixed.
- Battle mail coverage for each result owner. The current `mail-battle-${resultId}` ID and legacy `mail-battle-${battleId}` ID both count as coverage. Separate old rating-system mails are not required.

Each section limits samples to 20 rows to avoid dumping large JSON blobs. The audit intentionally exposes data closure risks without becoming a backend writer or migration tool.
