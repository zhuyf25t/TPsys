# Slay.one Menu Convergence Plan

## 1. Main Menu Rule

The home route should behave like a game main menu, not a route dashboard.

The first screen should answer only three questions:

1. who is the player
2. what is the current loadout / skill kit
3. how to start the next match immediately

Everything else must be visually subordinate.

---

## 2. Home Main Menu Structure

### Primary layer

The primary layer should contain:

- player identity
- current kit / current skills
- strongest `Start / Enter Next Match` CTA

### Secondary layer

The secondary layer may contain:

- loadout
- replay branch
- one community/ranking branch

But it must not read like a same-weight route matrix.

### Hidden or demoted layer

The following should not dominate the first screen:

- mails
- rating
- contribution
- profile
- discussion
- replay archive as a full library

They may exist as:

- smaller branch cards
- later page sections
- full routes reached intentionally

---

## 3. `/battle` Interaction Structure

The battle route must keep the URL at:

- `/battle`

while the player uses quick in-game menus.

### Left-top information cluster

- replay
- discussion
- rating

These should stay compact and feel like information access.

### Right-bottom message cluster

- mails
- social / friend notifications

These should stay compact and feel like inbox/notification access.

### End-of-match

Post-match should remain inside `/battle` first:

- result
- rating change
- replay action
- mails action
- play again

Only after that should the user branch outward.

---

## 4. `View all` Rule

Quick access stays inside `/battle`.

Only explicit `View all / 查看全部` actions should navigate to:

- `/replay`
- `/replay/:id`
- `/mails`
- `/rating`
- `/contribution`
- `/profile/:handle`
- `/discussion`
- `/discussion/:id`

These routes are text/body/archive surfaces, not the main in-battle interaction layer.

---

## 5. Visual Direction Rules

The interface should converge toward:

- game menu
- game frame
- game panel
- game HUD-like quick access

and away from:

- SaaS dashboard
- big feature grid
- clean information cards
- equal-weight app navigation

This means:

- stronger frame/chrome/material feeling
- fewer same-size buttons
- less explanatory copy
- more hierarchy and fewer visible choices at once

---

## 6. Truthfulness Rule

Convergence toward slay.one-style interaction must not reintroduce fake product signals.

Allowed:

- local truthful battle results
- local replay
- local mails
- local rating
- local profile summary
- local discussion
- honest empty social state

Not allowed:

- fake online counts
- fake social traffic
- fake replay density
- fake inbox density
- fake ranking volume

---

## 7. Current Convergence Outcome

The current product now aligns with this structure in the following way:

- home is centered on player + loadout + next match
- battle uses same-route overlays
- left-top / right-bottom split is implemented
- social is honest and quiet rather than fabricated
- `View all` routes now behave like body/archive destinations

Remaining work is now mostly polish:

- stronger game-material visual identity
- stronger post-match ritual
- unified manual battle feel acceptance
