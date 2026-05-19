import type { Vec2 } from "../../../objects/types";
import {
  ROCKET_SPLASH_VISUAL_RADIUS,
  createAuthoritativeProjectileTerminalCorrectionTracerOptions,
  createAuthoritativeProjectileTerminalTracerOptions,
  createProjectileTerminalCorrectionTracerOptions,
  createProjectileTerminalTracerOptions,
  resolveAuthoritativeTerminalVfxStrategy,
  resolveRocketShockwaveStartRadius,
  softenColor,
  type AuthoritativeProjectileTerminalFeedbackState,
  type ProjectileFeedbackState,
  type ProjectileTracerFeedbackOptions
} from "./projectileTerminalFeedbackPolicy";

export interface ProjectileTerminalVfxPresenterCallbacks {
  createPulse(position: Vec2, radius: number, color: number): void;
  createImpactSpark(position: Vec2, color: number): void;
  createProjectileDissipate(position: Vec2, color: number): void;
  createShockwave(position: Vec2, startRadius: number, endRadius: number, color: number, duration: number): void;
  createProjectileTracer(options: ProjectileTracerFeedbackOptions): void;
}

export interface ProjectileTerminalVfxPresentation {
  previous: ProjectileFeedbackState;
  color: number;
  callbacks: ProjectileTerminalVfxPresenterCallbacks;
}

export interface AuthoritativeProjectileTerminalVfxPresentation {
  terminal: AuthoritativeProjectileTerminalFeedbackState;
  color: number;
  callbacks: ProjectileTerminalVfxPresenterCallbacks;
}

export interface AuthoritativeProjectileTerminalTracerPresentation
  extends AuthoritativeProjectileTerminalVfxPresentation {
  previous: ProjectileFeedbackState | undefined;
}

export function presentProjectileTerminalDissipateVfx({
  previous,
  color,
  callbacks
}: ProjectileTerminalVfxPresentation): void {
  if (previous.ttlMs <= 0) {
    callbacks.createProjectileDissipate(previous.authoritativePosition, softenColor(color));
  }
}

export function presentProjectileTerminalRocketImpactVfx({
  previous,
  color,
  callbacks
}: ProjectileTerminalVfxPresentation): void {
  if (previous.kind === "rocket") {
    callbacks.createImpactSpark(previous.authoritativePosition, color);
    callbacks.createShockwave(
      previous.authoritativePosition,
      resolveRocketShockwaveStartRadius(),
      ROCKET_SPLASH_VISUAL_RADIUS,
      color,
      240
    );
  }
}

export function presentAuthoritativeProjectileTerminalReasonVfx({
  terminal,
  color,
  callbacks
}: AuthoritativeProjectileTerminalVfxPresentation): void {
  const strategy = resolveAuthoritativeTerminalVfxStrategy(terminal);
  if (strategy.impactSpark !== "none") {
    callbacks.createImpactSpark(
      terminal.terminalPosition,
      strategy.impactSpark === "weak" ? softenColor(color) : color
    );
  }

  if (strategy.pulseRadius !== null) {
    callbacks.createPulse(terminal.terminalPosition, strategy.pulseRadius, color);
  }

  if (strategy.shockwaveRadius !== null) {
    callbacks.createShockwave(
      terminal.terminalPosition,
      resolveRocketShockwaveStartRadius(),
      strategy.shockwaveRadius,
      color,
      240
    );
  }

  if (strategy.dissipate) {
    callbacks.createProjectileDissipate(terminal.terminalPosition, softenColor(color));
  }
}

export function presentProjectileTerminalTracer({
  previous,
  color,
  callbacks
}: ProjectileTerminalVfxPresentation): void {
  callbacks.createProjectileTracer(createProjectileTerminalTracerOptions(previous, color));
}

export function presentAuthoritativeProjectileTerminalTracer({
  terminal,
  previous,
  color,
  callbacks
}: AuthoritativeProjectileTerminalTracerPresentation): void {
  callbacks.createProjectileTracer(createAuthoritativeProjectileTerminalTracerOptions(terminal, previous, color));
}

export function presentProjectileTerminalCorrectionTracer({
  previous,
  color,
  callbacks
}: ProjectileTerminalVfxPresentation): void {
  const tracerOptions = createProjectileTerminalCorrectionTracerOptions(previous, color);
  if (!tracerOptions) {
    return;
  }

  callbacks.createProjectileTracer(tracerOptions);
}

export function presentAuthoritativeProjectileTerminalCorrectionTracer({
  terminal,
  previous,
  color,
  callbacks
}: AuthoritativeProjectileTerminalTracerPresentation): void {
  const tracerOptions = createAuthoritativeProjectileTerminalCorrectionTracerOptions(terminal, previous, color);
  if (!tracerOptions) {
    return;
  }

  callbacks.createProjectileTracer(tracerOptions);
}
