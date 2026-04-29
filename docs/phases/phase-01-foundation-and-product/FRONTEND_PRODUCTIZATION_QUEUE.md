# Frontend Productization Queue

## 1. Objective

Turn the current shell from a technically correct integration surface into a player-facing product layer.

This queue is intentionally product-first, not architecture-first.

---

## 2. Queue

### PX-01 Home Portal Productization

Goal:

- turn `/` into a true game portal with strong battle CTA, supporting navigation, and clear value framing

Allowed files:

- `src/pages/HomePage.tsx`
- `src/features/home/**`
- `src/shared/ui/ShellLayout.tsx`
- `src/app/styles.css`

Do not touch:

- `GameScene`
- battle runtime-local logic
- contracts
- backend

Risk:

- accidentally reintroducing engineering copy into the hero surface

Acceptance:

- home reads like a player portal, not an architecture summary

---

### PX-02 Battle Result Return Productization

Goal:

- make `/battle` result-return shell feel like a real post-match continuation surface

Allowed files:

- `src/pages/BattlePage.tsx`
- `src/features/battle/mock/**`
- `src/app/styles.css`

Do not touch:

- `GameScene`
- battle runtime-local logic

Risk:

- exposing mock/session identifiers or engineering semantics

Acceptance:

- battle page clearly routes users into replay, mails, and rating as a product path

---

### PX-03 Replay Library Productization

Goal:

- make `/replay` and `/replay/:id` feel like a replay archive rather than a raw list

Allowed files:

- `src/pages/ReplayPage.tsx`
- `src/pages/ReplayDetailPage.tsx`
- `src/features/replay/**`
- `src/app/styles.css`

Risk:

- keeping too much engineering phrasing around replay origin

Acceptance:

- replay library/detail feel like player-facing match history

---

### PX-04 Mails Inbox Productization

Goal:

- make `/mails` feel like an inbox/notifications surface with clear battle carryover

Allowed files:

- `src/pages/MailsPage.tsx`
- `src/features/mails/**`
- `src/app/styles.css`

Acceptance:

- users can read it as inbox UI, not system diagnostics

---

### PX-05 Rating / Contribution / Profile Productization

Goal:

- make ranking and profile surfaces feel closer to a competitive programming / arena profile site

Allowed files:

- `src/pages/RatingPage.tsx`
- `src/pages/ContributionPage.tsx`
- `src/pages/ProfilePage.tsx`
- `src/features/rating/**`
- `src/features/contribution/**`
- `src/features/profile/**`
- `src/app/styles.css`

Acceptance:

- rating, contribution, and profile surfaces feel connected and identity-driven

---

### PX-06 Discussion Productization

Goal:

- make discussion list/detail feel like a lightweight community surface

Allowed files:

- `src/pages/DiscussionPage.tsx`
- `src/pages/DiscussionDetailPage.tsx`
- `src/features/forum/**`
- `src/app/styles.css`

Acceptance:

- discussion feels like a forum shell, not a placeholder list

---

### PX-07 Final Copy And Style Consolidation

Goal:

- unify tone, CTA hierarchy, section labels, and page consistency across the shell

Allowed files:

- `src/app/styles.css`
- page files
- feature mock gateway text seeds

Acceptance:

- product voice is consistent
- engineering language is hidden from user-facing surfaces
