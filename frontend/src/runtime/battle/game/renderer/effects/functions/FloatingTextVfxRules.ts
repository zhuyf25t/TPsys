import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type {
  FloatingTextCreationPlan,
  FloatingTextStylePlan,
  FloatingTextTweenPlan,
  FloatingTone
} from "../objects/FloatingTextVfxObjects";

const FLOATING_TEXT_PALETTE: Record<FloatingTone, string> = {
  neutral: "#c4ccd6",
  success: "#7dff9d",
  warning: "#ffd36e",
  error: "#ff9a9a"
};

const FLOATING_TEXT_STYLE = {
  fontFamily: "Consolas",
  fontSize: "18px",
  strokeColor: "#12212b",
  strokeThickness: 3,
  origin: { x: 0.5, y: 1 },
  depth: 80
} satisfies Omit<FloatingTextStylePlan, "color">;

export function resolveFloatingTextColor(tone: FloatingTone): string {
  return FLOATING_TEXT_PALETTE[tone];
}

export function resolveFloatingTextCreationPlan(position: Vec2, text: string, color: string): FloatingTextCreationPlan {
  return {
    position: {
      x: position.x,
      y: position.y - 10
    },
    text,
    style: {
      ...FLOATING_TEXT_STYLE,
      color
    }
  };
}

export function resolveFloatingTextTweenPlan(position: Vec2): FloatingTextTweenPlan {
  return {
    y: position.y - 42,
    alpha: 0,
    durationMs: 620,
    ease: "Cubic.Out"
  };
}
