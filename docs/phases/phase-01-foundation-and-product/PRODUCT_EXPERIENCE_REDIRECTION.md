# Product Experience Redirection

## 1. Why The Current Frontend Still Feels Like An Engineering Panel

Even after shell, routing, contracts, gateways, and backend skeleton work, the current user-visible layer still leaks development intent.

The main problems are:

- too many pages speak in architecture terms rather than player language
- battle return surfaces still look like integration scaffolds
- peripheral pages feel like seed-data boards rather than product pages
- home behaves more like a capability summary than a true game portal

That makes the repository look technically structured but not yet product-facing.

---

## 2. New User-Facing Direction

The player-facing experience should feel closer to:

- a lightweight browser shooter portal
- a competitive ranking/profile site
- a light community/forum wrapper around a strong battle core

The tone should be:

- game-first
- player-facing
- clear and confident
- low on engineering vocabulary

---

## 3. Rules For User-Visible Surfaces

User-visible screens should **not** expose:

- typed contracts
- mock gateways
- adapters
- session IDs
- battle-session identifiers
- engineering milestones
- “future can connect to …” language
- construction-board style copy

These can remain in:

- docs
- internal code comments
- hidden debug surfaces

but not in player-facing primary UI.

---

## 4. Product Rules By Surface

### Home

Must feel like:

- a game landing page
- a portal with strong CTA into battle
- a place where replay, ranking, contribution, profile, discussion, and mails are naturally discoverable

### Battle

Must feel like:

- an actual active game page
- with lightweight shell chrome
- with clear “after battle” continuation paths

### Replay

Must feel like:

- a battle archive / library
- not a debug list

### Mails

Must feel like:

- a notification / inbox surface
- not a raw event dump

### Rating / Contribution / Profile

Must feel like:

- competitive identity surfaces
- not scaffolding around stored numbers

### Discussion

Must feel like:

- a lightweight playable community hub
- not a placeholder topic board

---

## 5. Productization Priorities

1. home page hero and CTA
2. battle result return shell
3. replay library/detail
4. mails inbox surface
5. rating / contribution / profile
6. discussion list/detail
7. final wording/style consistency pass

---

## 6. Acceptance Rule For This Productization Pass

This pass is successful only if a user can look at the front-end and think:

“this is a game product with community and progression surfaces”

instead of:

“this is a technical demo with routes and mock data.”
