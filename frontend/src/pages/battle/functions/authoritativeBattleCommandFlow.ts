import type {
  AuthoritativeBattleCommand,
  AuthoritativeBattleCommandAccepted,
  AuthoritativeBattleState
} from "../../../runtime/battle/microservices/session/api/BattleAuthoritativeSessionClient";
import type { AuthoritativePreparedInputResolution } from "./resolveAuthoritativePreparedInput";

export type AuthoritativeCommandUplinkDecision =
  | { readonly kind: "send" }
  | { readonly kind: "defer"; readonly reason: "request_in_flight" }
  | {
      readonly kind: "skip";
      readonly reason:
        | "runtime_inactive"
        | "missing_battle_id"
        | "missing_player_id"
        | "missing_ticket_id"
        | "missing_input_capture"
        | "battle_finished";
    };

export type PendingAuthoritativeCommandFlushDecision =
  | { readonly kind: "flush" }
  | { readonly kind: "skip"; readonly reason: "no_pending_command" | "runtime_inactive" | "finalized" };

interface ResolveAuthoritativeCommandUplinkDecisionInput {
  readonly requestInFlight: boolean;
  readonly sharedRuntimeActive: boolean;
  readonly battleId: string;
  readonly playerId: string;
  readonly ticketId: string;
  readonly inputCaptureActive: boolean;
  readonly battleFinished: boolean;
}

interface BuildAuthoritativeBattleCommandInput {
  readonly battleId: string;
  readonly playerId: string;
  readonly ticketId: string;
  readonly clientTick: number;
  readonly clientCommandSeq: number;
  readonly preparedInput: AuthoritativePreparedInputResolution;
}

interface PendingAuthoritativeCommandFlushInput {
  readonly pending: boolean;
  readonly sharedRuntimeActive: boolean;
  readonly finalized: boolean;
}

export function resolveAuthoritativeCommandUplinkDecision({
  requestInFlight,
  sharedRuntimeActive,
  battleId,
  playerId,
  ticketId,
  inputCaptureActive,
  battleFinished
}: ResolveAuthoritativeCommandUplinkDecisionInput): AuthoritativeCommandUplinkDecision {
  if (requestInFlight) {
    return { kind: "defer", reason: "request_in_flight" };
  }

  if (!sharedRuntimeActive) {
    return { kind: "skip", reason: "runtime_inactive" };
  }
  if (!battleId) {
    return { kind: "skip", reason: "missing_battle_id" };
  }
  if (!playerId) {
    return { kind: "skip", reason: "missing_player_id" };
  }
  if (!ticketId) {
    return { kind: "skip", reason: "missing_ticket_id" };
  }
  if (!inputCaptureActive) {
    return { kind: "skip", reason: "missing_input_capture" };
  }
  if (battleFinished) {
    return { kind: "skip", reason: "battle_finished" };
  }

  return { kind: "send" };
}

export function resolveAcknowledgedClientCommandSeq(
  state: AuthoritativeBattleState,
  playerId: string
): number | null {
  const normalizedPlayerId = playerId.trim();
  if (!normalizedPlayerId) {
    return null;
  }

  const localPlayer = state.players.find((player) => player.playerId === normalizedPlayerId);
  return localPlayer?.lastClientCommandSeq ?? null;
}

export function resolveAcceptedClientCommandSeq(
  currentClientCommandSeq: number,
  accepted: AuthoritativeBattleCommandAccepted
): number {
  return Math.max(currentClientCommandSeq, accepted.acceptedCommandSeq);
}

export function buildAuthoritativeBattleCommand({
  battleId,
  playerId,
  ticketId,
  clientTick,
  clientCommandSeq,
  preparedInput
}: BuildAuthoritativeBattleCommandInput): AuthoritativeBattleCommand {
  const { input, confirmedTarget } = preparedInput;
  return {
    battleId,
    playerId,
    ticketId,
    clientTick,
    clientCommandSeq,
    movement: input.movement,
    aim: input.aim,
    primaryHeld: input.primaryHeld,
    sprint: input.sprint,
    reloadPressed: input.reloadPressed,
    castDash: input.castDash,
    castBlink: input.castBlink,
    castFreeze: input.castFreeze,
    castCritical: input.castCritical,
    pointerWorld: confirmedTarget ?? input.pointerWorld,
    switchWeaponDirection: input.switchWeaponDirection,
    switchWeaponIndex: input.switchWeaponIndex
  };
}

export function hasAuthoritativePreparedInputIntent(
  {
    input,
    confirmedTarget,
    preparedSkill
  }: AuthoritativePreparedInputResolution,
  previousCommand: AuthoritativeBattleCommand | null = null
): boolean {
  const hasActiveIntent = (
    Math.hypot(input.movement.x, input.movement.y) > 0.0001 ||
    input.primaryHeld ||
    input.sprint ||
    input.reloadPressed ||
    input.castDash ||
    input.castBlink ||
    input.castFreeze ||
    input.castCritical ||
    input.switchWeaponDirection !== 0 ||
    input.switchWeaponIndex !== null ||
    confirmedTarget !== null ||
    preparedSkill !== null
  );

  if (hasActiveIntent) {
    return true;
  }

  return hasContinuousInputLatched(previousCommand);
}

function hasContinuousInputLatched(command: AuthoritativeBattleCommand | null): boolean {
  return (
    command !== null &&
    (
      Math.hypot(command.movement.x, command.movement.y) > 0.0001 ||
      command.primaryHeld ||
      command.sprint
    )
  );
}

export function resolvePendingAuthoritativeCommandFlushDecision({
  pending,
  sharedRuntimeActive,
  finalized
}: PendingAuthoritativeCommandFlushInput): PendingAuthoritativeCommandFlushDecision {
  if (!pending) {
    return { kind: "skip", reason: "no_pending_command" };
  }
  if (!sharedRuntimeActive) {
    return { kind: "skip", reason: "runtime_inactive" };
  }
  if (finalized) {
    return { kind: "skip", reason: "finalized" };
  }

  return { kind: "flush" };
}
