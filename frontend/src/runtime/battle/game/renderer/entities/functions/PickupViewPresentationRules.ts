import type { BattleItemPickupState as ItemPickup, BattleWeaponPickupState as WeaponPickup } from "../../../../../../objects/battle/microservices/abilities/objects/pickup/BattlePickupState";
import type { WeaponKind } from "../../../../../../objects/battle/microservices/combat/objects/weapon/WeaponKind";
import {
  ITEM_PICKUP_READABILITY_STYLE,
  WEAPON_PICKUP_READABILITY_STYLES,
  type PickupReadabilityStyle,
  type PickupViewCreationPlan,
  type PickupViewMotionPlan,
  type PickupViewVisualPlan,
  type ResolvePickupViewCreationPlanInput,
  type ResolvePickupViewVisualPlanInput
} from "../objects/PickupViewPresentationObjects";

const PICKUP_HALO_DEPTH = 61;
const PICKUP_INNER_RING_DEPTH = 61.5;
const PICKUP_LABEL_PLATE_DEPTH = 62.5;
const PICKUP_SPRITE_DEPTH = 62;
const PICKUP_GLINT_DEPTH = 62.75;
const PICKUP_LABEL_DEPTH = 63;
const PICKUP_INNER_RING_RADIUS_SCALE = 0.58;
const PICKUP_INNER_RING_STROKE_WIDTH = 1;
const PICKUP_INNER_RING_STROKE_ALPHA = 0.32;
const PICKUP_INNER_RING_PULSE_STROKE_ALPHA = 0.26;
const PICKUP_INNER_RING_PULSE_STROKE_ALPHA_SCALE = 0.12;
const PICKUP_LABEL_PLATE_OFFSET_Y = 34;
const PICKUP_LABEL_OFFSET_Y = 26;
const PICKUP_LABEL_PLATE_MIN_WIDTH = 72;
const PICKUP_LABEL_PLATE_LABEL_PADDING = 18;
const PICKUP_LABEL_PLATE_HEIGHT = 20;
const PICKUP_LABEL_PLATE_STROKE_WIDTH = 1;
const PICKUP_LABEL_PLATE_STROKE_ALPHA = 0.24;
const PICKUP_LABEL_PLATE_PULSE_STROKE_ALPHA = 0.22;
const PICKUP_LABEL_PLATE_PULSE_STROKE_ALPHA_SCALE = 0.08;
const PICKUP_GLINT_OFFSET_X_RADIUS_SCALE = 0.3;
const PICKUP_GLINT_OFFSET_Y_RADIUS_SCALE = -0.32;
const PICKUP_GLINT_BOB_SCALE = 0.35;
const PICKUP_GLINT_WIDTH = 14;
const PICKUP_GLINT_HEIGHT = 2;
const PICKUP_GLINT_FILL_ALPHA = 0.74;
const PICKUP_GLINT_PULSE_WIDTH_SCALE = 4;
const PICKUP_GLINT_PULSE_FILL_ALPHA = 0.55;
const PICKUP_GLINT_PULSE_FILL_ALPHA_SCALE = 0.22;
const PICKUP_HALO_PULSE_RADIUS_SCALE = 2;
const PICKUP_TEXT_FONT_FAMILY = "Segoe UI";
const PICKUP_TEXT_FONT_SIZE = "12px";
const PICKUP_TEXT_ORIGIN = { x: 0.5, y: 0 };

export function getWeaponPickupReadabilityStyle(weaponKind: WeaponKind): PickupReadabilityStyle {
  return WEAPON_PICKUP_READABILITY_STYLES[weaponKind];
}

export function getItemPickupReadabilityStyle(): PickupReadabilityStyle {
  return ITEM_PICKUP_READABILITY_STYLE;
}

export function resolveItemPickupSpriteTint(): number {
  return ITEM_PICKUP_READABILITY_STYLE.strokeTint;
}

export function resolveWeaponPickupMotionPlan(pickup: WeaponPickup, elapsedMs: number): PickupViewMotionPlan {
  const pulse = resolvePickupPulse(elapsedMs, pickup.position.y, 360);
  return {
    bob: Math.sin((elapsedMs + pickup.position.x) / 240) * 4,
    pulse,
    strokePulseAlpha: pulse * 0.1,
    glintRotation: -0.42 + pulse * 0.14
  };
}

export function resolveItemPickupMotionPlan(pickup: ItemPickup, elapsedMs: number): PickupViewMotionPlan {
  const pulse = resolvePickupPulse(elapsedMs, pickup.position.y, 420);
  return {
    bob: Math.sin((elapsedMs + pickup.position.x) / 260) * 3,
    pulse,
    strokePulseAlpha: pulse * 0.08,
    glintRotation: 0.48 - pulse * 0.12
  };
}

export function resolvePickupViewCreationPlan({
  position,
  textureKey,
  frameName,
  label,
  style,
  spriteTint
}: ResolvePickupViewCreationPlanInput): PickupViewCreationPlan {
  return {
    halo: {
      position,
      radius: style.radius,
      fillColor: style.fillTint,
      fillAlpha: style.fillAlpha,
      depth: PICKUP_HALO_DEPTH,
      stroke: {
        width: style.strokeWidth,
        color: style.strokeTint,
        alpha: style.strokeAlpha
      }
    },
    innerRing: {
      position,
      radius: style.radius * PICKUP_INNER_RING_RADIUS_SCALE,
      fillColor: style.strokeTint,
      fillAlpha: 0,
      depth: PICKUP_INNER_RING_DEPTH,
      stroke: {
        width: PICKUP_INNER_RING_STROKE_WIDTH,
        color: style.strokeTint,
        alpha: PICKUP_INNER_RING_STROKE_ALPHA
      }
    },
    sprite: {
      position,
      textureKey,
      frameName,
      scale: style.spriteScale,
      depth: PICKUP_SPRITE_DEPTH,
      tint: spriteTint
    },
    labelPlate: {
      position: {
        x: position.x,
        y: position.y + PICKUP_LABEL_PLATE_OFFSET_Y
      },
      size: {
        x: PICKUP_LABEL_PLATE_MIN_WIDTH,
        y: PICKUP_LABEL_PLATE_HEIGHT
      },
      fillColor: style.labelPlateTint,
      fillAlpha: style.labelPlateAlpha,
      depth: PICKUP_LABEL_PLATE_DEPTH,
      stroke: {
        width: PICKUP_LABEL_PLATE_STROKE_WIDTH,
        color: style.strokeTint,
        alpha: PICKUP_LABEL_PLATE_STROKE_ALPHA
      }
    },
    glint: {
      position: resolvePickupGlintPosition(position, style.radius, 0),
      size: {
        x: PICKUP_GLINT_WIDTH,
        y: PICKUP_GLINT_HEIGHT
      },
      fillColor: style.glintTint,
      fillAlpha: PICKUP_GLINT_FILL_ALPHA,
      depth: PICKUP_GLINT_DEPTH
    },
    label: {
      position: {
        x: position.x,
        y: position.y + PICKUP_LABEL_OFFSET_Y
      },
      label,
      style: {
        fontFamily: PICKUP_TEXT_FONT_FAMILY,
        fontSize: PICKUP_TEXT_FONT_SIZE,
        color: style.labelColor
      },
      origin: PICKUP_TEXT_ORIGIN,
      depth: PICKUP_LABEL_DEPTH
    }
  };
}

export function resolvePickupViewVisualPlan({
  position,
  motion,
  style,
  labelWidth
}: ResolvePickupViewVisualPlanInput): PickupViewVisualPlan {
  return {
    halo: {
      position,
      radius: style.radius + motion.pulse * PICKUP_HALO_PULSE_RADIUS_SCALE,
      fill: {
        color: style.fillTint,
        alpha: style.fillAlpha
      },
      stroke: {
        width: style.strokeWidth,
        color: style.strokeTint,
        alpha: style.strokeAlpha + motion.strokePulseAlpha
      }
    },
    innerRing: {
      position,
      radius: style.radius * PICKUP_INNER_RING_RADIUS_SCALE + motion.pulse,
      fill: {
        color: style.strokeTint,
        alpha: 0
      },
      stroke: {
        width: PICKUP_INNER_RING_STROKE_WIDTH,
        color: style.strokeTint,
        alpha: PICKUP_INNER_RING_PULSE_STROKE_ALPHA + motion.pulse * PICKUP_INNER_RING_PULSE_STROKE_ALPHA_SCALE
      }
    },
    sprite: {
      position: {
        x: position.x,
        y: position.y + motion.bob
      },
      scale: style.spriteScale
    },
    label: {
      position: {
        x: position.x,
        y: position.y + PICKUP_LABEL_OFFSET_Y
      }
    },
    labelPlate: {
      position: {
        x: position.x,
        y: position.y + PICKUP_LABEL_PLATE_OFFSET_Y
      },
      size: {
        x: Math.max(PICKUP_LABEL_PLATE_MIN_WIDTH, labelWidth + PICKUP_LABEL_PLATE_LABEL_PADDING),
        y: PICKUP_LABEL_PLATE_HEIGHT
      },
      fill: {
        color: style.labelPlateTint,
        alpha: style.labelPlateAlpha
      },
      stroke: {
        width: PICKUP_LABEL_PLATE_STROKE_WIDTH,
        color: style.strokeTint,
        alpha: PICKUP_LABEL_PLATE_PULSE_STROKE_ALPHA + motion.pulse * PICKUP_LABEL_PLATE_PULSE_STROKE_ALPHA_SCALE
      }
    },
    glint: {
      position: resolvePickupGlintPosition(position, style.radius, motion.bob),
      rotation: motion.glintRotation,
      size: {
        x: PICKUP_GLINT_WIDTH + motion.pulse * PICKUP_GLINT_PULSE_WIDTH_SCALE,
        y: PICKUP_GLINT_HEIGHT
      },
      fill: {
        color: style.glintTint,
        alpha: PICKUP_GLINT_PULSE_FILL_ALPHA + motion.pulse * PICKUP_GLINT_PULSE_FILL_ALPHA_SCALE
      }
    }
  };
}

function resolvePickupPulse(elapsedMs: number, positionY: number, periodMs: number): number {
  return 0.5 + Math.sin((elapsedMs + positionY) / periodMs) * 0.5;
}

function resolvePickupGlintPosition(position: { x: number; y: number }, radius: number, bob: number): { x: number; y: number } {
  return {
    x: position.x + radius * PICKUP_GLINT_OFFSET_X_RADIUS_SCALE,
    y: position.y + radius * PICKUP_GLINT_OFFSET_Y_RADIUS_SCALE + bob * PICKUP_GLINT_BOB_SCALE
  };
}
