import type {
  BattlePhaserGameViewportInput,
  BattlePhaserGameViewportSize
} from "../objects/BattlePhaserGameObjects";

export function resolveBattlePhaserGameViewportSize(
  input: BattlePhaserGameViewportInput
): BattlePhaserGameViewportSize {
  return {
    width: input.mountWidth || input.windowWidth || input.fallbackWidth,
    height: input.mountHeight || input.windowHeight || input.fallbackHeight
  };
}
