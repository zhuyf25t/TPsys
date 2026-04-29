# Battle Provisional Review

## 1. Purpose

This document consolidates all battle-side `provisional merge` decisions so they can be reviewed together during unified play acceptance.

These tickets are structurally acceptable and code-review clean, but still require real interaction review.

---

## 2. Provisional Ticket List

| Ticket | Area | Why Provisional |
| --- | --- | --- |
| GS-10 | dash / blink / jump motion control | target-point semantics restored by code audit, but final motion feel still needs real play |
| GS-11 | weapon state controller | switch / reload / fire gating look correct in code, but battle pacing still needs live feel verification |
| GS-12 | projectile factory | spawn vectors and launch feel are code-correct, but still need live weapon-feel validation |
| GF-03 | projectile runtime controller | runtime routing was extracted cleanly, but end-to-end projectile feel still needs play review |
| GF-04 | hit / damage / respawn resolver | rule chain was extracted cleanly, but combat fairness feel still needs actual battle review |
| GF-06 | weapon action extraction | scene now consumes fire plans rather than inlining weapon action logic; live feel still needs confirmation |
| GF-07 | combat frame / respawn orchestration | orchestration moved out of scene; still needs whole-loop battle verification |
| GF-09 | runtime-local geometry cleanup | geometry path is structurally correct, but collision feel still benefits from real interaction review |

---

## 3. Shared Risk Themes

These provisional items cluster around the same categories:

- movement-adjacent motion feel
- weapon pacing and fire gating
- projectile launch and travel feel
- combat fairness and visible hit trustworthiness
- geometry/collision behavior under real map pressure

None of these are blocked by known build or type errors.

---

## 4. What Has Already Been Proven

For all provisional tickets, the following is already true:

- allowed file boundaries were respected in the final accepted version
- `npm run build` passes
- `tsc` passes
- architecture direction is correct
- extracted code is cleaner than the original scene-owned implementation

So the remaining uncertainty is **experience validation**, not structural quality.

---

## 5. What Still Needs Human Review

The user should verify:

- motion still feels intentional
- no invisible semantic drift exists in blink/jump/dash
- no hidden fire gating drift exists in switch/reload/overheat
- projectile launch feels trustworthy
- hit feedback still matches actual damage
- respawn and score flow still feel coherent

---

## 6. Recommended Review Order

Run provisional review in this order:

1. movement + motion (`GS-10`, `GF-09`)
2. weapon state and fire loop (`GS-11`, `GF-06`)
3. projectile launch and runtime routing (`GS-12`, `GF-03`)
4. hit / damage / respawn whole-loop review (`GF-04`, `GF-07`)

This order isolates issues faster and avoids mixing motion problems with combat problems.

---

## 7. Resolution Outcomes

Each provisional item should eventually resolve to one of:

- confirmed accepted after unified play review
- minor tuning required
- semantic fix required

The purpose of this document is to make that later review explicit and bounded.
