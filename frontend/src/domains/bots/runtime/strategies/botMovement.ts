import type { Hero, Vec2 } from "../../../battle/objects/types";
import { normalizeVector } from "../../../battle/runtime/local/geometry/sceneGeometry";
import { heroSeed } from "./botMath";
import { getEngageRange, type BotBrainState, type BotTarget } from "./botTargeting";
import { type BotTacticState } from "./botTactics";

/** 中文名：构建机器人移动vector（buildBotMovementVector）。游戏职责：在前端 bot 域中组织机器人策略、目标选择和战术决策，辅助本地或演示战斗体验。 */
export function buildBotMovementVector(input: {
  bot: Hero;
  weapon: Hero["weapons"][number];
  target: BotTarget | null;
  enemyTarget: BotTarget | null;
  tactic: BotTacticState;
  brain: BotBrainState;
  worldSize: Vec2;
  elapsedMs: number;
}): Vec2 {
  const fallback = buildFallbackDirection(input.bot, input.brain, input.worldSize, input.elapsedMs);
  const stuckVector = input.elapsedMs < input.brain.stuckUntilMs ? buildStuckEscapeVector(input.bot, input.brain, input.worldSize) : { x: 0, y: 0 };
  const escapedFallback = input.tactic.shouldRetreat
    ? normalizeVector({
        x: fallback.x * 1.18 + Math.cos(input.brain.patrolHeading + Math.PI * 0.5) * 0.35 + stuckVector.x * 0.42,
        y: fallback.y * 1.18 + Math.sin(input.brain.patrolHeading + Math.PI * 0.5) * 0.35 + stuckVector.y * 0.42
      })
    : fallback;

  if (!input.target) {
    const patrolVector = buildPatrolVector(input.bot, input.worldSize, input.elapsedMs, input.brain);
    const recentEnemyVector =
      input.brain.lastEnemyPosition && input.elapsedMs - input.brain.lastEnemySeenAtMs < 3000
        ? normalizeVector({
            x: input.brain.lastEnemyPosition.x - input.bot.position.x,
            y: input.brain.lastEnemyPosition.y - input.bot.position.y
          })
        : { x: 0, y: 0 };

    return ensureMeaningfulVector(
      normalizeVector({
        x: patrolVector.x * 0.76 + recentEnemyVector.x * 1.22 + escapedFallback.x * 0.22 + stuckVector.x * 0.28,
        y: patrolVector.y * 0.76 + recentEnemyVector.y * 1.22 + escapedFallback.y * 0.22 + stuckVector.y * 0.28
      }),
      escapedFallback
    );
  }

  const toTarget = normalizeVector({
    x: input.target.position.x - input.bot.position.x,
    y: input.target.position.y - input.bot.position.y
  });

  if (input.tactic.shouldRetreat && input.enemyTarget) {
    const awayFromEnemy = normalizeVector({
      x: input.bot.position.x - input.enemyTarget.position.x,
      y: input.bot.position.y - input.enemyTarget.position.y
    });
    const escape = normalizeVector({
      x: awayFromEnemy.x * 1.35 + escapedFallback.x * 0.6 + stuckVector.x * 0.34,
      y: awayFromEnemy.y * 1.35 + escapedFallback.y * 0.6 + stuckVector.y * 0.34
    });
    return ensureMeaningfulVector(escape, escapedFallback);
  }

  if (input.target.kind === "pickup") {
    const awayFromEnemy =
      input.enemyTarget && input.enemyTarget.distance < (input.tactic.shouldContestPickup ? 360 : 260)
        ? normalizeVector({
            x: input.bot.position.x - input.enemyTarget.position.x,
            y: input.bot.position.y - input.enemyTarget.position.y
          })
        : { x: 0, y: 0 };

    return ensureMeaningfulVector(
      normalizeVector({
        x: toTarget.x * (1.06 + input.tactic.supportWeight * 0.12) + awayFromEnemy.x * (0.68 + input.tactic.supportWeight * 0.18) + stuckVector.x * 0.2,
        y: toTarget.y * (1.06 + input.tactic.supportWeight * 0.12) + awayFromEnemy.y * (0.68 + input.tactic.supportWeight * 0.18) + stuckVector.y * 0.2
      }),
      escapedFallback
    );
  }

  const engageRange = getEngageRange(input.weapon.weaponKind);
  if (input.target.distance > engageRange * 1.02) {
    return ensureMeaningfulVector(
      normalizeVector({
        x: toTarget.x * (1.08 + input.tactic.pursuitWeight * 0.16) + escapedFallback.x * 0.05 + stuckVector.x * 0.1,
        y: toTarget.y * (1.08 + input.tactic.pursuitWeight * 0.16) + escapedFallback.y * 0.05 + stuckVector.y * 0.1
      }),
      escapedFallback
    );
  }

  const orbit = heroSeed(input.bot.heroId) % 2 === 0 ? 1 : -1;
  const strafe = { x: -toTarget.y * orbit, y: toTarget.x * orbit };
  if (input.target.distance < engageRange * 0.58 || input.elapsedMs < input.brain.stuckUntilMs || input.tactic.enemyClose) {
    return ensureMeaningfulVector(
      normalizeVector({
        x: -toTarget.x * (0.6 + input.tactic.retreatWeight * 0.1) + strafe.x * (0.86 + input.tactic.strafeWeight * 0.12) + stuckVector.x * 0.22,
        y: -toTarget.y * (0.6 + input.tactic.retreatWeight * 0.1) + strafe.y * (0.86 + input.tactic.strafeWeight * 0.12) + stuckVector.y * 0.22
      }),
      escapedFallback
    );
  }

  return ensureMeaningfulVector(
    normalizeVector({
      x: toTarget.x * (0.42 + input.tactic.pursuitWeight * 0.12) + strafe.x * (0.84 + input.tactic.strafeWeight * 0.08) + stuckVector.x * 0.12,
      y: toTarget.y * (0.42 + input.tactic.pursuitWeight * 0.12) + strafe.y * (0.84 + input.tactic.strafeWeight * 0.08) + stuckVector.y * 0.12
    }),
    escapedFallback
  );
}

/** 中文名：shouldsprint（shouldSprint）。游戏职责：在前端 bot 域中组织机器人策略、目标选择和战术决策，辅助本地或演示战斗体验。 */
export function shouldSprint(bot: Hero, weapon: Hero["weapons"][number], target: BotTarget | null, tactic: BotTacticState): boolean {
  if (!target) {
    return false;
  }

  if (tactic.shouldRetreat) {
    return false;
  }

  if (target.kind === "pickup") {
    return target.distance > 760 && bot.stamina > 90 && !tactic.enemyClose && !tactic.enemyPressure;
  }

  return target.distance > getEngageRange(weapon.weaponKind) * 2.35 && bot.stamina > 92 && !tactic.enemyClose && !tactic.enemyPressure;
}

function buildPatrolVector(bot: Hero, worldSize: Vec2, elapsedMs: number, brain: BotBrainState): Vec2 {
  const now = Math.max(0, elapsedMs);

  if (now >= brain.patrolUntilMs) {
    const seed = heroSeed(bot.heroId);
    const span = 1100 + Math.round((2400 - 1100) * seed);
    brain.patrolUntilMs = now + span;
    brain.patrolHeading = (brain.patrolHeading + Math.PI * (0.55 + seed * 0.9)) % (Math.PI * 2);
  }

  const centerBias = normalizeVector({
    x: worldSize.x * 0.5 - bot.position.x,
    y: worldSize.y * 0.5 - bot.position.y
  });
  const sweep = {
    x: Math.cos(brain.patrolHeading),
    y: Math.sin(brain.patrolHeading)
  };
  return normalizeVector({
    x: sweep.x * 1.18 + centerBias.x * 0.52,
    y: sweep.y * 1.18 + centerBias.y * 0.52
  });
}

function buildFallbackDirection(bot: Hero, brain: BotBrainState, worldSize: Vec2, elapsedMs: number): Vec2 {
  if (brain.lastEnemyPosition && elapsedMs - brain.lastEnemySeenAtMs < 2200) {
    return normalizeVector({
      x: brain.lastEnemyPosition.x - bot.position.x,
      y: brain.lastEnemyPosition.y - bot.position.y
    });
  }

  const centerBias = normalizeVector({
    x: worldSize.x * 0.5 - bot.position.x,
    y: worldSize.y * 0.5 - bot.position.y
  });

  return normalizeVector({
    x: Math.cos(brain.patrolHeading) * 0.9 + centerBias.x * 0.6,
    y: Math.sin(brain.patrolHeading) * 0.9 + centerBias.y * 0.6
  });
}

function buildStuckEscapeVector(bot: Hero, brain: BotBrainState, worldSize: Vec2): Vec2 {
  const centerBias = normalizeVector({
    x: worldSize.x * 0.5 - bot.position.x,
    y: worldSize.y * 0.5 - bot.position.y
  });
  const sidestep = {
    x: Math.cos(brain.patrolHeading + Math.PI * 0.5),
    y: Math.sin(brain.patrolHeading + Math.PI * 0.5)
  };

  return normalizeVector({
    x: sidestep.x * 1.06 + centerBias.x * 0.42,
    y: sidestep.y * 1.06 + centerBias.y * 0.42
  });
}

function ensureMeaningfulVector(vector: Vec2, fallback: Vec2): Vec2 {
  if (Math.hypot(vector.x, vector.y) > 0.001) {
    return vector;
  }

  if (Math.hypot(fallback.x, fallback.y) > 0.001) {
    return fallback;
  }

  return { x: 0.6, y: 0.4 };
}

/** 中文名：stabilize机器人移动vector（stabilizeBotMovementVector）。游戏职责：在前端 bot 域中组织机器人策略、目标选择和战术决策，辅助本地或演示战斗体验。 */
export function stabilizeBotMovementVector(current: Vec2, previous: Vec2 | null): Vec2 {
  const currentMagnitude = Math.hypot(current.x, current.y);
  if (currentMagnitude <= 0.001) {
    return previous ?? current;
  }

  if (!previous) {
    return current;
  }

  const previousMagnitude = Math.hypot(previous.x, previous.y);
  if (previousMagnitude <= 0.001) {
    return current;
  }

  const currentDirection = normalizeVector(current);
  const previousDirection = normalizeVector(previous);
  const alignment = currentDirection.x * previousDirection.x + currentDirection.y * previousDirection.y;
  const currentWeight = alignment < 0.25 ? 0.08 : alignment < 0.72 ? 0.13 : 0.17;
  const previousWeight = 1 - currentWeight;

  return ensureMeaningfulVector(
    normalizeVector({
      x: previousDirection.x * previousWeight + currentDirection.x * currentWeight,
      y: previousDirection.y * previousWeight + currentDirection.y * currentWeight
    }),
    currentDirection
  );
}
