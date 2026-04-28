import type { Hero, ItemPickup, Projectile, SlowField, Vec2, WeaponPickup } from "../../../../domain/types";
import { AUTO_PICKUP_RADIUS, BASE_MOVE_SPEED, STAMINA_DRAIN_PER_SECOND, STAMINA_RECOVER_PER_SECOND } from "../../../../game/constants";
import { WEAPON_DEFINITIONS } from "../../../../game/weapons";
import { applyAutomaticItemPickup, applyAutomaticWeaponPickup } from "../pickups/pickupController";
import { type MotionObstacleBounds } from "../movement/motionController";
import { advanceMovement } from "../movement/movementController";
import { type BotBrainState, type BotTarget, chooseSupportTarget, chooseTarget, findNearestAliveEnemy, getBotFireDelayMs, getEngageRange } from "./botTargeting";
import { buildBotMovementVector, shouldSprint, stabilizeBotMovementVector } from "./botMovement";
import { steerBotDestination } from "./botSteering";
import { distanceBetween, heroSeed } from "./botMath";
import { getCurrentWeapon, resolveWeaponAction } from "../weapons/weaponActionController";
import { resolveBotTactic } from "./botTactics";
import { getFreezeSpeedMultiplier } from "../skills/freezeFieldController";
import { getBotBehaviorProfile } from "./botBehaviorRegistry";
import { buildBotDecisionContext, resolveBotStrategyCommand } from "./botSdk";

export interface BotControllerInput {
  heroes: Hero[];
  playerHeroId: string;
  worldSize: Vec2;
  obstacleBounds: readonly MotionObstacleBounds[];
  weaponPickups: WeaponPickup[];
  itemPickups: ItemPickup[];
  slowFields: SlowField[];
  deltaMs: number;
  elapsedMs: number;
  projectileSequence: number;
  authoritativeHeroIds?: ReadonlySet<string>;
}

export interface BotControllerResult {
  projectileSequence: number;
  projectiles: Projectile[];
}

const botBrains = new Map<string, BotBrainState>();

export function advanceBotActions(input: BotControllerInput): BotControllerResult {
  const deltaMs = Math.max(0, input.deltaMs);
  let projectileSequence = input.projectileSequence;
  const projectiles: Projectile[] = [];

  for (const bot of input.heroes) {
    if (bot.heroId === input.playerHeroId || !bot.alive || input.authoritativeHeroIds?.has(bot.heroId)) {
      continue;
    }

    bot.preparedSkill = null;

    const brain = getBotBrain(bot.heroId);
    const behaviorProfile = getBotBehaviorProfile({ botId: bot.heroId });
    const currentWeapon = getCurrentWeapon(bot);
    const enemyTarget = findNearestAliveEnemy(bot, input.heroes, currentWeapon);
    const pickupTarget = chooseSupportTarget(bot, currentWeapon, input.weaponPickups, input.itemPickups);
    const movementTarget = chooseTarget(bot, currentWeapon, brain, enemyTarget, pickupTarget, input.elapsedMs);
    const aimTarget = enemyTarget ?? movementTarget;
    const tactic = resolveBotTactic({
      bot,
      weapon: currentWeapon,
      enemyTarget,
      pickupTarget,
      brain,
      elapsedMs: input.elapsedMs
    });

    if (currentWeapon.weaponKind === "Gatling" && aimTarget?.kind === "enemy") {
      const engageRange = getEngageRange(currentWeapon.weaponKind);
      if (aimTarget.distance <= engageRange * 1.34 && input.elapsedMs >= brain.firePauseUntilMs) {
        const seed = heroSeed(bot.heroId);
        brain.fireBurstUntilMs =
          input.elapsedMs +
          behaviorProfile.gatlingBurstMinMs +
          Math.round((behaviorProfile.gatlingBurstMaxMs - behaviorProfile.gatlingBurstMinMs) * seed);
        brain.firePauseUntilMs =
          brain.fireBurstUntilMs +
          behaviorProfile.gatlingPauseMinMs +
          Math.round((behaviorProfile.gatlingPauseMaxMs - behaviorProfile.gatlingPauseMinMs) * (1 - seed));
      }
    }

    const movementVector = buildBotMovementVector({
      bot,
      weapon: currentWeapon,
      target: movementTarget,
      enemyTarget,
      tactic,
      brain,
      worldSize: input.worldSize,
      elapsedMs: input.elapsedMs
    });
    const stabilizedMovementVector = stabilizeBotMovementVector(movementVector, brain.lastMoveDirection);
    const movementDeltaMs = Math.min(deltaMs, behaviorProfile.movementUpdateMaxMs);
    const movementDeltaSeconds = movementDeltaMs / 1000;
    const movementResult = advanceMovement({
      alive: bot.alive,
      motionActive: false,
      movement: stabilizedMovementVector,
      sprint: shouldSprint(bot, currentWeapon, movementTarget, tactic),
      stamina: bot.stamina,
      maxStamina: bot.maxStamina,
      lastMoveDirection: brain.lastMoveDirection ?? stabilizedMovementVector,
      deltaMs: movementDeltaMs,
      // Keep bots slightly behind player movement while preventing large-delta catch-up jumps.
      baseMoveSpeed: BASE_MOVE_SPEED * behaviorProfile.movementSpeedScale,
      sprintMultiplier: behaviorProfile.sprintMultiplier,
      staminaDrainPerSecond: STAMINA_DRAIN_PER_SECOND,
      staminaRecoverPerSecond: STAMINA_RECOVER_PER_SECOND,
      speedMultiplier: getFreezeSpeedMultiplier(bot.position, input.slowFields)
    });

    const travelDistance = Math.hypot(movementResult.velocity.x, movementResult.velocity.y) * movementDeltaSeconds;
    if (travelDistance > 0) {
      const origin = { x: bot.position.x, y: bot.position.y };
      const stepCount = Math.max(
        1,
        Math.ceil(movementDeltaMs / behaviorProfile.movementFrameMaxMs),
        Math.ceil(travelDistance / behaviorProfile.maxStepDistance)
      );
      const stepDistance = Math.min(behaviorProfile.maxStepDistance, travelDistance / stepCount);
      let destination = origin;
      let blocked = false;

      for (let step = 0; step < stepCount; step += 1) {
        const steerResult = steerBotDestination({
          position: destination,
          direction: movementResult.velocity,
          distance: stepDistance,
          radius: bot.radius,
          worldSize: input.worldSize,
          obstacleBounds: input.obstacleBounds,
          preferStrafe: Boolean(aimTarget && aimTarget.kind === "enemy")
        });
        blocked = blocked || steerResult.blocked;
        if (distanceBetween(destination, steerResult.destination) < 0.5) {
          destination = steerResult.destination;
          break;
        }
        destination = steerResult.destination;
      }

      bot.position = destination;
      bot.velocity =
        movementDeltaSeconds > 0
          ? {
              x: (destination.x - origin.x) / movementDeltaSeconds,
              y: (destination.y - origin.y) / movementDeltaSeconds
            }
          : { x: 0, y: 0 };

      if (blocked || distanceBetween(origin, destination) < 3) {
        brain.stuckUntilMs = Math.max(brain.stuckUntilMs, input.elapsedMs + 300);
        brain.patrolHeading = (brain.patrolHeading + Math.PI * (1.05 + heroSeed(bot.heroId) * 0.95)) % (Math.PI * 2);
        brain.patrolUntilMs = Math.max(brain.patrolUntilMs, input.elapsedMs + 240);
      }
    } else {
      bot.velocity = { x: 0, y: 0 };
    }

    bot.stamina = movementResult.stamina;
    brain.lastMoveDirection = movementResult.lastMoveDirection;
    bot.facing = aimTarget ? Math.atan2(aimTarget.position.y - bot.position.y, aimTarget.position.x - bot.position.x) : bot.facing;

    applyAutomaticWeaponPickup({
      player: bot,
      weaponPickups: input.weaponPickups,
      itemPickups: input.itemPickups,
      autoPickupRadius: AUTO_PICKUP_RADIUS
    });

    applyAutomaticItemPickup({
      player: bot,
      weaponPickups: input.weaponPickups,
      itemPickups: input.itemPickups,
      autoPickupRadius: AUTO_PICKUP_RADIUS
    });

    const weapon = getCurrentWeapon(bot);
    const weaponDefinition = WEAPON_DEFINITIONS[weapon.weaponKind];
    const builtInBotCommand = buildBotCommand(
      bot,
      movementResult.velocity,
      aimTarget?.position ?? bot.position,
      weapon,
      aimTarget,
      tactic,
      brain,
      input.elapsedMs
    );
    const botCommand = resolveBotStrategyCommand(
      buildBotDecisionContext({
        bot,
        heroes: input.heroes,
        weaponPickups: input.weaponPickups,
        itemPickups: input.itemPickups,
        slowFields: input.slowFields,
        worldSize: input.worldSize,
        deltaMs,
        elapsedMs: input.elapsedMs,
        currentWeapon: weapon,
        defaultCommand: builtInBotCommand
      }),
      builtInBotCommand
    );
    const attackPlan = resolveWeaponAction({
      player: bot,
      weapon,
      weaponDefinition,
      command: botCommand,
      weaponSwitchRemainingMs: 0,
      playerMotionActive: false,
      projectileSequence
    });

    projectileSequence = attackPlan.nextProjectileSequence;
    if (attackPlan.projectiles.length > 0) {
      brain.nextFireAtMs = input.elapsedMs + getBotFireDelayMs(weapon.weaponKind, weaponDefinition.cooldownMs);
    } else if (attackPlan.startedReload && weaponDefinition.reloadMs > 0) {
      brain.nextFireAtMs = input.elapsedMs + weaponDefinition.reloadMs;
    }
    projectiles.push(...attackPlan.projectiles);
  }

  return {
    projectileSequence,
    projectiles
  };
}

function buildBotCommand(
  bot: Hero,
  movementVector: Vec2,
  pointerWorld: Vec2,
  weapon: Hero["weapons"][number],
  aimTarget: BotTarget | null | undefined,
  tactic: ReturnType<typeof resolveBotTactic>,
  brain: BotBrainState,
  elapsedMs: number
) {
  const shouldReload = weapon.weaponKind !== "Gatling" && weapon.ammoInMagazine <= 0 && (weapon.reserveAmmo ?? 0) > 0;
  const fireReady = elapsedMs >= brain.nextFireAtMs;
  const fireDistanceReady = aimTarget
    ? aimTarget.distance <= getEngageRange(weapon.weaponKind) * tactic.fireDistanceFactor
    : false;
  const attackWanted = aimTarget?.kind === "enemy" && fireDistanceReady;
  const gatlingBurstActive = elapsedMs >= brain.firePauseUntilMs && elapsedMs <= brain.fireBurstUntilMs;

  return {
    movement: movementVector,
    aim: {
      x: pointerWorld.x - bot.position.x,
      y: pointerWorld.y - bot.position.y
    },
    pointerWorld: { x: pointerWorld.x, y: pointerWorld.y },
    primaryHeld: weapon.weaponKind === "Gatling" && attackWanted && gatlingBurstActive && fireReady,
    primaryJustPressed: weapon.weaponKind !== "Gatling" && attackWanted && fireReady,
    secondaryJustPressed: false,
    sprint: false,
    switchWeaponDirection: 0 as const,
    toggleBlink: false,
    toggleFreeze: false,
    castDash: false,
    reloadPressed: shouldReload
  };
}

function getBotBrain(heroId: string): BotBrainState {
  const existing = botBrains.get(heroId);
  if (existing) {
    return existing;
  }

  const created: BotBrainState = {
    targetKey: null,
    targetKind: null,
    targetScore: 0,
    targetHoldUntilMs: 0,
    nextFireAtMs: 0,
    patrolHeading: heroSeed(heroId) * Math.PI * 2,
    patrolUntilMs: 0,
    fireBurstUntilMs: 0,
    firePauseUntilMs: 0,
    lastEnemyPosition: null,
    lastEnemySeenAtMs: 0,
    lastMoveDirection: null,
    stuckUntilMs: 0
  };

  botBrains.set(heroId, created);
  return created;
}
