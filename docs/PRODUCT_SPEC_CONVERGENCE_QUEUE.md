# Product Spec Convergence Queue

## Completed In This Round

### PSC-01 Battle Loop Convergence

Completed:

- 10-second matchmaking overlay added
- visible participant story aligned to 6-player arena
- 5-minute expression aligned across battle and portal copy

### PSC-02 Post-Match Return Productization

Completed:

- truthful post-match settlement cards added
- replay / mails / rating touchpoints made more natural

### PSC-03 Home Hero / Portal Mood Upgrade

Completed:

- home hero redesigned around battle-first portal mood
- real battle assets now anchor the hero area

### PSC-04 Loadout Truth Alignment

Completed:

- loadout page aligned to the actual runtime rules
- removed stale `E 护盾` wording
- now correctly presents `Q 闪现 / E 冲刺 / 右键跳跃`

### PSC-05 Replay / Mails Tightening

Completed:

- replay library feels more like a war-report archive
- replay detail feels more like a real battle report
- mails feels more like a truthful inbox

### PSC-06 Competitive Community Surfaces Polish

Completed:

- rating / contribution / profile / discussion moved closer to competitive-community product pages
- no fake density was introduced

## Remaining Convergence Queue

### PSC-07 Battle Loop Final Truth Pass

#### Goal

Do one final product-spec pass on the core battle loop:

- confirm the live battle shell, five-minute round, bot wording, and post-match return read as one coherent player story
- remove any remaining half-technical or underpowered presentation in the battle shell

#### Allowed files

- `src/pages/BattlePage.tsx`
- minimal adjacent battle-local presentation files if strictly required
- styles only if necessary

#### Forbidden files

- battle runtime semantics
- contracts
- backend skeleton
- unrelated pages

#### Acceptance standard

- battle shell reads like one real loop: matching -> battle -> settlement -> next steps
- no fake multiplayer claims appear
- no engineering vocabulary appears

#### Battle feel risk

- low

Status:

- completed
- current battle page now cleanly separates current match state from previous match records

### PSC-08 Portal Visual Strengthening

#### Goal

Push the shell further toward the intended game-portal feel without fabricating activity:

- home / shell / page chrome visual refinement
- stronger battle-derived imagery
- better CTA hierarchy

#### Allowed files

- `src/pages/HomePage.tsx`
- `src/shared/ui/ShellLayout.tsx`
- `src/app/styles.css`
- minimal home gateway copy changes if needed

#### Forbidden files

- battle runtime
- fake popularity metrics
- contracts/backend

#### Acceptance standard

- portal reads more like a game front door than a neat app shell
- no fake traffic / fake live stats appear
- the strongest CTA remains entering battle

#### Battle feel risk

- none

Status:

- completed
- home hero now prefers real replay imagery when available
- shell and portal wording lean further toward a player-facing game front door

### PSC-09 Local Truth Flow Closure

#### Goal

Close the remaining truthful-data loop gaps:

- ensure replay / mails / rating / profile / contribution / discussion all read from the real local sources cleanly
- remove any stale wording that still sounds like scaffold or setup copy

#### Allowed files

- relevant page files
- relevant local truth gateways/stores
- styles only if needed

#### Forbidden files

- battle runtime
- contracts/backend
- fabricated seed traffic

#### Acceptance standard

- remaining visible data is either real local data or honest empty state
- no user-visible engineering leakage remains
- no “pretend growth” language remains

#### Battle feel risk

- none

## Current Priority Order

1. PSC-08 Portal Visual Strengthening
2. PSC-09 Local Truth Flow Closure
3. Unified battle play acceptance and feel review
