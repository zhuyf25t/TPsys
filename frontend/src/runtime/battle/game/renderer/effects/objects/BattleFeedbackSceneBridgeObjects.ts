import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { ProjectileTerminalVfxPresenterCallbacks } from "./ProjectileTerminalVfxPresenterObjects";

export interface BattleFeedbackSceneBridgeOptions extends ProjectileTerminalVfxPresenterCallbacks {
  getSnapshot(): GameSnapshot;
  getHeroDisplayPosition(heroId: string): Vec2 | null;
  getProjectileDisplayPosition(projectileId: string): Vec2 | null;
  flashHero(heroId: string, color: number): void;
  showFloatingText(position: Vec2, text: string, tone: "neutral" | "success" | "warning" | "error"): void;
  createHitConfirm(position: Vec2, color: number): void;
  shakeCamera(duration: number, intensity: number): void;
}
