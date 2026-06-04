import { FEED_EVENT_TTL_MS } from "../../objects/BattleGameConstants";
import {
  cloneBattleExtractionState,
  cloneBattleGasZoneState,
  cloneBattleLootCacheState
} from "../../../microservices/extraction/functions/cloneBattleExtractionState";
import { syncBattleRuntimeAuthoritativeHeroes } from "../../../microservices/session/functions/BattleRuntimeAuthoritativeHeroSnapshotSync";
import {
  clampBattleRuntimeAuthoritativeElapsedMs,
  mergeBattleRuntimeAuthoritativeEvents,
  syncBattleRuntimeAuthoritativeItemPickups,
  syncBattleRuntimeAuthoritativeProjectiles,
  syncBattleRuntimeAuthoritativeSlowFields,
  syncBattleRuntimeAuthoritativeWeaponPickups
} from "../../../microservices/session/functions/BattleRuntimeAuthoritativeSnapshotSync";
import {
  createBattleAuthoritativeClockAnchor,
  resolveBattleAuthoritativeClockElapsedMs
} from "../../../microservices/session/functions/BattleAuthoritativeClockSyncRules";
import { resolveAuthoritativeLocalHeroReplayTarget } from "./BattleAuthoritativeLocalHeroReplay";
import type { ApplyAuthoritativeFrameToSnapshotInput } from "./objects/BattleAuthoritativeFrameSnapshotApplierObjects";

const MAX_AUTHORITATIVE_EVENTS = 12;

export type {
  ApplyAuthoritativeFrameToSnapshotInput,
  LocalPlayerAuthoritativeCorrectionTarget,
  LocalPlayerAuthoritativeReplayContext
} from "./objects/BattleAuthoritativeFrameSnapshotApplierObjects";

export function applyAuthoritativeFrameToSnapshot({
  snapshot,
  frame,
  receivedAtMs,
  localPlayerMovementActive,
  localPlayerReplay,
  applyLocalPlayerAuthoritativeCorrection
}: ApplyAuthoritativeFrameToSnapshotInput): void {
  const authoritativeElapsedMs = clampBattleRuntimeAuthoritativeElapsedMs(frame.elapsedMs, frame.durationMs);
  const frameReceivedAtMs = receivedAtMs ?? Date.now();
  snapshot.elapsedMs = resolveBattleAuthoritativeClockElapsedMs({
    anchor: createBattleAuthoritativeClockAnchor({
      frame: { ...frame, elapsedMs: authoritativeElapsedMs },
      receivedAtMs: frameReceivedAtMs
    }),
    fallbackElapsedMs: authoritativeElapsedMs,
    nowMs: frameReceivedAtMs
  });
  snapshot.worldSize = { x: frame.worldSize.x, y: frame.worldSize.y };
  syncBattleRuntimeAuthoritativeSlowFields(snapshot.slowFields, frame.slowFields);
  syncBattleRuntimeAuthoritativeWeaponPickups(snapshot.weaponPickups, frame.pickups);
  syncBattleRuntimeAuthoritativeItemPickups(snapshot.itemPickups, frame.pickups);
  syncBattleRuntimeAuthoritativeProjectiles(snapshot.projectiles, frame.projectiles);
  snapshot.gasZone = cloneBattleGasZoneState(frame.gasZone);
  snapshot.extraction = cloneBattleExtractionState(frame.extraction);
  snapshot.lootCaches = frame.lootCaches.map(cloneBattleLootCacheState);
  snapshot.events = mergeBattleRuntimeAuthoritativeEvents({
    frame,
    feedEventTtlMs: FEED_EVENT_TTL_MS,
    maxEvents: MAX_AUTHORITATIVE_EVENTS
  });

  syncBattleRuntimeAuthoritativeHeroes({
    snapshot,
    authoritativeHeroes: frame.heroes,
    localPlayerMovementActive,
    localPlayerReplay,
    resolveReplayTarget: resolveAuthoritativeLocalHeroReplayTarget,
    applyLocalPlayerAuthoritativeCorrection
  });
}
