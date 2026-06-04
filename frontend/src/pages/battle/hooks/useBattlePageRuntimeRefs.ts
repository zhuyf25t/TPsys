import { useRef } from "react";
import type { BattlePreparedSkill as PreparedSkill } from "../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { AuthoritativeBattleState } from "../../../runtime/battle/microservices/session/api/BattleAuthoritativeSessionClient";
import type { BattleRuntimeHandle } from "../../../runtime/battle/game/renderer/createBattleRuntime";
import type { MatchmakingQueueState } from "../../../runtime/battle/matchmaking/matchmakingQueueTypes";
import type { BattleReplayFrameState as ReplayFrame } from "../../../objects/battle/microservices/projections/objects/replay/BattleReplayFrameState";
import type { AuthoritativeBattleInputCapture } from "../input/authoritativeBattleInput";
import { createAuthoritativeCommandHistory } from "../objects/AuthoritativeCommandHistory";

export function useBattlePageRuntimeRefs() {
  const runtimeRootRef = useRef<HTMLDivElement | null>(null);
  const hudRootRef = useRef<HTMLDivElement | null>(null);
  const runtimeHandleRef = useRef<BattleRuntimeHandle | null>(null);
  const finalizedRef = useRef(false);
  const battleStartLockedRef = useRef(false);
  const discardSessionOnNextTeardownRef = useRef(false);
  const newBattleResetPendingRef = useRef(false);
  const lastUrlRequestedNewBattleRef = useRef(false);
  const queueStateRef = useRef<MatchmakingQueueState | null>(null);
  const localAuthoritativePlayerIdRef = useRef<string | null>(null);
  const replayFramesRef = useRef<ReplayFrame[]>([]);
  const lastReplaySampleFrameRef = useRef<ReplayFrame | null>(null);
  const lastReplaySampleElapsedRef = useRef<number | null>(null);
  const lastActiveSessionPersistedAtRef = useRef(0);
  const battleIdRef = useRef<string | null>(null);
  const activeSessionEpochRef = useRef<string | null>(null);
  const authoritativeBattleStateRef = useRef<AuthoritativeBattleState | null>(null);
  const authoritativeInputCaptureRef = useRef<AuthoritativeBattleInputCapture | null>(null);
  const authoritativeStateRequestInFlightRef = useRef(false);
  const authoritativeCommandRequestInFlightRef = useRef(false);
  const authoritativeCommandUplinkPendingRef = useRef(false);
  const authoritativeCommandSeqRef = useRef(0);
  const authoritativeCommandHistoryRef = useRef(createAuthoritativeCommandHistory());
  const authoritativeFinalizationInFlightRef = useRef(false);
  const sharedAuthoritativeRuntimeRef = useRef(false);
  const authoritativeFirstFrameAppliedRef = useRef(false);
  const authoritativePreparedSkillRef = useRef<PreparedSkill>(null);
  const backendQueueJoinPendingRef = useRef(false);

  return {
    runtimeRootRef,
    hudRootRef,
    runtimeHandleRef,
    finalizedRef,
    battleStartLockedRef,
    discardSessionOnNextTeardownRef,
    newBattleResetPendingRef,
    lastUrlRequestedNewBattleRef,
    queueStateRef,
    localAuthoritativePlayerIdRef,
    replayFramesRef,
    lastReplaySampleFrameRef,
    lastReplaySampleElapsedRef,
    lastActiveSessionPersistedAtRef,
    battleIdRef,
    activeSessionEpochRef,
    authoritativeBattleStateRef,
    authoritativeInputCaptureRef,
    authoritativeStateRequestInFlightRef,
    authoritativeCommandRequestInFlightRef,
    authoritativeCommandUplinkPendingRef,
    authoritativeCommandSeqRef,
    authoritativeCommandHistoryRef,
    authoritativeFinalizationInFlightRef,
    sharedAuthoritativeRuntimeRef,
    authoritativeFirstFrameAppliedRef,
    authoritativePreparedSkillRef,
    backendQueueJoinPendingRef
  };
}
