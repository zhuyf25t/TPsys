import type { Hero, PlayerCommand, Projectile, Vec2, WeaponState } from "../../../../objects/battle/types";
import { resolveProjectileBirthPosition } from "../../game/projectileBirth";
import type { WeaponDefinition } from "../../game/weapons";
import { createProjectileSpawn } from "../projectiles/projectileFactory";
import { requestWeaponReload, resolveWeaponFire, type WeaponBlockReason } from "./weaponController";
import {
  getWeaponRuntimeProfile,
  resolveWeaponAmmoMode,
  type WeaponMuzzleVfxProfile,
  type WeaponRuntimeProfile
} from "./weaponRuntimeProfiles";

export interface WeaponActionFloatingText {
  text: string;
  tone: "neutral" | "warning" | "error" | "success";
}

export interface WeaponActionMuzzleVfx {
  position: Vec2;
  color: number;
  radius: number;
  sparks: number;
  impactSparkColor?: number;
  pulse?: {
    radius: number;
    color: number;
  };
}

export interface WeaponActionPlan {
  canFire: boolean;
  blockReason?: WeaponBlockReason | "cooldown";
  floatingText?: WeaponActionFloatingText;
  projectiles: Projectile[];
  muzzle?: WeaponActionMuzzleVfx;
  recoilStrength: number;
  startedReload: boolean;
  nextProjectileSequence: number;
}

export interface WeaponActionContext {
  player: Hero;
  weapon: WeaponState;
  weaponDefinition: WeaponDefinition;
  command: PlayerCommand;
  weaponSwitchRemainingMs: number;
  playerMotionActive: boolean;
  projectileSequence: number;
  randomFn?: () => number;
}

/** 中文名：获取当前武器（getCurrentWeapon）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getCurrentWeapon(hero: Hero): WeaponState {
  const weapon = hero.weapons[hero.currentWeaponIndex];
  if (!weapon) {
    throw new Error(`Missing weapon for ${hero.heroId}`);
  }

  return weapon;
}

/** 中文名：解析武器action（resolveWeaponAction）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveWeaponAction(context: WeaponActionContext): WeaponActionPlan {
  const randomFn = context.randomFn ?? Math.random;
  const runtimeProfile = getWeaponRuntimeProfile(context.weapon.weaponKind);
  const ammoMode = resolveWeaponAmmoMode(runtimeProfile, context.weaponDefinition);
  const basePlan: WeaponActionPlan = {
    canFire: false,
    projectiles: [],
    recoilStrength: 0,
    startedReload: false,
    nextProjectileSequence: context.projectileSequence
  };

  if (!context.player.alive || context.player.preparedSkill !== null || context.playerMotionActive || context.weaponSwitchRemainingMs > 0) {
    return {
      ...basePlan,
      blockReason: "switching"
    };
  }

  if (context.command.reloadPressed && ammoMode === "magazine") {
    return startReload(context, basePlan, runtimeProfile);
  }

  const fireResolution = resolveWeaponFire({
    player: context.player,
    weapon: context.weapon,
    weaponDefinition: context.weaponDefinition,
    weaponRuntimeProfile: runtimeProfile,
    command: context.command,
    weaponSwitchRemainingMs: context.weaponSwitchRemainingMs,
    playerMotionActive: context.playerMotionActive
  });

  if (!fireResolution.result.canFire) {
    if (fireResolution.result.reason === "empty") {
      return {
        ...basePlan,
        blockReason: "empty",
        floatingText: { text: "弹药耗尽", tone: "neutral" }
      };
    }

    if (fireResolution.result.reason === "reloading" && (context.weapon.reserveAmmo ?? 0) > 0) {
      return startReload(context, basePlan, runtimeProfile);
    }

    if (fireResolution.result.reason === "overheated") {
      return {
        ...basePlan,
        blockReason: "overheated",
        floatingText: { text: "过热！", tone: "error" }
      };
    }

    return {
      ...basePlan,
      blockReason: fireResolution.result.reason ?? "cooldown"
    };
  }

  const aimAngle = Math.atan2(context.command.aim.y, context.command.aim.x);
  const direction = {
    x: Math.cos(aimAngle),
    y: Math.sin(aimAngle)
  };
  const muzzle = resolveProjectileBirthPosition({
    ownerPosition: context.player.position,
    direction,
    ownerRadius: context.player.radius,
    projectileRadius: context.weaponDefinition.projectileRadius
  });
  const projectileSpawn = createWeaponProjectiles({
    context,
    runtimeProfile,
    aimAngle,
    randomFn
  });
  const projectiles = projectileSpawn.projectiles;
  const nextProjectileSequence = projectileSpawn.nextProjectileSequence;
  const muzzleVfx = createMuzzleVfx(muzzle, runtimeProfile.muzzleVfx);
  const recoilStrength = context.weaponDefinition.recoilStrength;

  if (context.weapon.ammoInMagazine === 0 && (context.weapon.reserveAmmo ?? 0) > 0 && ammoMode === "magazine") {
    const reloadResult = requestWeaponReload({
      player: context.player,
      weapon: context.weapon,
      weaponDefinition: context.weaponDefinition,
      weaponRuntimeProfile: runtimeProfile,
      weaponSwitchRemainingMs: context.weaponSwitchRemainingMs
    });

    if (reloadResult.started) {
      context.weapon.reloadRemaining = context.weaponDefinition.reloadMs;
      return {
        canFire: true,
        projectiles,
        muzzle: muzzleVfx,
        recoilStrength,
        startedReload: true,
        nextProjectileSequence,
        floatingText: { text: "正在换弹", tone: "neutral" }
      };
    }
  }

  const showOverheatText = ammoMode === "heat" && context.weapon.overheated;

  return {
    canFire: true,
    projectiles,
    muzzle: muzzleVfx,
    recoilStrength,
    startedReload: context.weapon.reloadRemaining > 0 && context.weaponDefinition.reloadMs > 0,
    nextProjectileSequence,
    floatingText: showOverheatText ? { text: "过热！", tone: "error" } : undefined
  };
}

function startReload(
  context: WeaponActionContext,
  basePlan: WeaponActionPlan,
  runtimeProfile: Readonly<WeaponRuntimeProfile>
): WeaponActionPlan {
  const reloadResult = requestWeaponReload({
    player: context.player,
    weapon: context.weapon,
    weaponDefinition: context.weaponDefinition,
    weaponRuntimeProfile: runtimeProfile,
    weaponSwitchRemainingMs: context.weaponSwitchRemainingMs
  });

  if (!reloadResult.started) {
    return basePlan;
  }

  context.weapon.reloadRemaining = context.weaponDefinition.reloadMs;
  return {
    ...basePlan,
    canFire: false,
    startedReload: true,
    floatingText: { text: "正在换弹", tone: "neutral" }
  };
}

function randomBetween(randomFn: () => number, min: number, max: number): number {
  return min + (max - min) * randomFn();
}

interface WeaponProjectileSpawnInput {
  context: WeaponActionContext;
  runtimeProfile: Readonly<WeaponRuntimeProfile>;
  aimAngle: number;
  randomFn: () => number;
}

interface WeaponProjectileSpawnResult {
  projectiles: Projectile[];
  nextProjectileSequence: number;
}

function createWeaponProjectiles(input: WeaponProjectileSpawnInput): WeaponProjectileSpawnResult {
  const projectiles: Projectile[] = [];
  let nextProjectileSequence = input.context.projectileSequence;
  const spawnProjectile = (angle: number): void => {
    projectiles.push(
      createProjectileSpawn({
        projectileSequence: nextProjectileSequence,
        player: input.context.player,
        definition: input.context.weaponDefinition,
        angle
      })
    );
    nextProjectileSequence += 1;
  };

  const plan = input.runtimeProfile.projectileSpawnPlan;
  switch (plan.mode) {
    case "single":
      spawnProjectile(input.aimAngle);
      break;
    case "spread":
      for (let projectile = 0; projectile < plan.projectileCount; projectile += 1) {
        spawnProjectile(input.aimAngle + createSpreadOffset(input.context.weaponDefinition, input.randomFn));
      }
      break;
    case "pellets":
      for (let pellet = 0; pellet < input.context.weaponDefinition.pellets; pellet += 1) {
        spawnProjectile(input.aimAngle + createSpreadOffset(input.context.weaponDefinition, input.randomFn));
      }
      break;
  }

  return { projectiles, nextProjectileSequence };
}

function createSpreadOffset(definition: WeaponDefinition, randomFn: () => number): number {
  return randomBetween(randomFn, -definition.spreadRadians / 2, definition.spreadRadians / 2);
}

function createMuzzleVfx(position: Vec2, profile: Readonly<WeaponMuzzleVfxProfile>): WeaponActionMuzzleVfx {
  return {
    position,
    color: profile.color,
    radius: profile.radius,
    sparks: profile.sparks,
    ...(profile.impactSparkColor === undefined ? {} : { impactSparkColor: profile.impactSparkColor }),
    ...(profile.pulse === undefined ? {} : { pulse: { ...profile.pulse } })
  };
}
