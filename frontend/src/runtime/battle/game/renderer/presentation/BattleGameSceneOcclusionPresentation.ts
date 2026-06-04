import { updateOccludableAlpha } from "../arena/occlusionAlphaController";
import type { UpdateGameSceneOcclusionInput } from "./objects/BattleGameSceneOcclusionPresentationObjects";

export function updateGameSceneOcclusion({
  player,
  heroes,
  sharedAuthoritativeRuntime,
  localHeroDisplay,
  occludables
}: UpdateGameSceneOcclusionInput): void {
  const renderedPlayer = localHeroDisplay.heroFor(player, sharedAuthoritativeRuntime);
  updateOccludableAlpha({
    player: renderedPlayer,
    heroes: heroes.map((hero) => (hero.heroId === player.heroId ? renderedPlayer : hero)),
    occludables
  });
}
