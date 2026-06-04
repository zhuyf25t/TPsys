import { WEAPON_DEFINITIONS } from "../../../../../../objects/battle/microservices/combat/objects/combat/BattleCombatRuleDefinitions";
import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleWeaponState as WeaponState } from "../../../../../../objects/battle/microservices/combat/objects/weapon/BattleWeaponState";
import { resolveHeroVisual } from "../../../functions/BattleSpawnFactory";
import type {
  HeroWorldViewCreationPlan,
  ResolveHeroWorldViewCreationPlanInput
} from "../objects/HeroWorldViewFactoryObjects";
import type {
  HeroActionProgressPlan,
  HeroDisplayStatePlan,
  HeroVisibilityPlan,
  ResolveHeroDisplayStatePlanInput
} from "../objects/WorldViewFactoryObjects";

const LOCAL_HERO_NAME_LABEL_STYLE = {
  fontFamily: "Segoe UI",
  fontSize: "14px",
  color: "#e8fbff"
} as const;

const REMOTE_HERO_NAME_LABEL_STYLE = {
  fontFamily: "Segoe UI",
  fontSize: "13px",
  color: "#d9e3ef"
} as const;

const HERO_NAME_LABEL_OFFSET_Y = -54;
const HERO_NAME_LABEL_DEPTH = 58;
const HERO_HEALTH_BACKGROUND_OFFSET_Y = -38;
const HERO_HEALTH_BACKGROUND_SIZE = { x: 52, y: 8 } as const;
const HERO_HEALTH_BACKGROUND_TINT = 0x0d1014;
const HERO_HEALTH_BACKGROUND_ALPHA = 0.95;
const HERO_HEALTH_BACKGROUND_DEPTH = 56;
const HERO_HEALTH_FILL_OFFSET = { x: -25, y: -38 } as const;
const HERO_HEALTH_FILL_SIZE = { x: 48, y: 6 } as const;
const HERO_HEALTH_FILL_DEPTH = 57;
const HERO_ACTION_BACKGROUND_OFFSET_Y = -24;
const HERO_ACTION_BACKGROUND_SIZE = { x: 52, y: 6 } as const;
const HERO_ACTION_BACKGROUND_TINT = 0x10151d;
const HERO_ACTION_BACKGROUND_ALPHA = 0.9;
const HERO_ACTION_BACKGROUND_DEPTH = 55;
const HERO_ACTION_BACKGROUND_STROKE = {
  width: 1,
  color: 0xffffff,
  alpha: 0.14
} as const;
const HERO_ACTION_FILL_OFFSET = { x: -25, y: -24 } as const;
const HERO_ACTION_FILL_SIZE = { x: 0, y: 4 } as const;
const HERO_ACTION_FILL_TINT = 0xe7edf5;
const HERO_ACTION_FILL_ALPHA = 0.95;
const HERO_ACTION_FILL_DEPTH = 56;

export function isLocalPlayerHero(hero: Hero, playerHeroId: string): boolean {
  return hero.heroId === playerHeroId;
}

export function resolveHeroWorldViewCreationPlan({
  hero,
  playerHeroId,
  baseHeroScale
}: ResolveHeroWorldViewCreationPlanInput): HeroWorldViewCreationPlan {
  const visual = resolveHeroVisual(hero.heroId);
  const isPlayer = isLocalPlayerHero(hero, playerHeroId);

  return {
    isPlayer,
    spriteDepth: isPlayer ? 50 : 42,
    textureKey: visual.textureKey,
    tint: visual.tint,
    baseScale: baseHeroScale,
    nameLabel: {
      position: {
        x: hero.position.x,
        y: hero.position.y + HERO_NAME_LABEL_OFFSET_Y
      },
      text: hero.displayName,
      style: isPlayer ? LOCAL_HERO_NAME_LABEL_STYLE : REMOTE_HERO_NAME_LABEL_STYLE,
      origin: { x: 0.5, y: 1 },
      depth: HERO_NAME_LABEL_DEPTH
    },
    healthBackground: {
      position: {
        x: hero.position.x,
        y: hero.position.y + HERO_HEALTH_BACKGROUND_OFFSET_Y
      },
      size: HERO_HEALTH_BACKGROUND_SIZE,
      fillColor: HERO_HEALTH_BACKGROUND_TINT,
      fillAlpha: HERO_HEALTH_BACKGROUND_ALPHA,
      depth: HERO_HEALTH_BACKGROUND_DEPTH,
      visible: true
    },
    healthFill: {
      position: {
        x: hero.position.x + HERO_HEALTH_FILL_OFFSET.x,
        y: hero.position.y + HERO_HEALTH_FILL_OFFSET.y
      },
      size: HERO_HEALTH_FILL_SIZE,
      fillColor: visual.tint,
      fillAlpha: 1,
      depth: HERO_HEALTH_FILL_DEPTH,
      origin: { x: 0, y: 0.5 },
      visible: true
    },
    actionBackground: {
      position: {
        x: hero.position.x,
        y: hero.position.y + HERO_ACTION_BACKGROUND_OFFSET_Y
      },
      size: HERO_ACTION_BACKGROUND_SIZE,
      fillColor: HERO_ACTION_BACKGROUND_TINT,
      fillAlpha: HERO_ACTION_BACKGROUND_ALPHA,
      depth: HERO_ACTION_BACKGROUND_DEPTH,
      visible: false,
      stroke: HERO_ACTION_BACKGROUND_STROKE
    },
    actionFill: {
      position: {
        x: hero.position.x + HERO_ACTION_FILL_OFFSET.x,
        y: hero.position.y + HERO_ACTION_FILL_OFFSET.y
      },
      size: HERO_ACTION_FILL_SIZE,
      fillColor: HERO_ACTION_FILL_TINT,
      fillAlpha: HERO_ACTION_FILL_ALPHA,
      depth: HERO_ACTION_FILL_DEPTH,
      origin: { x: 0, y: 0.5 },
      visible: false
    }
  };
}

export function resolveHeroVisibilityPlan(hero: Hero): HeroVisibilityPlan {
  if (hero.alive) {
    return {
      visible: true,
      clearRemoteInterpolation: false,
      resetLocalMotionStreaks: false
    };
  }

  return {
    visible: false,
    clearRemoteInterpolation: true,
    resetLocalMotionStreaks: true
  };
}

export function resolveHeroDisplayStatePlan({
  hero,
  playerHeroId,
  sharedAuthoritativeRuntime,
  remoteAuthoritativeHeroIds,
  localHeroDisplayOverride
}: ResolveHeroDisplayStatePlanInput): HeroDisplayStatePlan {
  const isPlayer = isLocalPlayerHero(hero, playerHeroId);
  if (isPlayer && localHeroDisplayOverride) {
    return {
      kind: "localOverride",
      displayState: localHeroDisplayOverride
    };
  }

  if (sharedAuthoritativeRuntime && !isPlayer && remoteAuthoritativeHeroIds.has(hero.heroId)) {
    return { kind: "remoteAuthoritative" };
  }

  return {
    kind: "snapshot",
    displayState: {
      position: hero.position,
      facing: hero.facing
    }
  };
}

export function resolveHeroActionProgressPlan(input: {
  isPlayer: boolean;
  weapon: WeaponState | undefined;
  weaponSwitchRemainingMs: number;
  weaponSwitchTotalMs: number;
}): HeroActionProgressPlan {
  const { isPlayer, weapon, weaponSwitchRemainingMs, weaponSwitchTotalMs } = input;
  if (!weapon) {
    return { visible: false };
  }

  if (isPlayer && weaponSwitchRemainingMs > 0 && weaponSwitchTotalMs > 0) {
    return {
      visible: true,
      progress: clamp01(1 - weaponSwitchRemainingMs / weaponSwitchTotalMs)
    };
  }

  const reloadMs = WEAPON_DEFINITIONS[weapon.weaponKind].reloadMs;
  if (weapon.reloadRemainingMs > 0 && reloadMs > 0) {
    return {
      visible: true,
      progress: clamp01(1 - weapon.reloadRemainingMs / reloadMs)
    };
  }

  return { visible: false };
}

function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value));
}
