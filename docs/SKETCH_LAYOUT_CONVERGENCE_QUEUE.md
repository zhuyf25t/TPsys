# Sketch Layout Convergence Queue

## SLC-01 Home Lobby Hierarchy Rewrite

### Goal

Turn `/` from a route portal into a true game lobby:

- player identity becomes visible near the top
- current loadout / skills become first-class
- battle becomes the strongest action
- replay / mails / rating / discussion become secondary branches

### Allowed files

- `src/pages/HomePage.tsx`
- `src/features/home/homeGateway.ts`
- `src/features/loadout/loadoutGateway.ts` if strictly needed
- `src/features/profile/profileGateway.ts` if strictly needed
- `src/app/styles.css`

### Forbidden files

- battle runtime semantics
- contracts / backend
- fake activity metrics

### Most likely risk

- making the page prettier without actually fixing hierarchy

### Acceptance standard

- the player loop is visually obvious
- current loadout is visible near the top
- entering battle clearly dominates over browsing routes
- no fake data is introduced

Status: completed

---

## SLC-02 Competitive Page Hierarchy Rewrite

### Goal

Pull `/rating`, `/contribution`, and `/profile/:handle` closer to a competitive-site / dossier layout:

- stronger profile identity block
- clearer summary / recent record / future history zones
- rating and contribution remain separate boards

### Allowed files

- `src/pages/RatingPage.tsx`
- `src/pages/ContributionPage.tsx`
- `src/pages/ProfilePage.tsx`
- `src/app/styles.css`

### Forbidden files

- fake leaderboard population
- battle runtime
- contracts / backend

### Most likely risk

- turning them into decorative cards without clearer information hierarchy

### Acceptance standard

- profile reads like a player dossier
- rating reads like a standings page
- contribution reads like a separate contribution surface, not a generic summary card
- empty states remain honest

Status: completed

---

## SLC-03 Battle Shell HUD Tightening

### Goal

Keep the current same-route overlay structure, but tighten the battle shell so it reads more like an in-game frame and less like a web page.

### Allowed files

- `src/pages/BattlePage.tsx`
- `src/app/styles.css`

### Forbidden files

- battle runtime semantics
- matching/result data semantics
- contracts / backend

### Most likely risk

- over-layering the runtime and weakening play readability

### Acceptance standard

- `/battle` still keeps the URL
- overlays remain primary
- runtime readability remains intact
- no route-first regression

Status: partially satisfied by the current battle shell

Current note:

- left-top replay / discussion / rating quick entry already exists
- right-bottom mails / social quick entry already exists
- settlement already stays inside `/battle`
- social drawer is now honest and quiet
- further tightening is now polish-level rather than a structural blocker

---

## Current Recommended Order

1. `SLC-01 Home Lobby Hierarchy Rewrite` - completed
2. `SLC-02 Competitive Page Hierarchy Rewrite` - completed
3. `SLC-03 Battle Shell HUD Tightening` - optional polish only
