import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type {
  CollectProjectileTerminalHeroDisplayPositionsInput,
  ProjectileTerminalHeroDisplayPositions
} from "../objects/ProjectileTerminalDiagnosticsRecorderObjects";

export function collectProjectileTerminalHeroDisplayPositions({
  heroes,
  getHeroDisplayPosition
}: CollectProjectileTerminalHeroDisplayPositionsInput): ProjectileTerminalHeroDisplayPositions {
  const positions = new Map<string, Vec2>();
  heroes.forEach((hero) => {
    const position = getHeroDisplayPosition(hero.heroId);
    if (position) {
      positions.set(hero.heroId, {
        x: position.x,
        y: position.y
      });
    }
  });

  return positions;
}
