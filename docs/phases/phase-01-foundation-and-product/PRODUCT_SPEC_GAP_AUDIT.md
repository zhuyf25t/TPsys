# Product Spec Gap Audit

## 1. Intended Product Spec

The target product is not an architecture demo. It is a player-facing competitive portal with this loop:

1. Enter the home portal
2. Pick role / skills in loadout
3. See a short matchmaking step
4. Enter a 6-player match
5. Fill missing seats with bots
6. Play one 5-minute round
7. Return to the product shell after the match
8. Push the result into replay / mails / rating / profile

The desired tone is:

- slay.one-like game portal
- battle-first UX
- competitive community structure
- truthful data
- no engineering language exposed to players

## 2. What Already Matches The Spec

### Truthful local data flow

- battle now produces real local result payloads
- replay is fed by real finished matches
- mails are fed by real post-match notifications
- rating is derived from real finished matches
- profile is derived from real local battle history
- discussion supports real local topic/reply creation
- contribution is derived from real local activity

### Route structure

These routes exist and are product-facing:

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

### Engineering language is mostly hidden

- typed contracts / adapters / app shell / local mock wording is no longer exposed on main user surfaces
- empty states are honest instead of pretending busy systems exist

## 3. Current Gaps Against The Product Spec

### Priority 1: battle loop convergence

#### 3.1 Matchmaking flow

Current state:

- `/battle` now shows a 10-second matchmaking overlay before entering combat
- the player-visible story is now aligned to a 6-player arena
- the page honestly says the remaining seats are filled by bots

Remaining gap:

- the full battle loop still needs manual play validation in the live portal flow
- the pre-match ceremony is truthful now, but still lightweight
- current battle page product flow is now clearly split into matching / current match / settlement / previous match record

#### 3.2 6-player match alignment

Current state:

- runtime now creates a 6-participant match
- the current visible story is `player + 5 bots`

Remaining gap:

- bot capability is still modest
- there is still no richer participant preview or opponent identity layer

#### 3.3 Bot truthfulness

Current real state:

- bots are real runtime participants
- they fill seats honestly
- there is no trustworthy evidence of multi-tier bot skill, ranking, or player-evaluation logic

Required product interpretation:

- bots may be presented as honest seat fillers / training opponents
- the UI must not imply advanced matchmaking intelligence already exists

Current gap:

- bot role is now truthful
- but bot behavior depth is still limited and should not be oversold

#### 3.4 5-minute round consistency

Current state:

- battle result finalization uses 5 minutes
- battle page and home hero now visibly express a 5-minute round
- post-match local result generation is tied to that real five-minute session

Remaining gap:

- still needs player-facing verification in a full manual run

### Priority 2: post-match return flow still feels lightweight

Current state:

- battle result return is now truthful and product-facing
- replay / mails / rating links are real and derived from the finished local match
- replay / mails / rating / profile are now updated from the real local battle result flow

Gap:

- it still falls short of a fully satisfying “post-match ceremony”
- the result surface is better, but still not as strong as a finished commercial game end-of-match screen

### Priority 3: product mood still trails the original vision

Current state:

- product shell is no longer an engineering dashboard
- home page now reads more like a game portal
- loadout has been aligned to the real battle rule set
- replay / mails / rating / profile / discussion now read more like player-facing product pages
- battle page no longer mixes the current match state with the previous match result
- home hero now prefers a real replay thumbnail when one exists

Gap:

- battle visuals are still underused outside the home and battle routes
- some pages are now believable, but still lighter than the original slay.one-like aspiration

### Priority 4: peripheral pages are truthful but still minimal

- replay: now clearly a truthful war-report library, but still compact
- mails: now clearly a real inbox, but still light on depth
- rating/profile/contribution: now productized and truthful, but still single-player/local-history heavy
- discussion: now truthful and locally usable, but still a small local community shell

## 4. What Is Still “Engineering Correct” But Not Yet “Product Correct”

- truthful local data exists and now drives the key surrounding pages
- battle route mounts correctly and now returns real local results
- the remaining gap is no longer “fake data everywhere”; it is “the product still needs stronger emotional and visual conviction”

## 5. Fake Or Half-Closed Loops That Still Exist

These are the most important half-closed loops now:

1. battle result return is real, but still not fully ceremonial
2. bot participation is real, but bot capability is still modest
3. battle-to-portal flow is real, but not yet visually strong enough to sell the full product fantasy
4. replay / mails / rating / profile are real locally, but still not backend-authoritative

## 6. What Must Be Fixed Now

### Must fix in the current phase

1. battle loop product convergence validation
2. bot truthfulness and presentation
3. stronger battle-facing visual identity
4. final wording/ceremony polish around match return

### Can follow after that

5. stronger replay storytelling
6. stronger profile / rating / contribution identity
7. discussion/community depth
8. backend-authoritative data flow

## 7. Real State Summary

### Bot

- real existence: yes
- honest fill-in role: yes
- advanced bot layers: no

### 5-minute round

- real end condition: yes
- visible expression aligned: yes

### Result return

- truthful local result generation: yes
- truthful replay/mails/rating/profile updates: yes
- battle result return now feels like a product surface: mostly yes
- fully satisfying end-of-match ceremony: not yet

### Product visual

- engineering-panel feel removed: yes
- slay.one-like portal energy: materially closer
- competitive community tone: much closer, but still not fully mature

### Loadout

- runtime truth alignment: yes
- player-facing pre-match page: mostly yes
- deeper build/planning system: intentionally not claimed
