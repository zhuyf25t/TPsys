import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type {
  AuthoritativeProjectileTerminalFeedbackState,
  AuthoritativeProjectileTerminalVfxBudgetReason,
  ProjectileFeedbackState
} from "../../../../microservices/combat/functions/BattleProjectileFeedbackRules";

export type ProjectileTerminalDiagnosticsHeroes = Pick<GameSnapshot, "heroes">["heroes"];
export type ProjectileTerminalHeroDisplayPositions = ReadonlyMap<string, Vec2>;

export interface ProjectileTerminalDiagnosticsRecordInput {
  previous: ProjectileFeedbackState;
  projectileId: string;
  snapshot: Pick<GameSnapshot, "heroes">;
  getHeroDisplayPosition(heroId: string): Vec2 | null;
}

export interface AuthoritativeProjectileTerminalDiagnosticsRecordInput {
  terminal: AuthoritativeProjectileTerminalFeedbackState;
  previous: ProjectileFeedbackState | undefined;
  snapshot: Pick<GameSnapshot, "heroes">;
  getHeroDisplayPosition(heroId: string): Vec2 | null;
  vfxBudgetReason?: AuthoritativeProjectileTerminalVfxBudgetReason | null;
}

export interface SkippedAuthoritativeProjectileTerminalDiagnosticsRecordInput {
  terminal: AuthoritativeProjectileTerminalFeedbackState;
  previous: ProjectileFeedbackState | undefined;
  getSnapshot(): Pick<GameSnapshot, "heroes">;
  getHeroDisplayPosition(heroId: string): Vec2 | null;
  vfxBudgetReason: AuthoritativeProjectileTerminalVfxBudgetReason;
}

export interface CollectProjectileTerminalHeroDisplayPositionsInput {
  heroes: ProjectileTerminalDiagnosticsHeroes;
  getHeroDisplayPosition(heroId: string): Vec2 | null;
}
