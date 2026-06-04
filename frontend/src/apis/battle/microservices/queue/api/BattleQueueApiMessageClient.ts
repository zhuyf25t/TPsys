import type {
  BattleQueueJoinAPIMessageRequest
} from "../../../../../objects/battle/microservices/queue/api/queue/BattleQueueJoinApiTypes";
import type {
  BattleQueueStatusAPIMessageRequest
} from "../../../../../objects/battle/microservices/queue/api/queue/BattleQueueStatusApiTypes";
import type {
  BattleQueueLeaveAPIMessageRequest
} from "../../../../../objects/battle/microservices/queue/api/queue/BattleQueueLeaveApiTypes";
import type {
  BattleRoomSnapshotAPIMessageRequest
} from "../../../../../objects/battle/microservices/queue/api/room/BattleRoomSnapshotApiTypes";
import type {
  BattleRoomHeartbeatAPIMessageRequest
} from "../../../../../objects/battle/microservices/queue/api/room/BattleRoomHeartbeatApiTypes";
import {
  BATTLE_API_MESSAGES,
  postBattleApiMessage,
  type BattleApiMessageDecoder,
  type BattleApiMessageOptions,
  type BattleApiMessageResponse
} from "../../../BattleApiMessageTransport";

export type {
  BattleQueueJoinAPIMessageRequest
} from "../../../../../objects/battle/microservices/queue/api/queue/BattleQueueJoinApiTypes";

export type {
  BattleQueueStatusAPIMessageRequest
} from "../../../../../objects/battle/microservices/queue/api/queue/BattleQueueStatusApiTypes";

export type {
  BattleQueueLeaveAPIMessageRequest
} from "../../../../../objects/battle/microservices/queue/api/queue/BattleQueueLeaveApiTypes";

export type {
  BattleRoomSnapshotAPIMessageRequest
} from "../../../../../objects/battle/microservices/queue/api/room/BattleRoomSnapshotApiTypes";

export type {
  BattleRoomHeartbeatAPIMessageRequest
} from "../../../../../objects/battle/microservices/queue/api/room/BattleRoomHeartbeatApiTypes";

export function postBattleQueueJoinAPIMessage<TPayload>(
  request: BattleQueueJoinAPIMessageRequest,
  decoder: BattleApiMessageDecoder<TPayload>,
  options?: BattleApiMessageOptions
): Promise<BattleApiMessageResponse<TPayload> | null> {
  return postBattleApiMessage(BATTLE_API_MESSAGES.queueJoin, request, decoder, options);
}

export function postBattleQueueStatusAPIMessage<TPayload>(
  request: BattleQueueStatusAPIMessageRequest,
  decoder: BattleApiMessageDecoder<TPayload>,
  options?: BattleApiMessageOptions
): Promise<BattleApiMessageResponse<TPayload> | null> {
  return postBattleApiMessage(BATTLE_API_MESSAGES.queueStatus, request, decoder, options);
}

export function postBattleQueueLeaveAPIMessage<TPayload>(
  request: BattleQueueLeaveAPIMessageRequest,
  decoder: BattleApiMessageDecoder<TPayload>,
  options?: BattleApiMessageOptions
): Promise<BattleApiMessageResponse<TPayload> | null> {
  return postBattleApiMessage(BATTLE_API_MESSAGES.queueLeave, request, decoder, options);
}

export function postBattleRoomSnapshotAPIMessage<TPayload>(
  request: BattleRoomSnapshotAPIMessageRequest,
  decoder: BattleApiMessageDecoder<TPayload>,
  options?: BattleApiMessageOptions
): Promise<BattleApiMessageResponse<TPayload> | null> {
  return postBattleApiMessage(BATTLE_API_MESSAGES.roomSnapshot, request, decoder, options);
}

export function postBattleRoomHeartbeatAPIMessage<TPayload>(
  request: BattleRoomHeartbeatAPIMessageRequest,
  decoder: BattleApiMessageDecoder<TPayload>,
  options?: BattleApiMessageOptions
): Promise<BattleApiMessageResponse<TPayload> | null> {
  return postBattleApiMessage(BATTLE_API_MESSAGES.roomHeartbeat, request, decoder, options);
}
