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
}

export interface BotControllerResult {
  projectileSequence: number;
  projectiles: Projectile[];
}

const botBrains = new Map<string, BotBrainState>();
const GATLING_BURST_MIN_MS = 320;
const GATLING_BURST_MAX_MS = 520;
const GATLING_PAUSE_MIN_MS = 60;
const GATLING_PAUSE_MAX_MS = 150;
const BOT_BASE_MOVE_SPEED = BASE_MOVE_SPEED * 0.88;
const BOT_SPRINT_MULTIPLIER = 1;
const BOT_MOVEMENT_FRAME_MAX_MS = 16;
const BOT_MAX_STEP_DISTANCE = 3.5;

export function advanceBotActions(input: BotControllerInput): BotControllerResult {
  const deltaMs = Math.max(0, input.deltaMs);
  let projectileSequence = input.projectileSequence;
  const projectiles: Projectile[] = [];

  for (const bot of input.heroes) {
    if (bot.heroId === input.playerHeroId || !bot.alive) {
      continue;
    }

    bot.preparedSkill = null;

    const brain = getBotBrain(bot.heroId);
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
        brain.fireBurstUntilMs = input.elapsedMs + GATLING_BURST_MIN_MS + Math.round((GATLING_BURST_MAX_MS - GATLING_BURST_MIN_MS) * seed);
        brain.firePauseUntilMs = brain.fireBurstUntilMs + GATLING_PAUSE_MIN_MS + Math.round((GATLING_PAUSE_MAX_MS - GATLING_PAUSE_MIN_MS) * (1 - seed));
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
    const movementDeltaMs = Math.min(deltaMs, BOT_MOVEMENT_FRAME_MAX_MS);
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
      baseMoveSpeed: BOT_BASE_MOVE_SPEED,
      sprintMultiplier: BOT_SPRINT_MULTIPLIER,
      staminaDrainPerSecond: STAMINA_DRAIN_PER_SECOND,
      staminaRecoverPerSecond: STAMINA_RECOVER_PER_SECOND,
      speedMultiplier: getFreezeSpeedMultiplier(bot.position, input.slowFields)
    });

    const travelDistance = Math.min(
      Math.hypot(movementResult.velocity.x, movementResult.velocity.y) * movementDeltaSeconds,
      BOT_MAX_STEP_DISTANCE
    );
    if (travelDistance > 0) {
      const origin = { x: bot.position.x, y: bot.position.y };
      const steerResult = steerBotDestination({
        position: origin,
        direction: movementResult.velocity,
        distance: travelDistance,
        radius: bot.radius,
        worldSize: input.worldSize,
        obstacleBounds: input.obstacleBounds,
        preferStrafe: Boolean(aimTarget && aimTarget.kind === "enemy")
      });
      const destination = steerResult.destination;
      bot.position = destination;
      bot.velocity =
        movementDeltaSeconds > 0
          ? {
              x: (destination.x - origin.x) / movementDeltaSeconds,
              y: (destination.y - origin.y) / movementDeltaSeconds
            }
          : { x: 0, y: 0 };

      if (steerResult.blocked || distanceBetween(origin, destination) < 3) {
        brain.stuckUntilMs = Math.max(brain.stuckUntilMs, input.elapsedMs + 360);
        brain.patrolHeading = (brain.patrolHeading + Math.PI * (1.05 + heroSeed(bot.heroId) * 0.95)) % (Math.PI * 2);
        brain.patrolUntilMs = Math.max(brain.patrolUntilMs, input.elapsedMs + 300);
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
    const botCommand = buildBotCommand(
      bot,
      movementResult.velocity,
      aimTarget?.position ?? bot.position,
      weapon,
      aimTarget,
      tactic,
      brain,
      input.elapsedMs
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
