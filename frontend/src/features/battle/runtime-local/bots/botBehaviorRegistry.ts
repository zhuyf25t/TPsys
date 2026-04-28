import { getBotProfileById } from "./botRegistry";

export interface BotBehaviorProfile {
  movementSpeedScale: number;
  sprintMultiplier: number;
  movementFrameMaxMs: number;
  movementUpdateMaxMs: number;
  maxStepDistance: number;
  gatlingBurstMinMs: number;
  gatlingBurstMaxMs: number;
  gatlingPauseMinMs: number;
  gatlingPauseMaxMs: number;
}

export interface BotBehaviorLookupInput {
  botId?: string | null;
  profileTone?: string | null;
  strategyLabel?: string | null;
}

const DEFAULT_BOT_BEHAVIOR_PROFILE: Readonly<BotBehaviorProfile> = {
  // Keep bots a touch below human base pace so pursuit reads as pressure instead of teleporting.
  movementSpeedScale: 0.94,
  sprintMultiplier: 1.12,
  movementFrameMaxMs: 14,
  movementUpdateMaxMs: 32,
  maxStepDistance: 4.75,
  gatlingBurstMinMs: 320,
  gatlingBurstMaxMs: 520,
  gatlingPauseMinMs: 60,
  gatlingPauseMaxMs: 150
};

const BOT_ID_BEHAVIOR_OVERRIDES: Readonly<Record<string, Partial<BotBehaviorProfile>>> = {};
const PROFILE_TONE_BEHAVIOR_OVERRIDES: Readonly<Record<string, Partial<BotBehaviorProfile>>> = {};
const STRATEGY_LABEL_BEHAVIOR_OVERRIDES: Readonly<Record<string, Partial<BotBehaviorProfile>>> = {};

export function getDefaultBotBehaviorProfile(): BotBehaviorProfile {
  return { ...DEFAULT_BOT_BEHAVIOR_PROFILE };
}

export function getBotBehaviorProfile(input: BotBehaviorLookupInput): BotBehaviorProfile {
  const normalizedBotId = normalizeBehaviorKey(input.botId);
  const profileFromRegistry = normalizedBotId ? getBotProfileById(normalizedBotId) : undefined;
  const normalizedProfileTone = normalizeBehaviorKey(input.profileTone) ?? normalizeBehaviorKey(profileFromRegistry?.profileTone);
  const normalizedStrategyLabel =
    normalizeBehaviorKey(input.strategyLabel) ?? normalizeBehaviorKey(profileFromRegistry?.strategyLabel);

  return {
    ...DEFAULT_BOT_BEHAVIOR_PROFILE,
    ...readBehaviorOverride(PROFILE_TONE_BEHAVIOR_OVERRIDES, normalizedProfileTone),
    ...readBehaviorOverride(STRATEGY_LABEL_BEHAVIOR_OVERRIDES, normalizedStrategyLabel),
    ...readBehaviorOverride(BOT_ID_BEHAVIOR_OVERRIDES, normalizedBotId)
  };
}

function readBehaviorOverride(
  source: Readonly<Record<string, Partial<BotBehaviorProfile>>>,
  key: string | null
): Partial<BotBehaviorProfile> {
  if (!key) {
    return {};
  }

  return source[key] ?? {};
}

function normalizeBehaviorKey(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }

  const normalized = value.trim().toLowerCase();
  return normalized.length > 0 ? normalized : null;
}
