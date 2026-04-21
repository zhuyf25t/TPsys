# Local Data Truth Model

## 1. Purpose

Until a real backend exists, visible player data must come from truthful local sources rather than seed content.

This document defines which surfaces may derive real data locally and which surfaces must fall back to honest empty states.

---

## 2. Primary Local Truth Source

The primary local truth source is:

- a finished battle session

From one finished battle, the frontend can truthfully derive:

- result summary
- score / kills / deaths / placement
- finished timestamp
- replay list entry
- replay detail summary
- inbox notifications
- rating delta input
- profile recent match entry

---

## 3. Local Truth Stores

### Battle Session Store

Purpose:

- track the active local match
- collect runtime summary snapshots needed for finalization

Possible persistence:

- in-memory during match

### Battle History Store

Purpose:

- persist finished local match records

Persistence:

- localStorage first
- IndexedDB later if replay media grows

### Replay Store

Truth source:

- finished battle records

Replay entries should be generated from:

- battle result
- time finished
- map label
- player summary
- optional captured thumbnail from the real canvas

### Mail Store

Truth source:

- events generated when a match finishes

Examples:

- battle archived
- rating changed
- result recorded

### Rating Store

Truth source:

- accumulated local finished matches

Should derive:

- current rating
- recent rating delta
- recent form

Should not claim a populated global ladder unless such data truly exists.

### Profile Store

Truth source:

- local player identity
- local rating store
- local battle history

Should provide:

- player name
- current rating summary
- recent matches
- preferred loadout summary if available

### Contribution Store

Truth source:

- finished battle history
- local discussion activity

Should provide:

- total actions
- battle count
- replay count
- discussion topic count
- discussion reply count

Should not provide:

- fake global contributor ladders
- fabricated multi-user contribution totals

### Discussion Store

Current truthful mode:

- simple local posts/replies persisted locally

No fake threads should remain.

---

## 4. Data Reality By Route

### `/battle`

Truth source:

- active battle runtime
- local active session state

### `/replay`

Truth source:

- local replay store backed by finished matches

### `/replay/:id`

Truth source:

- local replay store

### `/mails`

Truth source:

- local mail store

### `/rating`

Truth source:

- local rating store

### `/contribution`

Truth source:

- local contribution summary derived from battle history and discussion activity

### `/profile/:handle`

Truth source:

- local profile store

For unknown handles:

- honest unavailable state

### `/discussion`

Truth source:

- local discussion store

---

## 5. User-Facing Honesty Rules

If the data is local-only, the UI may still be fully player-facing.
It does **not** need to say “localStorage” or “mock”.

But it must avoid pretending:

- many players exist when they do not
- many replays exist when they do not
- many mails exist when they do not
- a global ranking exists when only local match history exists

---

## 6. Current Recommended Truthful Mode

For the current project stage:

- battle: real local runtime
- replay: real local finished-match archive
- mails: real local result notifications
- rating: real local match-derived rating summary
- profile: real local player summary
- contribution: real local activity summary
- discussion: real local posting and replies
