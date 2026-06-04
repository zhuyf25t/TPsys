import type { BattleGameSnapshot as GameSnapshot } from "../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import {
  PROJECTILE_SPARK_COLORS,
  ROCKET_SPLASH_VISUAL_RADIUS,
  createAuthoritativeProjectileTerminalCorrectionTracerOptions,
  createAuthoritativeProjectileTerminalTracerOptions,
  createProjectileTerminalCorrectionTracerOptions,
  createProjectileTerminalTracerOptions,
  createRemoteGatlingProjectileBirthTracerOptions,
  resolveAuthoritativeTerminalVfxStrategy,
  resolveRemoteProjectileBirthFeedbackPosition,
  resolveRocketShockwaveStartRadius,
  softenColor,
  type AuthoritativeProjectileTerminalFeedbackState,
  type ProjectileFeedbackState,
  type ProjectileTracerFeedbackOptions
} from "./BattleProjectileFeedbackRules";

export type BattleProjectileFeedbackEffectPlan =
  | {
      readonly effect: "impactSpark";
      readonly position: Vec2;
      readonly color: number;
    }
  | {
      readonly effect: "pulse";
      readonly position: Vec2;
      readonly radius: number;
      readonly color: number;
    }
  | {
      readonly effect: "projectileDissipate";
      readonly position: Vec2;
      readonly color: number;
    }
  | {
      readonly effect: "shockwave";
      readonly position: Vec2;
      readonly startRadius: number;
      readonly endRadius: number;
      readonly color: number;
      readonly durationMs: number;
    }
  | {
      readonly effect: "projectileTracer";
      readonly options: ProjectileTracerFeedbackOptions;
    };

export interface BattleProjectileTerminalFeedbackPlanInput {
  previous: ProjectileFeedbackState;
  color: number;
}

export interface BattleAuthoritativeProjectileTerminalReasonFeedbackPlanInput {
  terminal: AuthoritativeProjectileTerminalFeedbackState;
  color: number;
}

export interface BattleAuthoritativeProjectileTerminalTracerFeedbackPlanInput
  extends BattleAuthoritativeProjectileTerminalReasonFeedbackPlanInput {
  previous: ProjectileFeedbackState | undefined;
}

export interface BattleRemoteProjectileBirthFeedbackPlan {
  projectile: GameSnapshot["projectiles"][number];
  ownerDisplayName: string | undefined;
  position: Vec2;
  effects: readonly BattleProjectileFeedbackEffectPlan[];
}

export interface BattleRemoteProjectileBirthFeedbackPlanInput {
  snapshot: GameSnapshot;
  previousProjectileStates: ReadonlyMap<string, ProjectileFeedbackState>;
}

export function planBattleProjectileTerminalDissipateEffects(
  input: BattleProjectileTerminalFeedbackPlanInput
): readonly BattleProjectileFeedbackEffectPlan[] {
  if (input.previous.ttlMs > 0) {
    return [];
  }

  return [
    {
      effect: "projectileDissipate",
      position: cloneVec2(input.previous.authoritativePosition),
      color: softenColor(input.color)
    }
  ];
}

export function planBattleProjectileTerminalRocketImpactEffects(
  input: BattleProjectileTerminalFeedbackPlanInput
): readonly BattleProjectileFeedbackEffectPlan[] {
  if (input.previous.kind !== "rocket") {
    return [];
  }

  const position = cloneVec2(input.previous.authoritativePosition);
  return [
    {
      effect: "impactSpark",
      position,
      color: input.color
    },
    {
      effect: "shockwave",
      position,
      startRadius: resolveRocketShockwaveStartRadius(),
      endRadius: ROCKET_SPLASH_VISUAL_RADIUS,
      color: input.color,
      durationMs: 240
    }
  ];
}

export function planBattleAuthoritativeProjectileTerminalReasonEffects(
  input: BattleAuthoritativeProjectileTerminalReasonFeedbackPlanInput
): readonly BattleProjectileFeedbackEffectPlan[] {
  const strategy = resolveAuthoritativeTerminalVfxStrategy(input.terminal);
  const position = cloneVec2(input.terminal.terminalPosition);
  const effects: BattleProjectileFeedbackEffectPlan[] = [];

  if (strategy.impactSpark !== "none") {
    effects.push({
      effect: "impactSpark",
      position,
      color: strategy.impactSpark === "weak" ? softenColor(input.color) : input.color
    });
  }

  if (strategy.pulseRadius !== null) {
    effects.push({
      effect: "pulse",
      position,
      radius: strategy.pulseRadius,
      color: input.color
    });
  }

  if (strategy.shockwaveRadius !== null) {
    effects.push({
      effect: "shockwave",
      position,
      startRadius: resolveRocketShockwaveStartRadius(),
      endRadius: strategy.shockwaveRadius,
      color: input.color,
      durationMs: 240
    });
  }

  if (strategy.dissipate) {
    effects.push({
      effect: "projectileDissipate",
      position,
      color: softenColor(input.color)
    });
  }

  return effects;
}

export function planBattleProjectileTerminalTracerEffects(
  input: BattleProjectileTerminalFeedbackPlanInput
): readonly BattleProjectileFeedbackEffectPlan[] {
  return [
    {
      effect: "projectileTracer",
      options: cloneTracerOptions(createProjectileTerminalTracerOptions(input.previous, input.color))
    }
  ];
}

export function planBattleAuthoritativeProjectileTerminalTracerEffects(
  input: BattleAuthoritativeProjectileTerminalTracerFeedbackPlanInput
): readonly BattleProjectileFeedbackEffectPlan[] {
  return [
    {
      effect: "projectileTracer",
      options: cloneTracerOptions(
        createAuthoritativeProjectileTerminalTracerOptions(input.terminal, input.previous, input.color)
      )
    }
  ];
}

export function planBattleProjectileTerminalCorrectionTracerEffects(
  input: BattleProjectileTerminalFeedbackPlanInput
): readonly BattleProjectileFeedbackEffectPlan[] {
  const tracerOptions = createProjectileTerminalCorrectionTracerOptions(input.previous, input.color);
  return tracerOptions
    ? [
        {
          effect: "projectileTracer",
          options: cloneTracerOptions(tracerOptions)
        }
      ]
    : [];
}

export function planBattleAuthoritativeProjectileTerminalCorrectionTracerEffects(
  input: BattleAuthoritativeProjectileTerminalTracerFeedbackPlanInput
): readonly BattleProjectileFeedbackEffectPlan[] {
  const tracerOptions = createAuthoritativeProjectileTerminalCorrectionTracerOptions(
    input.terminal,
    input.previous,
    input.color
  );
  return tracerOptions
    ? [
        {
          effect: "projectileTracer",
          options: cloneTracerOptions(tracerOptions)
        }
      ]
    : [];
}

export function planBattleAuthoritativeRemoteProjectileBirthFeedback(
  input: BattleRemoteProjectileBirthFeedbackPlanInput
): readonly BattleRemoteProjectileBirthFeedbackPlan[] {
  const plans: BattleRemoteProjectileBirthFeedbackPlan[] = [];

  input.snapshot.projectiles.forEach((projectile) => {
    if (
      input.previousProjectileStates.has(projectile.projectileId) ||
      projectile.ownerHeroId === input.snapshot.playerHeroId
    ) {
      return;
    }

    const owner = input.snapshot.heroes.find((hero) => hero.heroId === projectile.ownerHeroId);
    const position = resolveRemoteProjectileBirthFeedbackPosition(projectile, owner);
    const color = PROJECTILE_SPARK_COLORS[projectile.kind];
    const effects: BattleProjectileFeedbackEffectPlan[] =
      projectile.kind === "gatling-bullet"
        ? [
            {
              effect: "projectileTracer",
              options: cloneTracerOptions(createRemoteGatlingProjectileBirthTracerOptions(projectile, position, color))
            }
          ]
        : [
            {
              effect: "impactSpark",
              position: cloneVec2(position),
              color
            }
          ];

    if (projectile.kind === "rocket") {
      effects.push({
        effect: "pulse",
        position: cloneVec2(position),
        radius: 16,
        color
      });
    }

    plans.push({
      projectile,
      ownerDisplayName: owner?.displayName,
      position: cloneVec2(position),
      effects
    });
  });

  return plans;
}

function cloneVec2(position: Vec2): Vec2 {
  return {
    x: position.x,
    y: position.y
  };
}

function cloneTracerOptions(options: ProjectileTracerFeedbackOptions): ProjectileTracerFeedbackOptions {
  return {
    ...options,
    start: cloneVec2(options.start),
    direction: cloneVec2(options.direction)
  };
}
