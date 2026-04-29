# Bot Registry Foundation

## Current Boundary

`src/features/battle/runtime-local/bots/botRegistry.ts` is a local catalog for stable CPU bot identity metadata. It owns static bot profile records only:

- `botId`: runtime hero slot id, for example `bot-1`.
- `handle`: stable future account key, for example `cpu-sable`.
- `displayName`: name shown in battle and matching surfaces.
- `initialRating`: seed value for future balancing, not a leaderboard write.
- `profileTone` and `strategyLabel`: presentation and future strategy metadata.
- `skin`: avatar/texture metadata for later renderer/profile integration.

The registry does not own movement, firing, targeting, damage, pickups, weapons, rating writes, replay truth, or matchmaking truth. Current battle seeding reads registry names for CPU slots while preserving existing bot behavior.

## Runtime Use

Battle initialization still creates the same hero slots and combat state. After the initial heroes are created, remaining `bot-*` slots receive registry `displayName` values. Queued human handles still override front bot slots exactly as before.

The matching overlay reads the same catalog for CPU slot labels. This keeps temporary strings such as numbered robot labels out of the page while avoiding any claim that CPU opponents are online players.

## Future DB Integration

The registry can become a read-through catalog backed by a `bot_profiles` table keyed by `botId` and `handle`. The static records can remain as defaults for offline/local play, with DB data overriding display/profile metadata when available.

## Bot Suggestions

Recommendation systems can consume `profileTone`, `strategyLabel`, `initialRating`, and later performance tags to suggest training opponents without changing combat AI. Suggestions should select registered bot handles, not fabricate participants.

## Lineage And DAG

Future bot evolution can add lineage fields such as `parentBotIds`, `strategyVersion`, and `derivedFromMatchIds`. Those fields should describe how a strategy module evolved while keeping identity stable through `handle`.

## Rating Balancing

`initialRating` is only a seed for future placement and balance experiments. Real rating/contribution/profile/replay data must continue to come from existing result and rating sources until a dedicated bot rating pipeline exists.
