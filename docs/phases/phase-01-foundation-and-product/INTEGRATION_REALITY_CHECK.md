# Integration Reality Check

## 1. Purpose

This document describes the repository as it really stands now, not as an optimistic milestone label.

It separates:

- what is truly integrated and demo-capable
- what is structurally present but still scaffold/mock
- what still requires user play validation

---

## 2. What Is Truly Integrated

### 2.1 Battle Route Mount

These parts are genuinely integrated:

- React app shell bootstraps the application
- React Router owns the route tree
- `/battle` mounts Phaser locally through `createBattleRuntime(...)`
- leaving `/battle` destroys the runtime and clears the HUD mount

This is no longer a single-page Phaser-only app.

### 2.2 Battle-to-Shell Return Path

These parts are now visibly connected:

- `/battle` shows current session shell information
- `/battle` shows mock result-return information
- `/battle` provides clear links into:
  - `/replay`
  - `/mails`
  - `/rating`

Those pages also reflect the same battle-return summary, so the flow is no longer isolated.

### 2.3 Typed Battle Seams

The following typed seams are real:

- formal battle contract DTOs in `src/contracts/battle/*`
- local-to-contract mapping in `src/features/battle/adapters/battleContractAdapter.ts`
- feature-owned mock gateways for shell pages

This is a credible integration runway, not just a set of ideas in docs.

### 2.4 Backend Skeleton

The backend skeleton is genuinely present:

- `backend/src/main/scala`
- logical service boundaries
- layered directories
- `battle/runtime/`
- successful `sbt compile`

It is still skeletal, but it is real code structure, not a README-only promise.

---

## 3. What Is Demo-Capable Right Now

These routes are truly demo-capable at a shell level:

- `/`
- `/loadout`
- `/battle`
- `/replay`
- `/replay/:id`
- `/mails`
- `/rating`
- `/contribution`
- `/profile/:handle`
- `/discussion`
- `/discussion/:id`

The main demo path that is now believable:

`/` -> `/loadout` -> `/battle` -> `/replay` / `/mails` / `/rating`

Secondary believable paths:

- `/` -> `/profile/:handle`
- `/` -> `/discussion` -> `/discussion/:id`
- `/rating` -> `/profile/:handle`

---

## 4. What Is Still Scaffold / Mock

The following are still mock-backed rather than fully implemented:

- replay library data
- replay detail content
- mails content
- rating data
- contribution data
- profile summaries
- discussion summaries/details
- battle result return payload

However, these mocks are now feature-owned and replaceable, not global ad hoc blobs.

---

## 5. What Battle Flows Are Really Connected

### Connected

- battle runtime mount lifecycle
- battle HUD and scene running inside `/battle`
- battle shell status / result-return panel
- battle -> replay path
- battle -> mails path
- battle -> rating path

### Not Yet Fully Connected

- authoritative battle end event driving result return
- true replay generation from battle output
- true mail issuance from battle result
- true rating persistence/update from result

These are still represented by mock shell data, not backend authority.

---

## 6. Battle Verification Status

The battle codebase is structurally strong, but several gameplay-adjacent tickets remain provisional pending unified manual play review.

That means:

- architecture is ready
- demo shell is ready
- final trust in handfeel still requires user play validation

Reference:

- `docs/BATTLE_ACCEPTANCE_CHECKLIST.md`
- `docs/BATTLE_PROVISIONAL_REVIEW.md`

---

## 7. Backend Reality Check

Backend status is:

- skeleton exists
- boundaries are plausible
- placeholders compile
- battle service has runtime ownership

Backend status is **not**:

- fully implemented
- network-integrated with frontend
- persistence-complete

---

## 8. Bottom-Line Reality

This repository is now:

- much more than a Phaser prototype
- not yet a fully integrated end-to-end product
- close enough for a believable demo once the user performs unified battle play acceptance and a small amount of final tuning
