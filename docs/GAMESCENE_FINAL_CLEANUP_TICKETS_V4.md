# GameScene Final Cleanup Tickets V4

## 1. Purpose

This document records the final hard-gate cleanup ticket set under the V4 standard.

Unlike earlier phases, this set is judged entirely by code terminal state.

---

## 2. Tickets That Were Actually Needed

## GF-08: Presentation Catalog Extraction

### Goal

Remove scene-owned display formatting / label / dictionary helpers.

### Allowed files

- `src/scenes/GameScene.ts`
- `src/features/battle/presenters/battleDisplayCatalog.ts`
- minimal consumers:
  - `src/features/battle/presenters/hudPresenter.ts`
  - `src/features/battle/renderer/entities/worldViewFactory.ts`

### Forbidden files

- `src/features/battle/runtime-local/**`
- `src/features/battle/adapters/**`
- `src/ui/Hud.ts`
- `src/game/**`

### Most likely risk

- display text drift
- pickup tint mapping drift

### Acceptance criteria

- build / typecheck pass
- no display/formatting helper remains in `GameScene`
- HUD timer / labels / pickup tint semantics remain equivalent

### Provisional merge allowed

- yes

---

## GF-09: Scene Utility / Legacy Residue Cleanup

### Goal

Remove runtime-local geometry helpers and legacy residue from the scene.

### Allowed files

- `src/scenes/GameScene.ts`
- `src/features/battle/runtime-local/geometry/sceneGeometry.ts`
- if strictly necessary:
  - `src/features/battle/debug/legacyCompatibility.ts`

### Forbidden files

- `src/features/battle/presenters/**`
- `src/features/battle/adapters/**`
- `src/features/battle/runtime-local/weapons/**`
- `src/features/battle/runtime-local/pickups/**`
- `src/ui/Hud.ts`
- `src/game/**`

### Most likely risk

- destination clipping drift
- recoil / knockback geometry drift
- accidental removal of live code paths

### Acceptance criteria

- build / typecheck pass
- no runtime-local geometry helpers remain in `GameScene`
- no legacy runtime residue remains in `GameScene`

### Provisional merge allowed

- yes

---

## GF-10: Combat Obstacle Collision Adapter Cleanup

### Goal

Remove the last inline obstacle-collision geometry residue from `GameScene.updateProjectiles()`.

### Allowed files

- `src/scenes/GameScene.ts`
- `src/features/battle/runtime-local/geometry/sceneGeometry.ts`

### Forbidden files

- `src/features/battle/runtime-local/combat/**` except minimal consumption if unavoidable
- `src/features/battle/presenters/**`
- `src/features/battle/adapters/**`
- `src/ui/Hud.ts`
- `src/game/**`

### Most likely risk

- projectile collision semantic drift

### Acceptance criteria

- build / typecheck pass
- no inline obstacle-collision geometry remains in `GameScene`
- projectile collision behavior remains equivalent

### Provisional merge allowed

- yes

---

## 3. Remaining Required Tickets

- none

The V4 hard-gate cleanup set is exhausted.

---

## 4. Completion Rule

Completion under V4 is allowed only if:

1. `docs/GAMESCENE_HARD_AUDIT_V4.md` shows no remaining `MUST EXTRACT` methods
2. the remaining methods are all justified as scene lifecycle / orchestration / camera host / physics glue / HUD bridge / tween-VFX glue / minimal Phaser-local adapter glue
3. `docs/GAMESCENE_HARD_GATE_COMPLETION_REPORT.md` is produced with final size and remaining-method proof

These conditions are now satisfied.
