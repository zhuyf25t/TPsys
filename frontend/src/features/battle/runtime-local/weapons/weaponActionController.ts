import type { Hero, PlayerCommand, Projectile, Vec2, WeaponState } from "../../../../domain/types";
import type { WeaponDefinition } from "../../../../game/weapons";
import { createProjectileSpawn } from "../projectiles/projectileFactory";
import { requestWeaponReload, resolveWeaponFire, type WeaponBlockReason } from "./weaponController";

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

export function getCurrentWeapon(hero: Hero): WeaponState {
  const weapon = hero.weapons[hero.currentWeaponIndex];
  if (!weapon) {
    throw new Error(`Missing weapon for ${hero.heroId}`);
  }

  return weapon;
}

export function resolveWeaponAction(context: WeaponActionContext): WeaponActionPlan {
  const randomFn = context.randomFn ?? Math.random;
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

  if (context.command.reloadPressed && context.weapon.weaponKind !== "Gatling") {
    return startReload(context, basePlan);
  }

  const fireResolution = resolveWeaponFire({
    player: context.player,
    weapon: context.weapon,
    weaponDefinition: context.weaponDefinition,
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
      return startReload(context, basePlan);
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
  const muzzle = {
    x: context.player.position.x + direction.x * (context.player.radius + 14),
    y: context.player.position.y + direction.y * (context.player.radius + 14)
  };
  const fireKind = context.weapon.weaponKind;
  const projectiles: Projectile[] = [];
  let nextProjectileSequence = context.projectileSequence;
  let muzzleVfx: WeaponActionMuzzleVfx | undefined;
  let recoilStrength = 0;

  if (fireKind === "Shotgun") {
    for (let pellet = 0; pellet < context.weaponDefinition.pellets; pellet += 1) {
      const spread = randomBetween(randomFn, -context.weaponDefinition.spreadRadians / 2, context.weaponDefinition.spreadRadians / 2);
      projectiles.push(
        createProjectileSpawn({
          projectileSequence: nextProjectileSequence,
          player: context.player,
          definition: context.weaponDefinition,
          angle: aimAngle + spread
        })
      );
      nextProjectileSequence += 1;
    }
    muzzleVfx = {
      position: muzzle,
      color: 0xffefb7,
      radius: 20,
      sparks: 7,
      impactSparkColor: 0xffe2ba
    };
    recoilStrength = 80;
  } else if (fireKind === "Gatling") {
    const spread = randomBetween(randomFn, -context.weaponDefinition.spreadRadians / 2, context.weaponDefinition.spreadRadians / 2);
    projectiles.push(
      createProjectileSpawn({
        projectileSequence: nextProjectileSequence,
        player: context.player,
        definition: context.weaponDefinition,
        angle: aimAngle + spread
      })
    );
    nextProjectileSequence += 1;
    muzzleVfx = {
      position: muzzle,
      color: 0xffd86d,
      radius: 10,
      sparks: 4
    };
    recoilStrength = 8;
  } else {
    projectiles.push(
      createProjectileSpawn({
        projectileSequence: nextProjectileSequence,
        player: context.player,
        definition: context.weaponDefinition,
        angle: aimAngle
      })
    );
    nextProjectileSequence += 1;
    if (fireKind === "RocketLauncher") {
      muzzleVfx = {
        position: muzzle,
        color: 0xffb36f,
        radius: 18,
        sparks: 5,
        pulse: {
          radius: 18,
          color: 0xffb36f
        }
      };
      recoilStrength = 120;
    } else {
      muzzleVfx = {
        position: muzzle,
        color: 0xfff0c6,
        radius: 10,
        sparks: 3
      };
      recoilStrength = 20;
    }
  }

  if (context.weapon.ammoInMagazine === 0 && (context.weapon.reserveAmmo ?? 0) > 0 && !context.weaponDefinition.usesHeat) {
    const reloadResult = requestWeaponReload({
      player: context.player,
      weapon: context.weapon,
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

  const showOverheatText = context.weapon.weaponKind === "Gatling" && context.weapon.overheated;

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

function startReload(context: WeaponActionContext, basePlan: WeaponActionPlan): WeaponActionPlan {
  const reloadResult = requestWeaponReload({
    player: context.player,
    weapon: context.weapon,
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
