# AGENTS.md

## Mission

This repository is in a **GameScene hard-decoupling phase**.

The current highest priority is **not** frontend shell, routing, peripheral pages, or backend implementation.

The current highest priority is:

> Reduce `src/scenes/GameScene.ts` into a true **scene shell / renderer host / glue layer**.

Do not declare completion based on:
- ticket counts
- document counts
- build success alone
- "the battle still runs"

Completion is determined **only by code end-state**.

---

## Non-negotiable principles

### 1. `GameScene` is NOT a battle service
`GameScene` must not remain a giant runtime class.

It may keep:
- scene lifecycle
- top-level update orchestration
- camera host
- player actor / physics glue
- HUD bridge
- scene-side tween / VFX / indicator glue
- minimal Phaser-local adapter glue

It must NOT directly own:
- arena/world builder implementation details
- world view factory / sync implementation details
- projectile runtime/update logic
- hit / damage / kill / respawn main chain
- pickup lifecycle runtime logic
- weapon runtime controller logic
- combat frame orchestration logic
- formatting / display dictionary / label helper logic
- geometry / resolver / lookup logic that can live elsewhere
- temporary legacy/debug residue on the main path

### 2. One agent, one ticket, one merge decision
At any given time:
- one worker
- one concrete task
- one review
- one decision: accepted / provisional / rollback

No parallel execution across multiple code tickets.

### 3. The architect is not the coder
The main Codex agent acts as:
- architect
- planner
- reviewer
- integrator
- stop-condition judge

Large business-code edits should be delegated to a worker/subagent.

### 4. Do not stop early
Do NOT declare completion because:
- previous GF tickets are done
- a document says "phase complete"
- the file got somewhat smaller
- the game still runs

Only stop when hard gate criteria are met.

---

## Hard gate completion criteria

GameScene hard-decoupling is complete only when **all** conditions below are satisfied:

### A. Responsibility gate
`GameScene.ts` no longer directly implements:
- arena/world build details
- world view create/sync details
- projectile progression/update chain
- hit/damage/kill/respawn chain
- pickup/weapon/combat-frame runtime chains
- display label helpers
- runtime-local geometry / resolver helpers
- legacy residue on the hot path

### B. Role gate
All remaining methods inside `GameScene.ts` can be justified as one of:
- scene lifecycle
- orchestration
- camera host
- physics glue
- HUD bridge
- tween/VFX/indicator glue
- minimal Phaser-local adapter glue

### C. Size gate
Target:
- `GameScene.ts <= 25 KB`
- `GameScene.ts <= 700 LOC`

Stretch goal:
- `<= 20 KB`
- `<= 550 LOC`

If these targets are not reached, completion is NOT automatically allowed.
Instead, a method-by-method proof must be produced showing why every remaining method is legitimate scene-host glue.

### D. Duplicate-logic gate
No duplicated formatting / mapping / presentation helper should remain across:
- `GameScene.ts`
- presenters
- renderer helpers
- world view factories

### E. End-state proof gate
Before declaring completion, generate:

`docs/GAMESCENE_HARD_GATE_COMPLETION_REPORT.md`

It must include:
1. final LOC
2. final file size
3. remaining methods
4. why each remaining method belongs in scene host
5. all extracted responsibilities
6. remaining technical debt
7. whether any provisional pieces remain

---

## Current allowed scope

Until hard-decoupling is complete, do NOT switch focus to:
- frontend completion shell
- replay/mails/profile/discussion productization
- typed contracts rollout
- backend microservice implementation

Those are later phases.

Current scope is strictly:
- method-by-method audit of `GameScene.ts`
- extraction of remaining non-scene-host responsibilities
- hard cleanup until end-state criteria are satisfied

---

## Worker rules

Workers may:
- extract one responsibility at a time
- create focused helpers/controllers/builders/presenters/adapters
- modify `GameScene.ts` only as needed to thin it

Workers may NOT:
- bundle multiple unrelated tickets
- redesign the whole project
- touch peripheral systems
- silently change gameplay semantics
- use housekeeping/worklog files as part of ticket output

---

## Review rules

Every code task must be reviewed for:
- file boundary cleanliness
- build / typecheck success
- semantic drift
- battle-feel risk
- whether the responsibility actually left `GameScene`

If there is uncertainty, do not claim completion.

---

## Stop conditions

Stop immediately if any of the following happens:
1. build fails
2. typecheck fails
3. worker touches unrelated business-code boundaries
4. semantics drift is suspected
5. battle-feel critical logic cannot be verified
6. continuing would require switching to another project phase
7. hard gate completion cannot be justified

When stopped:
- do not continue silently
- produce a stop report with cause, status, and best next action

---

## Return conditions

Do not return after every ticket.

Return only when:
1. a stop condition is triggered
2. `GAMESCENE_HARD_GATE_COMPLETION_REPORT.md` is complete and hard gate criteria are met

---

## Quality bias

Bias toward:
- smaller surface area
- fewer responsibilities inside `GameScene`
- clearer scene-host boundaries
- no hidden formatting/runtime logic leakage
- no premature "done"

The user prefers **real decoupling**, not formal closure.

---

## Autonomous execution policy

The architect agent should keep running until one of these two outcomes occurs:
1. true completion
2. true hard stop

Do not stop merely because a ticket is done, a document is written, or a partial milestone is reached.

## Self-healing first, stop later

When problems occur, prefer automatic self-repair before returning to the user.

The architect agent must attempt local recovery for:
- build failures
- typecheck failures
- housekeeping/worklog/doc-only out-of-scope edits
- small boundary pollution that can be reverted cleanly
- provisional-vs-accepted uncertainty caused only by missing interactive validation

For these cases, the architect should:
1. isolate the issue
2. revert unrelated changes if needed
3. retry with one bounded worker
4. re-audit
5. continue if the issue is resolved

Maximum automatic repair attempts per issue: 2
After 2 failed repair attempts, escalate as a hard stop.

## True hard stops

Return to the user only if one of these happens:
- 2 consecutive repair attempts fail
- core business-code boundaries are crossed and cannot be cleanly repaired
- gameplay semantics drift is suspected and cannot be confidently restored
- continuing would force a phase change
- the architect cannot determine accepted / provisional / rollback

## Housekeeping false-stop rule

Changes to logs, worklogs, scratch docs, or other housekeeping files are not hard stops.
They should be auto-reverted or excluded from the ticket review unless they affect core repo behavior.

## Completion standard

Completion is determined by code end-state, not by ticket count.

For GameScene:
- it must behave as scene shell / renderer host / glue layer
- runtime, resolver, builder, sync, formatting, and geometry residue must be removed or justified
- if size targets are missed, a method-by-method justification is required

## Return conditions

Do not return after each ticket.
Return only when:
1. the hard-gate completion report is finished and completion is justified
2. a true hard stop has been reached