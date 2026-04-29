# External Bot Strategy Template

This folder contains a small, offline-friendly example for writing a community bot strategy without learning the full battle runtime.

## Files

- `community-distance-keeper.mjs` exports a plain JavaScript strategy object compatible with the `BotCommandStrategy` concept.
- `community-distance-keeper.plugin.json` is the offline contribution manifest for the example strategy.
- `scripts/smoke-bot-strategy-template.mjs` imports the example with mock observations and validates the strategy shape.
- `scripts/audit-community-bot-package.mjs` validates local `*.plugin.json` bot packages before review.

## Run The Smoke Harness

From the repository root:

```sh
npm run audit:bot-strategy-template
npm run audit:community-bot-package
```

The smoke harness does not start the frontend, backend, Phaser, or any battle session. It only imports the template and calls `decide(context)` with a frozen mock context.

The community package audit is also offline. It reads local manifests under `examples/bots`, imports only the manifest's local relative `entry`, and checks the package boundary. It is a contribution review tool, not a production security sandbox.

## Writing Your Own Strategy

Copy `community-distance-keeper.mjs`, change `strategyId`, and edit `decide(context)`.

Add a sibling `*.plugin.json` manifest:

```json
{
  "pluginId": "my-community-bot",
  "displayName": "My Community Bot",
  "version": "1.0.0",
  "apiVersion": "bot-sdk/v1",
  "entry": "./my-community-bot.mjs",
  "exportName": "myCommunityBotStrategy",
  "strategyIds": ["my-community-bot"],
  "botIds": ["my-community-bot-demo"],
  "permissions": ["bot:read-context", "bot:issue-command"]
}
```

The manifest is intentionally explicit: `entry` must be a local relative `.mjs` file inside the package directory, `exportName` must point at a strategy object, the exported `strategyId` must be listed in `strategyIds`, and permissions are limited to `bot:read-context` and `bot:issue-command`.

Keep strategies safe and portable:

- Do not import internal TypeScript runtime files from this repo.
- Treat `context` as read-only.
- Return a command object with finite `movement` and `aim` vectors.
- Prefer simple, stable behavior over heavy gameplay tuning.
- Let missing fields fall back to the built-in command when integrating through the SDK.
- Do not point manifests at remote URLs or expect production auto-loading.

The current SDK extension point only overrides bot commands. It does not grant ownership of navigation simulation, projectiles, pickups, damage, ratings, replays, backend state, or `GameScene`.
