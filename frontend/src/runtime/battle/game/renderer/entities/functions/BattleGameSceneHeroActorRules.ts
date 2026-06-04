import { resolveHeroVisual } from "../../../functions/BattleSpawnFactory";
import { BASE_MOVE_SPEED, SPRINT_MULTIPLIER } from "../../../objects/BattleGameConstants";
import type {
  GameSceneHeroActorCreationPlan,
  GameSceneHeroFlashPlan,
  ResolveGameSceneHeroActorCreationPlanInput,
  ResolveGameSceneHeroFlashPlanInput
} from "../objects/BattleGameSceneHeroActorObjects";

const GAME_SCENE_HERO_FLASH_RESTORE_DELAY_MS = 80;

export function resolveGameSceneHeroActorCreationPlan({
  hero
}: ResolveGameSceneHeroActorCreationPlanInput): GameSceneHeroActorCreationPlan {
  const maxVelocity = BASE_MOVE_SPEED * SPRINT_MULTIPLIER;

  return {
    position: hero.position,
    textureKey: resolveHeroVisual(hero.heroId).textureKey,
    visible: false,
    rotation: hero.facing,
    maxVelocity: {
      x: maxVelocity,
      y: maxVelocity
    },
    bodySize: {
      x: hero.radius * 2,
      y: hero.radius * 2
    },
    centerBody: true
  };
}

export function resolveGameSceneHeroFlashPlan({
  hero,
  flashColor
}: ResolveGameSceneHeroFlashPlanInput): GameSceneHeroFlashPlan {
  return {
    fillTint: flashColor,
    restoreDelayMs: GAME_SCENE_HERO_FLASH_RESTORE_DELAY_MS,
    restoreTint: resolveHeroVisual(hero.heroId).tint
  };
}
