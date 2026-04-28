import Phaser from "phaser";
import type { PlayerCommand, Vec2 } from "../../../domain/types";
import { getSelectedSkillBindings } from "../../loadout/loadoutGateway";
import { createPlayerCommand } from "../adapters/inputCommandMapper";
import type { ControlKeys } from "./controlKeys";
import { readSkillBindingPresses } from "./skillBindingInputAdapter";

export interface PhaserPlayerCommandInput {
  input: Phaser.Input.InputPlugin;
  controls: ControlKeys;
  playerPosition: Vec2;
  pointerJustPressed: boolean;
  secondaryJustPressed: boolean;
  pendingWeaponSwitchDirection: -1 | 0 | 1;
}

export function readPhaserPlayerCommand({
  input,
  controls,
  playerPosition,
  pointerJustPressed,
  secondaryJustPressed,
  pendingWeaponSwitchDirection
}: PhaserPlayerCommandInput): PlayerCommand {
  const skillPresses = readSkillBindingPresses(getSelectedSkillBindings(), {
    Q: Phaser.Input.Keyboard.JustDown(controls.skillQ),
    E: Phaser.Input.Keyboard.JustDown(controls.skillE),
    R: Phaser.Input.Keyboard.JustDown(controls.skillR)
  });

  return createPlayerCommand({
    playerPosition: { x: playerPosition.x, y: playerPosition.y },
    pointerWorld: { x: input.activePointer.worldX, y: input.activePointer.worldY },
    moveUp: controls.up.isDown,
    moveDown: controls.down.isDown,
    moveLeft: controls.left.isDown,
    moveRight: controls.right.isDown,
    primaryHeld: input.activePointer.leftButtonDown(),
    primaryJustPressed: pointerJustPressed,
    secondaryJustPressed,
    sprint: controls.sprint.isDown,
    switchWeaponDirection: pendingWeaponSwitchDirection,
    toggleBlink: skillPresses.Blink,
    toggleFreeze: skillPresses.Freeze,
    castDash: skillPresses.Dash,
    reloadPressed: Phaser.Input.Keyboard.JustDown(controls.reload)
  });
}
