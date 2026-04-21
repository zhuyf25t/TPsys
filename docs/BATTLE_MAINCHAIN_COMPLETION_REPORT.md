# BATTLE_MAINCHAIN_COMPLETION_REPORT

## 1. Completed GS Tickets

Battle collection work completed through these GS tickets:

- GS-01 HUD Presenter
- GS-02 Minimap Presenter
- GS-03 Input Command Mapper
- GS-04 Wheel Switch Adapter
- GS-05 Pickup Spawn Resolver
- GS-06 Automatic Pickup Controller
- GS-07 Hero / Weapon / Skill Timers Helper
- GS-08 Event Feed Clock
- GS-09 Movement Controller
- GS-10 Dash / Blink / Jump Motion Controller
- GS-11 Weapon State Controller
- GS-12 Projectile Spawn Helper
- GS-13 Combat Debug Reporter
- GS-14 Legacy Compatibility Isolation Notes

## 2. Accepted vs Provisional

### Accepted

- GS-01
- GS-02
- GS-03
- GS-04
- GS-05
- GS-06
- GS-07
- GS-08
- GS-09
- GS-13
- GS-14

### Provisional

- GS-10
- GS-11
- GS-12

These three are provisionally accepted because:

- file boundaries are clean
- build / typecheck pass
- code-level semantic audit is clear
- no broader forbidden chains were touched
- reliable browser smoke play was not available in the current environment

They still require later user handfeel acceptance.

## 3. What GameScene Still Owns

`src/scenes/GameScene.ts` is significantly thinner than before, but it still owns several major responsibilities:

- Phaser scene lifecycle and top-level orchestration
- arena construction and obstacle registration
- camera follow / pointer offset / occlusion alpha
- hero / projectile / pickup view sync
- world-space VFX triggering
- projectile update / collision / hit / damage / kill / respawn chain
- some remaining skill orchestration glue
- battle result / event wiring still local to the front-end runtime

In other words, `GameScene` is now much closer to a renderer host, but it is not yet a thin scene shell.

## 4. Modules Successfully Extracted

The following boundaries are now extracted out of `GameScene`:

- `src/features/battle/presenters/hudPresenter.ts`
- `src/features/battle/presenters/minimapPresenter.ts`
- `src/features/battle/adapters/inputCommandMapper.ts`
- `src/features/battle/input/wheelSwitchAdapter.ts`
- `src/features/battle/runtime-local/pickups/pickupSpawnResolver.ts`
- `src/features/battle/runtime-local/pickups/pickupController.ts`
- `src/features/battle/runtime-local/timers/heroWeaponSkillTimers.ts`
- `src/features/battle/runtime-local/timers/eventFeedClock.ts`
- `src/features/battle/runtime-local/movement/movementController.ts`
- `src/features/battle/runtime-local/movement/motionController.ts`
- `src/features/battle/runtime-local/weapons/weaponController.ts`
- `src/features/battle/runtime-local/projectiles/projectileFactory.ts`
- `src/features/battle/debug/combatDebugReporter.ts`
- `src/features/battle/debug/legacyCompatibility.ts`

## 5. Remaining Battle Technical Debt

The largest remaining battle-side technical debt is now concentrated in these areas:

1. Projectile update / collision / hit / damage / kill / respawn are still packed inside `GameScene`
2. Camera / occlusion are still hand-managed in scene code
3. Arena construction and static obstacle registration are still scene-owned
4. World VFX triggers are still tightly coupled to runtime and scene state
5. Formal typed battle contracts do not yet exist as a dedicated front-end contract layer
6. The front-end battle still runs as a local runtime instead of consuming a backend battle snapshot

## 6. What Still Needs User Handfeel Acceptance

The following extracted tickets still require later user play acceptance:

- GS-10
  - dash / blink / jump destination and block semantics
- GS-11
  - weapon switch, reload, depletion, heat / overheat feel
- GS-12
  - projectile spawn offset, direction, spread, launch feel

These are not blocked by current code quality, but they should be checked by real play before further gameplay tuning.

## 7. Recommended Next Phase

The recommended next phase is **not another large GS split by default**.

Recommended next step:

1. User performs unified battle handfeel acceptance on provisional tickets
2. Confirm whether current battle front-end feel is preserved well enough
3. Then explicitly choose one of these directions:
   - start typed battle contracts and backend battle service integration
   - start page shell / app shell integration around battle
   - continue deeper runtime extraction for projectile / damage chains

My recommendation is to choose:

- **typed battle contracts + backend integration planning**

Reason:

- the renderer-side collection pass is already strong enough
- continuing to split deeper runtime-first now would move closer to rewriting battle
- the project now benefits more from turning the collected front-end battle asset into a typed, backend-ready renderer consumer
