# External Bot Strategy Template

This folder contains a small, offline-friendly example for writing a community bot strategy without learning the full battle runtime.

## Files

- `community-distance-keeper.mjs` exports a plain JavaScript strategy object compatible with the `BotCommandStrategy` concept.
- `scripts/smoke-bot-strategy-template.mjs` imports the example with mock observations and validates the strategy shape.

## Run The Smoke Harness

From the repository root:

```sh
npm run audit:bot-strategy-template
```

The smoke harness does not start the frontend, backend, Phaser, or any battle session. It only imports the template and calls `decide(context)` with a frozen mock context.

## Writing Your Own Strategy

Copy `community-distance-keeper.mjs`, change `strategyId`, and edit `decide(context)`.

Keep strategies safe and portable:

- Do not import internal TypeScript runtime files from this repo.
- Treat `context` as read-only.
- Return a command object with finite `movement` and `aim` vectors.
- Prefer simple, stable behavior over heavy gameplay tuning.
- Let missing fields fall back to the built-in command when integrating through the SDK.

The current SDK extension point only overrides bot commands. It does not grant ownership of navigation simulation, projectiles, pickups, damage, ratings, replays, backend state, or `GameScene`.
