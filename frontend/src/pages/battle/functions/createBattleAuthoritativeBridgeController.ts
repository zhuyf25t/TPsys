import type { BattlePreparedSkill as PreparedSkill } from "../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import {
  loadAuthoritativeBattleState,
  openAuthoritativeBattleStateStream,
  sendAuthoritativeBattleCommand,
  type AuthoritativeBattleState
} from "../../../runtime/battle/microservices/session/api/BattleAuthoritativeSessionClient";
import type { BattleRuntimeHandle } from "../../../runtime/battle/game/renderer/createBattleRuntime";
import {
  createAuthoritativeBattleInputCapture,
  type AuthoritativeBattleInputCapture
} from "../input/authoritativeBattleInput";
import type { AuthoritativeCommandHistoryStore } from "../objects/AuthoritativeCommandHistory";
import {
  AUTHORITATIVE_COMMAND_UPLINK_INTERVAL_MS,
  AUTHORITATIVE_STATE_POLL_INTERVAL_MS
} from "../objects/BattlePageRuntimeConfig";
import {
  buildAuthoritativeBattleCommand,
  hasAuthoritativePreparedInputIntent,
  resolveAcceptedClientCommandSeq,
  resolveAcknowledgedClientCommandSeq,
  resolveAuthoritativeCommandUplinkDecision,
  resolvePendingAuthoritativeCommandFlushDecision
} from "./authoritativeBattleCommandFlow";
import {
  resolveAuthoritativeStatePollDecision,
  resolveAuthoritativeStatePollingTimerDecision,
  resolveAuthoritativeStatePollResultDecision,
  resolveAuthoritativeStateStreamFallbackDecision,
  resolveAuthoritativeStateStreamStartupPlan
} from "./authoritativeBattleStateBridge";
import {
  resolveAcceptedCommandNotice,
  resolveCommandFailureNotice
} from "./authoritativeCommandNotice";
import { resolveAuthoritativePlayerPosition } from "./resolveAuthoritativePlayerPosition";
import { resolveAuthoritativePreparedInput } from "./resolveAuthoritativePreparedInput";

interface MutableRef<T> {
  current: T;
}

type FinalizeRuntime = (
  forceTimeLimit?: boolean,
  forceCurrentSnapshot?: boolean,
  preserveCompletedSession?: boolean
) => void;

interface BattleAuthoritativeBridgeControllerOptions {
  readonly runtimeRootRef: MutableRef<HTMLDivElement | null>;
  readonly runtimeHandleRef: MutableRef<BattleRuntimeHandle | null>;
  readonly finalizedRef: MutableRef<boolean>;
  readonly battleIdRef: MutableRef<string | null>;
  readonly sharedAuthoritativeRuntimeRef: MutableRef<boolean>;
  readonly authoritativeFirstFrameAppliedRef: MutableRef<boolean>;
  readonly authoritativeBattleStateRef: MutableRef<AuthoritativeBattleState | null>;
  readonly authoritativeInputCaptureRef: MutableRef<AuthoritativeBattleInputCapture | null>;
  readonly authoritativeStateRequestInFlightRef: MutableRef<boolean>;
  readonly authoritativeCommandRequestInFlightRef: MutableRef<boolean>;
  readonly authoritativeCommandUplinkPendingRef: MutableRef<boolean>;
  readonly authoritativeCommandSeqRef: MutableRef<number>;
  readonly authoritativeCommandHistoryRef: MutableRef<AuthoritativeCommandHistoryStore>;
  readonly authoritativePreparedSkillRef: MutableRef<PreparedSkill>;
  readonly authoritativeStatePollTimerRef: MutableRef<number | null>;
  readonly authoritativeStateStreamCloseRef: MutableRef<(() => void) | null>;
  readonly authoritativeCommandUplinkTimerRef: MutableRef<number | null>;
  readonly clearAuthoritativeBridgeTimers: () => void;
  readonly resolveLocalAuthoritativePlayerId: () => string;
  readonly resolveLocalAuthoritativeTicketId: () => string;
  readonly isAuthoritativeBattleFinished: () => boolean;
  readonly isAuthoritativeDurationExpired: () => boolean;
  readonly finalizeRuntime: FinalizeRuntime;
  readonly showTransientNotice: (message: string) => void;
}

export function createBattleAuthoritativeBridgeController({
  runtimeRootRef,
  runtimeHandleRef,
  finalizedRef,
  battleIdRef,
  sharedAuthoritativeRuntimeRef,
  authoritativeFirstFrameAppliedRef,
  authoritativeBattleStateRef,
  authoritativeInputCaptureRef,
  authoritativeStateRequestInFlightRef,
  authoritativeCommandRequestInFlightRef,
  authoritativeCommandUplinkPendingRef,
  authoritativeCommandSeqRef,
  authoritativeCommandHistoryRef,
  authoritativePreparedSkillRef,
  authoritativeStatePollTimerRef,
  authoritativeStateStreamCloseRef,
  authoritativeCommandUplinkTimerRef,
  clearAuthoritativeBridgeTimers,
  resolveLocalAuthoritativePlayerId,
  resolveLocalAuthoritativeTicketId,
  isAuthoritativeBattleFinished,
  isAuthoritativeDurationExpired,
  finalizeRuntime,
  showTransientNotice
}: BattleAuthoritativeBridgeControllerOptions) {
  const ensureAuthoritativeInputCapture = (): void => {
    if (!authoritativeInputCaptureRef.current && runtimeRootRef.current) {
      authoritativeInputCaptureRef.current = createAuthoritativeBattleInputCapture({
        resolveRuntimeRoot: () => runtimeRootRef.current,
        resolvePlayerPosition: () =>
          resolveAuthoritativePlayerPosition(authoritativeBattleStateRef.current, resolveLocalAuthoritativePlayerId()),
        onImmediateCommandIntent: () => {
          if (!sharedAuthoritativeRuntimeRef.current || finalizedRef.current) {
            return;
          }
          void uplinkAuthoritativeBattleCommand();
        }
      });
    }
  };

  const setAuthoritativePreparedSkill = (preparedSkill: PreparedSkill): void => {
    authoritativePreparedSkillRef.current = preparedSkill;
    runtimeHandleRef.current?.setAuthoritativePreparedSkill(preparedSkill);
  };

  const pruneAuthoritativeCommandHistoryFromState = (state: AuthoritativeBattleState): void => {
    const playerId = resolveLocalAuthoritativePlayerId();
    const acknowledgedSeq = resolveAcknowledgedClientCommandSeq(state, playerId);
    if (acknowledgedSeq === null) {
      return;
    }

    authoritativeCommandSeqRef.current = Math.max(authoritativeCommandSeqRef.current, acknowledgedSeq);
    authoritativeCommandHistoryRef.current.pruneThrough(acknowledgedSeq);
  };

  const applyInitialAuthoritativeBattleState = (state: AuthoritativeBattleState): void => {
    authoritativeBattleStateRef.current = state;
    battleIdRef.current = state.battleId;
    pruneAuthoritativeCommandHistoryFromState(state);
    const applied =
      runtimeHandleRef.current?.applyAuthoritativeState(
        state,
        resolveLocalAuthoritativePlayerId(),
        authoritativeCommandHistoryRef.current.entries
      ) ?? false;
    if (applied) {
      authoritativeFirstFrameAppliedRef.current = true;
    }
  };

  const applyAuthoritativeBattleState = (state: AuthoritativeBattleState): void => {
    if (finalizedRef.current) {
      return;
    }

    applyInitialAuthoritativeBattleState(state);
    if (authoritativeFirstFrameAppliedRef.current && isAuthoritativeBattleFinished()) {
      finalizeRuntime(isAuthoritativeDurationExpired());
    }
  };

  const stopAuthoritativeBattleBridge = (): void => {
    clearAuthoritativeBridgeTimers();
    setAuthoritativePreparedSkill(null);
    authoritativeBattleStateRef.current = null;
    authoritativeStateRequestInFlightRef.current = false;
    authoritativeCommandRequestInFlightRef.current = false;
    authoritativeCommandUplinkPendingRef.current = false;
    authoritativeCommandHistoryRef.current.clear();
    authoritativeInputCaptureRef.current?.destroy();
    authoritativeInputCaptureRef.current = null;
  };

  const pollAuthoritativeBattleState = async (): Promise<void> => {
    const pollDecision = resolveAuthoritativeStatePollDecision({
      sharedRuntimeActive: Boolean(sharedAuthoritativeRuntimeRef.current),
      battleId: battleIdRef.current,
      requestInFlight: authoritativeStateRequestInFlightRef.current
    });
    if (pollDecision.kind === "skip") {
      return;
    }

    authoritativeStateRequestInFlightRef.current = true;
    try {
      const state = await loadAuthoritativeBattleState(pollDecision.battleId);
      const pollResultDecision = resolveAuthoritativeStatePollResultDecision({
        state,
        finalized: finalizedRef.current
      });
      if (pollResultDecision.kind === "skip") {
        return;
      }

      applyAuthoritativeBattleState(pollResultDecision.state);
    } finally {
      authoritativeStateRequestInFlightRef.current = false;
    }
  };

  const startAuthoritativeStatePolling = (): void => {
    const timerDecision = resolveAuthoritativeStatePollingTimerDecision({
      timerActive: authoritativeStatePollTimerRef.current !== null
    });
    if (timerDecision.kind === "skip") {
      return;
    }

    void pollAuthoritativeBattleState();
    authoritativeStatePollTimerRef.current = window.setInterval(() => {
      void pollAuthoritativeBattleState();
    }, AUTHORITATIVE_STATE_POLL_INTERVAL_MS);
  };

  const uplinkAuthoritativeBattleCommand = async (): Promise<void> => {
    const battleId = battleIdRef.current?.trim() ?? "";
    const playerId = resolveLocalAuthoritativePlayerId();
    const ticketId = resolveLocalAuthoritativeTicketId();
    const inputCapture = authoritativeInputCaptureRef.current;
    const uplinkDecision = resolveAuthoritativeCommandUplinkDecision({
      requestInFlight: authoritativeCommandRequestInFlightRef.current,
      sharedRuntimeActive: Boolean(sharedAuthoritativeRuntimeRef.current),
      battleId,
      playerId,
      ticketId,
      inputCaptureActive: inputCapture !== null,
      battleFinished: isAuthoritativeBattleFinished()
    });

    if (uplinkDecision.kind === "defer") {
      authoritativeCommandUplinkPendingRef.current = true;
      return;
    }

    if (uplinkDecision.kind === "skip" || !inputCapture) {
      return;
    }

    const fallbackCommand = inputCapture.readSnapshot();
    const runtimeCommand = sharedAuthoritativeRuntimeRef.current
      ? runtimeHandleRef.current?.readPlayerCommand() ?? null
      : null;
    const preparedCommand = runtimeCommand
      ? resolveAuthoritativePreparedInput(runtimeCommand, fallbackCommand, authoritativePreparedSkillRef.current)
      : resolveAuthoritativePreparedInput(null, fallbackCommand, authoritativePreparedSkillRef.current);
    setAuthoritativePreparedSkill(preparedCommand.preparedSkill);
    if (!hasAuthoritativePreparedInputIntent(preparedCommand)) {
      return;
    }
    const clientCommandSeq = authoritativeCommandSeqRef.current + 1;
    authoritativeCommandSeqRef.current = clientCommandSeq;
    const outboundCommand = buildAuthoritativeBattleCommand({
      battleId,
      playerId,
      ticketId,
      clientTick: authoritativeBattleStateRef.current?.tick ?? 0,
      clientCommandSeq,
      preparedInput: preparedCommand
    });
    authoritativeCommandHistoryRef.current.record(outboundCommand);
    authoritativeCommandRequestInFlightRef.current = true;
    try {
      const outcome = await sendAuthoritativeBattleCommand(outboundCommand);
      if (outcome.ok) {
        const { accepted } = outcome;
        authoritativeCommandSeqRef.current = resolveAcceptedClientCommandSeq(
          authoritativeCommandSeqRef.current,
          accepted
        );
        const acceptedNotice = resolveAcceptedCommandNotice(accepted);
        if (acceptedNotice) {
          showTransientNotice(acceptedNotice);
        }
      } else {
        showTransientNotice(resolveCommandFailureNotice(outcome));
      }
    } finally {
      authoritativeCommandRequestInFlightRef.current = false;
      const pendingFlushDecision = resolvePendingAuthoritativeCommandFlushDecision({
        pending: authoritativeCommandUplinkPendingRef.current,
        sharedRuntimeActive: Boolean(sharedAuthoritativeRuntimeRef.current),
        finalized: finalizedRef.current
      });
      if (pendingFlushDecision.kind === "flush") {
        authoritativeCommandUplinkPendingRef.current = false;
        window.setTimeout(() => {
          void uplinkAuthoritativeBattleCommand();
        }, 0);
      }
    }
  };

  const startAuthoritativeBattleBridge = (): void => {
    ensureAuthoritativeInputCapture();
    clearAuthoritativeBridgeTimers();
    const streamPlan = resolveAuthoritativeStateStreamStartupPlan({
      battleId: battleIdRef.current
    });
    const stream = streamPlan.kind === "open_stream"
      ? openAuthoritativeBattleStateStream(streamPlan.battleId, {
          onState: applyAuthoritativeBattleState,
          onFallback: () => {
            authoritativeStateStreamCloseRef.current = null;
            const fallbackDecision = resolveAuthoritativeStateStreamFallbackDecision({
              finalized: finalizedRef.current,
              sharedRuntimeActive: Boolean(sharedAuthoritativeRuntimeRef.current)
            });
            if (fallbackDecision.kind === "poll") {
              startAuthoritativeStatePolling();
            }
          }
        })
      : null;

    if (stream) {
      authoritativeStateStreamCloseRef.current = stream.close;
    } else {
      startAuthoritativeStatePolling();
    }

    void uplinkAuthoritativeBattleCommand();
    authoritativeCommandUplinkTimerRef.current = window.setInterval(() => {
      void uplinkAuthoritativeBattleCommand();
    }, AUTHORITATIVE_COMMAND_UPLINK_INTERVAL_MS);
  };

  return {
    applyInitialAuthoritativeBattleState,
    setAuthoritativePreparedSkill,
    startAuthoritativeBattleBridge,
    stopAuthoritativeBattleBridge
  };
}
