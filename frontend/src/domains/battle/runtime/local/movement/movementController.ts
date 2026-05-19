import type { Vec2 } from "../../../objects/types";

export interface MovementControllerInput {
  alive: boolean;
  motionActive: boolean;
  movement: Vec2;
  sprint: boolean;
  stamina: number;
  maxStamina: number;
  lastMoveDirection: Vec2;
  deltaMs: number;
  baseMoveSpeed: number;
  sprintMultiplier: number;
  staminaDrainPerSecond: number;
  staminaRecoverPerSecond: number;
  speedMultiplier?: number;
}

export interface MovementControllerResult {
  velocity: Vec2;
  stamina: number;
  lastMoveDirection: Vec2;
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

/** 中文名：推进移动（advanceMovement）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function advanceMovement(input: MovementControllerInput): MovementControllerResult {
  if (!input.alive || input.motionActive) {
    return {
      velocity: { x: 0, y: 0 },
      stamina: input.stamina,
      lastMoveDirection: input.lastMoveDirection
    };
  }

  const movement = normalizeVector(input.movement);
  const hasMovement = movement.x !== 0 || movement.y !== 0;
  const canSprint = input.sprint && hasMovement && input.stamina > 0;
  const speedMultiplier = input.speedMultiplier ?? 1;
  const speed = (canSprint ? input.baseMoveSpeed * input.sprintMultiplier : input.baseMoveSpeed) * speedMultiplier;
  const deltaSeconds = Math.max(0, input.deltaMs) / 1000;

  const stamina = canSprint
    ? Math.max(0, input.stamina - input.staminaDrainPerSecond * deltaSeconds)
    : Math.min(input.maxStamina, input.stamina + input.staminaRecoverPerSecond * deltaSeconds);

  return {
    velocity: {
      x: movement.x * speed,
      y: movement.y * speed
    },
    stamina,
    lastMoveDirection: hasMovement ? movement : input.lastMoveDirection
  };
}
