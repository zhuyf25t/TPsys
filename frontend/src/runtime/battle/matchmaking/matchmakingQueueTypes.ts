import { getBotProfileBySlot } from "../../bots/registry/botRegistry";
import {
  BATTLE_ARENA_PLAYER_CAPACITY,
  BATTLE_MATCHMAKING_DURATION_MS,
  battleArenaPlayerCapacityForMode
} from "../../../objects/battle/objects/core/BattleCoreRules";
import type {
  BattleModeIdDto,
  BattleRoomChatMessageResponseDto,
  BattleQueueParticipantResponseDto,
  BattleSessionBootstrapResponseDto,
  BattleSessionBootstrapSeatResponseDto,
  BattleSessionDescriptorResponseDto,
  BattleSessionRosterEntryResponseDto
} from "../../../objects/battle/microservices/queue/api/shared/BattleLobbySharedApiTypes";

export interface MatchmakingQueueParticipant extends BattleQueueParticipantResponseDto {}

export interface MatchmakingRoomChatMessage extends BattleRoomChatMessageResponseDto {}

export interface MatchmakingBattleSessionRosterEntry extends BattleSessionRosterEntryResponseDto {}

export interface MatchmakingBattleSessionBootstrapSeat extends BattleSessionBootstrapSeatResponseDto {}

export interface MatchmakingBattleSessionBootstrap extends BattleSessionBootstrapResponseDto {
  seats: MatchmakingBattleSessionBootstrapSeat[];
}

export interface MatchmakingBattleSessionDescriptor extends BattleSessionDescriptorResponseDto {
  roster: MatchmakingBattleSessionRosterEntry[];
  bootstrap?: MatchmakingBattleSessionBootstrap;
}

export type MatchmakingQueuePlayer = MatchmakingQueueParticipant;
export type MatchmakingRoomPhase = "waiting" | "active" | "finished" | "unknown";
export type MatchmakingSeatKind = "self" | "player" | "bot" | "empty";

export interface MatchmakingQueueState {
  ticketId: string;
  playerId: string;
  roomId: string;
  matchId: string;
  modeId: BattleModeIdDto;
  modeLabel: string;
  mapId: string;
  mapLabel: string;
  createdAt: number;
  startsAt: number;
  deadline: number;
  serverTime?: number;
  syncedAt?: number;
  participants: MatchmakingQueueParticipant[];
  players: MatchmakingQueueParticipant[];
  queuedHandles: string[];
  capacity: number;
  durationMs: number;
  phase?: MatchmakingRoomPhase;
  startPaused: boolean;
  pausedRemainingMs?: number;
  chatMessages: MatchmakingRoomChatMessage[];
  finishedAt?: number;
  battleSession?: MatchmakingBattleSessionDescriptor;
  source?: "backend" | "local";
}

export interface MatchmakingSlotState {
  slotLabel: string;
  kind: MatchmakingSeatKind;
  title: string;
  detail: string;
  isInteractive: boolean;
  isLocalPlayer: boolean;
  isHost: boolean;
}

export const MATCHMAKING_SLOT_COUNT = BATTLE_ARENA_PLAYER_CAPACITY;
const MATCHMAKING_DURATION_MS = BATTLE_MATCHMAKING_DURATION_MS;

export function resolveMatchmakingSlotCount(
  queueState: MatchmakingQueueState | null,
  fallbackModeId?: BattleModeIdDto | null
): number {
  const explicitCapacity = Math.trunc(queueState?.capacity ?? Number.NaN);
  if (Number.isFinite(explicitCapacity) && explicitCapacity > 0) {
    return explicitCapacity;
  }

  return battleArenaPlayerCapacityForMode(queueState?.modeId ?? fallbackModeId ?? null);
}

/** 中文名：构建matchmakingslots（buildMatchmakingSlots）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function buildMatchmakingSlots(
  localHandle: string,
  queueState: MatchmakingQueueState | null,
  fallbackModeId?: BattleModeIdDto | null
): MatchmakingSlotState[] {
  const slotCount = resolveMatchmakingSlotCount(queueState, fallbackModeId);
  const normalizedLocalHandle = normalizeHandle(localHandle);
  const participants = dedupeParticipants(queueState?.participants ?? []);
  const hostParticipant = participants[0];
  const localPlayerId = queueState?.playerId.trim() ?? "";
  const localParticipant = participants.find((participant) =>
    isLocalParticipant(participant, localPlayerId, normalizedLocalHandle)
  );
  const otherParticipants = participants.filter(
    (participant) => !isSameParticipant(participant, localParticipant, localPlayerId, normalizedLocalHandle)
  );
  const slots: MatchmakingSlotState[] = [];

  if (normalizedLocalHandle) {
    slots.push(buildLocalSeat(localHandle, localParticipant, queueState, isHostParticipant(localParticipant, hostParticipant)));
  }

  const firstPlayerSlotNumber = slots.length + 1;
  otherParticipants.slice(0, slotCount - slots.length).forEach((participant, index) => {
    slots.push(buildPlayerSeat(participant, firstPlayerSlotNumber + index, isHostParticipant(participant, hostParticipant)));
  });

  const remainingSeats = Math.max(0, slotCount - slots.length);
  const botSeatCount = remainingSeats;

  for (let index = 0; index < botSeatCount; index += 1) {
    slots.push(buildBotSeat(index));
  }

  while (slots.length < slotCount) {
    slots.push(buildEmptySeat(slots.length + 1));
  }

  return slots;
}

/** 中文名：创建本地matchmaking队列状态（createLocalMatchmakingQueueState）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createLocalMatchmakingQueueState(handle: string): MatchmakingQueueState {
  const now = Date.now();
  const modeId: BattleModeIdDto = "winter";
  const capacity = battleArenaPlayerCapacityForMode(modeId);
  const normalizedHandle = normalizeHandle(handle) || "player";
  const seed = buildLocalQueueSeed(now);
  const localPlayerId = `local-player-${seed}`;
  const localParticipant: MatchmakingQueueParticipant = {
    playerId: localPlayerId,
    handle: normalizedHandle,
    joinedAt: now,
    lastSeen: now
  };

  return {
    ticketId: `local-ticket-${seed}`,
    playerId: localPlayerId,
    roomId: `local-room-${seed}`,
    matchId: `local-room-${seed}`,
    modeId,
    modeLabel: "\u4e27\u5c38\u6a21\u5f0f",
    mapId: "winter-hunt-v1",
    mapLabel: "Suroi \u51ac\u5b63\u5730\u56fe",
    createdAt: now,
    startsAt: now + MATCHMAKING_DURATION_MS,
    deadline: now + MATCHMAKING_DURATION_MS,
    serverTime: now,
    syncedAt: now,
    participants: [localParticipant],
    players: [localParticipant],
    queuedHandles: [normalizedHandle],
    capacity,
    durationMs: MATCHMAKING_DURATION_MS,
    phase: "unknown",
    startPaused: false,
    chatMessages: [],
    source: "local"
  };
}

function buildLocalSeat(
  localHandle: string,
  participant: MatchmakingQueueParticipant | undefined,
  queueState: MatchmakingQueueState | null,
  isHost: boolean
): MatchmakingSlotState {
  const handle = participant?.handle ?? (normalizeHandle(localHandle) || "player");

  return {
    slotLabel: "S1",
    kind: "self",
    title: handle,
    detail: queueState?.source === "local" ? "本地等待" : "你",
    isInteractive: true,
    isLocalPlayer: true,
    isHost
  };
}

function buildPlayerSeat(participant: MatchmakingQueueParticipant, slotNumber: number, isHost: boolean): MatchmakingSlotState {
  return {
    slotLabel: `S${slotNumber}`,
    kind: "player",
    title: participant.handle,
    detail: participant.rating === undefined ? "真人玩家" : `真人玩家 / ${participant.rating}`,
    isInteractive: false,
    isLocalPlayer: false,
    isHost
  };
}

function buildBotSeat(slotIndex: number): MatchmakingSlotState {
  const profile = getBotProfileBySlot(slotIndex);

  return {
    slotLabel: `电脑${slotIndex + 1}`,
    kind: "bot",
    title: profile?.displayName ?? `电脑 ${slotIndex + 1}`,
    detail: profile ? `电脑 / ${formatBotStrategyLabel(profile.strategyLabel)}` : "电脑 / 补位",
    isInteractive: false,
    isLocalPlayer: false,
    isHost: false
  };
}

function formatBotStrategyLabel(strategyLabel: string): string {
  switch (strategyLabel) {
    case "Anchor skirmisher":
      return "据点游击";
    case "Close-range looter":
      return "近战搜刮";
    case "Pressure duelist":
      return "压制决斗";
    case "Mid-range kiter":
      return "中距离牵制";
    case "Pickup chaser":
      return "补给追击";
    default:
      return strategyLabel;
  }
}

function buildEmptySeat(slotNumber: number): MatchmakingSlotState {
  return {
    slotLabel: `S${slotNumber}`,
    kind: "empty",
    title: "空位",
    detail: "等待补位",
    isInteractive: false,
    isLocalPlayer: false,
    isHost: false
  };
}

function dedupeParticipants(participants: MatchmakingQueueParticipant[]): MatchmakingQueueParticipant[] {
  const seen = new Set<string>();
  const nextParticipants: MatchmakingQueueParticipant[] = [];

  for (const participant of participants) {
    const identity = participant.playerId.trim() || normalizeHandle(participant.handle).toLowerCase();
    if (!identity || seen.has(identity)) {
      continue;
    }

    seen.add(identity);
    nextParticipants.push(participant);
  }

  return nextParticipants;
}

function buildLocalQueueSeed(now: number): string {
  const randomSuffix =
    typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
      ? crypto.randomUUID().slice(0, 8)
      : Math.random().toString(36).slice(2, 10);

  return `${now.toString(36)}-${randomSuffix}`;
}

function normalizeHandle(handle: string): string {
  return handle.trim();
}

function sameHandle(left: string, right: string): boolean {
  return normalizeHandle(left).toLowerCase() === normalizeHandle(right).toLowerCase();
}

function isLocalParticipant(
  participant: MatchmakingQueueParticipant,
  localPlayerId: string,
  normalizedLocalHandle: string
): boolean {
  if (localPlayerId) {
    return participant.playerId === localPlayerId;
  }

  return sameHandle(participant.handle, normalizedLocalHandle);
}

function isSameParticipant(
  participant: MatchmakingQueueParticipant,
  localParticipant: MatchmakingQueueParticipant | undefined,
  localPlayerId: string,
  normalizedLocalHandle: string
): boolean {
  if (localParticipant) {
    return participant.playerId === localParticipant.playerId;
  }

  return isLocalParticipant(participant, localPlayerId, normalizedLocalHandle);
}

function isHostParticipant(
  participant: MatchmakingQueueParticipant | undefined,
  hostParticipant: MatchmakingQueueParticipant | undefined
): boolean {
  if (!participant || !hostParticipant) {
    return false;
  }

  const participantPlayerId = participant.playerId.trim();
  const hostPlayerId = hostParticipant.playerId.trim();
  if (participantPlayerId && hostPlayerId) {
    return participantPlayerId === hostPlayerId;
  }

  return sameHandle(participant.handle, hostParticipant.handle);
}
