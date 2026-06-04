import type { BattleVector2 as Vec2 } from "../../../../objects/battle/objects/core/BattleCoreScalars";
import Phaser from "phaser";
import type { BattlePlayerCommand as PlayerCommand } from "../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import { getSelectedSkillBindings } from "../../loadout/BattleLoadoutStore";
import { createPlayerCommand } from "../../microservices/session/functions/BattlePlayerCommandMapper";
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

/** 中文名：读取phaser玩家命令（readPhaserPlayerCommand）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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
    switchWeaponIndex: readWeaponSlotPress(controls),
    toggleBlink: skillPresses.Blink,
    toggleFreeze: skillPresses.Freeze,
    castDash: skillPresses.Dash,
    castCritical: skillPresses.Critical || Phaser.Input.Keyboard.JustDown(controls.critical),
    reloadPressed: Phaser.Input.Keyboard.JustDown(controls.reload)
  });
}

function readWeaponSlotPress(controls: ControlKeys): number | null {
  if (Phaser.Input.Keyboard.JustDown(controls.weapon1)) {
    return 0;
  }
  if (Phaser.Input.Keyboard.JustDown(controls.weapon2)) {
    return 1;
  }
  if (Phaser.Input.Keyboard.JustDown(controls.weapon3)) {
    return 2;
  }
  if (Phaser.Input.Keyboard.JustDown(controls.weapon4)) {
    return 3;
  }

  return null;
}
