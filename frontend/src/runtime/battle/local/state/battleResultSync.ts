import {
  postBattleResultRecordAPIMessage,
  type BattleResultRecordAPIMessageRequest
} from "../../../../apis/battle/microservices/results/api/BattleResultApiMessageClient";

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
