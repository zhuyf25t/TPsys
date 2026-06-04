import type { BattleProjectileState as Projectile } from "../../../../../objects/battle/microservices/combat/objects/projectile/BattleProjectileState";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { BattleWeaponState as WeaponState } from "../../../../../objects/battle/microservices/combat/objects/weapon/BattleWeaponState";
import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePlayerCommand as PlayerCommand } from "../../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import type { WeaponDefinition } from "../../../../../objects/battle/microservices/combat/objects/combat/BattleCombatRuleDefinitions";
import {
  requestWeaponReload,
  resolveWeaponFire,
  type WeaponBlockReason
} from "./BattleWeaponFireDecisionRules";
import {
  buildWeaponProjectileAnglePlan,
  createBattleProjectileSpawn,
  resolveProjectileBirthPosition
} from "./BattleProjectileFactoryRules";
import {
  getWeaponRuntimeProfile,
  resolveWeaponAmmoMode,
  type WeaponMuzzleVfxProfile,
  type WeaponRuntimeProfile
} from "./BattleWeaponRuntimeProfiles";

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
}

export function resolveWeaponAction(context: WeaponActionContext): WeaponActionPlan {
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
    ammoMode,
    triggerMode: runtimeProfile.triggerMode,
    command: context.command,
    weaponSwitchRemainingMs: context.weaponSwitchRemainingMs,
    playerMotionActive: context.playerMotionActive
  });

  if (!fireResolution.result.canFire) {
    if (fireResolution.result.reason === "empty") {
      return {
        ...basePlan,
        blockReason: "empty",
        floatingText: { text: "????", tone: "neutral" }
      };
    }

    if (fireResolution.result.reason === "reloading" && (context.weapon.reserveAmmo ?? 0) > 0) {
      return startReload(context, basePlan, runtimeProfile);
    }

    if (fireResolution.result.reason === "overheated") {
      return {
        ...basePlan,
        blockReason: "overheated",
        floatingText: { text: "??", tone: "error" }
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
    aimAngle
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
      ammoMode,
      weaponSwitchRemainingMs: context.weaponSwitchRemainingMs
    });

    if (reloadResult.started) {
      context.weapon.reloadRemainingMs = context.weaponDefinition.reloadMs;
      return {
        canFire: true,
        projectiles,
        muzzle: muzzleVfx,
        recoilStrength,
        startedReload: true,
        nextProjectileSequence,
        floatingText: { text: "????", tone: "neutral" }
      };
    }
  }

  const showOverheatText = ammoMode === "heat" && context.weapon.overheated;

  return {
    canFire: true,
    projectiles,
    muzzle: muzzleVfx,
    recoilStrength,
    startedReload: context.weapon.reloadRemainingMs > 0 && context.weaponDefinition.reloadMs > 0,
    nextProjectileSequence,
    floatingText: showOverheatText ? { text: "??", tone: "error" } : undefined
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
    ammoMode: resolveWeaponAmmoMode(runtimeProfile, context.weaponDefinition),
    weaponSwitchRemainingMs: context.weaponSwitchRemainingMs
  });

  if (!reloadResult.started) {
    return basePlan;
  }

  context.weapon.reloadRemainingMs = context.weaponDefinition.reloadMs;
  return {
    ...basePlan,
    canFire: false,
    startedReload: true,
    floatingText: { text: "????", tone: "neutral" }
  };
}

interface WeaponProjectileSpawnInput {
  context: WeaponActionContext;
  aimAngle: number;
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
      createBattleProjectileSpawn({
        projectileSequence: nextProjectileSequence,
        player: input.context.player,
        definition: input.context.weaponDefinition,
        angle
      })
    );
    nextProjectileSequence += 1;
  };

  buildWeaponProjectileAnglePlan({
    aimAngle: input.aimAngle,
    weaponDefinition: input.context.weaponDefinition
  }).forEach(spawnProjectile);

  return { projectiles, nextProjectileSequence };
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
