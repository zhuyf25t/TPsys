import {
  recordRemoteProjectileTerminalDiagnostics,
  shouldRecordRemoteProjectileTerminalDiagnostics
} from "../diagnostics/remoteViewDiagnostics";
import {
  planBattleAuthoritativeProjectileTerminalDiagnostic,
  planBattleProjectileTerminalDiagnostic
} from "../../../microservices/combat/functions/BattleProjectileFeedbackDiagnosticRules";
import type {
  AuthoritativeProjectileTerminalDiagnosticsRecordInput,
  ProjectileTerminalDiagnosticsRecordInput,
  SkippedAuthoritativeProjectileTerminalDiagnosticsRecordInput
} from "./objects/ProjectileTerminalDiagnosticsRecorderObjects";
import { collectProjectileTerminalHeroDisplayPositions } from "./functions/ProjectileTerminalDiagnosticsRecorderRules";

function shouldRecordProjectileTerminalDiagnostics(): boolean {
  return shouldRecordRemoteProjectileTerminalDiagnostics();
}

export function recordProjectileTerminalDiagnostics({
  previous,
  projectileId,
  snapshot,
  getHeroDisplayPosition
}: ProjectileTerminalDiagnosticsRecordInput): void {
  if (!shouldRecordProjectileTerminalDiagnostics()) {
    return;
  }

  recordRemoteProjectileTerminalDiagnostics(
    planBattleProjectileTerminalDiagnostic({
      previous,
      projectileId,
      heroes: snapshot.heroes,
      heroDisplayPositions: collectProjectileTerminalHeroDisplayPositions({
        heroes: snapshot.heroes,
        getHeroDisplayPosition
      })
    })
  );
}

export function recordAuthoritativeProjectileTerminalDiagnostics({
  terminal,
  previous,
  snapshot,
  getHeroDisplayPosition,
  vfxBudgetReason = null
}: AuthoritativeProjectileTerminalDiagnosticsRecordInput): void {
  if (!shouldRecordProjectileTerminalDiagnostics()) {
    return;
  }

  writeAuthoritativeProjectileTerminalDiagnostics({
    terminal,
    previous,
    snapshot,
    getHeroDisplayPosition,
    vfxBudgetReason
  });
}

export function recordSkippedAuthoritativeProjectileTerminalDiagnostics({
  terminal,
  previous,
  getSnapshot,
  getHeroDisplayPosition,
  vfxBudgetReason
}: SkippedAuthoritativeProjectileTerminalDiagnosticsRecordInput): void {
  if (!shouldRecordProjectileTerminalDiagnostics()) {
    return;
  }

  writeAuthoritativeProjectileTerminalDiagnostics({
    terminal,
    previous,
    snapshot: getSnapshot(),
    getHeroDisplayPosition,
    vfxBudgetReason
  });
}

function writeAuthoritativeProjectileTerminalDiagnostics({
  terminal,
  previous,
  snapshot,
  getHeroDisplayPosition,
  vfxBudgetReason = null
}: AuthoritativeProjectileTerminalDiagnosticsRecordInput): void {
  recordRemoteProjectileTerminalDiagnostics(
    planBattleAuthoritativeProjectileTerminalDiagnostic({
      terminal,
      previous,
      heroes: snapshot.heroes,
      heroDisplayPositions: collectProjectileTerminalHeroDisplayPositions({
        heroes: snapshot.heroes,
        getHeroDisplayPosition
      }),
      vfxBudgetReason
    })
  );
}
