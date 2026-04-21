# Next Action Queue

## 1. Highest Priority

### 1. Unified Battle Play Acceptance

Use:

- `docs/BATTLE_ACCEPTANCE_CHECKLIST.md`
- `docs/BATTLE_PROVISIONAL_REVIEW.md`

Goal:

- close the remaining provisional gameplay tickets through real play

Why first:

- this is now the largest source of uncertainty

---

## 2. Battle Result Return: Move From Mock To Local Runtime Hook

Goal:

- replace the current mock battle result summary with a result payload that is at least locally derived from battle runtime state

Why:

- demo shell is credible now, but this is the clearest remaining “fake integration” seam

---

## 3. Backend Battle API Alignment

Goal:

- align backend battle/replay/governance placeholder APIs more explicitly with the formal front-end battle contracts

Why:

- improves course-facing architectural credibility
- reduces future frontend/backend seam risk

---

## 4. Frontend Code-Splitting

Goal:

- split battle-heavy bundle from shell pages using route-level lazy loading and/or chunk strategy

Why:

- current Vite bundle warning is non-blocking but real
- improving this would strengthen demo robustness

---

## 5. Replay / Mails / Rating Mock-To-Adapter Upgrade

Goal:

- replace feature mock gateways with adapter-shaped interfaces, still local if necessary

Why:

- makes backend replacement more mechanical
- reduces demo-shell drift between mock and future real integration

---

## 6. Backend Skeleton Enrichment

Goal:

- add slightly stronger placeholder contracts/views inside backend battle/replay/governance modules

Why:

- not urgent for demo day
- useful if the next phase moves toward real backend implementation

---

## 7. UI / Copy Polish Pass

Goal:

- tighten wording, CTA ordering, and shell consistency after unified play review

Why:

- this should happen after gameplay and route integrity are confirmed
