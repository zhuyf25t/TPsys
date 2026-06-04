import type { BattleModeIdDto } from "../shared/BattleLobbySharedApiTypes";

export interface BattleQueueJoinAPIMessageRequest {
  userToken?: string;
  handle: string;
  sessionToken: string;
  modeId?: BattleModeIdDto;
  queueRequestId?: string;
  rating?: number | string;
  avatar?: string;
  skin?: string;
}

