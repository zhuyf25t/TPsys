# Battle Acceptance Checklist

## 1. Purpose

This checklist is the unified manual acceptance package for the current local battle prototype.

It exists to validate tickets that were accepted as `provisional` because:

- code boundaries are clean
- build/typecheck pass
- code-level semantic audit is clear
- but reliable browser-side play verification was not available during implementation

This checklist is intended to be executed by the user in one focused play session.

---

## 2. Current Acceptance Scope

The checklist covers:

- movement and stamina feel
- dash / blink / jump behavior
- weapon switching / reload / ammo / heat semantics
- projectile launch feel
- hit / damage / kill / respawn feedback
- camera, minimap, HUD, and pickup observability

It does **not** attempt to validate:

- future backend integration
- replay correctness beyond current local prototype behaviors
- full UX polish of peripheral pages

---

## 3. Test Environment

Run:

```bash
npm install
npm run dev
```

Open the local Vite URL in a desktop browser.

Recommended:

- Chrome or Edge
- 16:9 window
- mouse + keyboard
- no browser zoom

---

## 4. Smoke Flow

Execute this flow in order:

1. Enter battle from the application shell.
2. Move with `WASD`.
3. Sprint with `Shift`.
4. Right-click jump while moving.
5. Use `Q` blink on valid and invalid targets.
6. Use `E` ability.
7. Scroll weapon wheel across owned weapons.
8. Pick up weapons and medkits.
9. Fire pistol, rocket launcher, gatling, and shotgun.
10. Kill at least one bot.
11. Observe respawn.
12. Open replay/mails/rating entry points from the surrounding shell.

---

## 5. Detailed Acceptance Checklist

### 5.1 Scene / Camera / View

- Battle loads without console-breaking errors.
- Player spawns correctly.
- Camera follows the player.
- Pointer offset still feels directional, not detached.
- Occlusion fade still reveals the player near tall obstacles.
- Minimap still updates with player, bots, and pickups.
- HUD remains visible and anchored.

### 5.2 Movement / Stamina

- `WASD` direction matches previous feel.
- Diagonal motion is normalized.
- `Shift` sprint increases speed only while stamina permits.
- Stamina drains while sprinting.
- Stamina recovers when not sprinting.
- Collision against walls still blocks correctly.
- `lastMoveDirection` still drives downstream motion abilities correctly.

### 5.3 Jump / Blink / Dash

- Right-click jump only works when a valid move direction exists.
- Jump cooldown is honored.
- Jump does not clip through walls.
- Jump still has the expected motion feel and landing effect.
- `Q` enters blink preparation state.
- `Q` can be canceled.
- Blink validates the real clicked target point, not a substituted max-range point.
- Invalid blink targets remain invalid.
- Valid blink targets still trigger expected relocation.
- `E` ability still behaves exactly as before the refactor.

### 5.4 Weapon Switching / Reload / Ammo

- Mouse wheel switches weapons in both directions.
- Switch delay bar still appears and blocks firing during switch.
- `R` forces reload when legal.
- Empty magazine with reserve ammo still triggers reload behavior correctly.
- Empty magazine with no reserve ammo still shows the correct no-ammo feedback.
- Disposable weapons still disappear at the correct point after depletion.

### 5.5 Weapon-Specific Checks

#### Pistol

- Single shot fires reliably.
- Ammo decreases correctly.
- Reload completes correctly.

#### Rocket Launcher

- Launch direction is correct.
- Rocket has visible travel.
- Explosion occurs on hit/wall/end of flight.
- Explosion damage is applied.
- Knockback still feels present.

#### Gatling

- Holding fire produces continuous fire.
- Heat rises.
- Overheat blocks fire.
- Cooldown from overheated state resolves correctly.

#### Shotgun

- One trigger consumes one shell.
- Pellet spread is visible.
- Short-range burst damage still feels correct.

### 5.6 Combat Resolution

- Visual hit feedback appears only when damage is really applied.
- Bots lose HP when visibly hit.
- Kill feed entries still appear.
- Score increments on kill.
- Dead heroes disappear / disable as expected.
- Respawn occurs after the expected delay.
- Respawn location and restored state still match expectations.

### 5.7 Pickups / Runtime Presentation

- Weapon pickups respawn correctly.
- Medkits can still be picked and consumed correctly.
- Pickup labels remain readable.
- Pickup points remain visible on minimap and in world.

### 5.8 Shell Integration

- `/battle` can be entered from the shell.
- Return paths to replay / mails / rating are visible.
- Navigation around battle-adjacent shell pages does not break the Phaser scene mount lifecycle.

---

## 6. Provisional Ticket Focus

Pay special attention to the tickets that still depend on user feel verification:

- `GS-10`
- `GS-11`
- `GS-12`
- `GF-03`
- `GF-04`
- `GF-06`
- `GF-07`
- `GF-09`

---

## 7. Acceptance Log Template

Use this template during manual review:

| Area | Pass / Fail | Notes |
| --- | --- | --- |
| Camera / view |  |  |
| Movement / sprint |  |  |
| Jump |  |  |
| Blink |  |  |
| Dash / E ability |  |  |
| Weapon switch / reload |  |  |
| Pistol |  |  |
| Rocket launcher |  |  |
| Gatling |  |  |
| Shotgun |  |  |
| Hit / damage / kill / respawn |  |  |
| Pickups |  |  |
| Shell integration |  |  |

---

## 8. Exit Criteria

Battle unified acceptance is considered complete when:

- all core rows above are reviewed
- no blocking fail remains in movement / motion / weapon / combat chains
- any remaining issues are polish-level, not structural regressions
