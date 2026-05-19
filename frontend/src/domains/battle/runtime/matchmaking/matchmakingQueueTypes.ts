import { getBotProfileBySlot } from "../../../bots/runtime/registry/botRegistry";
import { BATTLE_ARENA_PLAYER_CAPACITY, BATTLE_MATCHMAKING_DURATION_MS } from "../../objects/battleRules";

export interface MatchmakingQueueParticipant {
  playerId: string;
  handle: string;
  joinedAt: number;
  lastSeen: number;
  rating?: number;
  avatar?: string;
  skin?: string;
}

export interface MatchmakingBattleSessionRosterEntry {
  seat: number;
  playerId: string;
  handle: string;
  joinedAt: number;
  rating?: number;
  avatar?: string;
  skin?: string;
}

export interface MatchmakingBattleSessionBootstrapSeat {
  seat: number;
  playerId: string;
  heroId: string;
  handle: string;
  displayName: string;
  joinedAt: number;
  isBot: boolean;
  spawnPointIndex: number;
  rating?: number;
  avatar?: string;
  skin?: string;
}

export interface MatchmakingBattleSessionBootstrap {
  seats: MatchmakingBattleSessionBootstrapSeat[];
}

export interface MatchmakingBattleSessionDescriptor {
  battleId: string;
  startedAt: number;
  serverTime: number;
  roster: MatchmakingBattleSessionRosterEntry[];
  capacity: number;
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
}

export const MATCHMAKING_SLOT_COUNT = BATTLE_ARENA_PLAYER_CAPACITY;
const MATCHMAKING_DURATION_MS = BATTLE_MATCHMAKING_DURATION_MS;

/** 中文名：构建matchmakingslots（buildMatchmakingSlots）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function buildMatchmakingSlots(
  localHandle: string,
  queueState: MatchmakingQueueState | null
): MatchmakingSlotState[] {
  const normalizedLocalHandle = normalizeHandle(localHandle);
  const participants = dedupeParticipants(queueState?.participants ?? []);
  const localPlayerId = queueState?.playerId.trim() ?? "";
  const localParticipant = participants.find((participant) =>
    isLocalParticipant(participant, localPlayerId, normalizedLocalHandle)
  );
  const otherParticipants = participants.filter(
    (participant) => !isSameParticipant(participant, localParticipant, localPlayerId, normalizedLocalHandle)
  );
  const slots: MatchmakingSlotState[] = [];

  if (normalizedLocalHandle) {
    slots.push(buildLocalSeat(localHandle, localParticipant, queueState));
  }

  const firstPlayerSlotNumber = slots.length + 1;
  otherParticipants.slice(0, MATCHMAKING_SLOT_COUNT - slots.length).forEach((participant, index) => {
    slots.push(buildPlayerSeat(participant, firstPlayerSlotNumber + index));
  });

  const remainingSeats = Math.max(0, MATCHMAKING_SLOT_COUNT - slots.length);
  const botSeatCount = queueState?.source === "local" ? Math.max(0, remainingSeats - 1) : remainingSeats;

  for (let index = 0; index < botSeatCount; index += 1) {
    slots.push(buildBotSeat(index));
  }

  while (slots.length < MATCHMAKING_SLOT_COUNT) {
    slots.push(buildEmptySeat(slots.length + 1));
  }

  return slots;
}

/** 中文名：创建本地matchmaking队列状态（createLocalMatchmakingQueueState）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createLocalMatchmakingQueueState(handle: string): MatchmakingQueueState {
  const now = Date.now();
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
    createdAt: now,
    startsAt: now + MATCHMAKING_DURATION_MS,
    deadline: now + MATCHMAKING_DURATION_MS,
    serverTime: now,
    syncedAt: now,
    participants: [localParticipant],
    players: [localParticipant],
    queuedHandles: [normalizedHandle],
    capacity: MATCHMAKING_SLOT_COUNT,
    durationMs: MATCHMAKING_DURATION_MS,
    phase: "unknown",
    source: "local"
  };
}

function buildLocalSeat(
  localHandle: string,
  participant: MatchmakingQueueParticipant | undefined,
  queueState: MatchmakingQueueState | null
): MatchmakingSlotState {
  const handle = participant?.handle ?? (normalizeHandle(localHandle) || "player");

  return {
    slotLabel: "S1",
    kind: "self",
    title: handle,
    detail: queueState?.source === "local" ? "本地等待" : "你",
    isInteractive: true,
    isLocalPlayer: true
  };
}

function buildPlayerSeat(participant: MatchmakingQueueParticipant, slotNumber: number): MatchmakingSlotState {
  return {
    slotLabel: `S${slotNumber}`,
    kind: "player",
    title: participant.handle,
    detail: participant.rating === undefined ? "真人玩家" : `真人玩家 / ${participant.rating}`,
    isInteractive: false,
    isLocalPlayer: false
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
    isLocalPlayer: false
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
    isLocalPlayer: false
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
