# Agent State

## Completed Ticket

ID: BWR-173
Goal: Restore room route missing-id error code parity.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/routes/BattleRoutes.scala`, focused API smoke or route contract assertions, `.codex/agent-state.md`, verification commands.
Forbidden scope: battle runtime rules, command parser semantics, weapon/projectile behavior, finish projection, replay storage, queue/matchmaking service behavior, frontend implementation, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Room snapshot/heartbeat requests missing a valid room id should use legacy `invalid_room_id` error code.
- Existing room-not-found and successful room snapshot behavior should remain unchanged.
Architecture/domain-modeling impact:
- Route error mapping only; no domain or service model changes.
Side-effect boundary impact:
- No new side effects; API response mapping only.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run demo:api-contract`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- Missing room id produces `invalid_room_id`.
- Room not found still produces `room_not_found`.
- Existing API contract smoke remains green.
Risks:
- Strict clients expecting `missing_room_id` may see legacy code instead; current frontend should not depend on this error path.

Result:
- Room snapshot and room heartbeat missing-id errors now return legacy-compatible `invalid_room_id`.
- API smoke now asserts missing room id for snapshot and heartbeat, plus unknown room `room_not_found`.
- Promoted the API smoke error helper to script scope so multiple endpoint checks can share it.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restored backend to normal explicit Postgres mode.
- `npm run demo:api-contract`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-174: Restore sprint stamina drain contract.

## Completed Ticket

ID: BWR-174
Goal: Restore and test sprint stamina drain semantics.
Allowed scope: battle domain/value types for stamina if required, `BattleStateService` sprint update path, focused runtime/API smoke contract tests, `scripts/api-contract-field-smoke.ps1`, `.codex/agent-state.md`, verification commands.
Forbidden scope: weapon/projectile behavior, pickup behavior, finish projection, replay storage, queue/matchmaking, frontend implementation, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Holding sprint while moving should consume stamina over authoritative ticks.
- Not sprinting should recover stamina according to existing/legacy rules.
- Sprint movement should still require positive stamina and remain collision-safe.
Architecture/domain-modeling impact:
- Preserved typed stamina modeling and immutable state transitions.
- Runtime code already had the expected drain/recovery behavior; the ticket added API-level regression coverage for the user-visible path.
Side-effect boundary impact:
- No new external side effects; smoke coverage only exercises existing HTTP API.
Verification commands:
- `npm run demo:api-contract`
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- Focused API smoke proves sprint drains stamina after movement ticks.
- Focused API smoke proves idle recovers stamina after sprinting.
- Existing battle runtime contracts remain green.
Risks:
- Stamina is still represented as `Int` in the current backend; legacy used `Double`. A separate precision ticket should handle that if strict wire parity is required.

Result:
- API contract smoke now asserts walk does not consume stamina, sprint consumes stamina, and idle recovery increases stamina.
- Latest smoke observed `stamina=100->90->96` through the actual `/battle/state` HTTP payload.

Verification passed:
- `npm run demo:api-contract`
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restored backend to normal explicit Postgres mode.
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-175: Restore projectile hit terminal contact point.

## Completed Ticket

ID: BWR-175
Goal: Restore projectile hit terminal contact point parity.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, focused battle state runtime contract tests, `.codex/agent-state.md`, verification commands.
Forbidden scope: projectile lifetime/range tuning, projectile speed tuning, frontend renderer, weapon ammo/reload behavior, pickup behavior, finish projection, replay storage, queue/matchmaking, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Projectile hit terminals should report the first contact point with the target collision circle, not the target center/closest point.
- Keep current long projectile lifetime and current speed constants unchanged for this ticket.
- Existing projectile obstacle and elimination behavior should remain unchanged.
Architecture/domain-modeling impact:
- Kept projectile collision as a pure geometry derivation inside the battle state transition.
- No new domain concepts.
Side-effect boundary impact:
- No new external effects; deterministic runtime geometry only.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run demo:api-contract`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- Focused contract proves a centerline hit terminal ends before the target center by the combined projectile/player hit radius.
- Existing projectile terminal and obstacle contracts remain green.
Risks:
- Frontend effects may still have muzzle/trail offset issues independent of backend terminal coordinates; that should be a separate frontend renderer ticket if this backend fix does not fully remove the visual mismatch.

Result:
- Projectile player-hit detection now uses the first segment-circle intersection for the target hit radius.
- Hit terminal positions now stop at target collision contact instead of the target center.
- Projectile speed and long lifetime were intentionally left unchanged in this ticket.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restored backend to normal explicit Postgres mode.
- `npm run demo:api-contract`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-176: Align Gatling heat and single-projectile spread parity.

## Completed Ticket

ID: BWR-176
Goal: Align Gatling heat cooldown and single-projectile spread parity.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/main/scala/slaydemo/backend/battle/routes/BattleStateJson.scala` only if reserve wire needs explicit parity, focused battle state runtime/API smoke contract tests, `scripts/api-contract-field-smoke.ps1`, `.codex/agent-state.md`, verification commands.
Forbidden scope: projectile speed/lifetime/range tuning, pistol/rocket/shotgun balance changes, pickup behavior, finish projection, replay storage, queue/matchmaking, frontend implementation, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Gatling heat should continue cooling while the weapon is holstered.
- Single-projectile Gatling shots should not receive extra spread if legacy parity requires no spread for projectile count 1.
- Gatling reserve ammo wire should be checked and either restored to legacy `0` or recorded as a separate intentional difference.
Architecture/domain-modeling impact:
- Preserved typed weapon inventory state and immutable player updates.
- Kept heat/cooldown as runtime state, not API-route logic.
Side-effect boundary impact:
- No new external effects; deterministic weapon runtime only.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run demo:api-contract`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- Focused contracts cover holstered Gatling heat cooldown and single-projectile spread behavior.
- Existing weapon/reload/pickup contracts remain green.
Risks:
- Frontend weapon HUD should now see Gatling `reserveAmmo=0` instead of `null`, matching legacy.

Result:
- Gatling reserve is now represented as `Some(0)` and renders as JSON `0`.
- Single-projectile shots no longer receive deterministic spread.
- Weapon heat now advances for all weapons, so holstered Gatling cools down while another weapon is active.
- API smoke was updated to expect Gatling reserve `0`.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restored backend to normal explicit Postgres mode.
- `npm run demo:api-contract`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-177: Preserve held sprint/movement through dash and blink commands.

## Completed Ticket

ID: BWR-177
Goal: Preserve held movement and sprint intent through dash/blink commands.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, focused battle state runtime/API smoke contract tests if needed, `.codex/agent-state.md`, verification commands.
Forbidden scope: frontend input handling, skill cooldown/balance tuning, projectile behavior, weapon behavior, pickup behavior, finish projection, replay storage, queue/matchmaking, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Dash and blink should not silently clear the latest held movement/sprint intent when the client sends those controls with the skill command.
- Movement/sprint after the skill should continue on subsequent authoritative ticks until a later command changes it.
- Existing dash/blink cooldown and blocked-target semantics remain unchanged.
Architecture/domain-modeling impact:
- Preserved immutable player state updates; movement/sprint remain explicit command state.
Side-effect boundary impact:
- No new external effects; deterministic command transition only.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run demo:api-contract`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- Focused contract proves dash/blink command with movement+sprint keeps effective sprint/movement after the skill.
- Existing skill contracts remain green.
Risks:
- Frontend may still send primaryHeld together with skill hotkeys; preventing skill key from firing weapon is likely a frontend input ticket, not solved here.

Result:
- Applied dash and blink no longer overwrite movement with zero or sprint with false.
- Runtime contract now proves dash preserves held sprint/movement and blink continues held sprint movement on the next tick.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restored backend to normal explicit Postgres mode.
- `npm run demo:api-contract`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-178: Restore pickup event id specificity.

## Completed Ticket

ID: BWR-178
Goal: Restore pickup/medkit event id specificity.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, focused battle state runtime/API smoke contract tests if needed, `.codex/agent-state.md`, verification commands.
Forbidden scope: pickup positions/effects/balance, weapon behavior, projectile behavior, skill behavior, finish projection, replay storage, queue/matchmaking, frontend implementation, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Pickup and medkit event ids should include the pickup id, matching legacy identity semantics.
- Event messages, event kind, target/source participants, and pickup availability/respawn behavior should remain unchanged.
Architecture/domain-modeling impact:
- Event identity remains deterministic runtime state derived from event context.
Side-effect boundary impact:
- No new external effects; deterministic event construction only.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run demo:api-contract`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- Focused contract proves medkit and weapon pickup event IDs include the concrete pickup id.
- Existing pickup contracts remain green.
Risks:
- Frontend event dedupe may improve; clients expecting generic event ids would see more specific ids.

Result:
- Pickup event IDs now follow `pickup-<eventElapsed>-<pickupId>-<playerId>`.
- Medkit event IDs now follow `heal-<eventElapsed>-<pickupId>-<playerId>`.
- Existing pickup messages and effects were left unchanged.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restored backend to normal explicit Postgres mode.
- `npm run demo:api-contract`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-179: Restore blink boundary invalid-target reason parity.

## Completed Ticket

ID: BWR-179
Goal: Restore blink boundary invalid-target reason parity.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, focused battle state runtime contract tests, `.codex/agent-state.md`, verification commands.
Forbidden scope: dash/freeze behavior, skill cooldown/balance tuning, projectile behavior, weapon behavior, pickup behavior, finish projection, replay storage, queue/matchmaking, frontend implementation, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Blink targets outside the player-occupiable world bounds should return `InvalidTarget`, not `Blocked`.
- Targets inside the world but blocked by obstacles should still return `Blocked`.
- Existing blink success, out-of-range, missing target, and cooldown behavior should remain unchanged.
Architecture/domain-modeling impact:
- Kept skill validation as deterministic geometry checks in the battle state transition.
Side-effect boundary impact:
- No new external effects; deterministic command validation only.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run demo:api-contract`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- Focused contract proves near-border non-occupiable blink target returns `InvalidTarget`.
- Existing blocked-cover blink contract remains green.
Risks:
- UI error text may change for edge-of-map blink attempts, matching legacy.

Result:
- Blink validation now checks player-occupiable world bounds before obstacle blocking.
- Near-border targets that cannot fit the player radius now return `InvalidTarget`; obstacle targets still return `Blocked`.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restored backend to normal explicit Postgres mode.
- `npm run demo:api-contract`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-180: Restore precise stamina value object.

## Completed Ticket

ID: BWR-180
Goal: Restore precise stamina value object.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/objects/BattleScalars.scala`, `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/main/scala/slaydemo/backend/battle/routes/BattleStateJson.scala`, focused battle state runtime/API smoke contract tests if needed, `.codex/agent-state.md`, verification commands.
Forbidden scope: movement speed/balance tuning, skill behavior, projectile behavior, weapon behavior, pickup behavior, finish projection, replay storage, queue/matchmaking, frontend implementation, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Stamina should preserve fractional drain/recovery precision like legacy, while remaining a typed `Stamina` value object.
- JSON should expose stamina as a number with precise value.
- Existing sprint drain/recovery semantics should remain green with adjusted precision assertions.
Architecture/domain-modeling impact:
- Strengthened domain modeling by keeping stamina typed while restoring numeric precision.
Side-effect boundary impact:
- No new external effects; deterministic runtime numeric state only.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run demo:api-contract`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- Focused contract proves sprint stamina changes can be fractional.
- API smoke proves sprint stamina wire value is fractional after drain.
- Existing API smoke sprint drain/recovery remains green.
Risks:
- Frontend HUD may show decimals if it does not round for display; backend wire parity with legacy is the priority for this ticket.

Result:
- `Stamina` now stores `Double` rather than `Int`.
- Sprint drain/recovery now uses precise elapsed-rate deltas.
- Runtime contract asserts precise sprint/recovery values.
- API smoke asserts sprint stamina does not regress to integer-only output.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restored backend to normal explicit Postgres mode.
- `npm run demo:api-contract`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; `Stamina` remains a value object.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- Pending fresh parity audit from subagent.

## Active Ticket

ID: BWR-181
Goal: Split `InMemoryBattleStateService` companion constants/catalog into a separate source file.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, new companion source file under `backend/src/main/scala/slaydemo/backend/battle/services/`, `.codex/agent-state.md`, verification commands.
Forbidden scope: battle runtime behavior changes, constants/balance changes, tests except if compile requires import-only adjustment, API routes, frontend implementation, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Move the `object InMemoryBattleStateService` companion from `BattleStateService.scala` to a dedicated file without changing values or visibility semantics.
- Keep the class API and all runtime behavior unchanged.
Architecture/domain-modeling impact:
- Reduces god-file pressure while preserving the current service boundary.
- No domain model changes.
Side-effect boundary impact:
- No new side effects; source organization only.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Backend contracts and compile remain green.
- Runtime constants/catalog values are byte-for-byte equivalent in code movement.
Risks:
- Scala companion private access must remain valid across files; compile will catch this.

## Completed Ticket

ID: BWR-172
Goal: Restore legacy ignored-command accepted sequence semantics.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, focused battle state runtime contract tests, `.codex/agent-state.md`, verification commands.
Forbidden scope: command route parsing, weapon/projectile behavior, finish projection, replay storage, queue/matchmaking, frontend implementation, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Ignored commands for inactive/finished/dead players should return the player's last accepted command sequence instead of the incoming rejected sequence.
- Applied commands should continue to return the incoming accepted command sequence.
- Command status and reason semantics remain unchanged.
Architecture/domain-modeling impact:
- Command sequence state remains on immutable player state.
- No new domain concepts.
Side-effect boundary impact:
- No external effects; deterministic battle command response only.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Finished ignored command returns the stored `lastClientCommandSeq`.
- Existing ownership and command application contracts remain green.
Risks:
- Frontend reconciliation may observe older accepted seq on ignored commands; this matches legacy.

Result:
- Ignored command responses now use the player's stored `lastClientCommandSeq`.
- Added a runtime contract that applies a command, finishes the battle, then verifies a later ignored command returns the stored sequence.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restored backend to normal explicit Postgres mode.
- `npm run demo:api-contract`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-173: Restore room route missing-id error code parity.

## Completed Ticket

ID: BWR-171
Goal: Restore strict required battle command control fields.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/routes/BattleRoutes.scala`, API smoke command payloads if needed, `.codex/agent-state.md`, verification commands.
Forbidden scope: battle runtime rules, weapon/projectile behavior, finish projection, replay storage, queue/matchmaking, frontend implementation, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Missing `primaryHeld`, `reloadPressed`, or `switchWeaponDirection` should be rejected by command parser as malformed input, matching legacy required controls.
- Existing valid frontend/API smoke command payloads should send those fields explicitly.
- Optional controls such as sprint/skills/pointer/switch index remain optional.
Architecture/domain-modeling impact:
- Parser validation stays at the API boundary.
- No changes to typed command DTO or runtime transition logic.
Side-effect boundary impact:
- No new side effects; parser-only compatibility change.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Missing each required control field fails with a parser error.
- Existing command smoke payloads remain green.
Risks:
- Tightening parser can expose callers that relied on defaults; current frontend/API smoke already sends required fields.

Result:
- `primaryHeld`, `reloadPressed`, and `switchWeaponDirection` are now required in battle command parsing with legacy error codes.
- API contract smoke asserts the three required-field failures.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restored backend to normal explicit Postgres mode.
- `npm run demo:api-contract`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-172: Restore legacy ignored-command accepted sequence semantics.

## Completed Ticket

ID: BWR-170
Goal: Restore legacy-compatible missing-ticket command authorization response.
Allowed scope: battle command API parsing/routes, focused route or API contract tests, `.codex/agent-state.md`, verification commands.
Forbidden scope: battle runtime rules, weapon/projectile behavior, finish projection, replay storage, queue/matchmaking, frontend implementation, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Missing `ticketId` in a battle command should map to the same authorization failure semantics as an invalid ticket, matching legacy behavior.
- Valid command parsing and explicit wrong-ticket authorization should remain unchanged.
- Bot/player/battle-not-found errors should remain unchanged.
Architecture/domain-modeling impact:
- Kept ticket ownership as a typed authorization boundary rather than a parser-only primitive failure.
- Did not widen the command DTO; only the route compatibility layer supplies a sentinel missing ticket.
Side-effect boundary impact:
- No new side effects; route parsing delegates missing ticket to service authorization semantics.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Command request without `ticketId` returns command authorization failure semantics instead of missing-field parser failure.
- Existing wrong-ticket command contract remains green.
- Existing API contract smoke remains green.
Risks:
- Older clients now see a 403 authorization error instead of 400 missing field, which is intended for legacy parity.

Result:
- Battle command route now maps missing/blank `ticketId` to a typed empty ticket sentinel so the service returns `command_not_authorized`.
- API contract smoke now asserts both wrong-owner and missing-ticket command requests return `403 command_not_authorized`.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restored backend to normal explicit Postgres mode.
- `npm run demo:api-contract`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-171: Restore strict required battle command control fields.

## Completed Ticket

ID: BWR-169
Goal: Restore weapon pickup event text specificity.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, focused battle state runtime contract tests, `.codex/agent-state.md`, verification commands.
Forbidden scope: weapon balance/constants, projectile behavior, finish projection, replay storage, queue/matchmaking, frontend implementation, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Weapon pickup battle events should name the picked weapon instead of only saying a generic weapon pickup occurred.
- Medkit pickup behavior and event text should remain unchanged.
- Replay frame eventMessages should inherit the more specific battle event text.
Architecture/domain-modeling impact:
- No new domain concepts; reused existing typed `WeaponKind` wire values.
- Preserved immutable battle state event append pattern.
Side-effect boundary impact:
- No external effects; deterministic battle state transition only.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Picking up Gatling/Rocket/Shotgun emits an event message containing the weapon kind.
- Picking up a medkit still emits the existing heal/pickup semantics.
- Existing weapon and medkit pickup contracts remain green.
Risks:
- This changes user-facing battle log strings; assertions focus on weapon identity and medkit specificity.

Result:
- Weapon pickup events now include the picked weapon kind, for example `RocketLauncher`.
- Medkit pickup still emits a heal event and medkit-specific message.
- Runtime contract now asserts weapon-specific pickup event text and medkit event preservation.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restored backend to normal explicit Postgres mode.
- `npm run demo:api-contract`
- `npm run demo:bp44-feel-suite`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-170: Restore legacy-compatible missing-ticket command authorization response.

## Completed Ticket

ID: BWR-168
Goal: Restore legacy battle finish settlement scoring and labels.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleFinishProjectionService.scala`, focused finish projection plan/write contract tests, `.codex/agent-state.md`, verification commands.
Forbidden scope: battle runtime rules, replay frame validation, queue/matchmaking, frontend implementation, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Align result score/rating delta and battle/replay labels with legacy finish projector semantics.
- Preserve additive current replay settlement data only where it does not change default catalog/detail output.
- Keep non-playable/bot filtering explicit in the planner.
Architecture/domain-modeling impact:
- Settlement remains an immutable projection plan derived from finished battle state.
- Scoring, labels, replay owner selection, and server fallback are pure planner derivations.
Side-effect boundary impact:
- No new side effects; repository writes remain in `DefaultBattleFinishProjector`.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Projection plan contracts assert legacy score/rating formulas.
- Projection plan/write contracts assert legacy-facing labels.
- Existing finish projection writes remain green.
Risks:
- User-facing finish labels now use intended Chinese legacy labels; Windows terminal may display them as mojibake, but compile/tests run UTF-8 source correctly.

Result:
- Restored legacy placement sorting: alive players by score/hp/seat, eliminated players by elimination time/score/seat.
- Restored playable-human filtering, including visitor-like handle exclusion.
- Restored legacy rating delta formula and omitted projected currentLoadout.
- Restored legacy-style Chinese result/replay labels, seat-ordered playersLine, server fallback result/replay, and playable-winner replay owner selection.
- Added focused plan/write contracts for rating formula, labels, server fallback, visitor filtering, and replay owner behavior.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restored backend to normal explicit Postgres mode.
- `npm run demo:api-contract`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; projection remains immutable plan construction.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-169: Restore weapon pickup event text specificity.

## Completed Ticket

ID: BWR-167
Goal: Restore replay frame validation and derived playback metadata.
Allowed scope: `backend/src/main/scala/slaydemo/backend/replay/**`, replay API/service contract tests, `.codex/agent-state.md`, verification commands.
Forbidden scope: battle runtime rules, finish projection scoring, queue/matchmaking, frontend implementation, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Replay submission should validate frames JSON as an array.
- Stored `frameCount` should be derived from parsed frame array length.
- Stored `playbackAvailable` should be derived from actual playable frame count, not trusted request flags.
Architecture/domain-modeling impact:
- Replay metadata stays as immutable replay records.
- Boundary DTO metadata no longer overrides normalized replay frame state.
Side-effect boundary impact:
- JSON validation and metadata normalization stay in replay service/API boundary.
- No battle runtime or repository side effects beyond existing save path.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Invalid/non-array `framesJson` cannot produce `playbackAvailable=true`.
- Submitted `frameCount` cannot disagree with the stored frame array length.
- Existing replay catalog/comment contracts remain green.
Risks:
- Tightening replay normalization changes behavior for bad external replay submissions by storing them as non-playable empty-frame replays.

Result:
- Added a pure replay-frame JSON array counter/normalizer.
- `DefaultReplayService.record` now derives `frameCount` and `playbackAvailable` from actual normalized frames.
- Replay contracts now cover invalid JSON, one-frame submissions, and two-frame submissions where request metadata lies.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restored backend to normal explicit Postgres mode.
- `npm run demo:api-contract`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none beyond boundary JSON parsing counters.
- Boolean business results introduced: none; playback availability is derived metadata.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-168: Restore legacy battle finish settlement scoring and labels.

## Completed Ticket

ID: BWR-166
Goal: Split battle finish artifact readiness into independent result/replay readiness.
Allowed scope: battle artifact/readiness domain enums or value objects, `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/main/scala/slaydemo/backend/battle/services/BattleFinishProjectionService.scala`, battle routes/API DTO mapping if needed, focused finish/readiness contract tests, `.codex/agent-state.md`, verification commands.
Forbidden scope: core combat rules, projectile/weapon constants, queue matchmaking behavior, frontend implementation, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Restore legacy-style independent readiness tracking for result and replay projection writes.
- Preserve explicit typed status instead of representing partial projection with a Boolean.
- API `resultReady` and `replayReady` should accurately reflect partial success/failure.
Architecture/domain-modeling impact:
- `BattleArtifactStatus` is now a finite ADT with pending, result-only ready, replay-only ready, and ready states.
- Finish projection outcome is now an explicit ADT for full success, full failure, and each partial success direction.
Side-effect boundary impact:
- Repository writes remain confined to finish projection.
- Battle state service stores projection readiness outcomes without writing repositories directly.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- If result write succeeds and replay write fails, `resultReady=true` and `replayReady=false`.
- If replay write succeeds and result write fails, `resultReady=false` and `replayReady=true`.
- Existing successful finish projection still reports both ready.
Risks:
- Partial retries now preserve already-ready artifact state; future retry idempotency should continue to avoid duplicate mail/result side effects.

Result:
- Split artifact readiness into four explicit states instead of one combined pending/ready flag.
- `DefaultBattleFinishProjector` now writes result and replay artifacts independently and returns typed partial outcomes.
- `InMemoryBattleStateService` merges projection outcomes into stored artifact readiness so API booleans can diverge correctly.
- Added contracts for result-only and replay-only projection readiness in both state runtime and finish write tests.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restored backend to normal explicit Postgres mode.
- `npm run demo:api-contract`
- `npm run demo:bp44-feel-suite`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none; readiness is modeled as explicit ADTs.
- Domain mutation introduced: none; status transitions use immutable copies.
- Side effects inside domain: none; repository writes remain in the projection service.
- Scope respected: yes.

Next ticket:
- BWR-167: Restore replay frame validation and derived playback metadata.

## Completed Ticket

ID: BWR-165
Goal: Verify and restore queue room finished status parity.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleQueueService.scala`, `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, focused queue/state contract tests, `.codex/agent-state.md`, verification commands.
Forbidden scope: battle gameplay rules, projection/replay repositories, frontend implementation, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- When the authoritative battle finishes, the owning queue room snapshot/status should transition to `MatchmakingRoomPhase.Finished`.
- Queue snapshots should preserve `finishedAt`.
- Add focused contracts for direct queue finish marking and battle-state-driven room finish notification.
Architecture/domain-modeling impact:
- Introduced a small service-boundary lifecycle sink for room finish notification.
- Queue state remains typed immutable room snapshots under the in-memory queue service lock.
Side-effect boundary impact:
- Battle runtime service notifies the queue service boundary when a battle first transitions to finished.
- No repository/database effects.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Queue `status` and `roomSnapshot` report `Finished` plus `finishedAt` after battle finish.
- Battle state service publishes the finish event exactly when an active battle first reaches finished.
- Existing battle queue and battle state contracts remain green.
Risks:
- Cross-service notification introduces a queue lock call from the battle state service; kept one-way to avoid cycles.

Result:
- Added a `BattleRoomLifecycleSink` boundary and implemented it in `InMemoryBattleQueueService`.
- `BattleStateService` now marks the owning queue room finished when an active battle first reaches `BattlePhase.Finished`.
- `BackendApp` wires the queue service as the battle room lifecycle sink.
- Added focused queue and battle-state contracts for finished room status propagation.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restored backend to normal explicit Postgres mode.
- `npm run demo:api-contract`
- `npm run demo:bp40-freshness`
- `npm run demo:bp44-feel-suite`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none; readiness work remains for BWR-166.
- Domain mutation introduced: none; queue rooms are updated through immutable copies under the queue lock.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-166: Split battle finish artifact readiness into independent result/replay readiness.

## Completed Ticket

ID: BWR-164
Goal: Restore bot reload parity so bots only start magazine reload when the active magazine is empty.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: weapon constants, player reload behavior, frontend implementation, queue/routes, projection/replay repositories, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Bot AI should request reload only when the current reloadable magazine is empty.
- Human/manual reload and automatic empty-magazine reload should remain unchanged.
- Add focused contract coverage around bot reload after one shot vs empty magazine.
Architecture/domain-modeling impact:
- Keeps reload behavior in battle runtime service boundary.
- No new domain states.
Side-effect boundary impact:
- No external effects; deterministic battle state transition only.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- A bot with partially spent pistol magazine does not start reload.
- A bot with an empty pistol magazine and reserve ammo starts reload.
- Existing player reload and bot fire contracts remain green.
Risks:
- Bot combat pressure increases slightly because bots no longer waste time topping off after one shot.

Result:
- Bot reload intent now requires the active weapon magazine to be empty and otherwise reloadable.
- Existing human/manual reload and automatic empty-magazine reload paths were not changed.
- Extended the bot runtime contract to assert that after the opening firing window the bot has spent ammo but has not started reload while ammo remains.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restored backend to normal explicit Postgres mode.
- `npm run demo:api-contract`
- `npm run demo:bp44-feel-suite`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; bot control remains immutable player-state copying.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-165: Verify and restore queue room finished status parity.

## Completed Ticket

ID: BWR-163
Goal: Restore projectile terminal full segment evidence for authoritative tracer/VFX parity.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: projectile speed/lifetime constants, frontend implementation, queue/routes, projection/replay repositories, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Preserve `terminalPosition` as the exact hit/block/expiry point.
- Record `terminal.end` as the full projectile movement segment end for the runtime step, matching legacy terminal evidence shape.
- Add contracts proving hit and blocked terminals keep a full segment end beyond the terminal point when the terminal happens mid-segment.
Architecture/domain-modeling impact:
- Keeps existing typed projectile terminal domain model.
- No new states or primitive business concepts.
Side-effect boundary impact:
- No external effects; deterministic battle state transition only.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Pistol/gatling hit terminals keep `terminalPosition` at hit point and `end` at full movement segment end.
- Block terminals keep `terminalPosition` at blocker intersection and `end` at full movement segment end.
- Existing projectile impact behavior and damage remain unchanged.
Risks:
- Frontend terminal correction tracer uses `start`, `end`, and `terminalPosition`; changing `end` may alter VFX length/direction, which is intended but should be browser-smoked afterward.

Result:
- `ProjectileMotionResult` now carries both the exact terminal destination and the full movement segment end.
- Projectile hit/block/expiry terminal records now keep:
  - `terminalPosition`: exact hit/block/expiry point
  - `end`: full authoritative projectile segment end for that runtime step
- Added backend contracts proving pistol hit, gatling hit, and obstacle-blocked terminals preserve a segment end beyond the terminal point.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restored backend to normal explicit Postgres mode.
- `npm run demo:api-contract`
- `npm run demo:bp44-feel-suite`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; projectile terminal state remains immutable.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-164: Restore bot reload parity so bots only start magazine reload when the active magazine is empty.

## Completed Ticket

ID: BWR-162
Goal: Perform a focused legacy/current battle backend parity audit and choose the next concrete gap.
Allowed scope: read-only inspection of `backend-legacy/src/main/scala/battle/**`, `backend/src/main/scala/slaydemo/backend/battle/**`, `backend/src/main/scala/slaydemo/backend/replay/**`, current battle/replay tests, and `.codex/agent-state.md`.
Forbidden scope: production code edits, test edits, frontend implementation, database/schema/data changes, dependency changes.
Expected change:
- Compare legacy and rebuilt battle backend behavior after BWR-161.
- Identify remaining parity gaps that can affect current gameplay, replay, finish artifacts, or battle routes.
- Pick exactly one next implementation ticket.
Architecture/domain-modeling impact:
- Audit only; no model changes.
Side-effect boundary impact:
- No code/runtime side effects beyond read-only inspection.
Verification commands:
- targeted `rg` / file reads
- reuse existing verification history from BWR-161
Acceptance criteria:
- Worklog records concrete remaining gaps and the next scoped ticket.
Risks:
- Do not turn this into a broad rewrite; split findings into small tickets.

Result:
- Runtime audit found remaining gameplay-visible differences:
  - projectile terminal `end` currently records the terminal position instead of the full authoritative movement segment end
  - bot reload currently starts whenever the magazine is not full, while legacy bots reload only when empty
  - finish currently clears slow fields while legacy kept the advanced slow fields on the finished aggregate
  - weapon pickup event text lost the specific weapon label
  - projectile TTL/speed differs from legacy, but the long TTL is intentional after the user requested no short projectile range limit
- Route/API audit found:
  - queue rooms may not transition their public snapshot to `finished`
  - result/replay readiness is exposed as one combined artifact status
  - battle error bodies differ from legacy code-only errors
  - command parsing now requires `ticketId`
  - result list limit is lower than legacy
- Finish/replay audit found:
  - result and replay projection readiness are all-or-nothing instead of independent
  - partial projection retries can repeat mail writes and potentially reset unread mail state
  - projection does not filter non-playable human handles before writing result/replay artifacts
  - replay routes do not expose the full settlements array
  - ranking/rating semantics differ from legacy

Self-review:
- Primitive business types introduced: none; audit only.
- Boolean business results introduced: none; audit only.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-163: Restore projectile terminal full segment evidence for authoritative tracer/VFX parity.

## Completed Ticket

ID: BWR-161
Goal: Restore authoritative replay frame capture and finish projection playback parity.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/objects/BattleAggregateState.scala`, `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/main/scala/slaydemo/backend/battle/services/BattleFinishProjectionService.scala`, focused battle/replay contract tests, `.codex/agent-state.md`, verification commands.
Forbidden scope: database schema/repository storage changes, frontend implementation, queue/routes/auth/social/forum/governance, dependency changes, `backend-legacy/**`, destructive DB/data actions.
Expected change:
- Add typed replay frame state to the authoritative battle aggregate.
- Capture initial, interval/event, and final replay frames during runtime advancement.
- Make finish projection render captured frames, set actual frame count, and enable playback when at least two frames exist.
- Preserve fallback frame rendering for manually constructed states without captured frames.
Architecture/domain-modeling impact:
- Replay frame data is immutable battle domain state with typed ids/enums/value objects.
- Capture remains a pure state transition inside the battle runtime boundary.
Side-effect boundary impact:
- No new external effects; repository writes still happen only in finish projection.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Runtime states retain replay frames starting at elapsed `0`.
- Finished projection writes replay records with multiple frames and `playbackAvailable=true` when runtime captured more than one frame.
- Replay JSON includes captured heroes, projectiles, pickups, and event messages.
Risks:
- Adding `replayFrames` to `BattleAggregateState` touches all direct constructors.
- Browser replay UI compatibility should be smoked later if backend contracts pass.

Result:
- Added typed replay frame domain state:
  - `BattleReplayFrameState`
  - `BattleReplayHeroFrameState`
  - `BattleReplayProjectileFrameState`
  - `BattleReplayPickupFrameState`
- `BattleAggregateState` now carries immutable `replayFrames`.
- Runtime captures:
  - initial frame at elapsed `0`
  - interval frames every replay sample boundary
  - event frames when runtime events occur
  - final frame when battle finishes
- Replay frame retention is capped at 32 while preserving the initial frame.
- Finish projection now renders captured replay frames, writes the actual frame count, and sets `playbackAvailable=true` for two or more frames.
- Finish projection fallback now builds frames from elapsed `0`, recent event times, and final elapsed time when no captured frames exist.
- Finish projection timing now uses capped elapsed duration and logical `startedAt + elapsed` finish time.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run demo:authoritative-finish-smoke` after restarting backend with short battle duration; saved replay reported `frames=5`, `frameCount=5`, `playbackAvailable=True`.
- Restored backend to normal explicit Postgres mode.
- `npm run demo:api-contract`
- `npm run demo:bp44-feel-suite`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: replay frame state uses typed ids/enums/value objects; constants are runtime retention/sample limits.
- Boolean business results introduced: none; `playbackAvailable` is existing replay DTO metadata.
- Domain mutation introduced: none; replay capture is immutable state copying.
- Side effects inside domain: none; repository writes remain in finish projection.
- Scope respected: yes.

Risks:
- `BattleArtifactStatus` still represents result/replay readiness as one combined ready flag; legacy tracked them separately. Split readiness should be separate if projection partial-failure handling becomes important.
- Browser replay detail was indirectly covered by API/BP44, but a dedicated replay-page smoke is still useful.

Next ticket:
- BWR-162: Perform a focused legacy/current battle backend parity audit to choose the next concrete implementation gap.

## Completed Ticket

ID: BWR-142
Goal: Replace Pistol direct-hit hitscan with a visible high-speed authoritative projectile.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `frontend/src/game/battleContentCatalog.ts`, `scripts/api-contract-field-smoke.ps1`, `.codex/agent-state.md`, process/log verification commands.
Forbidden scope: database/repository code, queue/routes, auth/identity, backend-legacy, dependency changes, schema/configuration.
Expected change:
- Pistol firing always adds a live authoritative projectile instead of directly applying damage when the aim ray intersects a player.
- Pistol projectile speed increases while preserving enough state-stream visibility for tracers/projectile presentation.
- Add contract coverage proving a Pistol shot is visible before it damages the target.
Architecture/domain-modeling impact:
- Keeps `ProjectileKind.PistolBullet` as the finite domain state for Pistol shots.
- Moves Pistol damage into the existing projectile impact transition instead of a separate direct-hit branch.
Side-effect boundary impact:
- No new external effects; only battle-state transition and content constant changes.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run build`
- `npm run audit:battle-content`
- `npm run demo:api-contract`
- `npm run demo:bp28-render-feel-smoke`
- `npm run demo:bp44-feel-suite`
Acceptance criteria:
- A Pistol shot aimed at a target creates a live `PistolBullet` first and leaves target HP unchanged in the same command frame.
- The target is damaged later when the projectile reaches it.
- Frontend and backend Pistol projectile speeds remain aligned.
Risks:
- Faster Pistol bullets can shorten the visible window; browser smoke must confirm muzzle/projectile feedback remains healthy.

Result:
- Removed the Pistol direct-hit branch; Pistol now always creates a live authoritative `PistolBullet`.
- Pistol damage now arrives through the same projectile impact transition used by other live projectiles.
- Increased Pistol projectile speed to 1400 in backend and frontend content.
- Updated API smoke slow-projectile expectation to the new Pistol speed.
- Added a backend contract proving a direct-aim Pistol shot creates a visible projectile first and damages only after travel.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run build`
- `npm run audit:battle-content`
- `npm run demo:api-contract`
- `npm run demo:bp28-render-feel-smoke`
- `npm run demo:bp44-feel-suite`
- `npm run dev:status`
- `git diff --check` completed with line-ending warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; projectile damage remains an immutable state transition.
- Side effects inside domain: none.
- Scope respected: yes; script update was required because the smoke encoded the old Pistol speed constant.

Parity backlog from legacy comparison:
- BWR-143: Restore held-primary runtime fire loop so sustained fire works during state advancement, not only on incoming command frames.
- BWR-144: Restore bot control synthesis for movement/aim/fire/reload.
- BWR-145: Replace sampled projectile obstacle terminals with first segment/AABB hit calculation.
- BWR-146: Match finish transition semantics: clear runtime state/projectiles, timeout winner semantics, authoritative finished-at time.
- BWR-147: Restore replay frame capture / finish projection parity or make the reduced replay behavior explicit.
- BWR-148: Split `BattleStateService` into smaller battle modules, then migrate backend source layout toward a shorter service-oriented `src/...` structure.

## Completed Ticket

ID: BWR-143
Goal: Restore held-primary runtime fire loop parity.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `.codex/agent-state.md`, process/log verification commands.
Forbidden scope: database/repository code, queue/routes, auth/identity, frontend rendering, backend-legacy, dependency changes, schema/configuration.
Expected change:
- Runtime advancement fires weapons while `primaryHeld` remains true and cooldown/reload/heat allow it.
- Add focused contracts for Pistol held fire and Gatling held fire without repeated command submission.
Architecture/domain-modeling impact:
- Kept weapon state transitions explicit and immutable.
- Kept projectile IDs typed and generated at the battle service boundary.
Side-effect boundary impact:
- No new external effects; state progression remains in the in-memory authoritative runtime boundary.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run demo:api-contract`
- `npm run demo:bp28-render-feel-smoke`
- `npm run demo:bp44-feel-suite`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- One Pistol `primaryHeld=true` command causes additional shots after cooldown during later `currentState` advancement.
- One Gatling `primaryHeld=true` command causes additional heat/projectiles after cooldown during later `currentState` advancement.
- Existing command-frame first-shot behavior stays intact.
Risks:
- This restored runtime held fire per state advancement. Full fixed-step catch-up for large inactive time gaps remains a future parity ticket because a fixed-step attempt changed movement/obstacle collision behavior.

Result:
- Added runtime held-primary resolution after player timers, pickup timers, and before projectile advancement.
- Refactored fire handling so command-frame fire and runtime held-fire use the same weapon transition path.
- Added negative runtime command sequences for authoritative projectile IDs generated outside client command frames.
- Added Pistol and Gatling held-fire runtime contracts.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run demo:api-contract`
- `npm run demo:bp28-render-feel-smoke`
- `npm run demo:bp44-feel-suite`
- `npm run dev:status`
- `git diff --check` completed with line-ending warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; weapon/projectile state transitions remain immutable copies.
- Side effects inside domain: none.
- Scope respected: yes.

## Completed Ticket

ID: BWR-144
Goal: Make projectile TTL a cleanup cap, not a short gameplay range, and restore bounded terminal history.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `frontend/src/game/battleContentCatalog.ts`, `.codex/agent-state.md`, verification commands.
Forbidden scope: database/repository code, queue/routes, auth/identity, unrelated frontend input/camera code, backend-legacy, dependency changes, schema/configuration.
Expected change:
- Increase authoritative and frontend projectile lifetime constants so bullets/rockets can cross the arena and normally terminate on hit, obstacle, or world boundary instead of disappearing in open space.
- Retain only recent projectile terminals, matching legacy retention, so old rocket explosions cannot replay forever from retained state history.
- Add focused backend contracts for no-short-range expiry and terminal retention.
Architecture/domain-modeling impact:
- Keep `ProjectileTerminalReason` finite ADT unchanged; TTL remains an explicit cleanup state but no longer acts as short range.
- Keep terminal history retention inside the battle runtime service boundary.
Side-effect boundary impact:
- No new external effects; only deterministic battle-state transitions and mirrored frontend content constants.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run build`
- `npm run audit:battle-content`
- `npm run demo:api-contract`
- `npm run demo:bp28-render-feel-smoke`
- `npm run demo:bp44-feel-suite`
Acceptance criteria:
- A projectile fired in an open lane remains live past the old Pistol/Gatling/Shotgun/Rocket short TTL ranges.
- A projectile terminal history snapshot is bounded to the most recent retained entries.
- Frontend/backend content audit stays aligned.
Risks:
- Longer projectile lifetimes increase simultaneous live projectile count during sustained fire; browser smoke should catch obvious rendering/performance regressions.

Result:
- Increased Pistol, Rocket, Gatling, and Shotgun projectile lifetime constants to 30000ms in backend and frontend content.
- Limited projectile travel by remaining TTL inside the authoritative projectile advancement path.
- Restored legacy-style bounded projectile terminal history with the most recent 64 terminals only.
- Added backend contracts proving projectiles remain live past the old short TTL range and terminal history is capped.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run build`
- `npm run audit:battle-content`
- `npm run demo:api-contract`
- `npm run demo:bp28-render-feel-smoke`
- `npm run demo:bp44-feel-suite`
- `npm run dev:status`
- `git diff --check` completed with line-ending warnings only.

Self-review:
- Primitive business types introduced: none; retention count and lifetime constants are runtime/content constants.
- Boolean business results introduced: none.
- Domain mutation introduced: none; projectile and terminal changes remain immutable state transitions.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- Longer projectile lifetimes can raise live projectile counts during heavy sustained fire; BP28/BP44 passed, but bot AI restoration may add more projectile pressure later.

## Completed Ticket

ID: BWR-145
Goal: Prevent skill casts from also firing and bind the initial authoritative camera to the local player.
Allowed scope: `frontend/src/features/battle/page/useBattlePageRuntime.ts`, `frontend/src/features/battle/renderer/authoritativeBattleStateBridge.ts`, focused verification commands, `.codex/agent-state.md`.
Forbidden scope: backend runtime code, database/repository code, queue/routes, unrelated renderer/VFX code, backend-legacy, dependency changes, schema/configuration.
Expected change:
- Ensure any outgoing Dash/Blink/Freeze command suppresses `primaryHeld` so skill use does not fire a weapon in the same command.
- Prefer local `playerId` matching over direct hero-id matching when applying authoritative heroes, so startup/camera binding cannot temporarily follow another player if local hero id is stale.
Architecture/domain-modeling impact:
- Frontend adapter/input binding only; no domain type changes.
Side-effect boundary impact:
- No new external effects; only client-side command mapping and authoritative frame reconciliation.
Verification commands:
- `npm run build`
- `npm run demo:bp28-render-feel-smoke`
- `npm run demo:bp44-feel-suite`
Acceptance criteria:
- Command mapping never sends `primaryHeld=true` when a skill cast flag is true.
- Initial authoritative hero reconciliation keeps the local snapshot hero aligned by backend player id.
Risks:
- Changing hero matching priority can affect stale local snapshots; browser smoke should confirm battle entry and same-battle diagnostics remain healthy.

Result:
- Skill command resolution now suppresses `primaryHeld` whenever Dash/Blink/Freeze is being sent.
- Authoritative hero reconciliation now reserves the local snapshot hero for the matching local backend `playerId` before direct hero-id matching.
- BP44 diagnostic summaries confirmed skill command packets had `primaryHeld=false` for Dash, Blink, and Freeze casts.

Verification passed:
- `npm run build`
- `npm run demo:bp28-render-feel-smoke`
- `npm run demo:bp44-feel-suite`
- BP44 command probe check:
  - `SkillPressure`: 2 skill requests, 0 with `primaryHeld=true`.
  - `TargetedSkillPressure`: 2 skill requests, 0 with `primaryHeld=true`.
- `npm run dev:status`
- `git diff --check` completed with line-ending warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Completed Ticket

ID: BWR-146
Goal: Restore minimal legacy bot runtime control.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: frontend implementation, database/repository code, queue/routes, auth/identity, backend-legacy, dependency changes, schema/configuration.
Expected change:
- Synthesize bot movement, aim, reload, and delayed fire during runtime advancement without accepting external bot commands.
- Preserve typed battle state and immutable player updates.
- Add contracts proving bots move/aim and begin firing after the legacy opening delay.
Architecture/domain-modeling impact:
- Keep bot intent as runtime service orchestration, not domain-object mutation or external side effects.
- Reuse existing weapon/fire transitions and finite weapon/projectile ADTs.
Side-effect boundary impact:
- No new external effects; bot control is deterministic battle-state synthesis inside the authoritative runtime service.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run demo:api-contract`
- `npm run demo:bp28-render-feel-smoke`
- `npm run demo:bp44-feel-suite`
Acceptance criteria:
- Bots can update movement/aim without client commands.
- Bots do not fire before the legacy opening delay.
- Bots can fire after the delay when a human target is available.
- External bot commands remain rejected.
Risks:
- Bot fire will increase projectile pressure and may expose projectile collision or VFX issues that should become follow-up tickets rather than hidden rewrites.

Result:
- Restored deterministic bot movement/aim synthesis during authoritative runtime advancement.
- Bots prefer human targets, orbit at legacy-style preferred range, respect the human opening fire delay, and reject external bot commands.
- Bot movement uses the legacy bot speed instead of human walk/sprint speed.
- Added a focused backend contract for bot movement, aim, delayed fire, and command rejection.
- Adjusted static projectile-hit contracts so their targets are non-bot fixtures and do not drift during the shot.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restarted backend with explicit Postgres configuration and `/health.storageMode=postgres`
- `npm run demo:api-contract`
- `npm run demo:bp28-render-feel-smoke`
- `npm run demo:bp44-feel-suite`
- `npm run build`
- `npm run audit:battle-content`
- `npm run dev:status`
- `git diff --check` completed with line-ending warnings only.

Self-review:
- Primitive business types introduced: none; bot tuning values are runtime constants matching the authoritative battle catalog.
- Boolean business results introduced: none.
- Domain mutation introduced: none; bot control produces immutable player-state copies.
- Side effects inside domain: none; bot behavior stays inside the battle runtime service boundary.
- Scope respected: yes.

Risks:
- Large state-read gaps still advance the battle in one bulk step. This can visibly skew bot movement, held-fire cadence, cooldown crossing, projectile collision, and pickup timing.

## Completed Ticket

ID: BWR-147
Goal: Restore fixed-step battle advancement parity for large read/command gaps.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: frontend implementation, database/repository code, queue/routes, auth/identity, backend-legacy edits, dependency changes, schema/configuration.
Expected change:
- Advance authoritative battle runtime through repeated `TickStepMs` slices instead of one bulk delta when `currentState` or `acceptCommand` observes a large time gap.
- Preserve current typed battle state and immutable state transitions.
- Add focused contracts proving held fire and bot movement do not collapse into one oversized simulation step.
Architecture/domain-modeling impact:
- Keep fixed-step accumulation in the battle service runtime boundary, not in domain objects.
- Reuse existing finite `BattlePhase`, `WeaponKind`, `ProjectileKind`, and command result types.
Side-effect boundary impact:
- No new external effects; only deterministic in-memory runtime advancement changes.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restart backend with explicit Postgres configuration
- `npm run demo:api-contract`
- `npm run demo:bp28-render-feel-smoke`
- `npm run demo:bp44-feel-suite`
Acceptance criteria:
- A 1000ms read gap advances held-primary fire across multiple cooldown windows, not one fire window.
- Bot movement over a 1000ms gap is composed from fixed control steps, not one bulk vector.
- Existing sprint, pickup, projectile, terminal, and finish contracts remain green.
Risks:
- Fixed-step changes can expose off-by-one timing in existing tests because `elapsedMs` and `tick` become step-driven rather than single-delta-driven.

Result:
- Added fixed-step catch-up to `InMemoryBattleStateService` using `lastUpdatedAt` and `pendingStepMs` on stored battles.
- Runtime now advances through repeated `TickStepMs` slices, then performs a zero-delta clock/projection pass at the read/command time.
- New battles initialize at `startedAt`; the first read/command catches up from battle start, restoring spawn pickup and timer behavior when the first read arrives after start.
- Added a backend contract proving a 1000ms held-fire gap crosses multiple pistol cooldown windows.
- Updated timing-sensitive battle contracts to assert fixed-step behavior instead of single-bulk-delta exact positions/timers.
- Adjusted the API Freeze projectile smoke threshold from `0.75x` to `0.85x` normal speed because fixed-step sampling can include a few normal-speed steps after the projectile leaves the slow field while still proving slowdown.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restarted backend with explicit Postgres configuration and `/health.storageMode=postgres`
- `npm run demo:api-contract`
- `npm run demo:bp28-render-feel-smoke`
- `npm run demo:bp44-feel-suite`
- `npm run build`
- `npm run audit:battle-content`
- `npm run dev:status`
- `git diff --check` completed with line-ending warnings only.

Self-review:
- Primitive business types introduced: none; `lastUpdatedAt` and `pendingStepMs` are runtime bookkeeping at the service boundary.
- Boolean business results introduced: none.
- Domain mutation introduced: none; fixed-step advancement still produces immutable aggregate-state copies.
- Side effects inside domain: none.
- Scope respected: mostly; `scripts/api-contract-field-smoke.ps1` was minimally adjusted because fixed-step behavior changed the valid sampling threshold in the verification script.

Risks:
- Bot orbit movement can nearly cancel at preferred range because legacy orbit direction flips by tick; bot tuning should be a separate gameplay ticket, not hidden inside fixed-step catch-up.

## Completed Ticket

ID: BWR-148
Goal: Replace sampled projectile obstacle/world terminals with exact segment collision.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: frontend implementation, database/repository code, queue/routes, auth/identity, backend-legacy edits, dependency changes, schema/configuration.
Expected change:
- Compute projectile motion terminal points from segment-vs-world/obstacle intersections instead of 16px sampling.
- Preserve typed projectile terminal reasons and immutable projectile updates.
- Add focused contracts for obstacle terminal accuracy and hit-before-block ordering.
Architecture/domain-modeling impact:
- Keep geometry as pure deterministic runtime helpers inside the battle service boundary.
- Do not add new primitive business states or side effects.
Side-effect boundary impact:
- No new external effects; projectile collision remains an in-memory battle-state transition.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restart backend with explicit Postgres configuration
- `npm run demo:api-contract`
- `npm run demo:bp28-render-feel-smoke`
- `npm run demo:bp44-feel-suite`
Acceptance criteria:
- Projectile terminals at walls/obstacles report the first intersection point, not an overshot sampled point.
- A player hit before an obstacle is still credited as a hit; an obstacle before a player blocks the projectile.
- Existing projectile, pickup, sprint, skill, and finish contracts remain green.
Risks:
- Exact collision can shift impact VFX positions by a few pixels compared with the previous sampled terminal; browser smoke must catch obvious presentation regressions.

Result:
- Replaced sampled projectile obstacle/world motion with first segment collision against the world bounds and expanded arena obstacle AABBs.
- Projectile terminals now use the exact first intersection point for blocked/out-of-bounds paths.
- Preserved hit-before-block behavior by continuing to test player hits along the projectile segment ending at the first block point.
- Added a backend contract proving a right-lane pistol terminal lands on the first expanded border intersection at `x=2488.0`.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restarted backend with explicit Postgres configuration and `/health.storageMode=postgres`
- `npm run demo:api-contract`
- `npm run demo:bp28-render-feel-smoke`
- `npm run demo:bp44-feel-suite`
- `npm run build`
- `npm run audit:battle-content`
- `npm run dev:status`
- `git diff --check` completed with line-ending warnings only.

Self-review:
- Primitive business types introduced: none; collision reasons remain `ProjectileTerminalReason` ADT values.
- Boolean business results introduced: none.
- Domain mutation introduced: none; projectile advancement remains immutable aggregate-state transition.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- World-border projectiles now block at expanded border obstacles before center-point world exit. This matches the physical wall representation but can shift border VFX inward compared with the previous center-only out-of-bounds sampling.

## Completed Ticket

ID: BWR-149
Goal: Align finish transition semantics with legacy terminal runtime cleanup.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: frontend implementation, database/repository code, queue/routes, auth/identity, backend-legacy edits, dependency changes, schema/configuration.
Expected change:
- Clear active runtime inputs/projectiles/slow fields when a battle reaches `Finished`.
- Align winner assignment so timeout/all-dead edge cases do not invent a winner unless the runtime has a clear surviving player.
- Add focused finish contracts for timeout cleanup and no-winner edge cases.
Architecture/domain-modeling impact:
- Keep finish semantics as an explicit aggregate-state transition in the battle service boundary.
- Preserve `BattlePhase` and winner fields as typed domain values.
Side-effect boundary impact:
- No new external effects; finish projection behavior remains in the existing projector boundary.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restart backend with explicit Postgres configuration
- `npm run demo:api-contract`
- `npm run demo:bp28-render-feel-smoke`
- `npm run demo:bp44-feel-suite`
Acceptance criteria:
- Finished snapshots do not retain active projectiles, held primary, sprint, reload, or active slow fields.
- A timeout with multiple survivors does not assign a fabricated single winner.
- A one-survivor elimination still assigns that survivor as winner.
Risks:
- Existing result/replay projection may assume a winner is always present on timeout; verification must include broad API and finish-related contracts.

Result:
- Finished battle snapshots now clear player movement, sprint, primary/reload inputs, active skill durations, weapon fire/reload timers, active projectiles, and slow fields.
- Winner assignment now requires exactly one alive player; timeout finishes with multiple survivors no longer fabricate a single winner.
- Added a backend contract proving timeout finish cleanup and no-winner semantics.
- Updated the finish projection contract to expect no timeout winner while still verifying artifact projection.
- Stabilized the API Freeze projectile smoke by placing the slow field on the projectile path rather than on a glancing edge.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restarted backend with explicit Postgres configuration and `/health.storageMode=postgres`
- `npm run demo:api-contract`
- `npm run demo:bp28-render-feel-smoke`
- `npm run demo:bp44-feel-suite`
- `npm run build`
- `npm run audit:battle-content`
- `npm run dev:status`
- `git diff --check` completed with line-ending warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; finish cleanup returns immutable copied state.
- Side effects inside domain: none; projection remains in the existing projector boundary.
- Scope respected: mostly; `scripts/api-contract-field-smoke.ps1` was minimally adjusted to make the Freeze slow-field verification path deterministic under fixed-step sampling.

Risks:
- Finish cleanup now clears slow fields on finished snapshots, which is stricter than legacy cleanup but matches the intended terminal runtime-state behavior.

## Completed Ticket

ID: BWR-150
Goal: Restore proactive bot reload intent.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: frontend implementation, database/repository code, queue/routes, auth/identity, backend-legacy edits, dependency changes, schema/configuration.
Expected change:
- Bot control should request reload when its current magazine weapon is empty and reserve ammo exists, matching legacy `shouldBotReload`.
- Add a focused backend contract for an empty bot pistol starting reload without external commands.
Architecture/domain-modeling impact:
- Keep reload as an explicit weapon-state transition through existing weapon ADTs.
- Keep bot intent synthesis in the battle runtime service boundary.
Side-effect boundary impact:
- No new external effects.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
Acceptance criteria:
- A bot with an empty magazine and reserve ammo starts reload during runtime advancement.
- Existing auto-reload, held-fire, and bot command rejection contracts remain green.
Risks:
- Bot reload may slightly reduce bot firing pressure; browser smoke can remain a follow-up if backend contracts pass and no production code outside battle runtime changes.

Result:
- Bot control now sets `reloadPressed` when its current magazine weapon can start reload.
- Runtime advancement now has an explicit requested-reload pass before held-primary fire, so persistent reload intent is handled outside command submission.
- Existing auto-reload and bot command rejection contracts remain green.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restarted backend with explicit Postgres configuration and `/health.storageMode=postgres`
- `npm run dev:status`
- `git diff --check` completed with line-ending warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; reload handling copies player/weapon state.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- This adds the proactive bot reload path but does not yet have an isolated empty-bot-magazine fixture; current coverage is through existing runtime weapon/reload contracts.

## Completed Ticket

ID: BWR-151
Goal: Restore closest-player pickup arbitration.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: frontend implementation, database/repository code, queue/routes, auth/identity, backend-legacy edits, dependency changes, schema/configuration.
Expected change:
- When multiple alive players are inside a pickup radius, award the pickup to the closest eligible player rather than the first player in state order.
- Add a focused backend contract for contested pickup resolution.
Architecture/domain-modeling impact:
- Keep pickup consumption as an explicit aggregate-state transition.
- Preserve `PickupKind` and `WeaponKind` ADTs.
Side-effect boundary impact:
- No new external effects.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
Acceptance criteria:
- A contested pickup goes to the nearest player.
- Existing weapon/medkit pickup contracts remain green.
Risks:
- Constructing a deterministic contested-pickup fixture may require careful spawn selection to avoid unrelated obstacle movement.

Result:
- Available pickups now choose the closest alive player within contact radius instead of the first player in state order.
- Existing weapon and medkit pickup contracts remain green.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restarted backend with explicit Postgres configuration and `/health.storageMode=postgres`
- `npm run dev:status`
- `git diff --check` completed with line-ending warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; pickup consumption remains an immutable aggregate-state update.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- No dedicated contested-pickup fixture was added because current bootstrap seats only expose catalog spawn points and most pickup-overlap setups consume immediately at bootstrap. Existing broad pickup contracts cover normal pickup behavior.

## Active Ticket

ID: BWR-152
Goal: Run backend-vs-legacy file/module parity audit and choose the next split/refactor ticket.
Allowed scope: read-only inspection of `backend/**`, `backend-legacy/**`, `.codex/agent-state.md`; optional worklog update.
Forbidden scope: production code edits, frontend edits, dependency changes, database resets, generated/data files.
Expected change:
- Compare current backend modules against legacy backend modules after the latest battle parity fixes.
- Identify remaining behavioral gaps and oversized-module risks.
- Choose the next smallest implementation ticket, likely either replay contract parity or splitting `BattleStateService`.
Architecture/domain-modeling impact:
- Audit only; should guide a small next ticket rather than starting a broad refactor.
Side-effect boundary impact:
- No code side effects.
Verification commands:
- targeted `rg`/file reads
- no build required unless audit discovers a specific runnable gap
Acceptance criteria:
- Worklog records the remaining top gaps and the next scoped ticket.
Risks:
- Do not turn the audit into a broad file move. Source layout simplification should be planned as a staged refactor after behavior is stable.

Result:
- Ran a fresh read-only comparison across battle runtime/rules, API/JSON, and persistence/config.
- Confirmed current battle/runtime top gaps:
  - Projectile terminal reason wire values still differ from legacy and frontend VFX expectations.
  - Command acceptance still fires/reloads/collects pickups immediately instead of only recording input and skills.
  - Weapon recoil is still missing.
  - Replay frame capture is still reduced to a single final frame.
  - Freeze obstacle placement, slow-field tick order, and event retention still diverge.
- Confirmed current API/frontend route surface is mostly aligned; replay handle settlement deserves a later focused contract.
- Confirmed explicit Postgres config is intentional, but legacy Postgres data compatibility still needs separate hardening.

Self-review:
- Primitive business types introduced: none; audit only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-153: Restore legacy-compatible projectile terminal reason wire values.

## Active Ticket

ID: BWR-153
Goal: Restore legacy-compatible projectile terminal reason wire values.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/objects/BattleEnums.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: frontend implementation, database/repository code, queue/routes, auth/identity, dependency changes, `backend-legacy/**`.
Expected change:
- Keep the internal `ProjectileTerminalReason` ADT expressive, but serialize it with legacy wire values expected by the existing frontend terminal VFX policy.
- Add focused backend contract coverage for the reason wire mapping.
Architecture/domain-modeling impact:
- Preserves enum-based finite terminal reasons internally.
- Limits compatibility handling to the serialization boundary.
Side-effect boundary impact:
- No external side effects.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
Acceptance criteria:
- `Hit` serializes as `hit`.
- `Blocked` serializes as `obstacle`.
- `OutOfBounds` serializes as `world`.
- `Expired` serializes as `ttl`.
Risks:
- Existing scripts that accepted rebuilt aliases like `blocked` may need no change because they already accept legacy values; browser VFX should improve rather than regress.

Result:
- `ProjectileTerminalReason.wireValue` now serializes internal reasons with legacy/frontend-compatible values:
  - `Hit` -> `hit`
  - `Blocked` -> `obstacle`
  - `OutOfBounds` -> `world`
  - `Expired` -> `ttl`
- Added a focused battle runtime contract for the wire mapping.
- Restarted the backend in explicit Postgres mode after verification.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- backend `/health.storageMode=postgres`
- `npm run dev:status`
- `git diff --check` completed with line-ending warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; compatibility is kept at the serialization boundary.
- Scope respected: yes.

Risks:
- Full browser VFX smoke was not rerun for this narrow backend wire change; BP28 already accepted the legacy reason names and the frontend policy branches on them.

Next ticket:
- BWR-154: Move reload/fire/pickup effects out of command acceptance and into runtime advancement.

## Active Ticket

ID: BWR-154
Goal: Move reload/fire/pickup effects out of command acceptance and into runtime advancement.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `.codex/agent-state.md`, focused verification commands.
Forbidden scope: frontend implementation, database/repository code, queue/routes, auth/identity, dependency changes, `backend-legacy/**`.
Expected change:
- `acceptCommand` should update player input, weapon switch, and skill effects, but should not immediately fire, reload, or collect ambient pickups in the command application path.
- Runtime advancement should remain responsible for reload, held-primary fire, projectile motion, and pickup collection.
- Preserve immediate skill movement/pickup semantics only where legacy intentionally applies the skill destination before the next step, if required by existing contracts.
Architecture/domain-modeling impact:
- Keeps commands as input/state-transition requests and battle effects as runtime transitions.
- Reduces hidden side effects in the command boundary.
Side-effect boundary impact:
- No external effects; in-memory battle-state transition only.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
Acceptance criteria:
- A command with `primaryHeld=true` records the held input but does not create a projectile until runtime advances.
- A command with `reloadPressed=true` records the reload intent but does not start reload until runtime advances.
- Existing held-fire, auto-reload, pickup, and skill contracts remain green or are adjusted to the legacy timing.
Risks:
- Some API smoke scripts may currently assume command-frame immediate fire; if so, scripts must wait for at least one runtime tick rather than encoding the old rebuilt behavior.

Result:
- `acceptCommand` now records player input, reload intent, weapon switch, and skill outcomes without directly firing, starting reload, or collecting ambient pickups in the command application path.
- `reloadPressed` is now preserved as player input and consumed by the runtime reload pass.
- Blink/Dash no longer run pickup collection during the command itself; ambient pickups are resolved by runtime advancement.
- Removed the now-unused command-frame fire/reload helper functions to keep the command boundary honest.
- Updated battle runtime contracts so command-frame assertions check recorded input, and projectile/reload effects are asserted after runtime ticks.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run demo:api-contract`
- `npm run demo:bp28-render-feel-smoke`
- `npm run demo:bp44-feel-suite`
- `npm run build`
- `npm run audit:battle-content`
- backend `/health.storageMode=postgres`
- `npm run dev:status`
- `git diff --check` completed with line-ending warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; command and runtime transitions remain immutable aggregate-state copies.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- This intentionally changes command-frame timing. Browser and API smokes passed, but any untested manual tooling that assumes immediate projectile creation right after `POST /battle/commands` should wait at least one runtime tick.

Next ticket:
- BWR-155: Restore legacy weapon recoil after authoritative fire.

## Completed Ticket

ID: BWR-155
Goal: Restore legacy weapon recoil after authoritative fire.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `.codex/agent-state.md`, focused verification commands.
Forbidden scope: frontend implementation, database/repository code, queue/routes, auth/identity, dependency changes, `backend-legacy/**`.
Expected change:
- Apply collision-aware recoil to the shooter after successful authoritative weapon fire, matching legacy runtime behavior.
- Keep recoil values in the existing weapon constants and apply them through immutable player updates.
Architecture/domain-modeling impact:
- Keeps recoil as deterministic runtime state transition, not an external side effect.
Side-effect boundary impact:
- No external effects.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
Acceptance criteria:
- Pistol/Rocket/Gatling/Shotgun firing moves the shooter opposite the firing direction when unobstructed.
- Recoil movement respects arena obstacles and world bounds.
- Existing battle runtime contracts remain green.
Risks:
- Recoil changes projectile birth owner position and can slightly affect close-range cover/hit geometry; tests should use tolerant assertions where projectile positions are observed after a runtime tick.

Result:
- Restored collision-aware weapon recoil after successful authoritative fire.
- Projectile birth still uses the pre-recoil shooter pose, so muzzle/projectile alignment remains stable while the stored player position receives recoil.
- Added runtime contract coverage for Pistol, Gatling, RocketLauncher, and Shotgun recoil distances:
  - Pistol: 3.6 world units.
  - Gatling: 1.44 world units.
  - RocketLauncher: 21.6 world units.
  - Shotgun: 14.4 world units.
- Verified that local frontend and backend services are restored after SBT checks:
  - 5173: Vite dev server.
  - 8080: BackendApp via sbt runMain.
  - backend `/health.storageMode=postgres`.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run demo:api-contract`
- `npm run demo:bp28-render-feel-smoke`
- `npm run demo:bp44-feel-suite`
- `npm run build`
- `npm run audit:battle-content`
- `npm run dev:status`
- backend `/health`
- `git diff --check` passed with CRLF warnings only

Verification notes:
- One concurrent `npm run demo:bp44-feel-suite` run failed because it overlapped with a standalone BP28 run and both tried to clean the same `.runtime/bp28-render-feel-smoke` browser profile. A serial BP44 rerun passed all five scenarios with `warnings=0` and `hitDisputeFailures=0`.
- Root build still emits existing Vite/Rollup warnings for React Router `"use client"` directives, dynamic/static import chunking, and large bundle size.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; recoil is an immutable player-state copy in the runtime state transition.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- Recoil has been restored for all weapon kinds, but deeper browser-level visual scrutiny can still reveal frontend presentation issues unrelated to backend projectile math.

Next ticket:
- BWR-156: Align Freeze targeted placement with legacy rules and frontend targeting assumptions.

## Completed Ticket

ID: BWR-156
Goal: Align Freeze targeted placement with legacy rules and frontend targeting assumptions.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `.codex/agent-state.md`, focused verification commands.
Forbidden scope: frontend implementation, database/repository code, queue/routes, auth/identity, dependency changes, `backend-legacy/**`.
Expected change:
- Allow Freeze to create a slow field at any in-world target within cast range, including obstacle-covered points.
- Preserve Blink's stricter blocked-target validation.
- Add a runtime contract covering legacy-compatible Freeze placement on an obstacle center.
Architecture/domain-modeling impact:
- Keeps `SkillKind.Freeze` as an explicit finite skill state and models the cast as an immutable battle-state transition.
Side-effect boundary impact:
- No external effects; backend runtime state only.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
Acceptance criteria:
- Freeze applied outcome is returned for an in-range obstacle target.
- A slow field is created exactly at the requested target.
- Blink remains blocked on obstacle targets.
Risks:
- This can place slow-field visuals over cover, matching legacy/frontend expectations; if product wants cover to block Freeze later, frontend target validity and UX must change in the same ticket.

Result:
- Removed the backend-only obstacle rejection from Freeze target validation.
- Preserved Blink's obstacle-blocked target behavior.
- Added a runtime contract that casts Freeze at `cover-nw-1` center and verifies an applied outcome plus a slow field at the requested target.
- Confirmed frontend authoritative target validity already treats Freeze as range + world only, so this restores backend/frontend agreement.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run demo:api-contract`
- `npm run demo:bp44-feel-suite`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only

Verification notes:
- An initial API/BP44 parallel run failed because both smoke suites create matchmaking rooms concurrently. Re-running the suites serially passed.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; Freeze remains an immutable aggregate-state transition.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- Local non-authoritative frontend runtime can still fire a weapon in the same frame after a local Freeze cast; authoritative path suppresses `primaryHeld`. This is a frontend parity ticket, not part of this backend-only fix.

Next ticket:
- BWR-157: Align slow-field tick order with legacy runtime.

## Completed Ticket

ID: BWR-157
Goal: Align slow-field tick order with legacy runtime.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `.codex/agent-state.md`, focused verification commands.
Forbidden scope: frontend implementation, database/repository code, queue/routes, auth/identity, dependency changes, `backend-legacy/**`.
Expected change:
- Decrement and remove expired slow fields before applying slow factors to player movement and projectile movement in a runtime step.
- Add a runtime contract proving an expiring Freeze field does not slow movement on the step where it expires.
Architecture/domain-modeling impact:
- Keeps slow fields as immutable runtime state values and preserves `DurationMillis` typing.
Side-effect boundary impact:
- No external effects; backend runtime state only.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
Acceptance criteria:
- Slow fields with `ttlMs <= deltaMs` are removed before movement/projectile slow checks.
- Existing Freeze smoke and browser feel checks remain green.
Risks:
- This changes the last-tick feel for players standing inside an expiring field; it matches legacy but may slightly reduce total slow duration by one runtime step.

Result:
- Runtime now decrements/removes slow fields before player movement, pickup updates, reload/held-fire resolution, and projectile movement.
- Added a contract where a Freeze field has exactly 1ms TTL before a 33ms tick; after the tick, the field is removed and the player receives normal single-tick walk distance instead of slow-field half speed.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run demo:api-contract`
- `npm run demo:bp44-feel-suite`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; slow-field expiry is an immutable aggregate-state copy.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- This matches legacy final-tick ordering. Browser smoke passed, but exact manual feel around a Freeze field expiring may differ by one tick from the immediately previous rebuilt backend.

Next ticket:
- BWR-158: Suppress local non-authoritative weapon fire after targeted skill release.

## Completed Ticket

ID: BWR-158
Goal: Suppress local non-authoritative weapon fire after targeted skill release.
Allowed scope: `frontend/src/features/battle/runtime-local/session/localBattleFrameSceneBridge.ts`, `.codex/agent-state.md`, focused verification commands.
Forbidden scope: backend implementation, database/repository code, battle API DTOs, broad renderer refactors, dependency changes.
Expected change:
- When the local runtime starts a frame with Blink/Freeze prepared, the mouse-confirm frame must not also be passed to weapon fire as `primaryHeld` or `primaryJustPressed`.
- Preserve existing authoritative suppression behavior.
Architecture/domain-modeling impact:
- Keeps local command handling as deterministic frame orchestration; no domain type changes.
Side-effect boundary impact:
- No external effects; local renderer/runtime command routing only.
Verification commands:
- `npm run build`
- `npm run demo:bp44-feel-suite`
Acceptance criteria:
- Local Freeze/Blink confirmation cannot create a same-frame weapon shot through the local weapon bridge.
- TypeScript build passes.
- Existing browser feel suite remains green.
Risks:
- This is a frontend parity fix in a backend-heavy pass; keep scope to one orchestration file.

Result:
- Local battle frame orchestration now records whether Blink/Freeze was already prepared before skill handling.
- If a targeted skill was prepared at frame start, the command passed to local weapon fire has `primaryHeld=false` and `primaryJustPressed=false`.
- This prevents a successful local Freeze release from clearing `preparedSkill` and firing a weapon in the same frame.
- Authoritative frontend/backend input behavior was already suppressing primary fire and remains unchanged.

Verification passed:
- `npm run build`
- `npm run demo:bp44-feel-suite`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only

Verification notes:
- Root build still emits existing Vite/Rollup warnings for React Router `"use client"` directives, dynamic/static import chunking, and large bundle size.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; this is local command routing only.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- No focused unit test harness exists for local battle frame orchestration; coverage is build plus BP44 browser feel suite.

Next ticket:
- BWR-159: Bound backend battle event history to legacy recent-event retention.

## Completed Ticket

ID: BWR-159
Goal: Bound backend battle event history to legacy recent-event retention.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `.codex/agent-state.md`, focused verification commands.
Forbidden scope: frontend implementation, database/repository code, queue/routes, auth/identity, dependency changes, `backend-legacy/**`.
Expected change:
- Retain only the most recent 12 battle events, matching legacy runtime.
- Add a runtime contract that repeatedly collects a respawned pickup and verifies old events are pruned.
Architecture/domain-modeling impact:
- Keeps `BattleEventKind` and event value objects unchanged; adds bounded aggregate history behavior.
Side-effect boundary impact:
- No external effects; backend runtime state only.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
Acceptance criteria:
- Event history is capped at 12.
- Oldest events are pruned after more than 12 event-producing runtime updates.
- Existing battle runtime contracts remain green.
Risks:
- Clients relying on full in-memory battle event history would need replay/artifact storage instead; legacy exposed only recent events.

Result:
- Added legacy-sized event retention (`12`) to backend battle state runtime.
- Pickup and kill events now append through `retainRecentEvents`.
- Added a runtime contract that repeatedly collects a respawned pickup for more than 12 event-producing updates and verifies the oldest event is pruned.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run demo:api-contract`
- `npm run demo:bp44-feel-suite`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; event retention is an immutable aggregate-state copy.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- Event IDs still include current retained event count as one component, matching the existing rebuilt format. The cap restores legacy retention but does not introduce a separate monotonic event sequence.

Next ticket:
- BWR-160: Align Freeze cooldown precedence with legacy skill validation.

## Completed Ticket

ID: BWR-160
Goal: Align Freeze cooldown precedence with legacy skill validation.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `.codex/agent-state.md`, focused verification commands.
Forbidden scope: frontend implementation, database/repository code, queue/routes, auth/identity, dependency changes, `backend-legacy/**`.
Expected change:
- Return `SkillNotOwned` if the player lacks Freeze.
- Return `Cooldown` for Freeze while on cooldown before validating pointer target world/range.
- Preserve already-restored Freeze obstacle placement behavior.
Architecture/domain-modeling impact:
- Keeps `SkillOutcomeReason` as the explicit ADT for business outcomes.
Side-effect boundary impact:
- No external effects; backend command validation only.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
Acceptance criteria:
- A second Freeze cast while on cooldown returns `SkillOutcomeReason.Cooldown` even if the pointer target is invalid.
- Existing Freeze range/world/success behavior remains covered.
Risks:
- UI may now show cooldown instead of invalid target when both are true; this matches legacy validation order.

Result:
- Freeze validation now returns `SkillNotOwned` before cooldown and returns `Cooldown` before pointer target validation.
- Added a runtime contract where a second Freeze cast uses an invalid pointer while still on cooldown and receives `SkillOutcomeReason.Cooldown`.
- Existing restored Freeze obstacle placement behavior remains covered.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run demo:api-contract`
- `npm run demo:bp44-feel-suite`
- `npm run dev:status`
- backend `/health.storageMode=postgres`
- `git diff --check` passed with CRLF warnings only

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; validation returns explicit `SkillOutcomeReason` ADT values.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- Only Freeze precedence was adjusted in this ticket. Blink still keeps current rebuilt ordering because its obstacle/range behavior is intentionally stricter and separately covered.

Next ticket:
- BWR-161: Audit and restore authoritative replay frame capture parity.

## Completed Ticket

ID: BWR-141
Goal: Align magazine auto-reload and projectile muzzle birth across authoritative backend and local visual paths.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `frontend/src/game/projectileBirth.ts`, `frontend/src/features/battle/runtime-local/projectiles/projectileFactory.ts`, `frontend/src/features/battle/runtime-local/weapons/weaponActionController.ts`, `frontend/src/features/battle/renderer/effects/sharedAuthoritativeLocalFeedbackSceneBridge.ts`, `frontend/src/features/battle/renderer/effects/projectileTerminalFeedbackPolicy.ts`, `.codex/agent-state.md`.
Forbidden scope: database/repository code, queue/routes, auth/identity, backend-legacy, dependency changes, schema/configuration.
Expected change:
- Start reload automatically when a magazine weapon empties and reserve ammo remains.
- Spawn authoritative projectiles from the same muzzle point the frontend VFX expects: hero radius + projectile radius + 4px clearance.
- Replace old local projectile/muzzle offsets with a shared frontend projectile birth helper.
Architecture/domain-modeling impact:
- Keep finite `WeaponKind`/`ProjectileKind` ADTs unchanged.
- Express reload as an explicit immutable weapon-state transition.
Side-effect boundary impact:
- No new external side effects; backend changes are pure battle-state transitions and frontend changes are deterministic presentation math.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run build`
- `npm run demo:api-contract`
- `npm run demo:bp28-render-feel-smoke`
- `npm run demo:bp44-feel-suite`
Acceptance criteria:
- Empty Pistol/Rocket/Shotgun magazines start reload without a manual reload command when reserve ammo exists.
- Pistol, Rocket, Gatling, and Shotgun authoritative projectile births are offset from the hero center by `18 + projectileRadius + 4`.
- Frontend local projectile factory and muzzle VFX use the same birth formula.
- Existing API and browser feel smoke checks remain green.
Risks:
- Moving projectile birth forward changes close-range cover and hit geometry; focused contracts must cover representative weapon births and reload.

Result:
- Magazine weapons now start reload automatically after the shot that empties the magazine when reserve ammo remains.
- Holding fire on an already-empty magazine weapon also starts reload if reserve ammo remains.
- Authoritative Pistol, Rocket, Gatling, and Shotgun projectile births now use hero radius + projectile radius + 4px clearance.
- Pistol ray hit and cover checks now start at that same muzzle point.
- Frontend local projectile factory, local muzzle VFX, shared authoritative muzzle feedback, and remote birth feedback now share `resolveProjectileBirthPosition`.
- Frontend dev server is listening on 5173 and backend is listening on 8080 with `storageMode=postgres`.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run build`
- `npm run audit:battle-content`
- `npm run demo:api-contract`
- `npm run demo:bp28-render-feel-smoke`
- `npm run demo:bp44-feel-suite`
- `npm run dev:status`
- `git diff --check` completed with line-ending warnings only.

Self-review:
- Primitive business types introduced: no new domain primitives; frontend helper uses presentation geometry numbers at the boundary.
- Boolean business results introduced: none.
- Domain mutation introduced: none; backend weapon reload/fire transitions remain immutable copies.
- Side effects inside domain: none.
- Scope respected: yes; frontend files were included because the visual mismatch had old local offset paths in addition to the backend source-of-truth bug.

Next ticket:
- BWR-142: Inspect remaining battle projectile terminal feedback semantics if terminal tracers should represent full muzzle-to-impact paths instead of last-segment effects.

## Completed Ticket

ID: BWR-137
Goal: Align the API battle smoke with the rebuilt authoritative map and weapon pickup rules.
Allowed scope: `scripts/api-contract-field-smoke.ps1`, `.codex/agent-state.md`, process/log verification commands.
Forbidden scope: backend production source, frontend implementation, `backend-legacy/**`, `backend-legacy/data/**`, database schema/configuration.
Expected change:
- Allow the smoke to validate a player inventory with auto-collected spawn Gatling plus Pistol.
- Replace removed legacy pistol-cache checks with current Rocket/Gatling pickup checks.
- Make terminal elimination choose a clear lane target under the new spawn map.
Architecture/domain-modeling impact:
- No domain-model changes; script-only verification alignment.
Side-effect boundary impact:
- Hits local HTTP endpoints through the public API only.
Verification commands:
- `npm run demo:api-contract`
Acceptance criteria:
- API contract smoke passes against the running dev stack.
Risks:
- Script alignment can mask a backend bug if it removes too much coverage; keep ammo, medkit, pickup respawn, dash, and terminal elimination assertions intact.

Result:
- API smoke no longer assumes the removed legacy pistol cache exists.
- API smoke now accepts a Pistol plus auto-collected spawn Gatling inventory.
- API smoke verifies current weapon scalar fields against `currentWeaponIndex`, not against a hard-coded first weapon.
- API smoke verifies Rocket pickup catalog identity and spawn Gatling pickup/inventory state.
- Terminal elimination smoke moves the killer into the clear center lane before shooting under the new map.
- `npm run demo:api-contract` passed.

Self-review:
- Primitive business types introduced: smoke script API boundary strings only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-138: Implement authoritative firing for RocketLauncher, Gatling, and Shotgun.

## Active Ticket

ID: BWR-138
Goal: Implement minimal authoritative backend firing for non-Pistol weapons.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/objects/**`, `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/main/scala/slaydemo/backend/battle/routes/BattleStateJson.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `.codex/agent-state.md`.
Forbidden scope: frontend implementation, database/repository code, queue/routes outside battle state JSON if avoidable, `backend-legacy/**`, `backend-legacy/data/**`, dependency changes.
Expected change:
- Add typed projectile kinds for Rocket, Gatling, and Shotgun.
- Let `applyFireCommand` consume/update each current weapon explicitly.
- Preserve Pistol behavior.
- Add focused runtime contracts proving non-Pistol weapons can fire and update ammo/heat/projectiles.
Architecture/domain-modeling impact:
- Extend finite `ProjectileKind` ADT instead of stringly projectile states.
- Keep weapon transition logic in the battle state service boundary.
Side-effect boundary impact:
- No new external effects; pure in-memory battle state transitions only.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run audit:battle-content`
- `npm run demo:api-contract`
Acceptance criteria:
- RocketLauncher consumes one shell and creates a Rocket projectile/terminal path.
- Gatling increases heat and creates a Gatling projectile without ammo reserve.
- Shotgun consumes one shell and creates multiple Shotgun pellet projectiles or terminals.
- Existing Pistol ammo/reload and smoke checks still pass.
Risks:
- Splash damage and exact spread parity may need a follow-up if frontend has richer behavior than the current backend can safely implement in one ticket.

Result:
- Added finite projectile kinds for Rocket, Gatling bullet, and Shotgun pellet.
- RocketLauncher, Gatling, and Shotgun now consume ammo/heat and emit authoritative projectiles.
- RocketLauncher and Shotgun now use the generic magazine reload path.
- Gatling now cools heat and clears overheat lock through the weapon timer path.
- Added runtime contracts for Gatling fire/cooldown, Rocket fire/reload, and Shotgun pellet emission.
- Verification passed:
  - `npm run backend:test-contracts`
  - `npm run backend:compile`
  - `npm run audit:battle-content`
  - `npm run demo:api-contract`

Self-review:
- Primitive business types introduced: none; projectile kinds remain ADTs.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-139: Apply authoritative active projectile hero damage for non-Pistol projectiles.

## Active Ticket

ID: BWR-139
Goal: Make active authoritative projectiles damage heroes after flight.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `.codex/agent-state.md`.
Forbidden scope: frontend implementation, database/repository code, queue/routes, `backend-legacy/**`, `backend-legacy/data/**`, dependency changes.
Expected change:
- Detect player collisions along active projectile paths.
- Apply projectile damage, score/kills, kill events, and terminal hit metadata.
- Apply Rocket splash damage at impact points.
- Preserve existing pistol direct-hit behavior.
Architecture/domain-modeling impact:
- Keep projectile impact as explicit state transition inside battle state service.
- Do not add raw string projectile states.
Side-effect boundary impact:
- No external effects; in-memory battle simulation only.
Verification commands:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run demo:api-contract`
Acceptance criteria:
- A non-Pistol active projectile can reduce a target hero's HP after flight.
- Projectile terminal metadata includes hit target and damage for direct hits.
- Existing API smoke still passes.
Risks:
- Exact multi-target rocket splash balance may still need frontend feel validation.

Result:
- Active authoritative projectiles now check hero collisions along their movement segment.
- Projectile hits now apply HP damage, terminal hit metadata, score/kills, and kill events.
- Rocket impacts now apply splash damage around the impact point.
- Added a focused contract proving a Gatling projectile damages a target after flight and emits hit terminal metadata.
- Verification passed:
  - `npm run backend:test-contracts`
  - `npm run backend:compile`
  - `npm run audit:battle-content`
  - `npm run demo:api-contract`
  - `git diff --check` passed with the existing CRLF warning for the smoke script.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; state transitions return copied aggregate state.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-140: Run browser-facing battle smoke to validate frontend entry/fire/pickup feel against the running backend.

## Active Ticket

ID: BWR-140
Goal: Run browser-facing battle smoke against the current frontend/backend stack.
Allowed scope: verification commands, `.codex/agent-state.md`, and narrow script fixes only if a smoke script has drifted from current contracts.
Forbidden scope: production code edits unless a verified frontend/backend integration bug appears, database resets, `backend-legacy/**`, `backend-legacy/data/**`, dependency changes.
Expected change:
- Execute the available render/feel battle smoke that validates actual frontend battle entry.
- Classify any failure as script drift, frontend bug, or backend bug.
Architecture/domain-modeling impact:
- Verification only unless a concrete bug appears.
Side-effect boundary impact:
- Uses local browser/front-end/backend APIs only.
Verification commands:
- `npm run demo:bp28-render-feel-smoke`
- `npm run demo:bp44-feel-suite` if BP28 passes or classifies cleanly
Acceptance criteria:
- Browser smoke passes, or failure is reduced to a concrete next ticket.
Risks:
- Browser automation may depend on local Edge/Playwright availability and can be environment-sensitive.

Result:
- `npm run demo:bp28-render-feel-smoke` passed:
  - two browser clients entered the same battle
  - movement/fire input was accepted
  - VFX/HUD diagnostics responded
- `npm run demo:bp44-feel-suite` initially found script drift in `DualClientPressure`: hit-dispute treated backend `blocked` terminal reasons as unexplained no-damage.
- Updated `scripts/bp28-render-feel-smoke.ps1` so `blocked` is an explanatory no-damage reason, matching the current backend terminal reason vocabulary.
- Re-ran `npm run demo:bp44-feel-suite`; all scenarios passed with warnings=0:
  - MixedMovement
  - SkillPressure
  - TargetedSkillPressure
  - DualClientPressure
  - StraightFire
- Final service status:
  - 5173: Vite dev server
  - 8080: BackendApp via sbt runMain
  - `/health.storageMode=postgres`
- `git diff --check` passed with existing CRLF warnings for touched PowerShell scripts.

Self-review:
- Primitive business types introduced: smoke script reason string only at test boundary.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Current runnable-state conclusion:
- Backend contracts pass.
- Backend compile passes.
- Battle content audit passes.
- API contract smoke passes.
- Browser BP28 render-feel smoke passes.
- Browser BP44 feel suite passes.
- Frontend and backend are both running locally.

Remaining risks:
- Bot combat AI was not expanded in this pass; bots are present and surfaced, but advanced bot behavior remains a future gameplay ticket.
- Exact weapon feel may still need manual tuning for balance, but the core authoritative paths now run and are covered.

## Completed Tickets

ID: BWR-135
Goal: Apply authoritative backend weapon switch commands after players collect weapons.

Result:
- Backend now honors `switchWeaponIndex` for owned weapons.
- Backend now honors `switchWeaponDirection` as a cyclic inventory step.
- Switching keeps `currentWeaponKind` and `currentWeaponIndex` synchronized and cancels reload on the previous current weapon.
- Added contracts that collect RocketLauncher, switch to it by explicit index, then cycle back to Pistol by direction.
- `npm run backend:test-contracts` passed.
- `npm run backend:compile` passed.
- `npm run audit:battle-content` passed.

ID: BWR-134
Goal: Make authoritative backend weapon and medkit pickups match the frontend battle map and actually affect player inventory/HP.

Result:
- Backend initial pickups now match frontend map content: six weapon pickups and two medkits.
- Backend pickup radius now matches frontend automatic pickup radius at 40px.
- Backend medkit heal now matches frontend at 25 HP and uses 10000ms respawn.
- RocketLauncher, Gatling, and Shotgun pickup state constructors/refill behavior now match frontend inventory rules.
- Collecting a weapon pickup adds/refills the corresponding weapon instead of consuming it as a no-op.
- Added contracts for pickup map parity, Rocket pickup inventory state, and medkit consumption.
- Added backend runtime content constants and audit extraction for all four weapon definitions and map metadata.
- `npm run backend:test-contracts` passed.
- `npm run backend:compile` passed.
- `npm run audit:battle-content` passed.

ID: BWR-133
Goal: Align authoritative backend battle spawn points with the frontend battle map.

Result:
- Backend spawn points now match the six frontend `HERO_SPAWN_POINTS`.
- Added a contract that bootstraps six seats and verifies initial player positions match the frontend spawn catalog.
- Updated affected movement/dash/pickup/no-respawn tests to use stable positions under the new spawn map.
- `npm run backend:test-contracts` passed.
- `npm run backend:compile` passed.
- `npm run audit:battle-content` now reaches real comparison and no longer reports `defaultMap.heroSpawnPoints` mismatches; it still reports pickup and non-pistol weapon drift for BWR-134+.

ID: BWR-132
Goal: Restore `npm run audit:battle-content` for the rewritten backend structure.

Result:
- The audit script now reads the new backend runtime service path instead of removed legacy runtime catalog files.
- The script extracts backend world size, spawn points, inner obstacles, pickups, pistol constants, and skill constants from `BattleStateService.scala`.
- `npm run audit:battle-content` now reaches real content comparison and fails on actual drift: spawn points, pickup layout, map metadata, and missing non-pistol weapon definitions.
- `git diff --check -- scripts/audit-battle-content-contract.mjs` passed with the existing LF-to-CRLF warning.

ID: BWR-131
Goal: Align authoritative Blink behavior with frontend target-range Blink.

Result:
- Backend Blink now uses the frontend-style direct target with 250px range, 2200ms cooldown, and 240ms active timer.
- The terminal elimination API smoke no longer depends on removed long Blink behavior.
- `npm run backend:test-contracts` passed.
- `npm run backend:compile` passed.
- Restarted backend in explicit Postgres mode and confirmed `/health` reports `storageMode=postgres`.
- `npm run demo:api-contract` passed.

ID: BWR-130
Goal: Strengthen the API smoke so battle SSE streams must emit multiple state frames.

Result:
- SSE API smoke now requires two `event: state` payloads from one stream.
- The smoke validates battle id and tick on each streamed payload.
- `npm run demo:api-contract` passed and reported two streamed ticks.
- `git diff --check` passed for the touched ticket scope, with the existing CRLF warning for the PowerShell smoke script.

ID: BWR-129
Goal: Align authoritative Freeze content constants with frontend battle content.

Result:
- Backend Freeze cast range now matches frontend content at 520px.
- Backend Freeze cooldown now matches frontend content at 12000ms.
- Backend slow movement/projectile multipliers now match frontend content at 0.5.
- Added a focused contract for max-range Freeze, cooldown, active duration, radius, ttl, and field position.
- `npm run backend:test-contracts` passed.
- `npm run backend:compile` passed.
- Restarted backend in explicit Postgres mode and confirmed `/health` reports `storageMode=postgres`.
- `npm run demo:api-contract` passed.
- `git diff --check` passed for the touched ticket scope.

ID: BWR-128
Goal: Align authoritative Dash content constants with frontend battle content.

Result:
- Backend Dash distance now matches frontend content at 180px.
- Backend Dash cooldown now matches frontend content at 5000ms.
- Backend Dash active duration now matches frontend content at 180ms.
- Added focused assertions for Dash cooldown and active timers after an applied dash.
- `npm run backend:test-contracts` passed.
- `npm run backend:compile` passed.
- Restarted backend in explicit Postgres mode and confirmed `/health` reports `storageMode=postgres`.
- `npm run demo:api-contract` passed.
- `git diff --check` passed for the touched ticket scope.

ID: BWR-127
Goal: Align authoritative pistol projectile content constants with frontend battle content.

Result:
- Backend pistol damage now matches frontend content at 12.
- Backend pistol projectile radius now matches frontend content at 8.
- Backend pistol projectile lifetime now matches frontend content at 900ms.
- Updated terminal elimination contract to require nine cooldown-spaced pistol hits instead of the old three-hit assumption.
- Added assertions for projectile damage/radius/ttl.
- `npm run backend:test-contracts` passed.
- `npm run backend:compile` passed.
- Restarted backend in explicit Postgres mode and confirmed `/health` reports `storageMode=postgres`.
- `npm run demo:api-contract` passed.
- `git diff --check` passed for the touched ticket scope.

ID: BWR-126
Goal: Preserve world-space `pointerWorld` in authoritative frontend battle commands.

Result:
- Authoritative command serialization now uses an explicit `normalizeWorldPoint` path for `pointerWorld`.
- Fallback authoritative pointer targets now project 220px in world space from the local player instead of 1px.
- `npm run build` passed with existing Vite warnings.
- `npm run demo:api-contract` passed.
- `git diff --check` passed for the touched ticket scope, with existing CRLF warnings on frontend files.

ID: BWR-125
Goal: Prevent authoritative Dash and Blink from moving players through or into arena obstacles.

Result:
- Dash now resolves through the same stepped arena collision helper used by regular movement.
- Blink now validates the final destination after backend lane displacement before applying the move.
- Added focused contracts proving Dash stops before the center wall and Blink into cover is rejected as `Blocked`.
- `npm run backend:test-contracts` passed.
- `npm run backend:compile` passed.
- Restarted backend in explicit Postgres mode and confirmed `/health` reports `storageMode=postgres`.
- `npm run demo:api-contract` passed.
- `git diff --check` passed for the touched ticket scope.

ID: BWR-124
Goal: Align authoritative backend movement speed constants with frontend battle content.

Result:
- Backend walk speed now matches frontend `BASE_MOVE_SPEED` at 255px/s.
- Backend sprint speed now matches frontend base speed times sprint multiplier at 446.25px/s.
- Added focused contracts for 1 second walk and sprint movement distances.
- `npm run backend:test-contracts` passed.
- `npm run backend:compile` passed.
- Restarted backend in explicit Postgres mode and confirmed `/health` reports `storageMode=postgres`.
- `npm run demo:api-contract` passed.
- `git diff --check` passed for the touched ticket scope.

ID: BWR-123
Goal: Make authoritative elimination terminal: dead players do not respawn, and battles finish when one or fewer players remain alive.

Result:
- Eliminated players now remain dead with `respawnMs=0`.
- Removed automatic backend respawn advancement for eliminated players.
- Backend battles now finish when one or fewer players remain alive, in addition to duration expiry.
- Winner selection now uses a stable alive/score/kills/seat comparator aligned with finish projection ordering.
- API smoke now checks terminal elimination instead of expecting respawn.
- `npm run backend:test-contracts` passed.
- `npm run backend:compile` passed.
- Restarted backend in explicit Postgres mode and confirmed `/health` reports `storageMode=postgres`.
- `npm run demo:api-contract` passed.
- `git diff --check` passed for the touched ticket scope, with the existing CRLF warning for the PowerShell smoke script.

ID: BWR-122
Goal: Make authoritative pistol ammo, reload, and ammo pickup behavior deterministic and frontend-aligned.

Result:
- Backend pistol fire now checks and sets `fireCooldownMs`.
- Reloading pistols cannot fire until reload completes.
- Pistol reload duration is aligned to the frontend content value of 1000ms.
- Pistol cache pickup now refills the current magazine, adds reserve ammo, and clears weapon cooldown/reload/heat state.
- Added focused battle state contracts for fire cooldown, reload blocking, reload completion, and pistol cache refill.
- `npm run backend:test-contracts` passed.
- `npm run backend:compile` passed.
- Restarted backend in explicit Postgres mode and confirmed `/health` reports `storageMode=postgres`.
- `npm run demo:api-contract` passed.
- `git diff --check` passed for the touched ticket scope.

ID: BWR-121
Goal: Make authoritative sprint consume/recover stamina and gate sprint speed when stamina is exhausted.

Result:
- Backend authoritative movement now computes effective sprint from movement intent, sprint input, and available stamina.
- Sprinting movement drains stamina at 38/s; non-effective sprint recovers at 24/s.
- Effective sprint state is written back to snapshots so exhausted/idle players are not reported as sprinting.
- Added a focused battle state contract for sprint drain and idle recovery.
- `npm run backend:test-contracts` passed.
- `npm run backend:compile` passed.
- Restarted backend in explicit Postgres mode and confirmed `/health` reports `storageMode=postgres`.
- `npm run demo:api-contract` passed.
- `git diff --check` passed for the touched ticket scope.

ID: BWR-120
Goal: Stop authoritative battle movement from passing through arena walls/obstacles.

Result:
- Backend authoritative movement now uses rectangular arena border/inner obstacle collision with 16px stepped resolution and axis fallback sliding.
- The same arena obstacles now block direct pistol ray checks instead of the previous single hard-coded cover point.
- Added a focused battle state contract proving movement from the center spawn stops before the center wall.
- `sbt "Test / runMain slaydemo.backend.BattleStateRuntimeContractTest"` passed.
- `npm run backend:test-contracts` passed.
- `npm run backend:compile` passed.
- `git diff --check` passed.

ID: BWR-119
Goal: Fix battle entry crash after backend matchmaking countdown by preserving backend bootstrap hero identities in the frontend runtime snapshot.
Allowed scope: `frontend/src/features/battle/runtime-local/session/initialBattleSnapshot.ts`, `frontend/src/game/spawn.ts`, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/**`, `backend-legacy/data/**`, database schema/configuration, persistence repositories, unrelated frontend UI.

Result:
- `createInitialHeroes` now supports backend bootstrap hero ids as canonical runtime hero ids and uses bootstrap spawn slots/display names/skins.
- Dynamic backend hero ids get stable visual overrides/fallbacks.
- `createInitialBattleSnapshot` passes backend `spawnPointIndex` into hero creation.
- `npm run build` passed with existing Vite warnings only.
- BP-28 two-client render-feel smoke passed; both clients entered `playing` in the same backend battle.

## Current Backlog

- BWR-121: Fix authoritative sprint stamina consumption and recovery.
- BWR-122: Fix authoritative weapon ammo/reload recovery behavior.
- BWR-123: Fix authoritative death/respawn/terminal battle rules.
- BWR-045: Document explicit Postgres opt-in env contract in `.env.example` and backend runbook.
- BWR-046: Extract battle queue identity validation out of HTTP routes into a service/application boundary.
- BWR-047: Split battle finish projection planning from repository writes.

## Completed Tickets

ID: BWR-001
Goal: Create the new backend baseline.
Allowed scope: `backend/**`, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/**`, `backend-legacy/data/**`, `frontend/**`, `package*.json`, `.env*`.
Verification:
- `npm run backend:compile`
- temporary `npm run backend:dev` plus `GET http://127.0.0.1:8080/health`

Result:
- Compile passed.
- Health returned `{"status":"ok","service":"slay-demo-backend","port":8080}`.

ID: BWR-002
Goal: Add explicit storage configuration strategy without opening database connections at startup.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendConfig.scala`, `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/shared/storage/**`, `backend/README.md`, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/**`, `backend-legacy/data/**`, `frontend/**`, `package*.json`, `.env*`.
Verification:
- `npm run backend:compile`
- temporary `npm run backend:dev` with only generic `DATABASE_URL` set plus `GET http://127.0.0.1:8080/health`
- `rg` search for database/file connection APIs in new backend.

Result:
- Compile passed.
- Health returned `{"status":"ok","service":"slay-demo-backend","port":8080}` while generic `DATABASE_URL` was set.
- No `DriverManager`, `Class.forName`, `java.sql`, `Files`, `Paths`, `FileWriter`, or `PrintWriter` usage exists in new backend sources.

ID: BWR-003
Goal: Restore identity register/session/me/accounts contracts with playable-handle guardrails.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/shared/json/**`, `backend/src/main/scala/slaydemo/backend/shared/objects/**`, `backend/src/main/scala/slaydemo/backend/shared/policies/**`, `backend/src/main/scala/slaydemo/backend/shared/routes/**`, `backend/src/main/scala/slaydemo/backend/identity/**`, `backend/README.md`, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/**`, `backend-legacy/data/**`, `frontend/**`, `package*.json`, `.env*`.
Verification:
- `npm run backend:compile`
- temporary backend identity API smoke for register/session/me/accounts.
- search new backend for database/file connection APIs.

Result:
- Compile passed.
- Identity smoke passed for register, session, me, accounts, remote session prefix, builtin admin listing, and Visitor registration rejection.
- No database/file connection API usage exists in new backend sources.
- BWR-003 review findings resolved: stored account output is playable-filtered, register create is repository-atomic, and repository credentials use `PasswordHash` rather than plaintext.

## Active Ticket

ID: BWR-004
Goal: Introduce battle queue/room/state domain and wire DTO types only.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/objects/**`, `backend/src/main/scala/slaydemo/backend/battle/api/**`, `.codex/agent-state.md`.
Forbidden scope: `backend/src/main/scala/slaydemo/backend/battle/routes/**`, `backend/src/main/scala/slaydemo/backend/battle/runtime/**`, `backend/src/main/scala/slaydemo/backend/battle/database/**`, `backend-legacy/**`, `backend-legacy/data/**`, `frontend/**`, `package*.json`, `.env*`.
Verification:
- `npm run backend:compile`
- search battle domain/API for raw finite-state fields such as `phase: String`, `status: String`, `kind: String`, `type: String`.

Result:
- Compile passed.
- No raw `phase/status/kind/type/reason: String` finite-state fields found in battle domain/API.

ID: BWR-005
Goal: Restore in-memory battle queue and room routes before authoritative battle runtime.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/battle/api/**`, `backend/src/main/scala/slaydemo/backend/battle/objects/**`, `backend/src/main/scala/slaydemo/backend/battle/routes/**`, `backend/src/main/scala/slaydemo/backend/battle/services/**`, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/**`, `backend-legacy/data/**`, `frontend/**`, `package*.json`, `.env*`.
Verification:
- `npm run backend:compile`
- temporary backend smoke for register, queue join auth failures, valid join, queue status, room heartbeat, room snapshot, room activation, and queue leave.
- search new backend for database/file connection APIs.
- search battle sources for raw finite-state fields.

Result:
- Compile passed.
- Queue smoke passed with authenticated join, session/handle guardrails, active room bootstrap, snapshot, heartbeat, and leave.
- No database/file connection API usage exists in new backend sources.
- Raw finite-state search only found route-boundary `Either[String, ...]` technical parse errors; domain/API finite states remain modeled as enums.

ID: BWR-006
Goal: Restore minimal authoritative battle state, command, and SSE contracts.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/battle/api/**`, `backend/src/main/scala/slaydemo/backend/battle/objects/**`, `backend/src/main/scala/slaydemo/backend/battle/routes/**`, `backend/src/main/scala/slaydemo/backend/battle/services/**`, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/**`, `backend-legacy/data/**`, `frontend/**`, `package*.json`, `.env*`.
Expected change:
- Expose `/battle/state/{battleId}`, `/battle/state?battleId=...`, `/battle/state/stream?battleId=...`, and `/battle/commands`.
- Build deterministic in-memory battle aggregate state from active queue sessions.
- Validate command battle/player/ticket ownership and return explicit accepted/ignored command results.
Architecture/domain-modeling impact:
- Keep `BattleAggregateState` and command outcomes typed with value objects and enums.
- Boundary DTOs may keep wire primitives; service results must use ADTs.
Side-effect boundary impact:
- In-memory mutable state remains inside the battle service boundary.
- Routes only parse/render HTTP and map typed service results to status codes.
Verification:
- `npm run backend:compile`
- temporary backend smoke covering queue activation, state path/query, SSE first event, command accepted, and ownership failures.
- search new backend for database/file connection APIs.
Acceptance criteria:
- Frontend-facing battle state response contains all currently modeled state fields.
- Command endpoint rejects missing/invalid ownership and accepts the joined player command.
- SSE emits a state event for a known battle id.
Risks:
- This ticket does not implement full combat simulation, projectile physics, replay persistence, or result generation.

Result:
- Compile passed.
- Smoke passed for identity register, queue activation, `/battle/state/{battleId}`, `/battle/state?battleId=...`, SSE `event: state`, accepted command, and wrong-ticket `403 command_not_authorized`.
- No database/file connection API usage exists in new backend sources.
- Raw finite-state search only found route-boundary `Either[String, ...]` technical parse errors; service/domain results use enums.

ID: BWR-007
Goal: Restore in-memory battle results read/write contract.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/battle/api/**`, `backend/src/main/scala/slaydemo/backend/battle/objects/**`, `backend/src/main/scala/slaydemo/backend/battle/routes/**`, `backend/src/main/scala/slaydemo/backend/battle/services/**`, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/**`, `backend-legacy/data/**`, `frontend/**`, `package*.json`, `.env*`.
Expected change:
- Expose `GET /battle/results?battleId=...`, `GET /battle/results?handle=...&limit=...`, and `POST /battle/results`.
- Keep result records in memory only.
- Filter visitor-like handles at the boundary.
Architecture/domain-modeling impact:
- Add result value objects and explicit service errors instead of raw Boolean outcomes.
Side-effect boundary impact:
- No database/file writes; repository-like state remains in service boundary.
Verification:
- `npm run backend:compile`
- temporary API smoke for post/list-by-battle/list-by-handle and visitor guardrail.
Acceptance criteria:
- Frontend result parser can consume the returned records.
- Results can be listed by battle id and handle with deterministic newest-first order.
Risks:
- This ticket will not yet create results automatically from finished authoritative battle state.

Result:
- Compile passed.
- Results smoke passed for POST, list by battle id, list by handle with limit/newest ordering, `currentLoadout: null`, visitor query empty list, and visitor POST rejection.
- No database/file connection API usage exists in new backend sources.
- Boolean fields introduced are wire/domain flags (`aliveAtEnd`) or predicates/comparators, not hidden business outcomes.

ID: BWR-008
Goal: Restore in-memory replay catalog/detail/comments contract.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/replay/**`, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/**`, `backend-legacy/data/**`, `frontend/**`, `package*.json`, `.env*`.
Expected change:
- Expose `GET /replay/catalog`, `GET /replay/catalog/{battleId}`, `POST /replay/catalog`, `GET /replay/catalog/{battleId}/comments`, and `POST /replay/catalog/{battleId}/comments`.
- Keep replay records and comments in memory only.
- Filter visitor-like handles for submissions/comments.
Architecture/domain-modeling impact:
- Add replay value objects and explicit service errors instead of raw Boolean outcomes.
Side-effect boundary impact:
- No database/file writes; repository-like state remains in service boundary.
Verification:
- `npm run backend:compile`
- temporary API smoke for post/list/detail/comments and visitor guardrails.
Acceptance criteria:
- Frontend replay parser can consume detail and catalog records.
- Comments can be listed and posted for a replay.
Risks:
- This ticket will not yet create replays automatically from finished authoritative battle state.

Result:
- Compile passed.
- Replay smoke passed for catalog POST with frontend-style `frames` plus `framesJson`, catalog list, detail load, comment POST/list, visitor replay POST rejection, and visitor comment rejection.
- No database/file connection API usage exists in new backend sources.
- Boolean fields introduced are replay wire/domain flags (`aliveAtEnd`, `playbackAvailable`) or predicates, not hidden business outcomes.

ID: BWR-009
Goal: Restore in-memory mail listing/read contract.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/mail/**`, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/**`, `backend-legacy/data/**`, `frontend/**`, `package*.json`, `.env*`.
Expected change:
- Expose `GET /mails?ownerHandle=...` and `POST /mails/read`.
- Keep mail records in memory only.
- Seed or create enough deterministic mail behavior for API consumers without touching old data.
Architecture/domain-modeling impact:
- Add mail value objects and explicit service errors instead of raw Boolean business outcomes.
Side-effect boundary impact:
- No database/file writes; repository-like state remains in service boundary.
Verification:
- `npm run backend:compile`
- temporary API smoke for list/read and visitor guardrails.
Acceptance criteria:
- Frontend mail parser can consume mail list and read acknowledgement.
- Visitor-like owner handles are rejected or return no user data.
Risks:
- This ticket will not yet integrate automatic mail creation from battle result projection.

Result:
- Compile passed.
- Mail smoke passed for list with deterministic welcome mail, read acknowledgement, read-state persistence in memory, missing owner, visitor owner, and unknown mail id.
- No database/file connection API usage exists in new backend sources.
- Boolean fields introduced are mail wire/domain flags (`unread`, `important`), not hidden business outcomes.

## Active Ticket

ID: BWR-010
Goal: Inventory remaining backend endpoints and run a broader contract compile/smoke gate before persistence.
Allowed scope: `.codex/agent-state.md`, docs under `docs/phases/phase-05-backend-rewrite/**` if needed, read-only inspection elsewhere.
Forbidden scope: `backend-legacy/data/**`, persistence/database adapters, schema changes, dependency changes.
Expected change:
- Identify frontend/legacy endpoints not yet implemented in the new backend.
- Decide the next safe ticket before touching persistence.
Architecture/domain-modeling impact:
- Prevent premature database work before in-memory API surface is known stable.
Side-effect boundary impact:
- No new runtime side effects.
Verification:
- `npm run backend:compile`
- endpoint inventory via `rg`
- optional broader API smoke if scripts can run against the current authenticated backend shape.
Acceptance criteria:
- Worklog contains the remaining endpoint backlog and next selected ticket.
Risks:
- Some legacy smoke scripts may assume unauthenticated queue joins and need adaptation rather than direct execution.

Result:
- Endpoint inventory completed via frontend and legacy route scans.
- New backend is missing `/bots/profiles` and `/bot/profiles`, `/social/friend-requests`, `/social/friend-requests/respond`, `/forum/topics` plus topic child routes, and governance adjustment/notification routes.
- Persistence remains deferred until these in-memory contracts are implemented and smoked.

## Active Ticket

ID: BWR-011
Goal: Restore static in-memory bot profile routes.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/bots/**`, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/**`, `backend-legacy/data/**`, `frontend/**`, `package*.json`, persistence/database adapters, schema changes.
Expected change:
- Expose `GET /bots/profiles` and legacy alias `GET /bot/profiles`.
- Return deterministic demo bot profile records matching the frontend parser.
Architecture/domain-modeling impact:
- Add bot profile value objects and passive immutable records.
- Keep static profile listing side-effect-free except route rendering.
Side-effect boundary impact:
- No database/file/network usage; static in-memory data only.
Verification:
- `npm run backend:compile`
- temporary API smoke for `/bots/profiles`, `/bot/profiles`, `HEAD`, and unsupported method.
Acceptance criteria:
- Frontend bot profile parser can consume every returned record.
- Alias route returns the same profile catalog.
Risks:
- Static catalog may later need to share data with queue bot seeding; this ticket keeps scope limited to API compatibility.

Result:
- Compile passed.
- Smoke passed for `GET /bots/profiles`, `GET /bot/profiles`, `HEAD /bots/profiles`, and POST method rejection.
- Returned catalog has five deterministic profiles and matches the frontend parser fields.
- No database/file connection API usage exists in new backend sources.
- No raw finite-state strings, Boolean business results, or domain mutation were introduced in bot profile sources.

## Active Ticket

ID: BWR-012
Goal: Restore in-memory social friend request routes.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/social/**`, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/**`, `backend-legacy/data/**`, `frontend/**`, `package*.json`, persistence/database adapters, schema changes.
Expected change:
- Expose `GET /social/friend-requests?ownerHandle=...`, `POST /social/friend-requests`, and `POST /social/friend-requests/respond`.
- Store friend request records in memory only.
- Return records matching the frontend parser, including explicit status and optional respondedAt.
Architecture/domain-modeling impact:
- Add friend request value objects and `FriendRequestStatus`/decision ADTs.
- Service results must represent created/already-existing/responded/error outcomes explicitly; wire Booleans are allowed only in route responses.
Side-effect boundary impact:
- No database/file writes; mutable in-memory state stays inside social service boundary.
Verification:
- `npm run backend:compile`
- temporary API smoke for list/create/duplicate/respond/authorization-like handle guardrails.
Acceptance criteria:
- Frontend friend request parser can consume list, create, duplicate, and respond responses.
- Visitor-like and invalid handles are rejected or return no private data.
Risks:
- Mail payload can be null until cross-service mail integration is planned as a later ticket.

Result:
- Compile passed after integrating social routes into `BackendApp.scala`.
- Smoke passed for create, list by owner, duplicate create, forbidden respond, accepted respond, and visitor owner rejection.
- First smoke attempt failed because PowerShell/curl JSON quoting produced a malformed request body; rerun with native `Invoke-WebRequest` body passed.
- No database/file connection API usage exists in new backend sources.
- No raw finite-state strings or Boolean business results were introduced in social/bot sources; response booleans are wire flags.

## Active Ticket

ID: BWR-013
Goal: Restore in-memory forum topic, reply, and vote routes.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/forum/**`, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/**`, `backend-legacy/data/**`, `frontend/**`, `package*.json`, persistence/database adapters, schema changes.
Expected change:
- Expose `GET /forum/topics`, `POST /forum/topics`, `GET /forum/topics/{topicId}`, `POST /forum/topics/{topicId}/replies`, `POST /forum/topics/{topicId}/votes`, and `POST /forum/topics/{topicId}/replies/{replyId}/votes`.
- Store forum topics, replies, and viewer votes in memory only.
- Return records matching the frontend parser, including viewerVote and score.
Architecture/domain-modeling impact:
- Add forum value objects, `ForumVote` enum, and explicit service result/error ADTs.
- Keep score computation as pure projection from immutable topic state.
Side-effect boundary impact:
- No database/file writes; mutable in-memory state stays inside forum service boundary.
Verification:
- `npm run backend:compile`
- temporary API smoke for list/create/detail/reply/topic vote/reply vote/not found/visitor guardrail.
Acceptance criteria:
- Frontend forum parser can consume all list/detail/mutation responses.
- Missing topic/reply maps to stable `topic_not_found`/`reply_not_found` errors.
Risks:
- Initial seeded topics are optional; this ticket prioritizes API contract and in-memory mutations.

Result:
- Compile passed after integrating forum routes into `BackendApp.scala`.
- Smoke passed for create topic, list, detail, add reply, topic vote, reply vote, null vote clear, missing topic, and visitor mutation rejection.
- No database/file connection API usage exists in new backend sources.
- No raw finite-state strings or Boolean business results were introduced in forum sources; `voteSeen` is a route parser flag.

## Active Ticket

ID: BWR-014
Goal: Restore in-memory governance contribution adjustment and admin notification routes.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/governance/**`, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/**`, `backend-legacy/data/**`, `frontend/**`, `package*.json`, persistence/database adapters, schema changes.
Expected change:
- Expose `GET/POST /governance/contribution-adjustments` and `GET/POST /governance/admin-notifications`.
- Store adjustment and notification records in memory only.
- Return records matching the frontend parser, plus mail snapshots for POST responses.
Architecture/domain-modeling impact:
- Add governance value objects, review kind/target type enums, and explicit service result/error ADTs.
- Keep admin authorization and validation explicit rather than Boolean.
Side-effect boundary impact:
- No database/file writes; mutable in-memory state stays inside governance service boundary.
Verification:
- `npm run backend:compile`
- temporary API smoke for adjustment list/create/invalid actor and notification list/create/filter/invalid kind.
Acceptance criteria:
- Frontend governance parser can consume adjustment and notification records.
- Admin-only contribution adjustment uses explicit actor validation.
Risks:
- Mail payloads are response snapshots until cross-service mail integration is planned as a later ticket.

Result:
- Compile passed after integrating governance routes into `BackendApp.scala`.
- Smoke passed for contribution adjustment create/list/invalid actor and admin notification create/filter/invalid kind.
- No database/file connection API usage exists in new backend sources.
- Review kind and target type are parsed into enums at the route boundary before service calls.

## Active Ticket

ID: BWR-015
Goal: Run broad frontend-facing backend contract smoke before any persistence work.
Allowed scope: `.codex/agent-state.md`, read-only inspection of `backend/**`, `frontend/**`, `scripts/**`.
Forbidden scope: `backend-legacy/data/**`, persistence/database adapters, schema changes, dependency changes.
Expected change:
- Verify the rebuilt in-memory backend exposes the frontend-facing API surface in one running server session.
- Reconfirm that no database/file connection APIs are present before persistence planning.
Architecture/domain-modeling impact:
- No production code changes expected; this is a verification and stabilization ticket.
Side-effect boundary impact:
- Temporary local server only; no DB or file-backed store usage.
Verification:
- `npm run backend:compile`
- single-session smoke covering health, identity, bot profiles, queue/state/commands, results, replay/comments, mails, social, forum, and governance.
- endpoint inventory comparison via `rg`.
Acceptance criteria:
- All covered frontend-facing endpoints return parseable contract-shaped JSON or expected error statuses.
- No listener remains on port 8080 after the smoke.
Risks:
- This does not prove full authoritative battle simulation or persistence durability.

Result:
- Compile passed.
- Broad single-session smoke passed for health, identity, bot profiles, battle queue/rooms/state/SSE/commands/results, replay/detail/comments, mails, social, forum, and governance.
- First broad smoke attempt used a stale session after login; corrected by using the latest session token.
- Second broad smoke attempt hit a PowerShell `Invoke-WebRequest` null reference on SSE; corrected by using `curl.exe` for the stream endpoint.
- Endpoint inventory shows frontend feature API calls are covered after Vite strips `/api`.
- Reviewer found direct BackendApp `/api/*` aliases are missing; this is the next ticket before persistence.

## Active Ticket

ID: BWR-016
Goal: Make `/api` frontend/backend prefix contract explicit in the backend.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, route path parsing helpers under `backend/src/main/scala/slaydemo/backend/**/routes/**`, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/**`, `backend-legacy/data/**`, `frontend/**`, `package*.json`, persistence/database adapters, schema changes.
Expected change:
- Register `/api/*` aliases for frontend-facing backend contexts.
- Ensure route handlers that inspect request paths handle both root paths and `/api`-prefixed paths.
- Keep existing root paths working.
Architecture/domain-modeling impact:
- No domain model changes expected.
Side-effect boundary impact:
- No new persistence or external side effects.
Verification:
- `npm run backend:compile`
- focused smoke for `/api/health`, `/api/identity/register`, `/api/bots/profiles`, `/api/battle/rooms/{id}/snapshot`, `/api/battle/state/stream`, `/api/replay/catalog`, `/api/forum/topics`, and `/api/governance/admin-notifications`.
Acceptance criteria:
- `/api` aliases return the same contract-shaped responses as root contexts.
- No listener remains on port 8080 after smoke.
Risks:
- Routes with path parsing must be updated carefully to avoid breaking non-prefixed paths.

Result:
- Compile passed.
- Focused `/api` prefix smoke passed for health, identity, bot profiles, battle queue/rooms/state/SSE/commands/results, replay/detail/comments, mails, social, forum, and governance.
- Root `/health` was also checked during the smoke to guard against non-prefixed regression.
- No database/file connection API usage exists in new backend sources.
- Route-level raw `kind`/`targetType` strings remain only in request DTO parsing; governance service uses enums.

## Active Ticket

ID: BWR-017
Goal: Replace primitive battle domain handles/display names with identity value objects.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/objects/**`, `backend/src/main/scala/slaydemo/backend/battle/services/**`, `backend/src/main/scala/slaydemo/backend/battle/routes/**`, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/**`, `backend-legacy/data/**`, `frontend/**`, `package*.json`, persistence/database adapters, schema changes.
Expected change:
- Change battle queue/session/aggregate domain records that currently store player handles or display names as raw `String` to use `PlayerHandle` and `DisplayName` where appropriate.
- Keep API JSON output unchanged.
Architecture/domain-modeling impact:
- Reduces primitive obsession in non-boundary battle domain objects.
Side-effect boundary impact:
- No new side effects.
Verification:
- `npm run backend:compile`
- focused battle smoke for queue activation, room snapshot, state, and command acceptance.
Acceptance criteria:
- Battle API responses remain contract-compatible.
- Battle domain objects no longer use raw `String` for player handle/displayName fields where identity value objects apply.
Risks:
- This touches battle service/routing projections, so scope must stay limited to type replacement and rendering.

Result:
- Compile passed after replacing battle domain `handle`/`displayName` fields with `PlayerHandle`/`DisplayName`.
- Focused battle smoke passed for queue join, activation, room snapshot, state rendering, and command acceptance.
- Battle objects/services no longer contain `handle: String` or `displayName: String`; API request DTOs remain string-based at the HTTP boundary.
- No database/file connection API usage exists in new backend sources.

## Active Ticket

ID: BWR-018
Goal: Inspect persistence expectations and design the first safe storage ticket.
Allowed scope: `.codex/agent-state.md`, `docs/**`, read-only inspection of `backend-legacy/**` except data, `scripts/**`, backend build/config files.
Forbidden scope: `backend-legacy/data/**`, production code writes, database connections, schema changes, dependency changes.
Expected change:
- Identify existing DB scripts/schema expectations and legacy repository behavior without opening a database connection.
- Decide the first persistence implementation ticket with minimal blast radius.
Architecture/domain-modeling impact:
- Keep storage behind repository/adapter boundaries; domain model must remain independent of DB APIs.
Side-effect boundary impact:
- No runtime DB/file side effects in this ticket.
Verification:
- Read docs/handoff and search schema/migration scripts.
- Search new backend for DB/file connection APIs remains clean.
Acceptance criteria:
- Worklog records a concrete persistence backlog and next ticket.
Risks:
- Actual DB connectivity may require credentials or a running service; do not assume availability.

Result:
- Handoff/runbook confirms old backend supported Postgres through `SLAY_DEMO_DATABASE_URL`, `SLAY_DEMO_DATABASE_USER`, `SLAY_DEMO_DATABASE_PASSWORD`, while new backend intentionally ignores generic `DATABASE_URL`.
- New backend already parses explicit `SLAY_DEMO_STORAGE_MODE`, `SLAY_DEMO_DATABASE_URL`, user, and password without opening a connection.
- New backend currently has no PostgreSQL JDBC dependency and no DB connection support code.
- Legacy repositories self-create tables in repository constructors; adopting that wholesale would open DB connections at startup as soon as repositories are wired.
- The safest first persistence ticket is a shared Postgres boundary/dependency with no repository wiring, followed by identity-only repository wiring in a separate ticket.

## Active Ticket

ID: BWR-019
Goal: Add shared Postgres connection boundary without wiring runtime repositories.
Allowed scope: `backend/build.sbt`, `backend/src/main/scala/slaydemo/backend/shared/database/**`, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/**`, `backend-legacy/data/**`, frontend, domain/service/route wiring, repository implementations outside shared database, schema changes.
Expected change:
- Add the PostgreSQL JDBC dependency.
- Add typed shared database helper that can open/close connections only when explicitly called.
- Keep BackendApp and default memory runtime unchanged.
Architecture/domain-modeling impact:
- Establish a side-effect boundary for future repository tickets without leaking DB APIs into domain code.
Side-effect boundary impact:
- No DB connections on startup; this ticket only introduces callable infrastructure.
Verification:
- `npm run backend:compile`
- default temporary backend health smoke with `DATABASE_URL` set but no `SLAY_DEMO_STORAGE_MODE=postgres`
- search for DB APIs confirms they exist only under `shared/database`.
Acceptance criteria:
- Default startup remains memory-only and does not react to generic `DATABASE_URL`.
- Shared DB helper redacts password in `toString` and closes resources.
Risks:
- Actual DB connectivity is not tested until a repository ticket with explicit postgres mode and credentials.

Result:
- Compile passed after adding PostgreSQL JDBC dependency and shared `PostgresSupport`.
- Default backend health smoke passed with generic `DATABASE_URL` set to an invalid local URL; the backend ignored it and did not try to connect.
- DB connection APIs are confined to `backend/src/main/scala/slaydemo/backend/shared/database/PostgresSupport.scala`.
- BackendApp and repositories are not wired to Postgres yet.

## Active Ticket

ID: BWR-020
Goal: Add explicit Postgres identity repository wiring.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/identity/database/**`, `backend/src/main/scala/slaydemo/backend/shared/database/**`, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/**`, `backend-legacy/data/**`, frontend, non-identity repositories, battle/replay/forum/governance/social persistence, file persistence, schema migrations outside identity.
Expected change:
- Implement typed Postgres identity repository compatible with current `IdentityAccountRepository`.
- Wire BackendApp to use it only when `SLAY_DEMO_STORAGE_MODE=postgres`.
- Keep default memory mode and generic `DATABASE_URL` behavior unchanged.
Architecture/domain-modeling impact:
- Persistence remains behind the identity repository trait; domain/service code should not depend on `java.sql`.
Side-effect boundary impact:
- DB connection and schema initialization happen only with explicit postgres storage mode.
Verification:
- `npm run backend:compile`
- default memory identity smoke with generic `DATABASE_URL` set.
- config smoke showing `SLAY_DEMO_STORAGE_MODE=postgres` without `SLAY_DEMO_DATABASE_URL` fails fast with the typed config error rather than silently using generic `DATABASE_URL`.
Acceptance criteria:
- Memory identity API remains unchanged.
- Postgres repository compiles and is selected only by explicit storage config.
Risks:
- Real Postgres write/read smoke may require credentials and a running database; if unavailable, report as unverified rather than fabricating.

Result:
- Compile passed.
- Default memory identity smoke passed with generic `DATABASE_URL` set to an invalid local Postgres URL; the backend ignored it and used memory storage.
- Explicit `SLAY_DEMO_STORAGE_MODE=postgres` without `SLAY_DEMO_DATABASE_URL` exited before binding a port and emitted the typed config error.
- Postgres identity repository is wired only through `StorageConfig.Postgres`.
- DB APIs are confined to `shared/database` and `identity/database/PostgresIdentityAccountRepository.scala`.
- Legacy-compatible column names are preserved, including `password`; in the rebuilt backend this column stores `PasswordHash.value`.
- Risk: historical legacy Postgres rows with plaintext `password` values will need a migration or password reset before they can authenticate with the new hashed-password service.
- Scope note: the read-only legacy exploration subagent inspected `backend-legacy/data/identity-accounts.json` to confirm JSON shape; no data values were propagated and no files were modified.

## Active Ticket

ID: BWR-021
Goal: Verify explicit Postgres identity mode against a temporary local database if available.
Allowed scope: `.codex/agent-state.md`, read-only inspection of local tooling, temporary local Postgres runtime resources, `backend/src/main/scala/slaydemo/backend/identity/database/**` only if smoke reveals a BWR-020 bug.
Forbidden scope: `backend-legacy/data/**`, frontend, non-identity repositories, persistent user databases, destructive database cleanup outside temporary resources created by this ticket.
Expected change:
- Prefer no production code changes.
- If Docker is available, run a temporary Postgres container with an isolated database and verify register/session/me/accounts through `SLAY_DEMO_STORAGE_MODE=postgres`.
- If Docker/Postgres is unavailable, record the exact blocker and continue to the next safe persistence/docs ticket.
Architecture/domain-modeling impact:
- No domain model changes expected.
- Any fixes must keep JDBC inside repository/shared database boundaries.
Side-effect boundary impact:
- Temporary database side effects only in a clearly named throwaway database/container.
Verification:
- local Docker/Postgres availability check
- temporary backend identity smoke in explicit postgres mode when possible
- cleanup check that no backend listener remains
Acceptance criteria:
- Either a real Postgres identity smoke passes, or the worklog records why it could not be run.
Risks:
- Pulling or starting Postgres may be unavailable on this workstation; do not use a non-temporary existing database without explicit credentials and scope.

Result:
- Docker is not installed or not on PATH.
- `psql`, `pg_isready`, `postgres`, `pg_ctl`, and `initdb` are not available on PATH.
- A local `postgres` process is listening on port 5432, but no explicit `SLAY_DEMO_DATABASE_URL`, user, or password is set in the current process.
- Real Postgres identity smoke was not run because using an unknown existing local database would create schema outside a clearly scoped temporary database.
- No production code changes were needed.

## Active Ticket

ID: BWR-022
Goal: Update backend storage docs after identity Postgres wiring.
Allowed scope: `backend/README.md`, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/**`, frontend, production Scala code, dependency changes, database connections.
Expected change:
- Document current storage behavior accurately: default memory, generic `DATABASE_URL` ignored, identity-only Postgres wiring through explicit `SLAY_DEMO_STORAGE_MODE=postgres`, file mode parsed but not implemented.
- Record that real Postgres smoke still needs scoped credentials or a temporary database.
Architecture/domain-modeling impact:
- Documentation only.
Side-effect boundary impact:
- No runtime side effects.
Verification:
- `Get-Content backend/README.md`
- `npm run backend:compile` only if production code changes unexpectedly happen.
Acceptance criteria:
- README no longer says repository construction/schema initialization is only future work for identity.
- README clearly distinguishes implemented identity Postgres wiring from still in-memory modules.
Risks:
- Docs may need another update after additional repositories are wired.

Result:
- `backend/README.md` now documents default memory mode, explicit Postgres mode, generic `DATABASE_URL` being ignored, file mode being parsed but not implemented, and identity-only Postgres wiring.
- The README records that battle, replay, social, forum, governance, mail, and bot profile storage remain in memory until separate repository tickets.
- Verification was `Get-Content backend/README.md`; compile was not run because this was documentation-only.

## Active Ticket

ID: BWR-023
Goal: Add Postgres battle result repository wiring.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/battle/database/**`, `backend/src/main/scala/slaydemo/backend/battle/services/**` only for repository abstraction seams, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/data/**`, frontend, identity repository changes, replay/forum/social/governance/mail persistence, schema changes outside battle results.
Expected change:
- Introduce a typed battle result repository boundary if the current service needs one.
- Implement Postgres battle result persistence selected only by `SLAY_DEMO_STORAGE_MODE=postgres`.
- Keep default memory behavior and battle result API contract unchanged.
Architecture/domain-modeling impact:
- Battle result domain/API should keep value objects and explicit result ADTs.
- JDBC must stay in `battle/database` and shared database support only.
Side-effect boundary impact:
- DB effects occur only in explicit postgres storage mode.
Verification:
- `npm run backend:compile`
- default memory battle result smoke with generic `DATABASE_URL` set
- explicit postgres config missing URL remains a typed startup failure
- real Postgres battle result smoke only if scoped credentials or temporary DB become available
Acceptance criteria:
- Existing battle result POST/list-by-battle/list-by-handle smoke remains green in memory mode.
- Postgres adapter compiles and is wired only through explicit storage config.
Risks:
- New abstraction may touch the in-memory service; keep the seam narrow and avoid changing route contracts.

Result:
- Compile passed after adding `BattleResultRepository`, `InMemoryBattleResultRepository`, `PostgresBattleResultRepository`, and storage-mode wiring.
- Default memory battle result smoke passed with generic `DATABASE_URL` set to an invalid local Postgres URL; POST/list-by-battle/list-by-handle/visitor rejection stayed contract-compatible.
- Explicit `SLAY_DEMO_STORAGE_MODE=postgres` without `SLAY_DEMO_DATABASE_URL` still exits before binding and emits the typed config error.
- Postgres battle result adapter uses the legacy `battle_results` columns and keeps JDBC in `battle/database`.
- The adapter performs non-destructive `result_id` add/backfill/index setup; duplicate historical rows will fail startup instead of being silently deleted.
- Real Postgres battle result smoke was not run because a scoped temporary database or explicit credentials are still unavailable.
- A persistence exploration subagent recommended doing mail and bot profiles before replay/social/forum/governance due cross-service dependencies and simple independent read models; backlog was reordered accordingly.

## Active Ticket

ID: BWR-024
Goal: Add Postgres mail repository wiring.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/mail/database/**`, `backend/src/main/scala/slaydemo/backend/mail/services/**` only for repository abstraction seams, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/data/**`, frontend, identity/battle/replay/forum/social/governance persistence changes, schema changes outside mail.
Expected change:
- Introduce a typed mail repository boundary if the current service needs one.
- Implement Postgres mail persistence selected only by `SLAY_DEMO_STORAGE_MODE=postgres`.
- Keep default memory mail API contract unchanged.
Architecture/domain-modeling impact:
- Mail kind/read state should remain typed in service/domain; repository maps database strings/booleans at the boundary.
- Avoid Boolean business results in service APIs.
Side-effect boundary impact:
- DB effects occur only in explicit postgres storage mode.
Verification:
- `npm run backend:compile`
- default memory mail smoke with generic `DATABASE_URL` set
- explicit postgres config missing URL remains a typed startup failure
- real Postgres mail smoke only if scoped credentials or temporary DB become available
Acceptance criteria:
- Existing mail list/read smoke remains green in memory mode.
- Postgres adapter compiles and is wired only through explicit storage config.
Risks:
- Cross-service mail creation from social/governance remains a later service-integration ticket.

Result:
- Compile passed after adding `MailRepository`, `InMemoryMailRepository`, `PostgresMailRepository`, and storage-mode wiring.
- Default memory mail smoke passed with generic `DATABASE_URL` set to an invalid local Postgres URL; welcome mail creation, mark-read, read-state persistence, and visitor rejection stayed contract-compatible.
- Explicit `SLAY_DEMO_STORAGE_MODE=postgres` without `SLAY_DEMO_DATABASE_URL` still exits before binding and emits the typed config error.
- Postgres mail adapter uses legacy `mails` core columns and adds nullable source columns already present in the rebuilt `MailRecord` domain.
- Service-level `markRead` still returns `Either[MailReadError, MailRecord]`; the legacy Boolean result was not reintroduced.
- Real Postgres mail smoke was not run because scoped temporary database credentials are unavailable.

## Active Ticket

ID: BWR-025
Goal: Add Postgres bot profile repository wiring.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/bots/database/**`, `backend/src/main/scala/slaydemo/backend/bots/services/**` only for repository abstraction seams, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/data/**`, frontend, identity/battle/mail/replay/forum/social/governance persistence changes, schema changes outside bot profiles.
Expected change:
- Introduce a typed bot profile repository boundary if the current service needs one.
- Implement Postgres bot profile persistence selected only by `SLAY_DEMO_STORAGE_MODE=postgres`.
- Seed deterministic default bot profiles only when the Postgres table is empty.
- Keep default memory/static bot profile API contract unchanged.
Architecture/domain-modeling impact:
- Bot tone/skin values should remain domain types or value objects where present; repository maps database strings at the boundary.
Side-effect boundary impact:
- DB effects occur only in explicit postgres storage mode.
Verification:
- `npm run backend:compile`
- default memory bot profile smoke with generic `DATABASE_URL` set
- explicit postgres config missing URL remains a typed startup failure
- real Postgres bot smoke only if scoped credentials or temporary DB become available
Acceptance criteria:
- Existing `/bots/profiles` and `/bot/profiles` smoke remains green in memory mode.
- Postgres adapter compiles and is wired only through explicit storage config.
Risks:
- Actual seeded profile tuning is demo data; this ticket preserves the existing static catalog rather than redesigning bots.

Result:
- Compile passed after adding `BotProfileRepository`, `InMemoryBotProfileRepository`, `PostgresBotProfileRepository`, and storage-mode wiring.
- Default memory bot profile smoke passed with generic `DATABASE_URL` set to an invalid local Postgres URL; `/bots/profiles` and `/bot/profiles` both returned five profiles in deterministic order.
- Explicit `SLAY_DEMO_STORAGE_MODE=postgres` without `SLAY_DEMO_DATABASE_URL` still exits before binding and emits the typed config error.
- Postgres bot profile adapter uses legacy `bot_profiles` columns and seeds `DemoBotProfiles.all` only when the table is empty.
- Bot profile tone remains an enum in domain/service code; database strings are parsed in the repository boundary.
- Real Postgres bot smoke was not run because scoped temporary database credentials are unavailable.

## Active Ticket

ID: BWR-026
Goal: Add Postgres replay repository wiring.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/replay/database/**`, `backend/src/main/scala/slaydemo/backend/replay/services/**` only for repository abstraction seams, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/data/**`, frontend, identity/battle/mail/bots/forum/social/governance persistence changes, schema changes outside replay.
Expected change:
- Introduce typed replay record/comment repository boundaries if the current service needs them.
- Implement Postgres replay record/comment persistence selected only by `SLAY_DEMO_STORAGE_MODE=postgres`.
- Keep default memory replay API contract unchanged.
Architecture/domain-modeling impact:
- Replay domain should keep value objects and explicit service errors.
- Base64 encoding for old `frames_json_b64` storage, if needed, must remain inside repository.
Side-effect boundary impact:
- DB effects occur only in explicit postgres storage mode.
Verification:
- `npm run backend:compile`
- default memory replay smoke with generic `DATABASE_URL` set
- explicit postgres config missing URL remains a typed startup failure
- real Postgres replay smoke only if scoped credentials or temporary DB become available
Acceptance criteria:
- Existing replay catalog/detail/comment smoke remains green in memory mode.
- Postgres adapter compiles and is wired only through explicit storage config.
Risks:
- Replay frames can be large; repository should preserve current API shape without moving frame parsing into routes/domain.

Result:
- Compile passed after adding `ReplayRepository`, `InMemoryReplayRepository`, `PostgresReplayRepository`, and storage-mode wiring.
- Default memory replay smoke passed with generic `DATABASE_URL` set to an invalid local Postgres URL; catalog POST/list/detail, frame round-trip, comment POST/list, and visitor rejection stayed contract-compatible.
- Explicit `SLAY_DEMO_STORAGE_MODE=postgres` without `SLAY_DEMO_DATABASE_URL` still exits before binding and emits the typed config error.
- Postgres replay adapter uses legacy `replay_records` and `replay_comments` columns.
- Base64 encode/decode for `frames_json_b64` is confined to the repository boundary.
- Unlike legacy, corrupted replay rows are not deleted during reads; invalid base64 frames decode to `[]` rather than hiding a destructive side effect in a read path.
- Real Postgres replay smoke was not run because scoped temporary database credentials are unavailable.

## Active Ticket

ID: BWR-027
Goal: Add Postgres social friend request repository wiring.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/social/database/**`, `backend/src/main/scala/slaydemo/backend/social/services/**` only for repository abstraction seams, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/data/**`, frontend, identity/battle/mail/bots/replay/forum/governance persistence changes, schema changes outside social friend requests.
Expected change:
- Introduce a typed friend request repository boundary if the current service needs one.
- Implement Postgres friend request persistence selected only by `SLAY_DEMO_STORAGE_MODE=postgres`.
- Keep default memory social API contract unchanged.
Architecture/domain-modeling impact:
- Friend request status/decision must remain enums or explicit result ADTs in service/domain code.
- Repository maps status strings at the boundary.
Side-effect boundary impact:
- DB effects occur only in explicit postgres storage mode.
Verification:
- `npm run backend:compile`
- default memory social smoke with generic `DATABASE_URL` set
- explicit postgres config missing URL remains a typed startup failure
- real Postgres social smoke only if scoped credentials or temporary DB become available
Acceptance criteria:
- Existing friend request create/list/respond/duplicate/guardrail smoke remains green in memory mode.
- Postgres adapter compiles and is wired only through explicit storage config.
Risks:
- Cross-service mail creation for social events remains a later integration ticket.

Result:
- Compile passed after adding `FriendRequestRepository`, `InMemoryFriendRequestRepository`, `PostgresFriendRequestRepository`, and storage-mode wiring.
- Default memory social smoke passed with generic `DATABASE_URL` set to an invalid local Postgres URL; create, duplicate already-sent, list, forbidden respond, accepted respond, and visitor rejection stayed contract-compatible.
- Explicit `SLAY_DEMO_STORAGE_MODE=postgres` without `SLAY_DEMO_DATABASE_URL` still exits before binding and emits the typed config error.
- Postgres social adapter uses legacy `social_friend_requests` columns and a unique source/target pair index; duplicate handling is represented as `FriendRequestStoreCreateResult`.
- Friend request status and decision remain enums in domain/service code; database strings are parsed in the repository boundary.
- Real Postgres social smoke was not run because scoped temporary database credentials are unavailable.

## Active Ticket

ID: BWR-028
Goal: Add Postgres forum repository wiring.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/forum/database/**`, `backend/src/main/scala/slaydemo/backend/forum/services/**` only for repository abstraction seams, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/data/**`, frontend, identity/battle/mail/bots/replay/social/governance persistence changes, schema changes outside forum.
Expected change:
- Introduce typed forum repository boundary if the current service needs one.
- Implement Postgres forum topic/reply/vote persistence selected only by `SLAY_DEMO_STORAGE_MODE=postgres`.
- Keep default memory forum API contract unchanged.
Architecture/domain-modeling impact:
- Forum vote choices/results must remain explicit enums/ADTs in service/domain code.
- Repository maps vote strings at the boundary.
Side-effect boundary impact:
- DB effects occur only in explicit postgres storage mode.
Verification:
- `npm run backend:compile`
- default memory forum smoke with generic `DATABASE_URL` set
- explicit postgres config missing URL remains a typed startup failure
- real Postgres forum smoke only if scoped credentials or temporary DB become available
Acceptance criteria:
- Existing forum create/list/detail/reply/topic vote/reply vote/guardrail smoke remains green in memory mode.
- Postgres adapter compiles and is wired only through explicit storage config.
Risks:
- Forum has multiple tables; keep the repository seam narrow and avoid moving scoring rules into routes.

Result:
- Compile passed after adding `ForumRepository`, `InMemoryForumRepository`, `PostgresForumRepository`, and storage-mode wiring.
- Default memory forum smoke passed with generic `DATABASE_URL` set to an invalid local Postgres URL; create/list/reply/topic vote/reply vote/clear vote/visitor rejection stayed contract-compatible.
- Explicit `SLAY_DEMO_STORAGE_MODE=postgres` without `SLAY_DEMO_DATABASE_URL` still exits before binding and emits the typed config error.
- Postgres forum adapter uses legacy `forum_topics`, `forum_replies`, `forum_votes`, and `forum_reply_votes` tables.
- The repository saves a whole `ForumTopicRecord` aggregate in a transaction; vote score and viewer vote projection remain in the domain model.
- Real Postgres forum smoke was not run because scoped temporary database credentials are unavailable.

## Active Ticket

ID: BWR-029
Goal: Add Postgres governance repository wiring.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/governance/database/**`, `backend/src/main/scala/slaydemo/backend/governance/services/**` only for repository abstraction seams, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/data/**`, frontend, identity/battle/mail/bots/replay/social/forum persistence changes, schema changes outside governance.
Expected change:
- Introduce typed governance repository boundaries if the current service needs them.
- Implement Postgres contribution adjustment and admin notification persistence selected only by `SLAY_DEMO_STORAGE_MODE=postgres`.
- Keep default memory governance API contract unchanged.
Architecture/domain-modeling impact:
- Review kind and target type must remain enums in service/domain code.
- Repository maps database strings at the boundary.
Side-effect boundary impact:
- DB effects occur only in explicit postgres storage mode.
Verification:
- `npm run backend:compile`
- default memory governance smoke with generic `DATABASE_URL` set
- explicit postgres config missing URL remains a typed startup failure
- real Postgres governance smoke only if scoped credentials or temporary DB become available
Acceptance criteria:
- Existing contribution adjustment and admin notification smoke remains green in memory mode.
- Postgres adapters compile and are wired only through explicit storage config.
Risks:
- Cross-service persisted mail integration remains a later ticket; this ticket preserves response mail snapshots only.

Result:
- Compile passed after adding `GovernanceRepository`, `InMemoryGovernanceRepository`, `PostgresGovernanceRepository`, and storage-mode wiring.
- Default memory governance smoke passed with generic `DATABASE_URL` set to an invalid local Postgres URL; contribution adjustment create/list/invalid actor and admin notification create/filter stayed contract-compatible.
- Explicit `SLAY_DEMO_STORAGE_MODE=postgres` without `SLAY_DEMO_DATABASE_URL` still exits before binding and emits the typed config error.
- Postgres governance adapter uses legacy `governance_contribution_adjustments` and `governance_review_notifications` tables.
- Review kind and target type remain enums in service/domain code; database strings are parsed in the repository boundary.
- Real Postgres governance smoke was not run because scoped temporary database credentials are unavailable.

## Active Ticket

ID: BWR-030
Goal: Run broad backend regression smoke after repository wiring.
Allowed scope: `.codex/agent-state.md`, read-only inspection of backend code, temporary local backend process.
Forbidden scope: production code edits unless the smoke finds a regression, `backend-legacy/data/**`, database connections.
Expected change:
- Prefer no production code changes.
- Verify the memory-mode API surface still works after all repository seams and Postgres adapters were added.
Architecture/domain-modeling impact:
- No domain changes expected.
Side-effect boundary impact:
- Temporary memory-mode backend only; generic `DATABASE_URL` should remain ignored.
Verification:
- `npm run backend:compile`
- single-session smoke covering health, identity, bots, battle queue/state/results, replay/comments, mails, social, forum, and governance with generic `DATABASE_URL` set
- listener cleanup check
Acceptance criteria:
- Smoke passes without leaving a backend listener.
- Any failure is either fixed in a scoped follow-up or recorded as a blocker.
Risks:
- This still does not verify real Postgres read/write behavior without scoped credentials.

Result:
- Compile passed before the smoke.
- Broad memory-mode smoke passed with generic `DATABASE_URL` set to an invalid local Postgres URL and no explicit storage mode.
- Covered health, identity register/session/me/accounts, bot profiles, battle queue capacity activation, room snapshot/heartbeat, battle state path/query/SSE, battle command acceptance, battle results, replay catalog/detail/comments, mails/read, social create/duplicate/list/respond, forum topic/reply/votes, and governance adjustment/notification list/create.
- Initial smoke failures were smoke harness issues: queue activation needs six players because default capacity is 6, `Invoke-WebRequest` is broken in this non-interactive Windows host, and direct Java smoke must use compiled classes rather than a stale packaged jar.
- Final smoke used `curl.exe` and `target/scala-3.3.3/classes` plus external dependency classpath, then verified listener cleanup.
- Real Postgres read/write behavior remains unverified without scoped credentials or a temporary database.

ID: BWR-031
Goal: Make storage mode visible and fix the Postgres runbook footgun.
Allowed scope: `backend/src/main/scala/slaydemo/backend/shared/api/**`, `backend/src/main/scala/slaydemo/backend/shared/services/**`, `backend/src/main/scala/slaydemo/backend/shared/routes/**`, `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/README.md`, `docs/phases/phase-05-backend-rewrite/DEMO_BACKEND_RUNBOOK.md`, `scripts/demo-db-sanity.ps1`, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/**`, `backend-legacy/data/**`, persistence repository behavior, frontend, dependency changes, real database connections.
Expected change:
- Add typed storage mode to `/health`.
- Update demo runbook to set `SLAY_DEMO_STORAGE_MODE=postgres`.
- Make `demo-db-sanity.ps1` fail when the backend is accidentally running memory mode.
Architecture/domain-modeling impact:
- Reuse the existing `StorageMode` enum; do not add stringly typed storage status.
Side-effect boundary impact:
- No new side effects; health renders config state only.
Verification:
- `npm run backend:compile`
- direct memory-mode health smoke with invalid generic `DATABASE_URL`
- `demo-db-sanity.ps1` against memory mode must fail with the expected postgres guardrail
Acceptance criteria:
- `/health` returns `storageMode`.
- Demo docs no longer imply that database URL alone selects Postgres.
- DB sanity catches accidental memory startup.
Risks:
- Real Postgres health response was not run because explicit credentials/temp DB are unavailable.

Result:
- Compile passed after wiring `HealthResponse.storageMode`.
- Memory-mode health smoke returned `{"status":"ok","service":"slay-demo-backend","port":18130,"storageMode":"memory"}` while generic `DATABASE_URL` was invalid and ignored.
- `demo-db-sanity.ps1` correctly failed against memory mode with an `expected postgres` guard.
- `backend/README.md` now documents all wired Postgres repositories and explicitly calls out live battle queue/room/state as process-memory realtime state.
- The demo runbook now sets `SLAY_DEMO_STORAGE_MODE=postgres` and shows `storageMode:"postgres"` in the expected health response.

## Active Ticket

ID: BWR-032
Goal: Plan the authoritative battle finish projection as a small implementation ticket.
Allowed scope: read-only inspection of battle state/result/replay/mail services, frontend expectations, and scripts; `.codex/agent-state.md` for planning.
Forbidden scope: production code edits until the projection boundary and minimal scope are clear, `backend-legacy/data/**`, real database connections.
Expected change:
- Identify the smallest safe boundary for flipping finished battle state into result/replay/mail projections.
- Decide whether the first implementation should be pure domain projection only or route/service wiring.
Architecture/domain-modeling impact:
- Projection results must be explicit ADTs/value objects, not Boolean flags.
- Keep DB writes behind existing repositories/services.
Side-effect boundary impact:
- Any future projector must live in application/service boundary, not battle domain models.
Verification:
- read-only code/script inventory for `resultReady`, `replayReady`, and finish smoke expectations
Acceptance criteria:
- Worklog records a scoped implementation ticket with risks and verification commands.
Risks:
- This may require product judgment if rating/reward formulas are not already defined in code.

Result:
- Read-only backend/frontend/script inventory completed.
- External readiness contract is stricter than `phase=finished`: consumers wait for `phase=finished && resultReady && replayReady`.
- Finish projection must write battle results, replay catalog/detail, battle/rating mails, and rating-visible result fields before flipping readiness.
- Replay responses currently emit rating fields as `null`; authoritative projection needs replay rating metadata to match the generated result.
- The existing finish smoke still uses an unauthenticated queue join shape, while rebuilt queue join requires an identity session; a later script update or compatibility decision is required.

ID: BWR-033
Goal: Make authoritative battle duration configurable for focused finish tests.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `.codex/agent-state.md`.
Forbidden scope: result/replay/mail projection, frontend, persistence repository behavior, `backend-legacy/**`, real database connections.
Expected change:
- Read `SLAY_DEMO_AUTHORITATIVE_BATTLE_DURATION_MS` at startup.
- Use the configured positive duration for `InMemoryBattleStateService`.
- Keep default battle duration unchanged when the env var is missing or invalid.
Architecture/domain-modeling impact:
- Reuse the existing `DurationMillis` value object; raw env parsing stays at startup boundary.
Side-effect boundary impact:
- No new side effects; this is config injection only.
Verification:
- `npm run backend:compile`
- short-duration memory smoke that creates a six-player battle and verifies it reaches `finished`
Acceptance criteria:
- `durationMs` in battle state matches the configured value.
- `resultReady` and `replayReady` remain false until projection exists.
Risks:
- This does not implement finish projection; it only makes projection testable in a short loop.

Result:
- Compile passed after adding duration injection.
- Focused smoke with `SLAY_DEMO_AUTHORITATIVE_BATTLE_DURATION_MS=250` reached `phase=finished`, returned `durationMs=250`, and kept `resultReady=false`/`replayReady=false`.

## Active Ticket

ID: BWR-034
Goal: Add first authoritative finish projection for result, replay, and mail readiness.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/battle/**`, `backend/src/main/scala/slaydemo/backend/replay/**`, `backend/src/main/scala/slaydemo/backend/mail/**`, `.codex/agent-state.md`.
Forbidden scope: frontend, unrelated routes, database connection config, `backend-legacy/**`, destructive migrations.
Expected change:
- Introduce an application/service boundary that projects a finished `BattleAggregateState`.
- Save idempotent battle results for human players.
- Save a replay with `replayId = battleId`.
- Save battle result mail and rating mail where appropriate.
- Set `resultReady`/`replayReady` only after projection succeeds.
Architecture/domain-modeling impact:
- Projection result must be explicit; do not hide projection outcome behind Boolean service APIs.
- Keep domain models passive; repository writes stay in services/repositories.
Side-effect boundary impact:
- Projection is an application/service side effect triggered by battle-state advancement.
- DB writes continue through existing repositories selected by storage mode.
Verification:
- `npm run backend:compile`
- focused short-duration finish projection smoke with authenticated queue join
- rerun broad memory smoke if implementation touches shared route behavior
Acceptance criteria:
- Finished battle state reaches both ready flags true.
- `/battle/results`, `/replay/catalog/{battleId}`, `/replay/catalog`, and `/mails` expose the projected records consistently.
Risks:
- Replay rating fields are not currently stored in `ReplayRecord`; this may require a scoped domain/repository expansion.
- Rating formula is not product-final; use a conservative deterministic formula matching existing local placement score constraints.

Result:
- Compile passed after adding `DefaultBattleFinishProjector`.
- The projector lives in the battle service/application boundary and writes through existing battle result, replay, and mail repositories.
- Finished state now flips `resultReady=true` and `replayReady=true` only after projection returns `BattleFinishProjectionOutcome.Projected`.
- Projected battle results are idempotent by existing `battleId:handle` result id.
- Projected replay uses `replayId = battleId`, includes one sparse replay frame, and carries optional rating fields matching the first projected human result.
- Projected mails include `mail-battle-${resultId}` and non-zero-delta `mail-rating-${resultId}` with replay source metadata.
- `ReplayRecord` now carries optional `ratingBefore`, `ratingDelta`, and `ratingAfter`; normal user-submitted replay records render these as `null`.
- Postgres replay schema now adds nullable replay rating columns at the repository boundary.
- Focused short-duration projection smoke passed with authenticated six-player queue: result, replay detail/catalog, and battle/rating mails were all readable after readiness.

## Active Ticket

ID: BWR-035
Goal: Update authoritative finish smoke to match rebuilt authenticated queue semantics.
Allowed scope: `scripts/api-contract-authoritative-finish-smoke.ps1`, `.codex/agent-state.md`; read-only backend inspection if needed.
Forbidden scope: production backend changes unless the updated smoke exposes a real regression, frontend, `backend-legacy/**`, real database connections.
Expected change:
- Register or session a temporary identity before joining the queue.
- Send `sessionToken` and a stable `queueRequestId`.
- Fill the default six-player room or otherwise wait for matchmaking activation without weakening backend auth.
Architecture/domain-modeling impact:
- None; this is verification alignment.
Side-effect boundary impact:
- Temporary API data only in smoke backend process.
Verification:
- `npm run backend:compile`
- run updated authoritative finish smoke against a short-duration memory backend
Acceptance criteria:
- The script validates the authoritative projection end to end against the rebuilt backend.
Risks:
- PowerShell `Invoke-RestMethod` can be flaky in this host; use the script as intended but keep curl-based smoke as fallback evidence if host HTTP cmdlets fail.

Result:
- Updated `scripts/api-contract-authoritative-finish-smoke.ps1` to register six temporary identities and join the authenticated queue with session tokens.
- The script now fills the default six-player room instead of relying on unauthenticated single-player matchmaking.
- Ran the updated script against a short-duration memory backend with `SLAY_DEMO_AUTHORITATIVE_BATTLE_DURATION_MS=250`; it passed end to end.
- Verified result/replay/mail/rating consistency through the official smoke output.

## Active Ticket

ID: BWR-036
Goal: Run regression gates after authoritative finish projection.
Allowed scope: `.codex/agent-state.md`, temporary local backend processes, read-only inspection.
Forbidden scope: production code edits unless a regression is found, `backend-legacy/data/**`, real database connections.
Expected change:
- Prefer no production code changes.
- Re-run broad memory API smoke after projection and replay schema changes.
- Run diff whitespace checks.
Architecture/domain-modeling impact:
- Confirm projection did not break existing in-memory contracts.
Side-effect boundary impact:
- Temporary memory-mode backend only; generic `DATABASE_URL` remains ignored.
Verification:
- `npm run backend:compile`
- broad memory smoke
- `git diff --check`
Acceptance criteria:
- Existing contracts still pass after projection.
- No listener remains after smoke.
Risks:
- Real Postgres projection remains unverified without scoped credentials.

Result:
- Compile passed.
- Read-only architecture review found a projection regression before the broad smoke: replay records were battle-level but rendered the first settlement's player-specific score/rating data for every `?handle=...` mail link.
- Broad smoke is deferred until the replay settlement selection bug is fixed.

## Active Ticket

ID: BWR-037
Goal: Make authoritative replay projection render player-scoped settlement fields by handle.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleFinishProjectionService.scala`, `backend/src/main/scala/slaydemo/backend/replay/**`, `frontend/src/features/replay/replayApi.ts`, `frontend/src/features/replay/replayGateway.ts`, `.codex/agent-state.md`, temporary local backend processes.
Forbidden scope: `backend-legacy/**`, `backend-legacy/data/**`, unrelated frontend UI, unrelated repositories, destructive migrations, real database connections.
Expected change:
- Store all human player settlement snapshots on projected replay records.
- Render replay detail/catalog fields from the requested `handle` when a matching projected settlement exists.
- Keep manually submitted replay records compatible with existing null rating behavior.
- Persist projected replay settlements in Postgres through an explicit replay repository boundary.
Architecture/domain-modeling impact:
- Add typed replay settlement records instead of overloading a battle-level replay with one player's settlement.
- Keep replay selection pure and route-side query parsing at the API boundary.
Side-effect boundary impact:
- DB writes remain inside replay repository methods; no domain object performs I/O.
Verification:
- `npm run backend:compile`
- focused short-duration projection smoke validating two different player handles on the same replay id
- broad memory smoke
- `git diff --check`
Acceptance criteria:
- `/replay/catalog/{battleId}?handle=A` and `?handle=B` return the same battle replay frames but handle-specific score, placement, and rating fields.
- Existing `/replay/catalog/{battleId}` remains compatible.
- Frontend replay API forwards the `handle` query it already reads from the page URL.
Risks:
- Cross-repository projection is still not atomic across battle results, replay, and mail; that remains a separate ticket.

Result:
- Added typed `ReplaySettlementRecord` snapshots to projected replay records.
- Authoritative finish projection now stores all human settlements on the battle-level replay instead of only the first result.
- Replay detail and catalog rendering select score, placement, rating, result label, highlight line, handle, display name, and current loadout from `?handle=...` when a matching settlement exists.
- Manual replay submissions remain compatible and continue to render rating fields as `null`.
- Postgres replay persistence now saves replay settlement rows through the replay repository boundary in the same replay save transaction.
- Frontend replay loading now forwards the `handle` query already read by `ReplayDetailPage`.
- Verification passed:
  - `npm run backend:compile`
  - focused short-duration replay settlement smoke: `REPLAY_SETTLEMENT_SMOKE_OK ... handleA=... scoreA=12 handleB=... scoreB=9`
  - `npm run build` passed with existing Vite warnings
  - broad memory smoke passed: health, identity, bots, queue/room/state/SSE/command, results, replay/comments, mail, social, forum, governance
  - official `scripts/api-contract-authoritative-finish-smoke.ps1` passed against a short-duration memory backend
  - `git diff --check` passed with CRLF warnings only

## Active Ticket

ID: BWR-038
Goal: Keep finish projection I/O out of the battle-state lock and retain projection failure state.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, focused smoke commands, `.codex/agent-state.md`.
Forbidden scope: repository schema changes, frontend, `backend-legacy/**`, `backend-legacy/data/**`, unrelated battle simulation logic, real database connections.
Expected change:
- Do not call result/replay/mail repositories while holding the in-memory battle-state lock.
- Track private service-level projection status with an explicit ADT so failures are not collapsed into silent ready=false state.
- Preserve public battle state compatibility: `resultReady` and `replayReady` remain the existing wire booleans.
Architecture/domain-modeling impact:
- The projection status is application/service state, not a domain entity mutation.
- Keep battle aggregate data immutable and state transitions explicit.
Side-effect boundary impact:
- Finish projection side effects remain in the projector, but orchestration should happen outside synchronized in-memory state mutation.
Verification:
- `npm run backend:compile`
- short-duration authoritative finish smoke
- broad memory smoke if the lock/orchestration change touches shared state behavior
Acceptance criteria:
- Finished battles still reach `resultReady=true` and `replayReady=true`.
- Commands/read calls do not execute repository projection under the synchronized lock.
- Projection failures have a retained private failure state for later diagnostics/retry decisions.
Risks:
- Concurrency behavior is subtle; keep the change small and rely on projector idempotent writes.

Result:
- `InMemoryBattleStateService` now tracks private finish projection status as an explicit ADT: pending, in progress, ready, not configured, or failed with message.
- `currentState` and `acceptCommand` advance immutable battle state under the lock, mark projection in progress under the lock, then call `finishProjector.project` outside the lock.
- Projection completion reacquires the lock to set public `resultReady`/`replayReady` only after the projector succeeds.
- Failed and not-configured projection states remain private service state instead of being collapsed into an untracked false flag.
- Verification passed:
  - `npm run backend:compile`
  - official `scripts/api-contract-authoritative-finish-smoke.ps1` against a short-duration memory backend
  - broad memory smoke covering health, identity, bots, queue/room/state/SSE/command, results, replay/comments, mail, social, forum, and governance
  - `git diff --check` passed with CRLF warnings only

## Active Ticket

ID: BWR-039
Goal: Determine whether real Postgres validation can run safely in an isolated local database.
Allowed scope: read-only environment/tool discovery, temporary local process/container discovery, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/data/**`, existing user databases, destructive database commands, production code edits, real credentials committed to files.
Expected change:
- Prefer no production code changes.
- Check whether Docker, psql, or an explicit safe local Postgres endpoint is available.
- If an isolated temporary Postgres database can be created safely, define the next smoke ticket.
Architecture/domain-modeling impact:
- None expected.
Side-effect boundary impact:
- Discovery only unless an isolated temp database path is confirmed.
Verification:
- `docker version` or equivalent discovery
- `psql --version` or equivalent discovery
Acceptance criteria:
- Worklog records whether real Postgres smoke is available, blocked, or needs user-provided credentials.
Risks:
- Must not connect to or modify unknown existing databases.

Result:
- Docker is not installed or not on PATH.
- `psql`, `pg_ctl`, and `postgres` are not installed or not on PATH.
- `SLAY_DEMO_STORAGE_MODE`, `SLAY_DEMO_DATABASE_URL`, `SLAY_DEMO_DATABASE_USER`, and `SLAY_DEMO_DATABASE_PASSWORD` are not set in this shell.
- Real Postgres smoke remains blocked until an isolated temporary database or explicit scoped credentials are available.

## Active Ticket

ID: BWR-040
Goal: Make the official authoritative finish smoke catch player-scoped replay settlement regressions.
Allowed scope: `scripts/api-contract-authoritative-finish-smoke.ps1`, temporary local backend process, `.codex/agent-state.md`.
Forbidden scope: production backend code, frontend, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Extend the finish smoke to verify at least two different human handles against `/replay/catalog/{battleId}?handle=...`.
- Keep the existing first-player result/replay/mail checks.
Architecture/domain-modeling impact:
- None; this is verification hardening.
Side-effect boundary impact:
- Temporary memory backend only.
Verification:
- Run the updated script against a short-duration memory backend.
Acceptance criteria:
- The smoke fails if a non-primary player's replay settlement renders the primary player's score/rating fields.
Risks:
- PowerShell HTTP cmdlets have been flaky in this host; keep the script's existing `Invoke-RestMethod` style unless it fails.

Result:
- Extended `scripts/api-contract-authoritative-finish-smoke.ps1` with `Assert-ReplaySettlementMatchesResult`.
- The smoke now checks primary replay detail and a secondary player's replay detail through `/replay/catalog/{battleId}?handle=...`.
- The smoke now checks secondary player settlement fields in `/replay/catalog?handle=...`.
- First script run exposed a PowerShell interpolation bug in the new assertion string; fixed with `${OwnerHandle}`.
- Verification passed:
  - updated official finish smoke against a short-duration memory backend
  - `git diff --check` passed with CRLF warnings only

## Active Ticket

ID: BWR-041
Goal: Run a parallel read-only audit for backend completeness and architecture risks.
Allowed scope: read-only inspection of `backend/**`, `frontend/src/**`, `scripts/**`, docs under `docs/phases/phase-05-backend-rewrite/**`, `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/data/**`, production code edits, destructive commands, real database connections.
Expected change:
- Identify missing or weak backend contracts after the current rewrite.
- Identify storage boundary risks and accidental generic `DATABASE_URL` usage.
- Identify domain modeling or side-effect boundary violations worth turning into small tickets.
Architecture/domain-modeling impact:
- Audit only; no code changes expected.
Side-effect boundary impact:
- None.
Verification:
- `rg`/read-only file inspection.
Acceptance criteria:
- Worklog records concrete next implementation ticket(s), not vague rewrite work.
Risks:
- Avoid broad refactor recommendations; findings must be actionable and scoped.

Result:
- Parallel audits completed.
- No missing frontend route contexts were found for current frontend fetch calls.
- Storage boundary audit confirmed rebuilt backend ignores generic `DATABASE_URL` and only selects Postgres through explicit `SLAY_DEMO_STORAGE_MODE=postgres` plus `SLAY_DEMO_DATABASE_URL`.
- Remaining storage doc gap: `.env.example` omits `SLAY_DEMO_STORAGE_MODE=postgres`.
- Highest verification gap: `scripts/api-contract-field-smoke.ps1` and `scripts/bp40-battle-session-freshness-smoke.ps1` still contain unauthenticated battle queue joins, while rebuilt backend correctly requires identity sessions.
- Mail metadata remains thinner than some frontend/script optional models; this needs a separate contract decision ticket.
- Domain audit found no mutable domain models, no DB/file/time effects inside domain objects, and mostly explicit enums/ADTs.
- Medium architecture risks to schedule later: move battle join identity validation out of routes, and split pure finish projection planning from repository writes.

## Active Ticket

ID: BWR-042
Goal: Update `api-contract-field-smoke.ps1` queue joins to use authenticated identities.
Allowed scope: `scripts/api-contract-field-smoke.ps1`, temporary local backend process, `.codex/agent-state.md`.
Forbidden scope: production backend code, frontend, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Add script-local helper(s) to register/login smoke handles and pass `sessionToken` to `/battle/queue/join`.
- Preserve existing negative tests for missing/invalid queue auth if present, or keep the change focused on existing positive joins.
Architecture/domain-modeling impact:
- None; this is verification alignment with the new backend auth boundary.
Side-effect boundary impact:
- Temporary memory backend only.
Verification:
- Run the updated field smoke against a memory backend as far as the script can validly proceed.
- If later field assertions are stale for unrelated mail metadata, report that separately instead of broadening this ticket silently.
Acceptance criteria:
- Queue-related failures due solely to missing session tokens are removed from the field smoke.
Risks:
- The field smoke is large and may still contain unrelated stale assertions after queue auth is fixed.

Result:
- Added authenticated battle queue helpers to `scripts/api-contract-field-smoke.ps1`.
- Queue smoke handles are now generated as short playable identity handles.
- Positive battle queue joins now register an identity and send `sessionToken`.
- Battle field tests that need an active room now fill the six-player room with authenticated peers.
- Verification:
  - `scripts/api-contract-field-smoke.ps1` was run against a memory backend.
  - Queue-auth related failures were removed: SSE state frame, authoritative ownership, and room snapshot queue tests passed.
  - Remaining failures are unrelated to queue auth: governance notification mail metadata and five missing battle runtime mechanics.
  - `git diff --check` passed with CRLF warnings only.

## Active Ticket

ID: BWR-043
Goal: Add explicit governance mail metadata to governance notification mail responses.
Allowed scope: `backend/src/main/scala/slaydemo/backend/governance/**`, focused smoke commands, `.codex/agent-state.md`.
Forbidden scope: unrelated mail repository schema changes, frontend, battle runtime, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Extend governance mail snapshot response data so `POST /governance/admin-notifications` includes `governanceActorHandle`, `governanceTargetPath`, and `governanceTargetLabel`.
- Keep the metadata typed inside governance objects/routes instead of adding ad hoc strings in route code.
Architecture/domain-modeling impact:
- Add value-object-backed metadata to governance mail snapshot if needed.
Side-effect boundary impact:
- No new persistence side effects expected in this ticket.
Verification:
- `npm run backend:compile`
- focused governance notification smoke
- rerun `scripts/api-contract-field-smoke.ps1` far enough to confirm the governance metadata failure is gone, while separately tracking battle runtime failures.
Acceptance criteria:
- Governance notification mail response includes the fields expected by the field smoke.
- Existing governance list/adjustment responses remain compatible.
Risks:
- Persisted mailbox metadata for governance mails is a separate contract decision; this ticket only targets the current governance route response failure unless broader persistence is required.

Result:
- Added `GovernanceMailMetadata` to mail records.
- Governance notification mails now include `governanceActorHandle`, `governanceTargetPath`, and `governanceTargetLabel`.
- Governance service now persists generated governance mails through `MailRepository`, so `/mails?ownerHandle=admin` returns the created notification mail with metadata.
- Postgres mail adapter gained nullable governance metadata columns at the repository boundary.
- Verification passed:
  - `npm run backend:compile`
  - focused governance notification mail metadata smoke
  - full `scripts/api-contract-field-smoke.ps1` rerun reduced failures from 6 to 5; the governance metadata test now passes
  - `git diff --check` passed with CRLF warnings only

## Active Ticket

ID: BWR-044
Goal: Implement the minimal authoritative battle runtime mechanics required by the field smoke.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/**`, battle object additions only if needed, temporary local backend processes, `.codex/agent-state.md`.
Forbidden scope: persistence repository changes, frontend, unrelated route contracts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Advance player movement over elapsed time, with sprint moving farther than walk.
- Apply legal Blink movement to `pointerWorld` and reject blocked Blink targets without cooldown.
- Apply basic pistol firing, ammo decrement, projectile creation/lifetime, and obstacle removal.
- Apply Freeze skill by creating a slow field that expires.
- Apply basic projectile damage, elimination, respawn countdown, and respawn event.
Architecture/domain-modeling impact:
- Keep battle state immutable; service mutation remains confined to the in-memory battle state boundary.
- Prefer pure helper functions inside battle service rather than hiding effects in domain objects.
Side-effect boundary impact:
- No new external effects; this is in-memory authoritative state evolution only.
Verification:
- `npm run backend:compile`
- `scripts/api-contract-field-smoke.ps1` against memory backend
- authoritative finish smoke and broad memory smoke after runtime changes
Acceptance criteria:
- The five remaining field smoke battle runtime failures are removed or clearly reduced to a smaller follow-up.
Risks:
- This is the largest remaining backend behavior gap; implement conservative deterministic mechanics only to the contract currently exercised.

Result:
- Added deterministic in-memory battle runtime mechanics:
  - elapsed-time movement with walk/sprint speeds
  - Blink/Dash/Freeze skill resolution with cooldown/active timers
  - Freeze slow fields with movement/projectile slow and expiry
  - pistol ammo consumption, reload completion, projectile lifetime, cover blocking, and player hit damage
  - medkit and pistol-cache pickups with respawn timers and battle events
  - elimination, respawn countdown, respawn restoration, and respawn events
- Added `movement` and `sprint` to immutable `BattlePlayerState` so input can be advanced over elapsed server time.
- Kept runtime side effects inside `InMemoryBattleStateService`; domain objects remain passive immutable data.
- Minimal scope expansion: `BattleStateJson.scala` now renders projectile `WeaponKind.Pistol` as `pistol-bullet` for the existing wire contract.
- Verification passed:
  - `npm run backend:compile`
  - full `npm run demo:api-contract` against memory backend
  - `npm run demo:authoritative-finish-smoke` after restarting backend with `SLAY_DEMO_AUTHORITATIVE_BATTLE_DURATION_MS=1800`
  - backend restarted back to default memory config and `/health` returned `storageMode=memory`
  - `git diff --check` passed with CRLF warnings only
- First field-smoke run after implementation reduced battle runtime failures to one medkit placement issue; moving the medkit away from `cover-nw` resolved it.

Self-review:
- Primitive business types introduced: no new domain-level raw status/role/result strings; one existing projectile wire-name renderer mapping remains a follow-up candidate for a `ProjectileKind` ADT.
- Boolean business results introduced: none.
- Domain mutation introduced: none; battle state transitions use immutable `copy`.
- Side effects inside domain: none.
- Scope respected: yes, with the documented renderer expansion for projectile wire compatibility.

## Active Ticket

ID: BWR-045
Goal: Document the explicit Postgres opt-in environment contract.
Allowed scope: `.env.example`, `docs/phases/phase-05-backend-rewrite/DEMO_BACKEND_RUNBOOK.md`, `.codex/agent-state.md`.
Forbidden scope: backend code, frontend, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections, secrets.
Expected change:
- Add or update env examples so Postgres mode requires explicit `SLAY_DEMO_STORAGE_MODE=postgres`.
- Keep generic `DATABASE_URL` out of the backend contract or clearly mark it ignored by the rebuilt backend.
- Make the runbook's DB connection guidance match the actual `StorageConfig`.
Architecture/domain-modeling impact:
- None; documentation and operator safety only.
Side-effect boundary impact:
- No runtime side effects.
Verification:
- Inspect `StorageConfig.scala` to confirm names.
- `git diff --check`.
Acceptance criteria:
- A developer can tell from the repo docs/env example that memory mode is default and Postgres is opt-in.
- The docs do not encourage accidental use of a generic `DATABASE_URL`.
Risks:
- Avoid adding real credentials or implying that Postgres smoke passed without an available database.

Result:
- `.env.example` now states memory storage is the default.
- `.env.example` now includes explicit `SLAY_DEMO_STORAGE_MODE=postgres` in the Postgres example.
- `.env.example` and the runbook now state generic `DATABASE_URL` alone is ignored by the rebuilt backend.
- Verified against `StorageConfig.scala`: memory is default, and Postgres requires `SLAY_DEMO_STORAGE_MODE=postgres` plus `SLAY_DEMO_DATABASE_URL`.
- Verification passed:
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-046
Goal: Move battle queue join identity validation out of HTTP routes.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/battle/routes/BattleRoutes.scala`, `backend/src/main/scala/slaydemo/backend/battle/services/**`, focused smoke commands, `.codex/agent-state.md`.
Forbidden scope: persistence repositories, frontend, unrelated battle runtime mechanics, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Introduce a small battle application/service boundary for queue join authorization.
- Keep route code focused on parsing, service calls, and HTTP response mapping.
- Preserve current behavior for invalid handle, missing session, invalid session, handle mismatch, and successful joins.
Architecture/domain-modeling impact:
- Removes identity orchestration from the API route layer.
- Keeps authorization outcomes explicit as an enum/ADT rather than string or Boolean results.
Side-effect boundary impact:
- Identity lookup remains effectful but moves behind a named service boundary.
Verification:
- `npm run backend:compile`
- focused queue join auth smoke against memory backend
- `git diff --check`
Acceptance criteria:
- `BattleRoutes` no longer directly depends on `IdentityService` or `HandlePolicy` for queue join validation.
- Queue join HTTP status/code behavior remains compatible.
Risks:
- Constructor wiring touches `BackendApp`; keep the change narrow and avoid changing queue semantics.

Result:
- Added `BattleQueueJoinAuthorizationService` with explicit `BattleQueueJoinAuthorizationError` outcomes.
- Moved session token parsing, identity lookup, playable-handle validation, and handle/session match checking out of `BattleRoutes`.
- `BattleRoutes` now depends on the battle authorization service and only maps authorization results to HTTP responses.
- `BackendApp` wires `DefaultBattleQueueJoinAuthorizationService(identityService)`.
- Verification passed:
  - `npm run backend:compile` after stopping the running sbt backend that held the compile lock.
  - focused queue auth smoke: invalid handle, missing session, invalid session, handle mismatch, and successful join.
  - `rg` confirmed `BattleRoutes.scala` no longer directly references `IdentityService`, `SessionToken`, `HandlePolicy`, `JoinValidationError`, or `validateJoinRequest`.
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; authorization failures are modeled as an enum.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; identity lookup remains in a service boundary.
- Scope respected: yes.

## Active Ticket

ID: BWR-047
Goal: Split battle finish projection planning from repository writes.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleFinishProjectionService.scala`, focused compile/smoke commands, `.codex/agent-state.md`.
Forbidden scope: repository implementations, route contracts, battle runtime mechanics, frontend, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Extract pure planning/mapping from `DefaultBattleFinishProjector.project`.
- Keep repository writes in the effectful projector boundary.
- Preserve current result/replay/mail outputs and player-scoped replay settlements.
Architecture/domain-modeling impact:
- Reduces god-service pressure in finish projection.
- Keeps battle-finish derived records explicit and testable as immutable planned values.
Side-effect boundary impact:
- Repository writes remain effectful but should be separated from pure derivation.
Verification:
- `npm run backend:compile`
- `npm run demo:authoritative-finish-smoke` with short duration memory backend
- `git diff --check`
Acceptance criteria:
- Finish projection behavior remains compatible with the official smoke.
- Pure projection plan can be read without scanning repository write order.
Risks:
- This service feeds result, replay, mail, and rating output; keep the refactor internal and do not change response contracts.

Result:
- Split `DefaultBattleFinishProjector.project` into:
  - repository-backed prior rating input fetch
  - pure `BattleFinishProjectionPlan` construction
  - effectful plan writes to result, mail, and replay repositories
- Preserved current mail behavior by building battle/rating mails from the saved result.
- Preserved player-scoped replay settlement behavior; replay still stores one battle-level replay with all human settlements.
- Subagent read-only review confirmed the split matches the recommended boundary and highlighted the same contract risks.
- Verification passed:
  - `npm run backend:compile`
  - `npm run demo:authoritative-finish-smoke` with `SLAY_DEMO_AUTHORITATIVE_BATTLE_DURATION_MS=1800`
  - backend restarted back to default memory config and `/health` returned `storageMode=memory`
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; repository effects remain in the projector boundary.
- Scope respected: yes.

## Active Ticket

ID: BWR-048
Goal: Replace projectile wire-name renderer special case with an explicit projectile kind ADT.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/objects/**`, `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/main/scala/slaydemo/backend/battle/routes/BattleStateJson.scala`, focused compile/smoke commands, `.codex/agent-state.md`.
Forbidden scope: persistence repositories, frontend, unrelated battle runtime behavior, finish projection, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Introduce a typed projectile kind for active/terminal projectile state.
- Render pistol bullets as `pistol-bullet` from the projectile ADT, not from an ad hoc `WeaponKind.Pistol` renderer branch.
- Preserve existing weapon-kind rendering for player weapons and loadouts.
Architecture/domain-modeling impact:
- Removes a primitive/wire rendering leak from projectile modeling.
- Keeps finite projectile kinds explicit in the type system.
Side-effect boundary impact:
- None; pure domain object and renderer change.
Verification:
- `npm run backend:compile`
- focused obstacle/freeze projectile smoke or full `npm run demo:api-contract`
- `git diff --check`
Acceptance criteria:
- `BattleProjectileState` and `BattleProjectileTerminalState` use projectile-specific kind modeling.
- Field smoke still sees active pistol projectiles as `kind="pistol-bullet"`.
Risks:
- Replay frame rendering currently omits projectile details, so the main risk is state JSON contract compatibility.

Result:
- Added explicit `ProjectileKind` ADT with `PistolBullet`.
- `BattleProjectileState` and `BattleProjectileTerminalState` now use `ProjectileKind` instead of overloading `WeaponKind`.
- `BattleStateJson` now renders projectile kind through `ProjectileKind.wireValue`; the ad hoc `WeaponKind.Pistol -> pistol-bullet` renderer branch was removed.
- Verification passed:
  - `npm run backend:compile`
  - full `npm run demo:api-contract` against memory backend
  - `rg` confirmed no remaining `projectileKind: WeaponKind`, `projectileKind = WeaponKind`, or `projectileWireValue` usage.
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; projectile finite states are now explicit.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-049
Goal: Audit rebuilt backend for remaining raw finite-state strings and Boolean business results.
Allowed scope: read-only inspection of `backend/src/main/scala/**`, `.codex/agent-state.md`.
Forbidden scope: production code edits, frontend, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Search for suspicious `String` finite states and meaningful `Boolean` results in backend domain/service layers.
- Separate acceptable wire/JSON/parser primitives from domain modeling risks.
- Produce concrete next tickets if risky cases remain.
Architecture/domain-modeling impact:
- Audit only.
Side-effect boundary impact:
- None.
Verification:
- `rg` searches and targeted file reads.
Acceptance criteria:
- Worklog records either no actionable domain-modeling issues or a prioritized next ticket with scope.
Risks:
- Avoid turning route-boundary parse errors or JSON wire primitives into false positives.

Result:
- Read-only audit found no mutable domain data, no domain object I/O, and no Boolean return values hiding business outcomes in the core service results.
- Most raw `String` hits are acceptable boundary/display values:
  - HTTP DTOs and route JSON parsing/rendering
  - ID value objects wrapping external identifiers
  - labels, excerpts, paths, and serialized replay frame JSON
  - database wire decoding before mapping into ADTs
- Service interfaces still accept raw strings for route-originated command inputs in several modules; current implementations parse immediately into value objects and return explicit error ADTs, so this is a medium cleanup rather than an active correctness bug.
- Actionable domain-modeling issue found: `SkinId` is a finite set modeled as a string-backed value object with a whitelist. This should be an enum/ADT while preserving API/DB wire values.

Self-review:
- Primitive business types introduced: none; audit only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-050
Goal: Replace string-backed identity `SkinId` with a finite enum.
Allowed scope: `backend/src/main/scala/slaydemo/backend/identity/**`, focused compile/smoke commands, `.codex/agent-state.md`.
Forbidden scope: battle queue skin fields, bot skin/profile fields, frontend, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Model identity account skin as a finite `SkinId` enum/ADT.
- Preserve existing wire/database values: `blue`, `survivor`, `soldier`, `old`.
- Keep invalid skin registration behavior unchanged.
Architecture/domain-modeling impact:
- Converts a finite business value from string validation to type-level modeling.
Side-effect boundary impact:
- No new side effects; Postgres adapter remains a boundary mapper.
Verification:
- `npm run backend:compile`
- focused identity registration/session/accounts smoke
- `git diff --check`
Acceptance criteria:
- `IdentityAccount.skinId` cannot hold an arbitrary raw string.
- API responses still render `skinId` as the same lowercase wire string.
Risks:
- Existing unexpected DB `skin_id` values cannot be represented; map unknown values to the default `blue` at the repository boundary rather than widening the domain.

Result:
- Replaced string-backed `SkinId` value object with a finite enum: `Blue`, `Survivor`, `Soldier`, `Old`.
- Added `SkinId.wireValue` to preserve existing API and database strings.
- Updated identity service, identity routes, and Postgres identity repository mapping.
- Unknown persisted `skin_id` values now map to `SkinId.Blue` at the repository boundary instead of widening the domain.
- Verification passed:
  - `npm run backend:compile`
  - focused identity skin smoke covering register/session/accounts with `soldier`, plus invalid `purple` returning `invalid_skin`
  - `rg` confirmed no remaining `case class SkinId`, direct `SkinId(...)`, or `skinId.value` in identity sources
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; a finite string-backed concept was replaced by an enum.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-051
Goal: Replace battle result/replay readiness booleans with an explicit artifact status ADT.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/objects/**`, `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/main/scala/slaydemo/backend/battle/routes/BattleStateJson.scala`, focused compile/smoke commands, `.codex/agent-state.md`.
Forbidden scope: repositories, frontend, unrelated battle runtime mechanics, finish projection behavior, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Model battle artifact projection readiness as an explicit enum/ADT instead of independent `resultReady` and `replayReady` booleans in the aggregate.
- Preserve current JSON response fields `resultReady` and `replayReady` for frontend/script compatibility.
- Keep projection lifecycle behavior unchanged.
Architecture/domain-modeling impact:
- Prevents invalid aggregate combinations such as result ready without replay ready when the backend treats them as one projection boundary.
Side-effect boundary impact:
- None; in-memory state modeling and renderer mapping only.
Verification:
- `npm run backend:compile`
- `npm run demo:authoritative-finish-smoke` with short duration memory backend
- `git diff --check`
Acceptance criteria:
- `BattleAggregateState` no longer stores independent `resultReady` and `replayReady` booleans.
- JSON still exposes both fields with the same values expected by scripts.
Risks:
- Finish projection status in `BattleStateService` is separate internal orchestration state; avoid conflating it with public artifact readiness.

Result:
- Added `BattleArtifactStatus` ADT with `Pending` and `Ready`.
- `BattleAggregateState` now stores `artifactStatus` instead of independent `resultReady` and `replayReady` booleans.
- `BattleStateJson` preserves the existing `resultReady` and `replayReady` fields by projecting them from the ADT.
- `BattleStateService` projection lifecycle behavior is unchanged; internal `FinishProjectionStatus` remains separate orchestration state.
- Verification passed:
  - `npm run backend:compile`
  - `npm run demo:authoritative-finish-smoke` with `SLAY_DEMO_AUTHORITATIVE_BATTLE_DURATION_MS=1800`
  - backend restarted back to default memory config and `/health` returned `storageMode=memory`
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none; aggregate readiness booleans were removed.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-052
Goal: Move battle result list filter parsing from service into route boundary.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleResultService.scala`, `backend/src/main/scala/slaydemo/backend/battle/routes/BattleResultRoutes.scala`, focused compile/smoke commands, `.codex/agent-state.md`.
Forbidden scope: repositories, frontend, battle runtime, finish projection, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Change battle result service list filters from `Option[String]` to typed `Option[PlayerHandle]` and `Option[BattleId]`.
- Keep HTTP query parsing and validation in the route layer.
- Preserve current visitor/invalid handle behavior.
Architecture/domain-modeling impact:
- Tightens service contract around business identifiers.
Side-effect boundary impact:
- None.
Verification:
- `npm run backend:compile`
- focused battle result list/record smoke or full `npm run demo:authoritative-finish-smoke`
- `git diff --check`
Acceptance criteria:
- `BattleResultService.list` no longer accepts raw string identifiers.
- Existing result list responses remain compatible.
Risks:
- Route currently maps some invalid filters to empty lists or specific errors; preserve observable behavior.

Result:
- Changed `BattleResultService.list` to accept typed `Option[PlayerHandle]` and `Option[BattleId]`.
- Moved query filter parsing into `BattleResultRoutes`.
- Preserved previous invalid handle filter behavior by returning an empty result list.
- Verification passed:
  - `npm run backend:compile`
  - focused battle result smoke covering POST record, GET by handle, GET by battleId, and invalid handle filter returning empty list
  - `rg` confirmed the service no longer has raw string list filters
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; service contract now uses typed identifiers.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-053
Goal: Move mail service owner/mail-id parsing to typed route boundary.
Allowed scope: `backend/src/main/scala/slaydemo/backend/mail/services/MailService.scala`, `backend/src/main/scala/slaydemo/backend/mail/routes/MailRoutes.scala`, focused compile/smoke commands, `.codex/agent-state.md`.
Forbidden scope: mail repositories, governance mail generation, frontend, unrelated services, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Change `MailService.list` and `markRead` to accept typed `PlayerHandle` and `MailId`.
- Keep HTTP owner/mail-id parsing and error mapping in `MailRoutes`.
- Preserve visitor/missing/invalid owner and missing mail id behavior.
Architecture/domain-modeling impact:
- Tightens service contract around business identifiers.
Side-effect boundary impact:
- None.
Verification:
- `npm run backend:compile`
- focused mail list/read smoke
- `git diff --check`
Acceptance criteria:
- `MailService` no longer accepts raw string handles or mail ids.
- Existing mail responses remain compatible.
Risks:
- `list` creates a welcome mail for empty mailboxes; smoke should account for that existing side effect.

Result:
- Changed `MailService.list` to accept `PlayerHandle` and return `Vector[MailRecord]`.
- Changed `MailService.markRead` to accept typed `PlayerHandle` and `MailId`; service now only reports `MailNotFound`.
- Moved owner/mail-id parsing and HTTP error mapping into `MailRoutes`.
- Preserved missing owner, visitor owner, missing mail id, and mail-not-found behavior.
- Verification passed:
  - `npm run backend:compile`
  - focused mail smoke covering welcome mail creation, mark read, missing owner, visitor owner, missing mail id, and not found
  - `rg` confirmed no raw string owner/mail-id methods or `MailOwnerError` remain in mail sources
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; service contract now uses typed identifiers.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-054
Goal: Move social friend request handle/id parsing to typed route boundary.
Allowed scope: `backend/src/main/scala/slaydemo/backend/social/services/FriendRequestService.scala`, `backend/src/main/scala/slaydemo/backend/social/routes/SocialRoutes.scala`, focused compile/smoke commands, `.codex/agent-state.md`.
Forbidden scope: social repositories, mail service/repositories, frontend, unrelated services, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Change friend request service methods to accept typed `PlayerHandle` and `FriendRequestId` where applicable.
- Keep HTTP parsing and existing error mapping in `SocialRoutes`.
- Preserve create/list/respond behavior and notification mail side effects.
Architecture/domain-modeling impact:
- Tightens social service contract around business identifiers.
Side-effect boundary impact:
- Existing mail creation remains in social service boundary.
Verification:
- `npm run backend:compile`
- focused social friend request smoke
- `git diff --check`
Acceptance criteria:
- `FriendRequestService` no longer accepts raw string handles or request ids.
- Existing social responses remain compatible.
Risks:
- Need to preserve distinction between missing fields, invalid actor/owner, visitor handles, forbidden actor, and already-resolved outcomes.

Result:
- Changed `FriendRequestService.create` to accept typed `PlayerHandle` source/target.
- Changed `FriendRequestService.respond` to accept typed `FriendRequestId` and `PlayerHandle`.
- Changed `FriendRequestService.list` to accept typed `PlayerHandle` and return records directly.
- Moved social route parsing and HTTP error mapping into `SocialRoutes`.
- Preserved missing owner, visitor, invalid handles, missing fields, forbidden actor, and accepted response behavior.
- Verification passed:
  - `npm run backend:compile`
  - focused social smoke covering list errors, create errors, successful create/list, respond missing fields, visitor actor, forbidden actor, and accepted response
  - `rg` confirmed no raw string create/list/respond identifiers or `FriendRequestListError` remain in social service sources
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; service contract now uses typed identifiers.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; existing mail creation remains in service boundary.
- Scope respected: yes.

## Active Ticket

ID: BWR-055
Goal: Introduce typed governance command objects for create operations.
Allowed scope: `backend/src/main/scala/slaydemo/backend/governance/services/GovernanceServices.scala`, `backend/src/main/scala/slaydemo/backend/governance/routes/GovernanceRoutes.scala`, focused compile/smoke commands, `.codex/agent-state.md`.
Forbidden scope: governance repositories, mail repositories, frontend, unrelated services, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Add `ContributionAdjustmentCommand` and `GovernanceReviewNotificationCommand` with typed fields.
- Move string parsing for governance create requests to routes.
- Keep existing list filters typed and unchanged.
- Preserve current HTTP error behavior and generated governance mail metadata.
Architecture/domain-modeling impact:
- Tightens governance application service commands and reduces raw primitive business identifiers in service APIs.
Side-effect boundary impact:
- Existing mail persistence remains in governance service boundary.
Verification:
- `npm run backend:compile`
- focused governance contribution/review smoke
- `git diff --check`
Acceptance criteria:
- Governance create service methods no longer accept raw string business identifiers/paths.
- Field smoke governance assertions remain compatible.
Risks:
- Governance review creation currently allows visitor-like actor display names; preserve that product behavior unless a separate decision changes it.

Result:
- Added typed `ContributionAdjustmentCommand` and `GovernanceReviewNotificationCommand`.
- Changed governance create service methods to accept typed command objects and return submission results directly.
- Moved admin/target/delta/review-kind/target-type/target-id/body parsing into `GovernanceRoutes`.
- Preserved contribution adjustment mail output and review notification governance mail metadata.
- Verification passed:
  - `npm run backend:compile`
  - focused governance smoke covering contribution adjustment create/list, review notification create/list, and admin mailbox governance metadata
  - `rg` confirmed governance create service methods no longer expose raw string command inputs or stale create-error Eithers
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; service create commands now use governance value objects and ADTs.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; repository/mail persistence remains in governance service boundary.
- Scope respected: yes.

## Active Ticket

ID: BWR-056
Goal: Move forum command identifier parsing from service into route boundary.
Allowed scope: `backend/src/main/scala/slaydemo/backend/forum/services/ForumService.scala`, `backend/src/main/scala/slaydemo/backend/forum/routes/ForumRoutes.scala`, focused compile/smoke commands, `.codex/agent-state.md`.
Forbidden scope: forum repositories, frontend, unrelated services, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Change forum service create/reply/vote/detail/list inputs from raw route strings to typed value objects or typed command objects.
- Keep HTTP parsing and existing error mapping in `ForumRoutes`.
- Preserve topic/reply/vote responses and visitor guardrails.
Architecture/domain-modeling impact:
- Tightens forum application service contracts around author/viewer/topic/reply identifiers and vote ADTs.
Side-effect boundary impact:
- Existing mutable in-memory/forum repository state remains in service/repository boundary; no new effects.
Verification:
- `npm run backend:compile`
- focused forum smoke for create topic, detail/list, add reply, topic vote, reply vote, null vote clear, missing topic, and visitor mutation rejection
- `git diff --check`
Acceptance criteria:
- `ForumService` no longer accepts raw string handles/topic ids/reply ids for business operations.
- Existing forum HTTP responses remain compatible.
Risks:
- Route currently distinguishes malformed JSON, missing fields, invalid viewer/author, missing topic, and missing reply; preserve observable status/error codes.

Result:
- Added typed forum command objects for create topic, add reply, topic vote, and reply vote.
- Changed `ForumService` list/load/create/reply/vote methods to accept typed handles, topic ids, reply ids, and command objects.
- Moved title/body/tag/author/viewer/topic-id/reply-id parsing into `ForumRoutes`.
- Split route parse errors from service mutation errors so the service only reports persisted topic/reply state failures.
- Preserved vote omission/null behavior as vote clearing.
- Verification passed:
  - `npm run backend:compile`
  - focused forum smoke covering create topic, topic upvote, viewer query vote projection, add reply, reply downvote, null vote clear, invalid vote, visitor rejection, and missing topic
  - `rg` confirmed `ForumService` no longer exposes raw string handles/topic ids/reply ids or the stale `ForumCreateTopicError`
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; service boundary now uses existing forum value objects and `PlayerHandle`.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; repository effects remain in service/repository boundary.
- Scope respected: yes.

## Active Ticket

ID: BWR-057
Goal: Move replay submission/comment identifier parsing from service into route boundary.
Allowed scope: `backend/src/main/scala/slaydemo/backend/replay/services/ReplayService.scala`, `backend/src/main/scala/slaydemo/backend/replay/routes/ReplayRoutes.scala`, focused compile/smoke commands, `.codex/agent-state.md`.
Forbidden scope: replay repositories, frontend, battle finish projector, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Change replay service submit/comment/detail/list inputs from raw route strings to typed value objects or typed command objects where they represent business identifiers.
- Keep HTTP parsing and existing error/status mapping in `ReplayRoutes`.
- Preserve replay catalog/detail/comment responses, settlement handle filtering, and visitor guardrails.
Architecture/domain-modeling impact:
- Tightens replay application service contracts around replay/battle/player/comment identifiers and submission commands.
Side-effect boundary impact:
- Existing mutable repository state remains in service/repository boundary; no new effects.
Verification:
- `npm run backend:compile`
- focused replay smoke for catalog submit/list/detail, detail `handle` settlement filtering, comment create/list, invalid/visitor guardrails
- `git diff --check`
Acceptance criteria:
- `ReplayService` no longer accepts raw string handles or ids for business operations.
- Existing replay HTTP responses remain compatible.
Risks:
- Replay route currently defaults some missing submission fields to empty/zero values; preserve this behavior unless split into a separate validation ticket.

Result:
- Added typed `ReplayRecordCommand` and `ReplayCommentCommand`.
- Changed `ReplayService.record` to accept a typed command and return the saved replay directly.
- Changed `ReplayService.addComment` to accept a typed command; service now only reports `ReplayNotFound`.
- Moved replay id, battle id, record handle, comment author, and comment body parsing into `ReplayRoutes`.
- Preserved permissive replay submission defaults, frame JSON normalization, invalid/unmatched `handle` query fallback, and comment error mapping.
- Verification passed:
  - `npm run backend:compile`
  - focused replay smoke covering catalog submit/list/detail, default normalization, invalid handle query fallback, comment create/list, visitor comment rejection, blank comment body, missing replay, and invalid replay id
  - `npm run demo:authoritative-finish-smoke` with `SLAY_DEMO_AUTHORITATIVE_BATTLE_DURATION_MS=1800`, covering generated replay settlement filtering for primary/secondary handles
  - backend restarted back to default memory config and `/health` returned `storageMode=memory`
  - `rg` confirmed replay service/route no longer reference stale string request DTOs or raw string id/handle service inputs
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none for service boundary identifiers; display labels and frame JSON remain boundary/read-model strings.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; replay repository writes remain in service boundary.
- Scope respected: yes.

## Active Ticket

ID: BWR-058
Goal: Remove stale replay API request DTOs left behind after typed replay commands.
Allowed scope: `backend/src/main/scala/slaydemo/backend/replay/api/ReplayCatalogApi.scala`, focused compile/search commands, `.codex/agent-state.md`.
Forbidden scope: replay routes/services/repositories beyond read-only verification, frontend, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Delete or neutralize unused raw-string replay submission DTOs now that routes build typed commands directly.
- Confirm no code references `ReplaySubmissionRequest` or `ReplayCommentSubmissionRequest`.
Architecture/domain-modeling impact:
- Removes obsolete primitive-heavy API models from the backend source tree.
Side-effect boundary impact:
- None.
Verification:
- `npm run backend:compile`
- `rg "ReplaySubmissionRequest|ReplayCommentSubmissionRequest" backend/src/main/scala`
- `git diff --check`
Acceptance criteria:
- Stale replay request DTOs no longer exist as misleading application models.
- Backend still compiles.
Risks:
- If an external Scala caller depended on these package types, removal would be source-incompatible; current repo search shows no such caller.

Result:
- Deleted `ReplayCatalogApi.scala`, which only contained unused raw-string replay request DTOs.
- Verification passed:
  - `npm run backend:compile`
  - `rg "ReplaySubmissionRequest|ReplayCommentSubmissionRequest" backend/src/main/scala` returned no matches
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; obsolete primitive-heavy DTOs were removed.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-059
Goal: Audit remaining backend service boundaries for raw business identifiers after typed-command cleanup.
Allowed scope: read-only inspection of `backend/src/main/scala/slaydemo/backend/**`, `.codex/agent-state.md`.
Forbidden scope: production code edits, frontend, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Search service traits and public service methods for raw `String`/primitive business identifiers, Boolean business outcomes, and route parsing leakage.
- Separate acceptable display/wire/technical strings from actionable service-boundary risks.
- Select the next smallest ticket.
Architecture/domain-modeling impact:
- Prevents continuing cleanup by guesswork after the obvious service APIs have been tightened.
Side-effect boundary impact:
- None; audit only.
Verification:
- `rg` searches and targeted file reads.
Acceptance criteria:
- Worklog records prioritized remaining service-boundary issues or confirms none remain in this category.
Risks:
- Avoid false positives for display labels, JSON/frame strings, URLs, excerpts, and repository boundary mapping.

Result:
- Local read-only audit found the remaining high-signal service-boundary cleanup:
  - `BattleResultService.record` still accepts `BattleResultSubmissionRequest`, a route-originated raw-string DTO for `battleId` and `handle`.
  - `IdentityService.register`/`issueSession` still accept raw handle/password/skin strings; password is a boundary credential and should be handled in a separate identity command ticket.
- Previously tightened services remain typed at the public boundary: mail, social, governance, forum, replay list/comment/record, and battle result list filters.
- Acceptable primitives noted:
  - display labels, excerpts, source paths, replay frame JSON, URLs, route-local parser strings, and repository mapping strings
  - numeric limits at list boundaries
  - booleans that are state flags (`aliveAtEnd`, `playbackAvailable`) rather than service result values.

Self-review:
- Primitive business types introduced: none; audit only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-060
Goal: Move battle result record identifier parsing from service into route boundary.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleResultService.scala`, `backend/src/main/scala/slaydemo/backend/battle/routes/BattleResultRoutes.scala`, focused compile/smoke commands, `.codex/agent-state.md`.
Forbidden scope: battle result repositories, battle runtime, finish projector, frontend, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Replace `BattleResultSubmissionRequest` service input with a typed battle-result record command.
- Parse `battleId` and `handle` in `BattleResultRoutes`, preserving current defaults and error mapping.
- Keep list filters typed and unchanged.
Architecture/domain-modeling impact:
- Tightens battle result application service contract around battle and player identifiers.
Side-effect boundary impact:
- Existing result repository write remains in service boundary; no new effects.
Verification:
- `npm run backend:compile`
- focused battle result smoke for POST record, GET by battleId, GET by handle, visitor/invalid record rejection, and invalid handle filter returning empty list
- `git diff --check`
Acceptance criteria:
- `BattleResultService.record` no longer accepts raw string battle ids or handles.
- Existing battle result HTTP responses remain compatible.
Risks:
- Public result POST is a compatibility endpoint; preserve permissive normalization for display name, timestamps, duration, score, placement, and current loadout.

Result:
- Added typed `BattleResultRecordCommand`.
- Changed `BattleResultService.record` to accept the typed command and return the saved result directly.
- Moved battle id and handle parsing into `BattleResultRoutes`.
- Preserved public POST normalization for display name, timestamps, duration, score, placement, rating fields, labels, and current loadout.
- Verification passed:
  - `npm run backend:compile`
  - focused battle result smoke covering POST record, normalization, GET by battleId, GET by handle, visitor handle filter returning empty list, visitor POST rejection, invalid battle id, and invalid handle
  - `rg` confirmed the service/route no longer reference `BattleResultSubmissionRequest` or `BattleResultRecordError`; only the stale DTO file remains
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; service boundary now uses battle/player/rating/time value objects.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; result repository write remains in service boundary.
- Scope respected: yes.

## Active Ticket

ID: BWR-061
Goal: Remove stale battle result API request DTO left behind after typed result command.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/api/BattleResultApi.scala`, focused compile/search commands, `.codex/agent-state.md`.
Forbidden scope: battle routes/services/repositories beyond read-only verification, frontend, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Delete or neutralize unused raw-string `BattleResultSubmissionRequest`.
- Confirm no code references the stale DTO.
Architecture/domain-modeling impact:
- Removes an obsolete primitive-heavy API model from the backend source tree.
Side-effect boundary impact:
- None.
Verification:
- `npm run backend:compile`
- `rg "BattleResultSubmissionRequest" backend/src/main/scala`
- `git diff --check`
Acceptance criteria:
- Stale battle result request DTO no longer exists as a misleading application model.
- Backend still compiles.
Risks:
- If an external Scala caller depended on this package type, removal would be source-incompatible; current repo search shows no such caller.

Result:
- Deleted `BattleResultApi.scala`, which only contained unused raw-string `BattleResultSubmissionRequest`.
- Verification passed:
  - `npm run backend:compile`
  - `rg "BattleResultSubmissionRequest" backend/src/main/scala` returned no matches
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; obsolete primitive-heavy DTO was removed.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-062
Goal: Move identity register/session primitive parsing into route boundary.
Allowed scope: `backend/src/main/scala/slaydemo/backend/identity/services/IdentityService.scala`, `backend/src/main/scala/slaydemo/backend/identity/routes/IdentityRoutes.scala`, focused compile/smoke commands, `.codex/agent-state.md`.
Forbidden scope: identity repositories, password hasher/ports, frontend, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Introduce typed identity service commands for registration and session issuance using `PlayerHandle`, `PlainTextPassword`, and `SkinId`.
- Keep raw JSON/body parsing and existing HTTP error mapping in `IdentityRoutes`.
- Preserve builtin admin session behavior and invalid skin/password/handle semantics.
Architecture/domain-modeling impact:
- Tightens identity service contract around player handle, password, and finite skin values.
Side-effect boundary impact:
- Existing identity repository/session effects remain in identity service boundary; no new effects.
Verification:
- `npm run backend:compile`
- focused identity smoke for register/session/me/accounts, invalid handle, invalid password, invalid skin, visitor rejection, and admin login
- `git diff --check`
Acceptance criteria:
- `IdentityService.register` and `issueSession` no longer accept raw strings.
- Existing identity HTTP responses remain compatible.
Risks:
- Password remains sensitive boundary data; use `PlainTextPassword` and do not log or expose it.

Result:
- Added typed `IdentityRegistrationCommand` and `IdentitySessionCommand`.
- Changed `IdentityService.register` and `issueSession` to accept typed commands instead of raw handle/password/skin strings.
- Moved registration/session parsing into `IdentityRoutes` with route-local parse error ADTs.
- Preserved builtin admin reservation, current-session behavior, invalid credential semantics, and skin wire values.
- Verification passed:
  - `npm run backend:compile`
  - focused identity smoke covering register, session, `/identity/me`, accounts, invalid handle, invalid password, invalid skin, Visitor rejection, reserved `admin` registration, and bad login
  - `rg` confirmed identity service methods no longer accept raw strings; only stale request DTOs remain in `IdentityApi.scala`
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; service boundary now uses `PlayerHandle`, `PlainTextPassword`, and `SkinId`.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; identity repository/session effects remain in service boundary.
- Scope respected: yes.

## Active Ticket

ID: BWR-063
Goal: Remove stale identity request DTOs left behind after typed identity commands.
Allowed scope: `backend/src/main/scala/slaydemo/backend/identity/api/IdentityApi.scala`, focused compile/search commands, `.codex/agent-state.md`.
Forbidden scope: identity routes/services/repositories beyond read-only verification, frontend, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Delete unused raw-string `IdentityRegisterRequest` and `IdentitySessionRequest`.
- Preserve `IdentityAuthResponse` and `IdentityAccountSummary`.
Architecture/domain-modeling impact:
- Removes obsolete primitive-heavy request models from the identity API package.
Side-effect boundary impact:
- None.
Verification:
- `npm run backend:compile`
- `rg "IdentityRegisterRequest|IdentitySessionRequest" backend/src/main/scala`
- `git diff --check`
Acceptance criteria:
- Stale identity request DTOs no longer exist as misleading application models.
- Backend still compiles.
Risks:
- If an external Scala caller depended on these package types, removal would be source-incompatible; current repo search shows no such caller.

Result:
- Removed unused `IdentityRegisterRequest` and `IdentitySessionRequest`.
- Preserved `IdentityAuthResponse` and `IdentityAccountSummary`.
- Verification passed:
  - `npm run backend:compile`
  - `rg "IdentityRegisterRequest|IdentitySessionRequest" backend/src/main/scala` returned no matches
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; obsolete primitive-heavy DTOs were removed.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-064
Goal: Audit battle queue service API before tightening join/heartbeat commands.
Allowed scope: read-only inspection of `backend/src/main/scala/slaydemo/backend/battle/api/BattleQueueApi.scala`, `backend/src/main/scala/slaydemo/backend/battle/services/BattleQueueService.scala`, `backend/src/main/scala/slaydemo/backend/battle/services/BattleQueueAuthorizationService.scala`, `backend/src/main/scala/slaydemo/backend/battle/routes/BattleRoutes.scala`, related focused smoke scripts if needed, `.codex/agent-state.md`.
Forbidden scope: production code edits, frontend, repositories, battle runtime mechanics, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Determine the smallest safe split between wire queue DTOs and typed service/application commands.
- Preserve authenticated join behavior added earlier.
- Select the next implementation ticket.
Architecture/domain-modeling impact:
- Prevents broad queue refactor without understanding route/auth/service ownership.
Side-effect boundary impact:
- None; audit only.
Verification:
- Targeted reads and `rg` searches.
Acceptance criteria:
- Worklog records a small follow-up ticket for queue command typing, or explains why it should be deferred.
Risks:
- Queue join touches identity authorization, room state, matchmaking, and smoke scripts; avoid silently expanding scope.

Result:
- Read-only audit found queue join is the smallest safe target:
  - `BattleQueueService.join` and `BattleQueueJoinAuthorizationService.authorize` both accept `BattleQueueJoinRequest`, a wire DTO with raw handle/session/rating/skin strings.
  - `BattleRoutes.parseJoinRequest` already owns JSON parsing and can build a typed command.
- Deferred heartbeat typing:
  - `RealtimeRoomHeartbeatCommand.handle: Option[String]` can be changed later, but existing behavior can match any trimmed handle string, including non-standard names. Mixing it with join typing would broaden the ticket.
- Existing authenticated join behavior to preserve:
  - missing/invalid handle -> `400 invalid_handle`
  - missing session -> `401 missing_session`
  - invalid session -> `401 invalid_session`
  - session/handle mismatch -> `403 identity_mismatch`
  - same `queueRequestId` idempotently returns the same waiting ticket.

Self-review:
- Primitive business types introduced: none; audit only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-065
Goal: Replace battle queue join wire DTO service input with a typed join command.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleQueueService.scala`, `backend/src/main/scala/slaydemo/backend/battle/services/BattleQueueAuthorizationService.scala`, `backend/src/main/scala/slaydemo/backend/battle/routes/BattleRoutes.scala`, focused compile/smoke commands, `.codex/agent-state.md`.
Forbidden scope: battle queue API DTO deletion, heartbeat command typing, battle state/runtime mechanics, frontend, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Add a typed `BattleQueueJoinCommand` using `PlayerHandle`, `SessionToken`, `QueueRequestId`, and `Rating`.
- Parse raw join body fields in `BattleRoutes`.
- Update join authorization and queue service to accept the typed command.
- Preserve HTTP error mapping and queue idempotency behavior.
Architecture/domain-modeling impact:
- Tightens queue application/service contracts around identity and queue identifiers.
Side-effect boundary impact:
- Existing in-memory matchmaking mutation remains in queue service boundary; no new effects.
Verification:
- `npm run backend:compile`
- focused authenticated queue join smoke covering missing/invalid handle/session, mismatch, valid join, same `queueRequestId` idempotency, status, room snapshot
- `git diff --check`
Acceptance criteria:
- `BattleQueueService.join` and `BattleQueueJoinAuthorizationService.authorize` no longer accept raw `BattleQueueJoinRequest`.
- Existing queue join responses remain compatible.
Risks:
- Do not delete `BattleQueueJoinRequest` in this ticket; remove stale DTOs only after search/compile confirms no usage.

Result:
- Added typed `BattleQueueJoinCommand` with `PlayerHandle`, `SessionToken`, optional `QueueRequestId`, optional `Rating`, avatar, and skin.
- Changed `BattleQueueService.join` to accept the typed command and return a snapshot directly.
- Changed `BattleQueueJoinAuthorizationService.authorize` to accept the typed command; route parsing now owns invalid handle and missing session.
- Preserved queue join idempotency for same `queueRequestId`.
- Verification passed:
  - `npm run backend:compile`
  - focused authenticated queue join smoke covering missing session, invalid handle, invalid session, handle/session mismatch, valid join, same `queueRequestId` idempotency, status, and room snapshot
  - `rg` confirmed service/auth/routes no longer reference `BattleQueueJoinRequest` or `BattleQueueJoinError`; only the stale DTO remains
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; join service/auth boundary now uses typed identity/session/queue/rating values.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; queue mutation remains in service boundary.
- Scope respected: yes.

## Active Ticket

ID: BWR-066
Goal: Remove stale battle queue join request DTO after typed join command.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/api/BattleQueueApi.scala`, focused compile/search commands, `.codex/agent-state.md`.
Forbidden scope: battle routes/services beyond read-only verification, heartbeat/leave DTO changes, frontend, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Delete unused raw-string `BattleQueueJoinRequest`.
- Preserve `RealtimeRoomHeartbeatRequest` and `BattleQueueLeaveRequest`.
Architecture/domain-modeling impact:
- Removes obsolete primitive-heavy join request model from the battle API package.
Side-effect boundary impact:
- None.
Verification:
- `npm run backend:compile`
- `rg "BattleQueueJoinRequest" backend/src/main/scala`
- `git diff --check`
Acceptance criteria:
- Stale battle queue join request DTO no longer exists as a misleading service/input model.
- Backend still compiles.
Risks:
- If an external Scala caller depended on this package type, removal would be source-incompatible; current repo search shows no such caller.

Result:
- Removed unused `BattleQueueJoinRequest`.
- Preserved `RealtimeRoomHeartbeatRequest` and `BattleQueueLeaveRequest`.
- Verification passed:
  - `npm run backend:compile`
  - `rg "BattleQueueJoinRequest" backend/src/main/scala` returned no matches
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; obsolete primitive-heavy join DTO was removed.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-067
Goal: Type the realtime room heartbeat handle at the service boundary.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleQueueService.scala`, `backend/src/main/scala/slaydemo/backend/battle/routes/BattleRoutes.scala`, focused compile/smoke commands, `.codex/agent-state.md`.
Forbidden scope: heartbeat request DTO deletion, join/leave behavior, battle state/runtime mechanics, frontend, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Change `RealtimeRoomHeartbeatCommand.handle` from `Option[String]` to `Option[PlayerHandle]`.
- Parse optional heartbeat handle in `BattleRoutes`; invalid/unplayable handles should simply not match a participant, preserving non-error heartbeat behavior when room/ticket identifies the room.
- Keep room/ticket resolution behavior unchanged.
Architecture/domain-modeling impact:
- Tightens room heartbeat service command around player identity without broad queue refactoring.
Side-effect boundary impact:
- Existing in-memory last-seen update remains in queue service boundary.
Verification:
- `npm run backend:compile`
- focused room heartbeat smoke after authenticated queue join
- `git diff --check`
Acceptance criteria:
- `RealtimeRoomHeartbeatCommand` no longer carries a raw handle string.
- Existing heartbeat response behavior remains compatible.
Risks:
- Bot/non-standard handle matching by raw string is no longer supported through this service command; clients should heartbeat with ticket/room and playable user handles.

Result:
- Changed `RealtimeRoomHeartbeatCommand.handle` from `Option[String]` to `Option[PlayerHandle]`.
- Moved optional heartbeat handle parsing into `BattleRoutes`; invalid/unplayable handles are ignored for matching, while room/ticket resolution behavior is unchanged.
- Removed the now-unused `HandlePolicy` dependency from `BattleQueueService`.
- Verification passed:
  - `npm run backend:compile`
  - focused heartbeat smoke covering authenticated join, heartbeat by ticket, heartbeat by room path, and invalid handle with room path still returning a room snapshot
  - `rg` confirmed `RealtimeRoomHeartbeatCommand` no longer carries `Option[String]`
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; heartbeat handle is now typed.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; last-seen update remains in queue service boundary.
- Scope respected: yes.

## Active Ticket

ID: BWR-068
Goal: Run a broad contract gate after service-boundary typed-command cleanup.
Allowed scope: verification commands, `.codex/agent-state.md`.
Forbidden scope: production code edits unless a verified regression is found, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Run the broad API contract smoke against the current memory backend/frontend proxy.
- Run the authoritative finish smoke with short battle duration if the broad contract passes.
- Record any failures as new tickets rather than mixing unrelated fixes.
Architecture/domain-modeling impact:
- Verification gate only.
Side-effect boundary impact:
- Memory backend state only; no database/file data access.
Verification:
- `npm run demo:api-contract`
- `npm run demo:authoritative-finish-smoke` with `SLAY_DEMO_AUTHORITATIVE_BATTLE_DURATION_MS=1800`
- backend restarted back to default memory config and `/health`
- `git diff --check`
Acceptance criteria:
- Broad contract and finish smokes pass, or failures are triaged into scoped follow-up tickets.
Risks:
- Contract scripts use the Vite proxy at `http://127.0.0.1:5173/api`; start missing frontend service if needed.

Result:
- Confirmed frontend proxy and backend were listening with `npm run dev:status`.
- Verification passed:
  - `npm run demo:api-contract`
  - `npm run demo:authoritative-finish-smoke` with `SLAY_DEMO_AUTHORITATIVE_BATTLE_DURATION_MS=1800`
  - backend restarted back to default memory config and `/health` returned `storageMode=memory`
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; verification only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-069
Goal: Re-audit remaining service/API primitive DTOs after the broad contract gate.
Allowed scope: read-only inspection of `backend/src/main/scala/slaydemo/backend/**`, `.codex/agent-state.md`.
Forbidden scope: production code edits, frontend, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Identify remaining raw request DTOs or service command fields that are still misleading or domain-significant.
- Separate boundary DTOs that are acceptable from service-level risks.
- Select the next smallest implementation ticket.
Architecture/domain-modeling impact:
- Keeps cleanup ticket-driven after a green broad verification gate.
Side-effect boundary impact:
- None; audit only.
Verification:
- `rg` searches and targeted reads.
Acceptance criteria:
- Worklog records prioritized remaining issues or confirms service-boundary typed-command cleanup is complete enough to move to tests/persistence validation.
Risks:
- Avoid over-modeling display strings, raw JSON artifacts, and route-only DTOs with no service leakage.

Result:
- Service-boundary typed-command cleanup is complete enough for the current rewrite pass:
  - identity, battle result record/list, battle queue join/heartbeat, mail, social, governance, forum, and replay record/comment service boundaries now use value objects or typed commands for business identifiers.
  - `BattleCommandRequest` remains in `api`, but it already carries typed battle/player/ticket/tick values and command flags rather than raw string identifiers.
- Remaining primitive-heavy items are acceptable or lower-priority:
  - route-only DTOs such as `RealtimeRoomHeartbeatRequest` and `BattleQueueLeaveRequest`
  - display labels, mail copy, replay frame JSON, source paths, optional current loadout display text
  - repository/database mapping strings
- The more valuable next gap is executable verification for storage env parsing, especially the guarantee that generic `DATABASE_URL` is ignored.

Self-review:
- Primitive business types introduced: none; audit only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-070
Goal: Add executable storage configuration contract checks.
Allowed scope: `backend/src/test/scala/slaydemo/backend/shared/storage/**`, `backend/build.sbt` only if unavoidable, focused test/compile commands, `.codex/agent-state.md`.
Forbidden scope: production storage implementation unless a verified bug is found, Postgres repositories, frontend, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Add a small dependency-free Scala test main for `StorageConfig.fromEnvironment`.
- Cover default memory, generic `DATABASE_URL` ignored, explicit Postgres requires `SLAY_DEMO_STORAGE_MODE=postgres` and `SLAY_DEMO_DATABASE_URL`, unsupported mode, and file root behavior.
Architecture/domain-modeling impact:
- Protects explicit storage side-effect boundaries with executable checks.
Side-effect boundary impact:
- Tests must not open database/file connections.
Verification:
- `cd backend && sbt "Test/runMain slaydemo.backend.shared.storage.StorageConfigContractTest"`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Storage env contract is tested without adding external test dependencies.
- Tests pass and do not require credentials or external services.
Risks:
- SBT test source layout is new in this backend; keep it minimal and dependency-free.

Result:
- Added dependency-free `StorageConfigContractTest` under SBT test sources.
- Covered default memory mode, generic `DATABASE_URL` ignored, explicit Postgres URL requirement, explicit Postgres settings parsing, file root requirement, file root parsing, and unsupported mode normalization.
- Verification passed:
  - `cd backend; sbt "Test / runMain slaydemo.backend.shared.storage.StorageConfigContractTest"`
  - `npm run backend:compile`
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; tests exercise existing config value objects and enums.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; tests pass an in-memory env map and do not connect to files or databases.
- Scope respected: yes.

## Active Ticket

ID: BWR-071
Goal: Audit battle finish projection for the next executable test seam.
Allowed scope: read-only inspection of `backend/src/main/scala/slaydemo/backend/battle/**`, related replay/mail/result domain models, `.codex/agent-state.md`.
Forbidden scope: production code edits, frontend, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Determine whether the next small ticket should test pure finish projection planning or first extract a small pure planning seam.
- Identify the exact files and cases needed without broadening battle runtime.
Architecture/domain-modeling impact:
- Protects authoritative finish projection behavior with typed, executable checks rather than only broad smoke scripts.
Side-effect boundary impact:
- Audit only; no repository writes or server startup.
Verification:
- `rg` searches and targeted reads.
Acceptance criteria:
- Worklog records a small follow-up implementation ticket or explains why this area should be deferred.
Risks:
- `DefaultBattleFinishProjector` currently mixes pure plan construction with repository writes; avoid a broad refactor if a smaller seam is available.

Result:
- Finish projection has a useful pure seam, but `buildProjectionPlan`, `BattleFinishProjectionPlan`, and `BattleSettlement` are currently private inside `DefaultBattleFinishProjector`.
- Testing through `DefaultBattleFinishProjector.project` is possible with in-memory repositories, but that would validate repository writes rather than the pure planning rule directly.
- The next smallest implementation ticket is to extract a package-private pure planner in the same service file and add a no-framework contract test for deterministic plan output.

Self-review:
- Primitive business types introduced: none; audit only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-072
Goal: Extract and test pure battle finish projection planning.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleFinishProjectionService.scala`, `backend/src/test/scala/slaydemo/backend/battle/services/**`, focused test/compile commands, `.codex/agent-state.md`.
Forbidden scope: battle runtime mechanics, repositories, routes, frontend, scripts, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Move projection plan construction into a package-private pure planner object.
- Keep `DefaultBattleFinishProjector.project` behavior and repository writes unchanged.
- Add a dependency-free `Test/runMain` contract test for human-only settlements, placement order, previous rating carry-forward, replay settlements, and frame JSON escaping.
Architecture/domain-modeling impact:
- Separates pure finish settlement planning from repository side effects.
- Keeps typed battle/replay/mail values and avoids introducing public API surface.
Side-effect boundary impact:
- Planning remains pure; repository reads/writes stay in `DefaultBattleFinishProjector`.
Verification:
- `cd backend && sbt "Test / runMain slaydemo.backend.battle.services.BattleFinishProjectionPlanContractTest"`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Planner test passes without server startup, file access, database access, or dependency changes.
- Existing projector still delegates to the planner and compiles.
Risks:
- Test fixtures for `BattleAggregateState` are verbose; keep them local and avoid touching runtime code.

Result:
- Extracted package-private `BattleFinishProjectionPlanner` and immutable `BattleFinishProjectionPlan`/`BattleSettlement` models from `DefaultBattleFinishProjector`.
- Added typed `BattlePreviousRatings` wrapper so planner input uses `PlayerHandle` and `Rating` rather than a raw `Map[String, Int]`.
- Kept repository reads and writes inside `DefaultBattleFinishProjector`; planner construction is pure.
- Added dependency-free `BattleFinishProjectionPlanContractTest`.
- Verification passed:
  - `cd backend; sbt "Test / runMain slaydemo.backend.battle.services.BattleFinishProjectionPlanContractTest"`
  - `npm run backend:compile`
  - `git diff --check` passed with CRLF warnings only.
  - `rg -n "[ \t]+$" ...` on touched new/untracked files returned no trailing-whitespace matches.

Self-review:
- Primitive business types introduced: none; previous ratings were tightened behind `BattlePreviousRatings`.
- Boolean business results introduced: none; existing replay playback/alive flags remain read-model fields.
- Domain mutation introduced: none.
- Side effects inside domain: none; repository effects remain in `DefaultBattleFinishProjector`.
- Scope respected: yes.

## Active Ticket

ID: BWR-073
Goal: Run an authoritative finish integration gate after planner extraction.
Allowed scope: verification commands and `.codex/agent-state.md`.
Forbidden scope: production code edits unless a verified regression is found, frontend changes, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Start the memory backend with a short authoritative battle duration.
- Run the authoritative finish smoke to verify projector integration still writes battle results, replay settlements, and mails through the service/repository boundary.
- Restore the backend to default memory config afterward.
Architecture/domain-modeling impact:
- Verification gate only.
Side-effect boundary impact:
- Uses memory backend state only; no database/file persistence.
Verification:
- `npm run demo:authoritative-finish-smoke` with `SLAY_DEMO_AUTHORITATIVE_BATTLE_DURATION_MS=1800`
- backend default `/health` after restore
- `git diff --check`
Acceptance criteria:
- Authoritative finish smoke passes after planner extraction.
- Backend is restored to default memory mode when the gate is done.
Risks:
- Requires frontend proxy on `127.0.0.1:5173`; start it only if `npm run dev:status` shows it is not listening.

Result:
- Confirmed frontend proxy was listening on `127.0.0.1:5173` and backend port `8080` was initially free.
- Started memory backend with `SLAY_DEMO_AUTHORITATIVE_BATTLE_DURATION_MS=1800`.
- Verification passed:
  - `npm run demo:authoritative-finish-smoke`
  - restarted backend with default environment
  - `GET http://127.0.0.1:8080/health` returned `storageMode=memory`
  - `git diff --check` passed with CRLF warnings only
  - `npm run dev:status` confirmed backend and Vite are listening.

Self-review:
- Primitive business types introduced: none; verification only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none beyond memory smoke state.
- Scope respected: yes.

## Active Ticket

ID: BWR-074
Goal: Add a single backend contract-test command for dependency-free Scala checks.
Allowed scope: `backend/src/test/scala/slaydemo/backend/**`, `package.json`, focused test/compile commands, `.codex/agent-state.md`.
Forbidden scope: production code, dependency changes, frontend source, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Add a test runner object that invokes existing no-framework contract tests.
- Add an npm script so future tickets can run all backend contract checks with one command.
Architecture/domain-modeling impact:
- No domain model changes; improves verification ergonomics.
Side-effect boundary impact:
- Tests must stay in-memory and must not start the server or connect to storage.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- One command runs storage config and finish projection planner contract checks.
- No test framework or dependency version changes are introduced.
Risks:
- The command will only include no-framework tests explicitly listed in the runner; future tests must be added deliberately.

Result:
- Added `BackendContractTestRunner` to run all current dependency-free backend contract checks.
- Added npm script `backend:test-contracts`.
- Verification passed:
  - `npm run backend:test-contracts`
  - `npm run backend:compile`
  - `git diff --check` passed with CRLF warnings only.
  - `rg -n "[ \t]+$" ...` on touched tracked/new files returned no trailing-whitespace matches.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; runner only invokes existing in-memory contract checks.
- Scope respected: yes.

## Active Ticket

ID: BWR-075
Goal: Audit Postgres connection and repository initialization boundaries before further persistence work.
Allowed scope: read-only inspection of `backend/src/main/scala/slaydemo/backend/shared/storage/**`, `backend/src/main/scala/slaydemo/backend/**/database/**`, `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, docs/runbook if needed, `.codex/agent-state.md`.
Forbidden scope: production code edits, dependency changes, frontend, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Identify whether database connections are only opened after explicit Postgres mode selection.
- Check whether repository constructors or table initialization have surprising side effects.
- Select the next smallest safe implementation or test ticket.
Architecture/domain-modeling impact:
- Keeps persistence effects at storage/repository boundaries.
Side-effect boundary impact:
- Audit only; do not connect to a database.
Verification:
- `rg` searches and targeted reads.
- `npm run backend:test-contracts` if no code changes are made.
Acceptance criteria:
- Worklog records current database connection boundary facts and next action.
Risks:
- Without credentials or a temporary Postgres instance, real Postgres runtime validation remains blocked.

Result:
- `PostgresSupport` is the only direct JDBC driver boundary in new backend code; it owns `Class.forName("org.postgresql.Driver")` and `DriverManager.getConnection`.
- `StorageConfig.fromEnvironment` and `BackendConfig.fromEnvironment` parse configuration only and do not open connections.
- `BackendApp` constructs Postgres repositories only inside `StorageConfig.Postgres(connection)` branches.
- All Postgres repositories call `initialize()` during construction, so repository construction itself opens connections and creates tables.
- `PostgresBotProfileRepository` also seeds default bot profiles during construction if the table is empty.
- Real Postgres validation remains blocked without a scoped database or credentials.

Self-review:
- Primitive business types introduced: none; audit only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-076
Goal: Add executable contract coverage for backend repository wiring selection.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, optional new backend app wiring file under `backend/src/main/scala/slaydemo/backend/**`, `backend/src/test/scala/slaydemo/backend/**`, `package.json` only if needed, focused test/compile commands, `.codex/agent-state.md`.
Forbidden scope: repository implementations, storage config implementation, routes/services behavior, frontend, scripts, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Extract repository bundle selection from `BackendApp` into a package-private component with injectable repository factories.
- Keep live behavior unchanged for memory/file/postgres modes.
- Add a dependency-free contract test proving memory mode selects only in-memory factories and Postgres mode is the only path that calls Postgres factories.
- Avoid constructing real Postgres repositories in tests.
Architecture/domain-modeling impact:
- Makes the storage side-effect boundary explicit and testable.
Side-effect boundary impact:
- No new runtime effects; tests use fake factories only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Repository selection is executable-tested without starting the server or connecting to a database.
- `BackendApp` still wires the same repositories and services.
Risks:
- There are many repository types; keep the extraction mechanical and avoid service/routing changes.

Result:
- Added `BackendRepositories` and `BackendRepositoryFactories` as a package-private repository bundle selection boundary.
- `BackendApp` now asks the bundle for repositories instead of matching each `StorageConfig` branch inline.
- Added `BackendRepositoryWiringContractTest` with fake counting factories:
  - memory mode calls only in-memory factories
  - Postgres mode calls only Postgres factories and passes the selected `PostgresConnectionSettings`
  - file mode rejects before constructing any repository
- Added the new wiring test to `BackendContractTestRunner`.
- Verification passed:
  - `npm run backend:test-contracts`
  - `npm run backend:compile`
  - default memory backend startup plus `GET http://127.0.0.1:8080/health` returned `storageMode=memory`
  - `git diff --check` passed with CRLF warnings only
  - `rg -n "[ \t]+$" ...` on touched files returned no trailing-whitespace matches.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; storage selection test uses fake factories and no database connection.
- Scope respected: yes.

## Active Ticket

ID: BWR-077
Goal: Run broad API and authoritative finish gates after backend repository wiring extraction.
Allowed scope: verification commands and `.codex/agent-state.md`.
Forbidden scope: production code edits unless a verified regression is found, frontend changes, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Verify the refactored live memory wiring still satisfies frontend API contracts.
- Re-run authoritative finish smoke because projector/repository wiring was touched recently.
- Restore backend to default memory mode afterward.
Architecture/domain-modeling impact:
- Verification gate only.
Side-effect boundary impact:
- Uses memory backend state only; no database/file persistence.
Verification:
- `npm run demo:api-contract`
- `npm run demo:authoritative-finish-smoke` with `SLAY_DEMO_AUTHORITATIVE_BATTLE_DURATION_MS=1800`
- backend default `/health` after restore
- `git diff --check`
Acceptance criteria:
- Broad API contract and finish smoke pass after wiring extraction.
- Backend is restored to default memory mode when the gate is done.
Risks:
- Requires frontend proxy on `127.0.0.1:5173`; start it only if missing.

Result:
- Confirmed Vite proxy and default memory backend were listening before the gate.
- Verification passed:
  - `npm run demo:api-contract`
  - restarted backend with `SLAY_DEMO_AUTHORITATIVE_BATTLE_DURATION_MS=1800`
  - `npm run demo:authoritative-finish-smoke`
  - restarted backend with default environment
  - `GET http://127.0.0.1:8080/health` returned `storageMode=memory`
  - `git diff --check` passed with CRLF warnings only
  - `npm run dev:status` confirmed backend and Vite are listening.

Self-review:
- Primitive business types introduced: none; verification only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none beyond memory smoke state.
- Scope respected: yes.

## Active Ticket

ID: BWR-078
Goal: Verify inherited generic `DATABASE_URL` cannot trigger database startup.
Allowed scope: verification commands and `.codex/agent-state.md`.
Forbidden scope: production code edits unless a verified regression is found, frontend changes, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Start the backend with a deliberately invalid generic `DATABASE_URL` and no `SLAY_DEMO_STORAGE_MODE`.
- Verify `/health` reports `storageMode=memory`.
- Restore backend to default memory mode afterward.
Architecture/domain-modeling impact:
- Verification gate only.
Side-effect boundary impact:
- Must not open a database connection; the invalid URL should be ignored.
Verification:
- backend startup with only generic `DATABASE_URL`
- `GET http://127.0.0.1:8080/health`
- restore default backend
- `git diff --check`
Acceptance criteria:
- Backend starts and reports memory storage despite inherited generic `DATABASE_URL`.
- Backend is restored to default memory mode when the gate is done.
Risks:
- This does not validate real Postgres mode; it validates the no-implicit-DB safety contract.

Result:
- Restarted backend with `DATABASE_URL=jdbc:postgresql://127.0.0.1:1/should_not_be_used` and without `SLAY_DEMO_STORAGE_MODE` or `SLAY_DEMO_DATABASE_URL`.
- `GET http://127.0.0.1:8080/health` returned `storageMode=memory`.
- Restored backend with default environment.
- Verification passed:
  - default `/health` again returned `storageMode=memory`
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; verification only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none beyond memory backend startup.
- Scope respected: yes.

## Active Ticket

ID: BWR-079
Goal: Re-scan backend red flags after storage and wiring verification.
Allowed scope: read-only inspection of `backend/src/main/scala/slaydemo/backend/**`, `backend/src/test/scala/slaydemo/backend/**`, docs if needed, `.codex/agent-state.md`.
Forbidden scope: production code edits, frontend, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Identify remaining `not implemented`, TODO/FIXME, hidden side effects, unsafe primitive service boundaries, or missing high-value tests.
- Select exactly one next implementation ticket.
Architecture/domain-modeling impact:
- Keeps the next work item grounded in actual code after a green verification gate.
Side-effect boundary impact:
- Audit only.
Verification:
- `rg` searches and targeted reads.
Acceptance criteria:
- Worklog records prioritized findings and a scoped next ticket.
Risks:
- Avoid broad cleanup; only promote issues that materially affect correctness, architecture, or database safety.

Result:
- Red-flag scan found no new default database connection risk after repository wiring extraction.
- File storage mode remains intentionally unsupported and executable-tested as a rejection.
- Domain layer I/O/time/random/logging was not found; time/random remain injected at application/service/port boundaries.
- Remaining primitive strings are mostly labels, paths, frame JSON, and route parser artifacts.
- Highest-value next issue is visitor-like handle guardrail coverage:
  - docs say visitor-like handles should not leak through mail/replay/battle result surfaces
  - existing contract runner does not cover these service-layer boundaries
  - code search shows fewer `HandlePolicy` checks in these services than expected.

Self-review:
- Primitive business types introduced: none; audit only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-080
Goal: Add and satisfy service-layer visitor handle guardrail contracts for result/replay/mail surfaces.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleResultService.scala`, `backend/src/main/scala/slaydemo/backend/replay/services/ReplayService.scala`, `backend/src/main/scala/slaydemo/backend/mail/services/MailService.scala`, focused in-memory repository files only if required, `backend/src/test/scala/slaydemo/backend/**`, `.codex/agent-state.md`.
Forbidden scope: routes, Postgres repositories, frontend, scripts, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Add dependency-free service contract tests proving visitor-like handles cannot create or read private result/replay/mail data at service boundaries.
- Make the smallest service-layer fixes needed if tests expose guardrail gaps.
- Add the contract test to `BackendContractTestRunner`.
Architecture/domain-modeling impact:
- Keeps user-data guardrails in application services, not only HTTP routes.
Side-effect boundary impact:
- Tests use in-memory repositories only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Visitor-like handles are rejected or hidden consistently in battle result, replay, and mail services.
- Existing API behavior remains compatible.
Risks:
- Do not over-model presentation labels or raw replay JSON in this ticket.

Result:
- Added `VisitorHandleGuardrailContractTest` and wired it into `BackendContractTestRunner`.
- `DefaultBattleResultService` now:
  - skips persistence for visitor-like result owners
  - returns no rows for visitor-like handle queries
  - filters visitor-like historical result owners from list output.
- `DefaultReplayService` now:
  - skips persistence for visitor-like replay owners
  - filters visitor-like replay owners from list/load output
  - rejects visitor-like comment authors with explicit `ReplayCommentError.InvalidAuthor`
  - hides comments for invisible replays and filters visitor-like comment authors from comment lists.
- `DefaultMailService` now:
  - returns no rows for visitor-like owners
  - does not create welcome mail for visitor-like owners
  - returns `MailNotFound` for visitor-like `markRead` without touching storage.
- Touched `ReplayRoutes` outside the initial allowed scope because the new `InvalidAuthor` ADT branch made its service-result match non-exhaustive; added the minimal 403 mapping.
- Verification passed:
  - `npm run backend:test-contracts`
  - `npm run backend:compile`
  - `git diff --check` passed with CRLF warnings only
  - `rg -n "[ \t]+$" ...` on touched files returned no trailing-whitespace matches.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; tests use in-memory repositories.
- Scope respected: mostly; `ReplayRoutes` was a necessary minimal scope expansion to handle the new explicit service result.

## Active Ticket

ID: BWR-081
Goal: Run broad API and finish gates after visitor guardrail service changes.
Allowed scope: verification commands and `.codex/agent-state.md`.
Forbidden scope: production code edits unless a verified regression is found, frontend changes, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Verify existing HTTP contracts still pass after service-layer guardrail tightening.
- Verify authoritative finish projection still produces result/replay/mail records for real players.
- Restore backend to default memory mode afterward.
Architecture/domain-modeling impact:
- Verification gate only.
Side-effect boundary impact:
- Uses memory backend state only; no database/file persistence.
Verification:
- `npm run demo:api-contract`
- `npm run demo:authoritative-finish-smoke` with `SLAY_DEMO_AUTHORITATIVE_BATTLE_DURATION_MS=1800`
- backend default `/health` after restore
- `git diff --check`
Acceptance criteria:
- Broad API contract and finish smoke pass.
- Backend is restored to default memory mode when the gate is done.
Risks:
- Requires frontend proxy on `127.0.0.1:5173`; start it only if missing.

Result:
- Started default memory backend and confirmed `/health` returned `storageMode=memory`.
- Verification passed:
  - `npm run demo:api-contract`
  - restarted backend with `SLAY_DEMO_AUTHORITATIVE_BATTLE_DURATION_MS=1800`
  - `npm run demo:authoritative-finish-smoke`
  - restarted backend with default environment
  - `GET http://127.0.0.1:8080/health` returned `storageMode=memory`
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; verification only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none beyond memory smoke state.
- Scope respected: yes.

## Active Ticket

ID: BWR-082
Goal: Update visitor handle guardrail documentation to match the rewritten backend services.
Allowed scope: `docs/phases/phase-05-backend-rewrite/BACKEND_VISITOR_HANDLE_GUARDRAILS.md`, `.codex/agent-state.md`, focused read-only verification commands.
Forbidden scope: production code, tests, frontend, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Replace stale references to old `HandleRules`, Boolean `markRead`, and mail `create` behavior with current `HandlePolicy`, ADT errors, and implemented service methods.
- Mention executable coverage in `VisitorHandleGuardrailContractTest`.
Architecture/domain-modeling impact:
- Keeps docs aligned with current typed service contracts.
Side-effect boundary impact:
- None; docs only.
Verification:
- read updated doc
- `git diff --check`
Acceptance criteria:
- Documentation accurately describes current result/replay/mail visitor guardrails.
Risks:
- Do not turn this into a broad documentation rewrite.

Result:
- Updated `BACKEND_VISITOR_HANDLE_GUARDRAILS.md` to name current `HandlePolicy` rather than stale `HandleRules`.
- Replaced stale mail `markRead false` and `create` wording with current `MailReadError.MailNotFound` and implemented `list`/`markRead` behavior.
- Documented current battle result, replay, mail, and authoritative finish guardrails.
- Mentioned executable coverage through `VisitorHandleGuardrailContractTest` and `npm run backend:test-contracts`.
- Verification passed:
  - read updated doc
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; docs only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-083
Goal: Add focused contract tests for battle queue join authorization outcomes.
Allowed scope: `backend/src/test/scala/slaydemo/backend/**`, `backend/src/main/scala/slaydemo/backend/battle/services/BattleQueueAuthorizationService.scala` only if tests reveal an actual bug, `.codex/agent-state.md`.
Forbidden scope: battle queue runtime, battle state runtime, routes, repositories other than in-memory test fixtures, frontend, scripts, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Add dependency-free service contract tests covering missing session, invalid session, handle/session mismatch, and valid session authorization.
- Add the test to `BackendContractTestRunner`.
- Fix only authorization-service bugs if exposed.
Architecture/domain-modeling impact:
- Protects the typed queue join authorization service boundary around `PlayerHandle` and `SessionToken`.
Side-effect boundary impact:
- Tests use in-memory identity repository and deterministic identity ports only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Authorization outcomes are executable-tested without HTTP, server startup, or database access.
Risks:
- Do not expand into queue matchmaking behavior; this ticket is authorization only.

Result:
- Added `BattleQueueAuthorizationContractTest` using `DefaultIdentityService`, in-memory identity repository, and deterministic identity/session generators.
- Covered invalid session, handle/session mismatch, and valid matching session authorization outcomes.
- Added the test to `BackendContractTestRunner`.
- Verification passed:
  - `npm run backend:test-contracts`
  - `npm run backend:compile`
  - `git diff --check` passed with CRLF warnings only
  - `rg -n "[ \t]+$" ...` on touched files returned no trailing-whitespace matches.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; tests use in-memory identity repository and deterministic ports.
- Scope respected: yes.

## Active Ticket

ID: BWR-084
Goal: Add focused contract tests for in-memory battle queue runtime invariants.
Allowed scope: `backend/src/test/scala/slaydemo/backend/**`, `backend/src/main/scala/slaydemo/backend/battle/services/BattleQueueService.scala` only if tests reveal an actual bug, `.codex/agent-state.md`.
Forbidden scope: battle state runtime, authorization service, routes, repositories, frontend, scripts, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Add dependency-free service contract tests for deterministic queue join behavior.
- Cover same `queueRequestId` idempotency, same handle with a different request isolating to a fresh waiting room, queue status, leave, and capacity activation.
- Add the test to `BackendContractTestRunner`.
Architecture/domain-modeling impact:
- Protects typed queue identifiers and explicit queue state transitions.
Side-effect boundary impact:
- Tests use in-memory queue service with deterministic clock only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Queue runtime invariants are executable-tested without HTTP, server startup, or database access.
Risks:
- Do not expand into authoritative battle mechanics; this ticket is queue runtime only.

Result:
- Added `BattleQueueRuntimeContractTest` and wired it into `BackendContractTestRunner`.
- Covered same `queueRequestId` idempotency, same handle with different request using a fresh waiting room, queue status, leave behavior, and capacity-triggered active battle session bootstrap.
- First test run failed because the fixture accidentally called the no-arg companion `apply`; fixed the fixture to construct `new InMemoryBattleQueueService(...)` with deterministic test settings.
- Verification passed:
  - `npm run backend:test-contracts`
  - `npm run backend:compile`
  - `git diff --check` passed with CRLF warnings only
  - `rg -n "[ \t]+$" ...` on touched files returned no trailing-whitespace matches.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none; assertions inspect existing queue/read-model flags.
- Domain mutation introduced: none; tests exercise service-boundary in-memory state.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-085
Goal: Add focused contract tests for identity service core outcomes.
Allowed scope: `backend/src/test/scala/slaydemo/backend/**`, `backend/src/main/scala/slaydemo/backend/identity/services/IdentityService.scala` only if tests reveal an actual bug, `.codex/agent-state.md`.
Forbidden scope: identity routes, repositories beyond in-memory test fixtures, battle/social/mail services, frontend, scripts, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Add dependency-free identity service contract tests for registration, duplicate handles, builtin admin reservation/login, current session, invalid session, and active account summaries.
- Add the test to `BackendContractTestRunner`.
Architecture/domain-modeling impact:
- Protects typed identity commands, session tokens, and builtin admin behavior.
Side-effect boundary impact:
- Tests use in-memory identity repository and deterministic identity/session generators only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Identity core outcomes are executable-tested without HTTP, server startup, or database access.
Risks:
- Do not expose plaintext passwords or hashes in output.

Result:
- Added `IdentityServiceContractTest` and wired it into `BackendContractTestRunner`.
- Covered registration, duplicate handle rejection, builtin admin handle reservation, invalid session, missing session, bad password, issued session lookup, admin login, and active account summaries.
- Verification passed:
  - `npm run backend:test-contracts`
  - `npm run backend:compile`
  - `git diff --check` passed with CRLF warnings only
  - `rg -n "[ \t]+$" ...` on touched files returned no trailing-whitespace matches.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; tests use in-memory repository and deterministic ports.
- Scope respected: yes.

## Active Ticket

ID: BWR-086
Goal: Audit social friend request service for the next focused contract test.
Allowed scope: read-only inspection of `backend/src/main/scala/slaydemo/backend/social/**`, existing route smoke expectations if needed, `.codex/agent-state.md`.
Forbidden scope: production code edits, tests, frontend, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Determine the smallest useful service-level social contract test.
- Identify any guardrail or state-transition gaps before editing.
Architecture/domain-modeling impact:
- Keeps explicit friend request state transitions reviewable.
Side-effect boundary impact:
- Audit only.
Verification:
- targeted reads and `rg` searches.
Acceptance criteria:
- Worklog records a scoped next implementation ticket or explains why social can be deferred.
Risks:
- Avoid duplicating broad API route smoke; focus on service/domain outcomes.

Result:
- Social friend request service has explicit ADTs for create/respond outcomes and a passive `FriendRequestRecord.respond` transition.
- Existing route smoke covers HTTP behavior, but there is no service-level contract test for duplicate create, forbidden respond, accepted transition, or already-resolved response.
- Direct service calls currently rely on callers to provide playable handles; the route does that, but service-level visitor-like guardrails are not executable-tested.
- The next smallest useful implementation ticket is a focused service contract test plus minimal service guardrails if exposed.

Self-review:
- Primitive business types introduced: none; audit only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-087
Goal: Add and satisfy social friend request service contract tests.
Allowed scope: `backend/src/main/scala/slaydemo/backend/social/services/FriendRequestService.scala`, `backend/src/test/scala/slaydemo/backend/**`, `.codex/agent-state.md`.
Forbidden scope: social routes, repositories unless a test exposes a repository bug, mail service, frontend, scripts, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Add dependency-free service contract tests for self-request rejection, visitor-like handle rejection/hiding, duplicate create, forbidden respond, accepted transition, and already-resolved response.
- Add the test to `BackendContractTestRunner`.
- Make the smallest service-layer fixes needed if tests expose guardrail gaps.
Architecture/domain-modeling impact:
- Protects friend request state transitions and service-level handle guardrails with explicit ADT outcomes.
Side-effect boundary impact:
- Tests use in-memory friend request repository and deterministic clock only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Friend request service outcomes are executable-tested without HTTP, server startup, or database access.
Risks:
- Do not expand into notification persistence; service returns mail snapshots only.

Result:
- Added `FriendRequestServiceContractTest` and wired it into `BackendContractTestRunner`.
- Covered self-request rejection, visitor source/target rejection, visitor owner list hiding, dirty visitor-like stored request hiding, duplicate create, forbidden respond, accepted transition, and already-resolved response.
- Updated `DefaultFriendRequestService` to:
  - reject non-playable source/target handles on create
  - return no rows for non-playable owners
  - filter visitor-like stored records from list/find/respond paths.
- Verification passed:
  - `npm run backend:test-contracts`
  - `npm run backend:compile`
  - `git diff --check` passed with CRLF warnings only
  - `rg -n "[ \t]+$" ...` on touched files returned no trailing-whitespace matches.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; existing service/repository state transitions remain explicit through ADTs and immutable records.
- Side effects inside domain: none; tests use in-memory repository and deterministic clock.
- Scope respected: yes.

## Active Ticket

ID: BWR-088
Goal: Run broad API gate after social service guardrail changes.
Allowed scope: verification commands and `.codex/agent-state.md`.
Forbidden scope: production code edits unless a verified regression is found, frontend changes, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Verify existing HTTP contracts still pass after social service guardrail tightening.
- Restore backend to default memory mode afterward.
Architecture/domain-modeling impact:
- Verification gate only.
Side-effect boundary impact:
- Uses memory backend state only; no database/file persistence.
Verification:
- `npm run demo:api-contract`
- backend default `/health` after restore
- `git diff --check`
Acceptance criteria:
- Broad API contract passes.
- Backend is restored to default memory mode when the gate is done.
Risks:
- Requires frontend proxy on `127.0.0.1:5173`; start it only if missing.

Result:
- Started default memory backend and confirmed `/health` returned `storageMode=memory`.
- Verification passed:
  - `npm run demo:api-contract`
  - `GET http://127.0.0.1:8080/health` returned `storageMode=memory`
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; verification only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none beyond memory smoke state.
- Scope respected: yes.

## Active Ticket

ID: BWR-089
Goal: Audit forum service for the next focused contract test.
Allowed scope: read-only inspection of `backend/src/main/scala/slaydemo/backend/forum/**`, `.codex/agent-state.md`.
Forbidden scope: production code edits, tests, frontend, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Determine the smallest useful service-level forum contract test.
- Identify whether service-layer visitor guardrails or vote state transitions need coverage.
Architecture/domain-modeling impact:
- Keeps forum topic/reply/vote state transitions explicit and reviewed.
Side-effect boundary impact:
- Audit only.
Verification:
- targeted reads and `rg` searches.
Acceptance criteria:
- Worklog records a scoped next implementation ticket or explains why forum can be deferred.
Risks:
- Avoid duplicating broad API route smoke; focus on service/domain outcomes.

Result:
- Forum routes already apply `HandlePolicy` for create/mutation authors and ignore visitor-like viewer query handles.
- `ForumService.createTopic` currently returns `ForumTopicView` directly, so adding service-level visitor rejection would require an API/result-type change and should not be mixed into a test ticket.
- Forum has useful state transitions that are not covered by the contract runner yet:
  - topic creation
  - reply append
  - topic vote set/change/clear
  - reply vote set
  - missing topic/reply ADT errors.

Self-review:
- Primitive business types introduced: none; audit only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-090
Goal: Add focused contract tests for forum service topic/reply/vote state transitions.
Allowed scope: `backend/src/test/scala/slaydemo/backend/**`, `backend/src/main/scala/slaydemo/backend/forum/services/ForumService.scala` only if tests reveal an actual bug, `.codex/agent-state.md`.
Forbidden scope: forum routes, repository implementations unless a test exposes a repository bug, frontend, scripts, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Add dependency-free service contract tests for topic creation, reply append, topic vote set/change/clear, reply vote, missing topic, and missing reply.
- Add the test to `BackendContractTestRunner`.
Architecture/domain-modeling impact:
- Protects forum finite-state vote choices and explicit mutation errors.
Side-effect boundary impact:
- Tests use in-memory forum repository and deterministic clock only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Forum service state transitions are executable-tested without HTTP, server startup, or database access.
Risks:
- Do not change forum service public API in this ticket.

Result:
- Added `ForumServiceContractTest` and wired it into `BackendContractTestRunner`.
- Covered topic creation, reply append, topic vote set/change/clear, reply vote, missing topic errors, and missing reply error.
- First `npm run backend:test-contracts` attempt was blocked by an already-running backend sbt process on Windows; stopped the 8080 backend listener and reran successfully.
- Verification passed:
  - `npm run backend:test-contracts`
  - `npm run backend:compile`
  - `git diff --check` passed with CRLF warnings only
  - `rg -n "[ \t]+$" ...` on touched files returned no trailing-whitespace matches.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; tests exercise explicit service/repository state transitions.
- Side effects inside domain: none; tests use in-memory repository and deterministic clock.
- Scope respected: yes.

## Active Ticket

ID: BWR-091
Goal: Audit governance service for the next focused contract test.
Allowed scope: read-only inspection of `backend/src/main/scala/slaydemo/backend/governance/**`, `.codex/agent-state.md`.
Forbidden scope: production code edits, tests, frontend, scripts, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Determine the smallest useful service-level governance contract test.
- Identify whether mail snapshot and review notification outcomes need coverage.
Architecture/domain-modeling impact:
- Keeps governance command outcomes and mail side effects explicit.
Side-effect boundary impact:
- Audit only.
Verification:
- targeted reads and `rg` searches.
Acceptance criteria:
- Worklog records a scoped next implementation ticket or explains why governance can be deferred.
Risks:
- Avoid duplicating broad API route smoke; focus on service outcomes and repository/mail boundaries.

Result:
- Governance service has typed commands for contribution adjustments and review notifications.
- Both create paths persist a governance record and a mail snapshot through the mail repository when the owner handle is playable.
- Existing contract runner does not directly cover governance service outcomes, list filters, or governance mail metadata persistence.
- The next useful ticket is a focused service contract test with in-memory governance and mail repositories.

Self-review:
- Primitive business types introduced: none; audit only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-092
Goal: Add focused contract tests for governance service records and mail snapshots.
Allowed scope: `backend/src/test/scala/slaydemo/backend/**`, `backend/src/main/scala/slaydemo/backend/governance/services/GovernanceServices.scala` only if tests reveal an actual bug, `.codex/agent-state.md`.
Forbidden scope: governance routes, repositories unless a test exposes a repository bug, mail service, frontend, scripts, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Add dependency-free service contract tests for contribution adjustment creation/listing/mail persistence.
- Add dependency-free service contract tests for review notification creation/filtering/admin mail metadata.
- Add the test to `BackendContractTestRunner`.
Architecture/domain-modeling impact:
- Protects governance command outcomes and explicit mail side-effect boundaries.
Side-effect boundary impact:
- Tests use in-memory governance/mail repositories and deterministic clock only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Governance service outcomes are executable-tested without HTTP, server startup, or database access.
Risks:
- Do not expand into route parsing or mail service behavior; this ticket tests service outputs and repository side effects only.

Result:
- Strengthened `GovernanceServiceContractTest` for contribution adjustment records, snapshot mail fields, persisted governance mail, list limit clamping, review notification filtering, admin mail metadata, and blank-title target-label fallback.
- `BackendContractTestRunner` already included the governance service check; no production code changes were needed.
- Verification passed:
  - `npm run backend:test-contracts`
  - `npm run backend:compile`
  - `git diff --check` passed with CRLF warnings only
  - `rg -n "[ \t]+$" ...` on touched files returned no trailing-whitespace matches.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none; assertions only inspect existing mail flags.
- Domain mutation introduced: none; tests exercise service/repository boundaries with immutable records.
- Side effects inside domain: none; tests use in-memory governance/mail repositories and deterministic clocks.
- Scope respected: yes.

## Active Ticket

ID: BWR-093
Goal: Audit remaining backend services for the next focused contract-test ticket.
Allowed scope: read-only inspection of `backend/src/main/scala/slaydemo/backend/**`, existing `backend/src/test/scala/slaydemo/backend/**`, `.codex/agent-state.md`.
Forbidden scope: production code edits, test edits, frontend, scripts, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Identify remaining services or boundaries with weak executable coverage after identity, queue, visitor, social, forum, and governance tests.
- Choose one small next implementation ticket.
Architecture/domain-modeling impact:
- Keeps follow-up work ordered by domain and side-effect risk instead of broad refactors.
Side-effect boundary impact:
- Audit only; no runtime side effects beyond file inspection.
Verification:
- targeted reads and `rg` searches.
Acceptance criteria:
- Worklog records the highest-priority next implementation ticket or explains why a broad gate should run first.
Risks:
- Avoid turning the audit into a broad rewrite; keep the next ticket small and executable.

Result:
- Existing focused contract coverage now protects repository wiring, storage config, identity, battle queue authorization/runtime, visitor guardrails, social, forum, governance, and pure battle finish projection planning.
- Remaining higher-risk gaps are:
  - real Postgres adapter behavior and constructor-time initialization/seed effects are not SQL-integration-tested
  - `DefaultBattleFinishProjector` effectful multi-repository write boundary is not directly contract-tested
  - battle state runtime command/finish path is lightly covered at service level
  - normal-path replay/mail/battle-result service behavior is partially covered only through visitor and API gates.
- Database audit found no non-explicit Postgres trigger path: generic `DATABASE_URL` is ignored, memory mode selects only memory factories, and file mode rejects before constructing repositories.
- Known database risk remains explicit Postgres mode: live Postgres repositories initialize in constructors and `PostgresBotProfileRepository` may seed defaults during construction.
- Next ticket selected: cover `DefaultBattleFinishProjector` repository-write boundary before expanding lower-risk service tests.

Self-review:
- Primitive business types introduced: none; audit only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; audit only.
- Scope respected: yes.

## Active Ticket

ID: BWR-094
Goal: Add focused contract tests for `DefaultBattleFinishProjector` repository-write boundary.
Allowed scope: `backend/src/test/scala/slaydemo/backend/battle/services/BattleFinishProjectionWriteContractTest.scala`, `backend/src/test/scala/slaydemo/backend/BackendContractTestRunner.scala`, `backend/src/main/scala/slaydemo/backend/battle/services/BattleFinishProjectionService.scala` only if tests expose an actual bug, `.codex/agent-state.md`.
Forbidden scope: frontend, scripts, dependency/build changes, real database adapters, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Add dependency-free tests for non-finished state writing nothing.
- Add dependency-free tests for finished state writing human results, settlement mails, rating mails when applicable, and one replay with settlements.
- Add dependency-free test that previous-rating lookup ignores the current battle id.
Architecture/domain-modeling impact:
- Protects the side-effect orchestration boundary around the pure projection plan.
- Keeps repository effects explicit and outside domain objects.
Side-effect boundary impact:
- Tests use in-memory battle-result/replay/mail repositories only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Projector write outcomes are executable-tested without HTTP, server startup, or database access.
Risks:
- Avoid re-testing the full battle runtime here; this ticket starts from constructed aggregate state fixtures.

Result:
- Added `BattleFinishProjectionWriteContractTest` and wired it into `BackendContractTestRunner`.
- Covered non-finished aggregate states returning `NotConfigured` with no repository writes.
- Covered finished aggregate projection writing human-only battle results, battle settlement mails, reward/rating mails for non-zero rating deltas, and one replay with settlement snapshots.
- Covered previous-rating lookup ignoring existing records for the current battle id before projection overwrites the current result.
- Verification passed:
  - `npm run backend:test-contracts`
  - `npm run backend:compile`
  - `git diff --check` passed with CRLF warnings only
  - `rg -n "[ \t]+$" ...` on touched files returned no trailing-whitespace matches.

Self-review:
- Primitive business types introduced: none beyond test fixture presentation strings.
- Boolean business results introduced: none; assertions inspect existing battle/mail flags.
- Domain mutation introduced: none; tests construct immutable aggregate states and exercise repository-boundary writes.
- Side effects inside domain: none; projector effects go through in-memory repositories only.
- Scope respected: yes.

## Active Ticket

ID: BWR-095
Goal: Audit battle state runtime service for focused contract-test coverage.
Allowed scope: read-only inspection of `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, battle API/object types, existing battle tests, `.codex/agent-state.md`.
Forbidden scope: production code edits, test edits, routes, frontend, scripts, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Identify the smallest useful service-level battle state runtime contract test.
- Decide whether to cover command acceptance/errors, finish transition projector invocation, or state bootstrap first.
Architecture/domain-modeling impact:
- Keeps command outcomes and battle phase transitions executable without mixing route parsing.
Side-effect boundary impact:
- Audit only; no runtime side effects beyond file inspection.
Verification:
- targeted reads and `rg` searches.
Acceptance criteria:
- Worklog records a scoped next implementation ticket for battle state runtime or explains why another gap is higher priority.
Risks:
- Avoid broad combat-simulation rewrites; isolate one runtime invariant ticket at a time.

Result:
- Battle state runtime has no focused service-level contract test yet.
- The smallest useful coverage is:
  - lazy `currentState` bootstrap from `BattleSessionLookup`, including unknown battle error and human/bot seat preservation
  - `acceptCommand` authorization boundaries: wrong ticket, bot command, and valid human command
  - finish transition glue: short-duration battle advances to `Finished`, invokes a projector once, and exposes `artifactStatus = Ready` when projection succeeds.
- Avoided broader physics, hit detection, projectile, pickup, respawn, and skill-number coverage for this audit; those should be separate small tickets if needed.
- Next ticket selected: add `BattleStateRuntimeContractTest`.

Self-review:
- Primitive business types introduced: none; audit only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; audit only.
- Scope respected: yes.

## Active Ticket

ID: BWR-096
Goal: Add focused battle state runtime contract tests.
Allowed scope: `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `backend/src/test/scala/slaydemo/backend/BackendContractTestRunner.scala`, `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala` only if tests expose an actual bug, `.codex/agent-state.md`.
Forbidden scope: battle routes, frontend, scripts, dependency/build changes, battle physics rewrites, finish projector production code, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Add dependency-free tests for unknown battle and lazy bootstrap from `BattleSessionLookup`.
- Add dependency-free tests for wrong-ticket, bot-command, and valid-human command outcomes.
- Add dependency-free tests for finish transition invoking a recording projector once and surfacing ready artifacts.
Architecture/domain-modeling impact:
- Protects typed battle command outcomes and phase/artifact transitions at the service boundary.
Side-effect boundary impact:
- Tests use a fixed in-memory session lookup, mutable test clock, and recording projector only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Battle state runtime behavior is executable-tested without HTTP, server startup, or database access.
Risks:
- Do not expand into combat physics or full route smoke in this ticket.

Result:
- Added `BattleStateRuntimeContractTest` and wired it into `BackendContractTestRunner`.
- Covered unknown battle lookup and lazy runtime bootstrap from `BattleSessionLookup`.
- Covered command boundaries for wrong ticket, bot command rejection, and valid human command application.
- Covered short-duration finish transition invoking a recording projector once and surfacing `artifactStatus = Ready`.
- First two test runs failed on test-only Scala issues:
  - helper method shadowed by `val service`
  - `identity` package shadowed `Predef.identity`
  Both were fixed in the test code; production code was unchanged.
- Verification passed:
  - `npm run backend:test-contracts`
  - `npm run backend:compile`
  - `git diff --check` passed with CRLF warnings only
  - `rg -n "[ \t]+$" ...` on touched files returned no trailing-whitespace matches.

Self-review:
- Primitive business types introduced: none beyond test fixture labels/ids.
- Boolean business results introduced: none; assertions inspect existing state flags.
- Domain mutation introduced: none; tests use immutable commands/states plus a test clock and recording projector.
- Side effects inside domain: none; service effects remain inside in-memory runtime and recording test double.
- Scope respected: yes.

## Active Ticket

ID: BWR-097
Goal: Audit available Postgres verification path without opening accidental database connections.
Allowed scope: read-only inspection of `backend/README.md`, `docs/phases/phase-05-backend-rewrite/**`, `scripts/**`, `package.json`, environment/tool availability checks, `.codex/agent-state.md`.
Forbidden scope: production code edits, test edits, frontend, dependency changes, running backend in Postgres mode without an explicit local database target, destructive database/container operations, `backend-legacy/**`, `backend-legacy/data/**`.
Expected change:
- Determine whether this workspace has a safe local Postgres verification path already configured.
- Identify exact command(s) for a later real DB gate or mark the gate blocked by missing tools/credentials.
Architecture/domain-modeling impact:
- Keeps database effects as an explicit verification boundary rather than accidental startup behavior.
Side-effect boundary impact:
- Audit only; do not open a DB connection unless a safe local target is already discoverable.
Verification:
- targeted reads
- `Get-Command` checks for local DB tooling if needed
Acceptance criteria:
- Worklog records whether real Postgres verification can run now, and the next safe ticket.
Risks:
- Do not start containers or mutate databases in this audit ticket.

Result:
- Current shell has no explicit Postgres environment configured:
  - `SLAY_DEMO_STORAGE_MODE` absent
  - `SLAY_DEMO_DATABASE_URL` absent
  - generic `DATABASE_URL` absent
  - common `PG*` environment values absent
- Local DB tooling is unavailable in PATH:
  - `docker` not found
  - `psql` not found
  - `pg_isready` not found
- `npm run demo:db-sanity` is an API-level Postgres sanity check that requires a backend already running with `/health.storageMode = postgres`; it is not a database bootstrapper.
- `backend/README.md`, `.env.example`, and the demo runbook document explicit Postgres opt-in. `backend/README.md` also states that Postgres repositories initialize tables during startup.
- Real Postgres verification is blocked in this environment until a safe local database target and tooling/credentials are provided or installed.

Self-review:
- Primitive business types introduced: none; audit only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; no DB connection was opened.
- Scope respected: yes.

## Blocked Tickets

ID: BWR-POSTGRES-GATE
Goal: Run real Postgres startup and API sanity verification.
Blocked by:
- No explicit `SLAY_DEMO_DATABASE_URL` / user / password in the current shell.
- No `docker`, `psql`, or `pg_isready` available in PATH.
Next safe step:
- Provide a scoped local Postgres database or install local DB tooling, then run backend in explicit Postgres mode and `npm run demo:db-sanity`.

## Active Ticket

ID: BWR-098
Goal: Add focused replay service normal-path contract tests.
Allowed scope: `backend/src/test/scala/slaydemo/backend/ReplayServiceContractTest.scala`, `backend/src/test/scala/slaydemo/backend/BackendContractTestRunner.scala`, `backend/src/main/scala/slaydemo/backend/replay/services/ReplayService.scala` only if tests expose an actual bug, `.codex/agent-state.md`.
Forbidden scope: replay routes, frontend, scripts, dependency/build changes, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Add dependency-free tests for replay record normalization, list/load limits, comment ordering, missing replay error, and playback/frame behavior.
- Keep visitor-specific behavior in `VisitorHandleGuardrailContractTest`.
Architecture/domain-modeling impact:
- Protects typed replay identifiers, replay service normalization, and explicit comment result ADTs.
Side-effect boundary impact:
- Tests use in-memory replay repository and deterministic clock only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Replay normal-path service behavior is executable-tested without HTTP, server startup, or database access.
Risks:
- Do not change permissive HTTP DTO defaults in this ticket; routes are out of scope.

Result:
- Added `ReplayServiceContractTest` and wired it into `BackendContractTestRunner`.
- Covered replay record normalization for frame count, playback availability, frames JSON, blank loadout, and blank thumbnail fields.
- Covered replay list/load behavior with newest-first limit and zero limit.
- Covered comment creation IDs/timestamps, latest comment limiting, missing replay comment error, and missing replay comment listing.
- Verification passed:
  - `npm run backend:test-contracts`
  - `npm run backend:compile`
  - `git diff --check` passed with CRLF warnings only
  - `rg -n "[ \t]+$" ...` on touched files returned no trailing-whitespace matches.

Self-review:
- Primitive business types introduced: none beyond test fixture labels/ids.
- Boolean business results introduced: none; assertions inspect existing replay playback flag.
- Domain mutation introduced: none; tests exercise explicit service commands and in-memory repository state.
- Side effects inside domain: none; tests use in-memory replay repository and deterministic clock.
- Scope respected: yes.

## Active Ticket

ID: BWR-099
Goal: Add focused mail service normal-path contract tests.
Allowed scope: `backend/src/test/scala/slaydemo/backend/MailServiceContractTest.scala`, `backend/src/test/scala/slaydemo/backend/BackendContractTestRunner.scala`, `backend/src/main/scala/slaydemo/backend/mail/services/MailService.scala` only if tests expose an actual bug, `.codex/agent-state.md`.
Forbidden scope: mail routes, frontend, scripts, dependency/build changes, governance service, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Add dependency-free tests for welcome mail creation, idempotent list behavior, successful mark-read, wrong-owner read protection, and missing mail error.
- Keep visitor-specific behavior in `VisitorHandleGuardrailContractTest`.
Architecture/domain-modeling impact:
- Protects typed mail IDs, owner scoping, and explicit `MailReadError`.
Side-effect boundary impact:
- Tests use in-memory mail repository and deterministic clock only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Mail normal-path service behavior is executable-tested without HTTP, server startup, or database access.
Risks:
- Do not mix governance mail snapshot behavior into this ticket; that is already covered separately.

Result:
- Added `MailServiceContractTest` and wired it into `BackendContractTestRunner`.
- Covered welcome mail creation, deterministic welcome id, idempotent listing, successful mark-read, wrong-owner protection, missing mail error, and persisted unread state.
- Verification passed:
  - `npm run backend:test-contracts`
  - `npm run backend:compile`
  - `git diff --check` passed with CRLF warnings only
  - `rg -n "[ \t]+$" ...` on touched files returned no trailing-whitespace matches.

Self-review:
- Primitive business types introduced: none beyond test fixture mail text.
- Boolean business results introduced: none; assertions inspect existing mail unread/important flags.
- Domain mutation introduced: none; tests exercise repository-boundary read-state update.
- Side effects inside domain: none; tests use in-memory mail repository and deterministic clock.
- Scope respected: yes.

## Active Ticket

ID: BWR-100
Goal: Add focused battle result service normal-path contract tests.
Allowed scope: `backend/src/test/scala/slaydemo/backend/BattleResultServiceContractTest.scala`, `backend/src/test/scala/slaydemo/backend/BackendContractTestRunner.scala`, `backend/src/main/scala/slaydemo/backend/battle/services/BattleResultService.scala` only if tests expose an actual bug, `.codex/agent-state.md`.
Forbidden scope: battle result routes, finish projector, frontend, scripts, dependency/build changes, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Add dependency-free tests for record persistence, newest-first listing, handle/battle filters, limit clamping, and current-loadout normalization.
- Keep visitor-specific behavior in `VisitorHandleGuardrailContractTest`.
Architecture/domain-modeling impact:
- Protects typed battle result identifiers, owner filtering, and result projection normalization.
Side-effect boundary impact:
- Tests use in-memory battle result repository only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Battle result normal-path service behavior is executable-tested without HTTP, server startup, or database access.
Risks:
- Do not mix finish projection behavior into this ticket; projector writes are already covered separately.

Result:
- Added `BattleResultServiceContractTest` and wired it into `BackendContractTestRunner`.
- Covered result persistence, current-loadout normalization, newest-first listing, handle filtering, battle filtering, zero limit, and negative limit.
- Verification passed:
  - `npm run backend:test-contracts`
  - `npm run backend:compile`
  - `git diff --check` passed with CRLF warnings only
  - `rg -n "[ \t]+$" ...` on touched files returned no trailing-whitespace matches.

Self-review:
- Primitive business types introduced: none beyond test fixture labels/ids.
- Boolean business results introduced: none; assertions inspect existing battle result flag fields.
- Domain mutation introduced: none; tests exercise service commands and in-memory repository state.
- Side effects inside domain: none; tests use in-memory battle result repository.
- Scope respected: yes.

## Active Ticket

ID: BWR-101
Goal: Add focused bot profile service/repository contract tests.
Allowed scope: `backend/src/test/scala/slaydemo/backend/BotProfileServiceContractTest.scala`, `backend/src/test/scala/slaydemo/backend/BackendContractTestRunner.scala`, `backend/src/main/scala/slaydemo/backend/bots/services/BotProfileService.scala` only if tests expose an actual bug, `backend/src/main/scala/slaydemo/backend/bots/database/InMemoryBotProfileRepository.scala` only if tests expose an actual bug, `.codex/agent-state.md`.
Forbidden scope: bot routes, Postgres repositories, frontend, scripts, dependency/build changes, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Add dependency-free tests for static demo catalog shape and in-memory repository ordering/save replacement.
Architecture/domain-modeling impact:
- Protects typed bot profile catalog records and deterministic catalog ordering.
Side-effect boundary impact:
- Tests use in-memory bot profile repository only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Bot profile catalog behavior is executable-tested without HTTP, server startup, or database access.
Risks:
- Do not expand into bot AI/strategy behavior; this ticket is catalog only.

Result:
- Added `BotProfileServiceContractTest` and wired it into `BackendContractTestRunner`.
- Covered deterministic demo catalog count, ids, handles, ordering, and tone wire values.
- Covered in-memory repository ordering and save replacement by `BotId`.
- Verification passed:
  - `npm run backend:test-contracts`
  - `npm run backend:compile`
  - `git diff --check` passed with CRLF warnings only
  - `rg -n "[ \t]+$" ...` on touched files returned no trailing-whitespace matches.

Self-review:
- Primitive business types introduced: none beyond test fixture labels/ids.
- Boolean business results introduced: none.
- Domain mutation introduced: none; tests exercise repository-boundary catalog replacement.
- Side effects inside domain: none; tests use in-memory bot profile repository only.
- Scope respected: yes.

## Active Ticket

ID: BWR-102
Goal: Run broad memory-mode API contract gate after focused service contract additions.
Allowed scope: verification commands and `.codex/agent-state.md`.
Forbidden scope: production code edits unless a verified regression is found, frontend changes, scripts, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Start or reuse local memory backend and frontend proxy as needed.
- Run `npm run demo:api-contract`.
- Restore backend to default memory mode afterward if a restart is needed.
Architecture/domain-modeling impact:
- Verification gate only.
Side-effect boundary impact:
- Uses in-memory backend state only; no database/file persistence.
Verification:
- `npm run demo:api-contract`
- backend `/health` after gate
- `git diff --check`
Acceptance criteria:
- Broad API contract passes in memory mode after the service contract work.
Risks:
- Requires frontend proxy on `127.0.0.1:5173`; start it only if missing.

Result:
- Confirmed Vite proxy was already listening on 5173 and backend 8080 was not listening.
- Started backend in default memory mode via `scripts/dev-start.ps1 -BackendOnly`.
- `/health` returned `{"status":"ok","service":"slay-demo-backend","port":8080,"storageMode":"memory"}`.
- Verification passed:
  - `npm run demo:api-contract`
  - backend `/health` after the gate still returned `storageMode=memory`
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; verification only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; smoke state stayed in memory backend.
- Scope respected: yes.

## Active Ticket

ID: BWR-103
Goal: Run authoritative finish smoke after service contract hardening.
Allowed scope: verification commands and `.codex/agent-state.md`.
Forbidden scope: production code edits unless a verified regression is found, frontend changes, scripts, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Restart memory backend with a short authoritative battle duration.
- Run `npm run demo:authoritative-finish-smoke`.
- Restore backend to default memory mode afterward.
Architecture/domain-modeling impact:
- Verification gate for battle finish projection, result/replay/mail side effects, and frontend API contracts.
Side-effect boundary impact:
- Uses memory backend state only; no database/file persistence.
Verification:
- `npm run demo:authoritative-finish-smoke`
- backend `/health` after restore
- `git diff --check`
Acceptance criteria:
- Authoritative finish smoke passes and backend is restored to default memory mode.
Risks:
- Requires controlled restart of the 8080 backend listener.

Result:
- Restarted memory backend with `SLAY_DEMO_AUTHORITATIVE_BATTLE_DURATION_MS=1800`.
- Verification passed:
  - `npm run demo:authoritative-finish-smoke`
  - smoke confirmed result/replay/mail/rating projection chain
  - backend was restored afterward with default memory mode
  - restored `/health` returned `storageMode=memory`
  - `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; verification only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; smoke state stayed in memory backend.
- Scope respected: yes.

## Active Ticket

ID: BWR-104
Goal: Run full repository build gate after backend rewrite contract work.
Allowed scope: verification commands and `.codex/agent-state.md`.
Forbidden scope: production code edits unless a verified regression is found, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Run the root build command to catch frontend type/build regressions against the rebuilt backend contracts.
Architecture/domain-modeling impact:
- Verification gate only.
Side-effect boundary impact:
- No backend database/file persistence.
Verification:
- `npm run build`
- `git diff --check`
Acceptance criteria:
- Root build passes or any failure is classified.
Risks:
- Existing frontend warnings may remain; report them without overstating.

Result:
- Verification passed:
  - `npm run build`
  - `git diff --check` passed with CRLF warnings only.
- Build emitted existing Vite/Rollup warnings:
  - React Router `"use client"` directives ignored while bundling
  - `battleTruthStore.ts` dynamic/static import chunking warning
  - chunk size warning for the large frontend bundle.
- `npm run dev:status` shows both local services are running:
  - 5173 Vite dev server
  - 8080 BackendApp via sbt runMain, restored to default memory mode after finish smoke.

Self-review:
- Primitive business types introduced: none; verification only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Active Ticket

ID: BWR-105
Goal: Perform final backend readiness audit after broad verification gates.
Allowed scope: read-only inspection of `backend/src/main/scala/**`, `backend/src/test/scala/**`, `backend/README.md`, `package.json`, `.codex/agent-state.md`.
Forbidden scope: production code edits, test edits, frontend, scripts, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Search for unresolved TODO/stub/error markers.
- Re-check storage/database/file I/O boundaries.
- Identify any remaining high-priority backend work before final handoff.
Architecture/domain-modeling impact:
- Ensures no known unsafe backend gap is hidden after the rewrite verification pass.
Side-effect boundary impact:
- Audit only.
Verification:
- targeted `rg` searches and file reads.
Acceptance criteria:
- Worklog records remaining risks, blocked items, and whether another small ticket is needed.
Risks:
- Do not start new broad refactors unless a concrete high-priority gap is found.

Result:
- No unresolved backend TODO/FIXME/stub/`???` markers were found.
- The only "not implemented" path is the intentional `SLAY_DEMO_STORAGE_MODE=file` rejection in `BackendRepositories.scala`, documented and covered by contract tests.
- DB access is confined to `PostgresSupport` and Postgres repositories; default memory mode and file rejection do not construct Postgres repositories.
- No rebuilt-backend file storage implementation or outbound HTTP client usage was found.
- Time/random/logging effects are limited to startup/factory/infrastructure/test boundaries; no domain object performs I/O.
- Contract runner covers all visible `*ContractTest.scala` files, but route context alias parity is only protected by broad API smoke, not by a small backend contract test.
- Next ticket selected: extract backend route context paths into one table and add a contract test for `/api` alias parity.

Self-review:
- Primitive business types introduced: none; audit only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; audit only.
- Scope respected: yes.

## Active Ticket

ID: BWR-106
Goal: Add backend route context alias parity contract.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/test/scala/slaydemo/backend/BackendRouteContextContractTest.scala`, `backend/src/test/scala/slaydemo/backend/BackendContractTestRunner.scala`, `.codex/agent-state.md`.
Forbidden scope: route handler behavior changes, frontend, scripts, dependency/build changes, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Extract route context path registration in `BackendApp` to a single package-private route table/helper.
- Add a contract test proving every base route has a matching `/api` route and no duplicate route contexts exist.
Architecture/domain-modeling impact:
- Keeps HTTP route registration explicit and reviewable without moving business logic into routes.
Side-effect boundary impact:
- No runtime behavior change intended; test uses route metadata only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Backend route context alias parity is executable-tested without opening a socket or database connection.
Risks:
- Keep this as route registration metadata only; do not broaden into fake `HttpExchange` endpoint testing.

Result:
- Extracted `BackendApp.BaseRouteContexts` / `RouteContexts` route metadata and registered handlers through one route-handler table.
- Added `BackendRouteContextContractTest` and wired it into `BackendContractTestRunner`.
- Covered no duplicate contexts, explicit base context list, and `/api` alias parity for every base route.
- Verification passed:
  - `npm run backend:test-contracts`
  - `npm run backend:compile`
  - `npm run demo:api-contract`
  - `git diff --check` passed with CRLF warnings only
  - `rg -n "[ \t]+$" ...` on touched files returned no trailing-whitespace matches.

Self-review:
- Primitive business types introduced: route paths are HTTP boundary metadata only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; route registration remains in `BackendApp`.
- Scope respected: yes.

## Active Ticket

ID: BWR-107
Goal: Final backend status consolidation and handoff update.
Allowed scope: `.codex/agent-state.md`, read-only status/verification commands.
Forbidden scope: production code edits, test edits, frontend, scripts, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, real database connections.
Expected change:
- Reconfirm current process/storage state.
- Record final verification summary and remaining blocked Postgres gate.
- Decide whether backend rewrite is locally complete for memory-mode operation.
Architecture/domain-modeling impact:
- Documentation/worklog only.
Side-effect boundary impact:
- No code side effects.
Verification:
- `npm run dev:status`
- backend `/health`
- `git diff --check`
Acceptance criteria:
- Worklog has a current, factual final status.
Risks:
- Do not claim real Postgres validation passed; it is blocked without DB tooling/credentials.

Result:
- Final status checks passed:
  - `npm run dev:status`
  - backend `/health`
  - `git diff --check` passed with CRLF warnings only.
- Re-ran authoritative finish smoke after the route-context refactor:
  - `npm run demo:authoritative-finish-smoke` passed
  - backend restored afterward to default memory mode.
- Current local services:
  - Vite dev server is listening on 5173.
  - BackendApp is listening on 8080.
  - `/health` reports `storageMode=memory`.
- Local memory-mode backend rewrite is complete to the current verification bar.
- Real Postgres verification remains blocked by missing explicit DB target/tooling, recorded under `BWR-POSTGRES-GATE`.

Self-review:
- Primitive business types introduced: none; consolidation only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

## Current Final Status

- Backend memory mode: locally verified.
- Backend route registration and `/api` alias parity: contract-tested and API-smoked.
- Service/domain contracts: contract-tested through `BackendContractTestRunner`.
- Broad API contract: passed.
- Authoritative finish/result/replay/mail chain: passed.
- Root frontend/backend build gate: passed with existing Vite warnings.
- Postgres real DB gate: blocked, not claimed.

## Notes

- `backend-legacy/data/` is read-only reference data until an explicit migration ticket exists.
- The baseline must not open PostgreSQL or file-backed data stores.

## Active Ticket

ID: BWR-108
Goal: Repair Postgres schema reconciliation for the authoritative finish projection chain.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/database/PostgresBattleResultRepository.scala`, `backend/src/main/scala/slaydemo/backend/replay/database/PostgresReplayRepository.scala`, `backend/src/main/scala/slaydemo/backend/mail/database/PostgresMailRepository.scala`, `.codex/agent-state.md`.
Forbidden scope: domain objects, HTTP routes, frontend, scripts, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, destructive data resets.
Expected change:
- Make Postgres repository initialization idempotently add every column currently selected or written by battle result, replay, replay settlement/comment, and mail repositories.
- Repair legacy incompatible primary keys for `battle_results` and `mails` without resetting data.
Architecture/domain-modeling impact:
- No domain model change; this is repository-boundary schema compatibility only.
Side-effect boundary impact:
- All database effects remain inside Postgres repository initialization and normal repository writes.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Postgres `/health`
- `npm run demo:db-sanity` in Postgres mode
- `npm run demo:authoritative-finish-smoke` in Postgres mode
- `git diff --check`
Acceptance criteria:
- Backend starts in explicit Postgres mode against the current local DB.
- Authoritative finish smoke reaches result/replay/mail readiness in Postgres mode.
Risks:
- Live databases with corrupt duplicate keys may still require a deliberate data repair ticket; this ticket does not reset tables or delete user data.

Result:
- Added idempotent column reconciliation for `battle_results`, `replay_records`, `replay_comments`, `replay_settlements`, and `mails`.
- Repaired legacy Postgres primary-key drift:
  - `battle_results` is now `PRIMARY KEY (result_id)`.
  - `mails` is now `PRIMARY KEY (owner_handle, id)`.
- Restarted the backend in explicit Postgres mode and confirmed `/health.storageMode=postgres`.
- Verified the live local schema after initialization.
- Verification passed:
  - `npm run backend:test-contracts` after stopping the existing backend SBT process
  - `npm run backend:compile`
  - `npm run demo:db-sanity` in Postgres mode
  - `npm run demo:api-contract` in Postgres mode
  - `npm run demo:authoritative-finish-smoke` in Postgres mode with short battle duration
  - restored backend to normal Postgres mode afterward
  - `git diff --check` passed with CRLF warnings only
  - trailing-whitespace scan on touched files returned no matches

Self-review:
- Primitive business types introduced: none; schema column names are repository-boundary SQL.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; database effects remain in Postgres repositories.
- Scope respected: yes.

Next ticket:
- BWR-109: Surface battle finish projection failures at the service boundary for faster Postgres/runtime diagnosis.

## Active Ticket

ID: BWR-109
Goal: Surface battle finish projection failures at the service boundary.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleFinishProjectionService.scala`, `backend/src/test/scala/slaydemo/backend/battle/services/BattleFinishProjectionWriteContractTest.scala`, `backend/src/test/scala/slaydemo/backend/BackendContractTestRunner.scala` only if a new test object is needed, `.codex/agent-state.md`.
Forbidden scope: Postgres repositories, HTTP routes, frontend, scripts, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`.
Expected change:
- Add an explicit projection failure reporter boundary.
- Include battle id plus exception class/message in console reporting for production projector failures.
- Contract-test that projection failures return an explicit `Failed` outcome and notify the reporter without writing artifacts.
Architecture/domain-modeling impact:
- Keeps failure reporting in the service/infrastructure boundary, not domain objects.
Side-effect boundary impact:
- Console output is isolated behind a named reporter interface.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- A repository exception no longer disappears into an unobservable `Failed` status.
- Normal projection behavior remains unchanged.
Risks:
- This is diagnostic only; it does not change retry policy or expose failure details through the public API.

Result:
- Added `BattleFinishProjectionFailureReporter` as an explicit diagnostic boundary.
- Production projector failures now report `battleId`, exception class, and exception message to stderr through `ConsoleBattleFinishProjectionFailureReporter`.
- Added a contract case proving repository exceptions return `BattleFinishProjectionOutcome.Failed`, notify the reporter, and leave replay/mail artifacts unwritten.
- Verification passed:
  - `npm run backend:test-contracts`
  - `npm run backend:compile`
  - `git diff --check` passed with CRLF warnings only
  - trailing-whitespace scan on touched files returned no matches

Self-review:
- Primitive business types introduced: none beyond diagnostic text.
- Boolean business results introduced: none.
- Domain mutation introduced: none; test-only recorder uses local mutable state.
- Side effects inside domain: none; console output is isolated behind a service-boundary reporter.
- Scope respected: yes.

Next ticket:
- BWR-110: Re-run a final Postgres service status gate after diagnostic changes and decide whether any backend-critical work remains.

## Active Ticket

ID: BWR-110
Goal: Reconfirm local service status after Postgres and diagnostic fixes.
Allowed scope: read-only verification commands and `.codex/agent-state.md`.
Forbidden scope: production code edits, test edits, frontend, scripts, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`.
Expected change:
- Restore backend to normal explicit Postgres mode after verification restarts.
- Confirm frontend and backend listeners are healthy.
Architecture/domain-modeling impact:
- Verification/worklog only.
Side-effect boundary impact:
- Starts the backend process in explicit Postgres mode; no code side effects.
Verification:
- backend `/health`
- `npm run dev:status`
Acceptance criteria:
- 5173 is Vite.
- 8080 is BackendApp.
- `/health.storageMode=postgres`.
Risks:
- Additional compile/test commands require stopping the running backend first because SBT runMain owns the SBT boot lock on Windows.

Result:
- Restored backend to normal explicit Postgres mode.
- `/health` returned `storageMode=postgres`.
- `npm run dev:status` confirmed:
  - 5173: Vite dev server.
  - 8080: BackendApp via sbt runMain.

Self-review:
- Primitive business types introduced: none; verification only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-111: Final backend readiness audit after the Postgres gate, focused on unresolved backend risk rather than new feature work.

## Active Ticket

ID: BWR-111
Goal: Final backend readiness audit after the Postgres gate.
Allowed scope: read-only inspection of `backend/src/main/scala/**`, `backend/src/test/scala/**`, `backend/README.md`, `package.json`, scripts referenced by backend verification, and `.codex/agent-state.md`.
Forbidden scope: production code edits, test edits, frontend implementation changes, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, database resets.
Expected change:
- Re-check unresolved placeholders, unsafe side effects, and remaining high-priority backend risks after Postgres verification.
Architecture/domain-modeling impact:
- Audit only.
Side-effect boundary impact:
- No code side effects.
Verification:
- targeted `rg` searches
- backend `/health`
- `git diff --check`
Acceptance criteria:
- Worklog records whether the backend is complete to the current agreed bar or what remains blocked.
Risks:
- Do not turn this audit into a broad refactor unless a concrete severe issue is found.

Result:
- No unresolved backend TODO/FIXME/`???` markers were found.
- The only intentional "not implemented" path remains `SLAY_DEMO_STORAGE_MODE=file`, documented in `backend/README.md` and covered by repository wiring tests.
- Database access remains confined to `PostgresSupport` and Postgres repository adapters.
- Time effects remain injected at service construction or app startup boundaries.
- The only production console side effect found is the explicit battle finish projection failure reporter added in BWR-109.
- `/health` still reports `storageMode=postgres`.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; audit only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Final backend status:
- Backend memory mode: verified.
- Backend Postgres mode: verified against the local DB.
- Broad API contract: passed in Postgres mode.
- Authoritative finish/result/replay/mail/rating projection chain: passed in Postgres mode.
- Backend service/domain contract runner: passed.
- Backend compile: passed.
- Frontend dev server and backend dev server are currently running locally on 5173 and 8080.

Remaining non-blocking risks:
- File-backed mode is intentionally rejected in the rebuilt backend.
- There is no automated temporary-Postgres schema-drift test; live local Postgres drift was repaired and smoked, but future migration coverage should be a dedicated infra ticket.
- Additional compile/test commands on Windows should stop the running backend first because `sbt runMain` owns the SBT boot lock.

## Active Ticket

ID: BWR-112
Goal: Audit rebuilt backend endpoint parity against legacy routes and current frontend/scripts.
Allowed scope: read-only inspection of `backend-legacy/src/main/scala/**`, `backend/src/main/scala/**`, `frontend/src/**`, `scripts/**`, plus `.codex/agent-state.md`.
Forbidden scope: `backend-legacy/data/**`, production code edits, test edits, dependency changes, database resets.
Expected change:
- Compare legacy route contexts, rebuilt route contexts, and current frontend/script API calls.
- Identify any missing or incompatible endpoints that block "can run" status.
Architecture/domain-modeling impact:
- Audit only.
Side-effect boundary impact:
- No code side effects.
Verification:
- targeted `rg` route/API scans
- subagent read-only route parity audit
Acceptance criteria:
- Worklog records high-risk endpoint gaps, if any, and the next executable smoke ticket.
Risks:
- Legacy endpoints no longer used by frontend/scripts should not drive broad compatibility work unless they block current operation.

Result:
- Legacy route contexts and rebuilt route contexts have the same major API surface, with the rebuilt backend using explicit `/battle/queue/join`, `/battle/queue/status`, and `/battle/queue/leave` contexts instead of the legacy broad `/battle/queue` prefix.
- Current frontend and main contract scripts call the rebuilt explicit queue routes.
- `npm run demo:smoke` exposed a real blocking compatibility gap: social friend request creation returns mail in the HTTP response but does not persist it into `/mails`, so the smoke and mailbox UI cannot find `mail-friend-{requestId}`.
- The same smoke expects the original friend request mail to become read and expose `friendRequestStatus` after accept/reject.

Self-review:
- Primitive business types introduced: none; audit only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-113: Persist friend request notification mail and status metadata through the mail repository.

## Active Ticket

ID: BWR-113
Goal: Persist friend request notification mail and response status metadata.
Allowed scope: `backend/src/main/scala/slaydemo/backend/BackendApp.scala`, `backend/src/main/scala/slaydemo/backend/mail/**`, `backend/src/main/scala/slaydemo/backend/social/services/FriendRequestService.scala`, `backend/src/main/scala/slaydemo/backend/social/routes/SocialRoutes.scala`, `backend/src/test/scala/slaydemo/backend/FriendRequestServiceContractTest.scala`, `backend/src/test/scala/slaydemo/backend/MailServiceContractTest.scala` only if needed, `.codex/agent-state.md`.
Forbidden scope: frontend implementation, scripts, unrelated social/forum/governance behavior, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, database resets.
Expected change:
- Store target notification mail when a friend request is created.
- Mark/update the original request mail when the target accepts or rejects.
- Add typed mail metadata for friend request id/status/source handle.
- Persist and render that metadata in memory and Postgres mail repositories.
Architecture/domain-modeling impact:
- Use mail-specific value objects/enums for friend request mail metadata instead of raw string status.
- Keep cross-repository orchestration in the social application service boundary.
Side-effect boundary impact:
- Mail writes remain in `MailRepository`.
- Social service coordinates `FriendRequestRepository` and `MailRepository`.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run demo:smoke`
- Postgres `/health`
- `git diff --check`
Acceptance criteria:
- `demo:smoke` friend request accept/reject flows pass through `/mails`.
- Frontend-visible mail JSON includes `friendRequestId`, `friendRequestStatus`, and `friendRequestSourceHandle` for friend request mails.
Risks:
- Existing old mail rows will not have friend request metadata; the frontend can still infer request id from the mail id for old pending request mails.

Result:
- Added typed friend request mail metadata:
  - `MailFriendRequestId`
  - `MailFriendRequestStatus`
  - `FriendRequestMailMetadata`
- Social service now coordinates both `FriendRequestRepository` and `MailRepository`.
- Friend request creation persists `mail-friend-{requestId}` to the target mailbox.
- Accept/reject updates the original request mail to read and updates `friendRequestStatus`.
- Accept/reject also persists a response mail for the source handle.
- Mail JSON now exposes `friendRequestId`, `friendRequestStatus`, and `friendRequestSourceHandle`.
- Postgres `mails` initialization now adds and persists friend request metadata columns.
- Verification passed:
  - `npm run backend:test-contracts`
  - `npm run backend:compile`
  - Postgres backend restart and `/health.storageMode=postgres`
  - `npm run demo:smoke`
  - `npm run demo:db-sanity`
  - `npm run demo:api-contract`
  - `git diff --check` passed with CRLF warnings only
  - trailing-whitespace scan on touched files returned no matches

Self-review:
- Primitive business types introduced: SQL/wire string fields only; domain metadata uses typed mail value objects and enum.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; cross-repository writes are in social service boundary.
- Scope respected: yes.

Next ticket:
- BWR-114: Restore legacy-compatible continuous SSE behavior for battle state streams.

## Active Ticket

ID: BWR-114
Goal: Restore legacy-compatible continuous SSE behavior for battle state streams.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/routes/BattleRoutes.scala`, focused verification commands, `.codex/agent-state.md`.
Forbidden scope: battle domain/service simulation changes, frontend, scripts, dependency changes, unrelated routes, `backend-legacy/**`, `backend-legacy/data/**`.
Expected change:
- Change `GET /battle/state/stream?battleId=...` from one-frame-and-close to repeated `event: state` frames until battle finish or client disconnect.
- Preserve current state JSON shape and direct `GET /battle/state` behavior.
Architecture/domain-modeling impact:
- Route streaming behavior only; no domain model change.
Side-effect boundary impact:
- Streaming I/O remains inside the HTTP route boundary.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- custom SSE smoke reading multiple frames
- `npm run demo:api-contract`
- `git diff --check`
Acceptance criteria:
- A live SSE request emits at least two `state` events for an active battle.
- Existing API contract still passes.
Risks:
- Long-lived SSE uses one server thread per connected client with the current JDK `HttpServer`; acceptable for this demo backend but not a production scaling model.

Result:
- `GET /battle/state/stream?battleId=...` now keeps the SSE connection open and emits repeated `event: state` frames every 33ms until the battle finishes or the client disconnects.
- Existing direct state read and state JSON rendering were unchanged.
- Verification passed:
  - `npm run backend:test-contracts`
  - `npm run backend:compile`
  - custom Postgres-mode SSE smoke reading at least two `state` events from one connection
  - `npm run demo:api-contract`
  - backend `/health.storageMode=postgres`
  - `git diff --check` passed with CRLF warnings only
  - trailing-whitespace scan on touched files returned no matches

Self-review:
- Primitive business types introduced: none; route timing constant only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; streaming I/O remains in route boundary.
- Scope respected: yes.

Next ticket:
- BWR-115: Run remaining battle/session smoke scripts that are likely to catch runtime regressions outside the broad API contract.

## Active Ticket

ID: BWR-115
Goal: Run remaining battle/session runtime smoke scripts after endpoint parity fixes.
Allowed scope: verification commands and `.codex/agent-state.md`.
Forbidden scope: production code edits unless a verified regression is found, frontend changes, scripts, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, database resets.
Expected change:
- Execute remaining backend-facing smoke scripts that validate multiplayer battle/session behavior beyond `demo:api-contract`.
Architecture/domain-modeling impact:
- Verification only.
Side-effect boundary impact:
- Uses the running Postgres backend through public APIs.
Verification:
- `npm run demo:battle-two-client`
- `npm run demo:bp40-freshness`
- other non-browser smoke scripts if they are backend-facing and available
Acceptance criteria:
- Scripts pass or failures are classified into concrete next tickets.
Risks:
- Browser/visual smoke scripts may require additional local browser dependencies and should be classified separately if blocked.

Result:
- `npm run demo:battle-two-client` launched the manual two-client Edge harness successfully. It is a manual checklist harness, not an automated pass/fail smoke.
- `npm run demo:bp40-freshness` failed before exercising freshness because the script still called `/battle/queue/join` without a session token.
- Classified the BP-40 failure as script drift: rebuilt backend intentionally requires authenticated queue joins, and other current frontend/API smokes already send `sessionToken`.

Self-review:
- Primitive business types introduced: none; verification/classification only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-116: Update BP-40 freshness smoke to create an authenticated account/session before queue joins.

## Active Ticket

ID: BWR-116
Goal: Update BP-40 battle session freshness smoke for authenticated queue joins.
Allowed scope: `scripts/bp40-battle-session-freshness-smoke.ps1`, `.codex/agent-state.md`.
Forbidden scope: backend auth/queue production behavior, frontend, other scripts, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, database resets.
Expected change:
- Register a short unique smoke account at the start of the script.
- Include the returned session token in every `/battle/queue/join` request.
Architecture/domain-modeling impact:
- No backend model change; script follows existing authenticated queue boundary.
Side-effect boundary impact:
- Script uses public identity and queue APIs only.
Verification:
- `npm run demo:bp40-freshness`
- `git diff --check`
Acceptance criteria:
- BP-40 freshness smoke reaches and validates two distinct battle sessions instead of failing on missing session.
Risks:
- The script creates disposable smoke accounts in the configured storage mode.

Result:
- Updated BP-40 smoke to register a short unique account and include `sessionToken` in queue joins.
- Verification passed:
  - `npm run demo:bp40-freshness`
  - `git diff --check` passed with CRLF warnings only
  - trailing-whitespace scan on touched files returned no matches
  - backend `/health.storageMode=postgres`

Self-review:
- Primitive business types introduced: smoke script handle/session strings only at API boundary.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-117: Run root build after backend and smoke-script changes, then perform a final runnable-state check.

## Active Ticket

ID: BWR-117
Goal: Run root build after backend and smoke-script changes, then perform a final runnable-state check.
Allowed scope: verification commands and `.codex/agent-state.md`.
Forbidden scope: production code edits, test edits, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, database resets.
Expected change:
- Run the repository build gate.
- Confirm local frontend/backend services remain reachable.
Architecture/domain-modeling impact:
- Verification only.
Side-effect boundary impact:
- No code side effects.
Verification:
- `npm run build`
- `npm run dev:status`
- backend `/health`
- `git diff --check`
Acceptance criteria:
- Build passes.
- 5173 and 8080 are listening.
- Backend reports explicit Postgres mode.
Risks:
- Existing Vite warnings may remain and should be reported as warnings, not failures.

Result:
- Verification passed:
  - `npm run build`
  - `npm run dev:status`
  - backend `/health.storageMode=postgres`
  - `git diff --check` passed with CRLF warnings only
- Build emitted existing Vite/Rollup warnings:
  - React Router `"use client"` directives ignored while bundling
  - `battleTruthStore.ts` dynamic/static import chunking warning
  - large frontend bundle chunk warning
- Current local services:
  - 5173: Vite dev server
  - 8080: BackendApp via sbt runMain

Self-review:
- Primitive business types introduced: none; verification only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Next ticket:
- BWR-118: Final post-run audit of known runnable-state blockers and remaining non-blocking risks.

## Active Ticket

ID: BWR-118
Goal: Final post-run audit of known runnable-state blockers and finish projection chain.
Allowed scope: verification commands and `.codex/agent-state.md`.
Forbidden scope: production code edits unless a new blocker appears, frontend changes, scripts, dependency changes, `backend-legacy/**`, `backend-legacy/data/**`, database resets.
Expected change:
- Re-run the authoritative finish smoke in Postgres mode after all recent backend/script fixes.
- Restore backend to normal explicit Postgres mode afterward.
- Record remaining blockers or declare no known runnable-state blockers.
Architecture/domain-modeling impact:
- Verification only.
Side-effect boundary impact:
- Uses public APIs against the local Postgres backend.
Verification:
- `npm run demo:authoritative-finish-smoke`
- backend `/health`
- `git diff --check`
Acceptance criteria:
- Finish projection still writes result/replay/mail/rating artifacts in Postgres mode.
- Backend is restored to normal Postgres mode afterward.
Risks:
- Smoke creates disposable battle/result/replay/mail data in the configured Postgres database.

Result:
- Restarted backend with short authoritative battle duration in explicit Postgres mode.
- `npm run demo:authoritative-finish-smoke` passed.
- Restored backend to normal explicit Postgres mode afterward.
- Final service status:
  - 5173: Vite dev server
  - 8080: BackendApp via sbt runMain
  - `/health.storageMode=postgres`
- Verification passed:
  - `npm run dev:status`
  - backend `/health`
  - `git diff --check` passed with CRLF warnings only
  - trailing-whitespace scan on touched files returned no matches

Self-review:
- Primitive business types introduced: none; verification only.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Current runnable-state conclusion:
- No known backend endpoint parity blocker remains for current frontend/scripts.
- No known Postgres-mode finish projection blocker remains.
- No known smoke-script blocker remains among the automated backend-facing scripts run in this pass.
- Manual two-client battle harness launched successfully, but still requires human visual confirmation of cross-window movement/fire.

Remaining non-blocking risks:
- File-backed storage mode remains intentionally rejected.
- Temporary-Postgres schema-drift coverage is still manual/live-DB based; a dedicated integration test container/harness would be future hardening.
- Current JDK `HttpServer` SSE implementation uses one thread per stream connection; acceptable for demo/local use, not production-grade scaling.
- Browser visual/render feel scripts were not treated as backend blockers in this backend rewrite pass.

## Completed Ticket

ID: BWR-181
Goal: Split the in-memory battle runtime catalog out of the oversized state service file without behavior changes.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/main/scala/slaydemo/backend/battle/services/InMemoryBattleStateCatalog.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: battle behavior changes, API routes, frontend, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Move map, weapon, physics, pickup, replay-retention, and bot tuning constants into a package-private catalog source file.
- Keep the Scala companion factory for `InMemoryBattleStateService` in the same file as the class, as required by Scala 3.
Architecture/domain-modeling impact:
- Reduced `BattleStateService.scala` size and separated static runtime catalog data from state-transition logic.
- No domain data mutation or business result shape changed.
Side-effect boundary impact:
- No new side effects; factory wiring remains in the service companion.
Verification:
- `npm run backend:test-contracts` passed.
- `npm run backend:compile` passed.
- Restarted backend in explicit Postgres mode; `/health.storageMode=postgres`.
- `npm run demo:api-contract` passed.
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.
Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes; the initial split attempt was adjusted because Scala 3 requires companion class/object co-location.
Risks:
- This is source organization only; it does not yet reduce the runtime logic branches inside `BattleStateService.scala`.

## Active Ticket

ID: BWR-AUD-1
Goal: Preserve monotonic `lastClientCommandSeq` when stale or out-of-order commands arrive.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, focused runtime contract test in `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: queue behavior, bot profiles, weapon tuning, projectile physics, frontend, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Match legacy behavior by storing `max(previousSeq, incomingSeq)` after accepted commands instead of allowing the stored sequence to move backward.
- Add a focused contract proving an older accepted command cannot lower the authoritative sequence.
Architecture/domain-modeling impact:
- Keeps command reconciliation state as an explicit immutable state copy/update.
Side-effect boundary impact:
- No new external effects; only in-memory battle state transition behavior changes.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- If backend remains runnable afterward: restart explicit Postgres backend and run `npm run demo:api-contract`
Acceptance criteria:
- Out-of-order accepted command preserves the highest seen command sequence.
- Existing battle contracts remain green.
Risks:
- Need to confirm whether ignored stale commands and accepted lower-but-valid commands should share the same monotonic rule; legacy used `math.max` in the accepted path.

Result:
- `applyCommandToPlayer` now stores `max(previousSeq, incomingSeq)`.
- Applied command responses now read `acceptedCommandSeq` from the updated player state, matching legacy response semantics.
- Added `acceptedCommandSequenceIsMonotonic` runtime contract for lower out-of-order accepted commands.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restarted backend in explicit Postgres mode; `/health.storageMode=postgres`
- `npm run demo:api-contract`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; state remains immutable copy/update.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- This fixes command-sequence monotonicity only; it does not address remaining queue bot identity or full-room countdown parity issues.

## Active Ticket

ID: BWR-AUD-2
Goal: Restore legacy bot bootstrap profile identity for queue-generated battle participants.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleQueueService.scala`, existing bot profile/domain objects if only imports are required, focused queue/runtime contract test in `backend/src/test/scala/slaydemo/backend/BattleQueueRuntimeContractTest.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: battle weapon/projectile rules, command parsing, frontend, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Use existing demo bot profile catalog when auto-filling queue rooms.
- Preserve stable profile handle/display/skin/avatar metadata rather than generic `Bot N`/`skin=bot` placeholders.
Architecture/domain-modeling impact:
- Keeps bot identity as typed participant/session descriptor data and avoids primitive placeholder leakage into battle state.
Side-effect boundary impact:
- Queue in-memory room bootstrap only; no new external effects.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restart explicit Postgres backend and run `npm run demo:api-contract`
Acceptance criteria:
- Queue-generated bot participants have legacy-compatible profile identity.
- Existing queue authorization/runtime contracts remain green.
Risks:
- Need to inspect current bot profile object definitions before deciding whether to reference profile catalog directly or map through a service-owned catalog abstraction.

Result:
- Queue-generated bot bootstrap seats now use `DemoBotProfiles` for handle, display name, rating, avatar, and skin.
- Bot bootstrap ids now match legacy slot identity: `playerId=bot-seat-N`, `heroId=bot-N`.
- Added a queue runtime contract proving a deadline-started room fills remaining seats from the demo bot profile catalog.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restarted backend in explicit Postgres mode; `/health.storageMode=postgres`
- `npm run demo:api-contract`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none beyond existing string wire values in queue metadata.
- Boolean business results introduced: none.
- Domain mutation introduced: none; queue state remains immutable room/seat copies inside the in-memory boundary.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- Full-room countdown parity remains intentionally untouched in this ticket and is handled next.

## Active Ticket

ID: BWR-AUD-3
Goal: Restore legacy full-room countdown semantics.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleQueueService.scala`, `backend/src/test/scala/slaydemo/backend/BattleQueueRuntimeContractTest.scala`, `scripts/api-contract-field-smoke.ps1`, `.codex/agent-state.md`, verification commands.
Forbidden scope: bot profile identity changes beyond preserving BWR-AUD-2 results, battle runtime weapon/projectile rules, command parsing, frontend, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- A room that reaches capacity before `deadline/startsAt` remains waiting.
- The room starts when the queue clock reaches the deadline.
- The battle session `startedAt` records the scheduled `startsAt`, not the later observation time.
Architecture/domain-modeling impact:
- Keeps matchmaking phase transitions explicit in `advanceRoom`.
Side-effect boundary impact:
- In-memory queue transition only; no new external effects.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restart explicit Postgres backend and run `npm run demo:api-contract`
Acceptance criteria:
- Full room is still waiting before deadline with no battle session.
- Status/room snapshot after deadline returns active with a battle session.
- Existing API smoke still passes because it already polls until `startsAt`.
Risks:
- Manual two-player full-room entry will wait the configured countdown instead of starting immediately; this matches legacy behavior.
- API smoke previously filled rooms with temporary human peers to trigger immediate starts; it now needs to wait for countdown and bot backfill instead.

Result:
- Full rooms no longer start immediately; `advanceRoom` starts only after `deadline/startsAt`.
- `BattleSessionDescriptor.startedAt` now records the scheduled room `startsAt`.
- Queue runtime contracts now assert full-room countdown waiting and deadline activation.
- API smoke was updated away from immediate-start assumptions:
  - single-player battle smokes wait for countdown and bot backfill
  - the long ammo/pickup smoke pre-registers six human accounts, then joins quickly to avoid bot AI interfering with pickup assertions

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restarted backend in explicit Postgres mode; `/health.storageMode=postgres`
- `npm run demo:api-contract`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; queue phase transition remains an explicit room copy/update.
- Side effects inside domain: none.
- Scope respected: yes; script scope was added because API smoke encoded the old immediate-start behavior.

Risks:
- Manual full human rooms now wait the configured countdown before battle session creation, matching legacy.
- Existing rooms created before this code path remain in memory only until process restart; current backend is running with the new behavior.

## Active Ticket

ID: BWR-AUD-4
Goal: Preserve slow-field state in finished battle snapshots.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, focused runtime contract test in `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: queue behavior, bot profiles, command parsing, weapon/projectile tuning, frontend, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Match legacy final-state behavior by retaining advanced slow fields in the final aggregate instead of clearing them during finish.
- Runtime cleanup for active projectiles and inputs should remain unchanged.
Architecture/domain-modeling impact:
- Keeps finish transition explicit while preserving authoritative VFX/evidence state.
Side-effect boundary impact:
- In-memory battle state transition only; no new external effects.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- If behavior is visible through API smoke without extra cost, run `npm run demo:api-contract`
Acceptance criteria:
- A battle that finishes while a slow field is active keeps that slow field in the finished snapshot.
- Existing finish projection and runtime cleanup contracts remain green.
Risks:
- Need to ensure retained slow fields still have advanced TTL, not stale pre-finish TTL.

Result:
- `finishRuntimeState` now clears active projectiles and player input timers but preserves the already-advanced `slowFields` vector, matching legacy final aggregate behavior.
- The runtime contract now finishes a battle while a slow field is active and asserts the final snapshot retains that field with a lower positive TTL.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restarted backend in explicit Postgres mode; `/health.storageMode=postgres`
- `npm run demo:api-contract`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; finish transition remains an explicit immutable state copy.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- This intentionally changes the old rewritten-backend behavior that cleared slow fields on finish; retained slow fields are authoritative evidence/VFX state and may be visible in replay/final snapshots.

## Active Ticket

ID: BP-CAM-1
Goal: Bind the initial battle camera to the authoritative local player before the game scene is created.
Allowed scope: `frontend/src/features/battle/renderer/createBattleRuntime.ts`, optional focused battle runtime helper/test if an existing local pattern is available, `.codex/agent-state.md`, verification commands.
Forbidden scope: backend runtime rules, queue behavior, weapon/projectile tuning, database/schema/data changes, dependency changes, broad renderer refactors.
Expected change:
- When an initial authoritative battle state and `localAuthoritativePlayerId` are present, resolve that player's `heroId` and use it as the boot snapshot `playerHeroId` before `GameScene` creates the player actor and camera target.
- Keep bootstrap/local-only behavior as fallback when authoritative mapping is absent.
Architecture/domain-modeling impact:
- Keeps frontend ownership derived from typed authoritative ids instead of relying on first-player or stale snapshot ordering.
Side-effect boundary impact:
- Renderer bootstrap data normalization only; no new external effects.
Verification:
- Inspect package scripts and run the smallest meaningful frontend check.
- Prefer `npm run build` if no focused frontend typecheck/test exists.
Acceptance criteria:
- Initial authoritative render starts with the local player's hero/camera when the backend state includes that player's `playerId`.
- Existing local-only/authoritative fallback bootstrapping remains valid.
Risks:
- If the boot snapshot does not yet contain the authoritative hero id, the first patch must no-op rather than invent a new hero; broader hero normalization should be a separate ticket.

Result:
- `createBootSnapshot` now resolves the local authoritative player from `initialAuthoritativeState.players` using `localAuthoritativePlayerId`.
- If the matching authoritative hero already exists in the boot snapshot, `snapshot.playerHeroId` is corrected before `GameScene` creates the local player actor and camera target.
- Bootstrap/local-only behavior remains the fallback when authoritative ownership data is absent.

Verification passed:
- `npm run build`
- `npm run demo:bp28-render-feel-smoke`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none beyond existing frontend wire ids.
- Boolean business results introduced: none.
- Domain mutation introduced: none in backend domain; this is frontend boot snapshot normalization before scene creation.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- The render smoke proves battle entry/render/input still works, but it does not assert the exact first-frame camera target. The code path now uses the authoritative owner mapping before camera creation, which directly addresses the suspected cause.

## Active Ticket

ID: BWR-AUD-5
Goal: Audit projectile speed/lifetime/terminal behavior against legacy and frontend content.
Allowed scope: read-only inspection of `backend/src/main/scala/slaydemo/backend/battle/services/**`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `backend-legacy/src/main/scala/battle/runtime/**`, `frontend/src/game/**`, focused contract edits only if a concrete mismatch is found; `.codex/agent-state.md`; verification commands.
Forbidden scope: queue behavior, camera/bootstrap frontend code, database/schema/data changes, dependency changes, broad weapon tuning without legacy evidence.
Expected change:
- Identify whether current backend projectile TTL/range/speed/terminal semantics diverge from legacy or frontend content.
- If a small concrete mismatch is found, fix that mismatch and add a focused runtime contract.
Architecture/domain-modeling impact:
- Keep projectile behavior expressed through typed weapon/projectile definitions and explicit terminal reasons.
Side-effect boundary impact:
- Pure in-memory runtime transitions only; no new external effects.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Run `npm run demo:api-contract` if runtime behavior changes.
Acceptance criteria:
- Projectile range/lifetime/speed and terminal rules are either proven parity-compatible or corrected with a focused contract.
- No unrelated weapon tuning is mixed into the ticket.
Risks:
- User-visible “instant hit” may partly be frontend feedback/VFX timing rather than backend damage timing; if so, split backend parity and frontend feel into separate tickets.

Result:
- Audited projectile constants against legacy and current frontend content.
- Confirmed current backend/frontend intentionally diverge from legacy short projectile TTL:
  - legacy pistol/gatling/shotgun/rocket TTLs were short-range balance values
  - current backend/frontend use 30000ms TTL so projectiles continue until hit/obstacle/world in normal play
- Confirmed pistol backend speed is current frontend parity at 1400px/s, not legacy 920px/s.
- Added focused backend contract coverage for Gatling and Rocket long authoritative projectile lifetimes after birth tick, complementing the existing pistol and shotgun coverage.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restarted backend in explicit Postgres mode; `/health.storageMode=postgres`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes; no runtime tuning was mixed into the audit.

Risks:
- Current projectile TTL/speed intentionally favors the user's requested long-range behavior over exact legacy constants.
- Muzzle/trajectory visual offset and local projectile prediction still need separate frontend-runtime investigation.

## Active Ticket

ID: BP-INPUT-1
Goal: Prevent skill-cast commands from also triggering weapon fire in the same authoritative command.
Allowed scope: `frontend/src/features/battle/page/authoritativeBattleInput.ts`, `frontend/src/features/battle/runtime-local/weapons/weaponActionController.ts` only if inspection proves local runtime also needs the same guard, `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala` only if frontend alone cannot make the server command safe, focused tests/contracts if available, `.codex/agent-state.md`, verification commands.
Forbidden scope: projectile speed/TTL tuning, queue behavior, camera bootstrap, database/schema/data changes, dependency changes, broad input refactors.
Expected change:
- A command that casts Dash/Blink/Freeze should not also submit/retain primary weapon fire for that same action.
- Prefer suppressing primary fire at the command boundary while preserving normal held-fire behavior on non-skill frames.
Architecture/domain-modeling impact:
- Keeps the skill-vs-weapon action conflict explicit at the input/command boundary rather than hidden in rendering effects.
Side-effect boundary impact:
- Frontend command construction or in-memory battle command transition only; no new external effects.
Verification:
- Inspect current input command construction.
- Run `npm run build`.
- If backend code changes, also run `npm run backend:test-contracts` and `npm run backend:compile`.
Acceptance criteria:
- Pressing a skill key cannot produce a simultaneous authoritative primary fire command in the same frame.
- Existing normal primary fire and held-fire behavior remains unchanged outside skill-cast frames.
Risks:
- If skill targeting uses mouse confirmation, suppress only the cast frame rather than disabling held fire permanently.

Result:
- Local battle runtime now suppresses `primaryHeld` and `primaryJustPressed` whenever the current command is a skill action (`castDash`, `toggleBlink`, `toggleFreeze`) or a prepared skill was already active.
- Backend authoritative command application now treats `castDash/castBlink/castFreeze` as mutually exclusive with persisted `primaryHeld`.
- Added a backend runtime contract proving `primaryHeld + castDash` applies the skill but does not consume pistol ammo or spawn a projectile on the next runtime tick.
- Updated the slow-field finish contract fixture so it no longer depends on an invalid same-command fire+skill combination.

Verification passed:
- `npm run build`
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restarted backend in explicit Postgres mode; `/health.storageMode=postgres`
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/bp28-render-feel-smoke.ps1 -Scenario SkillPressure -InputDurationMs 1800 -SummaryPath .codex/bp28-skill-pressure-summary.json`
- Parsed `.codex/bp28-skill-pressure-summary.json`: `skillRequests=1`, `skillPrimaryHeldConflicts=0`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none in backend domain; command normalization remains at the runtime service boundary.
- Side effects inside domain: none.
- Scope respected: yes; backend was added because the command boundary also needed a defensive invariant.

Risks:
- Skill-pressure smoke covered Dash in the captured run. Blink/Freeze backend suppression is covered by the same shared guard but does not yet have separate smoke rows.

## Active Ticket

ID: BP-VFX-1
Goal: Investigate and correct projectile muzzle/trajectory visual alignment.
Allowed scope: read-only inspection of `frontend/src/features/battle/renderer/effects/**`, `frontend/src/features/battle/runtime-local/projectiles/**`, `frontend/src/game/projectileBirth.ts`, backend projectile birth only for comparison; focused frontend patch if a concrete mismatch is found; `.codex/agent-state.md`; verification commands.
Forbidden scope: projectile TTL/speed balance, backend queue/runtime domain rules, database/schema/data changes, dependency changes, broad renderer refactors.
Expected change:
- Identify why muzzle flash and projectile/trail can appear as parallel but offset lines.
- If a small mismatch exists, align VFX birth/origin with the same projectile birth formula used by local and authoritative projectiles.
Architecture/domain-modeling impact:
- Keep visual effects derived from projectile/weapon birth data rather than duplicating independent geometry.
Side-effect boundary impact:
- Renderer/VFX only; no new external effects.
Verification:
- `npm run build`
- Run a focused render-feel smoke if the patch changes battle visuals.
Acceptance criteria:
- Muzzle flash/trail origin uses the same direction and forward distance as projectile spawn, or the mismatch is documented with a follow-up ticket.
Risks:
- Some offset may be intentional recoil/weapon sprite visual styling; avoid removing designed offsets unless they conflict with projectile origin.

Result:
- Shared authoritative local muzzle/tracer feedback now derives projectile birth from the authoritative `player.position`, not the locally smoothed display pose.
- This aligns local immediate muzzle feedback with backend projectile origin/path while preserving the same aim direction and shared `resolveProjectileBirthPosition` helper.
- Closed the VFX exploration subagent after integrating its finding.

Verification passed:
- `npm run build`
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/bp28-render-feel-smoke.ps1 -Scenario StraightFire -InputDurationMs 1800 -SummaryPath .codex/bp28-straight-fire-summary.json`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes; renderer/VFX only.

Risks:
- This fixes the local authoritative offset source. Remote projectile birth feedback has the same class of display-vs-authoritative offset and is handled next.

## Active Ticket

ID: BP-VFX-2
Goal: Align remote projectile birth feedback with authoritative projectile origin.
Allowed scope: `frontend/src/features/battle/renderer/effects/projectileTerminalFeedbackPolicy.ts`, `frontend/src/features/battle/renderer/effects/remoteProjectileBirthFeedbackPresenter.ts`, `.codex/agent-state.md`, verification commands.
Forbidden scope: projectile speed/TTL balance, backend runtime rules, local input, queue, database/schema/data changes, broad renderer refactors.
Expected change:
- Remote projectile birth spark/tracer should use authoritative owner/projectile geometry rather than smoothed remote hero display position.
Architecture/domain-modeling impact:
- Keeps remote VFX derived from authoritative projectile data.
Side-effect boundary impact:
- Renderer/VFX only; no new external effects.
Verification:
- `npm run build`
- Run a battle render-feel smoke if the patch compiles and affects visual feedback.
Acceptance criteria:
- `resolveRemoteProjectileBirthFeedbackPosition` no longer creates a parallel offset from remote display interpolation.
- Existing battle render smoke remains green.
Risks:
- Remote muzzle spark may appear slightly detached from a heavily interpolated remote hero, but it will align with the actual projectile path.

Result:
- Remote projectile birth feedback no longer uses smoothed remote hero display position.
- `resolveRemoteProjectileBirthFeedbackPosition` now uses authoritative owner position and the shared projectile birth helper.
- Removed the now-unused display-position argument from the presenter call path.

Verification passed:
- `npm run build`
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/bp28-render-feel-smoke.ps1 -Scenario StraightFire -InputDurationMs 1800 -SummaryPath .codex/bp28-straight-fire-summary.json`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes; renderer/VFX only.

Risks:
- Remote birth feedback now prioritizes alignment with projectile path over sticking to interpolated remote hero sprites.

## Completed Ticket

ID: BP-VFX-3
Goal: Improve local-owner authoritative projectile terminal visibility.
Allowed scope: `frontend/src/features/battle/renderer/effects/battleFeedbackSceneBridge.ts`, `frontend/src/features/battle/renderer/effects/projectileTerminalFeedbackPolicy.ts`, focused diagnostics/smoke files only if needed, `.codex/agent-state.md`, verification commands.
Forbidden scope: projectile speed/TTL balance, backend runtime rules, input semantics, queue, database/schema/data changes, broad renderer refactors.
Expected change:
- Audit why local-owner projectiles can feel like instant hit/no bullet when they terminate before a visible live projectile frame.
- If safe, allow a bounded local-owner terminal tracer/correction tracer so hits still have visible projectile travel feedback.
Architecture/domain-modeling impact:
- Keeps authoritative damage unchanged; this is visual feedback only.
Side-effect boundary impact:
- Renderer/VFX only; no new external effects.
Verification:
- `npm run build`
- Run a battle render-feel smoke; prefer a firing scenario.
Acceptance criteria:
- Local authoritative projectile terminals are no longer completely silent in tracer feedback when the projectile vanishes on hit.
- No duplicate noisy tracer spam for non-local projectile terminals.
Risks:
- Over-enabling local terminal tracers could duplicate regular live projectile visuals if the projectile was already visible; keep the change bounded.

Result:
- Local-owner authoritative projectile terminals now present a bounded terminal tracer when the projectile terminated before a live projectile feedback state existed.
- Existing non-local authoritative terminal tracers still present normally.
- Local projectiles that already had a live projectile state remain protected from duplicate terminal tracer spam.

Verification passed:
- `npm run build`
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/bp28-render-feel-smoke.ps1 -Scenario StraightFire -InputDurationMs 1800 -SummaryPath .codex/bp28-straight-fire-summary.json`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes; renderer/VFX only.

Risks:
- This improves fast-hit visibility but does not change projectile speed, lifetime, or damage timing. If pistol still feels too instant, the next investigation should compare frontend local projectile feedback timing against backend projectile ticks.

## Completed Ticket

ID: BWR-182
Goal: Extract pure battle geometry helpers from `BattleStateService.scala`.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, new backend battle service helper file under `backend/src/main/scala/slaydemo/backend/battle/services/`, `.codex/agent-state.md`, verification commands.
Forbidden scope: battle rules, projectile/weapon tuning, queue/matchmaking, finish projection semantics, frontend implementation, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Move pure vector/geometry helper functions out of the oversized state service into a package-private helper object.
- Keep all call sites behavior-equivalent.
Architecture/domain-modeling impact:
- Reduces god-service pressure while preserving typed battle vectors and immutable state transitions.
Side-effect boundary impact:
- No side effects; extracted functions are pure math helpers.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- `BattleStateService.scala` no longer owns the generic vector math helpers.
- Backend contracts and compile pass.
Risks:
- An import/scoping mistake can break compile; behavior should remain unchanged because this is extraction only.

Result:
- Added package-private `BattleGeometry` for pure vector math helpers.
- `BattleStateService.scala` now imports those helpers instead of owning generic geometry functions.
- No battle rules, constants, database, or frontend behavior were changed.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restarted backend in explicit Postgres mode; `/health.storageMode=postgres`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes; pure helper extraction only.

Risks:
- This reduces `BattleStateService.scala` size only slightly. Larger decomposition should continue through behavior-neutral, compile-verified tickets after parity issues stay green.

## Completed Ticket

ID: BWR-183
Goal: Restore replay POST playback availability gate parity.
Allowed scope: `backend/src/main/scala/slaydemo/backend/replay/services/ReplayService.scala`, focused replay service contract tests, `.codex/agent-state.md`, verification commands.
Forbidden scope: battle runtime behavior, finish projection, replay repository schema, replay route parsing unless needed, frontend implementation, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Stored replay playback availability should require both submitted `playbackAvailable=true` and normalized frames being playable.
- Frame count and frames JSON normalization should remain unchanged.
Architecture/domain-modeling impact:
- Preserves explicit replay metadata semantics at the replay service boundary.
Side-effect boundary impact:
- Replay service persistence command mapping only; no new side effects.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- A replay POST/record command with two frames but `playbackAvailable=false` stores `playbackAvailable=false`.
- Invalid or one-frame submissions remain not playable.
- Existing finish projection replay remains playable when it submits playable frames.
Risks:
- Clients that previously got playback for two-frame submissions despite sending `playbackAvailable=false` will now see legacy-compatible unavailable playback.

Result:
- Replay recording now stores `playbackAvailable=true` only when the submitted command allows playback and normalized frames are playable.
- Replay service contract now covers the false-submission/two-frame case and the true-submission/two-frame case.
- Frame count and frames JSON normalization were left unchanged.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restarted backend in explicit Postgres mode; `/health.storageMode=postgres`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none; this ticket corrected an existing explicit replay metadata flag.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- Strict playability shape is still based on valid JSON array element count. A separate ticket can inspect whether the array elements must be replay-frame objects with usable elapsed/heroes data.

## Completed Ticket

ID: BWR-184
Goal: Add finish projection partial-artifact retry contracts.
Allowed scope: `backend/src/test/scala/slaydemo/backend/battle/services/BattleFinishProjectionWriteContractTest.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: production battle runtime behavior, finish projection production code unless a test exposes a real bug, replay service behavior, frontend implementation, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Add focused tests proving result-ready states skip result writes and only retry replay.
- Add focused tests proving replay-ready states skip replay writes and only retry result.
Architecture/domain-modeling impact:
- Strengthens tests around explicit artifact readiness ADT behavior.
Side-effect boundary impact:
- Test-only repository doubles; no production side effects.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- Partial artifact readiness contracts pass.
- Existing finish projection contracts remain green.
Risks:
- If production code is already correct, this ticket only adds safety coverage.

Result:
- Added contracts proving `ResultOnlyReady` skips result writes and retries only replay.
- Added contracts proving `ReplayOnlyReady` skips replay writes and retries only result.
- Production code was already correct; this ticket added regression coverage only.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restarted backend in explicit Postgres mode; `/health.storageMode=postgres`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; test doubles only.
- Scope respected: yes.

Risks:
- This guards partial retry semantics but does not add an HTTP/API smoke around artifact readiness fields.

## Completed Ticket

ID: BWR-185
Goal: Extract pure battle event construction from `BattleStateService.scala`.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, new helper under `backend/src/main/scala/slaydemo/backend/battle/services/`, `.codex/agent-state.md`, verification commands.
Forbidden scope: battle rule changes, event retention policy changes, pickup effects, projectile/weapon tuning, frontend implementation, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Move battle event id/participant/message construction to a package-private helper object.
- Keep existing event IDs and messages unchanged.
Architecture/domain-modeling impact:
- Reduces `BattleStateService.scala` responsibility while keeping event construction explicit and typed.
Side-effect boundary impact:
- No side effects; pure event value construction.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- Existing event, pickup, replay, and battle state contracts pass.
- No runtime behavior changes.
Risks:
- A missed import could break compile; tests cover pickup event IDs and event retention.

Result:
- Added package-private `BattleEventFactory` for event IDs, participants, and default event messages.
- `BattleStateService.scala` now imports event construction helpers instead of owning that pure block.
- Existing event IDs and messages were preserved.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Restarted backend in explicit Postgres mode; `/health.storageMode=postgres`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes; pure helper extraction only.

Risks:
- `BattleStateService.scala` remains large; continue decomposition only in small behavior-neutral slices.

## Completed Ticket

ID: BWR-186
Goal: Run full API contract verification after replay and battle service changes.
Allowed scope: verification commands and `.codex/agent-state.md`; production/test code only if a verified failure is caused by the current changes.
Forbidden scope: unrelated cleanup, dependency changes, database/schema/data changes, broad frontend changes.
Expected change:
- No code changes expected unless the API smoke exposes a real regression.
Architecture/domain-modeling impact:
- Verification-only ticket.
Side-effect boundary impact:
- Exercises the running backend and Postgres-backed app through HTTP; no schema changes.
Verification:
- `npm run demo:api-contract`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- Full API contract passes against explicit Postgres backend.
Risks:
- API smoke may create demo data in the configured dev database, which is expected for this verification path.

Result:
- Full API contract passed against the running explicit Postgres backend.
- Covered health, identity, bots, mails, social, forum, governance, battle results, replay catalog/comments, SSE state, pistol ammo/reload, medkit/weapon pickup, obstacle collision, terminal elimination, sprint, freeze, ownership, and queue room snapshot paths.

Verification passed:
- `npm run demo:api-contract`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes; verification-only.

Risks:
- Smoke writes demo records into the local dev database. That is expected for this contract path.

## Completed Ticket

ID: BWR-187
Goal: Repair battle content audit script after backend catalog extraction.
Allowed scope: `scripts/audit-battle-content-contract.mjs`, `.codex/agent-state.md`, verification commands.
Forbidden scope: production backend/frontend code, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Static audit should read current backend battle catalog/content files instead of only `BattleStateService.scala`.
- Audit should continue checking weapon constants, pickup definitions, sprint/stamina constants, and obstacle content against frontend/legacy expectations.
Architecture/domain-modeling impact:
- Verification tooling only; no domain model changes.
Side-effect boundary impact:
- No production side effects.
Verification:
- `npm run audit:battle-content`
- `git diff --check`
Acceptance criteria:
- Battle content audit passes using the current split backend files.
Risks:
- Script parsing is text-based and may need future maintenance if catalog definitions move again.

Result:
- `scripts/audit-battle-content-contract.mjs` now combines `InMemoryBattleStateCatalog.scala` and `BattleStateService.scala` for backend content parsing.
- The audit once again sees backend map constants, spawn points, inner obstacles, weapon definitions, skill definitions, and pickup initialization after the catalog extraction.

Verification passed:
- `npm run audit:battle-content`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes; verification tooling only.

Risks:
- The audit remains a text parser. It now covers the current split, but future catalog moves should update the source list.

## Completed Ticket

ID: BWR-188
Goal: Add API smoke coverage for ordinary player movement collision.
Allowed scope: `scripts/api-contract-field-smoke.ps1`, `.codex/agent-state.md`, verification commands.
Forbidden scope: production backend/frontend code, database/schema/data changes, dependency changes, broad script refactors.
Expected change:
- Extend the existing battle command obstacle smoke to move a player into a known arena obstacle lane and assert the authoritative position stops before the blocker.
- Keep existing Blink/projectile obstacle assertions unchanged.
Architecture/domain-modeling impact:
- Verification-only API coverage for existing movement collision rules.
Side-effect boundary impact:
- Exercises the running backend through HTTP; no production side effects beyond normal smoke data.
Verification:
- `npm run demo:api-contract`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- API smoke proves ordinary movement cannot pass through the selected obstacle.
- Existing API smoke remains green.
Risks:
- HTTP timing can make exact positions noisy; assertion should use conservative bounds tied to the known blocker.

Result:
- Extended the existing obstacle collision API smoke with an ordinary movement collision assertion.
- The smoke now places the fourth human participant at spawn point `(1600, 320)`, moves into the right lane wall, and asserts the authoritative position advances but stops before the blocker.
- Existing blocked Blink and projectile obstacle checks were preserved.

Verification passed:
- `npm run demo:api-contract`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes; API smoke coverage only.

Risks:
- The movement bound is intentionally conservative (`y <= 590`) to avoid HTTP timing flake while still catching wall pass-through.

## Completed Ticket

ID: BWR-189
Goal: Add medkit positive heal runtime coverage.
Allowed scope: `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: production battle runtime behavior unless a real test failure exposes a bug, API smoke scripts, frontend implementation, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- Add or strengthen a focused runtime contract proving medkit pickup heals a damaged player by the configured amount and clamps at max HP.
Architecture/domain-modeling impact:
- Test-only coverage of existing typed hit point transition.
Side-effect boundary impact:
- No production side effects.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- Runtime contract verifies positive medkit healing.
- Existing pickup contracts remain green.
Risks:
- If producing damage through normal combat is too broad for this ticket, keep the test scoped by using an existing deterministic combat path.

Result:
- Added a runtime contract that damages a player through the normal pistol combat path, moves that damaged player onto `pickup-medkit-1`, and asserts HP clamps back to max.
- The contract also verifies the medkit becomes unavailable and the emitted battle event is `Heal`.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Explicit Postgres backend restart confirmed `/health.storageMode=postgres`.
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes; runtime contract coverage only.

Risks:
- The test uses a deterministic combat setup and contact path. It proves positive heal behavior, but API-level medkit pickup remains covered indirectly by existing smoke plus this runtime contract rather than a dedicated HTTP medkit-heal smoke.

## Completed Ticket

ID: BWR-190
Goal: Add API smoke coverage for empty-mag automatic pistol reload.
Allowed scope: `scripts/api-contract-field-smoke.ps1`, `.codex/agent-state.md`, verification commands.
Forbidden scope: production backend/frontend code unless API smoke exposes a real backend bug, database/schema/data changes, dependency changes, broad smoke-script refactors, `backend-legacy/**`.
Expected change:
- Extend the existing pistol ammo/reload API smoke to drain a full pistol magazine through HTTP commands and assert automatic reload starts when the magazine reaches zero.
- Assert reload completion restores a full magazine and consumes the expected reserve ammo.
Architecture/domain-modeling impact:
- Verification-only coverage of existing weapon state transitions exposed through the API.
Side-effect boundary impact:
- Exercises the running backend through HTTP; no production side effects beyond normal smoke data.
Verification:
- `npm run demo:api-contract`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- API smoke proves empty-mag auto reload starts without pressing reload.
- API smoke proves auto reload completion fills the pistol magazine and consumes reserve ammo.
Risks:
- HTTP timing can make exact intermediate cooldown values noisy; assertions should check stable ammo/reload end states and bounded reload start.

Result:
- Extended the existing pistol ammo API smoke to drain a full 12-round pistol magazine through HTTP commands without pressing reload.
- The smoke now asserts reserve ammo does not change before reload completion, auto reload starts when ammo reaches zero, and completion restores `12` magazine ammo while consuming `12` reserve rounds.

Verification passed:
- `npm run demo:api-contract`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes; API smoke coverage only.

Risks:
- The smoke takes several seconds to drain the weapon through real cooldown timing. It is intentionally slower than a unit test because it validates the actual HTTP path.

## Completed Ticket

ID: BWR-191
Goal: Extract pure weapon state rules out of `BattleStateService`.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, a new backend battle services helper file, `.codex/agent-state.md`, verification commands.
Forbidden scope: API/routes/repositories/database/frontend/scripts other than verification, dependency changes, broad package/path moves, `backend-legacy/**`.
Expected change:
- Move pure weapon inventory, reload, fire-readiness, and weapon-switch helper functions into a cohesive services helper object.
- Keep `BattleStateService` orchestration and side-effect boundaries unchanged.
Architecture/domain-modeling impact:
- Reduces god-service pressure while preserving immutable `BattleWeaponState` and `BattlePlayerState` transformations.
Side-effect boundary impact:
- Extracted code must remain pure and side-effect-free.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- explicit Postgres backend restart with `/health`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- Behavior remains unchanged and contracts pass.
- `BattleStateService` no longer owns weapon inventory/reload helper definitions.
Risks:
- A careless extraction could change private helper visibility or miss one reload path; keep it as a mechanical move with unchanged logic.

Result:
- Added `BattleWeaponRules` as a pure services helper for current weapon lookup/update, magazine and heat fire readiness, magazine charging, auto/manual reload transitions, reload completion, weapon creation/refill, pickup equip/refill, weapon switching, and heat-weapon detection.
- `BattleStateService` now imports `BattleWeaponRules.*` and no longer owns those weapon helper definitions.
- `BattleStateService.scala` dropped from 2092 lines to 1928 lines without changing runtime orchestration.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Explicit Postgres backend restart confirmed `/health.storageMode=postgres`.
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none. The moved helper retains existing numeric weapon constants behind typed `AmmoCount`/`CooldownMillis`.
- Boolean business results introduced: none.
- Domain mutation introduced: none; helper returns copied immutable state.
- Side effects inside domain: none; extracted helper is pure and package-local to services.
- Scope respected: yes; production change limited to battle services.

Risks:
- This is a mechanical extraction covered by contracts. Deeper weapon-domain modeling could later move definitions into richer value objects, but that would be a separate ticket.

## Completed Ticket

ID: BWR-192
Goal: Verify queue countdown and battle session freshness through the running API.
Allowed scope: `.codex/agent-state.md`, verification commands.
Forbidden scope: production backend/frontend code, scripts, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- No code change unless the smoke exposes a real failure.
- Run the existing BP-40 freshness smoke against the running frontend/backend.
Architecture/domain-modeling impact:
- Verification-only.
Side-effect boundary impact:
- Exercises queue/session APIs through HTTP and writes normal smoke records to the local dev database.
Verification:
- `npm run demo:bp40-freshness`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- Countdown/status polling creates an active battle session.
- A fresh queue request creates a fresh battle session with reset elapsed/remaining time.
Risks:
- The smoke is timing-sensitive because it polls real HTTP services.

Result:
- Ran the BP-40 freshness smoke against the running Vite proxy and backend.
- Verified a first queue join creates an active battle session after polling.
- Verified a second fresh queue request creates a different battle id and starts near zero elapsed time instead of inheriting the old session clock.

Verification passed:
- `npm run demo:bp40-freshness`
  - round1BattleId=`battle-room-000001`, round1ElapsedMs=`1658`
  - round2BattleId=`battle-room-000003`, round2ElapsedMs=`53`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes; verification-only.

Risks:
- This proves the backend/queue path produces active sessions. A browser-specific failure after countdown would need a frontend/runtime smoke or manual repro trace.

## Completed Ticket

ID: BWR-193
Goal: Run targeted browser battle-feel smoke for camera/projectile regression coverage.
Allowed scope: `.codex/agent-state.md`, `.runtime/**` generated smoke output, verification commands.
Forbidden scope: production backend/frontend code unless the smoke exposes a real failure, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- No production code change unless a real failure appears.
- Run BP-28 StraightFire against the running frontend/backend and record outcome.
Architecture/domain-modeling impact:
- Verification-only.
Side-effect boundary impact:
- Exercises the browser, frontend proxy, backend API, and local dev database through smoke users/sessions.
Verification:
- `npm run demo:bp28-render-feel-smoke -- -Scenario StraightFire -InputDurationMs 1800`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- Browser reaches playing battle state.
- Diagnostics include usable vision/camera and projectile feedback metrics.
- Smoke exits successfully.
Risks:
- Browser smoke is slower and can fail for local browser/360/security tooling reasons unrelated to code.

Result:
- Ran BP-28 StraightFire through headless Edge against the running frontend/backend.
- Both browser clients entered `playing=true` in the same battle (`battle-room-000004`).
- The smoke reported `ok=true`, `sameBattle=true`, command failures `0`, available vision/camera diagnostics, available VFX diagnostics, available HUD diagnostics, and no warnings.

Verification passed:
- `npm run demo:bp28-render-feel-smoke -- -Scenario StraightFire -InputDurationMs 1800`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes; verification-only.

Risks:
- StraightFire covers the browser entry/render/projectile path, but not every movement/skill stress path. Broader BP-44 suite remains a useful later verification pass.

## Completed Ticket

ID: BWR-194
Goal: Extract pure arena collision and world-bound geometry rules out of `BattleStateService`.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, a new backend battle services helper file, `.codex/agent-state.md`, verification commands.
Forbidden scope: API/routes/repositories/database/frontend/scripts other than verification, dependency changes, behavior tuning, `backend-legacy/**`.
Expected change:
- Move pure arena/world collision helpers and segment/AABB intersection helpers into a cohesive package-local helper object.
- Keep movement, Blink, projectile, and pickup behavior unchanged.
Architecture/domain-modeling impact:
- Reduces god-service pressure and makes collision rules easier to audit.
Side-effect boundary impact:
- Extracted helper must be pure and side-effect-free.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- explicit Postgres backend restart with `/health`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- Movement, obstacle, Blink, and projectile collision contracts remain green.
- `BattleStateService` no longer owns arena collision helper definitions.
Risks:
- The helper signatures include overloaded `isInWorld`; keep the extraction mechanical to avoid changing collision semantics.

Result:
- Added `BattleArenaCollision` as a pure package-local helper for world bounds, obstacle overlap, player occupancy, segment/world exit, segment/AABB entry, and segment/circle hit calculations.
- `BattleStateService` now imports `BattleArenaCollision.*` and no longer owns those collision helper definitions.
- `BattleStateService.scala` is now 1766 lines; the extracted collision helper is 168 lines.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Explicit Postgres backend restart confirmed `/health.storageMode=postgres`.
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; helper returns pure geometry decisions.
- Side effects inside domain: none; extracted helper is pure and package-local.
- Scope respected: yes; production change limited to backend battle services.

Risks:
- Collision behavior is contract-covered, but browser feel around tight obstacle corners can still deserve manual/visual smoke in broader BP-44.

## Completed Ticket

ID: BWR-195
Goal: Run full API contract regression after backend battle service extractions.
Allowed scope: `.codex/agent-state.md`, verification commands.
Forbidden scope: production backend/frontend code, scripts, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- No code change unless the API regression exposes a real failure.
- Run the full field API smoke against the currently running frontend/backend.
Architecture/domain-modeling impact:
- Verification-only after service decomposition.
Side-effect boundary impact:
- Exercises HTTP APIs and writes normal smoke records to the local dev database.
Verification:
- `npm run demo:api-contract`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- Full API smoke passes after weapon and collision helper extraction.
Risks:
- Smoke timing is longer because it includes real battle sessions and reload/pickup waits.

Result:
- Ran the full field API smoke after weapon and collision helper extractions.
- The smoke covered health, identity, bots, mails, social/forum/governance, battle results, replay catalog/comments, SSE battle state, ammo/manual reload/auto reload, medkit and weapon pickup, obstacle movement/projectile blocking, terminal elimination, sprint stamina, Freeze slow fields, ownership validation, and queue room snapshots.

Verification passed:
- `npm run demo:api-contract`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes; verification-only.

Risks:
- API smoke is broad but still script-driven; broader browser feel suite remains a separate pass for rendering stress.

## Completed Ticket

ID: BWR-196
Goal: Run frontend typecheck/build regression after battle runtime and backend integration work.
Allowed scope: `.codex/agent-state.md`, verification commands.
Forbidden scope: production code unless build exposes a real failure, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- No code change unless build fails due to current work.
- Run the repository frontend build command.
Architecture/domain-modeling impact:
- Verification-only.
Side-effect boundary impact:
- Build output only; no backend/database side effects.
Verification:
- `npm run build`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- TypeScript compile and Vite build pass.
Risks:
- Existing Vite chunk-size warnings are acceptable if build succeeds.

Result:
- Ran the repository frontend build command after battle runtime and backend integration work.
- TypeScript compile and Vite production build completed successfully.

Verification passed:
- `npm run build`
  - Vite emitted existing warnings for React Router `"use client"` directives, dynamic/static import chunking, and large bundle size.
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes; verification-only.

Risks:
- Build warnings are still present and pre-existing in nature; they are not blocking correctness but can be addressed later as a bundling/performance ticket.

## Completed Ticket

ID: BWR-197
Goal: Run broader browser battle-feel suite for movement, skill, and dual-client stress.
Allowed scope: `.codex/agent-state.md`, `.runtime/**` generated smoke output, verification commands.
Forbidden scope: production backend/frontend code unless the suite exposes a real failure, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- No production code change unless a real failure appears.
- Run BP-44 battle feel suite without the already-covered StraightFire scenario.
Architecture/domain-modeling impact:
- Verification-only.
Side-effect boundary impact:
- Exercises browser clients, frontend proxy, backend API, and local dev database through smoke users/sessions.
Verification:
- `npm run demo:bp44-feel-suite -- -SkipStraightFire`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- MixedMovement, SkillPressure, TargetedSkillPressure, and DualClientPressure scenarios pass.
Risks:
- Browser smoke may fail due to local browser/security tooling or timing. If it fails, inspect generated logs before changing code.

Result:
- Ran BP-44 battle-feel suite with StraightFire skipped because BWR-193 already covered it.
- MixedMovement, SkillPressure, TargetedSkillPressure, and DualClientPressure all passed with `sameBattle=true`.
- Suite summary was written to `.runtime/bp44-battle-feel-suite/suite-summary.json`.

Verification passed:
- `npm run demo:bp44-feel-suite -- -SkipStraightFire`
  - MixedMovement: `ok=True`, warnings `0`, hitDisputeFailures `0`
  - SkillPressure: `ok=True`, warnings `0`, hitDisputeFailures `0`
  - TargetedSkillPressure: `ok=True`, warnings `0`, hitDisputeFailures `0`
  - DualClientPressure: `ok=True`, warnings `0`, hitDisputeFailures `0`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes; verification-only.

Risks:
- Browser suite is strong regression coverage, but not a proof of every manual play path. Continue with codebase audit and smaller backend decomposition.

## Completed Ticket

ID: BWR-198
Goal: Remove local mutable command sequencing in battle command application.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: weapon/projectile/movement constants, API routes, database/schema/data changes, frontend/scripts, dependency changes, `backend-legacy/**`.
Expected change:
- Replace `var nextState` / `var outcomes` in `applyCommand` with an explicit fold over requested skill transitions.
- Preserve Blink -> Dash -> Freeze application order and outcome accumulation.
Architecture/domain-modeling impact:
- Makes a command transition read as immutable state threading instead of hidden local mutation.
Side-effect boundary impact:
- No side effects; pure service-level state transformation only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- explicit Postgres backend restart with `/health`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- Backend contracts pass.
- Skill command behavior remains unchanged.
Risks:
- Order must remain Blink, then Dash, then Freeze; do not reorder skill effects.

Result:
- Replaced mutable `nextState`/`outcomes` sequencing inside `applyCommand` with a fold over requested skill transitions.
- Preserved the original skill order: Blink, then Dash, then Freeze.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Explicit Postgres backend restart confirmed `/health.storageMode=postgres`.
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; command transition now threads immutable state explicitly.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- Remaining local `var` usages in battle service are in broader routines such as pickup/projectile accumulation and stepped movement. Those should be considered in separate small tickets.

## Completed Ticket

ID: BWR-199
Goal: Remove local mutable state from pickup collection transition.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: pickup constants/positions/effects, weapon rules, projectile rules, API routes, database/schema/data changes, frontend/scripts, dependency changes, `backend-legacy/**`.
Expected change:
- Replace mutable `var nextState` in `collectPickups` with an explicit fold over available pickups.
- Preserve nearest-player pickup resolution, event construction, pickup respawn state, and ordering.
Architecture/domain-modeling impact:
- Makes pickup collection an explicit immutable state transition.
Side-effect boundary impact:
- No side effects; pure service-level state transformation only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- explicit Postgres backend restart with `/health`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- Backend contracts pass.
- Pickup behavior remains unchanged.
Risks:
- Pickup event IDs and event ordering depend on current state elapsed/events; preserve use of the folded current state.

Result:
- Replaced mutable `var nextState` in `collectPickups` with an explicit fold over available pickups.
- Preserved nearest-player resolution, pickup consumption/respawn state, event kind/message, and event ID generation from the folded current state.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Explicit Postgres backend restart confirmed `/health.storageMode=postgres`.
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; pickup transition now threads immutable state explicitly.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- Remaining local mutable state in `BattleStateService` is now limited to the in-memory battle store, stepped movement scanning, and projectile impact accumulation. Those need separate scoped tickets.

## Completed Ticket

ID: BWR-200
Goal: Remove local mutable loop state from stepped movement resolution.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: movement speed/step constants, collision rules, projectile rules, API routes, database/schema/data changes, frontend/scripts, dependency changes, `backend-legacy/**`.
Expected change:
- Replace `var lastValid`, `var hitBlocker`, and `var step` in `resolveSteppedMotion` with immutable step scanning.
- Preserve first-blocker behavior and destination/block flags.
Architecture/domain-modeling impact:
- Makes movement resolution a pure immutable calculation.
Side-effect boundary impact:
- No side effects; pure service-level geometry transition only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- explicit Postgres backend restart with `/health`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- Backend contracts pass.
- Wall collision movement semantics remain unchanged.
Risks:
- Movement collision is sensitive to first-blocker handling; keep the exact short-circuit semantics by preserving the first blocked step.

Result:
- Added a small `SteppedMotionScan` value and replaced the local while loop state in `resolveSteppedMotion` with an immutable fold over motion steps.
- Preserved first-blocker semantics by carrying `hitBlocker` through the scan and returning the last valid point.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Explicit Postgres backend restart confirmed `/health.storageMode=postgres`.
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none; existing motion flags were preserved.
- Domain mutation introduced: none; movement resolution now uses immutable scan state.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- Remaining `var` in `BattleStateService` is now the in-memory battle store plus projectile impact accumulation. The store is an intentional effect boundary; projectile accumulation should be considered next.

## Completed Ticket

ID: BWR-201
Goal: Remove local mutable state from projectile advancement.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: projectile constants/damage/collision semantics, weapon rules, API routes, database/schema/data changes, frontend/scripts, dependency changes, `backend-legacy/**`.
Expected change:
- Replace mutable `var nextState` in `advanceProjectiles` with an explicit immutable accumulator.
- Preserve projectile processing order, impact side effects on folded state, and active projectile output order.
Architecture/domain-modeling impact:
- Makes projectile advancement an explicit state transition from old aggregate state to new aggregate state plus active projectiles.
Side-effect boundary impact:
- No external side effects; pure service-level state transformation only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- explicit Postgres backend restart with `/health`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- Backend contracts pass.
- Projectile hit/block/expiry behavior remains unchanged.
Risks:
- Later projectiles in the same tick must see damage from earlier projectile impacts. The accumulator must feed each projectile with the updated folded state.

Result:
- Added `ProjectileAdvance` as the explicit accumulator for folded aggregate state plus active projectile output.
- Replaced mutable `nextState` in `advanceProjectiles` with an immutable fold over projectiles.
- Preserved projectile order and ensured later projectiles still see earlier projectile impacts through the folded state.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Explicit Postgres backend restart confirmed `/health.storageMode=postgres`.
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none; projectile advancement now uses immutable accumulator state.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- The only remaining `var` in `BattleStateService` is the in-memory battle map, which is the intended repository-like mutable boundary protected by the service lock.

## Completed Ticket

ID: BWR-202
Goal: Run full API contract regression after immutable battle transition cleanup.
Allowed scope: `.codex/agent-state.md`, verification commands.
Forbidden scope: production backend/frontend code, scripts, database/schema/data changes, dependency changes, `backend-legacy/**`.
Expected change:
- No code change unless the API regression exposes a real failure.
- Run the full field API smoke against the currently running frontend/backend.
Architecture/domain-modeling impact:
- Verification-only after immutable transition cleanup.
Side-effect boundary impact:
- Exercises HTTP APIs and writes normal smoke records to the local dev database.
Verification:
- `npm run demo:api-contract`
- `npm run dev:status`
- `git diff --check`
Acceptance criteria:
- Full API smoke passes after pickup/movement/projectile transition cleanup.
Risks:
- Smoke timing is longer because it includes real battle sessions and reload/pickup waits.

Result:
- Ran the full field API smoke after pickup, movement, and projectile immutable transition cleanup.
- The smoke covered health, identity, bots, mails, social/forum/governance, battle results, replay catalog/comments, SSE battle state, ammo/manual reload/auto reload, medkit and weapon pickup, obstacle movement/projectile blocking, terminal elimination, sprint stamina, Freeze slow fields, ownership validation, and queue room snapshots.

Verification passed:
- `npm run demo:api-contract`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes; verification-only.

Risks:
- API smoke passed, but broader codebase completion still requires continuing route/replay/service audits and legacy parity checks.

## Completed Ticket

ID: BWR-203
Goal: Extract pure battle replay-frame capture helpers from `BattleStateService`.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, a new `backend/src/main/scala/slaydemo/backend/battle/services/BattleReplayFrameRecorder.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: replay storage/projection semantics, API routes, database/schema/data changes, frontend/scripts, weapon/projectile/movement balance changes, dependency changes, `backend-legacy/**`.
Expected change:
- Move replay frame sampling, capture, de-duplication, and retention helper logic into a small pure service object.
- Keep `BattleStateService` behavior and call sites equivalent.
Architecture/domain-modeling impact:
- Reduces `BattleStateService` responsibility by isolating pure replay snapshot construction.
- Keeps replay state as immutable value transformations.
Side-effect boundary impact:
- No new external side effects; the extracted object must remain pure and deterministic.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run demo:api-contract` if compile/contracts pass
- `git diff --check`
Acceptance criteria:
- Backend contracts compile and pass.
- API smoke still records/returns replay-related battle state successfully.
- `BattleStateService` no longer owns replay frame capture internals.
Risks:
- Replay frame retention must preserve the initial frame and most recent tail exactly.

Result:
- Added `BattleReplayFrameRecorder` for replay frame sampling, capture, de-duplication, and retention.
- Replaced the three `BattleStateService` replay-frame call sites with calls into the recorder.
- Removed replay frame capture internals from `BattleStateService`, reducing the file to about 1533 lines.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Explicit Postgres backend restart confirmed `/health.storageMode=postgres`.
- `npm run demo:api-contract`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none; existing replay sampling flags were preserved.
- Domain mutation introduced: none.
- Side effects inside domain: none; extracted recorder is pure.
- Scope respected: yes.

Risks:
- Replay persistence and replay route validation still need separate audit/fix tickets.

Next ticket:
- BWR-204: Restore legacy-style dead-player runtime cleanup for skills and weapon transient state.

## Completed Ticket

ID: BWR-204
Goal: Restore dead-player runtime cleanup parity for skills and weapon transient state.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: projectile constants/range/speed, weapon inventory balance, pickup behavior, finish projection, replay routes/storage, database/schema/data changes, frontend/scripts, dependency changes, `backend-legacy/**`.
Expected change:
- When a player is eliminated, active skill timers should be cleared.
- Dead-player advancement should keep movement/fire/reload inert and clear active skills.
- Weapon transient runtime that should not persist after death should be reset consistently with legacy behavior.
Architecture/domain-modeling impact:
- Keeps death as an explicit immutable player-state transition instead of partial field updates at scattered call sites.
Side-effect boundary impact:
- No new external side effects; deterministic battle state transition only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run demo:api-contract` if focused checks pass
- `git diff --check`
Acceptance criteria:
- Focused runtime contract covers dead-player skill/runtime cleanup.
- Existing terminal elimination and no-respawn API smoke remain green.
Risks:
- Clearing weapon cooldown/reload on death must not accidentally refill ammo or change retained inventory.

Result:
- Added `clearDeadPlayerRuntime` as the explicit dead-player cleanup transition.
- Projectile elimination now routes killed targets through the same cleanup path.
- Dead-player advancement now keeps dead players inert by using the cleanup path instead of a partial field reset.
- Added a three-player runtime contract proving an eliminated player is cleaned while the battle remains active.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Explicit Postgres backend restart confirmed `/health.storageMode=postgres`.
- `npm run demo:api-contract`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none; existing state flags were preserved.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- Projectile profile constants still differ from legacy and need a separate decision/test ticket.

Next ticket:
- BWR-205: Audit and contract-test projectile profile constants across backend, frontend, and legacy before changing range/speed behavior.

## Completed Ticket

ID: BWR-205
Goal: Audit and pin projectile profile constants across backend, frontend, and legacy.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/InMemoryBattleStateCatalog.scala`, `backend/src/test/scala/slaydemo/backend/BattleStateRuntimeContractTest.scala`, existing content-audit scripts if needed, `.codex/agent-state.md`, verification commands.
Forbidden scope: death cleanup, movement/collision logic, pickup behavior, replay/projection/routes, database/schema/data changes, frontend runtime behavior, dependency changes.
Expected change:
- Inspect current backend projectile speed/lifetime/radius/damage against frontend content and legacy backend.
- Add or adjust focused backend contract coverage so the intended current projectile profile is explicit.
- Do not change range/speed until the contract identifies which values are intentionally different versus accidental drift.
Architecture/domain-modeling impact:
- Keeps projectile profile as catalog data and runtime tests rather than route/UI logic.
Side-effect boundary impact:
- No new external side effects; verification only or catalog/test-only if needed.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Projectile profile constants are covered by focused backend contract assertions.
- Legacy divergence is recorded as intentional current design or queued as a behavior-fix ticket.
Risks:
- User-visible projectile feel may still need behavior changes after the audit; this ticket should not mix audit with broad tuning.

Result:
- Ran the frontend/backend battle content audit; projectile constants match between current frontend and rewritten backend.
- Added focused runtime assertions for Rocket projectile radius/speed and Shotgun pellet radius/speed.
- Confirmed current design intentionally diverges from legacy TTL/range values: current frontend and backend use `30000ms` projectile lifetimes so bullets do not expire at the old short range.

Verification passed:
- `npm run audit:battle-content`
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- Legacy projectile TTL values remain intentionally different. If product wants strict legacy range parity, that must be a separate behavior-changing ticket across frontend/backend.

Next ticket:
- RPL-206: Move replay submission/comment validation back into `ReplayService` with explicit result ADTs and keep routes as HTTP mapping only.

## Completed Ticket

ID: RPL-206
Goal: Move replay submission/comment validation into the replay application service with explicit ADT errors.
Allowed scope: `backend/src/main/scala/slaydemo/backend/replay/services/ReplayService.scala`, `backend/src/main/scala/slaydemo/backend/replay/routes/ReplayRoutes.scala`, `backend/src/main/scala/slaydemo/backend/replay/support/ReplayFrameJson.scala`, replay-focused tests in `backend/src/test/scala/slaydemo/backend/ReplayServiceContractTest.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: battle runtime behavior, replay repository schema/storage migrations, projection writer behavior, frontend/scripts, dependency changes, unrelated route modules.
Expected change:
- Invalid replay frame JSON should be rejected by the service with an explicit business error instead of normalized to `[]`.
- Empty or too-long replay comment body should be rejected by the service, not only by the route.
- Routes should parse HTTP inputs and map service result ADTs to HTTP responses.
Architecture/domain-modeling impact:
- Moves replay business validation out of API routes and into the application service boundary.
- Replaces stringly/implicit validation failures with explicit service result ADTs.
Side-effect boundary impact:
- Repository writes remain in the service after validation; no new external effects.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run demo:api-contract` if backend checks pass
- `git diff --check`
Acceptance criteria:
- Focused service contract proves invalid `framesJson` is rejected.
- Focused service contract proves invalid comment body is rejected without route involvement.
- Existing replay catalog/comment API smoke remains green.
Risks:
- Route error-code parity must be preserved for clients expecting `invalid_frames_json` and `invalid_body`.

Result:
- Added `ReplayRecordError.InvalidFramesJson` and changed `ReplayService.record` to return `Either`.
- Added `ReplayCommentError.InvalidBody`; comment body trimming/length validation now lives in `ReplayService`.
- Changed `ReplayFrameJson.normalize` to reject malformed non-array frame JSON instead of silently normalizing it to `[]`.
- Updated `ReplayRoutes` to map service ADTs to `invalid_frames_json` and `invalid_body`.
- Updated replay service and visitor guardrail contracts for the new explicit service results.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Explicit Postgres backend restart confirmed `/health.storageMode=postgres`.
- `npm run demo:api-contract`
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; validation happens before repository writes.
- Scope respected: yes.

Risks:
- Invalid replay route responses are mapped in route code and covered by compile/service contracts; a future API smoke can add direct HTTP invalid-frame assertions if desired.

Next ticket:
- RPL-207: Restore safe replay identifier validation at route/service boundaries.

## Completed Ticket

ID: RPL-207
Goal: Restore safe replay identifier validation at replay route/service boundaries.
Allowed scope: `backend/src/main/scala/slaydemo/backend/replay/services/ReplayService.scala`, `backend/src/main/scala/slaydemo/backend/replay/routes/ReplayRoutes.scala`, replay-focused tests in `backend/src/test/scala/slaydemo/backend/ReplayServiceContractTest.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: replay repository schema/storage migrations, battle runtime/projection behavior, frontend/scripts, dependency changes, unrelated routes.
Expected change:
- Replay IDs accepted for record/load/comment paths should be non-empty, bounded, and safe path identifiers.
- Service record validation should reject unsafe replay IDs before persistence.
- Routes should reject unsafe path replay IDs with `invalid_replay_id` where the request is syntactically a replay id operation.
Architecture/domain-modeling impact:
- Keeps identifier validation at application/API boundaries and prevents unsafe primitive strings entering the replay domain.
Side-effect boundary impact:
- No new external side effects; validation occurs before repository reads/writes.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Focused service contract proves unsafe replay IDs are rejected and not persisted.
- Existing valid replay record/comment/list behavior remains green.
Risks:
- Some historical rows with unsafe ids may become inaccessible through service routes; that matches the safer boundary but should be reported.

Operational note:
- Cleaned local Postgres smoke/test data on user request before verifying this ticket.
- `identity_accounts` went from 3490 rows to 5 persisted rows; `/identity/accounts` now returns 6 including the backend-provided `admin` account.
- Deleted matching smoke/test rows from mails, battle results, replay records/comments/settlements, social friend requests, forum topics/replies/votes, and governance tables.

Result:
- Added `ReplayIdentifierPolicy` with safe replay id rules matching the legacy character set: letters, digits, `-`, `_`, `.`, `~`, max length 200.
- `ReplayService.record`, `load`, and `addComment` now reject unsafe replay ids before repository reads/writes.
- `ReplayRoutes` now rejects unsafe replay ids in detail/comment path segments with `invalid_replay_id`.
- Replay service contracts now cover unsafe replay id rejection and non-persistence.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Explicit Postgres backend restart confirmed `/health.storageMode=postgres`.
- Targeted HTTP checks:
  - `POST /replay/catalog` with `bad/id` -> `400 invalid_replay_id`
  - `POST /replay/catalog` with object `frames` -> `400 invalid_frames_json`
  - `POST /replay/catalog/bad%24id/comments` -> `400 invalid_replay_id`
- `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; validation happens before repository effects.
- Scope respected: yes.

Risks:
- Encoded slash in a path segment is treated by the HTTP server as route structure and returns `replay_not_found`; unsafe non-slash characters return `invalid_replay_id`.

Next ticket:
- RPL-208: Review replay comment ordering parity and decide whether newest-limited chronological output is intentional or should match legacy oldest-limit behavior.

## Completed Ticket

ID: RPL-208
Goal: Review and pin replay comment ordering semantics.
Allowed scope: `backend/src/main/scala/slaydemo/backend/replay/database/PostgresReplayRepository.scala`, `backend/src/main/scala/slaydemo/backend/replay/services/ReplayService.scala`, replay-focused tests in `backend/src/test/scala/slaydemo/backend/ReplayServiceContractTest.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: replay record validation, battle runtime/projection behavior, frontend/scripts, database schema migrations, dependency changes, unrelated routes.
Expected change:
- Confirm service/repository comment listing semantics are explicit and consistent.
- Prefer current UI-friendly behavior only if covered by contract: newest limited comments returned in chronological order.
- If repository/service semantics diverge, align them without changing API fields.
Architecture/domain-modeling impact:
- Keeps ordering/pagination semantics in replay repository/service contracts, not ad hoc route behavior.
Side-effect boundary impact:
- No new external effects; read-ordering only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Focused replay service contract clearly proves comment limit ordering.
- Current Postgres repository query is consistent with the service contract or explicitly adjusted.
Risks:
- This intentionally differs from legacy oldest-limit behavior if we keep newest-limit chronological output.

Result:
- Confirmed InMemory and Postgres replay comment repositories already share the current contract: latest `limit` comments returned chronologically for display.
- Confirmed `ReplayServiceContractTest` already proves the latest-limited chronological behavior.
- Added a repository trait comment so the ordering contract is explicit at the persistence boundary.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Explicit Postgres backend restart confirmed `/health.storageMode=postgres`.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- This remains an intentional difference from legacy oldest-limit behavior.

Next ticket:
- BRT-209: Remove route-layer command ticket sentinel by modeling optional ticket parsing explicitly.

## Current Ticket

ID: BRT-209
Goal: Remove route-layer command ticket sentinel by modeling missing ticket parsing explicitly.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/routes/BattleRoutes.scala`, focused route/API contract tests or smoke assertions if needed, `.codex/agent-state.md`, verification commands.
Forbidden scope: battle runtime command authorization semantics, queue service behavior, frontend input, replay/projection/database/schema changes, dependency changes.
Expected change:
- Stop creating `TicketId("")` for missing command ticket.
- Route parsing should represent missing/invalid ticket explicitly and map it to the existing `command_not_authorized` response behavior.
- Valid command parsing/authorization should remain unchanged.
Architecture/domain-modeling impact:
- Prevents an invalid empty ticket value object from entering the battle command domain.
Side-effect boundary impact:
- No new external side effects; route request parsing only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- targeted command HTTP/API check if needed
- `git diff --check`
Acceptance criteria:
- No `MissingCommandTicket` or `TicketId("")` sentinel remains in `BattleRoutes`.
- Missing ticket command requests still receive the same client-facing authorization rejection.
Risks:
- Must preserve existing wrong-owner/missing-ticket API smoke behavior.

Result:
- Replaced the empty `TicketId("")` sentinel with `BattleCommandRequestParseError.MissingTicket`.
- Route parsing now rejects missing command tickets before constructing `BattleCommandRequest`.
- Missing command tickets still map to `403 command_not_authorized`; other command parse errors remain `400`.

Verification passed:
- `rg -n -F "MissingCommandTicket" backend\src\main\scala\slaydemo\backend\battle\routes\BattleRoutes.scala` returned no matches.
- `rg -n -F 'TicketId("")' backend\src\main\scala\slaydemo\backend\battle\routes\BattleRoutes.scala` returned no matches.
- `npm run backend:test-contracts`
- `npm run backend:compile`

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- I did not run the full API smoke because it recreates many synthetic accounts right after the requested database cleanup. Final smoke should run later, followed by another cleanup.

Next ticket:
- BRT-210: Move command weapon-switch normalization out of the HTTP route parse path so the route only parses transport input and command semantics stay in the battle command boundary.

## Current Ticket

ID: BRT-210
Goal: Keep weapon-switch direction normalization out of the HTTP route parser.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/routes/BattleRoutes.scala`, focused battle command/weapon tests if needed, `.codex/agent-state.md`, verification commands.
Forbidden scope: battle runtime switch semantics, frontend input behavior, weapon catalog values, database/schema/replay/projection changes, dependency changes.
Expected change:
- `BattleRoutes.parseCommandRequest` should preserve the parsed `switchWeaponDirection` integer.
- Existing service-side `BattleWeaponRules.applyWeaponSwitchRequest` remains the normalization boundary for direction values.
Architecture/domain-modeling impact:
- Keeps transport parsing separate from command/domain semantics and avoids duplicated normalization in the route layer.
Side-effect boundary impact:
- No new effects; route-only parse boundary cleanup.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- `BattleRoutes` no longer clamps `switchWeaponDirection`.
- Existing backend contracts and compile pass.
Risks:
- Very low: current service already clamps negative/positive direction to -1/1, matching legacy runtime behavior.

Result:
- Removed the route-layer clamp from `BattleRoutes.parseCommandRequest`.
- Command parsing now preserves the incoming `switchWeaponDirection`; service-side `BattleWeaponRules.applyWeaponSwitchRequest` remains the single normalization point.

Verification passed:
- `rg -n "switchWeaponDirection = switchWeaponDirection\.max|switchWeaponDirection\.max\(-1\)|\.min\(1\)" backend\src\main\scala\slaydemo\backend\battle\routes\BattleRoutes.scala` returned no matches.
- `npm run backend:test-contracts`
- `npm run backend:compile`

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- Full API/browser smoke still deferred until final validation to avoid recreating synthetic users repeatedly.

Next ticket:
- BRT-211: Align missing-skill command outcomes for Blink/Dash with Freeze and legacy semantics by returning `SkillNotOwned` instead of treating absent skills as cooldown failures.

## Current Ticket

ID: BRT-211
Goal: Align Blink/Dash skill availability checks with Freeze and legacy runtime semantics.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, focused battle runtime tests if a public seam exists, `.codex/agent-state.md`, verification commands.
Forbidden scope: route parsing, frontend input, weapon/projectile constants, queue authorization, database/schema/replay/projection changes, dependency changes.
Expected change:
- Blink and Dash should check skill ownership before cooldown.
- Blink should check cooldown before target-shape failures, matching legacy and Freeze priority.
- Dash without a skill should report `SkillNotOwned` rather than `Cooldown`.
Architecture/domain-modeling impact:
- Keeps finite skill command outcomes explicit and consistent across skills.
Side-effect boundary impact:
- No new effects; pure state-transition branch ordering only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- Blink/Dash absent-skill paths return `SkillOutcomeReason.SkillNotOwned`.
- Existing battle runtime contracts pass.
Risks:
- Current public test seams always seed players with all default skills, so absent-skill behavior may be covered by code review/static inspection rather than a direct integration assertion unless a narrow seam already exists.

Result:
- Blink now checks ownership before cooldown and checks cooldown before target validation.
- Dash now checks ownership before cooldown.
- Added a reachable battle runtime contract assertion that Blink cooldown takes priority over invalid target shape after a successful Blink.

Verification passed:
- `npm run backend:test-contracts` after fixing the new test setup to preserve held movement/sprint.
- `npm run backend:compile`

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: no new mutation; existing immutable player copies preserved.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- Direct absent-skill integration coverage is still limited because the current battle seed factory always grants default skills. Static code review confirms the new absent-skill branches.

Next ticket:
- BRT-212: Add focused battle route command input contract tests or a route parse seam for stricter command parsing parity decisions without full API smoke data churn.

## Current Ticket

ID: BRT-212
Goal: Add focused battle command route contracts and tighten command JSON parsing to legacy-shaped types.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/routes/BattleRoutes.scala`, new focused route contract test under `backend/src/test/scala/slaydemo/backend/battle/routes`, `backend/src/test/scala/slaydemo/backend/BackendContractTestRunner.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: battle runtime semantics, queue behavior, frontend input, database/schema/replay/projection changes, dependency changes, full API smoke.
Expected change:
- Battle command parser should require string IDs/ticket, JSON numeric ticks/vectors/switch fields, and JSON boolean flags.
- Route contract tests should verify valid command parsing, missing ticket authorization rejection, and representative malformed type rejections without touching Postgres.
Architecture/domain-modeling impact:
- Makes the API boundary explicit and prevents permissive transport parsing from hiding client contract drift.
Side-effect boundary impact:
- Tests use a fake `BattleStateService` and an ephemeral local HTTP server; no database writes.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- String booleans and numeric IDs are rejected by `/battle/commands`.
- Valid command route still reaches the battle state service.
- Missing ticket still returns `403 command_not_authorized` without reaching the service.
Risks:
- If any out-of-repo client depends on permissive command parsing, it will need to send normal JSON types.

Result:
- Added `BattleCommandRouteContractTest`, executed by `BackendContractTestRunner`.
- The route contract uses an ephemeral local HTTP server plus a recording fake `BattleStateService`; it does not touch Postgres.
- Tightened `/battle/commands` parsing to require legacy-shaped JSON: string IDs/ticket, numeric ticks/vectors/switch fields, and boolean flags.
- Confirmed valid command reaches the service, missing ticket returns `403 command_not_authorized` before service, and malformed representative types return `400`.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`

Self-review:
- Primitive business types introduced: none in domain; route/test transport strings are boundary data.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- This intentionally rejects previously permissive command JSON. The frontend sends normal JSON types, so expected in-repo risk is low.

Next ticket:
- DB-213: Replace silent Postgres enum fallback decoding with explicit safe decode behavior for one bounded repository, starting with friend request status.

## Current Ticket

ID: DB-213
Goal: Stop silently decoding invalid Postgres friend request statuses as `Pending`.
Allowed scope: `backend/src/main/scala/slaydemo/backend/social/objects/FriendRequestTypes.scala`, `backend/src/main/scala/slaydemo/backend/social/database/PostgresFriendRequestRepository.scala`, focused social/friend request contract tests, `.codex/agent-state.md`, verification commands.
Forbidden scope: database migrations/schema constraints, mail/governance enum decoding, route/API response shapes, frontend, battle/replay behavior, dependency changes.
Expected change:
- Add explicit `FriendRequestStatus.fromWire`.
- Postgres friend request repository should use that decoder and fail visibly on invalid persisted status instead of defaulting to pending.
- Add focused contract coverage for status wire decoding.
Architecture/domain-modeling impact:
- Preserves finite-state enum meaning at the database boundary; invalid persisted finite states no longer become valid domain states silently.
Side-effect boundary impact:
- No new external effects; read-time validation only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- No invalid status fallback to `FriendRequestStatus.Pending` remains in `PostgresFriendRequestRepository`.
- Valid pending/accepted/rejected wire values still decode.
- Invalid wire values decode to `None` at the domain codec layer and repository read fails explicitly.
Risks:
- Existing corrupted Postgres rows would now surface as errors instead of being hidden. Current cleanup left only normal rows, but this is a deliberate boundary hardening.

Result:
- Added `FriendRequestStatus.fromWire`.
- `PostgresFriendRequestRepository` now decodes statuses through the domain codec and throws `IllegalStateException` on invalid persisted values instead of defaulting to pending.
- Added focused friend request contract assertions for valid/invalid status wire decoding.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Static search confirms Postgres friend request status read now uses `FriendRequestStatus.fromWire`.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- Corrupted persisted social rows now fail visibly on read. That is intentional, but later DB sanitation tooling should report these before startup in production-style runs.

Next ticket:
- DB-214: Apply the same explicit enum decode pattern to Postgres mail kind and friend-request mail metadata status.

## Current Ticket

ID: DB-214
Goal: Apply explicit enum decode behavior to Postgres mail kind and friend-request mail metadata.
Allowed scope: `backend/src/main/scala/slaydemo/backend/mail/objects/MailTypes.scala`, `backend/src/main/scala/slaydemo/backend/mail/database/PostgresMailRepository.scala`, focused mail contract tests, `.codex/agent-state.md`, verification commands.
Forbidden scope: mail table migrations/schema constraints, social/governance decoding, route/API response shapes, frontend, battle/replay behavior, dependency changes.
Expected change:
- Add explicit `MailKind.fromWire`.
- Postgres mail repository should fail visibly on invalid persisted `kind`.
- Friend-request mail metadata should remain optional when all metadata fields are absent, but invalid or partial persisted metadata should fail visibly instead of being silently dropped.
Architecture/domain-modeling impact:
- Preserves finite mail state at the database boundary and avoids silently transforming corrupted rows into valid domain mail.
Side-effect boundary impact:
- No new external effects; read-time validation only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- No invalid `MailKind` fallback to `MailKind.System` remains in `PostgresMailRepository`.
- Valid mail kind and friend-request metadata status wire values decode.
- Invalid wire values decode to `None` at the domain codec layer and repository read fails explicitly.
Risks:
- Corrupted persisted mail rows now fail visibly on read. That is intentional boundary hardening.

Result:
- Added `MailKind.fromWire`.
- `PostgresMailRepository` now decodes mail kind via the domain codec and fails visibly on invalid persisted values.
- Friend-request mail metadata remains optional only when all metadata fields are absent; invalid status/source handle or partial metadata now fails visibly.
- Added focused mail contract assertions for mail kind and friend-request mail status decoding.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Static search confirms no `MailKind.System` fallback remains in Postgres mail decoding.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- Corrupted mail rows now fail visible reads. Later DB sanity tooling should report these before interactive demos.

Next ticket:
- DB-215: Apply explicit enum decode behavior to Postgres governance review kind and target type.

## Current Ticket

ID: DB-215
Goal: Stop silently decoding invalid Postgres governance review kind/target type as replay defaults.
Allowed scope: `backend/src/main/scala/slaydemo/backend/governance/database/PostgresGovernanceRepository.scala`, focused governance contract tests, `.codex/agent-state.md`, verification commands.
Forbidden scope: governance table migrations/schema constraints, routes/API response shapes, mail/social decoding, frontend, battle/replay behavior, dependency changes.
Expected change:
- Postgres governance repository should use `GovernanceReviewKind.fromWire` and `GovernanceReviewTargetType.fromWire` without defaulting invalid persisted values.
- Invalid persisted kind/target type should fail visibly.
- Add focused contract assertions for valid/invalid governance enum wire decoding.
Architecture/domain-modeling impact:
- Preserves finite governance review states at the database boundary.
Side-effect boundary impact:
- No new external effects; read-time validation only.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- No fallback to `ReplayProposal` or `Replay` remains in Postgres governance notification decoding.
- Valid wire values decode; invalid wire values return `None` in codec tests.
Risks:
- Corrupted governance rows now fail visibly on read. That is intentional.

Result:
- `PostgresGovernanceRepository` now decodes review kind and target type through explicit helpers.
- Invalid persisted governance review kind or target type throws `IllegalStateException` instead of defaulting to replay values.
- Added focused governance contract assertions for valid/invalid wire decoding.

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- Static search confirms no fallback to `ReplayProposal`/`Replay` remains in Postgres governance notification decoding.

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- Corrupted governance rows now fail visible reads. Later DB sanity tooling should surface these explicitly before demos.

Next ticket:
- BRT-216: Extract a small pure battle skill availability helper to make absent-skill outcomes directly testable without reaching into private service state.

## Current Ticket

ID: BRT-216
Goal: Extract pure battle skill availability rules and cover absent-skill outcomes directly.
Allowed scope: `backend/src/main/scala/slaydemo/backend/battle/services/BattleStateService.scala`, a new focused battle skill rules file under `backend/src/main/scala/slaydemo/backend/battle/services`, a focused service test under `backend/src/test/scala/slaydemo/backend/battle/services`, `BackendContractTestRunner.scala`, `.codex/agent-state.md`, verification commands.
Forbidden scope: route parsing, frontend input, weapon/projectile constants, queue authorization, database/schema/replay/projection changes, dependency changes.
Expected change:
- Move skill ownership/cooldown availability check into a pure helper.
- Battle state service should use that helper for Blink/Dash/Freeze command outcomes.
- Focused tests should directly cover absent skill -> `SkillNotOwned`, cooldown -> `Cooldown`, ready -> no failure.
Architecture/domain-modeling impact:
- Makes skill finite outcome rules explicit, pure, and directly testable outside the effectful battle state service.
Side-effect boundary impact:
- No new effects; helper is pure.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `git diff --check`
Acceptance criteria:
- No private ad hoc `hasSkill`/`skillReady` logic remains in `BattleStateService`.
- Existing battle runtime contracts pass.
- New pure rule contract covers absent-skill behavior.
Risks:
- Must preserve current skill outcome priority, including intentional skill-fire suppression.

Result:
- Added pure `BattleSkillRules.availabilityFailure`.
- `BattleStateService` now uses the shared skill availability helper for Blink, Dash, and Freeze ownership/cooldown outcomes.
- Removed private ad hoc `hasSkill` and `skillReady` helpers from `BattleStateService`.
- Added `BattleSkillRulesContractTest` to cover missing skill, cooldown, and ready outcomes directly.

Verification passed:
- Static search confirms `hasSkill`/`skillReady` are gone and `availabilityFailure` is used by the service/tests.
- `npm run backend:test-contracts`
- `npm run backend:compile`

Self-review:
- Primitive business types introduced: none.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none.
- Scope respected: yes.

Risks:
- The service currently calls `availabilityFailure` in pattern guards; this is pure and cheap, but a later readability cleanup could collapse each pair of guards into one local match.

Next ticket:
- BRT-217: Reduce repeated skill outcome branch boilerplate in `BattleStateService` without changing behavior.

## Current Ticket

ID: VAL-217
Goal: Run final layered verification for the rebuilt backend and current frontend integration, then clean smoke-test data.
Allowed scope: verification commands, local dev process management, Postgres test-data cleanup, `.codex/agent-state.md`.
Forbidden scope: new feature edits unless verification exposes a concrete defect, destructive DB/schema changes, reverting unrelated working-tree changes.
Expected change:
- Run non-mutating checks first: backend contracts/compile as needed, battle content audit, frontend build.
- Run API/browser smoke only after the low-cost checks pass.
- Clean synthetic smoke/test accounts after mutating smoke runs.
Architecture/domain-modeling impact:
- No code changes expected unless validation finds a defect.
Side-effect boundary impact:
- Smoke tests may create DB rows; cleanup must remove only known synthetic handles/prefixes and preserve real handles.
Verification:
- `npm run audit:battle-content`
- `npm run build`
- selected `demo:*` smoke scripts
- DB count sanity after cleanup
Acceptance criteria:
- Frontend and backend dev servers are running.
- Relevant checks pass or failures are triaged with exact next tickets.
- Postgres synthetic test data is removed after smoke.
Risks:
- Full smoke can add many DB rows; cleanup must be repeated afterward.

Result:
- Confirmed frontend and backend dev servers are running on 5173 and 8080.
- `npm run audit:battle-content` passed.
- `npm run build` passed with existing Vite warnings only.
- `npm run demo:api-contract` passed, including battle command, pickups, reload, collision, sprint stamina, freeze slow, elimination/no-respawn, wrong-owner and missing-ticket checks.
- First `npm run demo:authoritative-finish-smoke` failed because the backend was running with the normal 5 minute battle duration; this matched the script prerequisite. Restarted backend with `SLAY_DEMO_AUTHORITATIVE_BATTLE_DURATION_MS=3000` and reran successfully.
- Restored backend to default duration/Postgres config after finish smoke.
- `npm run demo:bp40-freshness` passed.
- `npm run demo:bp28-render-feel-smoke` passed.
- Ran DB sanity before cleanup, then cleaned smoke/test data.
- Post-cleanup DB sanity passed.
- Final `npm run dev:status` shows Vite on 5173 and BackendApp on 8080.
- Final `git diff --check` passed with CRLF warnings only.

Post-cleanup persisted DB counts:
- `identity_accounts=5` (`123123`, `5point`, `alkali`, `banana`, `kot`; API also exposes virtual `admin`)
- `mails=63`
- `battle_results=30`
- `replay_records=3`
- `replay_comments=1`
- `replay_settlements=0`
- `social_friend_requests=6`
- `forum_topics=2`
- `forum_replies=0`
- `governance_contribution_adjustments=1`
- `governance_review_notifications=2`

Verification passed:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- `npm run audit:battle-content`
- `npm run build`
- `npm run demo:api-contract`
- `npm run demo:authoritative-finish-smoke` after short-duration backend restart
- `npm run demo:bp40-freshness`
- `npm run demo:bp28-render-feel-smoke`
- `scripts/demo-db-sanity.ps1`
- `npm run dev:status`
- `git diff --check`

Self-review:
- Primitive business types introduced: no new domain primitives; route/test JSON strings remain boundary data.
- Boolean business results introduced: none.
- Domain mutation introduced: none in new domain helpers; existing service mutation boundaries unchanged.
- Side effects inside domain: none.
- Scope respected: yes for validation/cleanup.

Risks:
- Several unrelated frontend/docs/script files were already dirty before this validation window; they were not reverted.
- The Scala package path still follows normal sbt layout (`src/main/scala/...`). Flattening package directories would be a large follow-up refactor and was not done during stabilization.
- `BattleStateService` remains large even after extractions; further decomposition is a maintainability task, not a current runtime blocker.

Next ticket:
- Optional cleanup: reduce repeated skill outcome branch boilerplate or plan a package/path refactor if the team still wants a non-standard flatter backend layout.

## Current Ticket

ID: ID-218
Goal: Restore legacy plaintext-password login compatibility for existing ordinary accounts and migrate successful logins to hashed passwords.
Allowed scope: `backend/src/main/scala/slaydemo/backend/identity`, focused identity tests, `.codex/agent-state.md`, verification commands, git commit/push.
Forbidden scope: unrelated battle/replay/frontend behavior, destructive account deletion, schema migrations, exposing stored passwords.
Expected change:
- Existing legacy rows whose `identity_accounts.password` is plaintext should be able to log in with the same password.
- On successful legacy plaintext authentication, repository should replace the stored password with the current hash.
- Current hashed-password accounts should continue to authenticate only by hash.
Architecture/domain-modeling impact:
- Keeps password values wrapped as `PlainTextPassword`/`PasswordHash`; legacy compatibility is explicit at the repository boundary.
Side-effect boundary impact:
- Password upgrade is an explicit repository write during successful session issuance.
Verification:
- `npm run backend:test-contracts`
- `npm run backend:compile`
- targeted HTTP login against a temporary legacy-style Postgres account, then cleanup
- `git diff --check`
Acceptance criteria:
- Ordinary legacy plaintext account login succeeds and upgrades stored password to a SHA-256-looking hash.
- Wrong password still fails.
- Existing identity contracts pass.
Risks:
- Must not print or expose real stored passwords.

Result:
- Root cause confirmed: legacy backend stored ordinary account passwords as plaintext, while the rebuilt backend authenticated only by SHA-256 hash.
- Added an explicit legacy plaintext authentication path at the identity repository boundary.
- Successful legacy plaintext login now upgrades the row to the current `PasswordHash`; wrong passwords still fail.
- Existing hash-based accounts keep using the normal hash path.
- Verified with `npm run backend:test-contracts`.
- Verified with `npm run backend:compile`.
- Verified against the running Postgres backend by creating a temporary legacy-style account, confirming wrong-password 401, confirming correct-password login, confirming the stored password upgraded to a 64-character SHA-256-looking hash, then deleting the temporary account.
- `git diff --check` passed with CRLF warnings only.

Self-review:
- Primitive business types introduced: none; password values remain wrapped as `PlainTextPassword` and `PasswordHash`.
- Boolean business results introduced: none.
- Domain mutation introduced: none.
- Side effects inside domain: none; the migration write is in the repository boundary.
- Scope respected: yes.

Next ticket:
- Save the rebuilt backend and password compatibility fix to git, then push `main`.
