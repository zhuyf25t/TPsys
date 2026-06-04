import type { BattleGameSnapshot as GameSnapshot } from "../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import { createInitialBattleSnapshot } from "../../../local/session/initialBattleSnapshot";
import { buildBattleRuntimeAuthoritativeFrame } from "../../../microservices/session/functions/BattleRuntimeAuthoritativeFrameBuilder";
import { resolveBattleRuntimeInitialAuthoritativeLocalHeroId } from "../../../microservices/session/functions/BattleRuntimeAuthoritativeStartupRules";
import { applyAuthoritativeFrameToSnapshot } from "../authoritative/BattleAuthoritativeFrameSnapshotApplier";
import type { CreateBattleRuntimeBootSnapshotInput } from "./objects/BattleRuntimeBootSnapshotObjects";

export function createBattleRuntimeBootSnapshot({
  initialSnapshot,
  initialParticipants,
  initialAuthoritativeState,
  localAuthoritativePlayerId
}: CreateBattleRuntimeBootSnapshotInput): GameSnapshot {
  const snapshot = initialSnapshot ?? createInitialBattleSnapshot(initialParticipants, initialAuthoritativeState?.worldSize);
  if (!initialAuthoritativeState) {
    return snapshot;
  }

  const startupLocalPlayerId = localAuthoritativePlayerId || initialParticipants?.localPlayerId || "";
  const authoritativeLocalHeroId = resolveBattleRuntimeInitialAuthoritativeLocalHeroId(
    snapshot,
    initialAuthoritativeState,
    startupLocalPlayerId
  );
  if (authoritativeLocalHeroId) {
    snapshot.playerHeroId = authoritativeLocalHeroId;
  }

  const stableSeatHeroIds = snapshot.heroes.map((hero) => hero.heroId);
  const frame = buildBattleRuntimeAuthoritativeFrame(
    snapshot,
    initialAuthoritativeState,
    startupLocalPlayerId,
    stableSeatHeroIds
  );
  if (!frame) {
    return snapshot;
  }

  applyAuthoritativeFrameToSnapshot({
    snapshot,
    frame
  });

  return snapshot;
}
