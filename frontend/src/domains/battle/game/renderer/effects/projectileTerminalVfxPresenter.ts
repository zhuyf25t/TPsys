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

/** 中文名：present投射物终止dissipatevfx（presentProjectileTerminalDissipateVfx）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function presentProjectileTerminalDissipateVfx({
  previous,
  color,
  callbacks
}: ProjectileTerminalVfxPresentation): void {
  if (previous.ttlMs <= 0) {
    callbacks.createProjectileDissipate(previous.authoritativePosition, softenColor(color));
  }
}

/** 中文名：present投射物终止rocketimpactvfx（presentProjectileTerminalRocketImpactVfx）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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

/** 中文名：presentauthoritative投射物终止reasonvfx（presentAuthoritativeProjectileTerminalReasonVfx）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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

/** 中文名：present投射物终止tracer（presentProjectileTerminalTracer）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function presentProjectileTerminalTracer({
  previous,
  color,
  callbacks
}: ProjectileTerminalVfxPresentation): void {
  callbacks.createProjectileTracer(createProjectileTerminalTracerOptions(previous, color));
}

/** 中文名：presentauthoritative投射物终止tracer（presentAuthoritativeProjectileTerminalTracer）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function presentAuthoritativeProjectileTerminalTracer({
  terminal,
  previous,
  color,
  callbacks
}: AuthoritativeProjectileTerminalTracerPresentation): void {
  callbacks.createProjectileTracer(createAuthoritativeProjectileTerminalTracerOptions(terminal, previous, color));
}

/** 中文名：present投射物终止correctiontracer（presentProjectileTerminalCorrectionTracer）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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

/** 中文名：presentauthoritative投射物终止correctiontracer（presentAuthoritativeProjectileTerminalCorrectionTracer）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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
