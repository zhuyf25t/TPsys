import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { AuthoritativeLocalHeroReplayProjection } from "./BattleAuthoritativeLocalHeroReplayProjection";
import type { BattleRuntimeAuthoritativeFrame } from "./BattleRuntimeAuthoritativeFrameBuilder";
import {
  resolveBattleRuntimeAuthoritativeLocalReplaySnapshotProjection,
  type BattleRuntimeAuthoritativeLocalReplayContext,
  type BattleRuntimeAuthoritativeLocalReplayTargetInput
} from "./BattleRuntimeAuthoritativeLocalReplaySnapshotProjection";
import {
  clampBattleRuntimeAuthoritativeWeaponIndex,
  syncBattleRuntimeAuthoritativeSkillStates,
  syncBattleRuntimeAuthoritativeWeaponStates
} from "./BattleRuntimeAuthoritativeSnapshotSync";

type BattleRuntimeAuthoritativeHeroFrame = BattleRuntimeAuthoritativeFrame["heroes"][number];

export type BattleRuntimeAuthoritativeLocalPlayerReplayContext = BattleRuntimeAuthoritativeLocalReplayContext;

export interface BattleRuntimeAuthoritativeLocalPlayerCorrectionTarget {
  authoritativePosition: Vec2;
  localMovementActive?: boolean;
  forceHardSnap?: boolean;
}

export interface SyncBattleRuntimeAuthoritativeHeroesInput {
  snapshot: GameSnapshot;
  authoritativeHeroes: readonly BattleRuntimeAuthoritativeHeroFrame[];
  localPlayerMovementActive?: boolean;
  localPlayerReplay?: BattleRuntimeAuthoritativeLocalPlayerReplayContext;
  resolveReplayTarget(input: BattleRuntimeAuthoritativeLocalReplayTargetInput): AuthoritativeLocalHeroReplayProjection;
  applyLocalPlayerAuthoritativeCorrection?(target: BattleRuntimeAuthoritativeLocalPlayerCorrectionTarget): void;
}

export function syncBattleRuntimeAuthoritativeHeroes({
  snapshot,
  authoritativeHeroes,
  localPlayerMovementActive,
  localPlayerReplay,
  resolveReplayTarget,
  applyLocalPlayerAuthoritativeCorrection
}: SyncBattleRuntimeAuthoritativeHeroesInput): void {
  for (const authoritativeHero of authoritativeHeroes) {
    const hero = snapshot.heroes.find((entry) => entry.heroId === authoritativeHero.heroId);
    if (!hero) {
      continue;
    }

    syncBattleRuntimeAuthoritativeHeroFields({
      snapshot,
      hero,
      authoritativeHero,
      localPlayerMovementActive,
      localPlayerReplay,
      resolveReplayTarget,
      applyLocalPlayerAuthoritativeCorrection
    });
  }
}

function syncBattleRuntimeAuthoritativeHeroFields({
  snapshot,
  hero,
  authoritativeHero,
  localPlayerMovementActive,
  localPlayerReplay,
  resolveReplayTarget,
  applyLocalPlayerAuthoritativeCorrection
}: {
  snapshot: GameSnapshot;
  hero: Hero;
  authoritativeHero: BattleRuntimeAuthoritativeHeroFrame;
  localPlayerMovementActive?: boolean;
  localPlayerReplay?: BattleRuntimeAuthoritativeLocalPlayerReplayContext;
  resolveReplayTarget(input: BattleRuntimeAuthoritativeLocalReplayTargetInput): AuthoritativeLocalHeroReplayProjection;
  applyLocalPlayerAuthoritativeCorrection?(target: BattleRuntimeAuthoritativeLocalPlayerCorrectionTarget): void;
}): void {
  const previousPosition = hero.position;
  const previousAlive = hero.alive;
  hero.displayName = authoritativeHero.displayName;
  hero.facing = authoritativeHero.facing;
  syncBattleRuntimeAuthoritativeWeaponStates(hero.weapons, authoritativeHero.weapons);
  hero.currentWeaponIndex = clampBattleRuntimeAuthoritativeWeaponIndex(
    authoritativeHero.currentWeaponIndex,
    hero.weapons.length
  );
  syncBattleRuntimeAuthoritativeSkillStates(hero.skills, authoritativeHero.skills);
  hero.preparedSkill = null;
  hero.alive = authoritativeHero.alive;
  hero.respawnMs = Math.max(0, Math.round(authoritativeHero.respawnMs));
  hero.lifeState = authoritativeHero.alive ? "alive" : hero.respawnMs > 0 ? "respawning" : "dead";
  hero.maxHp = Math.max(1, authoritativeHero.maxHp);
  hero.hp = Math.max(0, Math.min(authoritativeHero.hp, hero.maxHp));
  hero.score = Math.max(0, authoritativeHero.score);
  hero.maxStamina = Math.max(1, authoritativeHero.maxStamina);
  hero.stamina = Math.max(0, Math.min(authoritativeHero.stamina, hero.maxStamina));
  hero.jumpCooldownMs = 0;
  hero.eliminatedAtMs = authoritativeHero.alive ? null : authoritativeHero.eliminatedAtMs;

  const authoritativePosition = { x: authoritativeHero.position.x, y: authoritativeHero.position.y };
  hero.position = authoritativePosition;

  if (hero.heroId === snapshot.playerHeroId) {
    syncBattleRuntimeAuthoritativeLocalHeroProjection({
      snapshot,
      hero,
      authoritativePosition,
      previousAlive,
      authoritativeAlive: authoritativeHero.alive,
      localPlayerMovementActive,
      localPlayerReplay,
      resolveReplayTarget,
      applyLocalPlayerAuthoritativeCorrection
    });
  }

  hero.velocity = {
    x: authoritativeHero.alive ? hero.position.x - previousPosition.x : 0,
    y: authoritativeHero.alive ? hero.position.y - previousPosition.y : 0
  };
}

function syncBattleRuntimeAuthoritativeLocalHeroProjection({
  snapshot,
  hero,
  authoritativePosition,
  previousAlive,
  authoritativeAlive,
  localPlayerMovementActive,
  localPlayerReplay,
  resolveReplayTarget,
  applyLocalPlayerAuthoritativeCorrection
}: {
  snapshot: GameSnapshot;
  hero: Hero;
  authoritativePosition: Vec2;
  previousAlive: boolean;
  authoritativeAlive: boolean;
  localPlayerMovementActive?: boolean;
  localPlayerReplay?: BattleRuntimeAuthoritativeLocalPlayerReplayContext;
  resolveReplayTarget(input: BattleRuntimeAuthoritativeLocalReplayTargetInput): AuthoritativeLocalHeroReplayProjection;
  applyLocalPlayerAuthoritativeCorrection?(target: BattleRuntimeAuthoritativeLocalPlayerCorrectionTarget): void;
}): void {
  const forceHardSnap = previousAlive !== authoritativeAlive || !authoritativeAlive;
  const replayProjection = resolveBattleRuntimeAuthoritativeLocalReplaySnapshotProjection({
    authoritativePosition,
    snapshot,
    heroRadius: hero.radius,
    authoritativeStamina: hero.stamina,
    authoritativeMaxStamina: hero.maxStamina,
    localHero: hero,
    localPlayerReplay: forceHardSnap ? undefined : localPlayerReplay,
    resolveReplayTarget
  });
  if (replayProjection.hasPredictedStamina) {
    hero.stamina = Math.max(0, Math.min(replayProjection.stamina, hero.maxStamina));
  }

  applyLocalPlayerAuthoritativeCorrection?.({
    authoritativePosition: replayProjection.position,
    localMovementActive: localPlayerMovementActive,
    forceHardSnap
  });
}
