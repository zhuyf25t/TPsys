# Battle Weapon Extension Foundation

This ticket introduces a frontend-local weapon runtime profile layer for the local battle runtime.

The profile is intentionally narrow. It describes the client runtime pieces that previously lived as weapon-name branches:

- trigger mode: `pressed` or `held`
- ammo mode: `magazine` or `heat`
- recoil strength
- muzzle VFX numbers
- projectile spawn plan: `single`, `spread`, or `pellets`

Current coverage:

- `Pistol`: pressed, magazine, single projectile
- `RocketLauncher`: pressed, magazine, single projectile with rocket muzzle pulse
- `Gatling`: held, heat, one spread projectile
- `Shotgun`: pressed, magazine, pellet spread

This is not a backend authoritative weapon plugin system. It does not define server validation, replicated contracts, skill behavior, art assets, or backend-owned balance. Backend alignment should be handled by a separate ticket after the local runtime boundary is stable.
