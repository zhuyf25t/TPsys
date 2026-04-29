# Bot SDK Foundation

## Purpose

The bot SDK is the first extension boundary for community or teammate-written bot behavior.

It lets external bot strategies decide battle commands without editing the local battle runtime, weapon runtime, projectile runtime, rating pipeline, replay pipeline, or mutable scene state.

## Current Entry Point

Code entry point:

- `frontend/src/features/battle/runtime-local/bots/botSdk.ts`

Runtime bridge:

- `frontend/src/features/battle/runtime-local/bots/botController.ts`

The controller still builds the existing built-in bot command first. Then it creates a copied `BotDecisionContext` and asks the strategy registry for an override. If no strategy is registered, if a strategy returns no command, or if a strategy throws, the built-in command is used.

This preserves current default bot behavior.

## Strategy Contract

A strategy implements:

```ts
export interface BotCommandStrategy {
  readonly strategyId: string;
  decide(context: BotDecisionContext): BotCommandStrategyDecision;
}
```

Strategies are registered through:

```ts
registerBotStrategy(strategy);
unregisterBotStrategy(strategyId);
listBotStrategyIds();
```

Strategy lookup currently uses normalized candidate ids in this order:

1. bot profile `strategyLabel`
2. bot profile `botId`
3. runtime `botId`

## What A Strategy Can Read

The `BotDecisionContext` exposes copied observation data:

- controlled bot state
- other hero observations
- weapon pickups
- item pickups
- slow fields
- world size
- `deltaMs`
- `elapsedMs`
- current weapon
- bot profile metadata
- default built-in command

These objects are copied before being passed to the strategy. A strategy does not receive live `Hero`, pickup, projectile, or snapshot references.

## What A Strategy Can Output

A strategy returns a partial or full `PlayerCommand`.

Missing or invalid command fields fall back to the built-in command. Movement vectors are normalized when needed, and weapon switch direction is clamped to `-1 | 0 | 1`.

## What A Strategy Must Not Own

The SDK does not allow a strategy to own:

- rating writes
- replay writes
- battle result writes
- direct projectile creation
- direct mutation of heroes, pickups, or slow fields
- direct mutation of the authoritative backend runtime
- direct mutation of `GameScene`

## Current Limitation

The first SDK slice only lets a strategy override the command used for weapon action resolution. Bot navigation, pickup handling, and movement simulation still run through the built-in controller. This is intentional for the first version because it keeps battle feel stable while opening a safe contribution point.

Future slices can add explicit strategy hooks for target choice and movement, but those should be separate tickets with their own semantic review.

## External Strategy Template

Community contributors can start from:

- `examples/bots/community-distance-keeper.mjs`
- `examples/bots/README.md`

The template is plain ESM JavaScript and intentionally does not import internal TypeScript runtime types. It demonstrates a readable, conservative command strategy that keeps distance, aims at the nearest living enemy, looks for health when low, and fires only when the copied weapon observation appears ready.

Offline smoke command:

```sh
npm run audit:bot-strategy-template
```

The smoke harness imports the template with a frozen mock `BotDecisionContext`, verifies the strategy id and command shape, checks finite `movement` / `aim` vectors and boolean command fields, and confirms the strategy does not mutate the provided context.
