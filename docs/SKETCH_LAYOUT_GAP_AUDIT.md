# Sketch Layout Gap Audit

## 1. User Sketch Translation

The requested layout grammar is not a generic portal site.
It is closer to:

- a game lobby on `/`
- an in-battle HUD shell on `/battle`
- quick-access overlays inside battle
- `View all` routes for archive / reading / profile surfaces
- a competitive-site profile/rating organization similar to Codeforces

The primary player story should be:

1. identify the player
2. show current kit / role / skills
3. push the player into the next match
4. let replay / mails / rating / discussion branch outward from the main loop

---

## 2. What Has Now Converged

### Home

The home route is no longer a same-weight feature matrix.
It now foregrounds:

- player identity
- current kit / skill set
- the next-match CTA

Replay / mails / rating / discussion now read more clearly as secondary branches off the player loop.

### `/battle`

`/battle` now follows the requested grammar much more closely:

- URL stays on `/battle`
- left-top quick entries are information-first
- right-bottom quick entries are message-first
- settlement remains inside the route
- `View all` is the handoff into body/archive routes

### Profile / rating / contribution

These pages now read more like competitive dossier / standings surfaces than generic web cards.

---

## 3. Which Parts Of The Sketch Are Now Correct

### Home as lobby

These parts are now aligned:

- the page starts from player identity rather than route browsing
- the next-match action dominates the page
- current loadout and current skills are visible near the top
- branch routes are demoted into secondary navigation

### Battle as in-route shell

These parts are now aligned:

- same-route overlay behavior
- left-top information cluster
- right-bottom message cluster
- settlement stays inside `/battle`
- archive/profile pages are reached through explicit `View all`

### Competitive pages

These parts are now aligned:

- rating behaves like a standings page
- contribution behaves like a separate activity/record surface
- profile behaves more like a player dossier than a generic social page

---

## 4. Current Remaining Gaps Against The Sketch

The remaining gaps are no longer structural.
They are now mostly quality/polish gaps:

1. Home still has some portal-card DNA, even though hierarchy is now correct.
2. The shell still looks cleaner and more web-like than the rougher game-menu feel in the sketch.
3. Battle status framing is much closer, but can still become more HUD-like.
4. Post-match settlement is correct, but still lighter than a strong in-game ritual.
5. Profile / rating / contribution now have the right hierarchy, but can still gain stronger competitive-site visual character.

---

## 5. Which Entrances Belong In The Top-Left

These belong in the information cluster:

- replay
- discussion
- rating

This is now correct in the current implementation.

---

## 6. Which Entrances Belong In The Bottom-Right

These belong in the message cluster:

- mails
- social / friend notifications

This is also now correct in the current implementation.

The social drawer is now honest:

- no fake friend requests
- no fake community heat
- calm empty state until a true local social layer exists

---

## 7. Which Features Should Exist Mainly As `View all` Pages

These remain correct as reading/body/archive surfaces:

- `/replay`
- `/replay/:id`
- `/mails`
- `/rating`
- `/contribution`
- `/profile/:handle`
- `/discussion`
- `/discussion/:id`

This part of the sketch is now properly implemented.

---

## 8. Top Remaining Mismatches

1. Home can still look more like a game hall and less like a refined web shell.
2. Battle can still gain a stronger game-frame / HUD materiality.
3. Settlement overlay can still become more ceremonial.
4. Competitive pages can still gain stronger dossier / standings visual character.
5. The overall shell is now structurally correct, but not yet at the strongest version of the sketch’s visual language.

---

## 9. Current Judgment

The biggest sketch gap is no longer layout correctness.
It is now visual/game-material convergence:

- the layout hierarchy is now largely correct
- the route/overlay split is now largely correct
- the remaining gap is how strongly the product feels like a game shell instead of a very polished app
