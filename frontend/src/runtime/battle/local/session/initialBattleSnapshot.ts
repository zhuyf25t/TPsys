import type { BattleInitialParticipantsConfig } from "../../../../objects/battle/microservices/session/objects/state/BattleInitialParticipants";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import { getActiveBattleMap } from "../../microservices/world/services/BattleArenaCatalog";
import { buildInitialBattleExtractionState } from "../../microservices/extraction/functions/buildInitialBattleExtractionState";
import {
  createInitialHeroes,
  createInitialItemPickups,
  createInitialWeaponPickups
} from "../../game/functions/BattleSpawnFactory";
import {
  applyBattleInitialParticipantsToHeroes,
  buildBattleInitialSeatHeroConfigs,
  createBattleInitialSnapshotState,
  normalizeBattleInitialSeatAssignments,
  resolveBattleInitialPlayerHeroId
} from "../../microservices/session/functions/BattleInitialSnapshotRules";
import { getBotProfileById } from "../../../bots/registry/botRegistry";

export type {
  BattleInitialParticipantSeat,
  BattleInitialParticipantsConfig
} from "../../../../objects/battle/microservices/session/objects/state/BattleInitialParticipants";

export function createInitialBattleSnapshot(
  participants?: BattleInitialParticipantsConfig,
  requestedWorldSize?: GameSnapshot["worldSize"]
): GameSnapshot {
  const activeMap = getActiveBattleMap();
  const worldSize = requestedWorldSize ?? activeMap.worldSize;
  const extractionInitialState = buildInitialBattleExtractionState({
    gasPlan: activeMap.gasPlan,
    extractionZones: activeMap.extractionZones,
    lootCaches: activeMap.lootCaches
  });
  const seatAssignments = normalizeBattleInitialSeatAssignments(participants?.seats);
  const seatHeroConfigs = buildBattleInitialSeatHeroConfigs(seatAssignments);
  const baseHeroes = createInitialHeroes(seatHeroConfigs.length > 0 ? seatHeroConfigs : undefined);
  const heroes =
    seatAssignments.length > 0
      ? baseHeroes
      : applyBattleInitialParticipantsToHeroes(
          baseHeroes,
          participants,
          buildBotDisplayNameOverrides(baseHeroes)
        );

  return createBattleInitialSnapshotState({
    heroes,
    weaponPickups: createInitialWeaponPickups(),
    itemPickups: createInitialItemPickups(),
    gasZone: extractionInitialState.gasZone,
    extraction: extractionInitialState.extraction,
    lootCaches: extractionInitialState.lootCaches,
    worldSize,
    playerHeroId: resolveBattleInitialPlayerHeroId(participants, seatAssignments)
  });
}

function buildBotDisplayNameOverrides(heroes: readonly GameSnapshot["heroes"][number][]): Map<string, string> {
  return new Map(
    heroes
      .filter((hero) => hero.heroId !== "player-1")
      .flatMap((hero) => {
        const botProfile = getBotProfileById(hero.heroId);
        return botProfile ? [[hero.heroId, botProfile.displayName] as const] : [];
      })
  );
}
