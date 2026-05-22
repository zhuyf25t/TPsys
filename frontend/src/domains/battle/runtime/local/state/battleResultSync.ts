import {
  postBattleResultRecordAPIMessage,
  type BattleResultRecordAPIMessageRequest
} from "../../../api/battleApiMessageClient";

export type BattleResultSyncPayload = BattleResultRecordAPIMessageRequest;

export async function syncBattleResultToBackend(payload: BattleResultSyncPayload): Promise<boolean> {
  if (typeof window === "undefined") {
    return false;
  }

  try {
    const response = await postBattleResultRecordAPIMessage(payload, () => true, {
      keepalive: true
    });

    return response?.ok === true;
  } catch {
    return false;
  }
}

export async function backfillLocalBattleTruthToBackend(): Promise<void> {
  if (typeof window === "undefined") {
    return;
  }

  try {
    const { backfillLocalBattleTruthToBackend: runBattleTruthBackfill } = await import("./battleTruthStore");
    await runBattleTruthBackfill();
  } catch {
    // Backfill is best effort; the live battle result flow already handles direct settlement.
  }
}
