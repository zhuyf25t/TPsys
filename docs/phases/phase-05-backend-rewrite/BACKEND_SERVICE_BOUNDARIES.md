# Backend Service Boundaries

## 1. Shared

Owns:

- common data primitives
- shared IDs
- common response envelopes
- shared policy helpers

Does not own:

- battle authority
- identity session rules
- forum moderation decisions

---

## 2. Identity

Owns:

- account identity
- profile handles
- auth/session issuance
- player-facing identity metadata

Does not own:

- battle simulation
- replay persistence semantics
- forum thread content rules outside identity linkage

---

## 3. Battle

Owns:

- authoritative command intake
- battle runtime
- snapshot generation
- battle event emission
- result generation

Does not own:

- replay long-term browsing UX
- forum discussion storage
- contribution governance rules

Battle is the only service that should own simulation authority.

---

## 4. Replay

Owns:

- replay metadata
- replay retrieval
- replay indexing
- battle result to replay linkage

Does not own:

- live battle simulation
- rating policy

---

## 5. Forum

Owns:

- discussion threads
- posts
- moderation/report handling at thread/post level

Does not own:

- battle authority
- rating rules

---

## 6. Governance

Owns:

- contribution bookkeeping
- ranking/rating policy aggregation
- administrative adjustments
- cross-feature governance/audit policy

Does not own:

- low-level battle simulation
- direct auth
- replay stream production

---

## 7. Battle Contracts Handoff

The most important service handoff is:

- front-end shell / adapter
- formal battle DTOs
- backend battle service API

That seam should be stable before deep backend implementation begins.

---

## 8. Physical Split Policy

At this phase, treat these as **logical services first**.

Physical repo/service separation can remain a later operational choice once:

- contracts are stable
- routes are clearer
- runtime boundaries are proven
