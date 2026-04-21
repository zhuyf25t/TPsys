# Slay.one Interaction Gap Audit

## 1. Current Interaction State

The frontend now reads much closer to a slay.one-like game portal than to an engineering dashboard.

The major interaction wins already in place are:

- the shell is now lobby-first rather than sidebar-first
- `/battle` keeps the URL while opening quick drawers
- replay / discussion / rating live in a left-top quick cluster
- mails / social live in a right-bottom quick cluster
- post-match resolution now appears as an in-route settlement overlay
- truthful local data or honest empty states are used throughout

This means the interaction grammar has materially converged.
The remaining gap is no longer structural; it is mostly about deeper product mood and richer ceremony.

---

## 2. What Has Been Fixed

### Home / lobby shell

The old dashboard grammar has largely been removed:

1. the global shell is now top-masthead / lobby-like rather than left-rail / control-panel-like
2. the home hero now prioritizes battle imagery and battle entry
3. portal cards feel more like route entrances inside a game hall
4. the route hierarchy now reads as "enter battle first, then branch outward"

### `/battle`

The battle route is no longer primarily a routed web page:

1. quick access is overlay-first
2. URL stays on `/battle` for replay / discussion / rating / mails / social drawers
3. settlement happens inside `/battle`
4. `查看全部 / View all` is now the explicit handoff into text-heavy routes

### Truthfulness

The social/friend gap has also been corrected:

1. the social drawer no longer borrows discussion items
2. if there is no true local social data, it shows a calm empty state
3. no fake friend graph, fake online count, or fake activity is shown

---

## 3. What Still Differs From The Target Interaction Feel

The remaining mismatches are subtler now:

1. the battle shell is interactionally correct, but could still gain a stronger "game frame" feel
2. the settlement overlay is truthful and useful, but not yet a full "post-match ceremony"
3. the home hero feels like a game portal, but not yet as visually forceful as the best slay.one-like outer shells
4. the social drawer is now honest, but intentionally quiet because no real local social system exists
5. View-all pages are now the right destinations, but they still read more like clean product pages than gritty in-world panels

---

## 4. Overlay-First Structure Now In Place

These interactions now correctly prefer overlays inside `/battle`:

### Left-top cluster

- replay quick panel
- discussion quick panel
- rating quick panel

### Right-bottom cluster

- mails quick panel
- social / friend notifications quick panel

### End-of-match

- settlement overlay
- next actions: play again / replay / mails / rating

This is now aligned with the requested route strategy.

---

## 5. What Correctly Remains As `View all` Pages

These routes now function as proper full-page destinations after explicit handoff:

- `/replay`
- `/replay/:id`
- `/mails`
- `/rating`
- `/contribution`
- `/profile/:handle`
- `/discussion`
- `/discussion/:id`

This split is now working as intended:
- overlay-first inside `/battle`
- archive/body pages after explicit navigation

---

## 6. Visual Tone Gap

The product no longer reads like an engineering dashboard, but it still has room to move further toward:

- metal / tactical frame language
- stronger battle-scene image dominance
- a more forceful game-world hero composition
- richer battle-shell material treatment

This is now a polish gap, not a structural interaction gap.

---

## 7. Truthfulness Gap

Truthfulness is now in a much better state.

What is already truthful:

- local battle results
- replay list/detail
- mails
- rating
- profile summary
- contribution summary
- discussion topics/replies
- social drawer empty state

What is still limited:

- there is still no real local social graph or friend-request stream
- battle result return is still local-authoritative, not backend-authoritative

These are honest limitations, not deceptive ones.

---

## 8. Current Top Remaining Gaps

1. stronger game-material visual identity on home and battle shell
2. stronger post-match ritual / ceremony
3. deeper social layer only if a truthful local model is actually added
4. unified manual battle play acceptance

---

## 9. Current Judgment

The front-end now clearly follows the requested slay.one-style interaction logic:

- battle stays on `/battle`
- quick access is overlay-first
- `View all` drives route transitions
- data is truthful
- empty states are honest
- the shell now feels like a game lobby rather than a developer console

What remains is refinement, not a mismatch of interaction grammar.
