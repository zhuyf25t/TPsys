# Slay.one Convergence Queue

## Completed

### SIC-01 Battle Overlay Shell

Status: accepted

Delivered:

- `/battle` now keeps the URL while opening quick panels
- replay / discussion / rating / mails are overlay-first
- `查看全部` is the route handoff into full pages
- no fake data was introduced

### SIC-02 Battle Settlement Overlay Convergence

Status: accepted

Delivered:

- settlement is distinct from live battle status
- current match and previous match are separated
- the post-match surface behaves like an in-battle continuation rather than a page escape

### SIC-03 Lobby Shell / Home Interaction Redesign

Status: accepted

Delivered:

- shell now reads as a game lobby rather than a dashboard
- battle is the dominant CTA
- home hero prioritizes battle imagery and route flow

### SIC-04 View-All Handoff Consistency

Status: accepted by convergence outcome

Delivered:

- `/replay`, `/mails`, `/discussion`, `/rating`, `/profile`, `/contribution` now read as archive/body pages
- these pages act as explicit `查看全部` destinations rather than being the primary in-battle interaction layer
- truthful local data and honest empty states were preserved

### SIC-05 Honest Social / Friend Drawer

Status: accepted

Delivered:

- battle route keeps a right-side social entry
- the social drawer is now honest and quiet
- no discussion content is misrepresented as friend/social activity
- `查看全部` honestly routes to `/mails` as the current unified notification inbox

---

## Remaining Refinement Queue

The major interaction-convergence work is now complete.
Only refinement-oriented follow-ups remain.

### R1 Stronger Home Hero Materiality

Goal:

- push the lobby shell closer to a game-world hero surface
- strengthen the use of real battle imagery / replay thumbnails

Allowed files:

- `src/pages/HomePage.tsx`
- `src/app/styles.css`

Risk:

- over-styling without improving product clarity

Acceptance:

- stronger game-world mood
- no fake activity or fake spectacle

### R2 Stronger Post-Match Ceremony

Goal:

- make the settlement overlay feel more like a post-match ritual
- improve replay / mails / rating action framing without changing runtime semantics

Allowed files:

- `src/pages/BattlePage.tsx`
- `src/app/styles.css`

Risk:

- making the overlay heavier without improving clarity

Acceptance:

- settlement still stays inside `/battle`
- battle result remains truthful
- no route-first regression

### R3 Optional Honest Social Layer

Goal:

- only if a truthful local social model is later added
- keep the social drawer honest and minimal until then

Allowed files:

- battle-local social storage / drawer presentation

Risk:

- inventing fake social volume

Acceptance:

- no fake friend graph
- no fake notifications
- honest empty state remains valid if no true data exists
