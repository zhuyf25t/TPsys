import Phaser from "phaser";

export interface ControlKeys {
  up: Phaser.Input.Keyboard.Key;
  down: Phaser.Input.Keyboard.Key;
  left: Phaser.Input.Keyboard.Key;
  right: Phaser.Input.Keyboard.Key;
  sprint: Phaser.Input.Keyboard.Key;
  skillQ: Phaser.Input.Keyboard.Key;
  skillE: Phaser.Input.Keyboard.Key;
  skillR: Phaser.Input.Keyboard.Key;
  reload: Phaser.Input.Keyboard.Key;
  weapon1: Phaser.Input.Keyboard.Key;
  weapon2: Phaser.Input.Keyboard.Key;
  weapon3: Phaser.Input.Keyboard.Key;
  weapon4: Phaser.Input.Keyboard.Key;
}

export function createBattleControlKeys(input: Phaser.Input.InputPlugin): ControlKeys {
  return {
    up: input.keyboard!.addKey(Phaser.Input.Keyboard.KeyCodes.W),
    down: input.keyboard!.addKey(Phaser.Input.Keyboard.KeyCodes.S),
    left: input.keyboard!.addKey(Phaser.Input.Keyboard.KeyCodes.A),
    right: input.keyboard!.addKey(Phaser.Input.Keyboard.KeyCodes.D),
    sprint: input.keyboard!.addKey(Phaser.Input.Keyboard.KeyCodes.SHIFT),
    skillQ: input.keyboard!.addKey(Phaser.Input.Keyboard.KeyCodes.Q),
    skillE: input.keyboard!.addKey(Phaser.Input.Keyboard.KeyCodes.E),
    skillR: input.keyboard!.addKey(Phaser.Input.Keyboard.KeyCodes.R),
    reload: input.keyboard!.addKey(Phaser.Input.Keyboard.KeyCodes.T),
    weapon1: input.keyboard!.addKey(Phaser.Input.Keyboard.KeyCodes.ONE),
    weapon2: input.keyboard!.addKey(Phaser.Input.Keyboard.KeyCodes.TWO),
    weapon3: input.keyboard!.addKey(Phaser.Input.Keyboard.KeyCodes.THREE),
    weapon4: input.keyboard!.addKey(Phaser.Input.Keyboard.KeyCodes.FOUR)
  };
}
