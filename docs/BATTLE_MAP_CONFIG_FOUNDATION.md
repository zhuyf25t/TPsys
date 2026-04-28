# Battle Map Config Foundation

This ticket only centralizes the current frontend built-in battle map data.

## What changed

- `frontend/src/game/battleMapCatalog.ts` owns the default map config.
- The default config includes world size, theme id, hero definitions, hero spawn points, inner obstacles, pickup definitions, and pickup spawn pads.
- Legacy public exports remain available through `constants.ts`, `battleContentCatalog.ts`, and `spawn.ts`.

## Current boundary

This is not full-stack map configurability. Backend authoritative geometry, remote map loading, external editors, art/theme refactors, and gameplay layout changes remain separate future work.

## Compatibility intent

The first cut keeps existing gameplay and rendering layout unchanged. Existing callers can continue importing `WORLD_SIZE`, `INNER_OBSTACLES`, `HERO_DEFINITIONS`, `HERO_SPAWN_POINTS`, `WEAPON_PICKUP_DEFINITIONS`, `ITEM_PICKUP_DEFINITIONS`, `WEAPON_PICKUP_SPAWN_POINTS`, and `ITEM_PICKUP_SPAWN_POINTS`.
