import type { Hero, Projectile, Vec2 } from "../../../../../objects/battle/types";
import { resolveProjectileBirthPosition } from "../../projectileBirth";
import { WEAPON_DEFINITIONS } from "../../weapons";
import type { BattleRuntimeAuthoritativeFrame } from "../authoritativeBattleStateBridge";

export interface ProjectileFeedbackState {
  ownerHeroId: string;
  kind: Projectile["kind"];
  displayPosition: Vec2;
  authoritativePosition: Vec2;
  direction: Vec2;
  ttlMs: number;
  maxLifetimeMs: number;
}

export type AuthoritativeProjectileTerminalFrame = BattleRuntimeAuthoritativeFrame["projectileTerminals"][number];

export interface AuthoritativeProjectileTerminalFeedbackState {
  projectileId: string;
  ownerPlayerId: string;
  ownerHeroId: string;
  kind: Projectile["kind"];
  reason: string;
  start: Vec2;
  end: Vec2;
  terminalPosition: Vec2;
  ttlBefore: number;
  ttlAfter: number;
  elapsedMs: number;
  targetPlayerId: string | null;
  targetHeroId: string | null;
  hpBefore: number | null;
  hpAfter: number | null;
  damage: number | null;
}

export interface AuthoritativeProjectileTerminalVfxStrategy {
  impactSpark: "none" | "normal" | "weak";
  pulseRadius: number | null;
  shockwaveRadius: number | null;
  dissipate: boolean;
}

export type AuthoritativeProjectileTerminalVfxBudgetReason = "queue-limit" | "per-update-limit";

export interface ProjectileTracerFeedbackOptions {
  start: Vec2;
  direction: Vec2;
  length: number;
  color: number;
  thickness: number;
  durationMs: number;
  alpha?: number;
  ghostScale?: number;
  glintAlphaScale?: number;
  underglowAlphaScale?: number;
  coreAlphaScale?: number;
  ghostAlphaScale?: number;
}

export interface AuthoritativeProjectileTerminalVfxCandidate {
  terminalKey: string;
  terminal: AuthoritativeProjectileTerminalFeedbackState;
}

export interface AuthoritativeProjectileTerminalQueueDecisionInput {
  seenLive: boolean;
  terminalElapsedMs: number;
  freshnessBaselineElapsedMs: number;
}

export interface NearestTerminalHero {
  heroId: string;
  displayName: string;
  authoritativeEdgeDistance: number;
  displayEdgeDistance: number;
}

export const PROJECTILE_SPARK_COLORS: Record<Projectile["kind"], number> = {
  "pistol-bullet": 0xfff0c6,
  rocket: 0xffb36f,
  "gatling-bullet": 0xffd86d,
  "shotgun-pellet": 0xffefb7
};

export const REMEMBERED_LIVE_PROJECTILE_ID_LIMIT = 256;
export const PLAYED_AUTHORITATIVE_PROJECTILE_TERMINAL_LIMIT = 256;
export const AUTHORITATIVE_PROJECTILE_TERMINAL_VFX_QUEUE_LIMIT = 96;
export const AUTHORITATIVE_PROJECTILE_TERMINAL_VFX_PER_UPDATE_LIMIT = 12;
export const ROCKET_SPLASH_VISUAL_RADIUS = WEAPON_DEFINITIONS.RocketLauncher.splashRadius;

const PROJECTILE_TERMINAL_TRACER_LENGTHS: Record<Projectile["kind"], number> = {
  "pistol-bullet": 34,
  rocket: 48,
  "gatling-bullet": 34,
  "shotgun-pellet": 22
};
const PROJECTILE_TERMINAL_TRACER_THICKNESS: Record<Projectile["kind"], number> = {
  "pistol-bullet": 2,
  rocket: 6,
  "gatling-bullet": 2,
  "shotgun-pellet": 4
};
const PROJECTILE_TERMINAL_TRACER_DURATION_MS = 180;
const PROJECTILE_TERMINAL_TRACER_ALPHA: Record<Projectile["kind"], number> = {
  "pistol-bullet": 0.34,
  rocket: 0.58,
  "gatling-bullet": 0.32,
  "shotgun-pellet": 0.3
};
const PROJECTILE_TERMINAL_TRACER_GHOST_SCALE: Record<Projectile["kind"], number> = {
  "pistol-bullet": 0.52,
  rocket: 1,
  "gatling-bullet": 0.48,
  "shotgun-pellet": 0.45
};
const PROJECTILE_CORRECTION_TRACER_MIN_DISTANCE = 18;
const PROJECTILE_CORRECTION_TRACER_MAX_DISTANCE = 140;
const PROJECTILE_CORRECTION_TRACER_DURATION_MS = 140;
const REMOTE_PROJECTILE_BIRTH_FALLBACK_BACKSTEP = 10;

/** 中文名：创建投射物feedback状态（createProjectileFeedbackState）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createProjectileFeedbackState(
  projectile: Projectile,
  displayPosition: Vec2,
  direction: Vec2
): ProjectileFeedbackState {
  return {
    ownerHeroId: projectile.ownerHeroId,
    kind: projectile.kind,
    displayPosition: { x: displayPosition.x, y: displayPosition.y },
    authoritativePosition: { x: projectile.position.x, y: projectile.position.y },
    direction: { x: direction.x, y: direction.y },
    ttlMs: projectile.ttlMs,
    maxLifetimeMs: projectile.maxLifetimeMs
  };
}

/** 中文名：创建authoritative投射物终止feedback状态（createAuthoritativeProjectileTerminalFeedbackState）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createAuthoritativeProjectileTerminalFeedbackState(
  terminal: AuthoritativeProjectileTerminalFrame
): AuthoritativeProjectileTerminalFeedbackState | null {
  const kind = normalizeProjectileKind(terminal.kind);
  if (!kind) {
    return null;
  }

  return {
    projectileId: terminal.projectileId,
    ownerPlayerId: terminal.ownerPlayerId,
    ownerHeroId: terminal.ownerHeroId,
    kind,
    reason: terminal.reason,
    start: { x: terminal.start.x, y: terminal.start.y },
    end: { x: terminal.end.x, y: terminal.end.y },
    terminalPosition: { x: terminal.terminalPosition.x, y: terminal.terminalPosition.y },
    ttlBefore: Math.max(0, Math.round(terminal.ttlBefore)),
    ttlAfter: Math.max(0, Math.round(terminal.ttlAfter)),
    elapsedMs: Math.max(0, Math.round(terminal.elapsedMs)),
    targetPlayerId: terminal.targetPlayerId,
    targetHeroId: terminal.targetHeroId,
    hpBefore: terminal.hpBefore,
    hpAfter: terminal.hpAfter,
    damage: terminal.damage
  };
}

/** 中文名：创建authoritative投射物终止key（createAuthoritativeProjectileTerminalKey）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createAuthoritativeProjectileTerminalKey(
  terminal: AuthoritativeProjectileTerminalFrame
): string {
  return [
    terminal.projectileId,
    terminal.reason,
    Math.round(terminal.elapsedMs),
    Math.round(terminal.terminalPosition.x * 100) / 100,
    Math.round(terminal.terminalPosition.y * 100) / 100
  ].join(":");
}

/** 中文名：should队列authoritative投射物终止（shouldQueueAuthoritativeProjectileTerminal）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function shouldQueueAuthoritativeProjectileTerminal(
  input: AuthoritativeProjectileTerminalQueueDecisionInput
): boolean {
  if (input.seenLive) {
    return true;
  }

  // After startup, unseen terminals represent too-fast projectiles rather than retained history.
  return normalizeElapsedMs(input.terminalElapsedMs) >= input.freshnessBaselineElapsedMs;
}

/** 中文名：解析authoritative帧已流逝watermark（resolveAuthoritativeFrameElapsedWatermark）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveAuthoritativeFrameElapsedWatermark(frame: BattleRuntimeAuthoritativeFrame): number {
  const frameElapsedMs = normalizeElapsedMs(frame.elapsedMs);
  if (frameElapsedMs > 0 || frame.projectileTerminals.length === 0) {
    return frameElapsedMs;
  }

  return frame.projectileTerminals.reduce(
    (watermark, terminal) => Math.max(watermark, normalizeElapsedMs(terminal.elapsedMs)),
    frameElapsedMs
  );
}

/** 中文名：规范化已流逝ms（normalizeElapsedMs）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function normalizeElapsedMs(elapsedMs: number): number {
  return Number.isFinite(elapsedMs) ? Math.max(0, Math.round(elapsedMs)) : 0;
}

/** 中文名：selectauthoritative投射物终止vfxkeys（selectAuthoritativeProjectileTerminalVfxKeys）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function selectAuthoritativeProjectileTerminalVfxKeys(
  terminals: ReadonlyArray<AuthoritativeProjectileTerminalVfxCandidate>,
  limit: number
): Set<string> {
  if (terminals.length <= limit) {
    return new Set(terminals.map(({ terminalKey }) => terminalKey));
  }

  const selected = new Set<string>();
  terminals.forEach(({ terminalKey, terminal }) => {
    if (selected.size < limit && terminal.reason === "hit") {
      selected.add(terminalKey);
    }
  });

  terminals.forEach(({ terminalKey }) => {
    if (selected.size < limit) {
      selected.add(terminalKey);
    }
  });

  return selected;
}

/** 中文名：解析authoritative投射物终止队列dropkey（resolveAuthoritativeProjectileTerminalQueueDropKey）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveAuthoritativeProjectileTerminalQueueDropKey(
  queuedTerminals: ReadonlyMap<string, AuthoritativeProjectileTerminalFeedbackState>,
  incomingTerminalKey: string,
  incomingTerminal: AuthoritativeProjectileTerminalFeedbackState
): string {
  for (const [terminalKey, terminal] of queuedTerminals) {
    if (terminal.reason !== "hit") {
      return terminalKey;
    }
  }

  return incomingTerminal.reason === "hit"
    ? queuedTerminals.keys().next().value ?? incomingTerminalKey
    : incomingTerminalKey;
}

/** 中文名：解析authoritative终止vfxstrategy（resolveAuthoritativeTerminalVfxStrategy）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveAuthoritativeTerminalVfxStrategy(
  terminal: AuthoritativeProjectileTerminalFeedbackState
): AuthoritativeProjectileTerminalVfxStrategy {
  if (terminal.kind === "gatling-bullet") {
    return {
      impactSpark: "none",
      pulseRadius: null,
      shockwaveRadius: null,
      dissipate: false
    };
  }

  if (terminal.kind === "rocket") {
    return {
      impactSpark: terminal.reason === "hit" || terminal.reason === "obstacle" ? "normal" : "weak",
      pulseRadius: null,
      shockwaveRadius: ROCKET_SPLASH_VISUAL_RADIUS,
      dissipate: false
    };
  }

  switch (terminal.reason) {
    case "hit":
    case "obstacle":
      return {
        impactSpark: "normal",
        pulseRadius: null,
        shockwaveRadius: null,
        dissipate: false
      };
    case "world":
      return {
        impactSpark: "weak",
        pulseRadius: 10,
        shockwaveRadius: null,
        dissipate: false
      };
    case "ttl":
      return {
        impactSpark: "none",
        pulseRadius: null,
        shockwaveRadius: null,
        dissipate: true
      };
    default:
      return {
        impactSpark: "weak",
        pulseRadius: null,
        shockwaveRadius: null,
        dissipate: false
      };
  }
}

/** 中文名：解析rocketshockwavestartradius（resolveRocketShockwaveStartRadius）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveRocketShockwaveStartRadius(): number {
  return Math.max(18, ROCKET_SPLASH_VISUAL_RADIUS * 0.16);
}

/** 中文名：创建投射物终止traceroptions（createProjectileTerminalTracerOptions）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createProjectileTerminalTracerOptions(
  previous: ProjectileFeedbackState,
  color: number
): ProjectileTracerFeedbackOptions {
  const length = PROJECTILE_TERMINAL_TRACER_LENGTHS[previous.kind];
  return {
    start: {
      x: previous.authoritativePosition.x - previous.direction.x * length,
      y: previous.authoritativePosition.y - previous.direction.y * length
    },
    direction: previous.direction,
    length,
    color,
    thickness: PROJECTILE_TERMINAL_TRACER_THICKNESS[previous.kind],
    durationMs: PROJECTILE_TERMINAL_TRACER_DURATION_MS,
    alpha: PROJECTILE_TERMINAL_TRACER_ALPHA[previous.kind],
    ghostScale: PROJECTILE_TERMINAL_TRACER_GHOST_SCALE[previous.kind],
    ...resolveProjectileTracerNoiseOptions(previous.kind)
  };
}

/** 中文名：创建authoritative投射物终止traceroptions（createAuthoritativeProjectileTerminalTracerOptions）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createAuthoritativeProjectileTerminalTracerOptions(
  terminal: AuthoritativeProjectileTerminalFeedbackState,
  previous: ProjectileFeedbackState | undefined,
  color: number
): ProjectileTracerFeedbackOptions {
  const direction = resolveAuthoritativeTerminalDirection(terminal, previous);
  const length = PROJECTILE_TERMINAL_TRACER_LENGTHS[terminal.kind];
  return {
    start: {
      x: terminal.terminalPosition.x - direction.x * length,
      y: terminal.terminalPosition.y - direction.y * length
    },
    direction,
    length,
    color,
    thickness: PROJECTILE_TERMINAL_TRACER_THICKNESS[terminal.kind],
    durationMs: PROJECTILE_TERMINAL_TRACER_DURATION_MS,
    alpha: PROJECTILE_TERMINAL_TRACER_ALPHA[terminal.kind],
    ghostScale: PROJECTILE_TERMINAL_TRACER_GHOST_SCALE[terminal.kind],
    ...resolveProjectileTracerNoiseOptions(terminal.kind)
  };
}

/** 中文名：创建投射物终止correctiontraceroptions（createProjectileTerminalCorrectionTracerOptions）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createProjectileTerminalCorrectionTracerOptions(
  previous: ProjectileFeedbackState,
  color: number
): ProjectileTracerFeedbackOptions | null {
  const distance = distanceBetween(previous.displayPosition, previous.authoritativePosition);
  if (
    distance <= PROJECTILE_CORRECTION_TRACER_MIN_DISTANCE ||
    distance > PROJECTILE_CORRECTION_TRACER_MAX_DISTANCE
  ) {
    return null;
  }

  return {
    start: previous.displayPosition,
    direction: {
      x: (previous.authoritativePosition.x - previous.displayPosition.x) / distance,
      y: (previous.authoritativePosition.y - previous.displayPosition.y) / distance
    },
    length: distance,
    color,
    thickness: 2,
    durationMs: PROJECTILE_CORRECTION_TRACER_DURATION_MS,
    alpha: 0.38,
    ghostScale: 0.35,
    glintAlphaScale: 0,
    underglowAlphaScale: 0,
    coreAlphaScale: 0.46,
    ghostAlphaScale: 0
  };
}

/** 中文名：创建authoritative投射物终止correctiontraceroptions（createAuthoritativeProjectileTerminalCorrectionTracerOptions）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createAuthoritativeProjectileTerminalCorrectionTracerOptions(
  terminal: AuthoritativeProjectileTerminalFeedbackState,
  previous: ProjectileFeedbackState | undefined,
  color: number
): ProjectileTracerFeedbackOptions | null {
  if (!previous) {
    return null;
  }

  const distance = distanceBetween(previous.displayPosition, terminal.terminalPosition);
  if (
    distance <= PROJECTILE_CORRECTION_TRACER_MIN_DISTANCE ||
    distance > PROJECTILE_CORRECTION_TRACER_MAX_DISTANCE
  ) {
    return null;
  }

  return {
    start: previous.displayPosition,
    direction: {
      x: (terminal.terminalPosition.x - previous.displayPosition.x) / distance,
      y: (terminal.terminalPosition.y - previous.displayPosition.y) / distance
    },
    length: distance,
    color,
    thickness: 2,
    durationMs: PROJECTILE_CORRECTION_TRACER_DURATION_MS,
    alpha: 0.38,
    ghostScale: 0.35,
    glintAlphaScale: 0,
    underglowAlphaScale: 0,
    coreAlphaScale: 0.46,
    ghostAlphaScale: 0
  };
}

/** 中文名：创建remotegatling投射物birthtraceroptions（createRemoteGatlingProjectileBirthTracerOptions）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createRemoteGatlingProjectileBirthTracerOptions(
  projectile: Projectile,
  position: Vec2,
  color: number
): ProjectileTracerFeedbackOptions {
  return {
    start: {
      x: position.x - projectile.velocity.x * 0.012,
      y: position.y - projectile.velocity.y * 0.012
    },
    direction: resolveProjectileDirection(projectile),
    length: 18,
    color,
    thickness: 1,
    durationMs: 58,
    alpha: 0.26,
    ghostScale: 0.35,
    glintAlphaScale: 0,
    underglowAlphaScale: 0,
    coreAlphaScale: 0.35,
    ghostAlphaScale: 0
  };
}

/** 中文名：softencolor（softenColor）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function softenColor(color: number): number {
  const red = (color >> 16) & 0xff;
  const green = (color >> 8) & 0xff;
  const blue = color & 0xff;
  return (
    (Math.round((red + 0x80) / 2) << 16) |
    (Math.round((green + 0x80) / 2) << 8) |
    Math.round((blue + 0x80) / 2)
  );
}

/** 中文名：解析authoritative终止direction（resolveAuthoritativeTerminalDirection）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveAuthoritativeTerminalDirection(
  terminal: AuthoritativeProjectileTerminalFeedbackState,
  previous: ProjectileFeedbackState | undefined
): Vec2 {
  const terminalDelta = {
    x: terminal.terminalPosition.x - terminal.start.x,
    y: terminal.terminalPosition.y - terminal.start.y
  };
  const terminalDeltaLength = Math.hypot(terminalDelta.x, terminalDelta.y);
  if (terminalDeltaLength > 0.0001) {
    return {
      x: terminalDelta.x / terminalDeltaLength,
      y: terminalDelta.y / terminalDeltaLength
    };
  }

  const segmentDelta = {
    x: terminal.end.x - terminal.start.x,
    y: terminal.end.y - terminal.start.y
  };
  const segmentDeltaLength = Math.hypot(segmentDelta.x, segmentDelta.y);
  if (segmentDeltaLength > 0.0001) {
    return {
      x: segmentDelta.x / segmentDeltaLength,
      y: segmentDelta.y / segmentDeltaLength
    };
  }

  return previous?.direction ?? { x: 1, y: 0 };
}

/** 中文名：创建终止diagnostic投射物状态（createTerminalDiagnosticProjectileState）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createTerminalDiagnosticProjectileState(
  terminal: AuthoritativeProjectileTerminalFeedbackState,
  previous: ProjectileFeedbackState | undefined
): ProjectileFeedbackState {
  const direction = resolveAuthoritativeTerminalDirection(terminal, previous);
  return {
    kind: terminal.kind,
    ownerHeroId: previous?.ownerHeroId ?? terminal.ownerHeroId,
    displayPosition: previous
      ? { x: previous.displayPosition.x, y: previous.displayPosition.y }
      : { x: terminal.terminalPosition.x, y: terminal.terminalPosition.y },
    authoritativePosition: { x: terminal.terminalPosition.x, y: terminal.terminalPosition.y },
    direction,
    ttlMs: terminal.ttlAfter,
    maxLifetimeMs: previous?.maxLifetimeMs ?? Math.max(terminal.ttlBefore, terminal.ttlAfter)
  };
}

/** 中文名：解析remote投射物birthfeedbackposition（resolveRemoteProjectileBirthFeedbackPosition）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveRemoteProjectileBirthFeedbackPosition(
  projectile: Projectile,
  owner?: Hero
): Vec2 {
  const direction = resolveProjectileDirection(projectile);

  if (owner) {
    return resolveProjectileBirthPosition({
      ownerPosition: owner.position,
      direction,
      ownerRadius: owner.radius,
      projectileRadius: projectile.radius
    });
  }

  return {
    x: projectile.position.x - direction.x * REMOTE_PROJECTILE_BIRTH_FALLBACK_BACKSTEP,
    y: projectile.position.y - direction.y * REMOTE_PROJECTILE_BIRTH_FALLBACK_BACKSTEP
  };
}

/** 中文名：解析投射物direction（resolveProjectileDirection）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveProjectileDirection(projectile: Projectile): Vec2 {
  const velocityLength = Math.hypot(projectile.velocity.x, projectile.velocity.y);
  if (velocityLength > 0.0001) {
    return {
      x: projectile.velocity.x / velocityLength,
      y: projectile.velocity.y / velocityLength
    };
  }

  return {
    x: Math.cos(projectile.facing),
    y: Math.sin(projectile.facing)
  };
}

/** 中文名：解析nearest终止英雄（resolveNearestTerminalHero）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveNearestTerminalHero(
  projectile: ProjectileFeedbackState,
  heroes: Hero[],
  getHeroDisplayPosition: (heroId: string) => Vec2 | null
): NearestTerminalHero | null {
  let nearest: NearestTerminalHero | null = null;

  heroes.forEach((hero) => {
    const authoritativeEdgeDistance = distanceBetween(projectile.authoritativePosition, hero.position) - hero.radius;
    const heroDisplayPosition = getHeroDisplayPosition(hero.heroId) ?? hero.position;
    const displayEdgeDistance = distanceBetween(projectile.displayPosition, heroDisplayPosition) - hero.radius;
    if (!nearest || authoritativeEdgeDistance < nearest.authoritativeEdgeDistance) {
      nearest = {
        heroId: hero.heroId,
        displayName: hero.displayName,
        authoritativeEdgeDistance,
        displayEdgeDistance
      };
    }
  });

  return nearest;
}

/** 中文名：判断是否本地投射物终止（isLocalProjectileTerminal）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function isLocalProjectileTerminal(projectile: ProjectileFeedbackState, playerHeroId: string): boolean {
  return projectile.ownerHeroId === playerHeroId;
}

/** 中文名：判断是否本地authoritative投射物终止（isLocalAuthoritativeProjectileTerminal）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function isLocalAuthoritativeProjectileTerminal(
  terminal: AuthoritativeProjectileTerminalFeedbackState,
  playerHeroId: string
): boolean {
  return terminal.ownerHeroId === playerHeroId;
}

/** 中文名：distancebetween（distanceBetween）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function distanceBetween(left: Vec2, right: Vec2): number {
  return Math.hypot(right.x - left.x, right.y - left.y);
}

function normalizeProjectileKind(kind: string): Projectile["kind"] | null {
  switch (kind) {
    case "pistol-bullet":
    case "rocket":
    case "gatling-bullet":
    case "shotgun-pellet":
      return kind;
    default:
      return null;
  }
}

function resolveProjectileTracerNoiseOptions(kind: Projectile["kind"]): Pick<
  ProjectileTracerFeedbackOptions,
  "glintAlphaScale" | "underglowAlphaScale" | "coreAlphaScale" | "ghostAlphaScale"
> {
  if (kind === "gatling-bullet") {
    return {
      glintAlphaScale: 0,
      underglowAlphaScale: 0,
      coreAlphaScale: 0.36,
      ghostAlphaScale: 0
    };
  }

  return {};
}
