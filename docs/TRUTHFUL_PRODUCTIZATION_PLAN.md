# Truthful Productization Plan

## 1. Why The Previous Frontend Was Not Truthful Enough

The earlier product shell looked better than the engineering-facing version, but it still overstated reality in several places:

- replay, mails, rating, contribution, profile, and discussion still rely on hand-written seed data
- battle result return is still driven by a fake summary instead of a real locally generated match result
- some pages look like a populated product even when the underlying feature has not truly happened yet

That means the UI looks more complete than the actual product loop.

The corrective direction is not to add more pages. The corrective direction is to make visible data honest.

---

## 2. Truthfulness Rules

### Show only what really exists

If a user has not actually produced:

- a finished battle
- a replay-worthy match result
- a local rating change
- a local inbox notification
- a recent match history entry

then the UI should not pretend those things exist.

### Prefer real local sources over fake content

Without a backend, user-visible truth can still come from:

- current battle runtime output
- localStorage
- IndexedDB
- local adapter-shaped repositories
- battle-end derived local records

### Empty state is better than fake richness

If there is no real data source yet, the page should use:

- empty states
- first-match guidance
- concise onboarding hints

instead of fake lists, fake traffic, fake leaderboard density, or fake community heat.

### Engineering facts stay internal

The product may internally use:

- contracts
- adapters
- gateways
- local repositories
- session identifiers

but these must stay out of normal player-facing surfaces.

---

## 3. Reality Targets By Surface

### Home

Should present:

- real entry points
- real navigation
- real CTA into battle

It should not claim fake popularity or fake live activity.

Current status:

- uses real replay / discussion / activity summaries when they exist
- otherwise falls back to honest empty states

### Battle

Must become the real local source of:

- match completion
- result summary
- replay capture metadata
- inbox notifications
- rating change input
- recent match history input

Current status:

- battle now finalizes a real local result after a finished five-minute match
- result return, replay entry, mails, rating, and profile updates are all derived from that finished match

### Replay

Should list only real locally recorded matches.

If no real match has been completed yet:

- show an honest empty state
- explain that finished matches will appear here

Current status:

- now driven by real locally archived matches only

### Mails

Should list only real locally generated notifications.

If nothing has happened yet:

- show an empty inbox state

Current status:

- now driven by real post-match notifications only

### Rating

Should show only what can be derived from real local match history.

If there are not enough matches:

- show a lightweight competitive summary for the current player
- avoid pretending there is a populated global ladder

Current status:

- now shows only real rating history derived from finished matches
- keeps an honest empty state before enough results exist

### Contribution

Contribution should not use fabricated ladders or fake activity density.

Current status:

- contribution is now derived from real finished matches and real local discussion activity
- if there is no activity, it remains empty

### Profile

Should show only real locally available data for the current player:

- identity
- current local rating summary
- recent real matches

For other handles:

- either show an honest unavailable state
- or only show data that actually exists locally

Current status:

- the current player profile is derived from real local rating and recent match history
- unknown handles fall back to an honest unavailable state

### Discussion

There are only two truthful choices:

1. build simple local posting / replying
2. show an honest empty-state community shell

Fake thread density is no longer acceptable.

Current status:

- discussion now supports real local topic creation and replies
- no fake thread list remains

---

## 4. Execution Order

1. Replace fake battle return summary with locally derived result data
2. Add a local battle records repository
3. Drive replay from real local battle records
4. Drive mails from real local battle notifications
5. Drive profile and rating from real local battle history
6. Convert contribution and discussion into truthful local activity surfaces
7. Do a final player-facing wording cleanup

Current status:

- steps 1 through 7 are complete
- remaining work is no longer “remove fake data”
- remaining work is “tighten demo realism and close the battle-to-result return loop further”

---

## 5. Acceptance Rule

This phase is successful only when a player can look at the UI and say:

"This is showing what I actually did in the game."

not:

"This is showing believable filler data."
