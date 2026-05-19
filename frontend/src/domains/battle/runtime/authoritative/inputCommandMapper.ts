import type { PlayerCommand, Vec2 } from "../../objects/types";

export interface InputCommandContext {
  playerPosition: Vec2;
  pointerWorld: Vec2;
  moveUp: boolean;
  moveDown: boolean;
  moveLeft: boolean;
  moveRight: boolean;
  primaryHeld: boolean;
  primaryJustPressed: boolean;
  secondaryJustPressed: boolean;
  sprint: boolean;
  switchWeaponDirection: -1 | 0 | 1;
  switchWeaponIndex: number | null;
  toggleBlink: boolean;
  toggleFreeze: boolean;
  castDash: boolean;
  reloadPressed: boolean;
}

function normalizeVector(vector: Vec2): Vec2 {
  const length = Math.hypot(vector.x, vector.y);
  if (length <= 0.0001) {
    return { x: 0, y: 0 };
  }

  return {
    x: vector.x / length,
    y: vector.y / length
  };
}

/** 中文名：创建玩家命令（createPlayerCommand）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createPlayerCommand(input: InputCommandContext): PlayerCommand {
  const movementInput = {
    x: Number(input.moveRight) - Number(input.moveLeft),
    y: Number(input.moveDown) - Number(input.moveUp)
  };
  const movement = normalizeVector(movementInput);
  const aimVector = {
    x: input.pointerWorld.x - input.playerPosition.x,
    y: input.pointerWorld.y - input.playerPosition.y
  };
  const aim = normalizeVector(aimVector);

  return {
    movement,
    aim: aim.x === 0 && aim.y === 0 ? { x: 1, y: 0 } : aim,
    pointerWorld: { x: input.pointerWorld.x, y: input.pointerWorld.y },
    primaryHeld: input.primaryHeld,
    primaryJustPressed: input.primaryJustPressed,
    secondaryJustPressed: input.secondaryJustPressed,
    sprint: input.sprint,
    switchWeaponDirection: input.switchWeaponDirection,
    switchWeaponIndex: normalizeSwitchWeaponIndex(input.switchWeaponIndex),
    toggleBlink: input.toggleBlink,
    toggleFreeze: input.toggleFreeze,
    castDash: input.castDash,
    reloadPressed: input.reloadPressed
  };
}

function normalizeSwitchWeaponIndex(index: number | null): number | null {
  return index === null || !Number.isFinite(index) ? null : Math.max(0, Math.trunc(index));
}
